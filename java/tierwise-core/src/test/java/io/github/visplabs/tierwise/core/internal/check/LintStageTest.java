package io.github.visplabs.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.CompileOptions;
import io.github.visplabs.tierwise.core.Severity;
import io.github.visplabs.tierwise.core.internal.project.ProjectLoad;
import io.github.visplabs.tierwise.core.internal.project.ProjectLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the lint stage over whole projects: a generated catalog's node IDs are checked and located in the
 * catalog (LINT-19, LINT-21, FIND-19), no lint runs without the examples the example stage publishes (FIND-35),
 * and no lint runs when an engine finding exists (LINT-11).
 */
class LintStageTest {

  @TempDir
  Path dir;

  private static final String MANIFEST = """
      specVersion: "0.1"
      sources:
        schema:
          - schema/core.yaml
        rules:
          - rules/*.yaml
      lint:
        rules:
          "naming/*": error
      """;

  /** A closed scope whose node IDs come from a generated catalog; the node IDs must be lower case. */
  private static final String SCHEMA = """
      naming:
        node_id:
          category: node_id
          pattern: '^[a-z][a-z0-9_]*$'
      decision_hierarchy:
        levels: [global, division]
        scopes:
          division:
            nodes: { from: catalog/divisions.json }
            on_unknown_node: error
      """;

  /** The catalog whose third node ID, on line 5, breaks the pattern. */
  private static final String CATALOG = """
      {
        "nodes": [
          "north",
          "south",
          "East"
        ]
      }
      """;

  private static Map<String, String> project(String manifest) {
    return Map.of("tierwise.yaml", manifest, "schema/core.yaml", SCHEMA, "catalog/divisions.json", CATALOG);
  }

  @Test
  void LINT_19_AndLINT_21_aCatalogNodeIdThatBreaksThePatternIsLocatedInTheCatalog() {
    CheckContext context = PipelineProject.run(dir, project(MANIFEST), null);
    assertEquals(List.of("naming/node_id catalog/divisions.json 5 [nodes, 2]"),
        describeLint(context));
    assertEquals(Severity.ERROR, context.findings().sorted().get(0).severity());
  }

  @Test
  void FIND_35_noLintRunsBeforeTheExampleSetIsPublished() {
    CheckContext context = PipelineProject.run(dir, project(MANIFEST), "examples");
    new LintStage().run(context);
    assertEquals(List.of(), describeLint(context));

    new ExampleStage().run(context);
    new LintStage().run(context);
    assertEquals(List.of("naming/node_id catalog/divisions.json 5 [nodes, 2]"), describeLint(context));
  }

  @Test
  void LINT_11_aManifestFaultIsAnEngineFindingSoNoLintRuleRuns() {
    String invalid = MANIFEST.replace("\"naming/*\": error", "\"naming/*\": loud");
    CheckContext context = PipelineProject.run(dir, project(invalid), null);
    assertTrue(context.findings().hasEngineFindings());
    assertFalse(describe(context).stream().anyMatch(line -> line.startsWith("naming/")), describe(context).toString());
    assertFalse(new LintStage().enabled(context));
  }

  @Test
  void aProjectWithoutADirectoryReadsNoCatalogAndGetsNoLintFinding() {
    CheckContext context = new CheckContext(loadOf(project(MANIFEST)), CompileOptions.defaults());
    for (Stage stage : Compiler.standardStages()) {
      if (stage.enabled(context)) {
        stage.run(context);
      }
    }
    assertEquals(List.of(), describeLint(context));
  }

  private ProjectLoad loadOf(Map<String, String> files) {
    PipelineProject.write(dir, files);
    return ProjectLoader.load(dir, CompileOptions.defaults());
  }

  private static List<String> describe(CheckContext context) {
    return PipelineProject.describe(context);
  }

  /** The lint findings only: the rule identifiers start with a lint family, and the rest is an engine fault. */
  private static List<String> describeLint(CheckContext context) {
    return describe(context).stream().filter(line -> line.startsWith("naming/")).toList();
  }
}
