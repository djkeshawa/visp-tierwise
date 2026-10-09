package io.github.tierwise.core.internal.bundle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Typed accessors for the plain values of a bundle. Each accessor raises an integrity fault when the value
 * has another form, so that callers read a value without repeating the checks.
 */
final class Members {

  private Members() {
  }

  /**
   * Returns a JSON object.
   *
   * @param value the value
   * @param at the location of the value, for the message
   * @return the members, in document order
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> object(Object value, String at) {
    if (!(value instanceof Map<?, ?> map)) {
      throw BundleFault.integrity(at + " must be an object");
    }
    return (Map<String, Object>) map;
  }

  /**
   * Returns a JSON array.
   *
   * @param value the value
   * @param at the location of the value
   * @return the elements, in document order
   */
  @SuppressWarnings("unchecked")
  static List<Object> array(Object value, String at) {
    if (!(value instanceof List<?> list)) {
      throw BundleFault.integrity(at + " must be an array");
    }
    return (List<Object>) list;
  }

  /**
   * Returns a JSON array whose elements are all strings.
   *
   * @param value the value
   * @param at the location of the value
   * @return the strings, in document order
   */
  static List<String> stringArray(Object value, String at) {
    List<String> out = new ArrayList<>();
    for (Object element : array(value, at)) {
      out.add(string(element, at + "[" + out.size() + "]"));
    }
    return out;
  }

  /**
   * Returns a JSON string.
   *
   * @param value the value
   * @param at the location of the value
   * @return the string
   */
  static String string(Object value, String at) {
    if (!(value instanceof String text)) {
      throw BundleFault.integrity(at + " must be a string");
    }
    return text;
  }

  /**
   * Returns a JSON integer.
   *
   * @param value the value
   * @param at the location of the value
   * @return the integer
   */
  static long integer(Object value, String at) {
    if (!(value instanceof Long number)) {
      throw BundleFault.integrity(at + " must be an integer");
    }
    return number;
  }

  /**
   * Requires that an object has exactly the given members: every required one, and no member that is neither
   * required nor optional (BUNDLE-14, BUNDLE-53, BUNDLE-76 and the other closed member sets).
   *
   * @param map the object
   * @param at the location of the object
   * @param required the members that must be present
   * @param optional the members that may be present
   */
  static void members(Map<String, Object> map, String at, Set<String> required, Set<String> optional) {
    for (String name : map.keySet()) {
      if (!required.contains(name) && !optional.contains(name)) {
        throw BundleFault.integrity(at + " has the member " + name + ", which the format does not allow");
      }
    }
    for (String name : required) {
      if (!map.containsKey(name)) {
        throw BundleFault.integrity(at + " lacks the required member " + name);
      }
    }
  }

  /**
   * Requires that a value is one of the given strings.
   *
   * @param value the value
   * @param allowed the allowed spellings
   * @param at the location of the value
   * @return the string
   */
  static String oneOf(Object value, Set<String> allowed, String at) {
    String text = string(value, at);
    if (!allowed.contains(text)) {
      throw BundleFault.integrity(at + " has the value " + text + ", which is not allowed");
    }
    return text;
  }
}
