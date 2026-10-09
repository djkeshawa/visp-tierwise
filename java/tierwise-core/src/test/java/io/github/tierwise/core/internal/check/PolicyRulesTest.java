package io.github.tierwise.core.internal.check;

import static io.github.tierwise.core.internal.check.FeatureFixture.GATE;
import static io.github.tierwise.core.internal.check.FeatureFixture.GATE_RULES;
import static io.github.tierwise.core.internal.check.FeatureFixture.gateWith;
import static io.github.tierwise.core.internal.check.FeatureFixture.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.internal.check.FeatureFixture.Outcome;
import io.github.tierwise.core.internal.model.PolicyDef;
import io.github.tierwise.core.internal.model.SettingDef;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Rules files, policies, settings and rules: POL-1 to POL-27. */
class PolicyRulesTest {

  private static final String OPEN_ONLY = """
      feature: gate
      policies:
        open: { default: ALLOW, rules: [] }
      bindings:
        global: { policy: open }
      """;

  private static final String INVOICE_FEATURE = """
      features:
        release:
          decided_per: invoice
          returns: { enum: [HOLD, RELEASE] }
          hit_policy: FIRST
          binding_settable_at: [global]
          if_fact_missing: HOLD
      """;

  private static void assertFound(Outcome outcome, String... expected) {
    assertEquals(List.of(expected).stream().sorted().toList(), outcome.found().stream().sorted().toList());
  }

  private static Outcome setting(String declaration) {
    return gateWith("allowed: { list_of: item.stock_type, default: [] }", declaration);
  }

  @Test
  void pol1ARulesFileDeclaresFeaturePoliciesAndBindings() {
    assertFound(run(GATE, OPEN_ONLY.replace("bindings:\n  global: { policy: open }\n", "")),
        "E_INVALID_DECLARATION []");
    assertFound(run(GATE, OPEN_ONLY.replace("policies:\n  open: { default: ALLOW, rules: [] }\n", "")),
        "E_INVALID_DECLARATION []");
    assertFound(run(GATE, OPEN_ONLY + "notes: x\n"), "E_INVALID_DECLARATION [notes]");
    assertFound(run(GATE, OPEN_ONLY + "examples: []\n"));
  }

  @Test
  void pol2ARulesFileNamesOneFeature() {
    assertFound(run(GATE, OPEN_ONLY.replace("feature: gate\n", "")), "E_INVALID_DECLARATION []",
        "E_ROOT_BINDING_MISSING [features, gate]");
    assertFound(run(GATE, OPEN_ONLY.replace("feature: gate", "feature: [gate]")), "E_INVALID_DECLARATION [feature]",
        "E_ROOT_BINDING_MISSING [features, gate]");
  }

  @Test
  void pol3TheFeatureIsDeclared() {
    assertFound(run(GATE, OPEN_ONLY.replace("feature: gate", "feature: gat")), "E_UNKNOWN_FEATURE [feature]",
        "E_ROOT_BINDING_MISSING [features, gate]");
  }

  @Test
  void pol4TheFileNameDoesNotDecideTheFeature() {
    Outcome outcome = run(GATE, OPEN_ONLY);
    assertFound(outcome);
    assertTrue(outcome.parts().features().isPresent());
  }

  @Test
  void pol5AFeatureIsBuiltFromItsFirstRulesFile() {
    Outcome outcome = run(GATE, OPEN_ONLY, GATE_RULES);
    assertFound(outcome);
    assertEquals(List.of("open"), List.copyOf(outcome.parts().features().orElseThrow().get("gate").policies().keySet()));
  }

  @Test
  void pol6PoliciesIsAMap() {
    assertFound(run(GATE, "feature: gate\npolicies: [a]\nbindings:\n  global: { policy: open }\n"),
        "E_INVALID_DECLARATION [policies]");
  }

  @Test
  void pol7APolicyDeclaresDefault() {
    assertFound(run(GATE, OPEN_ONLY.replace("default: ALLOW, ", "")), "E_INVALID_DECLARATION [policies, open]");
  }

  @Test
  void pol8APolicyDeclaresRules() {
    assertFound(run(GATE, OPEN_ONLY.replace(", rules: []", "")), "E_INVALID_DECLARATION [policies, open]");
    assertFound(run(GATE, OPEN_ONLY.replace("rules: []", "rules: {}")), "E_INVALID_DECLARATION [policies, open, rules]");
  }

  @Test
  void pol9ASettingsEntryIsAMapAndAPolicyIsAMap() {
    assertFound(run(GATE, OPEN_ONLY.replace("{ default: ALLOW, rules: [] }", "{ default: ALLOW, rules: [], settings: [a] }")),
        "E_INVALID_DECLARATION [policies, open, settings]");
    assertFound(run(GATE, OPEN_ONLY.replace("{ default: ALLOW, rules: [] }", "[ALLOW]")),
        "E_INVALID_DECLARATION [policies, open]");
  }

  @Test
  void pol11ASettingDeclaresDefault() {
    assertFound(setting("allowed: { list_of: item.stock_type }"), "E_INVALID_SETTING [policies, strict, settings, allowed]");
  }

  @Test
  void pol12ASettingHasExactlyOneForm() {
    assertFound(setting("allowed: { list_of: item.stock_type, value_of: item.stock_type, default: [] }"),
        "E_INVALID_SETTING [policies, strict, settings, allowed]");
    assertFound(setting("allowed: { default: [] }"), "E_INVALID_SETTING [policies, strict, settings, allowed]");
  }

  @Test
  void pol13ASettingHasNoOtherEntry() {
    assertFound(setting("allowed: { list_of: item.stock_type, default: [], note: x }"),
        "E_INVALID_SETTING [policies, strict, settings, allowed, note]");
  }

  @Test
  void pol14TheSettingFactIsDeclared() {
    assertFound(setting("allowed: { list_of: item.colour, default: [] }"),
        "E_UNKNOWN_FACT [policies, strict, settings, allowed, list_of]");
  }

  @Test
  void pol15AListOfDefaultMayBeEmptyAndAValueOfDefaultIsOneValue() {
    Outcome outcome = run(GATE, GATE_RULES);
    assertFound(outcome);
    PolicyDef strict = outcome.parts().features().orElseThrow().get("gate").policies().get("strict");
    assertEquals(List.of(), strict.settings().get("allowed").defaultValue());
    assertEquals(SettingDef.Kind.VALUE_OF, strict.settings().get("held").kind());
    assertEquals(true, strict.settings().get("held").defaultValue());
  }

  @Test
  void pol16TheSettingFactIsEligible() {
    assertFound(setting("allowed: { list_of: warehouse.open, default: [] }"),
        "E_FACT_NOT_IN_SCOPE [policies, strict, settings, allowed, list_of]");
    String rules = """
        feature: release
        policies:
          check:
            settings:
              kinds: { list_of: item.stock_type, default: [] }
            default: HOLD
            rules: []
        bindings:
          global: { policy: check }
        """;
    assertFound(run(INVOICE_FEATURE, rules), "E_DEPTH_UNSUPPORTED [policies, check, settings, kinds, list_of]");
  }

  @Test
  void pol17ASettingValueIsAValueOfTheFactsType() {
    assertFound(setting("allowed: { list_of: item.stock_type, default: [MAYBE] }"),
        "E_INVALID_SETTING [policies, strict, settings, allowed, default, 0]");
    assertFound(setting("allowed: { list_of: item.stock_type, default: STOCKED }"),
        "E_INVALID_SETTING [policies, strict, settings, allowed, default]");
    assertFound(gateWith("default: true }", "default: yes }"), "E_INVALID_SETTING [policies, strict, settings, held, default]");
  }

  @Test
  void pol18NullIsNeverASettingValue() {
    assertFound(setting("allowed: { list_of: item.stock_type, default: null }"),
        "E_INVALID_SETTING [policies, strict, settings, allowed, default]");
    assertFound(setting("allowed: { list_of: item.stock_type, default: [null] }"),
        "E_INVALID_SETTING [policies, strict, settings, allowed, default, 0]");
  }

  @Test
  void pol19ARepeatedListElementIsADuplicateValue() {
    assertFound(setting("allowed: { list_of: item.stock_type, default: [STOCKED, STOCKED] }"),
        "E_DUPLICATE_VALUE [policies, strict, settings, allowed, default, 1]");
  }

  @Test
  void pol20ASettingNameIsPlain() {
    String anchor = "allowed: { list_of: item.stock_type, default: [] }";
    for (String name : List.of("policy", "a.b", "$a")) {
      assertFound(gateWith(anchor, anchor + "\n      \"" + name + "\": { value_of: customer.active, default: true }"),
          "E_INVALID_SETTING [policies, strict, settings, " + name + "]");
    }
    assertFound(gateWith(anchor, anchor + "\n      \"\": { value_of: customer.active, default: true }"),
        "E_INVALID_SETTING [policies, strict, settings, ]");
  }

  @Test
  void pol21ARuleDeclaresIdNameWhenAndThen() {
    assertFound(rule("{ id: a, name: A, when: {}, then: ALLOW }"));
    assertFound(rule("{ name: A, when: {}, then: ALLOW }"), "E_INVALID_DECLARATION [policies, p, rules, 0]");
    assertFound(rule("{ id: a, when: {}, then: ALLOW }"), "E_INVALID_DECLARATION [policies, p, rules, 0]");
    assertFound(rule("{ id: a, name: A, then: ALLOW }"), "E_INVALID_DECLARATION [policies, p, rules, 0]");
    assertFound(rule("{ id: a, name: A, when: {} }"), "E_INVALID_DECLARATION [policies, p, rules, 0]");
    assertFound(rule("{ id: a, name: A, when: {}, then: ALLOW, extra: 1 }"),
        "E_INVALID_DECLARATION [policies, p, rules, 0, extra]");
    assertFound(rule("ALLOW"), "E_INVALID_DECLARATION [policies, p, rules, 0]");
  }

  @Test
  void pol22WhenIsAConditionMappingAndThenIsAnEffect() {
    assertFound(rule("{ id: a, name: A, when: [x], then: ALLOW }"), "E_INVALID_DECLARATION [policies, p, rules, 0, when]");
    assertFound(rule("{ id: a, name: A, when: {}, then: MAYBE }"), "E_EFFECT_MISMATCH [policies, p, rules, 0, then]");
  }

  @Test
  void pol24ARuleIdIsUniqueWithinOnePolicy() {
    assertFound(gateWith("- id: stock", "- id: good"), "E_DUPLICATE_RULE_ID [policies, strict, rules, 1, id]");
    Outcome sharedAcrossPolicies = run(GATE, GATE_RULES.replace("rules: []",
        "rules:\n            - id: good\n              name: Open\n              when: {}\n              then: ALLOW"));
    assertFound(sharedAcrossPolicies);
  }

  @Test
  void pol25IdAndNameAreNonEmptyStrings() {
    assertFound(rule("{ id: 1, name: A, when: {}, then: ALLOW }"), "E_INVALID_DECLARATION [policies, p, rules, 0, id]");
    assertFound(rule("{ id: '', name: A, when: {}, then: ALLOW }"), "E_INVALID_DECLARATION [policies, p, rules, 0, id]");
    assertFound(rule("{ id: a, name: '', when: {}, then: ALLOW }"), "E_INVALID_DECLARATION [policies, p, rules, 0, name]");
    assertFound(rule("{ id: a, name: [x], when: {}, then: ALLOW }"), "E_INVALID_DECLARATION [policies, p, rules, 0, name]");
  }

  @Test
  void pol26AReferenceIsCheckedAgainstTheSettingsOfItsPolicy() {
    assertFound(gateWith("$held", "$helds"), "E_UNKNOWN_SETTING [policies, strict, rules, 1, when, customer.credit_hold]");
  }

  @Test
  void pol27AThenIsNotASettingReference() {
    assertFound(rule("{ id: a, name: A, when: {}, then: $held }"), "E_INVALID_SETTING [policies, p, rules, 0, then]");
  }

  /** A rules file whose only policy has the given rule, written inline. */
  private static Outcome rule(String rule) {
    return run(GATE, "feature: gate\npolicies:\n  p:\n    settings:\n      held: { value_of: customer.credit_hold, default: true }\n"
        + "    default: DENY\n    rules:\n      - " + rule + "\nbindings:\n  global: { policy: p }\n");
  }
}
