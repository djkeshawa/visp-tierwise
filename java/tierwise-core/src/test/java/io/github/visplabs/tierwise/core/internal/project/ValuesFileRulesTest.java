package io.github.visplabs.tierwise.core.internal.project;

import static io.github.visplabs.tierwise.core.internal.project.ProjectFixture.assertFindings;
import static io.github.visplabs.tierwise.core.internal.project.ProjectFixture.document;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Values files: SRC-21 to SRC-33 and the inline values content of MANIFEST-15. */
class ValuesFileRulesTest {

  private static final String SCHEMA = """
      decision_hierarchy:
        levels: [global, region, terminal]
        axes: [seller]
        scopes:
          region:
            nodes: [north, south]
            on_unknown_node: error
          terminal:
            nodes: open
            values: service
          seller:
            nodes: [acme]
            on_unknown_node: error
      """;

  @TempDir
  Path dir;

  private ProjectFixture project(String valuesItems) {
    return new ProjectFixture(dir).manifest("specVersion: \"0.1\"\nsources:\n  schema:\n    - schema/core.yaml\n  values:\n"
        + valuesItems).file("schema/core.yaml", SCHEMA);
  }

  private static ValueGroup only(SourceDocument document) {
    assertEquals(1, document.groups().size(), document.groups().toString());
    return document.groups().get(0);
  }

  @Test
  void SRC_26_aRootEntryGivesTheEntriesOfTheRootLevel() {
    ProjectLoad load = project("    - file: v/root.yaml\n      scope: global\n").file("v/root.yaml", "k.a.b: 1\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    SourceDocument document = document(load, "v/root.yaml");
    assertEquals(new ValuesMapping.RootEntry("global", ((ValuesMapping.RootEntry) document.mapping().orElseThrow()).scopeNode()),
        document.mapping().orElseThrow());
    ValueGroup group = only(document);
    assertEquals("global", group.scope());
    assertEquals(Optional.empty(), group.node());
    assertEquals(List.of("k.a.b"), List.copyOf(group.entries().entries().keySet()));
    assertEquals(List.of("sources", "values", 0, "scope"), group.scopeLocation().path());
    assertEquals("tierwise.yaml", group.scopeLocation().file());
  }

  @Test
  void SRC_27_aRootEntryThatNamesAnotherDeclaredScopeIsEsourceMappingAtTheScopeMember() {
    ProjectLoad load = project("    - file: v/a.yaml\n      scope: region\n    - file: v/b.yaml\n      scope: seller\n")
        .file("v/a.yaml", "{}\n").file("v/b.yaml", "{}\n").load();
    assertFindings(load, "E_SOURCE_MAPPING tierwise.yaml:7 [sources, values, 0, scope]",
        "E_SOURCE_MAPPING tierwise.yaml:9 [sources, values, 1, scope]");
    assertTrue(load.sources().values().isEmpty());
  }

  @Test
  void SRC_27_aRootEntryThatNamesAnUndeclaredScopeIsLeftToTheHierarchyStage() {
    ProjectLoad load = project("    - file: v/a.yaml\n      scope: zone\n").file("v/a.yaml", "{}\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    assertEquals("zone", only(document(load, "v/a.yaml")).scope());
  }

  @Test
  void SRC_22_aPathMappedFileGivesTheEntriesOfTheCapturedScopeAndNode() {
    ProjectLoad load = project("    - pattern: 'v/{scope}/{node}.yaml'\n").file("v/region/north.yaml", "k.a.b: true\n")
        .file("v/seller/acme.yaml", "k.a.b: false\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    ValueGroup north = only(document(load, "v/region/north.yaml"));
    assertEquals("region", north.scope());
    assertEquals(Optional.of("north"), north.node());
    assertEquals("seller", only(document(load, "v/seller/acme.yaml")).scope());
  }

  @Test
  void SRC_28_aPathPatternThatCapturesTheRootLevelIsReportedAtThePatternMemberAndTheFileIsNotLoaded() {
    ProjectLoad load = project("    - pattern: 'v/{scope}/{node}.yaml'\n").file("v/global/x.yaml", "k: 1\n")
        .file("v/region/north.yaml", "k: 1\n").load();
    assertFindings(load, "E_SOURCE_MAPPING tierwise.yaml:6 [sources, values, 0, pattern]");
    assertEquals(List.of("schema/core.yaml", "v/region/north.yaml"), ProjectFixture.files(load));
  }

  @Test
  void SRC_28_noFindingWhenNoFileCapturesTheRootLevel() {
    ProjectLoad load = project("    - pattern: 'v/{scope}/{node}.yaml'\n").file("v/region/north.yaml", "k: 1\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
  }

  @Test
  void SRC_29_aMultiNodeFileGivesTheEntriesOfEachScopeAndNode() {
    ProjectLoad load = project("    - file: v/all.yaml\n").file("v/all.yaml", """
        global:
          k.a.b: 1
        region:
          north:
            k.a.b: 2
          south:
            k.a.b: 3
        seller:
          acme:
            k.a.b: 4
        """).load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    List<ValueGroup> groups = document(load, "v/all.yaml").groups();
    assertEquals(4, groups.size());
    assertEquals("global", groups.get(0).scope());
    assertEquals(Optional.empty(), groups.get(0).node());
    assertEquals(Optional.of("north"), groups.get(1).node());
    assertEquals(Optional.of("south"), groups.get(2).node());
    assertEquals("seller", groups.get(3).scope());
    assertEquals(List.of("region"), groups.get(1).scopeLocation().path());
    assertEquals(List.of("region", "north"), groups.get(1).nodeLocation().path());
    assertEquals(List.of("global"), groups.get(0).scopeLocation().path());
  }

  @Test
  void SRC_31_aScopeThatIsNotAMappingOfNodesIsReportedAtTheScope() {
    ProjectLoad load = project("    - file: v/all.yaml\n").file("v/all.yaml", "region: [a]\nseller: 5\nglobal: x\n").load();
    assertFindings(load, "E_SOURCE_MAPPING v/all.yaml:1 [region]", "E_SOURCE_MAPPING v/all.yaml:2 [seller]",
        "E_SOURCE_MAPPING v/all.yaml:3 [global]");
    assertTrue(document(load, "v/all.yaml").groups().isEmpty());
  }

  @Test
  void SRC_31_aNodeThatIsNotAMappingOfEntriesIsReportedAtTheNode() {
    ProjectLoad load = project("    - file: v/all.yaml\n").file("v/all.yaml",
        "region:\n  north: true\n  south:\n    k.a.b: 1\n").load();
    assertFindings(load, "E_SOURCE_MAPPING v/all.yaml:2 [region, north]");
    assertEquals(List.of(Optional.of("south")), document(load, "v/all.yaml").groups().stream().map(ValueGroup::node).toList());
  }

  @Test
  void SRC_30_theRootLevelEntryIsAMappingOfEntriesNotOfNodes() {
    ProjectLoad load = project("    - file: v/all.yaml\n").file("v/all.yaml", "global:\n  k.a.b: 1\n  other.c.d: 2\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    assertEquals(Optional.empty(), only(document(load, "v/all.yaml")).node());
    assertEquals(2, only(document(load, "v/all.yaml")).entries().entries().size());
  }

  @Test
  void SRC_33_aValuesFileForAServiceSourcedLevelIsReportedOnceAtTheLevelEntryAndGivesNoGroup() {
    ProjectLoad load = project("    - file: v/t.yaml\n").file("v/t.yaml", "terminal:\n  t1:\n    k.a.b: 1\n  t2:\n    k.a.b: 2\n").load();
    assertFindings(load, "E_SOURCE_MAPPING v/t.yaml:1 [terminal]");
    assertTrue(document(load, "v/t.yaml").groups().isEmpty());
  }

  @Test
  void SRC_33_aPathMappedFileForAServiceSourcedLevelIsReportedAtTheDocumentRoot() {
    ProjectLoad load = project("    - pattern: 'v/{scope}/{node}.yaml'\n").file("v/terminal/t1.yaml", "k.a.b: 1\n").load();
    assertFindings(load, "E_SOURCE_MAPPING v/terminal/t1.yaml:1 []");
    assertTrue(document(load, "v/terminal/t1.yaml").groups().isEmpty());
  }

  @Test
  void SRC_32_scopeNamesAndNodeIdsAreNormalisedToNfcAndLeftToSection2() {
    ProjectLoad load = project("    - file: v/all.yaml\n").file("v/all.yaml", "Zone\u0301:\n  cafe\u0301:\n    k.a.b: 1\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    ValueGroup group = only(document(load, "v/all.yaml"));
    assertEquals("Zon\u00e9", group.scope());
    assertEquals(Optional.of("caf\u00e9"), group.node());
    assertEquals(List.of("Zon\u00e9", "caf\u00e9"), group.nodeLocation().path());
  }

  @Test
  void SRC_35_theKeysOfAGroupAreKeptAsWrittenForTheKeyStages() {
    ProjectLoad load = project("    - file: v/all.yaml\n").file("v/all.yaml", "global:\n  cafe\u0301.a.b: 1\n").load();
    assertEquals(List.of("cafe\u0301.a.b"), List.copyOf(only(document(load, "v/all.yaml")).entries().entries().keySet()));
  }

  @Test
  void SRC_6_anEmptyValuesFileIsEParseAndGivesNoDocument() {
    ProjectLoad load = project("    - file: v/e.yaml\n").file("v/e.yaml", "").load();
    assertFindings(load, "E_PARSE v/e.yaml:1 []");
    assertEquals(List.of("schema/core.yaml"), ProjectFixture.files(load));
  }

  @Test
  void MANIFEST_15_inlineValuesAreAMultiNodeDocumentOfTheManifest() {
    ProjectLoad load = new ProjectFixture(dir).manifest("""
        specVersion: "0.1"
        schema:
          decision_hierarchy:
            levels: [global, region]
            scopes:
              region: {nodes: [north], on_unknown_node: error}
        values:
          global:
            k.a.b: 1
          region:
            north:
              k.a.b: 2
            south: x
        """).load();
    assertFindings(load, "E_SOURCE_MAPPING tierwise.yaml:13 [values, region, south]");
    SourceDocument values = load.sources().values().get(0);
    assertEquals(Origin.INLINE, values.origin());
    assertEquals(2, values.groups().size());
    assertEquals(List.of("values", "global"), values.groups().get(0).scopeLocation().path());
  }

  @Test
  void withoutAReadableHierarchyTheRootLevelIsUnknownAndNoGroupsAreBuilt() {
    ProjectLoad load = new ProjectFixture(dir).manifest("specVersion: \"0.1\"\nsources:\n  values:\n    - file: v/all.yaml\n"
        + "    - file: v/r.yaml\n      scope: anything\n").file("v/all.yaml", "global:\n  k.a.b: 1\nregion: x\n")
        .file("v/r.yaml", "k: 1\n").load();
    assertFindings(load, "E_SOURCE_MAPPING v/all.yaml:3 [region]");
    assertTrue(document(load, "v/all.yaml").groups().isEmpty());
    assertEquals(Optional.empty(), load.sources().rootLevel());
    assertEquals(1, document(load, "v/r.yaml").groups().size());
  }

  @Test
  void theRootLevelOfTheHierarchyIsExposed() {
    ProjectLoad load = project("    - file: v/root.yaml\n      scope: global\n").file("v/root.yaml", "{}\n").load();
    assertEquals(Optional.of("global"), load.sources().rootLevel());
  }
}
