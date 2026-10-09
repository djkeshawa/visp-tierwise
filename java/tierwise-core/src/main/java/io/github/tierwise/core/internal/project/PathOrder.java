package io.github.tierwise.core.internal.project;

import java.util.Comparator;
import java.util.List;

/**
 * Orders finding paths as FIND-26 requires: segment by segment, keys by code point on their decoded text,
 * sequence positions as numbers, and a path that is a prefix of another first. A key sorts before a
 * position when the two meet at one segment, which a well-formed document cannot produce.
 */
public final class PathOrder {

  /** The comparator. */
  public static final Comparator<List<Object>> ORDER = PathOrder::compare;

  private PathOrder() {
  }

  /**
   * Compares two paths.
   *
   * @param a the first path of {@code String} and {@code Integer} segments
   * @param b the second path
   * @return a negative number, zero or a positive number as {@code a} sorts before, with or after {@code b}
   */
  public static int compare(List<Object> a, List<Object> b) {
    int common = Math.min(a.size(), b.size());
    for (int i = 0; i < common; i++) {
      int order = compareSegment(a.get(i), b.get(i));
      if (order != 0) {
        return order;
      }
    }
    return Integer.compare(a.size(), b.size());
  }

  private static int compareSegment(Object x, Object y) {
    if (x instanceof String s && y instanceof String t) {
      return CodePointOrder.compare(s, t);
    }
    if (x instanceof Integer i && y instanceof Integer j) {
      return Integer.compare(i, j);
    }
    return x instanceof String ? -1 : 1;
  }
}
