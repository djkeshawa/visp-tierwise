package io.github.tierwise.core.internal.check;

import static io.github.tierwise.core.internal.check.FeatureFixture.GATE;
import static io.github.tierwise.core.internal.check.FeatureFixture.GATE_RULES;
import static io.github.tierwise.core.internal.check.FeatureFixture.gateWith;
import static io.github.tierwise.core.internal.check.FeatureFixture.run;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tierwise.core.internal.check.FeatureFixture.Outcome;
import io.github.tierwise.core.internal.model.BindingDef;
import io.github.tierwise.core.internal.model.FeatureDef;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Bindings: BIND-1 to BIND-13, FEAT-19 and FEAT-20. */
class BindingRulesTest {

  private static final String DIVISION = "  division:\n    north: { policy: open }\n    "
      + "south: { policy: strict, allowed: [STOCKED, SPECIAL_ORDER] }\n";
  private static final String SOUTH = "south: { policy: strict, allowed: [STOCKED, SPECIAL_ORDER] }";

  private static void assertFound(Outcome outcome, String... expected) {
    assertEquals(List.of(expected).stream().sorted().toList(), outcome.found().stream().sorted().toList());
  }

  @Test
  void aCleanBindingsMapBuildsTheBindings() {
    Outcome outcome = run(GATE, GATE_RULES);
    assertFound(outcome);
    FeatureDef gate = outcome.parts().features().orElseThrow().get("gate");
    assertEquals(Map.of("", new BindingDef("strict", Map.of())), gate.bindings().get("global"));
    assertEquals(new BindingDef("strict", Map.of("allowed", List.of("STOCKED", "SPECIAL_ORDER"))),
        gate.bindings().get("division").get("south"));
    assertEquals(new BindingDef("open", Map.of()), gate.bindings().get("division").get("north"));
  }

  @Test
  void bind1BindingsIsAMapFromLevelName() {
    assertFound(run(GATE, "feature: gate\npolicies:\n  open: { default: ALLOW, rules: [] }\nbindings: [a]\n"),
        "E_INVALID_DECLARATION [bindings]");
  }

  @Test
  void bind2TheRootLevelHoldsOneBindingWithNoNodeId() {
    assertFound(gateWith("global: { policy: strict }", "global: { x: { policy: strict } }"),
        "E_INVALID_DECLARATION [bindings, global]");
  }

  @Test
  void bind3AnotherLevelHoldsABindingPerNode() {
    assertFound(gateWith(DIVISION, "  division: { policy: open }\n"),
        "E_UNKNOWN_NODE [bindings, division, policy]", "E_INVALID_DECLARATION [bindings, division, policy]");
  }

  @Test
  void bind4ABindingHasAPolicyEntry() {
    assertFound(gateWith(SOUTH, "south: { allowed: [STOCKED] }"), "E_INVALID_DECLARATION [bindings, division, south]");
    assertFound(gateWith(SOUTH, "south: { policy: [strict] }"), "E_INVALID_DECLARATION [bindings, division, south, policy]");
    assertFound(gateWith(SOUTH, "south: strict"), "E_INVALID_DECLARATION [bindings, division, south]");
  }

  @Test
  void bind5And6TheBindingNamesADeclaredPolicy() {
    assertFound(gateWith(SOUTH, "south: { policy: stricter }"), "E_UNKNOWN_POLICY [bindings, division, south, policy]");
  }

  @Test
  void bind7ABindingWithoutAKnownPolicyGetsNoSettingChecks() {
    assertFound(gateWith(SOUTH, "south: { policy: stricter, nothing: 1 }"),
        "E_UNKNOWN_POLICY [bindings, division, south, policy]");
    assertFound(gateWith(SOUTH, "south: { nothing: 1 }"), "E_INVALID_DECLARATION [bindings, division, south]");
  }

  @Test
  void bind8AnEntryNamesASettingOfThePolicy() {
    assertFound(gateWith(SOUTH, "south: { policy: strict, alowed: [STOCKED] }"),
        "E_UNKNOWN_SETTING [bindings, division, south, alowed]");
    assertFound(gateWith(SOUTH, "south: { policy: open, allowed: [STOCKED] }"),
        "E_UNKNOWN_SETTING [bindings, division, south, allowed]");
  }

  @Test
  void bind8SettingNamesAreComparedAfterNormalisation() {
    Outcome outcome = run(GATE, GATE_RULES.replace("held:", "café:").replace("$held", "$café")
        .replace(SOUTH, "south: { policy: strict, café: false }"));
    assertFound(outcome);
    assertEquals(false, outcome.parts().features().orElseThrow().get("gate").bindings().get("division").get("south")
        .settings().get("café"));
  }

  @Test
  void bind9ASettingValueIsValidForTheSettingsForm() {
    assertFound(gateWith(SOUTH, "south: { policy: strict, allowed: [MAYBE, SPECIAL_ORDER] }"),
        "E_INVALID_SETTING [bindings, division, south, allowed, 0]");
    assertFound(gateWith(SOUTH, "south: { policy: strict, allowed: STOCKED }"),
        "E_INVALID_SETTING [bindings, division, south, allowed]");
    assertFound(gateWith(SOUTH, "south: { policy: strict, allowed: null }"),
        "E_INVALID_SETTING [bindings, division, south, allowed]");
    assertFound(gateWith(SOUTH, "south: { policy: strict, allowed: [STOCKED, STOCKED] }"),
        "E_DUPLICATE_VALUE [bindings, division, south, allowed, 1]");
    assertFound(gateWith(SOUTH, "south: { policy: strict, held: [true] }"),
        "E_INVALID_SETTING [bindings, division, south, held]");
  }

  @Test
  void bind10ANodeIdIsAdmitted() {
    assertFound(gateWith(SOUTH, "east: { policy: open }"), "E_UNKNOWN_NODE [bindings, division, east]");
    assertFound(gateWith(SOUTH, "\"\": { policy: open }"), "E_INVALID_NODE_ID [bindings, division, ]");
  }

  @Test
  void bind11ABindingsKeyNamesADeclaredScope() {
    assertFound(gateWith(DIVISION, DIVISION + "  region:\n    x: { policy: open }\n"),
        "E_UNKNOWN_SCOPE [bindings, region]");
  }

  @Test
  void bind12ABindingAppearsOnlyAtAListedLevel() {
    assertFound(run(GATE.replace("[global, division]", "[global]"), GATE_RULES),
        "E_BINDING_NOT_ALLOWED [bindings, division, north]", "E_BINDING_NOT_ALLOWED [bindings, division, south]");
  }

  @Test
  void bind13EveryFeatureHasARootBinding() {
    assertFound(gateWith("  global: { policy: strict }\n", ""), "E_ROOT_BINDING_MISSING [bindings]");
    assertFound(run(GATE), "E_ROOT_BINDING_MISSING [features, gate]");
  }

  @Test
  void bind15OnlyLevelsCarryBindings() {
    assertFound(gateWith(DIVISION, DIVISION + "  seller:\n    acme: { policy: open }\n"),
        "E_BINDING_NOT_ALLOWED [bindings, seller, acme]");
    assertFound(gateWith(DIVISION, DIVISION + "  account:\n    a1: { policy: open }\n"),
        "E_BINDING_NOT_ALLOWED [bindings, account, a1]");
  }
}
