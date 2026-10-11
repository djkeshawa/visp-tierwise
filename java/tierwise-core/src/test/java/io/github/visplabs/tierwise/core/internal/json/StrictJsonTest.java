package io.github.visplabs.tierwise.core.internal.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrictJsonTest {

  private static byte[] utf8(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static void assertRejected(String text) {
    assertThrows(JsonFormatException.class, () -> StrictJson.parse(utf8(text)), text);
  }

  @Test
  void parsesPlainValuesInDocumentOrder() {
    Object value = StrictJson.parse(utf8("{\"b\":[true,false,null,\"x\"],\"a\":-5}"));
    Map<?, ?> map = assertInstanceOf(Map.class, value);
    assertEquals(List.of("b", "a"), List.copyOf(map.keySet()));
    assertEquals(Arrays.asList(true, false, null, "x"), map.get("b"));
    assertEquals(-5L, map.get("a"));
    assertInstanceOf(Long.class, map.get("a"));
  }

  @Test
  void BUNDLE_111_duplicateMemberNameIsRejectedAtAnyDepth() {
    assertRejected("{\"a\":1,\"a\":1}");
    assertRejected("{\"x\":{\"y\":[{\"k\":1,\"k\":2}]}}");
  }

  @Test
  void BUNDLE_111_namesThatDifferOnlyInEscapesAreDuplicates() {
    assertRejected("{\"\\u0061\":1,\"a\":2}");
  }

  @Test
  void BUNDLE_11_nonIntegerNumbersAreRejected() {
    assertRejected("{\"a\":4.5}");
    assertRejected("{\"a\":1e3}");
    assertRejected("{\"a\":-0}");
    assertRejected("{\"a\":01}");
  }

  @Test
  void BUNDLE_12_integersOutsideTheSafeRangeAreRejected() {
    assertRejected("{\"a\":9007199254740992}");
    assertRejected("{\"a\":-9007199254740992}");
    assertEquals(9007199254740991L, StrictJson.parse(utf8("9007199254740991")));
    assertEquals(-9007199254740991L, StrictJson.parse(utf8("-9007199254740991")));
  }

  @Test
  void BUNDLE_13_unpairedSurrogateEscapeIsRejectedAndPairedIsAccepted() {
    assertRejected("\"\\ud800\"");
    assertRejected("\"\\udc00\"");
    assertEquals("\uD83D\uDE00", StrictJson.parse(utf8("\"\\ud83d\\ude00\"")));
  }

  @Test
  void BUNDLE_116_trailingDataIsRejected() {
    assertRejected("{}{}");
    assertRejected("{} x");
    assertRejected("1 2");
  }

  @Test
  void BUNDLE_116_wellFormedWhitespaceIsAcceptedBecauseTheByteFormIsCheckedElsewhere() {
    assertNull(StrictJson.parse(utf8(" null \n")));
  }

  @Test
  void BUNDLE_3_byteOrderMarkIsRejected() {
    byte[] bom = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, '{', '}'};
    assertThrows(JsonFormatException.class, () -> StrictJson.parse(bom));
  }

  @Test
  void BUNDLE_116_invalidUtf8IsRejected() {
    byte[] invalid = new byte[] {'"', (byte) 0xC3, '"'};
    assertThrows(JsonFormatException.class, () -> StrictJson.parse(invalid));
  }

  @Test
  void BUNDLE_116_emptyDocumentIsRejected() {
    assertRejected("");
  }
}
