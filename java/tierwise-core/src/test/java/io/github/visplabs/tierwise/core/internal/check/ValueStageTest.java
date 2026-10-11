package io.github.visplabs.tierwise.core.internal.check;

import static io.github.visplabs.tierwise.core.internal.check.CheckTestSupport.assertFindings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.internal.model.Values;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Authored values: KEY-10 to KEY-13, MERGE-10 to MERGE-12, MERGE-26, KIND-6, SCOPE-38 to SCOPE-46, SRC-32. */
class ValueStageTest {

  private static final String KEYS = """
      keys:
        flag:
          type: boolean
          combine: any
          settable_at: [global, division]
        limit:
          type: integer
          min: 0
          max: 10
          combine: override
          default: 1
          settable_at: [global, division, seller]
          combine_across_axes: max
        methods:
          type: set
          universe: [A, B, C]
          combine: union
          settable_at: [global, division]
        window:
          type: object
          fields:
            open: { type: string }
            close: { type: string }
            slot:
              type: object
              optional: true
              fields:
                from: { type: string }
                to: { type: string }
          combine: deep_merge
          default: { open: a, close: b }
          settable_at: [global, division]
        framed:
          type: object
          fields:
            open: { type: string }
            slot:
              type: object
              optional: true
              fields:
                from: { type: string }
                to: { type: string }
          combine: deep_merge
          default: { open: a, slot: { from: x, to: y } }
          settable_at: [global, division]
        profile:
          type: object
          fields:
            name: { type: string }
            email: { type: string }
          combine: deep_merge
          settable_at: [global, division]
        division_only:
          type: string
          combine: override
          default: x
          settable_at: [division]
        global_only:
          type: string
          combine: override
          default: x
          settable_at: [global]
      """;

  private static final String KINDS = """
      kinds:
        narrow: { strategies: [all, intersect, min], narrowing_only: true }
      """;

  private static final String NARROW_KEYS = """
      keys:
        perm:
          type: boolean
          combine: all
          kind: narrow
          settable_at: [global, division]
        perm_set:
          type: set
          universe: [A, B]
          combine: intersect
          kind: narrow
          settable_at: [global, division]
        perm_min:
          type: integer
          combine: min
          kind: narrow
          settable_at: [global, division]
        plain:
          type: boolean
          combine: all
          settable_at: [global, division]
      """;

  @TempDir
  Path dir;

  private KeyValueHarness project() {
    return new KeyValueHarness(dir).schema(KEYS);
  }

  /** A project with the keys, a root value for {@code profile}, and the given root values. */
  private CheckContext root(String yaml) {
    return project().root("profile: { name: n, email: e }\n" + yaml).run();
  }

  /** A project with the keys, a root value for {@code profile}, and the given multi-node values. */
  private CheckContext nodes(String yaml) {
    return project().root("profile: { name: n, email: e }\n").nodes(yaml).run();
  }

  private CheckContext narrow(String root) {
    return new KeyValueHarness(dir).schema(KINDS).schema(NARROW_KEYS).root(root).run();
  }

  // ---- keys -------------------------------------------------------------------------------------

  @Test
  void KEY_10_aValueForAKeyThatIsNotDeclaredIsEUnknownKeyAtTheValueWithASuggestion() {
    CheckContext context = project().root("profile: { name: n, email: e }\nflg: true\n").run();
    assertFindings(context.findings(), "E_UNKNOWN_KEY 2 [flg]");
    assertEquals(Optional.of("flag"), context.findings().sorted().get(0).suggestion());
  }

  @Test
  void KEY_10_theKeyIsLocatedAtItsEntryInAMultiNodeFile() {
    assertFindings(nodes("""
        division:
          north:
            flg: true
        """).findings(), "E_UNKNOWN_KEY 3 [division, north, flg]");
  }

  @Test
  void KEY_10_aKeyIsComparedByExactCodePoints() {
    assertFindings(root("Flag: true\n").findings(), "E_UNKNOWN_KEY 2 [Flag]");
  }

  @Test
  void KEY_11_aValueAtADeclaredScopeThatSettableAtDoesNotListIsENotSettableHere() {
    assertFindings(nodes("""
        division:
          north:
            global_only: y
        """).findings(), "E_NOT_SETTABLE_HERE 3 [division, north, global_only]");
  }

  @Test
  void KEY_11_theRootLevelCountsAsAScope() {
    assertFindings(root("division_only: y\n").findings(), "E_NOT_SETTABLE_HERE 2 [division_only]");
  }

  @Test
  void KEY_11_anAxisIsAScopeToo() {
    assertFindings(nodes("""
        seller:
          acme:
            flag: true
        """).findings(), "E_NOT_SETTABLE_HERE 3 [seller, acme, flag]");
  }

  @Test
  void KEY_11_aValueAtAScopeInSettableAtIsAccepted() {
    assertFindings(nodes("""
        division:
          north:
            flag: true
            limit: 2
        seller:
          acme:
            limit: 3
        """).findings());
  }

  @Test
  void KEY_12_aSecondValueForOneKeyAtOneScopeAndNodeIsEDuplicateValueAtTheLaterSource() {
    // values/nodes.yaml sorts before values/root.yaml, so the root file holds the later occurrence.
    assertFindings(project().root("profile: { name: n, email: e }\nlimit: 2\n").nodes("global:\n  limit: 3\n").run()
        .findings(), "E_DUPLICATE_VALUE 2 [limit]");
  }

  @Test
  void KEY_12_theSameKeyAtAnotherNodeOrScopeIsNotADuplicate() {
    assertFindings(nodes("""
        division:
          north:
            limit: 2
          south:
            limit: 2
        seller:
          acme:
            limit: 2
        """).findings());
  }

  // ---- scopes and nodes ---------------------------------------------------------------------------

  @Test
  void SCOPE_46_aScopeThatIsNotDeclaredIsEUnknownScopeAtTheScopeEntry() {
    CheckContext context = nodes("""
        region:
          north:
            flag: true
          south:
            flag: true
        """);
    assertFindings(context.findings(), "E_UNKNOWN_SCOPE 1 [region]");
    assertTrue(context.parts().values().isEmpty());
  }

  @Test
  void SCOPE_46_aScopeNameIsASuggestionCandidate() {
    CheckContext context = nodes("divsion:\n  north:\n    flag: true\n");
    assertFindings(context.findings(), "E_UNKNOWN_SCOPE 1 [divsion]");
    assertEquals(Optional.of("division"), context.findings().sorted().get(0).suggestion());
  }

  @Test
  void SCOPE_43_aNodeThatTheClosedCatalogDoesNotListIsEUnknownNodeAtTheNodeEntry() {
    assertFindings(nodes("""
        division:
          west:
            flag: true
        """).findings(), "E_UNKNOWN_NODE 2 [division, west]");
  }

  @Test
  void SCOPE_43_theCatalogOfAnAxisIsClosedToo() {
    assertFindings(nodes("""
        seller:
          globex:
            limit: 2
        """).findings(), "E_UNKNOWN_NODE 2 [seller, globex]");
  }

  @Test
  void SCOPE_43_anUnknownNodeCarriesASuggestionFromTheCatalog() {
    CheckContext context = nodes("division:\n  nroth:\n    flag: true\n");
    assertFindings(context.findings(), "E_UNKNOWN_NODE 2 [division, nroth]");
    assertEquals(Optional.of("north"), context.findings().sorted().get(0).suggestion());
  }

  @Test
  void SCOPE_38_aNodeIdThatBreaksTheStructuralContractIsEInvalidNodeIdAndNotAlsoUnknown() {
    assertFindings(nodes("""
        division:
          "":
            flag: true
        """).findings(), "E_INVALID_NODE_ID 2 [division, ]");
  }

  @Test
  void SCOPE_34_aNodeIdWithAControlCharacterIsEInvalidNodeId() {
    assertFindings(nodes("division:\n  \"a\\tb\":\n    flag: true\n").findings(), "E_INVALID_NODE_ID 2 [division, a\tb]");
  }

  @Test
  void SCOPE_33_aNodeIdOfMoreThan128CodePointsIsEInvalidNodeId() {
    String longId = "a".repeat(129);
    assertFindings(nodes("division:\n  " + longId + ":\n    flag: true\n").findings(),
        "E_INVALID_NODE_ID 2 [division, " + longId + "]");
  }

  @Test
  void SRC_32_withoutAHierarchyTheScopeAndNodeChecksAreSkipped() {
    CheckContext context = project().root("profile: { name: n, email: e }\n")
        .nodes("region:\n  west:\n    flag: true\n").run(false);
    assertFindings(context.findings());
    assertTrue(context.parts().values().isEmpty());
  }

  // ---- types ------------------------------------------------------------------------------------

  @Test
  void KEY_13_aValueOfTheWrongTypeIsETypeMismatchAtTheValue() {
    assertFindings(root("limit: x\nflag: 1\n").findings(), "E_TYPE_MISMATCH 2 [limit]",
        "E_TYPE_MISMATCH 3 [flag]");
  }

  @Test
  void TYPE_76_aNullIsNotAValue() {
    assertFindings(root("limit:\n").findings(), "E_TYPE_MISMATCH 2 [limit]");
  }

  @Test
  void TYPE_21_anIntegerOutsideItsBoundsIsEOutOfRange() {
    assertFindings(root("limit: 11\n").findings(), "E_OUT_OF_RANGE 2 [limit]");
  }

  @Test
  void TYPE_79_anIntegerOutsideTheEngineRangeAndItsBoundsGetsBothFindings() {
    assertFindings(root("limit: 99999999999999999\n").findings(), "E_INTEGER_RANGE 2 [limit]",
        "E_OUT_OF_RANGE 2 [limit]");
  }

  @Test
  void TYPE_48_aSetMemberOutsideTheUniverseIsENotInUniverseAtThatMember() {
    assertFindings(root("methods: [A, Z]\n").findings(), "E_NOT_IN_UNIVERSE 2 [methods, 1]");
  }

  @Test
  void TYPE_52_aRepeatedSetMemberIsEDuplicateValueAtTheLaterMember() {
    assertFindings(root("methods: [A, B, A]\n").findings(), "E_DUPLICATE_VALUE 2 [methods, 2]");
  }

  @Test
  void TYPE_50_aSetIsStoredInUniverseOrder() {
    CheckContext context = root("methods: [C, A]\n");
    assertFindings(context.findings());
    assertEquals(List.of("A", "C"), context.parts().values().orElseThrow().get("global", "", "methods"));
  }

  @Test
  void TYPE_71_everyInvalidElementOfAValueIsReported() {
    assertFindings(root("methods: [Z, Y]\n").findings(), "E_NOT_IN_UNIVERSE 2 [methods, 0]",
        "E_NOT_IN_UNIVERSE 2 [methods, 1]");
  }

  // ---- objects ----------------------------------------------------------------------------------

  @Test
  void MERGE_11_aValueOtherThanTheStartingValueMayBePartial() {
    assertFindings(nodes("""
        division:
          north:
            window:
              close: z
            profile:
              email: e
        """).findings());
  }

  @Test
  void MERGE_10_aRootValueMayBePartialWhenTheKeyHasADefault() {
    assertFindings(root("window:\n  open: z\n").findings());
  }

  @Test
  void MERGE_10_theRootValueOfAKeyWithoutDefaultIsTheStartingValueAndMustBeComplete() {
    assertFindings(project().root("profile:\n  name: n\n").run().findings(), "E_TYPE_MISMATCH 1 [profile]");
  }

  @Test
  void MERGE_12_aPartialValueStillRejectsAnUnknownField() {
    assertFindings(nodes("""
        division:
          north:
            window:
              close: z
              lunch: y
        """).findings(), "E_TYPE_MISMATCH 5 [division, north, window, lunch]");
  }

  @Test
  void TYPE_63_anObjectThatIsNotPartialAndLacksARequiredFieldIsETypeMismatchAtTheObject() {
    assertFindings(new KeyValueHarness(dir).schema("""
        keys:
          contact:
            type: object
            fields:
              name: { type: string }
              phone: { type: string }
            combine: override
            default: { name: n, phone: p }
            settable_at: [global]
        """).root("contact:\n  name: n\n").run().findings(), "E_TYPE_MISMATCH 1 [contact]");
  }

  @Test
  void MERGE_26_aValueThatSetsAnOptionalObjectFieldTheStartDoesNotSetMustGiveItComplete() {
    assertFindings(nodes("""
        division:
          north:
            window:
              slot:
                from: x
        """).findings(), "E_TYPE_MISMATCH 4 [division, north, window, slot]");
  }

  @Test
  void MERGE_26_aCompleteObjectForThatFieldIsAccepted() {
    assertFindings(nodes("""
        division:
          north:
            window:
              slot:
                from: x
                to: y
        """).findings());
  }

  @Test
  void MERGE_26_whereTheStartSetsTheFieldTheValueMayStayPartial() {
    assertFindings(nodes("""
        division:
          north:
            framed:
              slot:
                from: z
        """).findings());
  }

  @Test
  void MERGE_26_theStartingValueCanBeTheRootValueOfAKeyWithoutDefault() {
    CheckContext context = new KeyValueHarness(dir).schema("""
        keys:
          shape:
            type: object
            fields:
              open: { type: string }
              slot:
                type: object
                optional: true
                fields:
                  from: { type: string }
                  to: { type: string }
            combine: deep_merge
            settable_at: [global, division]
        """).root("shape: { open: a, slot: { from: x, to: y } }\n").nodes("""
        division:
          north:
            shape:
              slot:
                from: z
        """).run();
    assertFindings(context.findings());
  }

  // ---- kinds ------------------------------------------------------------------------------------

  @Test
  void KIND_6_anAuthoredTrueUnderANarrowingOnlyKindThatUsesAllIsEKindViolationAtTheValue() {
    assertFindings(narrow("perm: true\n").findings(), "E_KIND_VIOLATION 1 [perm]");
  }

  @Test
  void KIND_6_anAuthoredFalseIsLegalUnderAll() {
    assertFindings(narrow("perm: false\n").findings());
  }

  @Test
  void KIND_6_theFullUniverseIsTheIdentityOfIntersectInAnyOrder() {
    assertFindings(narrow("perm_set: [B, A]\n").findings(), "E_KIND_VIOLATION 1 [perm_set]");
  }

  @Test
  void KIND_6_everyStrictSubsetOfTheUniverseIsLegalUnderIntersect() {
    assertFindings(narrow("perm_set: [A]\n").findings());
    assertFindings(narrow("perm_set: []\n").findings());
  }

  @Test
  void KIND_6_everyValidValueIsLegalUnderMin() {
    assertFindings(narrow("perm_min: 0\n").findings());
    assertFindings(narrow("perm_min: 9007199254740991\n").findings());
  }

  @Test
  void KIND_6_aKeyWithoutTheKindIsNotConstrained() {
    assertFindings(narrow("plain: true\n").findings());
  }

  @Test
  void KIND_7_theCheckLooksAtEachAuthoredValueAlone() {
    CheckContext context = new KeyValueHarness(dir).schema(KINDS).schema(NARROW_KEYS).root("perm: false\n")
        .nodes("division:\n  north:\n    perm: false\n").run();
    assertFindings(context.findings());
  }

  // ---- suppression and the model ------------------------------------------------------------------

  @Test
  void FIND_35_aKeyWhoseTypeIsInvalidGetsNoValueCheckButIsStillDeclared() {
    CheckContext context = new KeyValueHarness(dir).schema("""
        keys:
          bad:
            type: nothing
            combine: all
            settable_at: [global]
        """).root("bad: 5\n").run();
    assertFindings(context.findings(), "E_INVALID_DECLARATION 3 [keys, bad, type]");
  }

  @Test
  void KEY_stage_theValuesOfAValidProjectGoIntoTheModelByScopeNodeAndKey() {
    CheckContext context = nodes("""
        division:
          north:
            flag: true
            limit: 4
          south:
            limit: 5
        seller:
          acme:
            limit: 6
        """);
    assertFindings(context.findings());
    Values values = context.parts().values().orElseThrow();
    assertEquals(Map.of("name", "n", "email", "e"), values.get("global", "", "profile"));
    assertEquals(true, values.get("division", "north", "flag"));
    assertEquals(4L, values.get("division", "north", "limit"));
    assertEquals(5L, values.get("division", "south", "limit"));
    assertEquals(6L, values.get("seller", "acme", "limit"));
    assertNull(values.get("division", "north", "methods"));
  }

  @Test
  void KEY_stage_aFaultLeavesTheValuesPartUnbuilt() {
    assertTrue(root("limit: x\n").parts().values().isEmpty());
  }

  @Test
  void KEY_stage_aProjectWithoutValuesHasEmptyValues() {
    CheckContext context = new KeyValueHarness(dir).schema("keys: {}\n").run();
    assertFindings(context.findings());
    assertEquals(Values.EMPTY, context.parts().values().orElseThrow());
  }
}
