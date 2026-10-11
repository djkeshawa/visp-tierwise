package io.github.visplabs.tierwise.core.internal.check;

import static io.github.visplabs.tierwise.core.internal.check.CheckTestSupport.assertFindings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Kind definitions and naming declarations: KIND-1 to KIND-12, LINT-14 to LINT-17, SVC-4, MANIFEST-32. */
class KindStageTest {

  private static final String NAMING = """
      naming:
        key_pattern: { category: key, pattern: '^[a-z]+$' }
        node_pattern: { category: node_id, pattern: '^[a-z]+$' }
      """;

  @TempDir
  Path dir;

  private CheckContext run(String schema) {
    return new KeyValueHarness(dir).schema(schema).run();
  }

  private KindTable kinds(CheckContext context) {
    return context.find(KindTable.class).orElseThrow();
  }

  @Test
  void KIND_3_aValidDefinitionWithEveryMemberIsAccepted() {
    CheckContext context = run(NAMING + """
        kinds:
          capability:
            strategies: [all, intersect, min]
            narrowing_only: true
            on_source_failure: deny
            naming: key_pattern
          configuration:
            strategies: [override, deep_merge]
            require_default: true
            on_source_failure: skip
        """);
    assertFindings(context.findings());
    KindDef capability = kinds(context).effective().get("capability");
    assertEquals(Optional.of(List.of("all", "intersect", "min")), capability.strategies());
    assertTrue(capability.narrowingOnly());
    assertFalse(capability.requireDefault());
    assertEquals(Optional.of("deny"), capability.onSourceFailure());
    assertEquals(Optional.of("key_pattern"), capability.naming());
    assertTrue(kinds(context).effective().get("configuration").requireDefault());
  }

  @Test
  void KIND_3_aDefinitionWithNoMemberImposesNoConstraint() {
    CheckContext context = run("""
        kinds:
          plain: {}
        """);
    assertFindings(context.findings());
    KindDef plain = kinds(context).effective().get("plain");
    assertEquals(Optional.empty(), plain.strategies());
    assertFalse(plain.narrowingOnly());
  }

  @Test
  void KIND_3_aMemberThatTheDefinitionMayNotHaveIsEKindDefinitionAtThatMember() {
    assertFindings(run("""
        kinds:
          odd: { strategies: [all], colour: red }
        """).findings(), "E_KIND_DEFINITION 2 [kinds, odd, colour]");
  }

  @Test
  void KIND_3_aDefinitionThatIsNotAMappingIsEKindDefinitionAtTheDefinition() {
    CheckContext context = run("""
        kinds:
          odd: [all]
        """);
    assertFindings(context.findings(), "E_KIND_DEFINITION 2 [kinds, odd]");
    assertTrue(kinds(context).effective().containsKey("odd"));
  }

  @Test
  void KIND_3_anEmptyStrategiesListIsEKindDefinitionAtTheList() {
    assertFindings(run("""
        kinds:
          empty: { strategies: [] }
        """).findings(), "E_KIND_DEFINITION 2 [kinds, empty, strategies]");
  }

  @Test
  void KEY_5_aStrategyThatIsNoMergeStrategyIsEKindDefinitionAtItsItem() {
    assertFindings(run("""
        kinds:
          bad: { strategies: [all, sometimes] }
        """).findings(), "E_KIND_DEFINITION 2 [kinds, bad, strategies, 1]");
  }

  @Test
  void KEY_5_strategiesThatIsNotAListIsEKindDefinitionAtTheMember() {
    assertFindings(run("""
        kinds:
          bad: { strategies: all }
        """).findings(), "E_KIND_DEFINITION 2 [kinds, bad, strategies]");
  }

  @Test
  void KIND_3_aFaultyStrategiesListImposesNoConstraint() {
    CheckContext context = run("""
        kinds:
          bad: { strategies: [all, sometimes] }
        """);
    assertEquals(Optional.empty(), kinds(context).effective().get("bad").strategies());
  }

  @Test
  void KIND_5_aNarrowingOnlyDefinitionWithoutStrategiesIsReportedAtNarrowingOnly() {
    assertFindings(run("""
        kinds:
          narrow: { narrowing_only: true }
        """).findings(), "E_KIND_DEFINITION 2 [kinds, narrow, narrowing_only]");
  }

  @Test
  void KIND_5_aNarrowingOnlyDefinitionThatListsAStrategyThatIsNotNarrowingIsReportedAtThatItem() {
    assertFindings(run("""
        kinds:
          narrow: { strategies: [all, union, any], narrowing_only: true }
        """).findings(), "E_KIND_DEFINITION 2 [kinds, narrow, strategies, 1]",
        "E_KIND_DEFINITION 2 [kinds, narrow, strategies, 2]");
  }

  @Test
  void KIND_5_aNarrowingOnlyDefinitionOfNarrowingStrategiesIsAccepted() {
    assertFindings(run("""
        kinds:
          narrow: { strategies: [min, all, intersect], narrowing_only: true }
        """).findings());
  }

  @Test
  void KIND_5_narrowingOnlyFalseImposesNothing() {
    assertFindings(run("""
        kinds:
          open: { strategies: [union], narrowing_only: false }
          free: { narrowing_only: false }
        """).findings());
  }

  @Test
  void KIND_3_narrowingOnlyThatIsNotABooleanIsEKindDefinitionAtTheMember() {
    assertFindings(run("""
        kinds:
          bad: { strategies: [all], narrowing_only: yes }
        """).findings(), "E_KIND_DEFINITION 2 [kinds, bad, narrowing_only]");
  }

  @Test
  void KIND_10_aRequireDefaultDefinitionThatListsACommutativeStrategyIsReportedAtThatItem() {
    assertFindings(run("""
        kinds:
          defaulted: { strategies: [override, all, min], require_default: true }
        """).findings(), "E_KIND_DEFINITION 2 [kinds, defaulted, strategies, 1]",
        "E_KIND_DEFINITION 2 [kinds, defaulted, strategies, 2]");
  }

  @Test
  void KIND_10_aRequireDefaultDefinitionWithoutStrategiesIsReportedAtRequireDefault() {
    assertFindings(run("""
        kinds:
          defaulted: { require_default: true }
        """).findings(), "E_KIND_DEFINITION 2 [kinds, defaulted, require_default]");
  }

  @Test
  void KIND_10_aRequireDefaultDefinitionOfOrderDependentStrategiesIsAccepted() {
    assertFindings(run("""
        kinds:
          defaulted: { strategies: [override, deep_merge], require_default: true }
        """).findings());
  }

  @Test
  void SVC_4_aKindOnSourceFailureThatIsNeitherSkipNorDenyIsEKindDefinitionAtTheMember() {
    assertFindings(run("""
        kinds:
          bad: { on_source_failure: maybe }
          also: { on_source_failure: Skip }
        """).findings(), "E_KIND_DEFINITION 2 [kinds, bad, on_source_failure]",
        "E_KIND_DEFINITION 3 [kinds, also, on_source_failure]");
  }

  @Test
  void KIND_12_aNamingMemberThatNamesNoDeclaredPatternIsEKindDefinitionAtTheMember() {
    assertFindings(run(NAMING + """
        kinds:
          named: { naming: missing }
        """).findings(), "E_KIND_DEFINITION 5 [kinds, named, naming]");
  }

  @Test
  void KIND_12_aNamingMemberThatNamesAPatternOfAnotherCategoryIsEKindDefinitionAtTheMember() {
    assertFindings(run(NAMING + """
        kinds:
          named: { naming: node_pattern }
        """).findings(), "E_KIND_DEFINITION 5 [kinds, named, naming]");
  }

  @Test
  void LINT_16_aNamingMemberThatIsNotAStringIsEKindDefinitionAtTheMember() {
    assertFindings(run(NAMING + """
        kinds:
          named: { naming: [key_pattern] }
        """).findings(), "E_KIND_DEFINITION 5 [kinds, named, naming]");
  }

  @Test
  void KIND_12_aNamingMemberThatNamesAKeyPatternIsAccepted() {
    assertFindings(run(NAMING + """
        kinds:
          named: { naming: key_pattern }
        """).findings());
  }

  @Test
  void KIND_12_aKindMayNameAPatternThatALaterDocumentDeclares() {
    CheckContext context = new KeyValueHarness(dir)
        .schema("kinds:\n  named: { naming: key_pattern }\n")
        .schema(NAMING).run();
    assertFindings(context.findings());
  }

  @Test
  void KIND_12_aPatternWithAnInvalidDeclarationIsDeclaredSoTheKindIsNotAlsoReported() {
    CheckContext context = run("""
        naming:
          key_pattern: { category: key, pattern: '[a-' }
        kinds:
          named: { naming: key_pattern }
        """);
    assertFindings(context.findings(), "E_INVALID_DECLARATION 2 [naming, key_pattern, pattern]");
  }

  @Test
  void MANIFEST_32_aLaterLayerReplacesAnEarlierKindEntirely() {
    CheckContext context = new KeyValueHarness(dir)
        .preset("presets/base.yaml", "kinds:\n  shared: { strategies: [all], narrowing_only: true }\n")
        .schema("kinds:\n  shared: { strategies: [override] }\n").run();
    assertFindings(context.findings());
    KindDef shared = kinds(context).effective().get("shared");
    assertEquals(Optional.of(List.of("override")), shared.strategies());
    assertFalse(shared.narrowingOnly());
    assertFalse(shared.preset());
    assertEquals(2, kinds(context).all().size());
  }

  @Test
  void MANIFEST_32_aKindFromAPresetIsInEffectWhenTheProjectDoesNotRedefineIt() {
    CheckContext context = new KeyValueHarness(dir)
        .preset("presets/base.yaml", "kinds:\n  shared: { strategies: [all], narrowing_only: true }\n")
        .schema("keys: {}\n").run();
    assertFindings(context.findings());
    assertTrue(kinds(context).effective().get("shared").preset());
  }

  @Test
  void KIND_3_aFaultInAPresetIsLocatedInThePreset() {
    CheckContext context = new KeyValueHarness(dir)
        .preset("presets/base.yaml", "kinds:\n  bad: { strategies: [] }\n")
        .schema("keys: {}\n").run();
    assertEquals(1, context.findings().size());
    assertEquals("presets/base.yaml", context.findings().sorted().get(0).file());
    assertEquals("E_KIND_DEFINITION", context.findings().sorted().get(0).code());
  }

  // ---- naming declarations ------------------------------------------------------------------------

  @Test
  void LINT_14_aValidDeclarationIsAcceptedAndPublishedWithoutDescription() {
    CheckContext context = run("""
        naming:
          key_pattern:
            category: key
            pattern: '^[a-z]+$'
            description: Keys are lowercase.
          bare: { category: enum_value, pattern: '^[A-Z]+$' }
        """);
    assertFindings(context.findings());
    NamingTable table = context.find(NamingTable.class).orElseThrow();
    assertEquals(Optional.of("key"), table.effective().get("key_pattern").category());
    assertEquals(Optional.of("Keys are lowercase."), table.effective().get("key_pattern").description());
    assertTrue(table.effective().get("bare").regex().orElseThrow().matches("ABC"));
    assertTrue(table.effective().get("bare").usable());
  }

  @Test
  void LINT_14_aBadEntryInASharedFileIsEInvalidDeclarationAtTheEntry() {
    assertFindings(run("""
        naming:
          scalar: key
        """).findings(), "E_INVALID_DECLARATION 2 [naming, scalar]");
  }

  @Test
  void LINT_14_anEntryWithAMemberThatIsNotAllowedIsReportedAtThatMember() {
    assertFindings(run("""
        naming:
          p: { category: key, pattern: '^a$', flags: i }
        """).findings(), "E_INVALID_DECLARATION 2 [naming, p, flags]");
  }

  @Test
  void LINT_14_anEntryWithoutCategoryOrPatternIsReportedAtTheEntry() {
    assertFindings(run("""
        naming:
          no_category: { pattern: '^a$' }
          no_pattern: { category: key }
        """).findings(), "E_INVALID_DECLARATION 2 [naming, no_category]",
        "E_INVALID_DECLARATION 3 [naming, no_pattern]");
  }

  @Test
  void LINT_14_aPatternNameWithASlashAsteriskOrWhiteSpaceIsAnInvalidDeclaration() {
    assertFindings(run("""
        naming:
          "a/b": { category: key, pattern: '^a$' }
          "a*": { category: key, pattern: '^a$' }
          "a b": { category: key, pattern: '^a$' }
          "": { category: key, pattern: '^a$' }
        """).findings(), "E_INVALID_DECLARATION 2 [naming, a/b]", "E_INVALID_DECLARATION 3 [naming, a*]",
        "E_INVALID_DECLARATION 4 [naming, a b]", "E_INVALID_DECLARATION 5 [naming, ]");
  }

  @Test
  void LINT_15_aCategoryOutsideTheFourIsReportedAtTheCategory() {
    assertFindings(run("""
        naming:
          p: { category: entity, pattern: '^a$' }
        """).findings(), "E_INVALID_DECLARATION 2 [naming, p, category]");
  }

  @Test
  void LINT_17_anInvalidPatternInASharedFileIsEInvalidDeclarationAtThePattern() {
    assertFindings(run("""
        naming:
          p: { category: node_id, pattern: '[a-z' }
          q: { category: node_id, pattern: '\\d+' }
          r: { category: node_id, pattern: '' }
          s: { category: node_id, pattern: 5 }
        """).findings(), "E_INVALID_DECLARATION 2 [naming, p, pattern]",
        "E_INVALID_DECLARATION 3 [naming, q, pattern]", "E_INVALID_DECLARATION 4 [naming, r, pattern]",
        "E_INVALID_DECLARATION 5 [naming, s, pattern]");
  }

  @Test
  void LINT_17_anInvalidPatternOrEntryInAPresetIsEPreset() {
    CheckContext context = new KeyValueHarness(dir)
        .preset("presets/base.yaml", """
            naming:
              p: { category: node_id, pattern: '(a' }
              q: { category: nothing, pattern: '^a$' }
              r: oops
            """)
        .schema("keys: {}\n").run();
    assertEquals(List.of("E_PRESET 2", "E_PRESET 3", "E_PRESET 4"), context.findings().sorted().stream()
        .map(finding -> finding.code() + " " + finding.line()).toList());
    assertEquals("presets/base.yaml", context.findings().sorted().get(0).file());
  }

  @Test
  void MANIFEST_32_aLaterLayerReplacesAnEarlierPatternEntirely() {
    CheckContext context = new KeyValueHarness(dir)
        .preset("presets/base.yaml", "naming:\n  p: { category: key, pattern: '^a$' }\n")
        .schema("naming:\n  p: { category: node_id, pattern: '^b$' }\n").run();
    assertFindings(context.findings());
    NamingTable table = context.find(NamingTable.class).orElseThrow();
    assertEquals(Optional.of("node_id"), table.effective().get("p").category());
    assertEquals(2, table.all().size());
  }
}
