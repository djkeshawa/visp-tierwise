package io.github.tierwise.core.internal.eval;

import static io.github.tierwise.core.internal.eval.TestModels.mapOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Context;
import io.github.tierwise.core.Decision;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.core.internal.model.BindingDef;
import io.github.tierwise.core.internal.model.ConditionDef;
import io.github.tierwise.core.internal.model.FeatureDef;
import io.github.tierwise.core.internal.model.PolicyDef;
import io.github.tierwise.core.internal.model.Returns;
import io.github.tierwise.core.internal.model.RuleDef;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * No coercion before a comparison (RULE-38, NORM-17, NORM-18): values of different types are never equal, a fact
 * value of the wrong type is invalid rather than converted, and decimals compare as canonical strings (RULE-37).
 */
class NoCoercionTest {

  private final TestModels.Builder model = TestModels.standard();
  private Evaluator evaluator;

  /** Adds a feature over {@code order} with one rule {@code fact op value} that allows; otherwise it denies. */
  private void feature(String name, String fact, String op, Object value) {
    PolicyDef policy = new PolicyDef(Map.of(), "DENY", List.of(fact),
        List.of(new RuleDef("r", "r", List.of(new ConditionDef(fact, op, value)), "ALLOW")));
    model.feature(name, new FeatureDef("order", Returns.enumeration(List.of("ALLOW", "DENY"), false), "FIRST", "DENY",
        List.of("global"), Map.of("p", policy), Map.of("global", Map.of("", new BindingDef("p", Map.of())))));
  }

  private Object effect(String feature, String fact, Object value) {
    return evaluator.decide(Context.empty(), feature, FactSet.of(mapOf(fact, value), Map.of())).effect();
  }

  private String code(String feature, String fact, Object value) {
    return assertThrows(TierwiseException.class,
        () -> evaluator.decide(Context.empty(), feature, FactSet.of(mapOf(fact, value), Map.of()))).code();
  }

  private void build() {
    evaluator = TestModels.evaluator(model, node -> Map.of(), node -> Map.of(), null);
  }

  @Test
  void RULE_38_valuesOfDifferentTypesAreNeverEqual() {
    List<Object[]> pairs = List.of(new Object[] {true, "true"}, new Object[] {false, "false"}, new Object[] {true, 1L},
        new Object[] {false, 0L}, new Object[] {7L, "7"}, new Object[] {"1.5", 1.5}, new Object[] {"1.5", new BigDecimal("1.5")},
        new Object[] {7L, 7}, new Object[] {0L, null});
    for (Object[] pair : pairs) {
      assertFalse(ValueEquality.scalarEquals(pair[0], pair[1]), Arrays.toString(pair));
      assertFalse(ValueEquality.scalarEquals(pair[1], pair[0]), Arrays.toString(pair));
    }
    for (Object value : List.of(true, 7L, "1.5", "true")) {
      assertTrue(ValueEquality.scalarEquals(value, value));
    }
  }

  @Test
  void RULE_38_aConditionOperandOfAnotherTypeNeverMatchesAndIsNotCoerced() {
    // A loaded bundle cannot hold such a literal (the compiler reports E_ILLEGAL_FACT_VALUE and the loader rejects
    // it), so a hand-built model checks the comparison itself.
    feature("f.flag", "customer.active", "eq", "true");
    feature("f.count", "order.lines", "eq", "7");
    feature("f.count_in", "order.lines", "in", List.of("7", true));
    feature("f.price", "order.total", "eq", 1L);
    build();
    assertEquals("DENY", effect("f.flag", "customer.active", true));
    assertEquals("DENY", effect("f.count", "order.lines", 7L));
    assertEquals("DENY", effect("f.count_in", "order.lines", 7L));
    assertEquals("DENY", effect("f.price", "order.total", "1"));
    Decision decision = evaluator.decide(Context.empty(), "f.flag", FactSet.of(mapOf("customer.active", true), Map.of()));
    assertFalse(decision.trace().get(0).matched());
  }

  @Test
  void RULE_38_aFactValueOfAnotherTypeIsInvalidNotCoerced() {
    feature("f.flag", "customer.active", "eq", true);
    feature("f.count", "order.lines", "eq", 7L);
    feature("f.price", "order.total", "eq", "1.5");
    feature("f.kind", "order.kind", "eq", "RETAIL");
    feature("f.label", "order.label", "eq", "true");
    build();
    assertEquals(Code.E_FACTS_INVALID, code("f.flag", "customer.active", "true"));
    assertEquals(Code.E_FACTS_INVALID, code("f.flag", "customer.active", 1L));
    assertEquals(Code.E_FACTS_INVALID, code("f.count", "order.lines", "7"));
    assertEquals(Code.E_FACTS_INVALID, code("f.count", "order.lines", true));
    assertEquals(Code.E_FACTS_INVALID, code("f.price", "order.total", 1.5));
    assertEquals(Code.E_FACTS_INVALID, code("f.price", "order.total", new BigDecimal("1.5")));
    assertEquals(Code.E_FACTS_INVALID, code("f.kind", "order.kind", 0L));
    assertEquals(Code.E_FACTS_INVALID, code("f.label", "order.label", true));
    assertEquals("ALLOW", effect("f.flag", "customer.active", true));
    assertEquals("ALLOW", effect("f.count", "order.lines", 7L));
    assertEquals("ALLOW", effect("f.count", "order.lines", 7), "integers compare by numeric value (RULE-36)");
    assertEquals("ALLOW", effect("f.label", "order.label", "true"));
    assertEquals("DENY", effect("f.label", "order.label", "True"), "strings compare exactly");
  }

  @Test
  void RULE_37_decimalsCompareAsCanonicalStrings() {
    feature("f.price", "order.total", "eq", "1.5");
    build();
    for (String value : List.of("1.5", "1.50", "+1.5", "01.5", "1.500000")) {
      assertEquals("ALLOW", effect("f.price", "order.total", value), value);
    }
    for (String value : List.of("1.05", "15", "-1.5", "1.51")) {
      assertEquals("DENY", effect("f.price", "order.total", value), value);
    }
  }
}
