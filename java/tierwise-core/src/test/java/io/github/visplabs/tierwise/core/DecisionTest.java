package io.github.visplabs.tierwise.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The JSON form of section 9.6, using the non-normative example of section 9. */
class DecisionTest {

  private static final String VERSION = "sha256:" + "0".repeat(64);

  private static Map<String, Object> map(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }

  @Test
  void aDefaultDecisionListsEveryCheckedRule() {
    Decision decision = new Decision("feature_x", "DENY", VERSION, List.of(),
        new Decision.Binding("level_a", Optional.of("node_x")), "policy_x", Decision.Reason.defaultEffect(),
        List.of(Decision.TraceEntry.failed("rule-1", "entity_a.fact_x", "eq"),
            Decision.TraceEntry.failed("rule-2", "entity_b.fact_y", "all_in")));
    Map<String, Object> expected = map(
        "feature", "feature_x", "effect", "DENY", "bundleVersion", VERSION, "skippedScopes", List.of(),
        "binding", map("level", "level_a", "node", "node_x"), "policy", "policy_x",
        "reason", map("type", "DEFAULT"),
        "trace", List.of(
            map("id", "rule-1", "matched", false, "failed", map("fact", "entity_a.fact_x", "op", "eq")),
            map("id", "rule-2", "matched", false, "failed", map("fact", "entity_b.fact_y", "op", "all_in"))));
    assertEquals(expected, decision.toJson());
    assertFalse(decision.toJson().containsKey("contributions"));
    assertFalse(decision.toJson().containsKey("source"));
  }

  @Test
  void aRuleReasonCarriesIdAndNameAndAMatchedEntryHasNoFailed() {
    Decision decision = new Decision("feature_x", "ALLOW", VERSION, List.of(),
        new Decision.Binding("level_root", Optional.empty()), "policy_x", Decision.Reason.rule("rule-1", "Rule one"),
        List.of(Decision.TraceEntry.matched("rule-1")));
    Map<String, Object> json = decision.toJson();
    assertEquals(map("level", "level_root"), json.get("binding"));
    assertEquals(map("type", "RULE", "id", "rule-1", "name", "Rule one"), json.get("reason"));
    assertEquals(List.of(map("id", "rule-1", "matched", true)), json.get("trace"));
  }

  @Test
  void aMissingFactReasonListsFactsAndHasAnEmptyTrace() {
    Decision decision = new Decision("feature_x", null, VERSION, List.of(),
        new Decision.Binding("level_root", Optional.empty()), "policy_x",
        Decision.Reason.missingFact(List.of("entity_a.fact_x")), List.of());
    Map<String, Object> json = decision.toJson();
    assertEquals(map("type", "MISSING_FACT", "facts", List.of("entity_a.fact_x")), json.get("reason"));
    assertEquals(List.of(), json.get("trace"));
    assertEquals(null, json.get("effect"));
    assertEquals(true, json.containsKey("effect"));
  }

  @Test
  void anObjectEffectIsReturnedUnchanged() {
    Decision decision = new Decision("feature_x", map("limit", 3), VERSION, List.of(),
        new Decision.Binding("level_root", Optional.empty()), "policy_x", Decision.Reason.defaultEffect(), List.of());
    assertEquals(map("limit", 3L), decision.toJson().get("effect"));
  }

  @Test
  void reasonAndTraceShapesAreEnforced() {
    assertThrows(IllegalArgumentException.class, () -> Decision.Reason.missingFact(List.of()));
    assertThrows(IllegalArgumentException.class, () -> new Decision.Reason(Decision.Reason.Type.RULE,
        Optional.of("rule-1"), Optional.empty(), List.of()));
    assertThrows(IllegalArgumentException.class, () -> new Decision.Reason(Decision.Reason.Type.DEFAULT,
        Optional.of("rule-1"), Optional.of("n"), List.of()));
    assertThrows(IllegalArgumentException.class, () -> new Decision.TraceEntry("rule-1", true,
        Optional.of(new Decision.Failed("entity_a.fact_x", "eq"))));
    assertThrows(IllegalArgumentException.class, () -> new Decision.TraceEntry("rule-1", false, Optional.empty()));
  }
}
