package io.github.tierwise.core.internal.project;

import static io.github.tierwise.core.internal.project.ProjectFixture.assertFindings;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.tierwise.core.CompileOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * MANIFEST-12: every path that the manifest names directly, and every file that a glob or a path-mapped pattern
 * selects, must resolve inside the project root after symbolic links are resolved. Each kind of path that the
 * clause lists is tried with a symbolic link that leads out of the root. VEC-92 keeps such paths out of the
 * compile cases, so this test is the enforcer.
 */
class ManifestPathEscapeTest {

  private static final String HEAD = "specVersion: \"0.1\"\n";
  private static final String EMPTY = "{}\n";

  @TempDir
  Path dir;

  /** The project root, one level below the temporary directory, so that a file can sit outside it. */
  private Path root() {
    return dir.resolve("project");
  }

  private ProjectFixture project() {
    return new ProjectFixture(root());
  }

  private ProjectFixture outside() {
    return new ProjectFixture(dir.resolve("outside"));
  }

  /** Creates a symbolic link inside the project root, or skips the test where links are not supported. */
  private void link(String name, Path target) throws IOException {
    Path link = root().resolve(name);
    Files.createDirectories(link.getParent());
    boolean created;
    try {
      Files.createSymbolicLink(link, target);
      created = true;
    } catch (UnsupportedOperationException | IOException | SecurityException e) {
      created = false;
    }
    assumeTrue(created, "the file system does not support symbolic links");
  }

  private ProjectLoad load() {
    return ProjectLoader.load(root(), CompileOptions.defaults());
  }

  @Test
  void MANIFEST_12_aValuesFileThatIsALinkOutsideTheRootIsEManifestAtItsFileMember() throws IOException {
    outside().file("root.yaml", "k: 1\n");
    project().manifest(HEAD + "sources:\n  values:\n    - file: values/root.yaml\n      scope: global\n");
    link("values/root.yaml", dir.resolve("outside/root.yaml"));
    ProjectLoad load = load();
    assertFindings(load, "E_MANIFEST tierwise.yaml:4 [sources, values, 0, file]");
    assertTrue(load.sources().documents().isEmpty(), "MANIFEST-12: the outside file contributes nothing");
  }

  @Test
  void MANIFEST_12_aNamespaceSchemaOutsideTheRootIsEManifestAtItsSchemaMember() throws IOException {
    outside().file("pos.yaml", "namespace: pos\n");
    project().manifest(HEAD + "namespaces:\n  pos: {schema: schema/pos.yaml}\n  shop: {schema: ../outside/pos.yaml}\n");
    link("schema/pos.yaml", dir.resolve("outside/pos.yaml"));
    ProjectLoad load = load();
    assertFindings(load, "E_MANIFEST tierwise.yaml:3 [namespaces, pos, schema]",
        "E_MANIFEST tierwise.yaml:4 [namespaces, shop, schema]");
    assertTrue(load.sources().documents().isEmpty());
  }

  @Test
  void MANIFEST_12_aGlobThatMatchesALinkToAFileOutsideTheRootIsEManifestAtThePattern() throws IOException {
    outside().file("leak.yaml", "keys: {}\n");
    project().manifest(HEAD + "sources:\n  schema: ['schema/*.yaml']\n  rules: ['rules/**/*.yaml']\n")
        .file("schema/inside.yaml", EMPTY);
    link("schema/leak.yaml", dir.resolve("outside/leak.yaml"));
    link("rules/linked", dir.resolve("outside"));
    ProjectLoad load = load();
    assertFindings(load, "E_MANIFEST tierwise.yaml:3 [sources, schema, 0]", "E_MANIFEST tierwise.yaml:4 [sources, rules, 0]");
    assertEquals(List.of("schema/inside.yaml"), ProjectFixture.files(load), "the inside match still contributes");
  }

  @Test
  void MANIFEST_12_aPathMappedPatternThatSelectsAFileOutsideTheRootIsEManifestAtThePattern() throws IOException {
    outside().file("north.yaml", "k: 1\n");
    project().manifest(HEAD + "sources:\n  values:\n    - pattern: 'values/{scope}/{node}.yaml'\n")
        .file("values/region/south.yaml", "k: 1\n");
    link("values/region/north.yaml", dir.resolve("outside/north.yaml"));
    link("values/division", dir.resolve("outside"));
    ProjectLoad load = load();
    assertFindings(load, "E_MANIFEST tierwise.yaml:4 [sources, values, 0, pattern]");
    assertEquals(List.of("values/region/south.yaml"), ProjectFixture.files(load));
  }

  @Test
  void MANIFEST_12_aPresetPathThatIsALinkToADirectoryOutsideTheRootIsEManifest() throws IOException {
    outside().file("presets/p.yaml", "lint:\n  rules: {}\n");
    project().manifest(HEAD + "extends:\n  - presets/p.yaml\n");
    link("presets", dir.resolve("outside/presets"));
    assertFindings(load(), "E_MANIFEST tierwise.yaml:3 [extends, 0]");
  }

  @Test
  void MANIFEST_12_aPathWithDotDotThatStaysInsideTheRootIsAccepted() {
    ProjectLoad load = project().manifest(HEAD + "sources:\n  schema: ['schema/../schema/*.yaml']\n  values:\n"
        + "    - file: values/../values/root.yaml\n      scope: global\n")
        .file("schema/a.yaml", EMPTY).file("values/root.yaml", EMPTY).load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
  }

  @Test
  void MANIFEST_12_aLinkWhoseTargetIsInsideTheRootIsFollowed() throws IOException {
    project().manifest(HEAD + "sources:\n  values:\n    - file: values/root.yaml\n      scope: global\n"
        + "    - pattern: 'mapped/{scope}/{node}.yaml'\nnamespaces:\n  pos: {schema: ns/pos.yaml}\n")
        .file("real/root.yaml", "k: 1\n").file("real/pos.yaml", "namespace: pos\n").file("realmap/north.yaml", "k: 2\n");
    link("values/root.yaml", root().resolve("real/root.yaml"));
    link("ns/pos.yaml", root().resolve("real/pos.yaml"));
    link("mapped/region", root().resolve("realmap"));
    ProjectLoad load = load();
    assertTrue(load.findings().isEmpty(), load.findings().toString());
    assertTrue(ProjectFixture.files(load).containsAll(List.of("values/root.yaml", "ns/pos.yaml", "mapped/region/north.yaml")),
        ProjectFixture.files(load).toString());
  }
}
