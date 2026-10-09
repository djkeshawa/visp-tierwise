package io.github.tierwise.core.internal.check;

import static io.github.tierwise.core.internal.check.FeatureFixture.GATE;
import static io.github.tierwise.core.internal.check.FeatureFixture.GATE_RULES;
import static io.github.tierwise.core.internal.check.FeatureFixture.declWith;
import static io.github.tierwise.core.internal.check.FeatureFixture.run;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.internal.check.FeatureFixture.Outcome;
import io.github.tierwise.core.internal.model.FeatureDef;
import io.github.tierwise.core.internal.model.PolicyDef;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Feature declarations: FEAT-1 to FEAT-21. */
class FeatureDeclarationTest {

  private static final String OBJECT_FEATURE = """
      features:
        quote:
          decided_per: order
          returns:
            object:
              method: { type: enum, values: [GROUND, NEXT_DAY] }
              days: { type: integer, min: 1, max: 30 }
              note: { type: string, optional: true }
          hit_policy: FIRST
          binding_settable_at: [global]
          if_fact_missing: { method: GROUND, days: 5 }
      """;

  private static final String QUOTE_RULES = """
      feature: quote
      policies:
        standard:
          default: { method: GROUND, days: 5 }
          rules:
            - id: express
              name: Express
              when: { customer.active: true }
              then: { method: NEXT_DAY, days: 1, note: Hurry }
      bindings:
        global: { policy: standard }
      """;

  private static void assertFound(Outcome outcome, String... expected) {
    assertEquals(List.of(expected).stream().sorted().toList(), outcome.found().stream().sorted().toList());
  }

  @Test
  void aCleanDeclarationBuildsTheFeature() {
    Outcome outcome = run(GATE, GATE_RULES);
    assertFound(outcome);
    FeatureDef gate = outcome.parts().features().orElseThrow().get("gate");
    assertEquals("order", gate.decidedPer());
    assertEquals("FIRST", gate.hitPolicy());
    assertEquals("DENY", gate.ifFactMissing());
    assertEquals(List.of("global", "division"), gate.bindingSettableAt());
    assertEquals(List.of("open", "strict"), gate.policies().keySet().stream().sorted().toList());
  }

  @Test
  void feat1ADeclarationLacksARequiredMember() {
    assertFound(declWith("    decided_per: order\n", ""), "E_INVALID_DECLARATION [features, gate]");
    assertFound(declWith("    hit_policy: FIRST\n", ""), "E_INVALID_DECLARATION [features, gate]");
    assertFound(declWith("    binding_settable_at: [global, division]\n", ""), "E_INVALID_DECLARATION [features, gate]");
  }

  @Test
  void feat1DescriptionIsAString() {
    assertFound(declWith("  hit_policy", "  description: A gate\n    hit_policy"));
    assertFound(declWith("  hit_policy", "  description: [a]\n    hit_policy"),
        "E_INVALID_DECLARATION [features, gate, description]");
  }

  @Test
  void feat2AMemberTheDeclarationDoesNotAllowIsInvalid() {
    assertFound(declWith("  hit_policy", "  owner: team\n    hit_policy"), "E_INVALID_DECLARATION [features, gate, owner]");
  }

  @Test
  void feat2AFeatureThatIsNotAMapIsInvalid() {
    assertFound(run("features:\n  gate: DENY\n", GATE_RULES), "E_INVALID_DECLARATION [features, gate]");
  }

  @Test
  void feat3IfFactMissingIsRequired() {
    assertFound(declWith("    if_fact_missing: DENY\n", ""), "E_INVALID_DECLARATION [features, gate]");
  }

  @Test
  void feat4DecidedPerIsOneEntityName() {
    assertFound(declWith("decided_per: order", "decided_per: [order]"),
        "E_INVALID_DECLARATION [features, gate, decided_per]");
  }

  @Test
  void feat5DecidedPerNamesADeclaredEntity() {
    assertFound(declWith("decided_per: order", "decided_per: ordr"), "E_UNKNOWN_ENTITY [features, gate, decided_per]");
  }

  @Test
  void feat6ReturnsTakesExactlyOneForm() {
    assertFound(declWith("returns: { enum: [ALLOW, DENY] }", "returns: { enum: [ALLOW, DENY], object: { a: { type: string } } }"),
        "E_INVALID_DECLARATION [features, gate, returns]");
    assertFound(declWith("returns: { enum: [ALLOW, DENY] }", "returns: {}"),
        "E_INVALID_DECLARATION [features, gate, returns]");
    assertFound(declWith("returns: { enum: [ALLOW, DENY] }", "returns: ALLOW"),
        "E_INVALID_DECLARATION [features, gate, returns]");
  }

  @Test
  void feat7TheEnumFormMayBeNullable() {
    Outcome outcome = run(GATE.replace("[ALLOW, DENY] }", "[ALLOW, DENY], nullable: true }"),
        GATE_RULES.replace("default: ALLOW\n", "default: null\n"));
    assertFound(outcome);
    assertTrue(outcome.parts().features().orElseThrow().get("gate").returns().nullable());
    assertFound(declWith("[ALLOW, DENY] }", "[ALLOW, DENY], nullable: maybe }"),
        "E_INVALID_DECLARATION [features, gate, returns, nullable]");
  }

  @Test
  void feat8TheEnumListIsNonEmptyStringsWithoutRepeats() {
    assertFound(declWith("[ALLOW, DENY]", "[]"), "E_INVALID_DECLARATION [features, gate, returns, enum]");
    assertFound(declWith("[ALLOW, DENY]", "[ALLOW, 5]"), "E_INVALID_DECLARATION [features, gate, returns, enum, 1]");
    assertFound(declWith("[ALLOW, DENY]", "[ALLOW, DENY, ALLOW]"), "E_DUPLICATE_VALUE [features, gate, returns, enum, 2]");
  }

  @Test
  void feat9NullIsAnEffectOnlyWhenTheEnumIsNullable() {
    assertFound(run(GATE, GATE_RULES.replace("default: ALLOW\n", "default: null\n")),
        "E_EFFECT_MISMATCH [policies, open, default]");
  }

  @Test
  void feat10NoEffectStringHasAMeaning() {
    Outcome outcome = run(GATE.replace("[ALLOW, DENY]", "[ALLOW, DENY, \"null\"]"),
        GATE_RULES.replace("default: ALLOW\n", "default: \"null\"\n"));
    assertFound(outcome);
    PolicyDef open = outcome.parts().features().orElseThrow().get("gate").policies().get("open");
    assertEquals("null", open.defaultEffect());
  }

  @Test
  void feat11TheObjectFormTakesObjectEffects() {
    Outcome outcome = run(OBJECT_FEATURE, QUOTE_RULES);
    assertFound(outcome);
    FeatureDef quote = outcome.parts().features().orElseThrow().get("quote");
    assertTrue(quote.returns().objectForm());
    assertEquals(java.util.Map.of("method", "GROUND", "days", 5L), quote.ifFactMissing());
  }

  @Test
  void feat12TheObjectFormDoesNotDeclareNullable() {
    assertFound(run(OBJECT_FEATURE.replace("      object:\n", "      nullable: true\n      object:\n"), QUOTE_RULES),
        "E_INVALID_DECLARATION [features, quote, returns, nullable]");
  }

  @Test
  void feat13And14EveryEffectIsAValueOfReturns() {
    assertFound(declWith("if_fact_missing: DENY", "if_fact_missing: MAYBE"),
        "E_EFFECT_MISMATCH [features, gate, if_fact_missing]");
    assertFound(run(GATE, GATE_RULES.replace("then: ALLOW\n", "then: MAYBE\n")),
        "E_EFFECT_MISMATCH [policies, strict, rules, 0, then]", "E_EFFECT_MISMATCH [policies, strict, rules, 1, then]");
    assertFound(run(OBJECT_FEATURE, QUOTE_RULES.replace("days: 1", "days: 99")),
        "E_EFFECT_MISMATCH [policies, standard, rules, 0, then]");
  }

  @Test
  void feat15And16TheHitPolicyIsFirst() {
    assertFound(declWith("hit_policy: FIRST", "hit_policy: LAST"), "E_INVALID_DECLARATION [features, gate, hit_policy]");
    assertFound(declWith("hit_policy: FIRST", "hit_policy: [FIRST]"), "E_INVALID_DECLARATION [features, gate, hit_policy]");
  }

  @Test
  void feat17BindingSettableAtNamesDeclaredScopes() {
    assertFound(declWith("[global, division]", "[global, division, region]"),
        "E_UNKNOWN_SCOPE [features, gate, binding_settable_at, 2]");
    assertFound(declWith("[global, division]", "global"), "E_INVALID_DECLARATION [features, gate, binding_settable_at]");
    assertFound(declWith("[global, division]", "[global, 5]"), "E_INVALID_DECLARATION [features, gate, binding_settable_at, 1]",
        "E_BINDING_NOT_ALLOWED [bindings, division, north]", "E_BINDING_NOT_ALLOWED [bindings, division, south]");
  }

  @Test
  void feat18TheRootLevelIsListed() {
    assertFound(declWith("[global, division]", "[division]"), "E_ROOT_BINDING_NOT_ALLOWED [features, gate, binding_settable_at]",
        "E_BINDING_NOT_ALLOWED [bindings, global]");
  }

  @Test
  void feat19AServiceLevelIsNotListed() {
    assertFound(declWith("[global, division]", "[global, division, account]"),
        "E_BINDING_NOT_ALLOWED [features, gate, binding_settable_at, 2]");
  }

  @Test
  void feat20AnAxisIsNotListed() {
    assertFound(declWith("[global, division]", "[global, division, seller]"),
        "E_BINDING_NOT_ALLOWED [features, gate, binding_settable_at, 2]");
  }

  @Test
  void feat21ALevelIsListedOnce() {
    assertFound(declWith("[global, division]", "[global, division, division]"),
        "E_DUPLICATE_VALUE [features, gate, binding_settable_at, 2]");
  }
}
