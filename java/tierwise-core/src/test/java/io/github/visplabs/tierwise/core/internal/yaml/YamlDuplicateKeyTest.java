package io.github.visplabs.tierwise.core.internal.yaml;

import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.FILE;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.assertFinding;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.at;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.load;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.loadManifest;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.onlyFinding;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.root;
import static io.github.visplabs.tierwise.core.internal.yaml.YamlTestSupport.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Code;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Duplicate keys, compared after NFC normalisation (SRC-3, SRC-4, SRC-9). */
class YamlDuplicateKeyTest {

  @Test
  void repeatedKeyIsReportedAtTheSecondOccurrenceAndTheFirstValueIsKept() {
    YamlLoadResult result = load("a: 1\nb: 2\na: 3\n");
    assertEquals(1, result.findings().size(), "findings: " + result.findings());
    assertFinding(result.findings().get(0), Code.E_DUPLICATE_KEY, List.of("a"), 3);
    assertEquals(1L, scalar(at(root(result), "a")).value());
  }

  @Test
  void keysEqualAfterNfcNormalisationAreDuplicatesAtTheSecondOccurrence() {
    String composed = "caf\u00e9";
    String decomposed = "cafe\u0301";
    YamlLoadResult result = load(composed + ": 1\n" + decomposed + ": 2\n");
    assertEquals(1, result.findings().size(), "findings: " + result.findings());
    assertFinding(result.findings().get(0), Code.E_DUPLICATE_KEY, List.of(decomposed), 2);
  }

  @Test
  void repeatedKeyAtAnyDepthIsReportedAtItsPath() {
    YamlLoadResult result = load("x:\n  y:\n    k: 1\n    k: 2\n");
    assertFinding(result.findings().get(0), Code.E_DUPLICATE_KEY, List.of("x", "y", "k"), 4);
  }

  @Test
  void quotedAndPlainKeysWithTheSameTextAreDuplicates() {
    YamlLoadResult result = load("\"a\": 1\na: 2\n");
    assertFinding(result.findings().get(0), Code.E_DUPLICATE_KEY, List.of("a"), 2);
  }

  @Test
  void everyRepeatIsReportedNotOnlyTheFirst() {
    YamlLoadResult result = load("a: 1\na: 2\nb: 1\nb: 2\n");
    assertEquals(2, result.findings().size(), "findings: " + result.findings());
    assertFinding(result.findings().get(0), Code.E_DUPLICATE_KEY, List.of("a"), 2);
    assertFinding(result.findings().get(1), Code.E_DUPLICATE_KEY, List.of("b"), 4);
  }

  @Test
  void duplicateKeyInTheManifestUsesTheSameCode() {
    YamlLoadResult result = loadManifest("specVersion: \"0.1\"\nspecVersion: \"0.1\"\n");
    assertFinding(result.findings().get(0), Code.E_DUPLICATE_KEY, List.of("specVersion"), 2);
  }

  @Test
  void keysAreKeptAsWrittenWhenNoPolicyNormalisesThem() {
    String composed = "caf\u00e9";
    String decomposed = "cafe\u0301";
    YamlLoadResult result = load(composed + ": 1\n" + decomposed + ": 2\n");
    assertFinding(onlyFinding(result, Code.E_DUPLICATE_KEY), Code.E_DUPLICATE_KEY, List.of(decomposed), 2);
    assertTrue(root(result).has(composed));
    assertFalse(root(result).has(decomposed));
  }

  @Test
  void keysOfAMappingThatASectionNormalisesAreComposedInTheTreeAndInFindings() {
    String composed = "customer.caf\u00e9";
    byte[] bytes = ("facts:\n  " + composed + ": 1\n  customer.cafe\u0301: 2\n").getBytes(StandardCharsets.UTF_8);
    YamlLoadResult result = YamlLoader.load(bytes, FILE, false, path -> path.equals(List.of("facts")));
    assertFinding(onlyFinding(result, Code.E_DUPLICATE_KEY), Code.E_DUPLICATE_KEY, List.of("facts", composed), 3);
    assertEquals(1L, scalar(at(root(result), "facts", composed)).value());
  }
}
