package io.github.tierwise.core.internal.yaml;

import static io.github.tierwise.core.internal.yaml.YamlTestSupport.assertFinding;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.load;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.loadManifest;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.onlyFinding;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.root;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import java.util.List;
import org.junit.jupiter.api.Test;

/** A mapping key is a string. A key that the core schema reads as a non-string is reported (SRC-11). */
class YamlNonStringKeyTest {

  @Test
  void integerKeyIsReportedWithASuggestionToQuoteIt() {
    YamlLoadResult result = load("1: x\n");
    var finding = onlyFinding(result, Code.E_TYPE_MISMATCH);
    assertFinding(finding, Code.E_TYPE_MISMATCH, List.of("1"), 1);
    assertEquals("\"1\"", finding.suggestion().orElseThrow());
    assertFalse(root(result).has("1"));
  }

  @Test
  void nullKeyIsReported() {
    YamlLoadResult result = load("~: x\n");
    assertFinding(onlyFinding(result, Code.E_TYPE_MISMATCH), Code.E_TYPE_MISMATCH, List.of("~"), 1);
  }

  @Test
  void booleanKeyIsReported() {
    YamlLoadResult result = load("true: x\n");
    assertFinding(onlyFinding(result, Code.E_TYPE_MISMATCH), Code.E_TYPE_MISMATCH, List.of("true"), 1);
  }

  @Test
  void nonStringKeyInTheManifestIsEManifest() {
    YamlLoadResult result = loadManifest("specVersion: \"0.1\"\n1: x\n");
    assertFinding(onlyFinding(result, Code.E_MANIFEST), Code.E_MANIFEST, List.of("1"), 2);
  }

  @Test
  void quotedIntegerKeyIsAString() {
    YamlLoadResult result = load("\"1\": x\n");
    assertTrue(result.findings().isEmpty(), "findings: " + result.findings());
    assertTrue(root(result).has("1"));
  }

  @Test
  void keyThatIsAMappingIsReportedAndItsEntryIsDropped() {
    YamlLoadResult result = load("? {a: 1}\n: x\nb: 2\n");
    assertEquals(1, result.findings().size(), "findings: " + result.findings());
    assertEquals(Code.E_TYPE_MISMATCH, result.findings().get(0).code());
    assertTrue(root(result).has("b"));
    assertEquals(1, root(result).entries().size());
  }
}
