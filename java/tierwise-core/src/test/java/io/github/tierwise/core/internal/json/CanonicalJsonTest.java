package io.github.tierwise.core.internal.json;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CanonicalJsonTest {

  private static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static void assertJcs(String expected, Object value) {
    assertArrayEquals(utf8(expected), CanonicalJson.toBytes(value));
  }

  @Test
  void BUNDLE_9_memberNamesSortByUtf16CodeUnits() {
    // U+1F600 is stored as the surrogate pair D83D DE00, which sorts before U+FF5E (BMP, above U+E000).
    Map<String, Object> members = new LinkedHashMap<>();
    members.put("～", 1);
    members.put("😀", 2);
    assertJcs("{\"😀\":2,\"～\":1}", members);
  }

  @Test
  void BUNDLE_9_memberOrderDoesNotDependOnInsertion() {
    Map<String, Object> members = new LinkedHashMap<>();
    members.put("b", 1);
    members.put("a", 2);
    members.put("aa", 3);
    assertJcs("{\"a\":2,\"aa\":3,\"b\":1}", members);
  }

  @Test
  void BUNDLE_8_noInsignificantWhitespace() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("list", List.of(1, 2, List.of()));
    value.put("empty", new LinkedHashMap<>());
    assertJcs("{\"empty\":{},\"list\":[1,2,[]]}", value);
  }

  @Test
  void BUNDLE_10_namedEscapesAndLowerCaseControlEscapes() {
    assertJcs("\"\\b\\t\\n\\f\\r\\\"\\\\\\u0001\\u001f\"", "\b\t\n\f\r\"\\\u0001\u001f");
  }

  @Test
  void BUNDLE_10_slashDeleteAndNonAsciiAreWrittenUnescaped() {
    assertJcs("\"/\u007fé€😀\"", "/\u007fé€😀");
    assertArrayEquals(utf8("\"/é\""), CanonicalJson.toBytes("/é"));
  }

  @Test
  void BUNDLE_11_integersAreWrittenInDecimalWithoutFraction() {
    assertJcs("[0,-1,42]", List.of(0L, -1L, 42));
  }

  @Test
  void BUNDLE_12_BUNDLE_DET_4_theSafeRangeIsInclusive() {
    assertJcs("[9007199254740991,-9007199254740991]", List.of(9007199254740991L, -9007199254740991L));
    assertThrows(IllegalArgumentException.class, () -> CanonicalJson.toBytes(9007199254740992L));
    assertThrows(IllegalArgumentException.class, () -> CanonicalJson.toBytes(-9007199254740992L));
  }

  @Test
  void BUNDLE_11_otherNumberTypesAreRejected() {
    assertThrows(IllegalArgumentException.class, () -> CanonicalJson.toBytes(1.5));
  }

  @Test
  void BUNDLE_13_unpairedSurrogateIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> CanonicalJson.toBytes("\uD800"));
    assertThrows(IllegalArgumentException.class, () -> CanonicalJson.toBytes("a\uDC00"));
  }

  @Test
  void BUNDLE_2_nullBooleansAndArrayOrderAreKept() {
    Map<String, Object> value = new LinkedHashMap<>();
    value.put("flag", false);
    value.put("none", null);
    value.put("items", Arrays.asList("z", "a"));
    assertJcs("{\"flag\":false,\"items\":[\"z\",\"a\"],\"none\":null}", value);
  }

  @Test
  void BUNDLE_7_stringKeysAreRequired() {
    Map<Object, Object> value = new LinkedHashMap<>();
    value.put(1, "x");
    assertThrows(IllegalArgumentException.class, () -> CanonicalJson.toBytes(value));
  }
}
