package io.github.tierwise.core.internal.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.ScopeKind;
import io.github.tierwise.core.internal.eval.AxisCombination.Contributed;
import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** AXIS-7 and AXIS-8, MERGE-19 to MERGE-25 and the source rules PROV-50 to PROV-53. */
class AxisCombinationTest {

  private static final PathScope ROOT = new PathScope("global", ScopeKind.LEVEL, null);
  private static final PathScope DIVISION = new PathScope("division", ScopeKind.LEVEL, "north");
  private static final PathScope ACCOUNT = new PathScope("account", ScopeKind.LEVEL, "a1");
  private static final PathScope SELLER = new PathScope("seller", ScopeKind.AXIS, "acme");
  private static final PathScope PROMO = new PathScope("promo", ScopeKind.AXIS, "spring");

  private static final TypeDecl DEEP = TestModels.standard().keys.get("k.deep").type();

  private static Contributed at(PathScope scope, Object value) {
    return new Contributed(scope, value);
  }

  private static Map<String, Object> map(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }

  @Test
  void AXIS_7_neitherPresentGivesTheDefaultWithSourceDefault() {
    AxisCombination.Outcome outcome = AxisCombination.override("d", List.of(), List.of(), Optional.of("axis_wins"));
    assertEquals("d", outcome.value());
    assertTrue(outcome.source().isEmpty());
  }

  @Test
  void AXIS_7_onlyTheHierarchyGivesTheMostSpecificLevel() {
    AxisCombination.Outcome outcome = AxisCombination.override("d", List.of(at(ROOT, "r"), at(DIVISION, "v")), List.of(),
        Optional.empty());
    assertEquals("v", outcome.value());
    assertEquals(Optional.of(DIVISION), outcome.source());
  }

  @Test
  void AXIS_7_onlyAnAxisGivesTheLastDeclaredAxis() {
    AxisCombination.Outcome outcome = AxisCombination.override("d", List.of(), List.of(at(SELLER, "s"), at(PROMO, "p")),
        Optional.of("hierarchy_wins"));
    assertEquals("p", outcome.value());
    assertEquals(Optional.of(PROMO), outcome.source());
  }

  @Test
  void AXIS_7_axisWinsTakesTheAxisResult() {
    AxisCombination.Outcome outcome = AxisCombination.override("d", List.of(at(DIVISION, "h")), List.of(at(SELLER, "a")),
        Optional.of("axis_wins"));
    assertEquals("a", outcome.value());
    assertEquals(Optional.of(SELLER), outcome.source());
  }

  @Test
  void AXIS_7_hierarchyWinsTakesTheHierarchyResult() {
    AxisCombination.Outcome outcome = AxisCombination.override("d", List.of(at(DIVISION, "h")), List.of(at(SELLER, "a")),
        Optional.of("hierarchy_wins"));
    assertEquals("h", outcome.value());
    assertEquals(Optional.of(DIVISION), outcome.source());
  }

  @Test
  void AXIS_7_maxTakesTheLargerOfHierarchyAndAxis() {
    Optional<String> max = Optional.of("max");
    assertEquals(8L, AxisCombination.override(3L, List.of(at(DIVISION, 6L)), List.of(at(SELLER, 8L)), max).value());
    assertEquals(Optional.of(SELLER),
        AxisCombination.override(3L, List.of(at(DIVISION, 6L)), List.of(at(SELLER, 8L)), max).source());
    assertEquals(9L, AxisCombination.override(3L, List.of(at(DIVISION, 9L)), List.of(at(SELLER, 8L)), max).value());
  }

  @Test
  void AXIS_7_minTakesTheSmallerOfHierarchyAndAxis() {
    Optional<String> min = Optional.of("min");
    assertEquals(6L, AxisCombination.override(3L, List.of(at(DIVISION, 6L)), List.of(at(SELLER, 8L)), min).value());
    assertEquals(Optional.of(DIVISION),
        AxisCombination.override(3L, List.of(at(DIVISION, 6L)), List.of(at(SELLER, 8L)), min).source());
    assertEquals(2L, AxisCombination.override(3L, List.of(at(DIVISION, 6L)), List.of(at(SELLER, 2L)), min).value());
  }

  @Test
  void AXIS_8_onATieTheHierarchyScopeIsTheSource() {
    for (String combine : List.of("max", "min")) {
      AxisCombination.Outcome outcome = AxisCombination.override(3L, List.of(at(ROOT, 1L), at(DIVISION, 7L)),
          List.of(at(SELLER, 7L)), Optional.of(combine));
      assertEquals(7L, outcome.value());
      assertEquals(Optional.of(DIVISION), outcome.source(), combine);
    }
  }

  @Test
  void MERGE_19_theHierarchyResultIsTheMostSpecificContributingLevel() {
    AxisCombination.Outcome outcome = AxisCombination.override("d", List.of(at(ROOT, "r"), at(DIVISION, "v"), at(ACCOUNT, "x")),
        List.of(), Optional.empty());
    assertEquals("x", outcome.value());
    assertEquals(Optional.of(ACCOUNT), outcome.source());
  }

  @Test
  void MERGE_21_anOverrideValueReplacesAnObjectAsAWhole() {
    Map<String, Object> winner = map("cap", 1L);
    AxisCombination.Outcome outcome = AxisCombination.override(map("cap", 10L, "unit", "items"), List.of(at(DIVISION, winner)),
        List.of(), Optional.empty());
    assertEquals(map("cap", 1L), outcome.value());
  }

  @Test
  void MERGE_25_deepMergeWithoutContributionsIsTheDefaultAndSourceDefault() {
    AxisCombination.Outcome outcome = AxisCombination.deepMerge(DEEP, map("cap", 10L), List.of(), List.of(),
        Optional.of("axis_wins"));
    assertEquals(map("cap", 10L), outcome.value());
    assertTrue(outcome.source().isEmpty());
  }

  @Test
  void AXIS_7_deepMergeHierarchyAloneIsTheDefaultMergedWithH() {
    AxisCombination.Outcome outcome = AxisCombination.deepMerge(DEEP, map("cap", 10L, "unit", "items"),
        List.of(at(ROOT, map("unit", "boxes")), at(DIVISION, map("cap", 20L))), List.of(), Optional.empty());
    assertEquals(map("cap", 20L, "unit", "boxes"), outcome.value());
    assertEquals(Optional.of(DIVISION), outcome.source());
  }

  @Test
  void AXIS_7_deepMergeAxisAloneIsTheDefaultMergedWithA() {
    AxisCombination.Outcome outcome = AxisCombination.deepMerge(DEEP, map("cap", 10L, "unit", "items"), List.of(),
        List.of(at(SELLER, map("cap", 25L)), at(PROMO, map("unit", "boxes"))), Optional.of("axis_wins"));
    assertEquals(map("cap", 25L, "unit", "boxes"), outcome.value());
    assertEquals(Optional.of(PROMO), outcome.source());
  }

  @Test
  void AXIS_7_deepMergeAxisWinsFoldsDHThenA() {
    AxisCombination.Outcome outcome = AxisCombination.deepMerge(DEEP, map("cap", 10L, "unit", "items"),
        List.of(at(DIVISION, map("cap", 20L, "unit", "boxes"))), List.of(at(SELLER, map("cap", 25L))),
        Optional.of("axis_wins"));
    assertEquals(map("cap", 25L, "unit", "boxes"), outcome.value());
    assertEquals(Optional.of(SELLER), outcome.source());
  }

  @Test
  void AXIS_7_deepMergeHierarchyWinsFoldsDAThenH() {
    AxisCombination.Outcome outcome = AxisCombination.deepMerge(DEEP, map("cap", 10L, "unit", "items"),
        List.of(at(DIVISION, map("cap", 20L))), List.of(at(SELLER, map("cap", 25L, "unit", "boxes"))),
        Optional.of("hierarchy_wins"));
    assertEquals(map("cap", 20L, "unit", "boxes"), outcome.value());
    assertEquals(Optional.of(DIVISION), outcome.source());
  }

  @Test
  void MERGE_25_withoutADefaultTheFoldStartsAtTheFirstPresentTerm() {
    AxisCombination.Outcome outcome = AxisCombination.deepMerge(DEEP, null,
        List.of(at(ROOT, map("cap", 1L, "unit", "u"))), List.of(at(SELLER, map("cap", 2L))), Optional.of("axis_wins"));
    assertEquals(map("cap", 2L, "unit", "u"), outcome.value());
    assertNull(AxisCombination.override(null, List.of(), List.of(), Optional.empty()).value());
  }
}
