package io.github.tierwise.core.internal.json;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

/**
 * The JSON Canonicalization Scheme (RFC 8785) for the subset that a bundle uses (BUNDLE-7 to BUNDLE-13).
 *
 * <p>The value model is the plain-value model of the core: {@code null}, {@code Boolean}, integers
 * ({@code Long}, {@code Integer}, {@code Short} and {@code Byte}), {@code String}, collections and maps with
 * {@code String} names. No other number type is accepted, because a bundle holds only integers. Object
 * members are sorted by UTF-16 code units (BUNDLE-9), strings use the escapes of BUNDLE-10, and the output
 * has no insignificant whitespace (BUNDLE-8).
 */
public final class CanonicalJson {

  /** The largest magnitude of an integer in a bundle, 2^53 - 1 (BUNDLE-11, BUNDLE-12, DET-4). */
  public static final long MAX_SAFE_INTEGER = (1L << 53) - 1;

  private static final char[] HEX = "0123456789abcdef".toCharArray();

  private CanonicalJson() {
  }

  /**
   * Serialises a value to its canonical UTF-8 bytes.
   *
   * @param value {@code null}, a boolean, an integer within the safe range, a string without an unpaired
   *     surrogate, a collection or a map with string names of such values
   * @return the UTF-8 bytes of the JCS serialisation (BUNDLE-2)
   * @throws IllegalArgumentException if the value is outside the bundle subset
   */
  public static byte[] toBytes(Object value) {
    StringBuilder out = new StringBuilder();
    write(value, out);
    return out.toString().getBytes(StandardCharsets.UTF_8);
  }

  private static void write(Object value, StringBuilder out) {
    if (value == null) {
      out.append("null");
    } else if (value instanceof Boolean flag) {
      out.append(flag ? "true" : "false");
    } else if (value instanceof String text) {
      writeString(text, out);
    } else if (value instanceof Long || value instanceof Integer || value instanceof Short
        || value instanceof Byte) {
      writeInteger(((Number) value).longValue(), out);
    } else if (value instanceof Map<?, ?> map) {
      writeObject(map, out);
    } else if (value instanceof Collection<?> items) {
      writeArray(items, out);
    } else {
      throw new IllegalArgumentException("not a value of the bundle subset: " + value.getClass().getName());
    }
  }

  private static void writeInteger(long value, StringBuilder out) {
    if (value > MAX_SAFE_INTEGER || value < -MAX_SAFE_INTEGER) {
      throw new IllegalArgumentException("integer outside +/-(2^53 - 1): " + value);
    }
    out.append(value);
  }

  private static void writeObject(Map<?, ?> map, StringBuilder out) {
    // String's natural order compares UTF-16 code units, which is the member order of RFC 8785 (BUNDLE-9).
    TreeMap<String, Object> sorted = new TreeMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (!(entry.getKey() instanceof String name)) {
        throw new IllegalArgumentException("a member name is not a string: " + entry.getKey());
      }
      sorted.put(name, entry.getValue());
    }
    out.append('{');
    boolean first = true;
    for (Map.Entry<String, Object> entry : sorted.entrySet()) {
      if (!first) {
        out.append(',');
      }
      first = false;
      writeString(entry.getKey(), out);
      out.append(':');
      write(entry.getValue(), out);
    }
    out.append('}');
  }

  private static void writeArray(Collection<?> items, StringBuilder out) {
    out.append('[');
    boolean first = true;
    for (Object item : items) {
      if (!first) {
        out.append(',');
      }
      first = false;
      write(item, out);
    }
    out.append(']');
  }

  /**
   * Writes a string with the escapes of BUNDLE-10. Only the five named escapes, and a backslash-u escape with
   * four lower-case hexadecimal digits for the other control characters, are used. Every other character,
   * including the slash and all non-ASCII characters, is written as itself.
   */
  private static void writeString(String text, StringBuilder out) {
    requireNoUnpairedSurrogate(text);
    out.append('"');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\t' -> out.append("\\t");
        case '\n' -> out.append("\\n");
        case '\f' -> out.append("\\f");
        case '\r' -> out.append("\\r");
        default -> {
          if (c < 0x20) {
            out.append("\\u00").append(HEX[c >> 4]).append(HEX[c & 0xF]);
          } else {
            out.append(c);
          }
        }
      }
    }
    out.append('"');
  }

  private static void requireNoUnpairedSurrogate(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
        i++;
      } else if (Character.isSurrogate(c)) {
        throw new IllegalArgumentException("unpaired surrogate in a string (BUNDLE-13)");
      }
    }
  }
}
