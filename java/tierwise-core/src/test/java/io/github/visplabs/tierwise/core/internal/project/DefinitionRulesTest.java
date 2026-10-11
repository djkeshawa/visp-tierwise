package io.github.visplabs.tierwise.core.internal.project;

import static io.github.visplabs.tierwise.core.internal.project.ProjectFixture.assertFindings;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Combining definitions: SRC-37 to SRC-44, POL-5 and the location rule FIND-32. */
class DefinitionRulesTest {

  private static final String HIER = "decision_hierarchy:\n  levels: [global]\n";
  private static final String KEY = "keys:\n  a.b.c:\n    type: boolean\n";

  @TempDir
  Path dir;

  private ProjectFixture project(String manifestTail) {
    return new ProjectFixture(dir).manifest("specVersion: \"0.1\"\n" + manifestTail);
  }

  @Test
  void SRC_39_aSecondHierarchyIsReportedAtTheLaterDeclarationEvenWhenIdentical() {
    ProjectLoad load = project("extends:\n  - presets/h.yaml\nsources:\n  schema: ['s/*.yaml']\n")
        .file("presets/h.yaml", HIER).file("s/a.yaml", HIER).file("s/b.yaml", HIER).load();
    assertFindings(load, "E_DUPLICATE_DEFINITION s/a.yaml:1 [decision_hierarchy]",
        "E_DUPLICATE_DEFINITION s/b.yaml:1 [decision_hierarchy]");
  }

  @Test
  void SRC_39_theInlineSchemaPrecedesFiles() {
    ProjectLoad load = project("schema:\n  decision_hierarchy:\n    levels: [global]\nsources:\n  schema: ['s/*.yaml']\n")
        .file("s/a.yaml", HIER).load();
    assertFindings(load, "E_DUPLICATE_DEFINITION s/a.yaml:1 [decision_hierarchy]");
  }

  @Test
  void NS_4_aDeclarationThatANamespacedFileMustNotHaveIsNotADefinitionAndCannotDuplicateOne() {
    ProjectLoad load = project("sources:\n  schema: ['s/model.yaml']\nnamespaces:\n  pos: {schema: s/pos.yaml}\n")
        .file("s/model.yaml", "entity_hierarchy: {}\n")
        .file("s/pos.yaml", "namespace: pos\nentity_hierarchy: {}\n").load();
    assertFindings(load, "E_NAMESPACE_VIOLATION s/pos.yaml:2 [entity_hierarchy]");
  }

  @Test
  void SRC_39_theEntityHierarchyIsDeclaredOnce() {
    ProjectLoad load = project("sources:\n  schema: ['s/*.yaml']\n").file("s/a.yaml", "entity_hierarchy: {}\n")
        .file("s/b.yaml", "\nentity_hierarchy: {}\n").load();
    assertFindings(load, "E_DUPLICATE_DEFINITION s/b.yaml:2 [entity_hierarchy]");
  }

  @Test
  void SRC_40_aKeyDeclaredAgainIsReportedAtTheLaterDeclarationAcrossPresetsAndFiles() {
    ProjectLoad load = project("extends:\n  - presets/k.yaml\nsources:\n  schema: ['s/*.yaml']\n")
        .file("presets/k.yaml", KEY).file("s/a.yaml", KEY).file("s/b.yaml", "\n" + KEY).load();
    assertFindings(load, "E_DUPLICATE_DEFINITION s/a.yaml:2 [keys, a.b.c]", "E_DUPLICATE_DEFINITION s/b.yaml:3 [keys, a.b.c]");
  }

  @Test
  void SRC_40_aKeyDeclaredByTwoPresetsIsReportedInTheLaterPreset() {
    ProjectLoad load = project("extends:\n  - presets/a.yaml\n  - presets/b.yaml\n").file("presets/a.yaml", KEY)
        .file("presets/b.yaml", KEY).load();
    assertFindings(load, "E_DUPLICATE_DEFINITION presets/b.yaml:2 [keys, a.b.c]");
  }

  @Test
  void SRC_40_featuresAndFactsAreDeclaredOnceAndFactNamesAreComparedAfterNfc() {
    ProjectLoad load = project("sources:\n  schema: ['s/*.yaml']\n")
        .file("s/a.yaml", "features:\n  f.one: {}\nfacts:\n  caf\u00e9.x: {}\n")
        .file("s/b.yaml", "features:\n  f.one: {}\n  f.two: {}\nfacts:\n  cafe\u0301.x: {}\n").load();
    assertFindings(load, "E_DUPLICATE_DEFINITION s/b.yaml:2 [features, f.one]",
        "E_DUPLICATE_DEFINITION s/b.yaml:5 [facts, caf\u00e9.x]");
  }

  @Test
  void SRC_40_keyNamesAreComparedAsWritten() {
    ProjectLoad load = project("sources:\n  schema: ['s/*.yaml']\n").file("s/a.yaml", "keys:\n  caf\u00e9.a.b: {}\n")
        .file("s/b.yaml", "keys:\n  cafe\u0301.a.b: {}\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
  }

  @Test
  void SRC_43_aKindOrNamingPatternDeclaredTwiceInTheProjectIsReportedAtTheLaterOne() {
    ProjectLoad load = project("sources:\n  schema: ['s/*.yaml']\n").file("s/a.yaml", "kinds:\n  k: {}\nnaming:\n  n: {}\n")
        .file("s/b.yaml", "kinds:\n  k: {}\nnaming:\n  n: {}\n").load();
    assertFindings(load, "E_DUPLICATE_DEFINITION s/b.yaml:2 [kinds, k]", "E_DUPLICATE_DEFINITION s/b.yaml:4 [naming, n]");
  }

  @Test
  void MANIFEST_32_aKindOrNamingPatternDefinedAgainInALaterLayerIsAReplacementNotAnError() {
    ProjectLoad load = project("extends:\n  - presets/a.yaml\n  - presets/b.yaml\nsources:\n  schema: ['s/*.yaml']\n")
        .file("presets/a.yaml", "kinds:\n  k: {}\nnaming:\n  n: {}\n").file("presets/b.yaml", "kinds:\n  k: {}\nnaming:\n  n: {}\n")
        .file("s/a.yaml", "kinds:\n  k: {}\nnaming:\n  n: {}\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
  }

  @Test
  void SRC_44_twoRulesDocumentsThatNameTheSameFeatureAreReportedAtTheLaterFeatureEntry() {
    ProjectLoad load = project("sources:\n  rules: ['r/*.yaml']\nrules:\n  - feature: f.one\n  - feature: f.two\n")
        .file("r/a.yaml", "\nfeature: f.two\n").file("r/b.yaml", "feature: f.one\n").file("r/c.yaml", "feature: f.one\n").load();
    assertFindings(load, "E_DUPLICATE_DEFINITION r/a.yaml:2 [feature]", "E_DUPLICATE_DEFINITION r/b.yaml:1 [feature]",
        "E_DUPLICATE_DEFINITION r/c.yaml:1 [feature]");
  }

  @Test
  void SRC_44_twoInlineRulesItemsThatNameTheSameFeatureAreReportedAtTheLaterItem() {
    ProjectLoad load = project("rules:\n  - feature: f.one\n  - feature: f.one\n").load();
    assertFindings(load, "E_DUPLICATE_DEFINITION tierwise.yaml:4 [rules, 1, feature]");
  }

  @Test
  void SRC_44_aRulesDocumentWithoutAStringFeatureIsLeftToTheFeatureStage() {
    ProjectLoad load = project("rules:\n  - feature: 5\n  - feature: 5\n  - {}\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
  }

  @Test
  void SRC_37_theResultDoesNotDependOnTheOrderOfPatterns() {
    ProjectLoad first = project("sources:\n  schema: ['s/b.yaml', 's/a.yaml']\n").file("s/a.yaml", KEY).file("s/b.yaml", KEY).load();
    ProjectLoad second = project("sources:\n  schema: ['s/a.yaml', 's/b.yaml']\n").load();
    assertFindings(first, "E_DUPLICATE_DEFINITION s/b.yaml:2 [keys, a.b.c]");
    assertFindings(second, "E_DUPLICATE_DEFINITION s/b.yaml:2 [keys, a.b.c]");
  }
}
