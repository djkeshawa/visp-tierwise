package io.github.tierwise.cli.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The reader of the JSON subset of canonical bundles. */
class JsonReaderTest {

  @Test
  void parsesNestedObjectsListsAndScalars() {
    Map<?, ?> value = (Map<?, ?>) JsonReader.parse("{\"a\": [1, true, null, \"x\"], \"b\": {\"c\": -5}}");
    assertEquals(Arrays.asList(1L, true, null, "x"), value.get("a"));
    assertEquals(Map.of("c", -5L), value.get("b"));
  }

  @Test
  void keepsTheDocumentOrderOfMembers() {
    Map<?, ?> value = (Map<?, ?>) JsonReader.parse("{\"b\": 1, \"a\": 2}");
    assertEquals(List.of("b", "a"), List.copyOf(value.keySet()));
  }

  @Test
  void decodesEscapesAndSurrogatePairs() {
    assertEquals("é😀\n", JsonReader.parse("\"\\u00e9\\ud83d\\ude00\\n\""));
  }

  @Test
  void rejectsANumberThatIsNotAnInteger() {
    assertThrows(IllegalStateException.class, () -> JsonReader.parse("1.5"));
  }

  @Test
  void rejectsAnExponent() {
    assertThrows(IllegalStateException.class, () -> JsonReader.parse("1e3"));
  }

  @Test
  void rejectsARepeatedMemberName() {
    assertThrows(IllegalStateException.class, () -> JsonReader.parse("{\"a\": 1, \"a\": 2}"));
  }

  @Test
  void rejectsTextAfterTheDocument() {
    assertThrows(IllegalStateException.class, () -> JsonReader.parse("1 2"));
  }
}
