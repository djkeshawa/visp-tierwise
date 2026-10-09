package io.github.tierwise.core.conformance;

import io.github.tierwise.core.internal.json.CanonicalJson;
import io.github.tierwise.core.internal.json.Sha256;
import java.util.HexFormat;

/**
 * Reaches the two canonical primitives of {@code internal.json}: the JCS serialisation of a plain value tree
 * (BUNDLE-7, BUNDLE-9 to BUNDLE-13) and the SHA-256 digest (BUNDLE-6).
 */
final class Primitives {

  private Primitives() {
  }

  /**
   * Serialises a plain value tree to its JCS bytes.
   *
   * @param value the value
   * @return the bytes
   */
  static byte[] canonicalJson(Object value) {
    return CanonicalJson.toBytes(value);
  }

  /**
   * Computes the SHA-256 digest.
   *
   * @param input the bytes to hash
   * @return the digest as 64 lower-case hexadecimal characters
   */
  static String sha256Hex(byte[] input) {
    return Sha256.hexDigest(input);
  }

  /**
   * Renders bytes as lower-case hexadecimal.
   *
   * @param bytes the bytes
   * @return the hexadecimal text
   */
  static String hex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }
}
