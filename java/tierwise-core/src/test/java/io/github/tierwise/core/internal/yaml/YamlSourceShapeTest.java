package io.github.tierwise.core.internal.yaml;

import static io.github.tierwise.core.internal.yaml.YamlTestSupport.assertFinding;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.load;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.loadBytes;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.loadManifest;
import static io.github.tierwise.core.internal.yaml.YamlTestSupport.onlyFinding;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Finding;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * File-level faults: encoding, byte-order mark, syntax, document count and root shape (SRC-5, SRC-6,
 * SRC-10, MANIFEST-2). Each is reported at the empty path and line 1 (FIND-23), and it is the only
 * finding of its source.
 */
class YamlSourceShapeTest {

  private static void assertFileFault(YamlLoadResult result, String code) {
    assertFinding(onlyFinding(result, code), code, List.of(), 1);
    assertTrue(result.root().isEmpty(), "a faulty source has no root");
  }

  @Test
  void byteOrderMarkIsEParseForASource() {
    byte[] bytes = concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, "a: 1\n");
    assertFileFault(loadBytes(bytes, false), Code.E_PARSE);
  }

  @Test
  void byteOrderMarkIsEManifestForTheManifest() {
    byte[] bytes = concat(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, "specVersion: \"0.1\"\n");
    assertFileFault(loadBytes(bytes, true), Code.E_MANIFEST);
  }

  @Test
  void invalidUtf8IsEParseEvenInsideAComment() {
    byte[] bytes = concat(new byte[] {'#', ' ', (byte) 0xFF, '\n'}, "a: 1\n");
    assertFileFault(loadBytes(bytes, false), Code.E_PARSE);
  }

  @Test
  void syntaxErrorIsEParseForASource() {
    assertFileFault(load("a: [1\n"), Code.E_PARSE);
  }

  @Test
  void syntaxErrorIsEManifestForTheManifest() {
    assertFileFault(loadManifest("a: [1\n"), Code.E_MANIFEST);
  }

  @Test
  void twoDocumentsAreEParseAndNothingElse() {
    YamlLoadResult result = load("a: 1\n---\nb: 2\n");
    assertFileFault(result, Code.E_PARSE);
    assertEquals(1, result.findings().size());
  }

  @Test
  void emptySourceAndCommentOnlySourceAreEParse() {
    assertFileFault(load(""), Code.E_PARSE);
    assertFileFault(load("# only a comment\n"), Code.E_PARSE);
  }

  @Test
  void documentWithNullRootIsEParse() {
    assertFileFault(load("---\n"), Code.E_PARSE);
  }

  @Test
  void sequenceRootIsEParseForASource() {
    assertFileFault(load("- a\n- b\n"), Code.E_PARSE);
  }

  @Test
  void scalarRootIsEParseForASource() {
    assertFileFault(load("just text\n"), Code.E_PARSE);
  }

  @Test
  void sequenceRootIsEManifestForTheManifest() {
    assertFileFault(loadManifest("- a\n"), Code.E_MANIFEST);
  }

  @Test
  void yamlVersionOtherThanOneTwoIsRejected() {
    assertFileFault(load("%YAML 1.1\n---\na: 1\n"), Code.E_PARSE);
  }

  @Test
  void yamlVersionOneTwoIsAccepted() {
    YamlLoadResult result = load("%YAML 1.2\n---\na: 1\n");
    assertTrue(result.findings().isEmpty(), "findings: " + result.findings());
    assertTrue(result.root().isPresent());
  }

  @Test
  void fileFaultReplacesTheNodeFindingsOfTheSameSource() {
    YamlLoadResult result = load("a: &x 1\nb: *x\nc: [\n");
    assertFileFault(result, Code.E_PARSE);
  }

  @Test
  void nestingAtTheDepthLimitIsAcceptedAndOneLevelDeeperIsAFileFault() {
    YamlLoadResult atLimit = load("a: " + "[".repeat(98) + "x" + "]".repeat(98) + "\n");
    assertTrue(atLimit.findings().isEmpty(), "findings: " + atLimit.findings());
    assertNestedTooDeeply(load("a: " + "[".repeat(99) + "x" + "]".repeat(99) + "\n"));
  }

  @Test
  void hostileNestingIsAFileFaultAndDoesNotOverflowTheStack() {
    assertNestedTooDeeply(load("a: " + "[".repeat(100_000) + "]".repeat(100_000) + "\n"));
  }

  @Test
  void complexKeysThatNestAreAFileFaultAndDoNotLengthenThePath() {
    String key = "k";
    for (int level = 0; level < 1000; level++) {
      key = "{" + key + ": 1}";
    }
    assertNestedTooDeeply(load(key + "\n"));
  }

  private static void assertNestedTooDeeply(YamlLoadResult result) {
    Finding finding = onlyFinding(result, Code.E_PARSE);
    assertFinding(finding, Code.E_PARSE, List.of(), 1);
    assertTrue(finding.message().contains("nested too deeply"), finding.message());
    assertTrue(result.root().isEmpty(), "a faulty source has no root");
  }

  private static byte[] concat(byte[] prefix, String text) {
    byte[] rest = text.getBytes(StandardCharsets.UTF_8);
    byte[] all = new byte[prefix.length + rest.length];
    System.arraycopy(prefix, 0, all, 0, prefix.length);
    System.arraycopy(rest, 0, all, prefix.length, rest.length);
    return all;
  }
}
