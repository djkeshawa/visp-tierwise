package io.github.visplabs.tierwise.core.conformance;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.opentest4j.AssertionFailedError;

/**
 * Runs one canonical vector (section 12.4): the JCS encoding of {@code input} or the SHA-256 digest of
 * its bytes, compared exactly with the expected bytes (VEC-78, VEC-82).
 */
final class CanonicalRunner {

  private static final BigInteger MAX_SAFE = BigInteger.valueOf((1L << 53) - 1);
  private static final Set<String> FIELDS =
      Set.of("id", "specVersion", "clauses", "description", "primitive", "input", "expect");

  private CanonicalRunner() {
  }

  /**
   * Runs the vector in a file.
   *
   * @param file the vector file under {@code canonical/}
   * @throws RunnerFailure if the vector cannot be executed as written
   * @throws AssertionFailedError if the computed bytes differ from the expected ones
   */
  static void run(Path file) {
    Map<String, Object> doc = VectorFiles.loadMapping(file);
    for (String name : doc.keySet()) {
      if (!FIELDS.contains(name)) {
        throw new RunnerFailure("unknown field " + name + " (VEC-19)");
      }
    }
    if (!"0.1".equals(doc.get("specVersion"))) {
      throw new RunnerFailure("unsupported specVersion (VEC-15)");
    }
    if (!doc.containsKey("input") || !(doc.get("expect") instanceof Map)) {
      throw new RunnerFailure("a canonical vector has input and expect");
    }
    Map<String, Object> expect = VectorFiles.asStringMap(doc.get("expect"), "expect");
    Object primitive = doc.get("primitive");
    if ("jcs".equals(primitive)) {
      byte[] expected = bytes(expect.get("output"), "expect.output");
      byte[] actual = Primitives.canonicalJson(toJsonModel(doc.get("input"), "input"));
      if (!Arrays.equals(expected, actual)) {
        throw new AssertionFailedError("JCS output differs\n  expected: " + describe(expected)
            + "\n  actual:   " + describe(actual));
      }
    } else if ("sha256".equals(primitive)) {
      if (!(expect.get("digest") instanceof String digest)) {
        throw new RunnerFailure("expect.digest must be a string (VEC-83)");
      }
      String actual = Primitives.sha256Hex(bytes(doc.get("input"), "input"));
      if (!digest.equals(actual)) {
        throw new AssertionFailedError("SHA-256 digest differs\n  expected: " + digest + "\n  actual:   " + actual);
      }
    } else {
      throw new RunnerFailure("primitive must be jcs or sha256 (VEC-75)");
    }
  }

  /**
   * Decodes a byte field: exactly one of {@code utf8} and {@code hex} (VEC-84).
   *
   * @param field the field value
   * @param what a description for error messages
   * @return the bytes
   * @throws RunnerFailure if the field has another form
   */
  static byte[] bytes(Object field, String what) {
    Map<String, Object> map = VectorFiles.asStringMap(field, what);
    if (map.size() != 1) {
      throw new RunnerFailure(what + " takes exactly one of utf8 and hex (VEC-84)");
    }
    if (map.get("utf8") instanceof String text) {
      requireNoUnpairedSurrogate(text, what);
      return text.getBytes(StandardCharsets.UTF_8);
    }
    if (map.get("hex") instanceof String hex) {
      try {
        if (!hex.equals(hex.toLowerCase(java.util.Locale.ROOT))) {
          throw new IllegalArgumentException("upper-case digits");
        }
        return HexFormat.of().parseHex(hex);
      } catch (IllegalArgumentException e) {
        throw new RunnerFailure(what + ".hex is not lower-case hexadecimal of even length (VEC-84)", e);
      }
    }
    throw new RunnerFailure(what + " takes exactly one of utf8 and hex (VEC-84)");
  }

  /**
   * Converts a YAML value of a {@code jcs} vector into the plain value tree that the encoder takes
   * (VEC-79): integers become {@code Long}, objects become maps in document order.
   *
   * @param value a YAML value
   * @param at a location for error messages
   * @return the plain value
   * @throws RunnerFailure if the value is outside the JSON data model of VEC-79
   */
  static Object toJsonModel(Object value, String at) {
    if (value == null || value instanceof Boolean) {
      return value;
    }
    if (value instanceof String text) {
      requireNoUnpairedSurrogate(text, at);
      return text;
    }
    if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
        || value instanceof BigInteger) {
      BigInteger big = value instanceof BigInteger b ? b : BigInteger.valueOf(((Number) value).longValue());
      if (big.abs().compareTo(MAX_SAFE) > 0) {
        throw new RunnerFailure(at + ": integer outside +/-(2^53 - 1) (VEC-79)");
      }
      return big.longValue();
    }
    if (value instanceof List<?> list) {
      List<Object> out = new java.util.ArrayList<>(list.size());
      for (int i = 0; i < list.size(); i++) {
        out.add(toJsonModel(list.get(i), at + "[" + i + "]"));
      }
      return out;
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!(entry.getKey() instanceof String name)) {
          throw new RunnerFailure(at + ": a member name is not a string");
        }
        requireNoUnpairedSurrogate(name, at);
        out.put(name, toJsonModel(entry.getValue(), at + "." + name));
      }
      return out;
    }
    throw new RunnerFailure(at + ": " + value.getClass().getSimpleName() + " is outside the JSON data model (VEC-79)");
  }

  private static void requireNoUnpairedSurrogate(String text, String at) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
        i++;
      } else if (Character.isSurrogate(c)) {
        throw new RunnerFailure(at + ": unpaired surrogate (VEC-79)");
      }
    }
  }

  private static String describe(byte[] bytes) {
    String text = new String(bytes, StandardCharsets.UTF_8);
    return text.length() <= 200 ? text + "  [" + Primitives.hex(bytes) + "]" : bytes.length + " bytes";
  }
}
