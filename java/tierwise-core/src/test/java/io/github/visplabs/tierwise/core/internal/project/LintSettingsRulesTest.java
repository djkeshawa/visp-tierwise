package io.github.visplabs.tierwise.core.internal.project;

import static io.github.visplabs.tierwise.core.internal.project.ProjectFixture.assertFindings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.Finding;
import io.github.visplabs.tierwise.core.Severity;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Lint settings: MANIFEST-37 to MANIFEST-39, LINT-3 to LINT-5, LINT-8 and LINT-10. */
class LintSettingsRulesTest {

  private static final String HEAD = "specVersion: \"0.1\"\n";

  @TempDir
  Path dir;

  private ProjectFixture project(String manifest) {
    return new ProjectFixture(dir).manifest(HEAD + manifest);
  }

  @Test
  void MANIFEST_37_lintIsAMappingWhoseOnlyKeyIsRules() {
    assertFindings(project("lint: []\n").load(), "E_MANIFEST tierwise.yaml:2 [lint]");
    assertFindings(project("lint:\n  rules: {}\n  severity: warn\n").load(), "E_MANIFEST tierwise.yaml:4 [lint, severity]");
    assertFindings(project("lint:\n").load(), "E_MANIFEST tierwise.yaml:2 [lint]");
    assertTrue(project("lint: {}\n").load().findings().isEmpty());
  }

  @Test
  void MANIFEST_38_rulesIsAMapping() {
    assertFindings(project("lint:\n  rules:\n    - values/no-op\n").load(), "E_MANIFEST tierwise.yaml:3 [lint, rules]");
    assertFindings(project("lint:\n  rules:\n").load(), "E_MANIFEST tierwise.yaml:3 [lint, rules]");
  }

  @Test
  void LINT_10_aSettingForAnUnknownRuleIsEManifestInTheManifestAndEPresetInAPreset() {
    ProjectLoad manifest = project("lint:\n  rules:\n    values/no-such: warn\n").load();
    assertFindings(manifest, "E_MANIFEST tierwise.yaml:4 [lint, rules, values/no-such]");
    assertEquals(Optional.of("values/no-op"), manifest.findings().get(0).suggestion());
    ProjectLoad preset = project("extends: [p.yaml]\n").file("p.yaml", "lint:\n  rules:\n    values/no-such: warn\n").load();
    assertFindings(preset, "E_PRESET p.yaml:3 [lint, rules, values/no-such]");
  }

  @Test
  void LINT_3_aSeverityThatIsNotOneOfTheFourIsInvalidWithTheSameCode() {
    ProjectLoad load = project("lint:\n  rules:\n    values/no-op: fatal\n    rules/dead-rule: 5\n    schema/unused: ~\n"
        + "    presets/override: [warn]\n").load();
    assertFindings(load, "E_MANIFEST tierwise.yaml:4 [lint, rules, values/no-op]",
        "E_MANIFEST tierwise.yaml:5 [lint, rules, rules/dead-rule]", "E_MANIFEST tierwise.yaml:6 [lint, rules, schema/unused]",
        "E_MANIFEST tierwise.yaml:7 [lint, rules, presets/override]");
    ProjectLoad preset = project("extends: [p.yaml]\n").file("p.yaml", "lint:\n  rules:\n    values/no-op: Warn\n").load();
    assertFindings(preset, "E_PRESET p.yaml:3 [lint, rules, values/no-op]");
  }

  @Test
  void LINT_8_aSettingThatNamesAnEngineErrorCodeIsEPresetWhateverItsValueAndHasNoEffect() {
    ProjectLoad load = project("lint:\n  rules:\n    E_UNKNOWN_KEY: warn\n    E_TYPE_MISMATCH: nonsense\n").load();
    assertFindings(load, "E_PRESET tierwise.yaml:4 [lint, rules, E_UNKNOWN_KEY]",
        "E_PRESET tierwise.yaml:5 [lint, rules, E_TYPE_MISMATCH]");
    assertTrue(load.sources().lint().layers().get(0).settings().isEmpty());
  }

  @Test
  void LINT_8_theSameHoldsInAPreset() {
    ProjectLoad load = project("extends: [p.yaml]\n").file("p.yaml", "lint:\n  rules:\n    E_PARSE: off\n").load();
    assertFindings(load, "E_PRESET p.yaml:3 [lint, rules, E_PARSE]");
  }

  @Test
  void LINT_10_aNamingRuleExistsOnlyWhenTheProjectDeclaresOrInheritsThePattern() {
    String schema = "naming:\n  key: {category: key, pattern: x}\n";
    ProjectLoad declared = project("sources:\n  schema: [s.yaml]\nlint:\n  rules:\n    naming/key: error\n    \"naming/*\": warn\n")
        .file("s.yaml", schema).load();
    assertTrue(declared.findings().isEmpty(), declared.findings().toString());
    ProjectLoad undeclared = project("lint:\n  rules:\n    naming/key: error\n    \"naming/*\": warn\n").load();
    assertFindings(undeclared, "E_MANIFEST tierwise.yaml:4 [lint, rules, naming/key]");
    ProjectLoad inherited = project("extends: [p.yaml]\nlint:\n  rules:\n    naming/key: error\n")
        .file("p.yaml", schema).load();
    assertTrue(inherited.findings().isEmpty(), inherited.findings().toString());
  }

  @Test
  void LINT_10_theNamingWildcardIsValidEvenWithoutAnyNamingPattern() {
    assertTrue(project("lint:\n  rules:\n    \"naming/*\": warn\n").load().findings().isEmpty());
  }

  @Test
  void LINT_5_aRuleThatNoSettingNamesIsOff() {
    LintSettings lint = project("").load().sources().lint();
    for (String rule : new String[] {Code.LINT_VALUES_NO_OP, Code.LINT_PRESETS_OVERRIDE, "naming/key"}) {
      assertEquals(Optional.empty(), lint.severityOf(rule));
    }
  }

  @Test
  void LINT_4_theLastSettingInLayerOrderWins() {
    ProjectLoad load = project("extends: [a.yaml, b.yaml]\nlint:\n  rules:\n    rules/dead-rule: info\n")
        .file("a.yaml", "lint:\n  rules:\n    values/no-op: error\n    rules/dead-rule: error\n    schema/unused: warn\n")
        .file("b.yaml", "lint:\n  rules:\n    values/no-op: warn\n    schema/unused: \"off\"\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    LintSettings lint = load.sources().lint();
    assertEquals(Optional.of(Severity.WARN), lint.severityOf(Code.LINT_VALUES_NO_OP));
    assertEquals(Optional.of(Severity.INFO), lint.severityOf(Code.LINT_RULES_DEAD_RULE));
    assertEquals(Optional.empty(), lint.severityOf(Code.LINT_SCHEMA_UNUSED));
    assertEquals(Optional.empty(), lint.severityOf(Code.LINT_SCHEMA_OWNER_REQUIRED));
  }

  @Test
  void LINT_4_anOffInALaterLayerSwitchesTheRuleOffAndALaterSettingSwitchesItOnAgain() {
    LintSettings off = project("extends: [a.yaml]\nlint:\n  rules:\n    values/no-op: \"off\"\n")
        .file("a.yaml", "lint:\n  rules:\n    values/no-op: error\n").load().sources().lint();
    assertEquals(Optional.empty(), off.severityOf(Code.LINT_VALUES_NO_OP));
    LintSettings on = project("extends: [a.yaml, b.yaml]\n").file("a.yaml", "lint:\n  rules:\n    values/no-op: \"off\"\n")
        .file("b.yaml", "lint:\n  rules:\n    values/no-op: info\n").load().sources().lint();
    assertEquals(Optional.of(Severity.INFO), on.severityOf(Code.LINT_VALUES_NO_OP));
  }

  @Test
  void LINT_10_anExactSettingBeatsTheWildcardWithinOneLayerButTheLastLayerWinsAcrossLayers() {
    String naming = "naming:\n  key: {category: key, pattern: x}\n  node_id: {category: node_id, pattern: y}\n";
    LintSettings within = project("sources:\n  schema: [s.yaml]\nlint:\n  rules:\n    naming/key: error\n    \"naming/*\": \"off\"\n"
        + "    naming/node_id: info\n").file("s.yaml", naming).load().sources().lint();
    assertEquals(Optional.of(Severity.ERROR), within.severityOf("naming/key"));
    assertEquals(Optional.of(Severity.INFO), within.severityOf("naming/node_id"));
    LintSettings across = project("extends: [a.yaml]\nsources:\n  schema: [s.yaml]\nlint:\n  rules:\n    \"naming/*\": warn\n")
        .file("s.yaml", naming).file("a.yaml", "lint:\n  rules:\n    naming/key: error\n").load().sources().lint();
    assertEquals(Optional.of(Severity.WARN), across.severityOf("naming/key"));
  }

  @Test
  void theSettingsOfTheBuiltInPresetsResolve() {
    LintSettings recommended = project("extends: ['tierwise:recommended']\n").load().sources().lint();
    assertEquals(Optional.of(Severity.ERROR), recommended.severityOf(Code.LINT_VALUES_NO_OP));
    assertEquals(Optional.of(Severity.WARN), recommended.severityOf(Code.LINT_RULES_DEAD_RULE));
    assertEquals(Optional.of(Severity.ERROR), recommended.severityOf("naming/key"));
    assertEquals(Optional.of(Severity.INFO), recommended.severityOf(Code.LINT_PRESETS_OVERRIDE));
    LintSettings minimal = project("extends: ['tierwise:recommended', 'tierwise:minimal']\n").load().sources().lint();
    assertEquals(Optional.empty(), minimal.severityOf("naming/key"));
    assertEquals(Optional.empty(), minimal.severityOf(Code.LINT_SCHEMA_UNUSED));
    assertEquals(Optional.of(Severity.WARN), minimal.severityOf(Code.LINT_VALUES_NO_OP));
  }

  @Test
  void theBuiltInPresetsRaiseNoFindingWithTheirOwnNamingPatterns() {
    for (String name : new String[] {"recommended", "minimal", "entitlements"}) {
      ProjectLoad load = project("extends: ['tierwise:" + name + "']\n").load();
      assertTrue(load.findings().isEmpty(), name + " " + load.findings());
    }
  }

  @Test
  void aFindingOfALintSettingIsAlwaysAnEngineError() {
    for (Finding finding : project("lint:\n  rules:\n    nope: warn\n").load().findings()) {
      assertEquals(Severity.ERROR, finding.severity());
    }
  }
}
