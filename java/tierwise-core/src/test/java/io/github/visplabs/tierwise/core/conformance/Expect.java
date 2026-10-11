package io.github.visplabs.tierwise.core.conformance;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The matching rules of section 12.3 (VEC-54 to VEC-64), kept free of any JUnit or core type so that they
 * can be unit tested.
 *
 * <p>{@code expected} is a value as YAML loads it and {@code actual} is a value as a result's
 * {@code toJson()} gives it. Each method returns the list of mismatches; an empty list means a match.
 *
 * <ul>
 *   <li>Unless {@code exact}, a mapping matches when each key it names matches, recursively. Unnamed keys
 *       are not compared (VEC-54).
 *   <li>A key named in {@code expected} that is absent from the actual mapping never matches, even for an
 *       expected {@code null} (VEC-55). An expected {@code null} matches only an actual {@code null}
 *       (VEC-56).
 *   <li>A scalar matches only with the same JSON type and an equal value: booleans only booleans, integers
 *       by value (VEC-57). Strings, decimals included, compare by exact code points (VEC-58, VEC-59).
 *   <li>A sequence matches only with the same length and a match at each position (VEC-61).
 *   <li>When {@code exact}, every mapping has exactly the keys that {@code expected} names (VEC-63).
 * </ul>
 */
final class Expect {

  private Expect() {
  }

  /**
   * Compares an expected value with an actual one.
   *
   * @param expected the expected value from the vector
   * @param actual the actual value from the result
   * @param exact whether the vector sets {@code exact: true}
   * @return the mismatches, each prefixed with its location; empty when the values match
   */
  static List<String> mismatches(Object expected, Object actual, boolean exact) {
    List<String> out = new ArrayList<>();
    compare("$", expected, actual, exact, out);
    return out;
  }

  /**
   * Tells whether the values match.
   *
   * @param expected the expected value from the vector
   * @param actual the actual value from the result
   * @param exact whether the vector sets {@code exact: true}
   * @return {@code true} when there is no mismatch
   */
  static boolean matches(Object expected, Object actual, boolean exact) {
    return mismatches(expected, actual, exact).isEmpty();
  }

  private static void compare(String at, Object expected, Object actual, boolean exact, List<String> out) {
    if (expected == null) {
      if (actual != null) {
        out.add(at + ": expected null, got " + describe(actual));
      }
      return;
    }
    if (expected instanceof Map<?, ?> expectedMap) {
      compareMaps(at, expectedMap, actual, exact, out);
    } else if (expected instanceof List<?> expectedList) {
      compareLists(at, expectedList, actual, exact, out);
    } else if (expected instanceof Boolean) {
      if (!expected.equals(actual)) {
        out.add(at + ": expected " + expected + ", got " + describe(actual));
      }
    } else if (isInteger(expected)) {
      if (!isInteger(actual) || !asBig(expected).equals(asBig(actual))) {
        out.add(at + ": expected " + expected + ", got " + describe(actual));
      }
    } else if (expected instanceof String) {
      if (!expected.equals(actual)) {
        out.add(at + ": expected \"" + expected + "\", got " + describe(actual));
      }
    } else if (expected instanceof Double) {
      if (!expected.equals(actual)) {
        out.add(at + ": expected " + expected + ", got " + describe(actual));
      }
    } else {
      out.add(at + ": unsupported expected value " + expected.getClass().getName());
    }
  }

  private static void compareMaps(String at, Map<?, ?> expected, Object actual, boolean exact, List<String> out) {
    if (!(actual instanceof Map<?, ?> actualMap)) {
      out.add(at + ": expected a mapping, got " + describe(actual));
      return;
    }
    for (Map.Entry<?, ?> entry : expected.entrySet()) {
      Object key = entry.getKey();
      String child = at + "." + key;
      if (!(key instanceof String) || !actualMap.containsKey(key)) {
        out.add(child + ": absent from the result");
        continue;
      }
      compare(child, entry.getValue(), actualMap.get(key), exact, out);
    }
    if (exact) {
      for (Object key : actualMap.keySet()) {
        if (!expected.containsKey(key)) {
          out.add(at + "." + key + ": present in the result but not named by exact expect");
        }
      }
    }
  }

  private static void compareLists(String at, List<?> expected, Object actual, boolean exact, List<String> out) {
    if (!(actual instanceof List<?> actualList)) {
      out.add(at + ": expected a sequence, got " + describe(actual));
      return;
    }
    if (expected.size() != actualList.size()) {
      out.add(at + ": expected " + expected.size() + " item(s), got " + actualList.size());
      return;
    }
    for (int i = 0; i < expected.size(); i++) {
      compare(at + "[" + i + "]", expected.get(i), actualList.get(i), exact, out);
    }
  }

  private static boolean isInteger(Object value) {
    return value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
        || value instanceof BigInteger;
  }

  private static BigInteger asBig(Object value) {
    return value instanceof BigInteger big ? big : BigInteger.valueOf(((Number) value).longValue());
  }

  private static String describe(Object value) {
    if (value == null) {
      return "null";
    }
    String text = value instanceof String ? "\"" + value + "\"" : String.valueOf(value);
    if (text.length() > 120) {
      text = text.substring(0, 117) + "...";
    }
    return text + " (" + value.getClass().getSimpleName() + ")";
  }
}
