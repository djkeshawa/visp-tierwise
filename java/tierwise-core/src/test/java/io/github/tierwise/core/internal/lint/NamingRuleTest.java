package io.github.tierwise.core.internal.lint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.github.tierwise.core.internal.project.SourceSet;
import io.github.tierwise.core.internal.yaml.PSeq;
import io.github.tierwise.core.internal.yaml.YamlLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the naming rules {@code naming/<name>} (LINT-12, LINT-16 to LINT-22): each category, the kind rule
 * and the search semantics.
 */
class NamingRuleTest {

  @TempDir
  Path dir;

  private static final String MANIFEST = """
      specVersion: "0.1"
      sources:
        schema:
          - "schema/**/*.yaml"
        values:
          - file: "values/root.yaml"
            scope: global
      lint:
        rules:
          "naming/*": error
      """;

  private static final String SCHEMA = """
      naming:
        key:
          category: key
          pattern: '^[a-z]+(\\.[a-z_]+){2}$'
        enum_value:
          category: enum_value
          pattern: '^[A-Z][A-Z0-9_]*$'
        feature:
          category: feature
          pattern: '^[a-z]+\\.[a-z_]+$'
        node_id:
          category: node_id
          pattern: '^[a-z][a-z0-9_]*$'
      decision_hierarchy:
        levels: [global, region]
        scopes:
          region:
            nodes: [north, South]
            on_unknown_node: error
      keys:
        checkout.order.submit:
          type: enum
          values: [ALLOW, deny]
          combine: override
          settable_at: [global]
          description: Good name, bad enum value.
        Search.fallback:
          type: boolean
          combine: all
          settable_at: [global]
          description: Bad key name.
      features:
        order.submission:
          decided_per: order
          description: Good feature.
        Order.submission:
          decided_per: order
          description: Bad feature name.
      """;

  @Test
  void eachCategoryReportsItsNamesAtTheirDeclarations() {
    SourceSet sources = LintFixture.sources(dir, Map.of("tierwise.yaml", MANIFEST, "schema/core.yaml", SCHEMA));
    List<String> found = LintRunner.run(LintContext.of(sources, LintFixture.emptyModel(), List.of())).stream()
        .map(LintFixture::describe).sorted().toList();
    assertEquals(List.of(
        "naming/enum_value 23 [keys, checkout.order.submit, values, 1]",
        "naming/feature 36 [features, Order.submission]",
        "naming/key 27 [keys, Search.fallback]",
        "naming/node_id 18 [decision_hierarchy, scopes, region, nodes, 1]"), found);
  }

  @Test
  void withoutALintSettingNoNamingRuleRuns() {
    SourceSet sources = LintFixture.sources(dir, Map.of("schema/core.yaml", SCHEMA));
    assertEquals(List.of(), LintRunner.run(LintContext.of(sources, LintFixture.emptyModel(), List.of())));
  }

  @Test
  void aPatternOutsideTheSubsetGetsNoRule() {
    String schema = """
        naming:
          key:
            category: key
            pattern: '\\d+'
        """;
    SourceSet sources = LintFixture.sources(dir, Map.of("schema/core.yaml", schema));
    List<String> ids = LintRules.of(LintContext.of(sources, LintFixture.emptyModel(), List.of())).stream()
        .map(LintRule::id).toList();
    assertFalse(ids.contains("naming/key"), ids.toString());
  }

  /** A schema with one node_id pattern and no inline node list, so that only the catalog items are checked. */
  private static final String NODE_ID_SCHEMA = """
      naming:
        node_id:
          category: node_id
          pattern: '^[a-z][a-z0-9_]*$'
      """;

  @Test
  void aGeneratedCatalogItemIsCheckedAndLocatedInTheCatalogFile() {
    SourceSet sources = LintFixture.sources(dir, Map.of("tierwise.yaml", MANIFEST, "schema/core.yaml",
        NODE_ID_SCHEMA));
    String catalog = "nodes:\n  - north\n  - South\n  - east\n";
    PSeq items = (PSeq) YamlLoader.load(catalog.getBytes(StandardCharsets.UTF_8), "catalog/divisions.json", false)
        .root().orElseThrow().get("nodes").orElseThrow();
    List<String> found = LintRunner.run(LintContext.of(sources, LintFixture.emptyModel(), List.of(), items.items()))
        .stream().map(finding -> finding.ruleId() + " " + finding.node().file() + " " + finding.node().line() + " "
            + finding.node().path()).toList();
    assertEquals(List.of("naming/node_id catalog/divisions.json 3 [nodes, 1]"), found);
  }
}
