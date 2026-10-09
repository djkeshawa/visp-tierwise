package io.github.tierwise.core.internal.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Context;
import io.github.tierwise.core.Decision;
import io.github.tierwise.core.Decision.Reason;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.internal.model.BindingDef;
import io.github.tierwise.core.internal.model.ConditionDef;
import io.github.tierwise.core.internal.model.FeatureDef;
import io.github.tierwise.core.internal.model.PolicyDef;
import io.github.tierwise.core.internal.model.Returns;
import io.github.tierwise.core.internal.model.RuleDef;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Binding selection, settings, the missing-fact precheck, FIRST and the trace: BIND-14 to BIND-24,
 * RULE-7 to RULE-55, FACT-16 to FACT-21 and PROV-62 to PROV-84.
 */
class DecisionEngineTest {

  private final TestModels.Builder builder = TestModels.standard();

  private Evaluator evaluator() {
    return TestModels.evaluator(builder, TestModels.failing(), TestModels.failing(), null);
  }

  private static FactSet.Builder facts() {
    return FactSet.builder();
  }

  private static Map<String, Object> child(String stock) {
    return Map.of("item.stock", stock);
  }

  private Decision decide(Context context, FactSet facts) {
    return evaluator().decide(context, "f.gate", facts);
  }

  @Test
  void BIND_17_theSelectedBindingIsTheCandidateAtTheLatestLevel() {
    Decision root = decide(Context.empty(), facts().fact("customer.active", true).fact("customer.credit_hold", false)
        .child("item", child("DROP")).build());
    assertEquals(new Decision.Binding("global", Optional.empty()), root.binding());
    assertEquals("strict", root.policy());
    Decision north = decide(Context.builder().level("division", "north").build(), FactSet.empty());
    assertEquals(new Decision.Binding("division", Optional.of("north")), north.binding());
    assertEquals("open", north.policy());
    Decision site = decide(Context.builder().level("division", "north").level("site", "s1").build(),
        facts().fact("order.total", "2").build());
    assertEquals(new Decision.Binding("site", Optional.of("s1")), site.binding());
    assertEquals("totals", site.policy());
  }

  @Test
  void BIND_16_aNodeWithoutABindingContributesNoCandidateAndTheNextLevelDown_isUsed() {
    Decision south = decide(Context.builder().level("account", "a1").build(), FactSet.empty());
    assertEquals(new Decision.Binding("global", Optional.empty()), south.binding());
    assertEquals(Reason.Type.MISSING_FACT, south.reason().type());
  }

  @Test
  void BIND_15_anAxisNeverCarriesABindingOrSelectsOne() {
    Decision decision = decide(Context.builder().level("division", "north").axis("seller", "acme").build(), FactSet.empty());
    assertEquals("division", decision.binding().level());
  }

  @Test
  void CTX_15_aSkippedScopeContributesNoCandidate() {
    Decision decision = decide(Context.builder().level("division", "north").level("site", "s9").build(), FactSet.empty());
    assertEquals(new Decision.Binding("division", Optional.of("north")), decision.binding());
    assertEquals(1, decision.skippedScopes().size());
  }

  @Test
  void BIND_18_theSelectedBindingReplacesAllOthersEntirely() {
    // north binds "open" (no rules); the root binding's rules play no part
    Decision decision = decide(Context.builder().level("division", "north").build(),
        facts().fact("customer.active", true).fact("customer.credit_hold", false).build());
    assertEquals(Reason.defaultEffect(), decision.reason());
    assertEquals("ALLOW", decision.effect());
    assertTrue(decision.trace().isEmpty());
  }

  @Test
  void BIND_23_aSettingTakesTheBindingValueElseItsDefault() {
    FactSet onHold = facts().fact("customer.active", true).fact("customer.credit_hold", true)
        .child("item", child("STOCKED")).child("item", child("SPECIAL")).build();
    Decision south = decide(Context.builder().level("division", "south").build(), onHold);
    assertEquals("ALLOW", south.effect());
    assertEquals(Reason.rule("hold-allowed-stock", "On hold, every item is an allowed stock type"), south.reason());
    Decision root = decide(Context.empty(), onHold);
    assertEquals("DENY", root.effect(), "the root binding leaves 'allowed' at its default, the empty list");
    assertEquals(Reason.defaultEffect(), root.reason());
  }

  @Test
  void BIND_24_aSettingTheSelectedBindingOmitsTakesNoValueFromAnotherBinding() {
    builder.features.put("f.gate", withSouthSettingOmitted(builder.features.get("f.gate")));
    FactSet onHold = facts().fact("customer.active", true).fact("customer.credit_hold", true)
        .child("item", child("STOCKED")).build();
    Decision decision = decide(Context.builder().level("division", "south").build(), onHold);
    assertEquals("DENY", decision.effect());
  }

  private static FeatureDef withSouthSettingOmitted(FeatureDef feature) {
    Map<String, Map<String, BindingDef>> bindings = new LinkedHashMap<>(feature.bindings());
    Map<String, BindingDef> division = new LinkedHashMap<>(bindings.get("division"));
    division.put("south", new BindingDef("strict", Map.of()));
    bindings.put("division", division);
    bindings.put("global", Map.of("", new BindingDef("strict", Map.of("allowed", List.of("STOCKED")))));
    return new FeatureDef(feature.decidedPer(), feature.returns(), feature.hitPolicy(), feature.ifFactMissing(),
        feature.bindingSettableAt(), feature.policies(), bindings);
  }

  @Test
  void RULE_8_andRULE_54_conditionsAreCheckedInCodePointOrderOfFactNameAndTheFirstFailureIsReported() {
    FactSet facts = facts().fact("customer.active", true).fact("customer.credit_hold", true)
        .child("item", child("STOCKED")).child("item", child("DROP")).build();
    Decision decision = decide(Context.builder().level("division", "south").build(), facts);
    assertEquals(List.of(
        Decision.TraceEntry.failed("good-credit", "customer.credit_hold", "eq"),
        Decision.TraceEntry.failed("hold-allowed-stock", "item.stock", "all_in")), decision.trace());
  }

  @Test
  void RULE_8_theBundleOrderOfConditionsDoesNotMatter() {
    PolicyDef policy = builder.features.get("f.gate").policies().get("strict");
    RuleDef reordered = new RuleDef("r", "R", List.of(new ConditionDef("customer.credit_hold", "eq", true),
        new ConditionDef("customer.active", "eq", true)), "ALLOW");
    RuleMatcher matcher = new RuleMatcher(builder.model());
    ValidatedFacts facts = new ValidatedFacts(Map.of("customer.active", false, "customer.credit_hold", false), Map.of());
    assertEquals("customer.active", matcher.firstFailure(reordered, facts, Map.of()).orElseThrow().fact());
    assertEquals(2, policy.rules().size());
  }

  @Test
  void RULE_2_aRuleWithNoConditionsAlwaysMatches() {
    RuleMatcher matcher = new RuleMatcher(builder.model());
    assertTrue(matcher.firstFailure(new RuleDef("r", "R", List.of(), "ALLOW"), new ValidatedFacts(Map.of(), Map.of()),
        Map.of()).isEmpty());
  }

  @Test
  void RULE_46_andRULE_47_theFirstMatchingRuleWinsAndLaterRulesAreNotChecked() {
    Decision decision = decide(Context.empty(), facts().fact("customer.active", true).fact("customer.credit_hold", false)
        .child("item", child("DROP")).build());
    assertEquals("ALLOW", decision.effect());
    assertEquals(Reason.rule("good-credit", "Good credit"), decision.reason());
    assertEquals(List.of(Decision.TraceEntry.matched("good-credit")), decision.trace());
  }

  @Test
  void RULE_52_andRULE_53_theTraceListsTheRulesUpToAndIncludingTheWinner() {
    Decision decision = decide(Context.builder().level("division", "south").build(),
        facts().fact("customer.active", true).fact("customer.credit_hold", true).child("item", child("STOCKED")).build());
    assertEquals(2, decision.trace().size());
    assertFalse(decision.trace().get(0).matched());
    assertTrue(decision.trace().get(1).matched());
    assertEquals("hold-allowed-stock", decision.reason().id().orElseThrow());
  }

  @Test
  void RULE_50_andRULE_51_noMatchGivesThePolicyDefaultWithReasonDefault() {
    Decision decision = decide(Context.empty(), facts().fact("customer.active", false).fact("customer.credit_hold", false)
        .child("item", child("DROP")).build());
    assertEquals("DENY", decision.effect());
    assertEquals(Reason.defaultEffect(), decision.reason());
    assertEquals(2, decision.trace().size());
    assertTrue(decision.trace().stream().noneMatch(Decision.TraceEntry::matched));
  }

  @Test
  void RULE_42_aMissingFactInThePrecheckSetChecksNoRuleAndGivesIfFactMissing() {
    Decision decision = decide(Context.empty(), facts().fact("customer.active", true).build());
    assertEquals("DENY", decision.effect());
    assertEquals(Reason.missingFact(List.of("customer.credit_hold", "item.stock")), decision.reason());
    assertTrue(decision.trace().isEmpty());
    assertEquals("strict", decision.policy(), "binding and policy are present with MISSING_FACT (PROV-72)");
    assertEquals("global", decision.binding().level());
  }

  @Test
  void RULE_44_theMissingFactsAreSortedByCodePointWhateverTheOrderOfTheBundle() {
    PolicyDef strict = builder.features.get("f.gate").policies().get("strict");
    PolicyDef unsorted = new PolicyDef(strict.settings(), strict.defaultEffect(),
        List.of("item.stock", "customer.credit_hold", "customer.active"), strict.rules());
    Map<String, PolicyDef> policies = new LinkedHashMap<>(builder.features.get("f.gate").policies());
    policies.put("strict", unsorted);
    FeatureDef feature = builder.features.get("f.gate");
    builder.features.put("f.gate", new FeatureDef(feature.decidedPer(), feature.returns(), feature.hitPolicy(),
        feature.ifFactMissing(), feature.bindingSettableAt(), policies, feature.bindings()));
    Decision decision = decide(Context.empty(), FactSet.empty());
    assertEquals(List.of("customer.active", "customer.credit_hold", "item.stock"), decision.reason().facts());
  }

  @Test
  void FACT_16_aNullSingleValueFactIsMissingLikeAnAbsentOne() {
    Decision nullValue = decide(Context.empty(), facts().fact("customer.active", null).fact("customer.credit_hold", false)
        .child("item", child("DROP")).build());
    assertEquals(List.of("customer.active"), nullValue.reason().facts());
  }

  @Test
  void FACT_17_aChildFactIsMissingWhenTheChildrenEntryIsOmitted() {
    Decision decision = decide(Context.empty(), facts().fact("customer.active", true).fact("customer.credit_hold", false).build());
    assertEquals(Reason.missingFact(List.of("item.stock")), decision.reason());
  }

  @Test
  void FACT_18_anEmptyChildListMakesNoChildFactMissing() {
    FactSet empty = FactSet.fromJson(Map.of("facts", Map.of("customer.active", true, "customer.credit_hold", true),
        "children", Map.of("item", List.of())));
    Decision decision = decide(Context.builder().level("division", "south").build(), empty);
    assertEquals(Reason.defaultEffect(), decision.reason(), "the all_in condition does not hold on an empty collection (RULE-33)");
    assertEquals("DENY", decision.effect());
  }

  @Test
  void FACT_19_aChildFactIsMissingWhenOneChildMapLacksItOrHoldsNull() {
    Map<String, Object> withNull = new LinkedHashMap<>();
    withNull.put("item.stock", null);
    FactSet facts = facts().fact("customer.active", true).fact("customer.credit_hold", true)
        .child("item", child("STOCKED")).child("item", withNull).build();
    assertEquals(Reason.missingFact(List.of("item.stock")), decide(Context.empty(), facts).reason());
    FactSet lacking = facts().fact("customer.active", true).fact("customer.credit_hold", true)
        .child("item", child("STOCKED")).child("item", Map.of()).build();
    assertEquals(Reason.missingFact(List.of("item.stock")), decide(Context.empty(), lacking).reason());
  }

  @Test
  void FACT_20_theOrderOfChildMapsDoesNotChangeTheResult() {
    FactSet one = facts().fact("customer.active", true).fact("customer.credit_hold", true)
        .child("item", child("STOCKED")).child("item", child("DROP")).build();
    FactSet other = facts().fact("customer.active", true).fact("customer.credit_hold", true)
        .child("item", child("DROP")).child("item", child("STOCKED")).build();
    Context south = Context.builder().level("division", "south").build();
    assertEquals(decide(south, one).toJson(), decide(south, other).toJson());
  }

  @Test
  void RULE_41_aFactNamedOnlyByASettingIsNotInThePrecheckSet() {
    // the "totals" policy has the setting 'expected' for order.total, which is also in a rule; the 'strict'
    // policy has the setting 'allowed' for item.stock; remove item.stock from requiredFacts to model a fact
    // named only by a setting
    PolicyDef strict = builder.features.get("f.gate").policies().get("strict");
    PolicyDef settingOnly = new PolicyDef(strict.settings(), "DENY", List.of(), List.of());
    Map<String, PolicyDef> policies = new LinkedHashMap<>(builder.features.get("f.gate").policies());
    policies.put("strict", settingOnly);
    FeatureDef feature = builder.features.get("f.gate");
    builder.features.put("f.gate", new FeatureDef(feature.decidedPer(), feature.returns(), feature.hitPolicy(),
        feature.ifFactMissing(), feature.bindingSettableAt(), policies, feature.bindings()));
    Decision decision = decide(Context.empty(), FactSet.empty());
    assertEquals(Reason.defaultEffect(), decision.reason());
  }

  @Test
  void RULE_45_anEmptyPrecheckSetPassesTheCheck() {
    Decision decision = decide(Context.builder().level("division", "north").build(), FactSet.empty());
    assertEquals(Reason.defaultEffect(), decision.reason());
    assertEquals("ALLOW", decision.effect());
  }

  @Test
  void RULE_37_decimalsAreComparedAsCanonicalStrings() {
    Context site = Context.builder().level("site", "s1").build();
    assertEquals("ALLOW", decide(site, facts().fact("order.total", "2.000").build()).effect());
    assertEquals("ALLOW", decide(site, facts().fact("order.total", "+02").build()).effect());
    assertEquals("DENY", decide(site, facts().fact("order.total", "1.5").build()).effect());
  }

  @Test
  void RULE_31_andRULE_32_allInAndAnyInOverTheChildren() {
    PolicyDef any = new PolicyDef(Map.of(), "DENY", List.of("item.stock"), List.of(
        new RuleDef("any", "Any", List.of(new ConditionDef("item.stock", "any_in", List.of("SPECIAL"))), "ALLOW")));
    PolicyDef all = new PolicyDef(Map.of(), "DENY", List.of("item.stock"), List.of(
        new RuleDef("all", "All", List.of(new ConditionDef("item.stock", "all_in", List.of("SPECIAL", "STOCKED"))), "ALLOW")));
    RuleMatcher matcher = new RuleMatcher(builder.model());
    ValidatedFacts mixed = new ValidatedFacts(Map.of(), Map.of("item", List.of(child("SPECIAL"), child("DROP"))));
    assertTrue(matcher.firstFailure(any.rules().get(0), mixed, Map.of()).isEmpty());
    assertTrue(matcher.firstFailure(all.rules().get(0), mixed, Map.of()).isPresent());
    ValidatedFacts good = new ValidatedFacts(Map.of(), Map.of("item", List.of(child("SPECIAL"), child("STOCKED"))));
    assertTrue(matcher.firstFailure(all.rules().get(0), good, Map.of()).isEmpty());
  }

  @Test
  void RULE_33_aPresentEmptyCollectionNeitherHoldsAllInNorAnyIn() {
    RuleMatcher matcher = new RuleMatcher(builder.model());
    ValidatedFacts empty = new ValidatedFacts(Map.of(), Map.of("item", List.of()));
    for (String op : List.of("all_in", "any_in")) {
      RuleDef rule = new RuleDef("r", "R", List.of(new ConditionDef("item.stock", op, List.of("SPECIAL"))), "ALLOW");
      assertEquals(op, matcher.firstFailure(rule, empty, Map.of()).orElseThrow().op());
    }
  }

  @Test
  void RULE_34_anEmptyOperandListNeverHolds() {
    RuleMatcher matcher = new RuleMatcher(builder.model());
    ValidatedFacts facts = new ValidatedFacts(Map.of("order.kind", "RETAIL"), Map.of("item", List.of(child("DROP"))));
    assertTrue(matcher.firstFailure(new RuleDef("r", "R", List.of(new ConditionDef("order.kind", "in", List.of())), "A"),
        facts, Map.of()).isPresent());
    assertTrue(matcher.firstFailure(new RuleDef("r", "R", List.of(new ConditionDef("item.stock", "any_in", "$s")), "A"),
        facts, Map.of("s", List.of())).isPresent());
  }

  @Test
  void RULE_30_inHoldsWhenTheFactEqualsAnElementOfTheList() {
    RuleMatcher matcher = new RuleMatcher(builder.model());
    ValidatedFacts facts = new ValidatedFacts(Map.of("order.kind", "RETAIL"), Map.of());
    assertTrue(matcher.firstFailure(new RuleDef("r", "R",
        List.of(new ConditionDef("order.kind", "in", List.of("TRADE", "RETAIL"))), "A"), facts, Map.of()).isEmpty());
    assertTrue(matcher.firstFailure(new RuleDef("r", "R",
        List.of(new ConditionDef("order.kind", "in", List.of("TRADE"))), "A"), facts, Map.of()).isPresent());
  }

  @Test
  void RULE_36_integersAreComparedByNumericValue() {
    RuleMatcher matcher = new RuleMatcher(builder.model());
    ValidatedFacts facts = new ValidatedFacts(Map.of("order.lines", 5L), Map.of());
    assertTrue(matcher.firstFailure(new RuleDef("r", "R", List.of(new ConditionDef("order.lines", "eq", 5L)), "A"),
        facts, Map.of()).isEmpty());
    assertTrue(matcher.firstFailure(new RuleDef("r", "R", List.of(new ConditionDef("order.lines", "eq", "5")), "A"),
        facts, Map.of()).isPresent());
  }

  @Test
  void RULE_38_aBooleanFactNeverEqualsTheStringTrue() {
    RuleMatcher matcher = new RuleMatcher(builder.model());
    ValidatedFacts facts = new ValidatedFacts(Map.of("customer.active", true), Map.of());
    assertTrue(matcher.firstFailure(new RuleDef("r", "R", List.of(new ConditionDef("customer.active", "eq", "true")), "A"),
        facts, Map.of()).isPresent());
  }

  @Test
  void RULE_39_evaluatingAConditionRaisesNoError() {
    Decision decision = decide(Context.empty(), facts().fact("customer.active", true).fact("customer.credit_hold", true)
        .child("item", child("DROP")).build());
    assertEquals("DENY", decision.effect());
  }

  @Test
  void PROV_66_andPROV_67_theEffectIsReturnedUnchangedWhateverItsText() {
    PolicyDef labelled = new PolicyDef(Map.of(), "IGNORED", List.of(), List.of(new RuleDef("r", "R", List.of(), "SET")));
    Map<String, PolicyDef> policies = new LinkedHashMap<>(builder.features.get("f.gate").policies());
    policies.put("open", labelled);
    FeatureDef feature = builder.features.get("f.gate");
    builder.features.put("f.gate", new FeatureDef(feature.decidedPer(),
        Returns.enumeration(List.of("ALLOW", "DENY", "IGNORED", "SET"), false), "FIRST", "DENY",
        feature.bindingSettableAt(), policies, feature.bindings()));
    Decision decision = decide(Context.builder().level("division", "north").build(), FactSet.empty());
    assertEquals("SET", decision.effect());
  }

  @Test
  void FEAT_9_aNullEffectOfANullableEnumIsReturnedAsNull() {
    PolicyDef nullable = new PolicyDef(Map.of(), null, List.of(), List.of());
    Map<String, PolicyDef> policies = new LinkedHashMap<>(builder.features.get("f.gate").policies());
    policies.put("open", nullable);
    FeatureDef feature = builder.features.get("f.gate");
    builder.features.put("f.gate", new FeatureDef(feature.decidedPer(), Returns.enumeration(List.of("ALLOW", "DENY"), true),
        "FIRST", null, feature.bindingSettableAt(), policies, feature.bindings()));
    Decision decision = decide(Context.builder().level("division", "north").build(), FactSet.empty());
    assertNull(decision.effect());
    assertTrue(decision.toJson().containsKey("effect"));
    assertNull(decision.toJson().get("effect"));
  }

  @Test
  void PROV_63_aDecisionHasExactlyTheMembersOfPROV_62() {
    Decision decision = decide(Context.empty(), facts().fact("customer.active", true).build());
    assertEquals(java.util.Set.of("feature", "effect", "bundleVersion", "skippedScopes", "binding", "policy", "reason",
        "trace"), decision.toJson().keySet());
    assertFalse(decision.toJson().containsKey("contributions"));
    assertFalse(decision.toJson().containsKey("source"));
  }

  @Test
  void PROV_2_noEvaluatorOptionChangesTheMemberSetOfADecision() {
    Evaluator plain = TestModels.evaluator(builder, TestModels.failing(), TestModels.failing(), null);
    Evaluator listening = TestModels.evaluator(builder, TestModels.failing(), TestModels.failing(), event -> { });
    Decision a = plain.decide(Context.empty(), "f.gate", FactSet.empty());
    Decision b = listening.decide(Context.empty(), "f.gate", FactSet.empty());
    assertEquals(a.toJson(), b.toJson());
  }

  @Test
  void PROV_83_theLastTraceEntryOfARuleReasonHasTheWinnersId() {
    Decision decision = decide(Context.empty(), facts().fact("customer.active", true).fact("customer.credit_hold", false)
        .child("item", child("DROP")).build());
    List<Decision.TraceEntry> trace = new ArrayList<>(decision.trace());
    assertEquals(decision.reason().id().orElseThrow(), trace.get(trace.size() - 1).id());
  }

  @Test
  void PROV_85_aSerialisedDecisionIsUnorderedSoOnlyArrayOrderMatters() {
    Decision decision = decide(Context.empty(), FactSet.empty());
    Map<String, Object> json = decision.toJson();
    Map<String, Object> reordered = new java.util.TreeMap<>(java.util.Comparator.reverseOrder());
    reordered.putAll(json);
    assertEquals(json, reordered);
  }
}
