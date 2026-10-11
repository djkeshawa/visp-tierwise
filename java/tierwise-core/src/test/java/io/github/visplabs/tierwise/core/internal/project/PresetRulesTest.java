package io.github.visplabs.tierwise.core.internal.project;

import static io.github.visplabs.tierwise.core.internal.project.ProjectFixture.assertFindings;
import static io.github.visplabs.tierwise.core.internal.project.ProjectFixture.document;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.CompileOptions;
import io.github.visplabs.tierwise.core.PresetFetcher;
import io.github.visplabs.tierwise.core.internal.json.Sha256;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Presets: MANIFEST-20 to MANIFEST-36, NS-12 and the location rules FIND-18 and FIND-23. */
class PresetRulesTest {

  private static final String HEAD = "specVersion: \"0.1\"\nextends:\n";

  @TempDir
  Path dir;

  private ProjectFixture project() {
    return new ProjectFixture(dir);
  }

  @Test
  void MANIFEST_21_everyBuiltInPresetIsReadFromTheClasspathAndNamedAsFind18Requires() {
    for (String name : List.of("recommended", "minimal", "entitlements")) {
      ProjectLoad load = project().manifest(HEAD + "  - \"tierwise:" + name + "\"\n").load();
      assertTrue(load.findings().isEmpty(), name + ": " + load.findings());
      SourceDocument preset = document(load, "tierwise:" + name);
      assertEquals(Origin.PRESET, preset.origin());
      assertEquals(SourceRole.SCHEMA, preset.role());
      assertTrue(preset.root().has("lint"));
    }
  }

  @Test
  void MANIFEST_21_theBuiltInFilesAreThoseOfThePresetsDirectoryOfTheRepository() throws IOException {
    Path presets = Path.of(System.getProperty("tierwise.spec.dir")).resolve("../presets").normalize();
    for (String name : List.of("recommended", "minimal", "entitlements")) {
      assertEquals(Files.readString(presets.resolve(name + ".yaml")),
          new String(BuiltInPresets.read(name).orElseThrow(), StandardCharsets.UTF_8));
    }
  }

  @Test
  void MANIFEST_22_aNameThatIsNotBuiltInIsEPresetAtItsExtendsItem() {
    assertFindings(project().manifest(HEAD + "  - \"tierwise:unknown\"\n  - \"tierwise:\"\n").load(),
        "E_PRESET tierwise.yaml:3 [extends, 0]", "E_PRESET tierwise.yaml:4 [extends, 1]");
  }

  @Test
  void MANIFEST_21_theNameIsCaseSensitive() {
    assertFindings(project().manifest(HEAD + "  - \"tierwise:Minimal\"\n").load(), "E_PRESET tierwise.yaml:3 [extends, 0]");
  }

  @Test
  void MANIFEST_23_aRelativePathPresetIsNamedByItsPathRelativeToTheProject() {
    ProjectLoad load = project().manifest(HEAD + "  - \"./presets/../presets/quiet.yaml\"\n")
        .file("presets/quiet.yaml", "lint:\n  rules: {}\n").load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    assertEquals(List.of("presets/quiet.yaml"), ProjectFixture.files(load));
  }

  @Test
  void MANIFEST_24_aPathThatNamesNoFileOrADirectoryIsEPreset() throws IOException {
    Files.createDirectories(dir.resolve("presets/sub"));
    assertFindings(project().manifest(HEAD + "  - \"presets/missing.yaml\"\n  - \"presets/sub\"\n").load(),
        "E_PRESET tierwise.yaml:3 [extends, 0]", "E_PRESET tierwise.yaml:4 [extends, 1]");
  }

  @Test
  void MANIFEST_12_aPresetPathOutsideTheProjectRootIsEManifest() {
    Path nested = dir.resolve("project");
    new ProjectFixture(dir).file("outside.yaml", "lint:\n  rules: {}\n");
    new ProjectFixture(nested).manifest(HEAD + "  - \"../outside.yaml\"\n  - \"/etc/hosts\"\n");
    assertFindings(ProjectLoader.load(nested, CompileOptions.defaults()), "E_MANIFEST tierwise.yaml:3 [extends, 0]",
        "E_MANIFEST tierwise.yaml:4 [extends, 1]");
  }

  @Test
  void MANIFEST_12_aSymbolicLinkToAFileOutsideTheProjectRootIsEManifest() throws IOException {
    Path nested = dir.resolve("project");
    new ProjectFixture(dir).file("outside.yaml", "lint:\n  rules: {}\n");
    new ProjectFixture(nested).manifest(HEAD + "  - \"link.yaml\"\n");
    try {
      Files.createSymbolicLink(nested.resolve("link.yaml"), dir.resolve("outside.yaml"));
    } catch (UnsupportedOperationException | IOException e) {
      return; // the file system has no symbolic links
    }
    assertFindings(ProjectLoader.load(nested, CompileOptions.defaults()), "E_MANIFEST tierwise.yaml:3 [extends, 0]");
  }

  @Test
  void MANIFEST_12_aSymbolicLinkInsideTheProjectRootIsFollowed() throws IOException {
    new ProjectFixture(dir).manifest(HEAD + "  - \"link.yaml\"\n").file("real.yaml", "lint:\n  rules: {}\n");
    try {
      Files.createSymbolicLink(dir.resolve("link.yaml"), dir.resolve("real.yaml"));
    } catch (UnsupportedOperationException | IOException e) {
      return;
    }
    ProjectLoad load = project().load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    assertEquals(List.of("link.yaml"), ProjectFixture.files(load));
  }

  @Test
  void MANIFEST_30_thePresetNamedTwiceIsReportedAtTheLaterItem() {
    assertFindings(project().manifest(HEAD + "  - \"presets/q.yaml\"\n  - \"./presets/q.yaml\"\n  - \"tierwise:minimal\"\n"
        + "  - \"tierwise:minimal\"\n").file("presets/q.yaml", "lint:\n  rules: {}\n").load(),
        "E_PRESET tierwise.yaml:4 [extends, 1]", "E_PRESET tierwise.yaml:6 [extends, 3]");
  }

  @Test
  void MANIFEST_35_aPresetWithExtendsIsEPresetAtThatKeyInThePresetFile() {
    assertFindings(project().manifest(HEAD + "  - \"presets/c.yaml\"\n").file("presets/c.yaml", "extends: []\n").load(),
        "E_PRESET presets/c.yaml:1 [extends]");
  }

  @Test
  void MANIFEST_36_aPresetHasOnlyTheKeysOfASchemaFileWithoutNamespaceAndLint() {
    String preset = "specVersion: \"0.1\"\nnamespace: pos\nsources: {}\nvalues: {}\nrules: []\nschema: {}\nnamespaces: {}\n"
        + "lint: {}\nkinds: {}\n";
    assertFindings(project().manifest(HEAD + "  - \"presets/p.yaml\"\n").file("presets/p.yaml", preset).load(),
        "E_PRESET presets/p.yaml:1 [specVersion]", "E_PRESET presets/p.yaml:2 [namespace]",
        "E_PRESET presets/p.yaml:3 [sources]", "E_PRESET presets/p.yaml:4 [values]", "E_PRESET presets/p.yaml:5 [rules]",
        "E_PRESET presets/p.yaml:6 [schema]", "E_PRESET presets/p.yaml:7 [namespaces]");
  }

  @Test
  void aPresetMayDeclareEveryMemberOfASchemaFileExceptNamespace() {
    ProjectLoad load = project().manifest(HEAD + "  - \"presets/p.yaml\"\n").file("presets/p.yaml", """
        decision_hierarchy: {levels: [global]}
        entity_hierarchy: {}
        facts: {}
        kinds: {}
        naming: {}
        keys: {}
        features: {}
        lint:
          rules: {}
        """).load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
  }

  @Test
  void aPresetMemberThatIsNotAMappingIsEPreset() {
    assertFindings(project().manifest(HEAD + "  - \"presets/p.yaml\"\n").file("presets/p.yaml", "keys: []\nkinds:\n").load(),
        "E_PRESET presets/p.yaml:1 [keys]", "E_PRESET presets/p.yaml:2 [kinds]");
  }

  @Test
  void SRC_6_aPresetWhoseRootIsNotAMappingIsEParse() {
    assertFindings(project().manifest(HEAD + "  - \"presets/l.yaml\"\n").file("presets/l.yaml", "- a\n").load(),
        "E_PARSE presets/l.yaml:1 []");
  }

  @Test
  void SRC_5_aSyntaxErrorInAPresetIsEParseAtLineOne() {
    assertFindings(project().manifest(HEAD + "  - \"presets/b.yaml\"\n").file("presets/b.yaml", "a: [x\n").load(),
        "E_PARSE presets/b.yaml:1 []");
  }

  @Test
  void aFaultInOnePresetDoesNotHideTheNextPreset() {
    assertFindings(project().manifest(HEAD + "  - \"presets/a.yaml\"\n  - \"presets/b.yaml\"\n  - \"tierwise:unknown\"\n")
        .file("presets/a.yaml", "x: 1\n").file("presets/b.yaml", "- b\n").load(),
        "E_PRESET presets/a.yaml:1 [x]", "E_PARSE presets/b.yaml:1 []", "E_PRESET tierwise.yaml:5 [extends, 2]");
  }

  @Test
  void MANIFEST_31_presetsAreLayersInExtendsOrderAndTheProjectIsTheLastLayer() {
    ProjectLoad load = project().manifest(HEAD + "  - \"presets/b.yaml\"\n  - \"tierwise:minimal\"\n  - \"presets/a.yaml\"\n"
        + "schema:\n  keys: {}\n").file("presets/a.yaml", "lint: {}\n").file("presets/b.yaml", "lint: {}\n").load();
    List<SourceDocument> documents = load.sources().documents();
    assertEquals(List.of("presets/b.yaml", "tierwise:minimal", "presets/a.yaml", "tierwise.yaml"),
        documents.stream().map(SourceDocument::file).toList());
    assertEquals(List.of(0, 1, 2, 3), documents.stream().map(SourceDocument::layer).toList());
    assertEquals(3, load.sources().lint().layers().size() - 1);
  }

  // ---- URL presets --------------------------------------------------------------------------------

  private static String urlManifest(String digest) {
    return HEAD + "  - url: \"https://presets.invalid/p.yaml\"\n    sha256: \"" + digest + "\"\n";
  }

  private static String digestOf(String content) {
    return Sha256.hexDigest(content.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void MANIFEST_27_aUrlPresetWithoutAFetcherIsEPresetAtItsExtendsItem() {
    assertFindings(project().manifest(urlManifest(digestOf("x"))).load(), "E_PRESET tierwise.yaml:3 [extends, 0]");
  }

  @Test
  void MANIFEST_27_aFetcherThatFailsGivesEPreset() {
    PresetFetcher failing = url -> {
      throw new IOException("offline");
    };
    assertFindings(project().manifest(urlManifest(digestOf("x"))).load(CompileOptions.builder().presetFetcher(failing).build()),
        "E_PRESET tierwise.yaml:3 [extends, 0]");
    PresetFetcher crashing = url -> {
      throw new IllegalStateException("bug");
    };
    assertFindings(project().load(CompileOptions.builder().presetFetcher(crashing).build()),
        "E_PRESET tierwise.yaml:3 [extends, 0]");
    assertFindings(project().load(CompileOptions.builder().presetFetcher(url -> null).build()),
        "E_PRESET tierwise.yaml:3 [extends, 0]");
  }

  @Test
  void MANIFEST_28_bytesWhoseDigestDiffersAreEPresetAndTheirContentIsNeverRead() {
    AtomicInteger fetched = new AtomicInteger();
    String content = "this is: [not yaml\n";
    PresetFetcher fetcher = url -> {
      fetched.incrementAndGet();
      return content.getBytes(StandardCharsets.UTF_8);
    };
    ProjectLoad load = project().manifest(urlManifest(digestOf("something else")))
        .load(CompileOptions.builder().presetFetcher(fetcher).build());
    assertFindings(load, "E_PRESET tierwise.yaml:3 [extends, 0]");
    assertEquals(1, fetched.get());
    assertFalse(load.findings().stream().anyMatch(f -> f.code().equals("E_PARSE")), "MANIFEST-29: not interpreted");
    assertTrue(load.sources().documents().isEmpty());
  }

  @Test
  void MANIFEST_29_aPresetWhoseDigestDoesNotMatchContributesNothingAndItsContentIsNotRead() {
    String content = "keys:\n  a.b.c: {}\nextends: []\nunknown: [\n";
    PresetFetcher fetcher = url -> content.getBytes(StandardCharsets.UTF_8);
    ProjectLoad load = project().manifest(urlManifest(digestOf("other bytes")))
        .load(CompileOptions.builder().presetFetcher(fetcher).build());
    assertFindings(load, "E_PRESET tierwise.yaml:3 [extends, 0]");
    assertTrue(load.sources().documents().isEmpty());
    assertTrue(load.sources().lint().layers().get(0).settings().isEmpty());
  }

  @Test
  void MANIFEST_25_aFetchedPresetWithTheRightDigestIsNamedByItsUrl() {
    String content = "lint:\n  rules:\n    values/no-op: warn\n";
    List<String> urls = new ArrayList<>();
    PresetFetcher fetcher = url -> {
      urls.add(url);
      return content.getBytes(StandardCharsets.UTF_8);
    };
    ProjectLoad load = project().manifest(urlManifest(digestOf(content)))
        .load(CompileOptions.builder().presetFetcher(fetcher).build());
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    assertEquals(List.of("https://presets.invalid/p.yaml"), urls);
    assertEquals(List.of("https://presets.invalid/p.yaml"), ProjectFixture.files(load));
  }

  @Test
  void aFaultInAFetchedPresetIsLocatedInThePresetNamedByItsUrl() {
    String content = "extends: []\n";
    PresetFetcher fetcher = url -> content.getBytes(StandardCharsets.UTF_8);
    assertFindings(project().manifest(urlManifest(digestOf(content)))
        .load(CompileOptions.builder().presetFetcher(fetcher).build()),
        "E_PRESET https://presets.invalid/p.yaml:1 [extends]");
  }

  @Test
  void MANIFEST_30_aUrlPresetIsIdentifiedByItsDigest() {
    String content = "lint: {}\n";
    String digest = digestOf(content);
    PresetFetcher fetcher = url -> content.getBytes(StandardCharsets.UTF_8);
    String manifest = HEAD + "  - url: \"https://a.invalid/p.yaml\"\n    sha256: \"" + digest + "\"\n"
        + "  - url: \"https://b.invalid/p.yaml\"\n    sha256: \"" + digest + "\"\n";
    assertFindings(project().manifest(manifest).load(CompileOptions.builder().presetFetcher(fetcher).build()),
        "E_PRESET tierwise.yaml:5 [extends, 1]");
  }

  @Test
  void FIND_18_theMessageOfAFindingInAPresetNamesTheExtendsItemThatBroughtItIn() {
    ProjectLoad load = project().manifest(HEAD + "  - \"tierwise:minimal\"\n  - \"presets/c.yaml\"\n")
        .file("presets/c.yaml", "extends: []\nbad: [\n").load();
    assertFindings(load, "E_PARSE presets/c.yaml:1 []");
    assertTrue(load.findings().get(0).message().contains("extends item 1"), load.findings().get(0).message());
    ProjectLoad semantic = project().manifest(HEAD + "  - \"presets/c.yaml\"\n").file("presets/c.yaml", "extends: []\n").load();
    assertTrue(semantic.findings().get(0).message().contains("extends item 0"), semantic.findings().get(0).message());
  }
}
