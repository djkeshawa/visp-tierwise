package io.github.tierwise.core.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class VectorFilesTest {

  private static Object parse(String text) {
    return VectorFiles.parseYaml(text.getBytes(StandardCharsets.UTF_8), "test.yaml");
  }

  @Test
  void theSpecDirectoryIsFound() {
    assertTrue(Files.isDirectory(VectorFiles.conformanceDir().resolve("evaluate")));
    assertTrue(Files.isRegularFile(VectorFiles.specDir().resolve("spec.md")));
  }

  @Test
  void identifiersAreThePathWithoutTheExtension() {
    Path root = Path.of("/s/evaluate");
    assertEquals("group/case", VectorFiles.idOf(root, root.resolve("group").resolve("case.yaml")));
  }

  @Test
  void theCoreSchemaTypesScalars() {
    Map<String, Object> doc = VectorFiles.asStringMap(parse("a: 1\nb: 0x1F\nc: true\nd: null\ne: 1.5\nf: no\ng: \"1\"\n"), "doc");
    assertEquals(1, doc.get("a"));
    assertEquals(31, doc.get("b"));
    assertEquals(true, doc.get("c"));
    assertEquals(null, doc.get("d"));
    assertEquals(1.5, doc.get("e"));
    assertEquals("no", doc.get("f"));
    assertEquals("1", doc.get("g"));
  }

  @Test
  void duplicateKeysAreARunnerFailure() {
    assertThrows(RunnerFailure.class, () -> parse("a: 1\na: 2\n"));
  }

  @Test
  void aByteOrderMarkIsARunnerFailure() {
    byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'a', ':', ' ', '1'};
    assertThrows(RunnerFailure.class, () -> VectorFiles.parseYaml(bom, "bom.yaml"));
  }

  @Test
  void invalidUtf8IsARunnerFailure() {
    assertThrows(RunnerFailure.class, () -> VectorFiles.parseYaml(new byte[] {'a', ':', ' ', (byte) 0xC3, '\n'}, "bad.yaml"));
  }

  @Test
  void aliasesAndSeveralDocumentsAreARunnerFailure() {
    assertThrows(RunnerFailure.class, () -> parse("a: &x 1\nb: *x\n"));
    assertThrows(RunnerFailure.class, () -> parse("a: 1\n---\nb: 2\n"));
  }

  @Test
  void aNonStringKeyIsARunnerFailure() {
    assertThrows(RunnerFailure.class, () -> VectorFiles.asStringMap(parse("7: node\n"), "doc"));
    assertEquals(List.of("7"), List.copyOf(VectorFiles.asStringMap(parse("\"7\": node\n"), "doc").keySet()));
  }
}
