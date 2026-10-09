package io.github.tierwise.core.internal.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Finding;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Helpers that load YAML text and navigate the resulting tree. */
final class YamlTestSupport {

  /** The default source path of the tests. */
  static final String FILE = "schema/core.yaml";

  private YamlTestSupport() {
  }

  static YamlLoadResult load(String text) {
    return YamlLoader.load(text.getBytes(StandardCharsets.UTF_8), FILE, false);
  }

  static YamlLoadResult loadManifest(String text) {
    return YamlLoader.load(text.getBytes(StandardCharsets.UTF_8), "tierwise.yaml", true);
  }

  static YamlLoadResult loadBytes(byte[] bytes, boolean manifest) {
    return YamlLoader.load(bytes, manifest ? "tierwise.yaml" : FILE, manifest);
  }

  static PMap root(YamlLoadResult result) {
    assertTrue(result.root().isPresent(), "a root was expected, findings: " + result.findings());
    return result.root().get();
  }

  /**
   * Returns the node at a path, where a {@code String} selects a mapping entry and an {@code Integer}
   * a sequence item.
   */
  static PNode at(PNode start, Object... path) {
    PNode node = start;
    for (Object segment : path) {
      if (segment instanceof String key) {
        assertTrue(node instanceof PMap, "a mapping is expected before " + key);
        node = ((PMap) node).get(key).orElseThrow(() -> new AssertionError("no entry " + key));
      } else {
        assertTrue(node instanceof PSeq, "a sequence is expected before " + segment);
        node = ((PSeq) node).items().get((Integer) segment);
      }
    }
    return node;
  }

  static PScalar scalar(PNode node) {
    assertTrue(node instanceof PScalar, "a scalar is expected, found " + node);
    return (PScalar) node;
  }

  static Finding onlyFinding(YamlLoadResult result, String code) {
    assertEquals(1, result.findings().size(), "findings: " + result.findings());
    Finding finding = result.findings().get(0);
    assertEquals(code, finding.code());
    return finding;
  }

  static void assertNoFindings(YamlLoadResult result) {
    assertFalse(result.findings().size() > 0, "no findings expected, found " + result.findings());
  }

  static void assertFinding(Finding finding, String code, List<Object> path, int line) {
    assertEquals(code, finding.code());
    assertEquals(path, finding.path());
    assertEquals(line, finding.line());
  }
}
