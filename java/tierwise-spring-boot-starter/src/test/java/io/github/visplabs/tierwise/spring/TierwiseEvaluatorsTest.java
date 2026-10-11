package io.github.visplabs.tierwise.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.Evaluator;
import io.github.visplabs.tierwise.core.EvaluatorOptions;
import io.github.visplabs.tierwise.core.Tierwise;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TierwiseEvaluatorsTest {

  private static final Duration THRESHOLD = Duration.ofMinutes(5);

  private final TestClock clock = new TestClock();
  private final RecordingObserver observer = new RecordingObserver();
  private final List<TierwiseEvaluators> started = new ArrayList<>();
  private final EvaluatorOptions options = EvaluatorOptions.builder()
      .provider("tenant", nodeId -> Map.of(), Duration.ofMillis(200))
      .build();

  private byte[] bundleA;
  private byte[] bundleB;
  private String versionA;
  private String versionB;
  private StubBundleServer server;

  @BeforeEach
  void setUp() throws IOException {
    bundleA = TestBundles.limit(10);
    bundleB = TestBundles.limit(20);
    versionA = Tierwise.loadBundle(bundleA).bundleVersion();
    versionB = Tierwise.loadBundle(bundleB).bundleVersion();
    server = new StubBundleServer(bundleA, versionA);
  }

  @AfterEach
  void tearDown() {
    started.forEach(TierwiseEvaluators::close);
    server.close();
  }

  private BundleSource httpSource() {
    return BundleSources.forServer(server.baseUrl(), new TierwiseProperties().getHttp());
  }

  private TierwiseEvaluators start(BundleSource source, BundleSource snapshot) {
    return register(TierwiseEvaluators.start(source, snapshot, options, Duration.ZERO, THRESHOLD, observer, clock));
  }

  private TierwiseEvaluators register(TierwiseEvaluators evaluators) {
    started.add(evaluators);
    return evaluators;
  }

  private static Object limit(TierwiseEvaluators evaluators) {
    return evaluators.current().resolve(Context.empty(), "limit.max_items").value();
  }

  private static BundleSource snapshotOf(byte[] bundle) {
    return new BundleSource() {
      @Override
      public String describe() {
        return "snapshot-fixture";
      }

      @Override
      public BundleFetch fetch(String currentVersion) {
        return BundleFetch.of(bundle, null);
      }
    };
  }

  private static void await(BooleanSupplier condition) throws InterruptedException {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (!condition.getAsBoolean()) {
      assertTrue(System.nanoTime() < deadline, "timed out waiting for the condition");
      Thread.sleep(10);
    }
  }

  @Test
  void startupLoadsTheBundleFromTheServer() {
    TierwiseEvaluators evaluators = start(httpSource(), null);

    assertEquals(versionA, evaluators.bundleVersion());
    assertEquals(10L, limit(evaluators));
    assertFalse(evaluators.isUsingSnapshot());
    assertFalse(evaluators.isStale());
    assertEquals(List.of(versionA), observer.activated);
    assertSame(evaluators, observer.started);
    assertEquals("/v1/bundles/latest", server.requests().get(0).path());
  }

  @Test
  void refreshSendsTheVersionAsEtagAndAnUnchangedBundleIs304() {
    TierwiseEvaluators evaluators = start(httpSource(), null);
    Evaluator before = evaluators.current();
    clock.advance(Duration.ofMinutes(2));
    assertEquals(Duration.ofMinutes(2), evaluators.staleness());

    assertEquals(RefreshResult.UNCHANGED, evaluators.refresh());

    assertEquals("\"" + versionA + "\"", server.requests().get(1).ifNoneMatch());
    assertEquals(1, server.notModifiedCount());
    assertSame(before, evaluators.current());
    assertEquals(Duration.ZERO, evaluators.staleness(), "a 304 confirms the bundle");
    assertEquals(List.of(versionA), observer.activated);
  }

  @Test
  void aNewVersionIsSwappedIn() {
    TierwiseEvaluators evaluators = start(httpSource(), null);
    Evaluator before = evaluators.current();
    server.publish(bundleB, versionB);

    assertEquals(RefreshResult.SWAPPED, evaluators.refresh());

    assertEquals(versionB, evaluators.bundleVersion());
    assertEquals(20L, limit(evaluators));
    assertNotEquals(before, evaluators.current());
    assertEquals(List.of(versionA, versionB), observer.activated);
    assertEquals(0, observer.refreshFailures.get());
  }

  @Test
  void promotingAnOlderVersionRollsBack() {
    TierwiseEvaluators evaluators = start(httpSource(), null);
    server.publish(bundleB, versionB);
    evaluators.refresh();

    server.publish(bundleA, versionA);

    assertEquals(RefreshResult.SWAPPED, evaluators.refresh());
    assertEquals(versionA, evaluators.bundleVersion());
    assertEquals(10L, limit(evaluators));
  }

  @Test
  void aSourceThatReturnsTheSameBundleAgainIsUnchanged() {
    BundleSource ignoresEtag = new BundleSource() {
      @Override
      public String describe() {
        return "ignores-etag";
      }

      @Override
      public BundleFetch fetch(String currentVersion) {
        return BundleFetch.of(bundleA, versionA);
      }
    };
    TierwiseEvaluators evaluators = start(ignoresEtag, null);
    Evaluator before = evaluators.current();

    assertEquals(RefreshResult.UNCHANGED, evaluators.refresh());

    assertSame(before, evaluators.current());
    assertEquals(List.of(versionA), observer.activated);
  }

  @Test
  void lastKnownGoodServesWhileTheServerIsDownAndStalenessGrows() throws IOException {
    TierwiseEvaluators evaluators = start(httpSource(), null);
    server.stop();

    assertEquals(RefreshResult.FAILED, evaluators.refresh());
    assertEquals(RefreshResult.FAILED, evaluators.refresh());

    assertEquals(versionA, evaluators.bundleVersion());
    assertEquals(10L, limit(evaluators), "the last-known-good bundle keeps answering");
    assertEquals(2, observer.refreshFailures.get());
    assertTrue(evaluators.lastFailure().isPresent());
    assertFalse(evaluators.isStale());

    clock.advance(THRESHOLD.plusSeconds(1));
    assertTrue(evaluators.isStale());
    assertEquals(THRESHOLD.plusSeconds(1), evaluators.staleness());
    assertEquals(10L, limit(evaluators));

    server.restart();
    assertEquals(RefreshResult.UNCHANGED, evaluators.refresh());
    assertFalse(evaluators.isStale());
    assertTrue(evaluators.lastFailure().isEmpty());
    assertEquals(Duration.ZERO, evaluators.staleness());
  }

  @Test
  void aServerErrorKeepsTheCurrentBundle() {
    TierwiseEvaluators evaluators = start(httpSource(), null);
    server.failWith(500);

    assertEquals(RefreshResult.FAILED, evaluators.refresh());

    assertEquals(versionA, evaluators.bundleVersion());
    assertTrue(evaluators.lastFailure().orElseThrow().message().contains("500"));

    server.recover();
    server.publish(bundleB, versionB);
    assertEquals(RefreshResult.SWAPPED, evaluators.refresh());
    assertEquals(versionB, evaluators.bundleVersion());
  }

  @Test
  void aCorruptBundleIsRejectedAndTheCurrentBundleKeepsServing() {
    TierwiseEvaluators evaluators = start(httpSource(), null);
    byte[] corrupt = bundleB.clone();
    corrupt[corrupt.length / 2] ^= 0x01;
    server.publish(corrupt, versionB);

    assertEquals(RefreshResult.FAILED, evaluators.refresh());

    assertEquals(versionA, evaluators.bundleVersion());
    assertEquals(10L, limit(evaluators));
    assertEquals(1, observer.refreshFailures.get());
  }

  @Test
  void aBundleThatNeedsAMissingProviderIsRejectedAtRefresh() {
    TierwiseEvaluators evaluators = start(httpSource(), null);
    server.publish(TestBundles.spec("edge-providers.json"), "sha256:ignored");

    assertEquals(RefreshResult.FAILED, evaluators.refresh());

    assertEquals(versionA, evaluators.bundleVersion());
    assertTrue(evaluators.lastFailure().orElseThrow().message().contains("E_PROVIDER_MISSING"),
        evaluators.lastFailure().orElseThrow().message());
  }

  @Test
  void startupFailsFastWhenTheServerIsDown() {
    server.stop();

    BundleLoadException e = assertThrows(BundleLoadException.class, () -> start(httpSource(), null));

    assertInstanceOf(IOException.class, e.getCause());
    assertTrue(e.getMessage().contains("no snapshot is configured"), e.getMessage());
    assertTrue(e.getMessage().contains("127.0.0.1"), e.getMessage());
  }

  @Test
  void startupFailsFastWhenTheBundleIsInvalidAndThereIsNoSnapshot() {
    server.publish("not a bundle".getBytes(java.nio.charset.StandardCharsets.UTF_8), "x");

    BundleLoadException e = assertThrows(BundleLoadException.class, () -> start(httpSource(), null));

    assertTrue(e.getMessage().contains("E_BUNDLE_INTEGRITY"), e.getMessage());
  }

  @Test
  void startupFailsFastWhenTheBundleNeedsAProviderThatIsMissing() {
    server.publish(TestBundles.spec("edge-providers.json"), "x");

    BundleLoadException e = assertThrows(BundleLoadException.class, () -> start(httpSource(), null));

    assertTrue(e.getMessage().contains("E_PROVIDER_MISSING"), e.getMessage());
  }

  @Test
  void startupSucceedsFromTheSnapshotWhenTheServerIsDownAndRecoversLater() throws IOException {
    server.stop();

    TierwiseEvaluators evaluators = start(httpSource(), snapshotOf(bundleA));

    assertEquals(versionA, evaluators.bundleVersion());
    assertTrue(evaluators.isUsingSnapshot());
    assertEquals(10L, limit(evaluators));

    server.publish(bundleB, versionB);
    server.restart();
    clock.advance(Duration.ofMinutes(1));
    assertEquals(RefreshResult.SWAPPED, evaluators.refresh());
    assertEquals(versionB, evaluators.bundleVersion());
    assertFalse(evaluators.isUsingSnapshot());
    assertEquals(Duration.ZERO, evaluators.staleness());
  }

  @Test
  void aSnapshotWithTheServersVersionIsConfirmedBy304() throws IOException {
    server.stop();
    TierwiseEvaluators evaluators = start(httpSource(), snapshotOf(bundleA));
    server.restart();

    assertEquals(RefreshResult.UNCHANGED, evaluators.refresh());

    assertFalse(evaluators.isUsingSnapshot());
    assertEquals(1, server.notModifiedCount());
  }

  @Test
  void aSnapshotIsUsedWhenTheServersBundleCannotBeLoaded() {
    server.publish("garbage".getBytes(java.nio.charset.StandardCharsets.UTF_8), "x");

    TierwiseEvaluators evaluators = start(httpSource(), snapshotOf(bundleA));

    assertTrue(evaluators.isUsingSnapshot());
    assertEquals(versionA, evaluators.bundleVersion());
  }

  @Test
  void startupFailsWhenTheSnapshotFailsToo() {
    server.stop();
    BundleSource brokenSnapshot = new FileBundleSource(java.nio.file.Path.of("does-not-exist.json"));

    BundleLoadException e = assertThrows(BundleLoadException.class, () -> start(httpSource(), brokenSnapshot));

    assertEquals(1, e.getSuppressed().length);
    assertTrue(e.getMessage().contains("snapshot"), e.getMessage());
  }

  @Test
  void theScheduledRefreshPicksUpANewVersionAndCloseStopsIt() throws InterruptedException {
    TierwiseEvaluators evaluators = register(TierwiseEvaluators.start(
        httpSource(), null, options, Duration.ofMillis(50), THRESHOLD, observer, Clock.systemUTC()));
    server.publish(bundleB, versionB);

    await(() -> evaluators.bundleVersion().equals(versionB));

    evaluators.close();
    int requests = server.requests().size();
    Thread.sleep(300);
    assertEquals(requests, server.requests().size(), "no request after close");
  }

  @Test
  void aZeroIntervalSchedulesNothing() throws InterruptedException {
    TierwiseEvaluators evaluators = start(httpSource(), null);
    server.publish(bundleB, versionB);

    Thread.sleep(300);

    assertEquals(versionA, evaluators.bundleVersion());
    assertEquals(1, server.requests().size());
  }

  @Test
  void anObserverThatThrowsDoesNotBreakTheEvaluators() {
    TierwiseObserver failing = new TierwiseObserver() {
      @Override
      public void onBundleActivated(String bundleVersion) {
        throw new IllegalStateException("observer failure");
      }

      @Override
      public void onRefreshFailure() {
        throw new IllegalStateException("observer failure");
      }
    };
    TierwiseEvaluators evaluators = register(
        TierwiseEvaluators.start(httpSource(), null, options, Duration.ZERO, THRESHOLD, failing, clock));
    server.failWith(500);

    assertEquals(RefreshResult.FAILED, evaluators.refresh());
    assertEquals(10L, limit(evaluators));
  }

  @Test
  void aFileSourceIsRefreshedWhenTheFileChanges(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) throws IOException {
    java.nio.file.Path file = java.nio.file.Files.write(dir.resolve("bundle.json"), bundleA);
    TierwiseEvaluators evaluators = start(new FileBundleSource(file), null);
    assertEquals(RefreshResult.UNCHANGED, evaluators.refresh());

    java.nio.file.Files.write(file, bundleB);
    java.nio.file.Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));

    assertEquals(RefreshResult.SWAPPED, evaluators.refresh());
    assertEquals(20L, limit(evaluators));
  }

  @Test
  void aCorruptFileKeepsFailingInsteadOfBeingConfirmedOnTheNextPoll(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
      throws IOException {
    java.nio.file.Path file = java.nio.file.Files.write(dir.resolve("bundle.json"), bundleA);
    TierwiseEvaluators evaluators = start(new FileBundleSource(file), null);
    java.nio.file.Files.write(file, "corrupt".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    java.nio.file.Files.setLastModifiedTime(file, java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() + 5_000));
    clock.advance(THRESHOLD.plusSeconds(1));

    assertEquals(RefreshResult.FAILED, evaluators.refresh());
    assertEquals(RefreshResult.FAILED, evaluators.refresh(), "the second poll must not mistake the corrupt file for the bundle in use");

    assertEquals(versionA, evaluators.bundleVersion());
    assertTrue(evaluators.lastFailure().isPresent());
    assertTrue(evaluators.isStale(), "staleness is not reset by a bundle that never loaded");
    assertEquals(2, observer.refreshFailures.get());
  }

  @Test
  void aSnapshotStartIsNotConfirmedByAPrimaryFileThatNeverLoaded(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir)
      throws IOException {
    java.nio.file.Path file = java.nio.file.Files.write(dir.resolve("bundle.json"), "corrupt".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    TierwiseEvaluators evaluators = start(new FileBundleSource(file), snapshotOf(bundleA));
    assertTrue(evaluators.isUsingSnapshot());

    assertEquals(RefreshResult.FAILED, evaluators.refresh());

    assertTrue(evaluators.isUsingSnapshot());
    assertTrue(evaluators.lastFailure().isPresent());
  }

  @Test
  void httpSourceUriIsNotLeakedAsAQuery() {
    BundleSource source = new HttpBundleSource(
        URI.create(server.latestUrl() + "?token=secret"), Duration.ofSeconds(1), Duration.ofSeconds(2), Map.of());
    server.stop();

    BundleLoadException e = assertThrows(BundleLoadException.class, () -> start(source, null));

    assertFalse(e.getMessage().contains("secret"), e.getMessage());
  }
}
