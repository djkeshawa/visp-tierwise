package io.github.visplabs.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.internal.model.ScopeDef;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The decision hierarchy declaration: levels, axes, scopes, entries and node lists (SCOPE-1 to SCOPE-26, SCOPE-42,
 * SCOPE-48). Each test runs the stages over a project written for it and checks the findings and the built part.
 */
class HierarchyDeclarationTest {

  @TempDir
  Path dir;

  /** Joins lines with a line feed and a final line feed, so that line {@code n} is the {@code n}-th argument. */
  static String text(String... lines) {
    return String.join("\n", lines) + "\n";
  }

  private static final String ENTITIES = "entity_hierarchy:\n  customer: {}\n";

  private String project(String... lines) throws IOException {
    HierarchyFixture.schema(dir, text(lines));
    return text(lines);
  }

  private void assertFindings(String... expected) {
    assertEquals(java.util.List.of(expected), describeOf(run()));
  }

  private CheckContext run() {
    return HierarchyFixture.run(dir);
  }

  @Test
  void scope1_levelsMustHaveAnEntry() throws IOException {
    project("decision_hierarchy:", "  levels: []", "  scopes: {}");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, levels] 2");
  }

  @Test
  void scope2_aLevelIsAStringAndNeverConverted() throws IOException {
    project("decision_hierarchy:", "  levels: [1, division]", "  scopes:", "    division:",
        "      nodes: [north, south]", "      on_unknown_node: error");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, levels, 0] 2");
  }

  @Test
  void scope3_scope4_aRepeatedNameIsReportedAtTheLaterOccurrence() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division, division]", "  scopes:", "    division:",
        "      nodes: [north, south]", "      on_unknown_node: error");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, levels, 2] 2");
  }

  @Test
  void scope3_aNameRepeatedAcrossLevelsAndAxesIsReportedAtTheAxis() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  axes: [division]", "  scopes:",
        "    division:", "      nodes: [north, south]", "      on_unknown_node: error");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, axes, 0] 3");
  }

  @Test
  void scope5_theAxesListIsOptionalAndAnEmptyOneDeclaresNoAxes() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  axes: []", "  scopes:", "    division:",
        "      nodes: [north]", "      on_unknown_node: error");
    CheckContext context = run();
    assertTrue(describeOf(context).isEmpty());
    assertTrue(context.parts().hierarchy().orElseThrow().axes().isEmpty());
  }

  @Test
  void scope6_theFirstLevelIsTheLeastSpecificAndTheRootLevel() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division, site]", "  scopes:", "    division:",
        "      nodes: [north]", "      on_unknown_node: error", "    site:", "      nodes: open", "      values: files");
    CheckContext context = run();
    assertEquals(java.util.List.of("global", "division", "site"), context.parts().hierarchy().orElseThrow().levels());
    assertEquals("global", context.parts().hierarchy().orElseThrow().rootLevel());
  }

  @Test
  void scope7_axesHaveNoPositionInTheSpecificityOrder() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  axes: [seller]", "  scopes:",
        "    division:", "      nodes: [north]", "      on_unknown_node: error", "    seller:", "      nodes: open");
    CheckContext context = run();
    assertEquals(java.util.List.of("global", "division", "seller"),
        context.parts().hierarchy().orElseThrow().scopeOrder());
  }

  @Test
  void scope8_aNameIsNotEmpty() throws IOException {
    project("decision_hierarchy:", "  levels: [\"\", division]", "  scopes:", "    division:",
        "      nodes: [north, south]", "      on_unknown_node: error");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, levels, 0] 2");
  }

  @Test
  void scope9_theDeclarationHasOnlyLevelsAxesAndScopes() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  root: global", "  scopes:", "    division:",
        "      nodes: [north, south]", "      on_unknown_node: error");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, root] 3");
  }

  @Test
  void scope9_levelsAbsentIsReportedAtTheDeclaration() throws IOException {
    project("decision_hierarchy:", "  scopes: {}");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy] 1");
  }

  @Test
  void scope9_axesThatAreNotAListAreReportedAtTheMember() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  axes: true", "  scopes:", "    division:",
        "      nodes: [north, south]", "      on_unknown_node: error");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, axes] 3");
  }

  @Test
  void scope10_aRootOnlyHierarchyIsBuiltWithNoScopes() throws IOException {
    project("decision_hierarchy:", "  levels: [global]");
    CheckContext context = run();
    assertTrue(describeOf(context).isEmpty());
    assertTrue(context.parts().hierarchy().orElseThrow().scopes().isEmpty());
  }

  @Test
  void scope11_theRootLevelIsNotListedInAxes() throws IOException {
    project("decision_hierarchy:", "  levels: [global, account]", "  axes: [global]", "  scopes:",
        "    account: { nodes: open, values: service }");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, axes, 0] 3");
  }

  @Test
  void scope12_theRootLevelHasNoScopeEntry() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    global:", "      nodes: open",
        "      values: service", "    division:", "      nodes: [north]", "      on_unknown_node: error");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, scopes, global] 4");
  }

  @Test
  void scope14_everyNonRootLevelAndAxisHasOneEntryAndNoOther() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division, site]", "  scopes:", "    division:",
        "      nodes: [north]", "      on_unknown_node: error", "    region:", "      nodes: [east]",
        "      on_unknown_node: error");
    assertFindings(
        "E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, scopes] 3",
        "E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, scopes, region] 7");
  }

  @Test
  void scope14_aMissingScopeIsReportedAtTheScopesMapping() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division, site]", "  scopes:", "    division:",
        "      nodes: [north]", "      on_unknown_node: error");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, scopes] 3");
  }

  @Test
  void scope15_scopesMustBeAMap() throws IOException {
    project("decision_hierarchy:", "  levels: [global]", "  scopes: [division]");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, scopes] 3");
  }

  @Test
  void scope16_aRootOnlyHierarchyMayOmitScopesOrGiveItAsNullOrEmpty() throws IOException {
    project("decision_hierarchy:", "  levels: [global]", "  scopes:");
    assertTrue(describeOf(run()).isEmpty());
    project("decision_hierarchy:", "  levels: [global]", "  scopes: {}");
    assertTrue(describeOf(run()).isEmpty());
  }

  @Test
  void scope16_aNullScopesMemberIsAFaultWhenThereAreNonRootScopes() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, scopes] 3");
  }

  @Test
  void scope17_aScopeEntryMustContainNodes() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    division:",
        "      on_unknown_node: error");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, scopes, division] 4");
  }

  @Test
  void scope18_aScopeEntryHasOnlyNodesOnUnknownNodeAndValues() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    division:",
        "      nodes: [north]", "      on_unknown_node: error", "      label: north");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA
        + " [decision_hierarchy, scopes, division, label] 7");
  }

  @Test
  void scope19_nodesIsAnInlineListAFromObjectOrOpen() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    division:",
        "      nodes: closed", "      on_unknown_node: error");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA
        + " [decision_hierarchy, scopes, division, nodes] 5");
  }

  @Test
  void scope19_aFromObjectHasOnlyAFromMemberWithAPath() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    division:",
        "      nodes: { from: catalogs/x.json, extra: 1 }", "      on_unknown_node: error");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA
        + " [decision_hierarchy, scopes, division, nodes] 5");
  }

  @Test
  void scope20_aNonStringInlineNodeIsNotConverted() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    division:",
        "      nodes: [north, 2]", "      on_unknown_node: error");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA
        + " [decision_hierarchy, scopes, division, nodes, 1] 5");
  }

  @Test
  void scope22_aClosedCatalogNeedsOnUnknownNode() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    division:",
        "      nodes: [north, south]");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA + " [decision_hierarchy, scopes, division] 4");
  }

  @Test
  void scope23_onUnknownNodeIsErrorOrIgnore() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    division:",
        "      nodes: [north, south]", "      on_unknown_node: warn");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA
        + " [decision_hierarchy, scopes, division, on_unknown_node] 6");
  }

  @Test
  void scope24_anOpenCatalogHasNoOnUnknownNode() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    division:",
        "      nodes: open", "      on_unknown_node: error");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA
        + " [decision_hierarchy, scopes, division, on_unknown_node] 6");
  }

  @Test
  void scope25_valuesIsFilesOrService() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    division:",
        "      nodes: [north, south]", "      on_unknown_node: error", "      values: database");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA
        + " [decision_hierarchy, scopes, division, values] 7");
  }

  @Test
  void scope26_anAxisIsNeverServiceSourced() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  axes: [seller]", "  scopes:",
        "    division:", "      nodes: [north]", "      on_unknown_node: error", "    seller:", "      nodes: [acme]",
        "      on_unknown_node: error", "      values: service");
    assertFindings("E_SCOPE_DECLARATION " + HierarchyFixture.SCHEMA
        + " [decision_hierarchy, scopes, seller, values] 11");
  }

  @Test
  void scope26_aLevelMaySetServiceAsItsValuesSource() throws IOException {
    project("decision_hierarchy:", "  levels: [global, account]", "  scopes:", "    account:",
        "      nodes: open", "      values: service");
    CheckContext context = run();
    assertTrue(describeOf(context).isEmpty());
    assertEquals(ScopeDef.ValuesSource.SERVICE,
        context.parts().hierarchy().orElseThrow().scope("account").values());
  }

  @Test
  void scope21_anEmptyClosedCatalogIsValid() throws IOException {
    project("decision_hierarchy:", "  levels: [global, channel]", "  scopes:", "    channel:", "      nodes: []",
        "      on_unknown_node: error");
    CheckContext context = run();
    assertTrue(describeOf(context).isEmpty());
    assertTrue(context.parts().hierarchy().orElseThrow().scope("channel").nodes().isEmpty());
  }

  @Test
  void scope31_32_theNodesOfAClosedCatalogAreKeptInCodePointOrder() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    division:",
        "      nodes: [south, north, east]", "      on_unknown_node: error");
    CheckContext context = run();
    assertEquals(java.util.List.of("east", "north", "south"),
        context.parts().hierarchy().orElseThrow().scope("division").nodes());
  }

  @Test
  void scope33_aNodeIdBreakingTheFormRulesIsInvalid() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    division:",
        "      nodes: [\"\", \"a\\u0001b\"]", "      on_unknown_node: error");
    assertFindings(
        "E_INVALID_NODE_ID " + HierarchyFixture.SCHEMA + " [decision_hierarchy, scopes, division, nodes, 0] 5",
        "E_INVALID_NODE_ID " + HierarchyFixture.SCHEMA + " [decision_hierarchy, scopes, division, nodes, 1] 5");
  }

  @Test
  void scope39_anInvalidNodeIdIsNotCheckedForADuplicate() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    division:",
        "      nodes: [\"\", \"\"]", "      on_unknown_node: error");
    assertFindings(
        "E_INVALID_NODE_ID " + HierarchyFixture.SCHEMA + " [decision_hierarchy, scopes, division, nodes, 0] 5",
        "E_INVALID_NODE_ID " + HierarchyFixture.SCHEMA + " [decision_hierarchy, scopes, division, nodes, 1] 5");
  }

  @Test
  void scope42_aRepeatAfterNfcNormalisationIsDuplicateAtTheLaterItem() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    division:",
        "      nodes: [\"cafe\\u0301\", \"caf\\u00E9\"]", "      on_unknown_node: error");
    assertFindings("E_DUPLICATE_NODE " + HierarchyFixture.SCHEMA
        + " [decision_hierarchy, scopes, division, nodes, 1] 5");
  }

  @Test
  void scope48_aProjectWithoutAHierarchyIsReportedAtTheManifestRoot() throws IOException {
    HierarchyFixture.write(dir, java.util.Map.of(HierarchyFixture.SCHEMA, ENTITIES));
    assertFindings("E_SCOPE_DECLARATION tierwise.yaml [] 1");
  }

  @Test
  void scope48_isNotReportedWhenTheManifestAlreadyFailed() throws IOException {
    Files.writeString(dir.resolve("tierwise.yaml"), "specVersion: [\n");
    CheckContext context = run();
    assertEquals(java.util.List.of(), HierarchyFixture.describe(context, "E_SCOPE_DECLARATION"));
    assertFalse(context.parts().hierarchy().isPresent());
  }

  @Test
  void findings35_aFaultyDeclarationLeavesTheHierarchyUnbuilt() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    division:",
        "      nodes: [north]", "      on_unknown_node: warn");
    CheckContext context = run();
    assertFalse(context.parts().hierarchy().isPresent());
    assertFalse(context.scopes().isPresent());
  }

  @Test
  void scope10_aValidDeclarationPublishesTheScopeLookup() throws IOException {
    project("decision_hierarchy:", "  levels: [global, division]", "  scopes:", "    division:",
        "      nodes: [north]", "      on_unknown_node: error");
    CheckContext context = run();
    assertTrue(context.scopes().isPresent());
    assertTrue(context.parts().hierarchy().isPresent());
  }

  private java.util.List<String> describeOf(CheckContext context) {
    return HierarchyFixture.describe(context, "E_SCOPE_DECLARATION", "E_INVALID_NODE_ID", "E_DUPLICATE_NODE");
  }
}
