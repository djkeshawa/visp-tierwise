package io.github.visplabs.tierwise.core.internal.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class Sha256Test {

  @Test
  void BUNDLE_6_digestOfAbc() {
    assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        Sha256.hexDigest("abc".getBytes(StandardCharsets.UTF_8)));
  }

  @Test
  void BUNDLE_6_digestOfEmptyInput() {
    assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Sha256.hexDigest(new byte[0]));
  }

  @Test
  void BUNDLE_5_digestsAreLowerCaseHexadecimal() {
    String hex = Sha256.hexDigest(new byte[] {(byte) 0xFF});
    assertEquals(64, hex.length());
    assertEquals(hex.toLowerCase(java.util.Locale.ROOT), hex);
  }

  @Test
  void hexRendersTwoLowerCaseDigitsPerByte() {
    assertEquals("00ff7a", Sha256.hex(new byte[] {0, (byte) 0xff, 0x7a}));
  }
}
