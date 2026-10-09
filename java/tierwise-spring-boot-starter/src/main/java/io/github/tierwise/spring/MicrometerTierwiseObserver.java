package io.github.tierwise.spring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Objects;

/**
 * Reports the starter's events as Micrometer meters.
 *
 * <table>
 *   <caption>Meters</caption>
 *   <tr><th>Meter</th><th>Type</th><th>Tags</th><th>Meaning</th></tr>
 *   <tr><td>{@code tierwise.bundle.version}</td><td>gauge, always 1</td><td>{@code version}</td>
 *     <td>The {@code bundleVersion} in use. Only the current version is registered.</td></tr>
 *   <tr><td>{@code tierwise.bundle.age}</td><td>gauge, seconds</td><td></td>
 *     <td>Time since the bundle in use was last confirmed current (see {@link TierwiseEvaluators}).</td></tr>
 *   <tr><td>{@code tierwise.refresh.failures}</td><td>counter</td><td></td>
 *     <td>Refreshes that failed.</td></tr>
 *   <tr><td>{@code tierwise.decisions}</td><td>counter</td><td>{@code feature}, {@code reason}</td>
 *     <td>Decisions, by feature and the type of the reason.</td></tr>
 *   <tr><td>{@code tierwise.provider.failures}</td><td>counter</td><td>{@code level}, {@code outcome}</td>
 *     <td>Keys whose provider call failed ({@code unavailable}) or returned an invalid value
 *     ({@code invalid}).</td></tr>
 * </table>
 */
public final class MicrometerTierwiseObserver implements TierwiseObserver {

  private final MeterRegistry registry;
  private final Counter refreshFailures;
  private Gauge versionGauge;

  /**
   * Creates an observer that registers its meters in a registry.
   *
   * @param registry the registry
   */
  public MicrometerTierwiseObserver(MeterRegistry registry) {
    this.registry = Objects.requireNonNull(registry, "registry");
    this.refreshFailures = Counter.builder("tierwise.refresh.failures")
        .description("Bundle refreshes that failed; the previous bundle stayed in use")
        .register(registry);
  }

  @Override
  public void onStarted(TierwiseEvaluators evaluators) {
    Gauge.builder("tierwise.bundle.age", evaluators, e -> e.staleness().toMillis() / 1000.0)
        .description("Seconds since the bundle in use was last confirmed current by its source")
        .baseUnit("seconds")
        .strongReference(true)
        .register(registry);
  }

  @Override
  public synchronized void onBundleActivated(String bundleVersion) {
    if (versionGauge != null) {
      registry.remove(versionGauge);
    }
    versionGauge = Gauge.builder("tierwise.bundle.version", () -> 1)
        .description("The bundle version in use, as the version tag")
        .tag("version", bundleVersion)
        .strongReference(true)
        .register(registry);
  }

  @Override
  public void onRefreshFailure() {
    refreshFailures.increment();
  }

  @Override
  public void onProviderFailure(String level, String outcome) {
    registry.counter("tierwise.provider.failures", "level", level, "outcome", outcome).increment();
  }

  @Override
  public void onDecision(String feature, String reason) {
    registry.counter("tierwise.decisions", "feature", feature, "reason", reason).increment();
  }
}
