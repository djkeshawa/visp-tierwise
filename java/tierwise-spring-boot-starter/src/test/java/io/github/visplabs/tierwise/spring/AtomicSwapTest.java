package io.github.visplabs.tierwise.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.EvaluatorOptions;
import io.github.visplabs.tierwise.core.Resolved;
import io.github.visplabs.tierwise.core.Tierwise;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The swap is atomic: evaluations in flight finish on the bundle they started with. */
class AtomicSwapTest {

  private final ExecutorService threads = Executors.newCachedThreadPool();

  private byte[] bundleA;
  private byte[] bundleB;
  private String versionA;
  private String versionB;
  private StubBundleServer server;
  private TierwiseEvaluators evaluators;
  private volatile CountDownLatch providerEntered = new CountDownLatch(1);
  private volatile CountDownLatch releaseProvider = new CountDownLatch(0);

  @BeforeEach
  void setUp() throws IOException {
    bundleA = TestBundles.limit(10);
    bundleB = TestBundles.limit(20);
    versionA = Tierwise.loadBundle(bundleA).bundleVersion();
    versionB = Tierwise.loadBundle(bundleB).bundleVersion();
    server = new StubBundleServer(bundleA, versionA);
    EvaluatorOptions options = EvaluatorOptions.builder()
        .provider("tenant", nodeId -> {
          providerEntered.countDown();
          assertTrue(releaseProvider.await(20, TimeUnit.SECONDS));
          return Map.of("limit.max_items", 5);
        }, Duration.ofSeconds(30))
        .build();
    evaluators = TierwiseEvaluators.start(
        BundleSources.forServer(server.baseUrl(), new TierwiseProperties().getHttp()),
        null, options, Duration.ZERO, Duration.ofMinutes(5), TierwiseObserver.NOOP, Clock.systemUTC());
  }

  @AfterEach
  void tearDown() {
    releaseProvider.countDown();
    threads.shutdownNow();
    evaluators.close();
    server.close();
  }

  @Test
  void everyConcurrentEvaluationSeesOneBundleCompletely() throws Exception {
    Resolver resolver = new DefaultResolver(evaluators);
    AtomicBoolean running = new AtomicBoolean(true);
    AtomicLong seenA = new AtomicLong();
    AtomicLong seenB = new AtomicLong();
    List<Future<Void>> readers = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      readers.add(threads.submit((Callable<Void>) () -> {
        while (running.get()) {
          Resolved resolved = resolver.resolve(Context.empty(), "limit.max_items");
          if (resolved.bundleVersion().equals(versionA)) {
            assertEquals(10L, resolved.value(), "a result of bundle A holds A's value");
            seenA.incrementAndGet();
          } else {
            assertEquals(versionB, resolved.bundleVersion());
            assertEquals(20L, resolved.value(), "a result of bundle B holds B's value");
            seenB.incrementAndGet();
          }
        }
        return null;
      }));
    }

    for (int swap = 0; swap < 100; swap++) {
      boolean toB = swap % 2 == 0;
      server.publish(toB ? bundleB : bundleA, toB ? versionB : versionA);
      assertEquals(RefreshResult.SWAPPED, evaluators.refresh());
    }
    running.set(false);
    for (Future<Void> reader : readers) {
      reader.get(30, TimeUnit.SECONDS);
    }

    assertTrue(seenA.get() > 0 && seenB.get() > 0, "readers saw both bundles: " + seenA + " / " + seenB);
  }

  @Test
  void anEvaluationInFlightFinishesOnTheOldBundle() throws Exception {
    releaseProvider = new CountDownLatch(1);
    Resolver resolver = new DefaultResolver(evaluators);
    Future<Resolved> inFlight = threads.submit(
        () -> resolver.resolve(Context.builder().level("tenant", "t-1").build(), "limit.max_items"));
    assertTrue(providerEntered.await(10, TimeUnit.SECONDS), "the call is inside the provider");

    server.publish(bundleB, versionB);
    assertEquals(RefreshResult.SWAPPED, evaluators.refresh());
    Resolved after = resolver.resolve(Context.empty(), "limit.max_items");
    releaseProvider.countDown();
    Resolved finished = inFlight.get(10, TimeUnit.SECONDS);

    assertEquals(versionB, after.bundleVersion());
    assertEquals(20L, after.value());
    assertEquals(versionA, finished.bundleVersion(), "the call that started on A finishes on A");
    assertEquals(5L, finished.value(), "A's root value 10 narrowed by the provider's 5");
  }

  @Test
  void concurrentRefreshesSwapExactlyOnce() throws Exception {
    server.publish(bundleB, versionB);
    int callers = 6;
    CountDownLatch go = new CountDownLatch(1);
    List<Future<RefreshResult>> results = new ArrayList<>();
    for (int i = 0; i < callers; i++) {
      results.add(threads.submit(() -> {
        go.await();
        return evaluators.refresh();
      }));
    }
    go.countDown();

    int swapped = 0;
    int unchanged = 0;
    for (Future<RefreshResult> result : results) {
      switch (result.get(20, TimeUnit.SECONDS)) {
        case SWAPPED -> swapped++;
        case UNCHANGED -> unchanged++;
        case FAILED -> throw new AssertionError("a refresh failed");
      }
    }

    assertEquals(1, swapped);
    assertEquals(callers - 1, unchanged);
    assertEquals(versionB, evaluators.bundleVersion());
  }
}
