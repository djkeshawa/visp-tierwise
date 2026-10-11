package io.github.visplabs.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Finding;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PScalar;
import io.github.visplabs.tierwise.core.internal.yaml.PSeq;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The reader of generated catalogs: encoding, well-formedness, duplicates and positions (SCOPE-30, FIND-19 to FIND-22). */
class HierarchyJsonTest {

  private static final String FILE = "catalogs/divisions.json";

  private static HierarchyJson.Read read(String text) {
    return HierarchyJson.read(FILE, text.getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void scope30_aByteOrderMarkIsAParseFaultAtTheRoot() {
    byte[] bytes = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '{', '}'};
    HierarchyJson.Read read = HierarchyJson.read(FILE, bytes);
    assertTrue(read.root().isEmpty());
    assertFalse(read.findings().isEmpty());
    Finding finding = read.findings().get(0);
    assertEquals("E_PARSE", finding.code());
    assertEquals(List.of(), finding.path());
    assertEquals(1, finding.line());
  }

  @Test
  void scope30_invalidUtf8IsAParseFault() {
    HierarchyJson.Read read = HierarchyJson.read(FILE, new byte[] {'{', '"', (byte) 0xC3, '"', '}'});
    assertTrue(read.root().isEmpty());
    assertEquals("E_PARSE", read.findings().get(0).code());
  }

  @Test
  void scope30_malformedJsonIsAParseFault() {
    assertEquals("E_PARSE", read("{\"nodes\": [").findings().get(0).code());
    assertEquals("E_PARSE", read("{nodes: []}").findings().get(0).code());
    assertEquals("E_PARSE", read("").findings().get(0).code());
  }

  @Test
  void scope30_dataAfterTheDocumentIsAParseFault() {
    assertEquals("E_PARSE", read("{\"nodes\": []} {}").findings().get(0).code());
  }

  @Test
  void scope30_aRepeatedPropertyIsDuplicateKeyAtTheSecondOccurrence() {
    String text = "{\n  \"nodes\": [\"a\"],\n  \"nodes\": [\"b\"]\n}";
    HierarchyJson.Read read = read(text);
    assertEquals(1, read.findings().size());
    Finding finding = read.findings().get(0);
    assertEquals("E_DUPLICATE_KEY", finding.code());
    assertEquals(List.of("nodes"), finding.path());
    assertEquals(3, finding.line());
    PSeq nodes = (PSeq) ((PMap) read.root().orElseThrow()).get("nodes").orElseThrow();
    assertEquals("a", ((PScalar) nodes.items().get(0)).value(), "the first value is kept");
  }

  @Test
  void positionsFollowTheRulesOfFindings19To22() {
    String text = "{\n  \"nodes\": [\n    \"north\",\n    \"south\"\n  ]\n}";
    PMap root = (PMap) read(text).root().orElseThrow();
    assertEquals(1, root.line());
    assertEquals(List.of(), root.path());
    PSeq nodes = (PSeq) root.get("nodes").orElseThrow();
    assertEquals(2, nodes.line(), "a value begins on the line of its key");
    assertEquals(List.of("nodes"), nodes.path());
    assertEquals(3, nodes.items().get(0).line());
    assertEquals(List.of("nodes", 1), nodes.items().get(1).path());
    assertEquals(4, nodes.items().get(1).line());
  }

  @Test
  void scalarKindsFollowTheCoreSchema() {
    PMap root = (PMap) read("{\"n\": 1, \"f\": 1.5, \"b\": true, \"z\": null, \"s\": \"x\"}").root().orElseThrow();
    assertEquals(PScalar.Kind.INTEGER, ((PScalar) root.get("n").orElseThrow()).kind());
    assertEquals(PScalar.Kind.FLOAT, ((PScalar) root.get("f").orElseThrow()).kind());
    assertEquals(PScalar.Kind.BOOLEAN, ((PScalar) root.get("b").orElseThrow()).kind());
    assertEquals(PScalar.Kind.NULL, ((PScalar) root.get("z").orElseThrow()).kind());
    PNode text = root.get("s").orElseThrow();
    assertEquals("x", ((PScalar) text).value());
  }
}
