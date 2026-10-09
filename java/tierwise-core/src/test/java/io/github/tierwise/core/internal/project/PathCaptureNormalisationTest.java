package io.github.tierwise.core.internal.project;

import static io.github.tierwise.core.internal.project.ProjectFixture.document;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.CompileOptions;
import io.github.tierwise.core.CompileResult;
import io.github.tierwise.core.Finding;
import io.github.tierwise.core.Tierwise;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * SRC-24: the text that a {@code {scope}} or {@code {node}} placeholder captures from a file path is NFC-normalised
 * before it is used, so that names from file paths and names from YAML content agree (NORM-1, NORM-6). The
 * file names here are written in NFD. The conformance suite keeps non-ASCII file names out (VEC-92), so this test
 * is the enforcer.
 */
class PathCaptureNormalisationTest {

  /** "café" in NFC: one code point for the accented letter. */
  private static final String NODE_NFC = "café";
  /** "café" in NFD: the letter e followed by a combining acute accent. */
  private static final String NODE_NFD = "café";
  /** "région" in NFC and NFD. */
  private static final String SCOPE_NFC = "région";
  private static final String SCOPE_NFD = "région";

  @TempDir
  Path dir;

  /** Writes a file whose name holds NFD text, or skips the test where the platform cannot name it. */
  private void writeNfd(ProjectFixture project, String path, String content) {
    try {
      project.file(path, content);
    } catch (InvalidPathException e) {
      assumeTrue(false, "the platform cannot encode non-ASCII file names: " + e.getMessage());
    }
    try (var names = Files.list(dir.resolve(path).getParent())) {
      boolean kept = names.anyMatch(p -> p.getFileName().toString().equals(Path.of(path).getFileName().toString()));
      assumeTrue(kept, "the file system does not keep NFD file names");
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  @Test
  void SRC_24_aScopeAndANodeCapturedInNfdBecomeNfcNames() {
    assertNotEquals(NODE_NFC, NODE_NFD);
    assertEquals(NODE_NFC, Normalizer.normalize(NODE_NFD, Normalizer.Form.NFC));
    ProjectFixture project = new ProjectFixture(dir)
        .manifest("specVersion: \"0.1\"\nsources:\n  values:\n    - pattern: 'v/{scope}/{node}.yaml'\n");
    String file = "v/" + SCOPE_NFD + "/" + NODE_NFD + ".yaml";
    writeNfd(project, file, "k: 1\n");
    ProjectLoad load = project.load();
    ValuesMapping.PathMapped mapping = (ValuesMapping.PathMapped) document(load, file).mapping().orElseThrow();
    assertEquals(NODE_NFC, mapping.node());
    assertEquals(SCOPE_NFC, mapping.scope());
  }

  private static final String SCHEMA = """
      decision_hierarchy:
        levels: [global, region]
        scopes:
          region:
            nodes: [%s]
            on_unknown_node: error
      keys:
        shop.checkout.enabled:
          type: boolean
          combine: any
          settable_at: [global, region]
      """.formatted(NODE_NFC);

  @Test
  void SRC_24_aNodeCapturedInNfdMatchesTheNfcNodeOfTheCatalog() {
    ProjectFixture project = new ProjectFixture(dir).manifest("specVersion: \"0.1\"\nsources:\n  schema: [schema.yaml]\n"
        + "  values:\n    - pattern: 'v/{scope}/{node}.yaml'\n").file("schema.yaml", SCHEMA);
    writeNfd(project, "v/region/" + NODE_NFD + ".yaml", "shop.checkout.enabled: true\n");
    CompileResult result = Tierwise.compile(dir, CompileOptions.defaults());
    assertEquals(List.of(), result.findings(), "SCOPE-43: the captured node is a node of the catalog");
    assertTrue(result.bundle().isPresent());
  }

  @Test
  void SRC_24_aNodeCapturedInNfdIsTheSameNodeAsTheNfcNodeOfAMultiNodeFile() {
    ProjectFixture project = new ProjectFixture(dir).manifest("specVersion: \"0.1\"\nsources:\n  schema: [schema.yaml]\n"
        + "  values:\n    - pattern: 'v/{scope}/{node}.yaml'\n    - file: nodes.yaml\n").file("schema.yaml", SCHEMA)
        .file("nodes.yaml", "region:\n  " + NODE_NFC + ":\n    shop.checkout.enabled: false\n");
    writeNfd(project, "v/region/" + NODE_NFD + ".yaml", "shop.checkout.enabled: true\n");
    CompileResult result = Tierwise.compile(dir, CompileOptions.defaults());
    assertEquals(List.of(Code.E_DUPLICATE_VALUE), result.findings().stream().map(Finding::code).toList(),
        "KEY-12: two values for one key at one node: " + result.findings());
  }
}
