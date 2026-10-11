package io.github.visplabs.tierwise.core.internal.project;

import static io.github.visplabs.tierwise.core.internal.project.ProjectFixture.assertFindings;
import static io.github.visplabs.tierwise.core.internal.project.ProjectFixture.document;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Which files the manifest selects and under which role: SRC-12 to SRC-20, SRC-34, MANIFEST-12, NS-8. */
class SourceSelectionTest {

  private static final String HEAD = "specVersion: \"0.1\"\n";
  private static final String EMPTY = "{}\n";

  @TempDir
  Path dir;

  private ProjectFixture project() {
    return new ProjectFixture(dir);
  }

  @Test
  void SRC_13_aFileThatNoEntrySelectsDoesNotContribute() {
    ProjectLoad load = project().manifest(HEAD + "sources:\n  schema:\n    - schema/a.yaml\n")
        .file("schema/a.yaml", EMPTY).file("schema/unused.yaml", "labels: broken\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    assertEquals(List.of("schema/a.yaml"), ProjectFixture.files(load));
  }

  @Test
  void SRC_12_theEntryDecidesTheRoleOfTheFile() {
    ProjectLoad load = project().manifest(HEAD + "sources:\n  schema: [s.yaml]\n  rules: [r.yaml]\n  values:\n    - file: v.yaml\n")
        .file("s.yaml", EMPTY).file("r.yaml", EMPTY).file("v.yaml", EMPTY).load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    assertEquals(SourceRole.SCHEMA, document(load, "s.yaml").role());
    assertEquals(SourceRole.RULES, document(load, "r.yaml").role());
    assertEquals(SourceRole.VALUES, document(load, "v.yaml").role());
    assertEquals(Origin.FILE, document(load, "s.yaml").origin());
    assertEquals(1, load.sources().schemas().size());
    assertEquals(1, load.sources().rules().size());
    assertEquals(1, load.sources().values().size());
  }

  @Test
  void SRC_18_aGlobDoesNotDependOnTheOrderOfPatternsAndFilesAreInCodePointOrder() {
    ProjectLoad first = project().manifest(HEAD + "sources:\n  schema: ['schema/**/*.yaml', 'z.yaml']\n")
        .file("schema/b.yaml", EMPTY).file("schema/a/c.yaml", EMPTY).file("schema/a.yaml", EMPTY).file("z.yaml", EMPTY).load();
    ProjectLoad second = project().manifest(HEAD + "sources:\n  schema: ['z.yaml', 'schema/**/*.yaml']\n").load();
    assertEquals(List.of("schema/a.yaml", "schema/a/c.yaml", "schema/b.yaml", "z.yaml"), ProjectFixture.files(first));
    assertEquals(ProjectFixture.files(first), ProjectFixture.files(second));
  }

  @Test
  void SRC_18_hiddenFilesAndHiddenDirectoriesAreIncluded() {
    ProjectLoad load = project().manifest(HEAD + "sources:\n  schema: ['.hidden/*.yaml', 'schema/*.yaml']\n").file(".hidden/x.yaml", EMPTY)
        .file("schema/.draft.yaml", EMPTY).load();
    assertEquals(List.of(".hidden/x.yaml", "schema/.draft.yaml"), ProjectFixture.files(load));
  }

  @Test
  void SRC_19_aPatternThatMatchesNothingContributesNothingAndIsNoError() {
    ProjectLoad load = project().manifest(HEAD + "sources:\n  schema: ['nothing/**/*.yaml', '*.zzz']\n  rules: ['rules/*.yaml']\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    assertTrue(load.sources().documents().isEmpty());
  }

  @Test
  void SRC_20_aFileMatchedByTwoPatternsOfOneListIsSelectedOnce() {
    ProjectLoad load = project().manifest(HEAD + "sources:\n  schema: ['s/*.yaml', 's/s.yaml']\n").file("s/s.yaml", EMPTY).load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    assertEquals(List.of("s/s.yaml"), ProjectFixture.files(load));
  }

  @Test
  void SRC_20_aFileSelectedUnderTwoRolesIsReportedAtTheLaterEntryAndLoadedOnce() {
    String manifest = HEAD + "sources:\n  schema:\n    - x.yaml\n  rules:\n    - x.yaml\n  values:\n    - file: x.yaml\n";
    ProjectLoad load = project().manifest(manifest).file("x.yaml", EMPTY).load();
    assertFindings(load, "E_SOURCE_MAPPING tierwise.yaml:6 [sources, rules, 0]",
        "E_SOURCE_MAPPING tierwise.yaml:8 [sources, values, 0]");
    assertEquals(List.of("x.yaml"), ProjectFixture.files(load));
    assertEquals(SourceRole.SCHEMA, document(load, "x.yaml").role());
  }

  @Test
  void SRC_34_aValuesFileSelectedByTwoItemsIsReportedAtTheLaterItem() {
    String manifest = HEAD + "sources:\n  values:\n    - file: v.yaml\n    - file: ./v.yaml\n      scope: global\n";
    assertFindings(project().manifest(manifest).file("v.yaml", EMPTY).load(), "E_SOURCE_MAPPING tierwise.yaml:5 [sources, values, 1]");
  }

  @Test
  void SRC_34_aPathPatternAndAFileEntryThatSelectTheSameFileConflict() {
    String manifest = HEAD + "sources:\n  values:\n    - pattern: 'v/{scope}/{node}.yaml'\n    - file: v/region/north.yaml\n";
    assertFindings(project().manifest(manifest).file("v/region/north.yaml", EMPTY).load(),
        "E_SOURCE_MAPPING tierwise.yaml:5 [sources, values, 1]");
  }

  @Test
  void SRC_17_aFileThatDoesNotExistOrIsADirectoryIsReportedAtTheFileMember() throws IOException {
    Files.createDirectories(dir.resolve("values/dir"));
    String manifest = HEAD + "sources:\n  values:\n    - file: values/missing.yaml\n      scope: global\n    - file: values/dir\n";
    assertFindings(project().manifest(manifest).load(), "E_MANIFEST tierwise.yaml:4 [sources, values, 0, file]",
        "E_MANIFEST tierwise.yaml:6 [sources, values, 1, file]");
  }

  @Test
  void MANIFEST_12_aGlobMatchThatResolvesOutsideTheProjectRootIsEManifestAtThePattern() throws IOException {
    Path nested = dir.resolve("project");
    new ProjectFixture(dir).file("outside/o.yaml", EMPTY);
    new ProjectFixture(nested).manifest(HEAD + "sources:\n  schema:\n    - 'linked/*.yaml'\n    - '../outside/*.yaml'\n    - '/abs/*.yaml'\n")
        .file("linked-target/ok.yaml", EMPTY);
    try {
      Files.createSymbolicLink(nested.resolve("linked"), dir.resolve("outside"));
    } catch (UnsupportedOperationException | IOException e) {
      return;
    }
    ProjectLoad load = io.github.visplabs.tierwise.core.internal.project.ProjectLoader.load(nested,
        io.github.visplabs.tierwise.core.CompileOptions.defaults());
    assertFindings(load, "E_MANIFEST tierwise.yaml:4 [sources, schema, 0]", "E_MANIFEST tierwise.yaml:5 [sources, schema, 1]",
        "E_MANIFEST tierwise.yaml:6 [sources, schema, 2]");
    assertTrue(load.sources().documents().isEmpty());
  }

  @Test
  void MANIFEST_12_aValuesFileOutsideTheRootIsEManifestAtTheFileMember() {
    Path nested = dir.resolve("project");
    new ProjectFixture(dir).file("outside.yaml", EMPTY);
    new ProjectFixture(nested).manifest(HEAD + "sources:\n  values:\n    - file: ../outside.yaml\n");
    assertFindings(ProjectLoader.load(nested, io.github.visplabs.tierwise.core.CompileOptions.defaults()),
        "E_MANIFEST tierwise.yaml:4 [sources, values, 0, file]");
  }

  @Test
  void SRC_21_aValuesItemMustHaveExactlyOneOfTheThreeShapes() {
    String manifest = HEAD + "sources:\n  values:\n    - \"values/root.yaml\"\n    - {}\n    - {scope: global}\n"
        + "    - {file: a.yaml, pattern: 'v/{scope}/{node}.yaml'}\n    - {file: a.yaml, scope: global, extra: 1}\n    - [a]\n";
    assertFindings(project().manifest(manifest).load(),
        "E_SOURCE_MAPPING tierwise.yaml:4 [sources, values, 0]", "E_SOURCE_MAPPING tierwise.yaml:5 [sources, values, 1]",
        "E_SOURCE_MAPPING tierwise.yaml:6 [sources, values, 2]", "E_SOURCE_MAPPING tierwise.yaml:7 [sources, values, 3]",
        "E_SOURCE_MAPPING tierwise.yaml:8 [sources, values, 4]", "E_SOURCE_MAPPING tierwise.yaml:9 [sources, values, 5]");
  }

  @Test
  void SRC_21_aMemberThatIsNotAStringIsReportedAtTheMember() {
    String manifest = HEAD + "sources:\n  values:\n    - {file: 5}\n    - {file: a.yaml, scope: 7}\n    - {pattern: 5}\n";
    assertFindings(project().manifest(manifest).load(), "E_SOURCE_MAPPING tierwise.yaml:4 [sources, values, 0, file]",
        "E_SOURCE_MAPPING tierwise.yaml:5 [sources, values, 1, scope]", "E_SOURCE_MAPPING tierwise.yaml:6 [sources, values, 2, pattern]");
  }

  @Test
  void SRC_23_aBadPathPatternIsReportedAtThePatternMember() {
    String manifest = HEAD + "sources:\n  values:\n    - pattern: 'values/{scope}/{node}*.yaml'\n    - pattern: 'values/{node}.yaml'\n";
    assertFindings(project().manifest(manifest).load(), "E_SOURCE_MAPPING tierwise.yaml:4 [sources, values, 0, pattern]",
        "E_SOURCE_MAPPING tierwise.yaml:5 [sources, values, 1, pattern]");
  }

  @Test
  void SRC_24_theCapturedNamesAreNormalisedToNfc() {
    String manifest = HEAD + "sources:\n  values:\n    - pattern: 'v/{scope}/{node}.yaml'\n";
    ProjectLoad load = project().manifest(manifest).file("v/region/café.yaml", "k: 1\n").load();
    SourceDocument document = document(load, "v/region/café.yaml");
    ValuesMapping.PathMapped mapping = (ValuesMapping.PathMapped) document.mapping().orElseThrow();
    assertEquals("café", mapping.node());
    assertEquals("region", mapping.scope());
  }

  @Test
  void NS_8_aNamespacedFileIsSelectedWithoutAPatternAndOnceWhenAPatternSelectsItToo() {
    String manifest = HEAD + "sources:\n  schema: ['schema/*.yaml']\nnamespaces:\n  pos: {schema: schema/pos.yaml}\n  shop: {schema: schema/shop.yaml}\n";
    ProjectLoad load = project().manifest(manifest).file("schema/core.yaml", EMPTY)
        .file("schema/pos.yaml", "namespace: pos\n").file("schema/shop.yaml", "namespace: shop\n").file("other/x.yaml", EMPTY).load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    assertEquals(List.of("schema/core.yaml", "schema/pos.yaml", "schema/shop.yaml"), ProjectFixture.files(load));
    assertEquals(Optional.empty(), document(load, "schema/core.yaml").namespace());
    assertEquals(Optional.of("pos"), document(load, "schema/pos.yaml").namespace());
    assertTrue(document(load, "schema/shop.yaml").isNamespaced());
    String only = HEAD + "namespaces:\n  pos: {schema: schema/pos.yaml}\n";
    assertEquals(List.of("schema/pos.yaml"), ProjectFixture.files(project().manifest(only).load()));
  }

  @Test
  void NS_9_aFileListedUnderTwoNamespacesIsEManifestAtTheSecondEntry() {
    String manifest = HEAD + "namespaces:\n  pos: {schema: schema/pos.yaml}\n  shop: {schema: ./schema/pos.yaml}\n";
    assertFindings(project().manifest(manifest).file("schema/pos.yaml", "namespace: pos\n").load(),
        "E_MANIFEST tierwise.yaml:4 [namespaces, shop]");
  }

  @Test
  void SRC_20_aNamespacedFileThatAlsoServesAnotherRoleIsReported() {
    String manifest = HEAD + "sources:\n  rules: ['schema/pos.yaml']\nnamespaces:\n  pos: {schema: schema/pos.yaml}\n";
    assertFindings(project().manifest(manifest).file("schema/pos.yaml", "namespace: pos\n").load(),
        "E_SOURCE_MAPPING tierwise.yaml:5 [namespaces, pos]");
  }

  @Test
  void SRC_17_aNamespaceSchemaFileThatDoesNotExistIsEManifestAtTheSchemaMember() {
    assertFindings(project().manifest(HEAD + "namespaces:\n  pos:\n    schema: schema/none.yaml\n").load(),
        "E_MANIFEST tierwise.yaml:4 [namespaces, pos, schema]");
  }

  @Test
  void SRC_37_theSetOfDocumentsDoesNotDependOnTheOrderOfTheEntries() {
    String a = HEAD + "sources:\n  values:\n    - file: v/a.yaml\n    - file: v/b.yaml\n";
    String b = HEAD + "sources:\n  values:\n    - file: v/b.yaml\n    - file: v/a.yaml\n";
    ProjectLoad first = project().manifest(a).file("v/a.yaml", "{}\n").file("v/b.yaml", "{}\n").load();
    ProjectLoad second = project().manifest(b).load();
    assertEquals(ProjectFixture.files(first), ProjectFixture.files(second));
  }

  @Test
  void aFileThatCannotBeParsedIsReportedOnceAndOtherFilesStillLoad() {
    String manifest = HEAD + "sources:\n  schema: ['s/*.yaml']\n";
    ProjectLoad load = project().manifest(manifest).file("s/a.yaml", "{}\n").file("s/b.yaml", "x: [\n").file("s/c.yaml", "- 1\n").load();
    assertFindings(load, "E_PARSE s/b.yaml:1 []", "E_PARSE s/c.yaml:1 []");
    assertEquals(List.of("s/a.yaml"), ProjectFixture.files(load));
  }
}
