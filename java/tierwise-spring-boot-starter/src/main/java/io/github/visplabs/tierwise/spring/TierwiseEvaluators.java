package io.github.visplabs.tierwise.spring;

import io.github.visplabs.tierwise.core.Evaluator;
import io.github.visplabs.tierwise.core.EvaluatorOptions;
import io.github.visplabs.tierwise.core.LoadedBundle;
import io.github.visplabs.tierwise.core.Tierwise;
import io.github.visplabs.tierwise.core.TierwiseException;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Holds the evaluator for the bundle in use, refreshes it from a {@link BundleSource}, and exposes how
 * fresh it is.
 *
 * <h2>Swapping</h2>
 *
 * <p>The evaluator and its bundle version are one immutable pair behind one {@link AtomicReference}. A
 * refresh builds the new evaluator completely, then publishes the pair with a single write. A caller
 * that read {@link #current()} before the write keeps using the old evaluator until its call returns
 * (evaluators are immutable, EVAL-48, EVAL-52); a caller that reads afterwards gets the new one. No call
 * sees a mix of the two.
 *
 * <h2>Last-known-good</h2>
 *
 * <p>Any failure during a refresh (the source is unreachable or answers with an error, the bytes fail
 * the integrity or version checks, the evaluator cannot be created) leaves the pair in place, counts a
 * refresh failure and records it. {@link #staleness()} then grows, and once it passes the threshold
 * {@link #isStale()} is {@code true} and the failure is logged at error level.
 *
 * <h2>Staleness</h2>
 *
 * <p>A bundle carries no publication time, so staleness is the time since the bundle in use was last
 * confirmed current: loaded, or confirmed unchanged by a successful refresh (for HTTP, a {@code 304}).
 * While the application runs on a startup snapshot ({@link #isUsingSnapshot()}) no confirmation from the
 * primary source has happened yet.
 */
public final class TierwiseEvaluators implements AutoCloseable {

  private static final Log LOG = LogFactory.getLog(TierwiseEvaluators.class);

  /**
   * A refresh failure.
   *
   * @param at when it happened
   * @param message the failure, without a stack trace
   */
  public record Failure(Instant at, String message) {

    /** Validates the components. */
    public Failure {
      Objects.requireNonNull(at, "at");
      Objects.requireNonNull(message, "message");
    }
  }

  private record Active(Evaluator evaluator, String bundleVersion, Instant loadedAt) {
  }

  private final BundleSource source;
  private final BundleSource snapshot;
  private final EvaluatorOptions options;
  private final Duration stalenessThreshold;
  private final TierwiseObserver observer;
  private final Clock clock;
  private final AtomicReference<Active> active = new AtomicReference<>();
  private final ReentrantLock refreshLock = new ReentrantLock();
  private volatile Instant confirmedAt;
  private volatile boolean usingSnapshot;
  private volatile Failure lastFailure;
  private volatile ScheduledExecutorService scheduler;

  private TierwiseEvaluators(
      BundleSource source,
      BundleSource snapshot,
      EvaluatorOptions options,
      Duration stalenessThreshold,
      TierwiseObserver observer,
      Clock clock) {
    this.source = source;
    this.snapshot = snapshot;
    this.options = options;
    this.stalenessThreshold = stalenessThreshold;
    this.observer = observer;
    this.clock = clock;
  }

  /**
   * Loads the first bundle and starts the refresh schedule.
   *
   * @param source the primary source
   * @param snapshot the startup fallback, or {@code null}
   * @param options the providers, timeouts and listener for every evaluator
   * @param refreshInterval the delay between refreshes; zero means no scheduled refresh
   * @param stalenessThreshold when the bundle in use counts as stale
   * @param observer receives the operational events
   * @param clock the time source
   * @return the started evaluators
   * @throws BundleLoadException when neither the source nor the snapshot supplies a loadable bundle
   */
  static TierwiseEvaluators start(
      BundleSource source,
      BundleSource snapshot,
      EvaluatorOptions options,
      Duration refreshInterval,
      Duration stalenessThreshold,
      TierwiseObserver observer,
      Clock clock) {
    TierwiseEvaluators evaluators = new TierwiseEvaluators(
        Objects.requireNonNull(source, "source"),
        snapshot,
        Objects.requireNonNull(options, "options"),
        Objects.requireNonNull(stalenessThreshold, "stalenessThreshold"),
        Objects.requireNonNull(observer, "observer"),
        Objects.requireNonNull(clock, "clock"));
    evaluators.loadInitial();
    evaluators.notifyObserver(o -> o.onStarted(evaluators));
    evaluators.notifyObserver(o -> o.onBundleActivated(evaluators.bundleVersion()));
    if (!refreshInterval.isZero()) {
      evaluators.schedule(refreshInterval);
    }
    return evaluators;
  }

  /**
   * Returns the evaluator for the bundle in use. Read it once per operation: the value stays valid for
   * as long as the caller holds it, even after a swap.
   *
   * @return the current evaluator
   */
  public Evaluator current() {
    return active.get().evaluator();
  }

  /**
   * Returns the {@code bundleVersion} of the bundle in use.
   *
   * @return the digest of the bundle
   */
  public String bundleVersion() {
    return active.get().bundleVersion();
  }

  /**
   * Returns when the bundle in use was loaded.
   *
   * @return the load time
   */
  public Instant loadedAt() {
    return active.get().loadedAt();
  }

  /**
   * Returns when the bundle in use was last confirmed current.
   *
   * @return the time of the load or of the last successful refresh
   */
  public Instant confirmedAt() {
    return confirmedAt;
  }

  /**
   * Returns how long ago the bundle in use was last confirmed current.
   *
   * @return the staleness, never negative
   */
  public Duration staleness() {
    Duration age = Duration.between(confirmedAt, clock.instant());
    return age.isNegative() ? Duration.ZERO : age;
  }

  /**
   * Returns the staleness at which {@link #isStale()} turns {@code true}.
   *
   * @return {@code tierwise.staleness-threshold}
   */
  public Duration stalenessThreshold() {
    return stalenessThreshold;
  }

  /**
   * Tells whether the bundle in use has gone unconfirmed for longer than the threshold.
   *
   * @return {@code true} when {@link #staleness()} exceeds {@link #stalenessThreshold()}
   */
  public boolean isStale() {
    return staleness().compareTo(stalenessThreshold) > 0;
  }

  /**
   * Tells whether the bundle in use came from the startup snapshot because the primary source failed.
   * It turns {@code false} at the first successful refresh from the primary source.
   *
   * @return {@code true} while the snapshot is in use
   */
  public boolean isUsingSnapshot() {
    return usingSnapshot;
  }

  /**
   * Returns the most recent refresh failure since the last success.
   *
   * @return the failure, or empty when the last refresh succeeded
   */
  public Optional<Failure> lastFailure() {
    return Optional.ofNullable(lastFailure);
  }

  /**
   * Describes the primary source.
   *
   * @return the description, safe to log
   */
  public String sourceDescription() {
    return source.describe();
  }

  /**
   * Checks the primary source now and swaps to a new bundle if it supplies one. The scheduled refresh
   * calls this; it can also be called directly, for example after a webhook. Calls are serialised.
   *
   * @return what happened; a failure is returned, not thrown
   */
  public RefreshResult refresh() {
    refreshLock.lock();
    try {
      Active current = active.get();
      BundleFetch fetched = source.fetch(current.bundleVersion());
      if (fetched.isNotModified()) {
        confirm();
        return RefreshResult.UNCHANGED;
      }
      LoadedBundle bundle = Tierwise.loadBundle(fetched.bytes());
      if (bundle.bundleVersion().equals(current.bundleVersion())) {
        confirm();
        return RefreshResult.UNCHANGED;
      }
      Evaluator evaluator = Tierwise.evaluator(bundle, options);
      active.set(new Active(evaluator, bundle.bundleVersion(), clock.instant()));
      confirm();
      LOG.info("Tierwise bundle " + current.bundleVersion() + " replaced by " + bundle.bundleVersion()
          + " from " + source.describe());
      notifyObserver(o -> o.onBundleActivated(bundle.bundleVersion()));
      return RefreshResult.SWAPPED;
    } catch (IOException | RuntimeException e) {
      recordFailure(e);
      return RefreshResult.FAILED;
    } finally {
      refreshLock.unlock();
    }
  }

  /** Stops the scheduled refresh. The evaluator in use stays usable. */
  @Override
  public void close() {
    ScheduledExecutorService running = scheduler;
    if (running != null) {
      running.shutdownNow();
    }
  }

  private void confirm() {
    confirmedAt = clock.instant();
    usingSnapshot = false;
    lastFailure = null;
  }

  private void recordFailure(Exception e) {
    String message = describe(e);
    lastFailure = new Failure(clock.instant(), message);
    notifyObserver(TierwiseObserver::onRefreshFailure);
    String text = "Tierwise bundle refresh from " + source.describe() + " failed, keeping bundle "
        + bundleVersion() + " (last confirmed " + staleness() + " ago): " + message;
    if (isStale()) {
      LOG.error(text + ". The bundle is stale: it was last confirmed more than " + stalenessThreshold + " ago.");
    } else {
      LOG.warn(text);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("Refresh failure detail", e);
    }
  }

  private void loadInitial() {
    Exception primary;
    try {
      publishInitial(source, false);
      return;
    } catch (IOException | RuntimeException e) {
      primary = e;
    }
    if (snapshot == null) {
      throw new BundleLoadException("No Tierwise bundle could be loaded from " + source.describe()
          + " and no snapshot is configured (tierwise.snapshot): " + describe(primary), primary);
    }
    LOG.warn("No Tierwise bundle could be loaded from " + source.describe() + " (" + describe(primary)
        + "); starting from the snapshot " + snapshot.describe());
    try {
      publishInitial(snapshot, true);
    } catch (IOException | RuntimeException e) {
      BundleLoadException failure = new BundleLoadException("No Tierwise bundle could be loaded from "
          + source.describe() + " (" + describe(primary) + ") or from the snapshot " + snapshot.describe()
          + " (" + describe(e) + ")", primary);
      failure.addSuppressed(e);
      throw failure;
    }
  }

  private void publishInitial(BundleSource from, boolean fromSnapshot) throws IOException {
    BundleFetch fetched = from.fetch(null);
    if (fetched.isNotModified()) {
      throw new IOException(from.describe() + " reported no change although no bundle is held");
    }
    LoadedBundle bundle = Tierwise.loadBundle(fetched.bytes());
    Evaluator evaluator = Tierwise.evaluator(bundle, options);
    Instant now = clock.instant();
    active.set(new Active(evaluator, bundle.bundleVersion(), now));
    confirmedAt = now;
    usingSnapshot = fromSnapshot;
    LOG.info("Tierwise bundle " + bundle.bundleVersion() + " loaded from " + from.describe());
  }

  private void schedule(Duration interval) {
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
      Thread thread = new Thread(task, "tierwise-refresh");
      thread.setDaemon(true);
      return thread;
    });
    scheduler = executor;
    executor.scheduleWithFixedDelay(this::refreshQuietly, interval.toMillis(), interval.toMillis(), TimeUnit.MILLISECONDS);
  }

  private void refreshQuietly() {
    try {
      refresh();
    } catch (RuntimeException | Error e) {
      // A task that throws is never scheduled again, so nothing may escape.
      LOG.error("Tierwise refresh task failed unexpectedly", e);
    }
  }

  private void notifyObserver(Consumer<TierwiseObserver> call) {
    try {
      call.accept(observer);
    } catch (RuntimeException e) {
      LOG.warn("Tierwise observer failed: " + e);
    }
  }

  private static String describe(Exception e) {
    String message = e.getMessage();
    if (e instanceof TierwiseException tierwise) {
      return message != null && message.startsWith(tierwise.code()) ? message : tierwise.code() + ": " + message;
    }
    return message == null ? e.getClass().getSimpleName() : e.getClass().getSimpleName() + ": " + message;
  }
}
