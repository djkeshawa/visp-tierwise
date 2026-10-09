package io.github.tierwise.core.internal.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.LevelValueProvider;
import io.github.tierwise.core.Tierwise;
import io.github.tierwise.core.TierwiseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Create evaluator: EVAL-4 to EVAL-6, EVAL-15, EVAL-26 to EVAL-28, SVC-14, SVC-38, SVC-48. */
class EvaluatorCreationTest {

  private final TestModels.Builder builder = TestModels.standard();
  private static final LevelValueProvider EMPTY = node -> Map.of();

  private String codeOf(EvaluatorOptions options) {
    return assertThrows(TierwiseException.class, () -> Tierwise.evaluator(builder.loaded(), options)).code();
  }

  private static EvaluatorOptions.Builder both() {
    return EvaluatorOptions.builder().provider("account", EMPTY, Duration.ofSeconds(1))
        .provider("seat", EMPTY, Duration.ofSeconds(1));
  }

  @Test
  void EVAL_5_createEvaluatorIsSynchronousAndReturnsTheEvaluatorDirectly() {
    Evaluator evaluator = Tierwise.evaluator(builder.loaded(), both().build());
    assertNotNull(evaluator);
  }

  @Test
  void EVAL_6_createEvaluatorCallsNoProvider() {
    List<String> calls = new ArrayList<>();
    LevelValueProvider recording = node -> {
      calls.add(node);
      return Map.of("k.all", false);
    };
    Tierwise.evaluator(builder.loaded(), EvaluatorOptions.builder().provider("account", recording, Duration.ofSeconds(1))
        .provider("seat", recording, Duration.ofSeconds(1)).build());
    assertTrue(calls.isEmpty());
  }

  @Test
  void EVAL_28_anOmittedOptionsArgumentCountsAsEmptyOptions() {
    TestModels.Builder fileOnly = new TestModels.Builder();
    fileOnly.hierarchy = new io.github.tierwise.core.internal.model.Hierarchy(List.of("global", "division"), List.of(),
        Map.of("division", io.github.tierwise.core.internal.model.ScopeDef.openCatalog(
            io.github.tierwise.core.internal.model.ScopeDef.ValuesSource.FILES)));
    assertNotNull(Tierwise.evaluator(fileOnly.loaded(), null));
    assertNotNull(Tierwise.evaluator(fileOnly.loaded(), EvaluatorOptions.none()));
    assertEquals(Code.E_PROVIDER_MISSING, assertThrows(TierwiseException.class,
        () -> Tierwise.evaluator(builder.loaded(), null)).code());
  }

  @Test
  void EVAL_28_aTimeoutThatIsNotAPositiveWholeNumberOfMillisecondsRaisesEvaluatorOptions() {
    for (Duration invalid : new Duration[] {Duration.ZERO, Duration.ofMillis(-5), Duration.ofNanos(1),
        Duration.ofNanos(1_500_000), Duration.ofNanos(999_999)}) {
      EvaluatorOptions options = EvaluatorOptions.builder().provider("account", EMPTY, invalid)
          .provider("seat", EMPTY, Duration.ofSeconds(1)).build();
      assertEquals(Code.E_EVALUATOR_OPTIONS, codeOf(options), invalid.toString());
    }
  }

  @Test
  void SVC_38_aPositiveWholeMillisecondTimeoutIsAccepted() {
    for (Duration valid : new Duration[] {Duration.ofMillis(1), Duration.ofSeconds(30), Duration.ofDays(1_000_000_000L),
        Duration.ofNanos(2_000_000)}) {
      Tierwise.evaluator(builder.loaded(), EvaluatorOptions.builder().provider("account", EMPTY, valid)
          .provider("seat", EMPTY, Duration.ofSeconds(1)).build());
    }
  }

  @Test
  void SVC_41_aTimeoutTooLargeForALongOfMillisecondsIsStillAPositiveTimeout() {
    Duration huge = Duration.ofSeconds(Long.MAX_VALUE);
    assertNotNull(Tierwise.evaluator(builder.loaded(), EvaluatorOptions.builder().provider("account", EMPTY, huge)
        .provider("seat", EMPTY, Duration.ofSeconds(1)).build()));
  }

  @Test
  void SVC_48_aProviderWithNoTimeoutRaisesEvaluatorOptionsAndNoDefaultIsSupplied() {
    EvaluatorOptions options = EvaluatorOptions.builder().provider("account", EMPTY, null)
        .provider("seat", EMPTY, Duration.ofSeconds(1)).build();
    assertEquals(Code.E_EVALUATOR_OPTIONS, codeOf(options));
  }

  @Test
  void EVAL_27_aLevelRegisteredTwiceRaisesEvaluatorOptions() {
    EvaluatorOptions options = both().provider("account", EMPTY, Duration.ofSeconds(1)).build();
    assertEquals(Code.E_EVALUATOR_OPTIONS, codeOf(options));
  }

  @Test
  void EVAL_27_aLevelTheBundleDoesNotDeclareRaisesEvaluatorOptions() {
    assertEquals(Code.E_EVALUATOR_OPTIONS, codeOf(both().provider("warehouse", EMPTY, Duration.ofSeconds(1)).build()));
    assertEquals(Code.E_EVALUATOR_OPTIONS, codeOf(both().provider("Account", EMPTY, Duration.ofSeconds(1)).build()));
  }

  @Test
  void EVAL_27_aDeclaredLevelThatIsNotServiceSourcedRaisesEvaluatorOptions() {
    assertEquals(Code.E_EVALUATOR_OPTIONS, codeOf(both().provider("division", EMPTY, Duration.ofSeconds(1)).build()));
    assertEquals(Code.E_EVALUATOR_OPTIONS, codeOf(both().provider("global", EMPTY, Duration.ofSeconds(1)).build()));
    assertEquals(Code.E_EVALUATOR_OPTIONS, codeOf(both().provider("seller", EMPTY, Duration.ofSeconds(1)).build()));
  }

  @Test
  void EVAL_26_aServiceSourcedLevelWithoutAProviderRaisesProviderMissing() {
    assertEquals(Code.E_PROVIDER_MISSING, codeOf(EvaluatorOptions.none()));
    assertEquals(Code.E_PROVIDER_MISSING, codeOf(EvaluatorOptions.builder()
        .provider("account", EMPTY, Duration.ofSeconds(1)).build()));
  }

  @Test
  void SVC_14_aProviderIsRequiredEvenForALevelAtWhichNoKeyIsSettable() {
    // "seat" has the key k.seat, so build a model where no key is settable at it.
    builder.keys.remove("k.seat");
    assertEquals(Code.E_PROVIDER_MISSING, codeOf(EvaluatorOptions.builder()
        .provider("account", EMPTY, Duration.ofSeconds(1)).build()));
  }

  @Test
  void EVAL_15_theOptionsAreCheckedBeforeTheRegistrationsAndTheRegistrationsBeforeTheProviderCheck() {
    // a bad timeout and an undeclared level: both E_EVALUATOR_OPTIONS; a missing provider alone: E_PROVIDER_MISSING
    EvaluatorOptions misspelt = EvaluatorOptions.builder().provider("acount", EMPTY, Duration.ofSeconds(1)).build();
    assertEquals(Code.E_EVALUATOR_OPTIONS, codeOf(misspelt), "a misspelt level is E_EVALUATOR_OPTIONS, not only a missing provider");
    EvaluatorOptions filesLevel = EvaluatorOptions.builder().provider("division", EMPTY, Duration.ofSeconds(1)).build();
    assertEquals(Code.E_EVALUATOR_OPTIONS, codeOf(filesLevel));
    EvaluatorOptions timeoutFirst = EvaluatorOptions.builder().provider("division", EMPTY, Duration.ZERO).build();
    assertEquals(Code.E_EVALUATOR_OPTIONS, codeOf(timeoutFirst));
  }

  @Test
  void SVC_13_aBundleOfFilesOnlyNeedsNoProviderAtAll() {
    TestModels.Builder fileOnly = new TestModels.Builder();
    fileOnly.hierarchy = new io.github.tierwise.core.internal.model.Hierarchy(List.of("global"), List.of(), Map.of());
    assertNotNull(Tierwise.evaluator(fileOnly.loaded(), EvaluatorOptions.none()));
  }
}
