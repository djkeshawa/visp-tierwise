package io.github.tierwise.core.internal.json;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The SHA-256 digest that names a bundle (BUNDLE-5, BUNDLE-6), from the JDK's own implementation.
 */
public final class Sha256 {

  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private Sha256() {
  }

  /**
   * Computes the digest of a byte array.
   *
   * @param input the bytes to hash
   * @return the 32 digest bytes
   */
  public static byte[] digest(byte[] input) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(input);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("every Java platform provides SHA-256", e);
    }
  }

  /**
   * Computes the digest of a byte array and renders it as text.
   *
   * @param input the bytes to hash
   * @return the 64 lower-case hexadecimal digits
   */
  public static String hexDigest(byte[] input) {
    return hex(digest(input));
  }

  /**
   * Renders bytes as lower-case hexadecimal.
   *
   * @param bytes the bytes
   * @return two lower-case digits per byte
   */
  public static String hex(byte[] bytes) {
    StringBuilder out = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      out.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
    }
    return out.toString();
  }
}
