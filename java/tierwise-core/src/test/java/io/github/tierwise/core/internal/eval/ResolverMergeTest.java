package io.github.tierwise.core.internal.eval;

import static io.github.tierwise.core.internal.eval.TestModels.mapOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Context;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.Resolved;
import io.github.tierwise.core.Resolved.Contribution.Label;
import io.github.tierwise.core.ScopeKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Combination and provenance (section 5 and section 9.2 to 9.5) through the public evaluator. */
class ResolverMergeTest {

  private final TestModels.Builder builder = TestModels.standard();

  private Evaluator evaluator(TestModels.CountingProvider account) {
    return TestModels.evaluator(builder, account, new TestModels.CountingProvider(Map.of()), null);
  }

  private Evaluator evaluator() {
    return evaluator(new TestModels.CountingProvider(Map.of()));
  }

  @Test
  void MERGE_5_noContributionGivesTheIdentity() {
    Evaluator evaluator = evaluator();
    assertEquals(true, evaluator.resolve(Context.empty(), "k.all").value());
    assertEquals(false, evaluator.resolve(Context.empty(), "k.any").value());
    assertEquals(List.of(), evaluator.resolve(Context.empty(), "k.union").value());
    assertEquals(List.of("red", "green", "blue"), evaluator.resolve(Context.empty(), "k.intersect").value());
  }

  @Test
  void MERGE_6_aMinOrMaxKeyWithoutContributionIsAbsent() {
    Resolved resolved = evaluator().resolve(Context.empty(), "k.max");
    assertNull(resolved.value());
    assertFalse(resolved.hasValue());
    assertFalse(resolved.toJson().containsKey("source"));
    assertTrue(resolved.toJson().containsKey("value"));
    assertNull(resolved.toJson().get("value"));
  }

  @Test
  void MERGE_17_aCommutativeKeyCombinesTheValuesOfTheScopesOnThePath() {
    builder.value("global", "", "k.max", 7L).value("division", "north", "k.max", 40L)
        .value("seller", "acme", "k.max", 90L);
    Evaluator evaluator = evaluator();
    assertEquals(40L, evaluator.resolve(Context.builder().level("division", "north").build(), "k.max").value());
    assertEquals(90L, evaluator.resolve(Context.builder().level("division", "north").axis("seller", "acme").build(),
        "k.max").value());
    assertEquals(7L, evaluator.resolve(Context.empty(), "k.max").value());
  }

  @Test
  void PROV_20_aScopeWhoseValueIsTheIdentityStillHasAnEntryLabelledIgnored() {
    builder.value("global", "", "k.all", true).value("division", "north", "k.all", false);
    Resolved resolved = evaluator().resolve(Context.builder().level("division", "north").build(), "k.all");
    assertEquals(false, resolved.value());
    assertEquals(Label.IGNORED, resolved.contributions().get(0).effect());
    assertEquals(Label.VETO, resolved.contributions().get(1).effect());
  }

  @Test
  void PROV_34_entriesAreInEvaluationPathOrderAndTheRootEntryHasNoNode() {
    builder.value("global", "", "k.any", false).value("division", "north", "k.any", true)
        .value("seller", "acme", "k.any", true);
    Resolved resolved = evaluator().resolve(Context.builder().axis("seller", "acme").level("division", "north").build(),
        "k.any");
    assertEquals(List.of("global", "division", "seller"),
        resolved.contributions().stream().map(Resolved.Contribution::name).toList());
    assertEquals(Optional.empty(), resolved.contributions().get(0).node());
    assertEquals(Optional.of("north"), resolved.contributions().get(1).node());
    assertEquals(ScopeKind.AXIS, resolved.contributions().get(2).scope());
  }

  @Test
  void PROV_43_aScopeAtWhichTheKeyIsNotSettableIsNotListed() {
    builder.value("division", "north", "k.note", "n");
    Resolved resolved = evaluator().resolve(Context.builder().level("division", "north").level("site", "s1").build(),
        "k.note");
    assertEquals(1, resolved.contributions().size());
  }

  @Test
  void PROV_42_aScopeWhoseNodeHasNoValueIsNotListed() {
    builder.value("division", "north", "k.note", "n");
    Resolved resolved = evaluator().resolve(Context.builder().level("division", "south").build(), "k.note");
    assertTrue(resolved.contributions().isEmpty());
    assertEquals("none", resolved.value());
  }

  @Test
  void MERGE_19_anOverrideKeyTakesTheMostSpecificLevelAndNamesItAsTheSource() {
    builder.value("global", "", "k.label", "root").value("division", "north", "k.label", "north-label");
    Resolved resolved = evaluator().resolve(Context.builder().level("division", "north").build(), "k.label");
    assertEquals("north-label", resolved.value());
    assertEquals(Resolved.Source.of(ScopeKind.LEVEL, "division", "north"), resolved.source().orElseThrow());
    assertEquals(List.of(Label.SET, Label.SET), resolved.contributions().stream().map(Resolved.Contribution::effect).toList());
  }

  @Test
  void PROV_47_theRootLevelAsASourceHasNoNode() {
    builder.value("global", "", "k.label", "root");
    Resolved resolved = evaluator().resolve(Context.empty(), "k.label");
    assertEquals(Resolved.Source.of(ScopeKind.LEVEL, "global", null), resolved.source().orElseThrow());
    assertTrue(resolved.source().orElseThrow().node().isEmpty());
  }

  @Test
  void PROV_48_theSourceIsDefaultWhenNoScopeContributes() {
    Resolved resolved = evaluator().resolve(Context.empty(), "k.label");
    assertEquals("standard", resolved.value());
    assertTrue(resolved.source().orElseThrow().isDefault());
    assertEquals("DEFAULT", resolved.toJson().get("source"));
  }

  @Test
  void PROV_45_sourceIsPresentOnlyForOrderDependentKeys() {
    assertTrue(evaluator().resolve(Context.empty(), "k.label").source().isPresent());
    assertTrue(evaluator().resolve(Context.empty(), "k.deep").source().isPresent());
    assertTrue(evaluator().resolve(Context.empty(), "k.all").source().isEmpty());
    assertFalse(evaluator().resolve(Context.empty(), "k.min").toJson().containsKey("source"));
  }

  @Test
  void AXIS_7_theFourCombinationsOfAnOverrideKeyAcrossAxes() {
    builder.value("division", "north", "k.axis_wins", "H").value("seller", "acme", "k.axis_wins", "A")
        .value("division", "north", "k.hierarchy_wins", "H").value("seller", "acme", "k.hierarchy_wins", "A")
        .value("division", "north", "k.max_across", 6L).value("seller", "acme", "k.max_across", 8L)
        .value("division", "north", "k.min_across", 6L).value("seller", "acme", "k.min_across", 8L);
    Context both = Context.builder().level("division", "north").axis("seller", "acme").build();
    Evaluator evaluator = evaluator();
    assertEquals("A", evaluator.resolve(both, "k.axis_wins").value());
    assertEquals("H", evaluator.resolve(both, "k.hierarchy_wins").value());
    assertEquals(8L, evaluator.resolve(both, "k.max_across").value());
    assertEquals(6L, evaluator.resolve(both, "k.min_across").value());
    assertEquals(Resolved.Source.of(ScopeKind.AXIS, "seller", "acme"),
        evaluator.resolve(both, "k.max_across").source().orElseThrow());
    assertEquals(Resolved.Source.of(ScopeKind.LEVEL, "division", "north"),
        evaluator.resolve(both, "k.min_across").source().orElseThrow());
  }

  @Test
  void AXIS_8_onATieTheSourceIsTheHierarchyScope() {
    builder.value("division", "north", "k.max_across", 8L).value("seller", "acme", "k.max_across", 8L);
    Resolved resolved = evaluator().resolve(Context.builder().level("division", "north").axis("seller", "acme").build(),
        "k.max_across");
    assertEquals(8L, resolved.value());
    assertEquals(Resolved.Source.of(ScopeKind.LEVEL, "division", "north"), resolved.source().orElseThrow());
    assertEquals(2, resolved.contributions().size());
  }

  @Test
  void MERGE_20_theLastDeclaredContributingAxisIsTheAxisResult() {
    builder.value("seller", "acme", "k.axis_wins", "S").value("promo", "spring", "k.axis_wins", "P");
    Resolved resolved = evaluator().resolve(Context.builder().axis("seller", "acme").axis("promo", "spring").build(),
        "k.axis_wins");
    assertEquals("P", resolved.value());
    assertEquals(Resolved.Source.of(ScopeKind.AXIS, "promo", "spring"), resolved.source().orElseThrow());
  }

  @Test
  void MERGE_23_deepMergeFoldsTheLevelsFromTheRootAndMergesOverTheDefault() {
    builder.value("division", "north", "k.deep", mapOf("cap", 20L))
        .value("global", "", "k.deep", mapOf("unit", "boxes"));
    Resolved resolved = evaluator().resolve(Context.builder().level("division", "north").build(), "k.deep");
    assertEquals(mapOf("cap", 20L, "unit", "boxes", "tags", List.of("a")), resolved.value());
    assertEquals(Resolved.Source.of(ScopeKind.LEVEL, "division", "north"), resolved.source().orElseThrow());
    assertEquals(Map.of("cap", 20L), resolved.contributions().get(1).value().orElseThrow(),
        "an entry holds the value the scope contributes, not a running result (PROV-28)");
  }

  @Test
  void AXIS_7_deepMergeOrdersTheFoldByCombineAcrossAxes() {
    builder.value("division", "north", "k.deep", mapOf("cap", 20L)).value("seller", "acme", "k.deep", mapOf("cap", 25L))
        .value("division", "north", "k.deep_hierarchy", mapOf("cap", 20L))
        .value("seller", "acme", "k.deep_hierarchy", mapOf("cap", 25L, "unit", "boxes"));
    Context both = Context.builder().level("division", "north").axis("seller", "acme").build();
    Evaluator evaluator = evaluator();
    assertEquals(25L, ((Map<?, ?>) evaluator.resolve(both, "k.deep").value()).get("cap"));
    Resolved hierarchy = evaluator.resolve(both, "k.deep_hierarchy");
    assertEquals(20L, ((Map<?, ?>) hierarchy.value()).get("cap"));
    assertEquals("boxes", ((Map<?, ?>) hierarchy.value()).get("unit"));
    assertEquals(Resolved.Source.of(ScopeKind.LEVEL, "division", "north"), hierarchy.source().orElseThrow());
  }

  @Test
  void MERGE_10_deepMergeWithoutADefaultStartsFromTheRootValue() {
    builder.value("global", "", "k.deep_root", mapOf("cap", 1L, "unit", "u", "tags", List.of()))
        .value("division", "north", "k.deep_root", mapOf("cap", 2L));
    Resolved resolved = evaluator().resolve(Context.builder().level("division", "north").build(), "k.deep_root");
    assertEquals(mapOf("cap", 2L, "unit", "u", "tags", List.of()), resolved.value());
  }

  @Test
  void MERGE_21_anOverrideValueReplacesAnObjectAsAWhole() {
    builder.value("global", "", "k.object_override", mapOf("from", "09:00", "to", "17:00"))
        .value("division", "north", "k.object_override", mapOf("from", "10:00"));
    builder.key("k.object_override", TestModels.key(builder.keys.get("k.object_override").type(), "override",
        mapOf("from", "08:00"), List.of("global", "division", "account"), null, "skip"));
    Resolved resolved = evaluator().resolve(Context.builder().level("division", "north").build(), "k.object_override");
    assertEquals(mapOf("from", "10:00"), resolved.value());
  }

  @Test
  void PROV_14_kindIsPresentIfAndOnlyIfTheKeyOptsIntoOne() {
    Evaluator evaluator = evaluator();
    assertEquals(Optional.of("capability"), evaluator.resolve(Context.empty(), "k.kinded").kind());
    assertEquals(Optional.empty(), evaluator.resolve(Context.empty(), "k.all").kind());
    assertFalse(evaluator.resolve(Context.empty(), "k.all").toJson().containsKey("kind"));
    assertEquals("capability", evaluator.resolve(Context.empty(), "k.kinded").toJson().get("kind"));
  }

  @Test
  void PROV_10_theResultCarriesKeyStrategyAndBundleVersion() {
    Resolved resolved = evaluator().resolve(Context.empty(), "k.union");
    assertEquals("k.union", resolved.key());
    assertEquals("union", resolved.strategy());
    assertEquals(TestModels.VERSION, resolved.bundleVersion());
    assertEquals(List.of(), resolved.skippedScopes());
  }

  @Test
  void PROV_16_aSetResultIsInUniverseOrderEvenWhenSourcesListItDifferently() {
    builder.value("global", "", "k.union", List.of("blue")).value("division", "north", "k.union", List.of("red"));
    Resolved resolved = evaluator().resolve(Context.builder().level("division", "north").build(), "k.union");
    assertEquals(List.of("red", "blue"), resolved.value());
  }

  @Test
  void PROV_60_resolveAndDecideOverOneContextReportTheSameSkippedScopes() {
    Context context = Context.builder().level("site", "s9").level("division", "north").axis("promo", "autumn").build();
    Evaluator evaluator = evaluator();
    List<?> viaKey = evaluator.resolve(context, "k.all").skippedScopes();
    assertEquals(2, viaKey.size());
    assertEquals(viaKey, evaluator.resolve(context, "k.union").skippedScopes());
    assertEquals(viaKey, evaluator.decide(context, "f.gate", io.github.tierwise.core.FactSet.empty()).skippedScopes());
  }

  @Test
  void CTX_15_aSkippedScopeContributesNothing() {
    builder.value("site", "s1", "k.label", "site-label");
    Resolved resolved = evaluator().resolve(Context.builder().level("site", "s9").build(), "k.label");
    assertEquals("standard", resolved.value());
    assertTrue(resolved.contributions().isEmpty());
    assertEquals(1, resolved.skippedScopes().size());
  }
}
