package io.github.visplabs.tierwise.core.internal.eval;

import static io.github.visplabs.tierwise.core.internal.eval.TestModels.mapOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.EvaluationEvent;
import io.github.visplabs.tierwise.core.EvaluationEvent.Type;
import io.github.visplabs.tierwise.core.Evaluator;
import io.github.visplabs.tierwise.core.EvaluatorOptions;
import io.github.visplabs.tierwise.core.FactSet;
import io.github.visplabs.tierwise.core.LevelValueProvider;
import io.github.visplabs.tierwise.core.Resolved;
import io.github.visplabs.tierwise.core.Tierwise;
import io.github.visplabs.tierwise.core.TierwiseException;
import io.github.visplabs.tierwise.core.internal.eval.TestModels.CountingProvider;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Listener events: SVC-43 to SVC-47 and EVAL-41 to EVAL-45, EVAL-53. */
class ListenerEventsTest {

  private static final Context ACCOUNT = Context.builder().level("account", "a1").build();

  private final TestModels.Builder builder = TestModels.standard();
  private final List<EvaluationEvent> events = Collections.synchronizedList(new ArrayList<>());

  private Evaluator evaluator(LevelValueProvider account) {
    events.clear();
    Evaluator evaluator = TestModels.evaluator(builder, account, new CountingProvider(Map.of()), events::add);
    events.clear();
    return evaluator;
  }

  private static LevelValueProvider returning(Map<String, Object> values) {
    return node -> values;
  }

  private List<Type> types() {
    return events.stream().map(EvaluationEvent::type).toList();
  }

  @Test
  void EVAL_43_createEvaluatorEmitsOneLoadEventBeforeItReturns() {
    List<EvaluationEvent> seen = new ArrayList<>();
    EvaluatorOptions options = EvaluatorOptions.builder().provider("account", node -> Map.of(), Duration.ofSeconds(1))
        .provider("seat", node -> Map.of(), Duration.ofSeconds(1)).listener(seen::add).build();
    Tierwise.evaluator(builder.loaded(), options);
    assertEquals(1, seen.size());
    assertEquals(Type.BUNDLE_LOADED, seen.get(0).type());
    assertEquals(TestModels.VERSION, seen.get(0).bundleVersion());
    assertEquals(Optional.empty(), seen.get(0).level());
  }

  @Test
  void EVAL_43_createEvaluatorEmitsNoEventWhenItRaisesAnError() {
    List<EvaluationEvent> seen = new ArrayList<>();
    EvaluatorOptions missing = EvaluatorOptions.builder().provider("account", node -> Map.of(), Duration.ofSeconds(1))
        .listener(seen::add).build();
    assertThrows(TierwiseException.class, () -> Tierwise.evaluator(builder.loaded(), missing));
    EvaluatorOptions badTimeout = EvaluatorOptions.builder().provider("account", node -> Map.of(), Duration.ZERO)
        .listener(seen::add).build();
    assertThrows(TierwiseException.class, () -> Tierwise.evaluator(builder.loaded(), badTimeout));
    assertTrue(seen.isEmpty());
  }

  @Test
  void EVAL_41_andSVC_43_aProviderFailureIsReportedOncePerAffectedKey() {
    Evaluator evaluator = evaluator(TestModels.failing());
    ((EvaluatorImpl) evaluator).resolveAll(ACCOUNT, List.of("k.all", "k.min", "k.note"));
    assertEquals(List.of(Type.SOURCE_UNAVAILABLE, Type.SOURCE_UNAVAILABLE), types());
    assertEquals(List.of("k.all", "k.min"), events.stream().map(e -> e.key().orElseThrow()).toList());
    EvaluationEvent first = events.get(0);
    assertEquals(Optional.of("account"), first.level());
    assertEquals(Optional.of("a1"), first.node());
    assertEquals(TestModels.VERSION, first.bundleVersion());
  }

  @Test
  void SVC_44_aTimeoutIsReportedAsASourceFailure() {
    events.clear();
    LevelValueProvider slow = node -> {
      Thread.sleep(2_000);
      return Map.of();
    };
    EvaluatorOptions options = EvaluatorOptions.builder().provider("account", slow, Duration.ofMillis(50))
        .provider("seat", node -> Map.of(), Duration.ofSeconds(1)).listener(events::add).build();
    Evaluator evaluator = Tierwise.evaluator(builder.loaded(), options);
    events.clear();
    evaluator.resolve(ACCOUNT, "k.all");
    assertEquals(List.of(Type.SOURCE_UNAVAILABLE), types());
  }

  @Test
  void SVC_43_anInvalidValueIsReportedAsASourceFailureForItsKeyOnly() {
    Evaluator evaluator = evaluator(returning(mapOf("k.all", "yes", "k.min", 5L)));
    ((EvaluatorImpl) evaluator).resolveAll(ACCOUNT, List.of("k.all", "k.min"));
    assertEquals(List.of(Type.SOURCE_INVALID), types());
    assertEquals(Optional.of("k.all"), events.get(0).key());
  }

  @Test
  void SVC_45_theUnknownKeysReportedAreExactlyTheEntriesForUndeclaredKeys() {
    Evaluator evaluator = evaluator(returning(mapOf("k.all", true, "zz.unknown", 1L, "aa.unknown", null, "k.min", 5L)));
    evaluator.resolve(ACCOUNT, "k.all");
    assertEquals(List.of(Type.UNKNOWN_KEY, Type.UNKNOWN_KEY), types());
    assertEquals(List.of("aa.unknown", "zz.unknown"), events.stream().map(e -> e.key().orElseThrow()).toList());
  }

  @Test
  void SVC_46_aKeyTheResultOmitsIsReportedNeitherAsAFailureNorAsUnknown() {
    Evaluator evaluator = evaluator(returning(Map.of()));
    ((EvaluatorImpl) evaluator).resolveAll(ACCOUNT, List.of("k.all", "k.min"));
    assertTrue(events.isEmpty());
  }

  @Test
  void SVC_47_anIgnoredEntryIsReportedAsNeitherAFailureNorAnUnknownKey() {
    Evaluator evaluator = evaluator(returning(mapOf("k.all", true, "k.note", "x")));
    ((EvaluatorImpl) evaluator).resolveAll(ACCOUNT, List.of("k.all", "k.note"));
    assertEquals(List.of(Type.IGNORED_ENTRY), types());
    assertEquals(Optional.of("k.note"), events.get(0).key());
  }

  @Test
  void SVC_28_aDeclaredKeyThatIsNotBeingResolvedIsNotReported() {
    Evaluator evaluator = evaluator(returning(mapOf("k.all", true, "k.note", "x", "k.min", "bad")));
    evaluator.resolve(ACCOUNT, "k.all");
    assertTrue(events.isEmpty());
  }

  @Test
  void EVAL_42_decideAndTheSynchronousVariantsReportNoProviderEvent() {
    Evaluator evaluator = evaluator(TestModels.failing());
    evaluator.decide(ACCOUNT, "f.gate", FactSet.empty());
    evaluator.resolveLocal(Context.empty(), "k.all");
    assertThrows(TierwiseException.class, () -> evaluator.resolveLocal(ACCOUNT, "k.all"));
    assertTrue(events.isEmpty());
  }

  @Test
  void EVAL_45_noRaisedErrorAndNoSkippedScopeIsReported() {
    Evaluator evaluator = evaluator(TestModels.failing());
    assertThrows(TierwiseException.class, () -> evaluator.resolve(Context.builder().level("division", "west").build(), "k.all"));
    assertThrows(TierwiseException.class, () -> evaluator.resolve(Context.empty(), "no.such.key"));
    Resolved skipped = evaluator.resolve(Context.builder().level("site", "s9").build(), "k.all");
    assertEquals(1, skipped.skippedScopes().size());
    assertTrue(events.isEmpty());
  }

  @Test
  void EVAL_44_aListenerThatThrowsChangesNoResultAndRaisesNoError() {
    Resolved quiet = TestModels.evaluator(builder, TestModels.failing(), new CountingProvider(Map.of()), null)
        .resolve(ACCOUNT, "k.all");
    EvaluatorOptions options = EvaluatorOptions.builder().provider("account", TestModels.failing(), Duration.ofSeconds(1))
        .provider("seat", node -> Map.of(), Duration.ofSeconds(1))
        .listener(event -> {
          throw new IllegalStateException("listener failure");
        }).build();
    Evaluator evaluator = Tierwise.evaluator(builder.loaded(), options);
    assertEquals(quiet.toJson(), evaluator.resolve(ACCOUNT, "k.all").toJson());
  }

  @Test
  void EVAL_44_eventsAreDeliveredBeforeTheOperationReturns() {
    Evaluator evaluator = evaluator(TestModels.failing());
    evaluator.resolve(ACCOUNT, "k.all");
    assertEquals(1, events.size());
  }

  @Test
  void EVAL_53_aListenerToleratesConcurrentCallsAndEveryEventCarriesTypeAndBundleVersion() throws Exception {
    AtomicInteger inFlightMax = new AtomicInteger();
    AtomicInteger inFlight = new AtomicInteger();
    CountDownLatch start = new CountDownLatch(1);
    List<EvaluationEvent> seen = Collections.synchronizedList(new ArrayList<>());
    EvaluatorOptions options = EvaluatorOptions.builder().provider("account", TestModels.failing(), Duration.ofSeconds(5))
        .provider("seat", node -> Map.of(), Duration.ofSeconds(5)).listener(event -> {
          int now = inFlight.incrementAndGet();
          inFlightMax.accumulateAndGet(now, Math::max);
          Thread.yield();
          seen.add(event);
          inFlight.decrementAndGet();
        }).build();
    Evaluator evaluator = Tierwise.evaluator(builder.loaded(), options);
    seen.clear();
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Future<?>> done = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        done.add(pool.submit(() -> {
          start.await();
          for (int j = 0; j < 20; j++) {
            evaluator.resolve(ACCOUNT, "k.all");
          }
          return null;
        }));
      }
      start.countDown();
      for (Future<?> f : done) {
        f.get();
      }
    } finally {
      pool.shutdownNow();
    }
    assertEquals(160, seen.size());
    assertTrue(seen.stream().allMatch(e -> e.type() == Type.SOURCE_UNAVAILABLE && e.bundleVersion().equals(TestModels.VERSION)));
  }
}
