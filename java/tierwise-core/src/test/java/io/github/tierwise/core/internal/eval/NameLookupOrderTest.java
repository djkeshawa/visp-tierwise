package io.github.tierwise.core.internal.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Context;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.TierwiseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.Test;

/**
 * EVAL-37: an omitted or empty key or feature is an unknown name, reported at the lookup step of EVAL-16 or
 * EVAL-17: after context validation, and before the provider check, any provider call and FactSet validation.
 */
class NameLookupOrderTest {

  private static final Context ACCOUNT = Context.builder().level("account", "a1").build();
  private static final Context UNKNOWN_SCOPE = Context.builder().level("warehouse", "w1").build();
  private static final Context UNKNOWN_NODE = Context.builder().level("division", "west").build();
  private static final Context ROOT = Context.builder().level("global", "").build();
  private static final FactSet INVALID_FACTS = FactSet.builder().fact("order.lines", "many").build();

  private final TestModels.CountingProvider account = new TestModels.CountingProvider(Map.of("a1", Map.of()));
  private final Evaluator evaluator = TestModels.evaluator(TestModels.standard(), account, node -> Map.of(), null);

  private static String code(Executable call) {
    return assertThrows(TierwiseException.class, call).code();
  }

  @Test
  void EVAL_37_contextValidationComesBeforeTheLookupOfAnOmittedOrEmptyName() {
    for (String name : Arrays.asList(null, "")) {
      assertEquals(Code.E_UNKNOWN_SCOPE, code(() -> evaluator.resolve(UNKNOWN_SCOPE, name)));
      assertEquals(Code.E_UNKNOWN_SCOPE, code(() -> evaluator.resolveLocal(UNKNOWN_SCOPE, name)));
      assertEquals(Code.E_UNKNOWN_NODE, code(() -> evaluator.resolve(UNKNOWN_NODE, name)));
      assertEquals(Code.E_CONTEXT_INVALID, code(() -> evaluator.resolve(ROOT, name)));
      assertEquals(Code.E_UNKNOWN_SCOPE, code(() -> evaluator.decide(UNKNOWN_SCOPE, name, INVALID_FACTS)));
      assertEquals(Code.E_UNKNOWN_NODE, code(() -> evaluator.decide(UNKNOWN_NODE, name, INVALID_FACTS)));
    }
  }

  @Test
  void EVAL_37_anOmittedOrEmptyKeyIsReportedBeforeTheProviderCheckAndAnyProviderCall() {
    for (String key : Arrays.asList(null, "")) {
      assertEquals(Code.E_UNKNOWN_KEY, code(() -> evaluator.resolveLocal(ACCOUNT, key)),
          "E_UNKNOWN_KEY, not E_PROVIDER_REQUIRED");
      assertEquals(Code.E_UNKNOWN_KEY, code(() -> evaluator.resolve(ACCOUNT, key)));
    }
    assertTrue(account.calls().isEmpty(), "no provider is called for an unknown key");
  }

  @Test
  void EVAL_37_anOmittedOrEmptyFeatureIsReportedBeforeFactSetValidation() {
    for (String feature : Arrays.asList(null, "")) {
      assertEquals(Code.E_UNKNOWN_FEATURE, code(() -> evaluator.decide(Context.empty(), feature, INVALID_FACTS)));
      assertEquals(Code.E_UNKNOWN_FEATURE, code(() -> evaluator.decide(ACCOUNT, feature, FactSet.fromJson(List.of()))));
    }
  }
}
