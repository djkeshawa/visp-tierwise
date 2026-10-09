package io.github.tierwise.core.internal.check;

import static io.github.tierwise.core.internal.check.CheckTestSupport.assertFindings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Finding;
import io.github.tierwise.core.internal.model.KeyDef;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Key declarations: KEY-1 to KEY-9, MERGE-3 to MERGE-10, AXIS-2 to AXIS-6, KIND-4 to KIND-13, SVC-1 to SVC-12. */
class KeyStageTest {

  private static final String KINDS = """
      kinds:
        flag: { strategies: [all, any] }
        narrow: { strategies: [all, intersect, min], narrowing_only: true, on_source_failure: deny }
        defaulted: { strategies: [override], require_default: true }
        skipper: { on_source_failure: skip }
      """;

  @TempDir
  Path dir;

  private CheckContext run(String schema) {
    return new KeyValueHarness(dir).schema(schema).run();
  }

  private CheckContext runWithKinds(String schema) {
    return new KeyValueHarness(dir).schema(KINDS).schema(schema).run();
  }

  private static List<String> descriptions(CheckContext context) {
    return CheckTestSupport.descriptions(context.findings());
  }

  private static KeyInfo info(CheckContext context, String name) {
    return context.find(KeyTable.class).orElseThrow().keys().get(name);
  }

  // ---- members ----------------------------------------------------------------------------------

  @Test
  void KEY_2_aDeclarationWithoutCombineIsEInvalidDeclarationAtTheDeclaration() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            settable_at: [global]
        """).findings(), "E_INVALID_DECLARATION 2 [keys, k]");
  }

  @Test
  void KEY_2_aDeclarationWithoutSettableAtIsEInvalidDeclarationAtTheDeclaration() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            combine: all
        """).findings(), "E_INVALID_DECLARATION 2 [keys, k]");
  }

  @Test
  void TYPE_4_aDeclarationWithoutTypeIsETypeRequiredAtTheDeclaration() {
    assertFindings(run("""
        keys:
          k:
            combine: all
            settable_at: [global]
        """).findings(), "E_TYPE_REQUIRED 2 [keys, k]");
  }

  @Test
  void KEY_3_aMemberThatKey3DoesNotAllowIsEInvalidDeclarationAtTheMember() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            combine: all
            settable_at: [global]
            notes: x
        """).findings(), "E_INVALID_DECLARATION 6 [keys, k, notes]");
  }

  @Test
  void KEY_3_aTypeMemberThatTheTypeDoesNotDefineIsReportedByTheTypeGrammar() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            min: 1
            combine: all
            settable_at: [global]
        """).findings(), "E_INVALID_DECLARATION 4 [keys, k, min]");
  }

  @Test
  void KEY_3_optionalOnAKeyIsEInvalidDeclarationAtTheMember() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            optional: true
            combine: all
            settable_at: [global]
        """).findings(), "E_INVALID_DECLARATION 4 [keys, k, optional]");
  }

  @Test
  void KEY_5_aDeclarationThatIsNotAMappingIsEInvalidDeclarationAtTheDeclaration() {
    CheckContext context = run("""
        keys:
          k: [boolean]
        """);
    assertFindings(context.findings(), "E_INVALID_DECLARATION 2 [keys, k]");
    assertFalse(info(context, "k").checkable());
  }

  @Test
  void KEY_5_aCombineThatNamesNoStrategyIsEInvalidDeclarationAtCombine() {
    assertFindings(run("""
        keys:
          a:
            type: boolean
            combine: sometimes
            settable_at: [global]
          b:
            type: boolean
            combine: Override
            settable_at: [global]
        """).findings(), "E_INVALID_DECLARATION 4 [keys, a, combine]", "E_INVALID_DECLARATION 8 [keys, b, combine]");
  }

  @Test
  void KEY_5_aSettableAtThatIsNotAListIsEInvalidDeclarationAtTheMember() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            combine: all
            settable_at: global
        """).findings(), "E_INVALID_DECLARATION 5 [keys, k, settable_at]");
  }

  @Test
  void KEY_6_anEntryOfSettableAtThatIsNotAStringIsEInvalidDeclarationAtTheEntry() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            combine: all
            settable_at: [global, 5]
        """).findings(), "E_INVALID_DECLARATION 5 [keys, k, settable_at, 1]");
  }

  @Test
  void KEY_7_anEmptySettableAtIsEInvalidDeclarationAtTheList() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            combine: all
            settable_at: []
        """).findings(), "E_INVALID_DECLARATION 5 [keys, k, settable_at]");
  }

  @Test
  void KEY_7_aScopeNameRepeatedInSettableAtIsEDuplicateValueAtTheLaterItem() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            combine: all
            settable_at: [global, division, global]
        """).findings(), "E_DUPLICATE_VALUE 5 [keys, k, settable_at, 2]");
  }

  @Test
  void KEY_7_aRepeatAfterNormalisationIsEDuplicateValue() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            combine: all
            settable_at: [global, "caf\\u00e9", "cafe\\u0301"]
        """).findings(), "E_DUPLICATE_VALUE 5 [keys, k, settable_at, 2]", "E_UNKNOWN_SCOPE 5 [keys, k, settable_at, 1]");
  }

  @Test
  void KEY_6_aNameThatIsNoDeclaredScopeIsEUnknownScopeAtTheItemWithASuggestion() {
    CheckContext context = run("""
        keys:
          k:
            type: boolean
            combine: all
            settable_at: [global, divison]
        """);
    assertFindings(context.findings(), "E_UNKNOWN_SCOPE 5 [keys, k, settable_at, 1]");
    Finding finding = context.findings().sorted().get(0);
    assertEquals(Optional.of("division"), finding.suggestion());
  }

  @Test
  void KEY_6_aLevelAnAxisTheRootAndAServiceSourcedLevelAreAllowed() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            combine: all
            settable_at: [global, division, account, seller]
            on_source_failure: deny
        """).findings());
  }

  @Test
  void KEY_9_aDescriptionOrOwnerThatIsNotAStringIsEInvalidDeclarationAtTheMember() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            combine: all
            settable_at: [global]
            description: 5
            owner: [a]
        """).findings(), "E_INVALID_DECLARATION 6 [keys, k, description]",
        "E_INVALID_DECLARATION 7 [keys, k, owner]");
  }

  @Test
  void KEY_5_anOnSourceFailureThatIsNeitherSkipNorDenyIsEInvalidDeclarationAtTheMember() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            combine: all
            settable_at: [global]
            on_source_failure: maybe
        """).findings(), "E_INVALID_DECLARATION 6 [keys, k, on_source_failure]");
  }

  @Test
  void KEY_1_aRepeatedDeclarationIsNotCheckedAgain() {
    CheckContext context = new KeyValueHarness(dir).tolerateLoaderFindings()
        .schema("keys:\n  k:\n    type: boolean\n    combine: all\n    settable_at: [global]\n")
        .schema("keys:\n  k:\n    type: boolean\n    combine: sometimes\n    settable_at: [global]\n").run();
    assertFindings(context.findings());
    assertTrue(info(context, "k").checkable());
  }

  // ---- strategy and type, defaults ----------------------------------------------------------------

  @Test
  void MERGE_3_aStrategyThatDoesNotAllowTheTypeIsEStrategyTypeAtTheDeclaration() {
    assertFindings(run("""
        keys:
          a:
            type: string
            combine: all
            settable_at: [global]
          b:
            type: integer
            combine: union
            settable_at: [global]
          c:
            type: set
            universe: [x]
            combine: deep_merge
            default: []
            settable_at: [global]
        """).findings(), "E_STRATEGY_TYPE 2 [keys, a]", "E_STRATEGY_TYPE 6 [keys, b]",
        "E_STRATEGY_TYPE 10 [keys, c]");
  }

  @Test
  void MERGE_3_everyAllowedPairOfTheTableIsAccepted() {
    assertFindings(run("""
        keys:
          a: { type: string, combine: override, default: x, settable_at: [global] }
          b: { type: object, fields: { f: { type: string } }, combine: deep_merge, default: { f: x }, settable_at: [global] }
          c: { type: boolean, combine: all, settable_at: [global] }
          d: { type: boolean, combine: any, settable_at: [global] }
          e: { type: set, universe: [x], combine: intersect, settable_at: [global] }
          f: { type: set, universe: [x], combine: union, settable_at: [global] }
          g: { type: integer, combine: min, settable_at: [global] }
          h: { type: integer, combine: max, settable_at: [global] }
        """).findings());
  }

  @Test
  void MERGE_4_aCommutativeKeyThatDeclaresADefaultIsEDefaultNotAllowedAtTheDefault() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            combine: all
            default: true
            settable_at: [global]
        """).findings(), "E_DEFAULT_NOT_ALLOWED 5 [keys, k, default]");
  }

  @Test
  void MERGE_8_aDefaultThatIsNotAValueOfTheTypeIsReportedAtTheDefault() {
    assertFindings(run("""
        keys:
          k:
            type: decimal
            combine: override
            default: "1e3"
            settable_at: [global]
        """).findings(), "E_TYPE_MISMATCH 5 [keys, k, default]");
  }

  @Test
  void MERGE_8_aValidDefaultIsNormalisedIntoTheKey() {
    CheckContext context = run("""
        keys:
          k:
            type: decimal
            combine: override
            default: "+1.50"
            settable_at: [global]
        """);
    assertFindings(context.findings());
    assertEquals(Optional.of("1.5"), context.parts().keys().orElseThrow().get("k").defaultValue());
  }

  @Test
  void MERGE_10_aDeepMergeDefaultThatLacksARequiredFieldIsETypeMismatchAtTheDefault() {
    assertFindings(run("""
        keys:
          k:
            type: object
            fields:
              open: { type: string }
              close: { type: string }
            combine: deep_merge
            default: { open: x }
            settable_at: [global]
        """).findings(), "E_TYPE_MISMATCH 8 [keys, k, default]");
  }

  @Test
  void MERGE_10_aDeepMergeDefaultWithAPartialOptionalObjectFieldIsReportedAtThatField() {
    assertFindings(run("""
        keys:
          k:
            type: object
            fields:
              open: { type: string }
              slot:
                type: object
                optional: true
                fields:
                  a: { type: string }
                  b: { type: string }
            combine: deep_merge
            default: { open: x, slot: { a: y } }
            settable_at: [global]
        """).findings(), "E_TYPE_MISMATCH 13 [keys, k, default, slot]");
  }

  @Test
  void MERGE_7_anOrderDependentKeyWithNeitherDefaultNorRootValueIsEMissingDefaultAtTheDeclaration() {
    assertFindings(run("""
        keys:
          k:
            type: integer
            combine: override
            settable_at: [global]
        """).findings(), "E_MISSING_DEFAULT 2 [keys, k]");
  }

  @Test
  void MERGE_7_aRootValueInAnySourceMeansTheKeyNeedsNoDefault() {
    CheckContext context = new KeyValueHarness(dir).schema("""
        keys:
          k:
            type: integer
            combine: override
            settable_at: [global]
          m:
            type: integer
            combine: override
            settable_at: [global]
        """).root("k: 1\n").nodes("global:\n  m: 2\n").run();
    assertFindings(context.findings());
  }

  @Test
  void MERGE_7_aValueAtAnotherScopeIsNotARootValue() {
    assertFindings(new KeyValueHarness(dir).schema("""
        keys:
          k:
            type: integer
            combine: override
            settable_at: [global, division]
        """).nodes("division:\n  north:\n    k: 1\n").run().findings(), "E_MISSING_DEFAULT 2 [keys, k]");
  }

  @Test
  void MERGE_7_aCommutativeKeyNeedsNoDefault() {
    assertFindings(run("""
        keys:
          k: { type: integer, combine: max, settable_at: [global] }
        """).findings());
  }

  // ---- axes -------------------------------------------------------------------------------------

  @Test
  void AXIS_2_aCommutativeKeyThatDeclaresCombineAcrossAxesIsEAxisCombineInvalidAtTheMember() {
    assertFindings(run("""
        keys:
          k:
            type: integer
            combine: max
            combine_across_axes: max
            settable_at: [global, seller]
        """).findings(), "E_AXIS_COMBINE_INVALID 5 [keys, k, combine_across_axes]");
  }

  @Test
  void AXIS_3_anOrderDependentKeySettableOnAnAxisWithoutCombineAcrossAxesIsEMissingAxisCombine() {
    assertFindings(run("""
        keys:
          k:
            type: string
            combine: override
            default: x
            settable_at: [global, seller]
        """).findings(), "E_MISSING_AXIS_COMBINE 2 [keys, k]");
  }

  @Test
  void AXIS_4_aValueOutsideTheFourIsEAxisCombineInvalidAtTheMember() {
    assertFindings(run("""
        keys:
          k:
            type: integer
            combine: override
            default: 1
            combine_across_axes: sum
            settable_at: [global, seller]
        """).findings(), "E_AXIS_COMBINE_INVALID 6 [keys, k, combine_across_axes]");
  }

  @Test
  void AXIS_5_maxOrMinOnAKeyThatIsNotAnIntegerIsEAxisCombineInvalidAtTheMember() {
    assertFindings(run("""
        keys:
          k:
            type: string
            combine: override
            default: x
            combine_across_axes: min
            settable_at: [global, seller]
        """).findings(), "E_AXIS_COMBINE_INVALID 6 [keys, k, combine_across_axes]");
  }

  @Test
  void AXIS_6_aKeySettableOnNoAxisThatDeclaresCombineAcrossAxesIsEAxisCombineInvalidAtTheMember() {
    assertFindings(run("""
        keys:
          k:
            type: string
            combine: override
            default: x
            combine_across_axes: axis_wins
            settable_at: [global, division]
        """).findings(), "E_AXIS_COMBINE_INVALID 6 [keys, k, combine_across_axes]");
  }

  @Test
  void AXIS_6_aSettableAtEntryThatIsNotDeclaredHidesWhetherTheKeyIsSettableOnAnAxis() {
    assertFindings(run("""
        keys:
          k:
            type: string
            combine: override
            default: x
            combine_across_axes: axis_wins
            settable_at: [global, seler]
        """).findings(), "E_UNKNOWN_SCOPE 7 [keys, k, settable_at, 1]");
  }

  @Test
  void AXIS_4_everyAllowedValueIsAccepted() {
    CheckContext context = run("""
        keys:
          a: { type: string, combine: override, default: x, combine_across_axes: axis_wins, settable_at: [global, seller] }
          b: { type: string, combine: override, default: x, combine_across_axes: hierarchy_wins, settable_at: [global, seller] }
          c: { type: integer, combine: override, default: 1, combine_across_axes: max, settable_at: [global, seller] }
          d: { type: integer, combine: override, default: 1, combine_across_axes: min, settable_at: [global, seller] }
        """);
    assertFindings(context.findings());
    assertEquals(Optional.of("min"), context.parts().keys().orElseThrow().get("d").combineAcrossAxes());
  }

  // ---- kinds ------------------------------------------------------------------------------------

  @Test
  void KIND_13_aKindThatIsNotDeclaredIsEUnknownKindAtTheKindMemberWithASuggestion() {
    CheckContext context = runWithKinds("""
        keys:
          k:
            type: boolean
            combine: all
            kind: fag
            settable_at: [global]
        """);
    assertFindings(context.findings(), "E_UNKNOWN_KIND 5 [keys, k, kind]");
    assertEquals(Optional.of("flag"), context.findings().sorted().get(0).suggestion());
  }

  @Test
  void KIND_2_aKindIsComparedAfterNormalisation() {
    assertFindings(new KeyValueHarness(dir).schema("kinds:\n  caf\u00e9: { strategies: [all] }\n").schema("""
        keys:
          k:
            type: boolean
            combine: all
            kind: "cafe\\u0301"
            settable_at: [global]
        """).run().findings());
  }

  @Test
  void KIND_2_aKindThatIsNotAStringIsEInvalidDeclarationAtTheMember() {
    assertFindings(runWithKinds("""
        keys:
          k:
            type: boolean
            combine: all
            kind: [flag]
            settable_at: [global]
        """).findings(), "E_INVALID_DECLARATION 5 [keys, k, kind]");
  }

  @Test
  void KIND_4_aStrategyThatTheKindDoesNotListIsEKindViolationAtCombine() {
    assertFindings(runWithKinds("""
        keys:
          k:
            type: boolean
            combine: override
            default: true
            kind: flag
            settable_at: [global]
        """).findings(), "E_KIND_VIOLATION 4 [keys, k, combine]");
  }

  @Test
  void KIND_8_aKeyOfARequireDefaultKindWithoutDefaultIsEKindViolationAtTheDeclaration() {
    assertFindings(new KeyValueHarness(dir).schema(KINDS).schema("""
        keys:
          k:
            type: integer
            combine: override
            kind: defaulted
            settable_at: [global]
        """).root("k: 1\n").run().findings(), "E_KIND_VIOLATION 2 [keys, k]");
  }

  @Test
  void KIND_11_aKeyOnSourceFailureThatDiffersFromTheKindsIsEKindViolationAtTheMember() {
    assertFindings(runWithKinds("""
        keys:
          k:
            type: boolean
            combine: all
            kind: narrow
            on_source_failure: skip
            settable_at: [global]
        """).findings(), "E_KIND_VIOLATION 6 [keys, k, on_source_failure]");
  }

  @Test
  void KIND_11_aKeyOnSourceFailureThatEqualsTheKindsIsAccepted() {
    assertFindings(runWithKinds("""
        keys:
          k:
            type: boolean
            combine: all
            kind: narrow
            on_source_failure: deny
            settable_at: [global, account]
        """).findings());
  }

  @Test
  void KIND_9_aKeyTakesTheSourceFailureOfItsKind() {
    CheckContext context = runWithKinds("""
        keys:
          k:
            type: string
            combine: override
            default: x
            kind: skipper
            settable_at: [global, account]
        """);
    assertFindings(context.findings());
    assertEquals(Optional.of("skip"), context.parts().keys().orElseThrow().get("k").onSourceFailure());
    assertEquals(Optional.of("skipper"), context.parts().keys().orElseThrow().get("k").kind());
  }

  // ---- source failure ---------------------------------------------------------------------------

  @Test
  void SVC_2_aKeySettableAtAServiceSourcedLevelWithoutSourceFailureIsEMissingSourceFailure() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            combine: all
            settable_at: [global, account]
        """).findings(), "E_MISSING_SOURCE_FAILURE 2 [keys, k]");
  }

  @Test
  void SVC_7_aSourceFailureOnAKeyThatIsSettableAtNoServiceLevelIsAcceptedAndKeptOutOfTheModel() {
    CheckContext context = run("""
        keys:
          k:
            type: boolean
            combine: all
            on_source_failure: deny
            settable_at: [global, division]
        """);
    assertFindings(context.findings());
    assertEquals(Optional.empty(), context.parts().keys().orElseThrow().get("k").onSourceFailure());
  }

  @Test
  void SVC_11_aDenyKeyAtAServiceLevelWithAStrategyThatIsNotNarrowingIsEDenyNotNarrowingAtCombine() {
    assertFindings(run("""
        keys:
          k:
            type: boolean
            combine: any
            on_source_failure: deny
            settable_at: [global, account]
        """).findings(), "E_DENY_NOT_NARROWING 4 [keys, k, combine]");
  }

  @Test
  void SVC_11_aDenyKeyThatIsSettableAtNoServiceLevelMayUseAnyStrategy() {
    assertFindings(run("""
        keys:
          k: { type: boolean, combine: any, on_source_failure: deny, settable_at: [global] }
        """).findings());
  }

  @Test
  void SVC_12_aMinKeyWithDenyAndNoMinBoundIsEDenyBoundRequiredAtTheDeclaration() {
    assertFindings(run("""
        keys:
          k:
            type: integer
            max: 10
            combine: min
            on_source_failure: deny
            settable_at: [global, account]
        """).findings(), "E_DENY_BOUND_REQUIRED 2 [keys, k]");
  }

  @Test
  void SVC_12_aMinKeyWithDenyAndAMinBoundIsAccepted() {
    assertFindings(run("""
        keys:
          k:
            type: integer
            min: 0
            combine: min
            on_source_failure: deny
            settable_at: [global, account]
        """).findings());
  }

  // ---- suppression and the model ------------------------------------------------------------------

  @Test
  void FIND_35_aKeyWhoseCombineIsInvalidGetsNoStrategyDefaultKindOrAxisCheck() {
    assertFindings(runWithKinds("""
        keys:
          k:
            type: string
            combine: sometimes
            default: 5
            kind: nothing
            combine_across_axes: bad
            settable_at: [global, account, seller]
        """).findings(), "E_INVALID_DECLARATION 4 [keys, k, combine]");
  }

  @Test
  void FIND_35_aKeyWhoseTypeIsInvalidGetsNoStrategyDefaultKindOrAxisCheck() {
    assertFindings(runWithKinds("""
        keys:
          k:
            type: nothing
            combine: all
            default: 5
            kind: nothing
            combine_across_axes: bad
            settable_at: [global, account, seller]
        """).findings(), "E_INVALID_DECLARATION 3 [keys, k, type]");
  }

  @Test
  void FIND_35_aKeyWithoutTypeIsStillADeclaredKey() {
    CheckContext context = run("""
        keys:
          k:
            combine: all
            settable_at: [global]
        """);
    assertFindings(context.findings(), "E_TYPE_REQUIRED 2 [keys, k]");
    assertFalse(info(context, "k").checkable());
  }

  @Test
  void KEY_stage_aValidProjectBuildsTheKeysOfTheModelInSettableOrder() {
    CheckContext context = run("""
        keys:
          k:
            type: integer
            min: 0
            max: 9
            combine: override
            default: 3
            combine_across_axes: max
            on_source_failure: skip
            settable_at: [seller, account, global, division]
        """);
    assertFindings(context.findings());
    KeyDef key = context.parts().keys().orElseThrow().get("k");
    assertEquals(List.of("global", "division", "account", "seller"), key.settableAt());
    assertEquals(Optional.of(3L), key.defaultValue());
    assertEquals(Optional.of("max"), key.combineAcrossAxes());
    assertEquals(Optional.of("skip"), key.onSourceFailure());
    assertEquals("override", key.combine());
  }

  @Test
  void KEY_stage_aFaultLeavesTheKeysPartUnbuiltButPublishesTheTable() {
    CheckContext context = run("""
        keys:
          good: { type: boolean, combine: all, settable_at: [global] }
          bad: { type: boolean, combine: all, default: true, settable_at: [global] }
        """);
    assertEquals(List.of("E_DEFAULT_NOT_ALLOWED 3 [keys, bad, default]"), descriptions(context));
    assertTrue(context.parts().keys().isEmpty());
    Map<String, KeyInfo> table = context.find(KeyTable.class).orElseThrow().keys();
    assertEquals(List.of("good", "bad"), List.copyOf(table.keySet()));
    assertTrue(table.get("good").checkable());
  }

  @Test
  void KEY_stage_withoutAHierarchyTheChecksThatNeedItAreSkipped() {
    CheckContext context = new KeyValueHarness(dir).schema("""
        keys:
          k:
            type: string
            combine: override
            default: x
            settable_at: [nowhere, seller]
        """).run(false);
    assertFindings(context.findings());
    assertTrue(context.parts().keys().isEmpty());
  }
}
