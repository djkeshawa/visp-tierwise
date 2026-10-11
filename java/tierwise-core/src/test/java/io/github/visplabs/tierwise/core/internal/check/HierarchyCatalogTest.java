package io.github.visplabs.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.CompileOptions;
import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.project.ProjectLoad;
import io.github.visplabs.tierwise.core.internal.project.ProjectLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Generated node catalogs: the locations of the files (SCOPE-27, SCOPE-28), their shape (SCOPE-29, SCOPE-30), and
 * the node IDs that they list (SCOPE-31, SCOPE-32, SCOPE-38, SCOPE-42).
 */
class HierarchyCatalogTest {

  private static final String CATALOG = "catalogs/divisions.json";

  @TempDir
  Path root;

  private Path project;

  /** A schema whose {@code division} scope takes its nodes from {@code from}. */
  private void schema(String from) throws IOException {
    project = root.resolve("project");
    HierarchyFixture.write(project, Map.of(HierarchyFixture.SCHEMA, HierarchyDeclarationTest.text(
        "decision_hierarchy:",
        "  levels: [global, division]",
        "  scopes:",
        "    division:",
        "      nodes: { from: " + from + " }",
        "      on_unknown_node: error")));
  }

  private void catalog(String text) throws IOException {
    write(CATALOG, text);
  }

  private void write(String name, String text) throws IOException {
    Path file = project.resolve(name);
    Files.createDirectories(file.getParent());
    Files.writeString(file, text);
  }

  private CheckContext run() {
    CompileOptions options = CompileOptions.defaults();
    ProjectLoad load = ProjectLoader.load(project, options);
    CheckContext context = new CheckContext(load, options, project);
    new ManifestStage().run(context);
    new HierarchyStage().run(context);
    new EntityFactStage().run(context);
    return context;
  }

  private static List<String> describe(CheckContext context) {
    return HierarchyFixture.describe(context);
  }

  @Test
  void scope27_scope28_anAbsolutePathIsOutsideTheProject() throws IOException {
    schema("/etc/divisions.json");
    assertEquals(List.of("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA
        + " [decision_hierarchy, scopes, division, nodes, from] 5"), describe(run()));
  }

  @Test
  void scope27_scope28_aPathThatLeavesTheProjectIsOutside() throws IOException {
    schema("../outside.json");
    write("../outside.json", "{\"nodes\": []}");
    assertEquals(List.of("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA
        + " [decision_hierarchy, scopes, division, nodes, from] 5"), describe(run()));
  }

  @Test
  void scope28_aMissingCatalogIsReportedAtTheFromMember() throws IOException {
    schema(CATALOG);
    assertEquals(List.of("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA
        + " [decision_hierarchy, scopes, division, nodes, from] 5"), describe(run()));
  }

  @Test
  void scope28_aDirectoryIsNotACatalog() throws IOException {
    schema("catalogs");
    write("catalogs/divisions.json", "{\"nodes\": [\"north\"]}");
    assertEquals(List.of("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA
        + " [decision_hierarchy, scopes, division, nodes, from] 5"), describe(run()));
  }

  @Test
  void scope27_aLinkThatResolvesOutsideTheProjectIsOutside() throws IOException {
    schema("catalogs/link.json");
    Path outside = root.resolve("outside.json");
    Files.writeString(outside, "{\"nodes\": [\"north\"]}");
    Path link = project.resolve("catalogs/link.json");
    Files.createDirectories(link.getParent());
    try {
      Files.createSymbolicLink(link, outside);
    } catch (IOException | UnsupportedOperationException e) {
      Assumptions.assumeTrue(false, "symbolic links are not available: " + e);
    }
    assertEquals(List.of("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA
        + " [decision_hierarchy, scopes, division, nodes, from] 5"), describe(run()));
  }

  @Test
  void scope29_30_aCatalogHasOnlyTheNodesProperty() throws IOException {
    schema(CATALOG);
    catalog("{\n  \"nodes\": [\"north\"],\n  \"owner\": \"ops\"\n}\n");
    assertEquals(List.of("E_SCOPE_DECLARATION " + CATALOG + " [owner] 3"), describe(run()));
  }

  @Test
  void scope30_aCatalogIsAnObject() throws IOException {
    schema(CATALOG);
    catalog("[\"north\"]");
    assertEquals(List.of("E_SCOPE_DECLARATION " + CATALOG + " [] 1"), describe(run()));
  }

  @Test
  void scope30_aCatalogHasANodesProperty() throws IOException {
    schema(CATALOG);
    catalog("{}");
    assertEquals(List.of("E_SCOPE_DECLARATION " + CATALOG + " [] 1"), describe(run()));
  }

  @Test
  void scope29_nodesIsAnArrayOfStrings() throws IOException {
    schema(CATALOG);
    catalog("{\n  \"nodes\": \"north\"\n}\n");
    assertEquals(List.of("E_SCOPE_DECLARATION " + CATALOG + " [nodes] 2"), describe(run()));
  }

  @Test
  void scope30_aNonStringItemIsReportedAtItsPosition() throws IOException {
    schema(CATALOG);
    catalog("{\n  \"nodes\": [\"north\", 2]\n}\n");
    assertEquals(List.of("E_SCOPE_DECLARATION " + CATALOG + " [nodes, 1] 2"), describe(run()));
  }

  @Test
  void scope30_aCatalogThatIsNotWellFormedIsAParseFault() throws IOException {
    schema(CATALOG);
    catalog("{\"nodes\": [");
    assertEquals(List.of("E_PARSE " + CATALOG + " [] 1"), describe(run()));
  }

  @Test
  void scope30_aRepeatedPropertyIsDuplicateKeyAtTheSecondOccurrence() throws IOException {
    schema(CATALOG);
    catalog("{\n  \"nodes\": [\"north\"],\n  \"nodes\": [\"south\"]\n}\n");
    assertEquals(List.of("E_DUPLICATE_KEY " + CATALOG + " [nodes] 3"), describe(run()));
  }

  @Test
  void scope31_aGeneratedCatalogIsEmbeddedInCodePointOrder() throws IOException {
    schema(CATALOG);
    catalog("{\"nodes\": [\"south\", \"north\", \"east\"]}");
    CheckContext context = run();
    assertTrue(describe(context).isEmpty());
    Hierarchy hierarchy = context.parts().hierarchy().orElseThrow();
    assertEquals(List.of("east", "north", "south"), hierarchy.scope("division").nodes());
  }

  @Test
  void scope32_theOrderOfTheNodesDoesNotAffectTheModel() throws IOException {
    schema(CATALOG);
    catalog("{\"nodes\": [\"south\", \"north\"]}");
    Hierarchy first = run().parts().hierarchy().orElseThrow();
    catalog("{\"nodes\": [\"north\", \"south\"]}");
    Hierarchy second = run().parts().hierarchy().orElseThrow();
    assertEquals(first, second);
  }

  @Test
  void scope38_anInvalidGeneratedNodeIdIsReportedAtItsItem() throws IOException {
    schema(CATALOG);
    catalog("{\"nodes\": [\"\", \"north\"]}");
    assertEquals(List.of("E_INVALID_NODE_ID " + CATALOG + " [nodes, 0] 1"), describe(run()));
  }

  @Test
  void scope42_aRepeatedGeneratedNodeIsDuplicateAtTheLaterItem() throws IOException {
    schema(CATALOG);
    catalog("{\n  \"nodes\": [\n    \"north\",\n    \"south\",\n    \"north\"\n  ]\n}\n");
    assertEquals(List.of("E_DUPLICATE_NODE " + CATALOG + " [nodes, 2] 5"), describe(run()));
  }

  @Test
  void scope27_twoScopesMayNameTheSameCatalog() throws IOException {
    project = root.resolve("project");
    HierarchyFixture.write(project, Map.of(HierarchyFixture.SCHEMA, HierarchyDeclarationTest.text(
        "decision_hierarchy:",
        "  levels: [global, division, site]",
        "  scopes:",
        "    division: { nodes: { from: catalogs/divisions.json }, on_unknown_node: error }",
        "    site: { nodes: { from: catalogs/divisions.json }, on_unknown_node: ignore }")));
    catalog("{\"nodes\": [\"north\"]}");
    CheckContext context = run();
    assertTrue(describe(context).isEmpty());
    assertEquals(List.of("north"), context.parts().hierarchy().orElseThrow().scope("site").nodes());
  }

  @Test
  void scope31_withoutAKnownProjectDirectoryTheDeclarationIsNotBuilt() throws IOException {
    schema(CATALOG);
    catalog("{\"nodes\": [\"north\"]}");
    CompileOptions options = CompileOptions.defaults();
    ProjectLoad load = ProjectLoader.load(project, options);
    CheckContext context = new CheckContext(load, options);
    new ManifestStage().run(context);
    new HierarchyStage().run(context);
    assertTrue(describe(context).isEmpty());
    assertFalse(context.parts().hierarchy().isPresent());
  }
}
