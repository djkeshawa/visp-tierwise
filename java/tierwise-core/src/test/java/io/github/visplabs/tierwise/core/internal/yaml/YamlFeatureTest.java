package io.github.visplabs.tierwise.core.internal.yaml;

import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.assertFinding;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.at;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.load;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.loadManifest;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.onlyFinding;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.root;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.Finding;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Aliases, anchors, explicit tags and merge keys (SRC-2, SRC-7, FIND-19). */
class YamlFeatureTest {

  @Test
  void anchorIsReportedAtTheNodeThatCarriesIt() {
    YamlLoadResult result = loadManifest("specVersion: \"0.1\"\nsources: &srcs\n  schema: []\n");
    Finding finding = onlyFinding(result, Code.E_YAML_FEATURE);
    assertFinding(finding, Code.E_YAML_FEATURE, List.of("sources"), 2);
  }

  @Test
  void aliasIsReportedAtTheAliasingKey() {
    String text = "naming:\n  key_a: &p {category: key}\n  key_b: *p\n";
    YamlLoadResult result = load(text);
    assertEquals(2, result.findings().size(), "findings: " + result.findings());
    assertFinding(result.findings().get(0), Code.E_YAML_FEATURE, List.of("naming", "key_a"), 2);
    assertFinding(result.findings().get(1), Code.E_YAML_FEATURE, List.of("naming", "key_b"), 3);
  }

  @Test
  void aliasTargetIsCopiedToTheAliasingKeyWithItsPaths() {
    PMap root = root(load("naming:\n  key_a: &p {category: key}\n  key_b: *p\n"));
    PNode copy = at(root, "naming", "key_b");
    assertEquals(List.of("naming", "key_b"), copy.path());
    assertEquals(3, copy.line());
    assertEquals(List.of("naming", "key_b", "category"), at(root, "naming", "key_b", "category").path());
    assertEquals("key", scalar(at(root, "naming", "key_b", "category")).value());
  }

  @Test
  void aliasToAnAncestorIsReportedAndNotCopied() {
    YamlLoadResult result = load("a: &x [*x]\n");
    assertEquals(2, result.findings().size(), "findings: " + result.findings());
    assertTrue(result.findings().stream().allMatch(f -> f.code().equals(Code.E_YAML_FEATURE)));
    assertEquals(0, ((PSeq) at(root(result), "a")).items().size());
  }

  @Test
  void undefinedAliasIsReportedAndDropped() {
    YamlLoadResult result = load("a: *missing\nb: 1\n");
    assertFinding(onlyFinding(result, Code.E_YAML_FEATURE), Code.E_YAML_FEATURE, List.of("a"), 1);
    assertFalse(root(result).has("a"));
    assertTrue(root(result).has("b"));
  }

  @Test
  void explicitTagIsReportedAtItsNodeAndTheValueIsStillRead() {
    YamlLoadResult result = loadManifest("specVersion: !!str \"0.1\"\n");
    assertFinding(onlyFinding(result, Code.E_YAML_FEATURE), Code.E_YAML_FEATURE, List.of("specVersion"), 1);
    assertEquals("0.1", scalar(at(root(result), "specVersion")).value());
  }

  @Test
  void localTagIsAnExplicitTag() {
    YamlLoadResult result = load("a: !custom value\n");
    assertFinding(onlyFinding(result, Code.E_YAML_FEATURE), Code.E_YAML_FEATURE, List.of("a"), 1);
  }

  @Test
  void plainMergeKeyIsReportedAtTheKeyAndDropped() {
    YamlLoadResult result = load("naming:\n  key_a: {}\n  <<: {}\n");
    assertFinding(onlyFinding(result, Code.E_YAML_FEATURE), Code.E_YAML_FEATURE, List.of("naming", "<<"), 3);
    PMap naming = (PMap) at(root(result), "naming");
    assertEquals(List.of("key_a"), List.copyOf(naming.entries().keySet()));
  }

  @Test
  void findingsWithTheSameFindingKeyAreReportedOnce() {
    YamlLoadResult result = load("naming:\n  <<: *missing\n");
    assertFinding(onlyFinding(result, Code.E_YAML_FEATURE), Code.E_YAML_FEATURE, List.of("naming", "<<"), 2);
  }

  @Test
  void quotedMergeKeyIsAnOrdinaryKey() {
    YamlLoadResult result = load("\"<<\": 1\n");
    assertTrue(result.findings().isEmpty(), "findings: " + result.findings());
    assertEquals(1L, scalar(root(result).get("<<").orElseThrow()).value());
  }

  @Test
  void tenLevelsOfTenAliasesFinishWithinTheCopyBudgetAndEveryAliasIsReported() {
    StringBuilder text = new StringBuilder("l0: &l0 x\n");
    for (int level = 1; level <= 10; level++) {
      text.append("l").append(level).append(": &l").append(level).append(" [");
      text.append(String.join(", ", Collections.nCopies(10, "*l" + (level - 1))));
      text.append("]\n");
    }
    YamlLoadResult result = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> load(text.toString()));
    for (int level = 1; level <= 10; level++) {
      for (int item = 0; item < 10; item++) {
        assertAliasFinding(result, List.of("l" + level, item), level + 1);
      }
    }
    assertTrue(countNodes(root(result)) <= AnchorTable.COPY_BUDGET + 200, "the copies are bounded");
  }

  @Test
  void aliasWhoseCopyWouldBeTooDeepIsReportedAndDropped() {
    String anchored = "a: &x " + "[".repeat(98) + "]".repeat(98) + "\n";
    String aliasing = "b: " + "[".repeat(49) + "*x" + "]".repeat(49) + "\n";
    YamlLoadResult result = load(anchored + aliasing);
    List<Object> aliasPath = new ArrayList<>(List.of("b"));
    for (int i = 0; i < 49; i++) {
      aliasPath.add(0);
    }
    assertEquals(2, result.findings().size(), "findings: " + result.findings());
    assertFinding(result.findings().get(0), Code.E_YAML_FEATURE, List.of("a"), 1);
    assertFinding(result.findings().get(1), Code.E_YAML_FEATURE, aliasPath, 2);
    PNode node = at(root(result), "b");
    for (int i = 0; i < 48; i++) {
      node = ((PSeq) node).items().get(0);
    }
    assertTrue(((PSeq) node).items().isEmpty(), "the too-deep copy is dropped");
  }

  private static void assertAliasFinding(YamlLoadResult result, List<Object> path, int line) {
    boolean found = result.findings().stream().anyMatch(
        f -> f.code().equals(Code.E_YAML_FEATURE) && f.path().equals(path) && f.line() == line);
    assertTrue(found, "an alias finding at " + path + " line " + line + " in " + result.findings());
  }

  private static int countNodes(PNode node) {
    int total = 1;
    if (node instanceof PMap map) {
      for (PNode child : map.entries().values()) {
        total += countNodes(child);
      }
    } else if (node instanceof PSeq seq) {
      for (PNode child : seq.items()) {
        total += countNodes(child);
      }
    }
    return total;
  }
}
