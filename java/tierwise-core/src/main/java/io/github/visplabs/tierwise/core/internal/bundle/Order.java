package io.github.visplabs.tierwise.core.internal.bundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The orders that a bundle uses (DET-5, DET-6, BUNDLE-75, BUNDLE-152). Strings compare by code point, never by
 * UTF-16 code unit, except for the member order of JSON objects (BUNDLE-9), which {@code CanonicalJson} owns.
 */
final class Order {

  /** Compares two strings by Unicode code point (DET-5). */
  static final Comparator<String> CODE_POINT = Order::compareCodePoints;

  private Order() {
  }

  /**
   * Compares two strings code point by code point; a proper prefix sorts first.
   *
   * @param a a string
   * @param b a string
   * @return a negative number, zero or a positive number
   */
  static int compareCodePoints(String a, String b) {
    int i = 0;
    int j = 0;
    while (i < a.length() && j < b.length()) {
      int x = a.codePointAt(i);
      int y = b.codePointAt(j);
      if (x != y) {
        return Integer.compare(x, y);
      }
      i += Character.charCount(x);
      j += Character.charCount(y);
    }
    return Boolean.compare(i < a.length(), j < b.length());
  }

  /**
   * Compares two scalar literals of one type: booleans with {@code false} first, integers numerically, and
   * strings by code point (BUNDLE-75, BUNDLE-152).
   *
   * @param a a boolean, an integer or a string
   * @param b a value of the same type
   * @return a negative number, zero or a positive number
   * @throws IllegalArgumentException if the values are of different or unsupported types
   */
  static int compareScalars(Object a, Object b) {
    if (a instanceof Boolean x && b instanceof Boolean y) {
      return Boolean.compare(x, y);
    }
    if (a instanceof Long x && b instanceof Long y) {
      return Long.compare(x, y);
    }
    if (a instanceof String x && b instanceof String y) {
      return compareCodePoints(x, y);
    }
    throw new IllegalArgumentException("scalars of different types cannot be ordered");
  }

  /**
   * Tells whether the strings are in strictly increasing code point order, which also means distinct.
   *
   * @param names the strings
   * @return {@code true} when each string sorts before the next
   */
  static boolean strictlyIncreasing(List<String> names) {
    for (int i = 1; i < names.size(); i++) {
      if (compareCodePoints(names.get(i - 1), names.get(i)) >= 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns a copy of the strings in code point order.
   *
   * @param names the strings
   * @return the sorted copy
   */
  static List<String> sortedCopy(Collection<String> names) {
    List<String> copy = new ArrayList<>(names);
    copy.sort(CODE_POINT);
    return copy;
  }

  /**
   * Returns a copy of the scalar literals in their bundle order (BUNDLE-75, BUNDLE-152).
   *
   * @param values the literals, all of one type
   * @return the sorted copy
   */
  static List<Object> sortedScalars(Collection<?> values) {
    List<Object> copy = new ArrayList<>(values);
    copy.sort(Order::compareScalars);
    return copy;
  }

  /**
   * Returns a copy of the names in the order of a reference list, such as the scope order (BUNDLE-54).
   *
   * @param names the names; each occurs in {@code reference}
   * @param reference the reference order
   * @return the sorted copy
   */
  static List<String> sortedByReference(Collection<String> names, List<String> reference) {
    List<String> copy = new ArrayList<>(names);
    copy.sort(Comparator.comparingInt(reference::indexOf));
    return copy;
  }
}
