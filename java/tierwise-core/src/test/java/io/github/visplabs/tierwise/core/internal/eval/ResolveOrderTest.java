package io.github.visplabs.tierwise.core.internal.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.Evaluator;
import io.github.visplabs.tierwise.core.FactSet;
import io.github.visplabs.tierwise.core.TierwiseException;
import io.github.visplabs.tierwise.core.internal.eval.TestModels.CountingProvider;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Check order and the synchronous variant: EVAL-9 to EVAL-12, EVAL-16 to EVAL-20, EVAL-29 to EVAL-37. */
class ResolveOrderTest {

  private final TestModels.Builder builder = TestModels.standard();
  private final CountingProvider account = new CountingProvider(Map.of("a1", Map.of("k.all", false)));
  private final CountingProvider seat = new CountingProvider(Map.of());
  private final Evaluator evaluator = TestModels.evaluator(builder, account, seat, null);

  private static String code(Runnable action) {
    return assertThrows(TierwiseException.class, action::run).code();
  }

  private static final Context ACCOUNT = Context.builder().level("account", "a1").build();

  @Test
  void EVAL_10_theSynchronousVariantReturnsTheSameResultWhenNoProviderCallIsNeeded() {
    builder.value("global", "", "k.label", "root");
    Evaluator evaluator = TestModels.evaluator(builder, account, seat, null);
    assertEquals(evaluator.resolve(Context.empty(), "k.label").toJson(), evaluator.resolveLocal(Context.empty(), "k.label").toJson());
    Context north = Context.builder().level("division", "north").build();
    assertEquals(evaluator.resolve(north, "k.note").toJson(), evaluator.resolveLocal(north, "k.note").toJson());
    Context skippedOnly = Context.builder().level("site", "s9").build();
    assertEquals(evaluator.resolve(skippedOnly, "k.all").toJson(), evaluator.resolveLocal(skippedOnly, "k.all").toJson());
  }

  @Test
  void EVAL_11_andEVAL_29_theSynchronousVariantRaisesProviderRequiredInsteadOfCalling() {
    assertEquals(Code.E_PROVIDER_REQUIRED, code(() -> evaluator.resolveLocal(ACCOUNT, "k.all")));
    assertTrue(account.calls().isEmpty());
    assertTrue(seat.calls().isEmpty());
  }

  @Test
  void SVC_42_aCallIsNeededOnlyWhenAnAdmittedServiceLevelHasASettableKey() {
    evaluator.resolveLocal(ACCOUNT, "k.note");
    evaluator.resolveLocal(Context.empty(), "k.all");
    evaluator.resolveLocal(Context.builder().level("seat", "t1").build(), "k.all");
    assertEquals(Code.E_PROVIDER_REQUIRED,
        code(() -> evaluator.resolveLocal(Context.builder().level("seat", "t1").build(), "k.seat")));
  }

  @Test
  void EVAL_16_contextValidationPrecedesKeyLookupAndKeyLookupPrecedesTheProviderCheck() {
    Context unknownScope = Context.builder().level("warehouse", "x").build();
    assertEquals(Code.E_UNKNOWN_SCOPE, code(() -> evaluator.resolve(unknownScope, "no.such.key")));
    assertEquals(Code.E_UNKNOWN_SCOPE, code(() -> evaluator.resolveLocal(unknownScope, "no.such.key")));
    assertEquals(Code.E_UNKNOWN_KEY, code(() -> evaluator.resolveLocal(ACCOUNT, "no.such.key")));
    assertEquals(Code.E_UNKNOWN_KEY, code(() -> evaluator.resolve(ACCOUNT, "no.such.key")));
    assertTrue(account.calls().isEmpty(), "nothing is called when a check fails (EVAL-20)");
  }

  @Test
  void CTX_10_noProviderIsCalledBeforeContextValidationCompletes() {
    Context invalid = Context.builder().level("account", "a1").level("division", "west").build();
    assertEquals(Code.E_UNKNOWN_NODE, code(() -> evaluator.resolve(invalid, "k.all")));
    assertTrue(account.calls().isEmpty());
  }

  @Test
  void EVAL_37_anOmittedOrEmptyKeyIsAnUnknownKey() {
    assertEquals(Code.E_UNKNOWN_KEY, code(() -> evaluator.resolve(Context.empty(), null)));
    assertEquals(Code.E_UNKNOWN_KEY, code(() -> evaluator.resolve(Context.empty(), "")));
    assertEquals(Code.E_UNKNOWN_KEY, code(() -> evaluator.resolveLocal(Context.empty(), null)));
    assertEquals(Code.E_UNKNOWN_KEY, code(() -> evaluator.resolveLocal(Context.empty(), "")));
    assertEquals(Code.E_UNKNOWN_KEY, code(() -> evaluator.resolve(Context.empty(), "K.ALL")));
  }

  @Test
  void EVAL_37_anOmittedOrEmptyFeatureIsAnUnknownFeature() {
    assertEquals(Code.E_UNKNOWN_FEATURE, code(() -> evaluator.decide(Context.empty(), null, FactSet.empty())));
    assertEquals(Code.E_UNKNOWN_FEATURE, code(() -> evaluator.decide(Context.empty(), "", FactSet.empty())));
    assertEquals(Code.E_UNKNOWN_FEATURE, code(() -> evaluator.decide(Context.empty(), "F.GATE", FactSet.empty())));
  }

  @Test
  void EVAL_37_aNonStringKeyCannotBeExpressedInJavaSoTheTypeSystemEnforcesIt() throws Exception {
    assertEquals(String.class, Evaluator.class.getMethod("resolve", Context.class, String.class).getParameterTypes()[1]);
    assertEquals(String.class,
        Evaluator.class.getMethod("decide", Context.class, String.class, FactSet.class).getParameterTypes()[1]);
  }

  @Test
  void EVAL_54_anOmittedContextIsEmptyAndAnOmittedFactSetIsEmpty() {
    assertEquals(evaluator.resolve(Context.empty(), "k.all").toJson(), evaluator.resolve(null, "k.all").toJson());
    assertEquals(evaluator.decide(Context.empty(), "f.gate", FactSet.empty()).toJson(),
        evaluator.decide(null, "f.gate", null).toJson());
  }

  @Test
  void EVAL_12_decideIsTheSynchronousFormAndCallsNoProvider() {
    evaluator.decide(ACCOUNT, "f.gate", FactSet.builder().fact("customer.active", true).build());
    assertTrue(account.calls().isEmpty());
  }

  @Test
  void EVAL_39_aSourceFailureIsNeverRaised() {
    Evaluator failing = TestModels.evaluator(builder, TestModels.failing(), TestModels.failing(), null);
    failing.resolve(ACCOUNT, "k.all");
  }

  @Test
  void EVAL_38_operationsRaiseOnlyTheCodesOfTheEvaluationCatalogue() {
    java.util.Set<String> allowed = new java.util.HashSet<>(Code.evaluation());
    allowed.removeAll(java.util.Set.of(Code.E_BUNDLE_INTEGRITY, Code.E_BUNDLE_UNSUPPORTED, Code.E_PROVIDER_MISSING,
        Code.E_EVALUATOR_OPTIONS));
    Context[] contexts = {Context.fromJson(null), Context.builder().level("global", "x").build(),
        Context.builder().level("warehouse", "x").build(), Context.builder().level("division", "west").build(),
        Context.builder().level("account", "").build(), Context.empty()};
    for (Context context : contexts) {
      for (String name : new String[] {null, "", "k.all", "no.such"}) {
        try {
          evaluator.resolve(context, name);
        } catch (TierwiseException e) {
          assertTrue(allowed.contains(e.code()), e.code());
        }
        try {
          evaluator.resolveLocal(context, name);
        } catch (TierwiseException e) {
          assertTrue(allowed.contains(e.code()), e.code());
        }
        try {
          evaluator.decide(context, name, FactSet.fromJson(Map.of("facts", Map.of("customer.active", "x"))));
        } catch (TierwiseException e) {
          assertTrue(allowed.contains(e.code()), e.code());
        }
      }
    }
  }

  @Test
  void EVAL_20_afterACheckFailsNoLaterCheckRunsAndNoSecondErrorIsRaised() {
    FactSet invalidFacts = FactSet.fromJson(Map.of("facts", Map.of("customer.active", "x")));
    assertEquals(Code.E_UNKNOWN_SCOPE,
        code(() -> evaluator.decide(Context.builder().level("warehouse", "x").build(), "no.such", invalidFacts)));
    assertEquals(Code.E_UNKNOWN_FEATURE, code(() -> evaluator.decide(Context.empty(), "no.such", invalidFacts)));
    assertEquals(Code.E_FACTS_INVALID, code(() -> evaluator.decide(Context.empty(), "f.gate", invalidFacts)));
  }

  @Test
  void FIND_9_anEvaluationErrorIsRaisedNotReturnedAsAFinding() {
    TierwiseException e = assertThrows(TierwiseException.class, () -> evaluator.resolve(Context.empty(), "no.such"));
    assertEquals(Code.E_UNKNOWN_KEY, e.code());
    assertTrue(e.getMessage().startsWith(Code.E_UNKNOWN_KEY));
  }
}
