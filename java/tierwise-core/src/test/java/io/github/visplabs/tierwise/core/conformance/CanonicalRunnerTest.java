package io.github.visplabs.tierwise.core.conformance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CanonicalRunnerTest {

  @Test
  void utf8FieldsAreEncodedExactly() {
    assertArrayEquals("aé😀".getBytes(StandardCharsets.UTF_8),
        CanonicalRunner.bytes(Map.of("utf8", "aé😀"), "input"));
    assertArrayEquals(new byte[0], CanonicalRunner.bytes(Map.of("utf8", ""), "input"));
  }

  @Test
  void hexFieldsAreDecoded() {
    assertArrayEquals(new byte[] {(byte) 0xff, 0x00, 0x61}, CanonicalRunner.bytes(Map.of("hex", "ff0061"), "input"));
    assertArrayEquals(new byte[0], CanonicalRunner.bytes(Map.of("hex", ""), "input"));
  }

  @Test
  void aByteFieldTakesExactlyOneForm() {
    assertThrows(RunnerFailure.class, () -> CanonicalRunner.bytes(Map.of("utf8", "a", "hex", "61"), "input"));
    assertThrows(RunnerFailure.class, () -> CanonicalRunner.bytes(Map.of(), "input"));
    assertThrows(RunnerFailure.class, () -> CanonicalRunner.bytes(Map.of("hex", "6"), "input"));
    assertThrows(RunnerFailure.class, () -> CanonicalRunner.bytes(Map.of("hex", "FF"), "input"));
    assertThrows(RunnerFailure.class, () -> CanonicalRunner.bytes("text", "input"));
  }

  @Test
  void jsonModelConvertsIntegersAndKeepsOrder() {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("b", List.of(1, 2L, new BigInteger("9007199254740991")));
    input.put("a", null);
    Object model = CanonicalRunner.toJsonModel(input, "input");
    assertEquals(List.of("b", "a"), List.copyOf(((Map<?, ?>) model).keySet()));
    assertEquals(List.of(1L, 2L, 9007199254740991L), ((Map<?, ?>) model).get("b"));
  }

  @Test
  void inputOutsideTheJsonModelIsARunnerFailure() {
    assertThrows(RunnerFailure.class, () -> CanonicalRunner.toJsonModel(1.5, "input"));
    assertThrows(RunnerFailure.class, () -> CanonicalRunner.toJsonModel(new BigInteger("9007199254740992"), "input"));
    assertThrows(RunnerFailure.class, () -> CanonicalRunner.toJsonModel("\ud800", "input"));
    assertThrows(RunnerFailure.class, () -> CanonicalRunner.toJsonModel(Map.of(1, "x"), "input"));
  }

  @Test
  void hexRendersLowerCase() {
    assertEquals("00ff7a", Primitives.hex(new byte[] {0, (byte) 0xff, 0x7a}));
  }
}
