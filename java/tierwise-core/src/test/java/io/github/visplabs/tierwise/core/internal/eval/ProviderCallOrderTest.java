package io.github.visplabs.tierwise.core.internal.eval;

import static io.github.visplabs.tierwise.core.internal.eval.TestModels.mapOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.EvaluationEvent;
import io.github.visplabs.tierwise.core.EvaluatorOptions;
import io.github.visplabs.tierwise.core.LevelValueProvider;
import io.github.visplabs.tierwise.core.Resolved;
import io.github.visplabs.tierwise.core.Tierwise;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * SVC-21: a result does not depend on the order or the concurrency of the provider calls of one evaluation.
 *
 * <p>Two service-sourced levels are called in one evaluation call. Each property draws an outcome for each
 * provider and a delay for each, so the calls finish in either order, and compares the results and the
 * listener events with those of the same outcomes returned at once. The same request is also run from
 * several threads at the same time.
 */
class ProviderCallOrderTest {

  private static final Context BOTH = Context.builder().level("account", "a1").level("seat", "t1").build();
  private static final List<String> KEYS = List.of("k.both", "k.cap", "k.all", "k.seat");
  private static final Duration TIMEOUT = Duration.ofSeconds(5);

  /** What a provider does in one call. */
  enum Outcome {
    NARROWS, IDENTITY, INVALID, OMITS, THROWS, NOT_A_MAPPING
  }

  private static TestModels.Builder model() {
    TestModels.Builder builder = TestModels.standard();
    List<String> both = List.of("global", "account", "seat");
    builder.key("k.both", TestModels.key(TypeDecl.simple("boolean"), "all", null, both, null, "deny"));
    builder.key("k.cap", TestModels.key(TypeDecl.integer(1L, 500L), "min", null, both, null, "skip"));
    builder.value("global", "", "k.cap", 400L);
    return builder;
  }

  private static LevelValueProvider provider(Outcome outcome, long delayMillis, long cap) {
    return node -> {
      if (delayMillis > 0) {
        Thread.sleep(delayMillis);
      }
      return switch (outcome) {
        case NARROWS -> mapOf("k.both", false, "k.cap", cap, "k.all", false, "k.seat", false);
        case IDENTITY -> mapOf("k.both", true, "k.cap", 500L, "k.all", true, "k.seat", true);
        case INVALID -> mapOf("k.both", "no", "k.cap", 0L, "k.all", 1L, "k.seat", null);
        case OMITS -> mapOf();
        case THROWS -> throw new java.io.IOException("unavailable");
        case NOT_A_MAPPING -> null;
      };
    };
  }

  /** The results of one evaluation call, with the listener events it reported in a fixed order. */
  private record Run(List<Map<String, Object>> results, List<String> events) {
  }

  private static Run run(Outcome account, long accountDelay, Outcome seat, long seatDelay) {
    List<EvaluationEvent> seen = Collections.synchronizedList(new ArrayList<>());
    EvaluatorOptions options = EvaluatorOptions.builder()
        .provider("account", provider(account, accountDelay, 50L), TIMEOUT)
        .provider("seat", provider(seat, seatDelay, 20L), TIMEOUT)
        .listener(seen::add)
        .build();
    EvaluatorImpl evaluator = (EvaluatorImpl) Tierwise.evaluator(model().loaded(), options);
    seen.clear();
    List<Map<String, Object>> results = evaluator.resolveAll(BOTH, KEYS).stream().map(Resolved::toJson).toList();
    List<String> events = seen.stream()
        .map(e -> e.type() + " " + e.level().orElse("") + " " + e.node().orElse("") + " " + e.key().orElse(""))
        .sorted(Comparator.naturalOrder())
        .toList();
    return new Run(results, events);
  }

  @Property(tries = 40)
  void SVC_21_theResultDoesNotDependOnTheOrderInWhichTheProviderCallsFinish(
      @ForAll("outcomes") Outcome account, @ForAll("delays") long accountDelay,
      @ForAll("outcomes") Outcome seat, @ForAll("delays") long seatDelay) {
    Run immediate = run(account, 0, seat, 0);
    Run accountLast = run(account, accountDelay + 15, seat, seatDelay);
    Run seatLast = run(account, accountDelay, seat, seatDelay + 15);
    assertEquals(immediate, accountLast, "the account provider finishes last");
    assertEquals(immediate, seatLast, "the seat provider finishes last");
  }

  @Property(tries = 10)
  void SVC_21_theResultDoesNotDependOnConcurrentEvaluationCalls(
      @ForAll("outcomes") Outcome account, @ForAll("outcomes") Outcome seat) throws Exception {
    Run alone = run(account, 0, seat, 0);
    EvaluatorOptions options = EvaluatorOptions.builder()
        .provider("account", provider(account, 3, 50L), TIMEOUT)
        .provider("seat", provider(seat, 1, 20L), TIMEOUT)
        .build();
    EvaluatorImpl evaluator = (EvaluatorImpl) Tierwise.evaluator(model().loaded(), options);
    ExecutorService pool = Executors.newFixedThreadPool(6);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<List<Map<String, Object>>>> running = new ArrayList<>();
      for (int i = 0; i < 6; i++) {
        running.add(pool.submit(() -> {
          start.await();
          return evaluator.resolveAll(BOTH, KEYS).stream().map(Resolved::toJson).toList();
        }));
      }
      start.countDown();
      for (Future<List<Map<String, Object>>> call : running) {
        assertEquals(alone.results(), call.get());
      }
    } finally {
      pool.shutdownNow();
    }
  }

  @Provide
  Arbitrary<Outcome> outcomes() {
    return Arbitraries.of(Outcome.class);
  }

  @Provide
  Arbitrary<Long> delays() {
    return Arbitraries.longs().between(0L, 10L);
  }
}
