package io.github.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Finding;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import io.github.tierwise.core.internal.yaml.YamlLoadResult;
import io.github.tierwise.core.internal.yaml.YamlLoader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Helpers that load YAML text as a node tree and describe the findings it produces. */
final class CheckTestSupport {

  static final String FILE = "schema/keys.yaml";

  private CheckTestSupport() {
  }

  /** Loads a document whose root is a mapping, and requires that the loader reports nothing. */
  static PMap root(String yaml) {
    YamlLoadResult result = YamlLoader.load(yaml.getBytes(StandardCharsets.UTF_8), FILE, false);
    assertTrue(result.findings().isEmpty(), "the loader reported " + result.findings());
    return result.root().orElseThrow();
  }

  /** Returns the node at a path of keys and positions. */
  static PNode at(PNode start, Object... path) {
    PNode node = start;
    for (Object segment : path) {
      if (segment instanceof String key) {
        node = ((PMap) node).get(key).orElseThrow(() -> new AssertionError("no entry " + key));
      } else {
        node = ((io.github.tierwise.core.internal.yaml.PSeq) node).items().get((Integer) segment);
      }
    }
    return node;
  }

  static String describe(Finding finding) {
    return finding.code() + " " + finding.line() + " " + finding.path();
  }

  /** The sorted descriptions {@code CODE line [path]} of the findings. */
  static List<String> descriptions(Findings findings) {
    List<String> list = new ArrayList<>();
    for (Finding finding : findings.sorted()) {
      list.add(describe(finding));
    }
    Collections.sort(list);
    return list;
  }

  static void assertFindings(Findings findings, String... expected) {
    List<String> wanted = new ArrayList<>(List.of(expected));
    Collections.sort(wanted);
    assertEquals(wanted, descriptions(findings));
  }
}
