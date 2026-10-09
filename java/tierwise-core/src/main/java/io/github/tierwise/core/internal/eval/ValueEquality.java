package io.github.tierwise.core.internal.eval;

import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Value equality of NORM-18 and NORM-19. Values never coerce between types: a boolean, an integer and a
 * string are never equal, and a decimal is a string in canonical form.
 */
final class ValueEquality {

  private ValueEquality() {
  }

  /**
   * Compares two scalar values (RULE-35 to RULE-38). The values must already be in normal form, so
   * decimals are compared as canonical strings.
   *
   * @param left a {@code Boolean}, {@code Long} or {@code String}
   * @param right another value
   * @return {@code true} when the two have the same type and an equal value
   */
  static boolean scalarEquals(Object left, Object right) {
    return left != null && right != null && left.getClass() == right.getClass() && left.equals(right);
  }

  /**
   * Compares two values of a declared type (NORM-19): scalars as {@link #scalarEquals}, sets by their
   * elements, lists by length and the elements at each position, and objects by field names and field
   * values, whatever the field order.
   *
   * @param type the declaration both values belong to
   * @param left the first value
   * @param right the second value
   * @return {@code true} when the values are equal
   */
  static boolean equal(TypeDecl type, Object left, Object right) {
    if (left == null || right == null) {
      return left == right;
    }
    switch (type.type()) {
      case "set":
        return left instanceof List<?> a && right instanceof List<?> b
            && a.size() == b.size() && new HashSet<>(a).equals(new HashSet<>(b));
      case "list":
        return equalLists(type, left, right);
      case "object":
        return equalObjects(type, left, right);
      default:
        return scalarEquals(left, right);
    }
  }

  private static boolean equalLists(TypeDecl type, Object left, Object right) {
    if (!(left instanceof List<?> a) || !(right instanceof List<?> b) || a.size() != b.size()
        || type.items().isEmpty()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      if (!equal(type.items().get(), a.get(i), b.get(i))) {
        return false;
      }
    }
    return true;
  }

  private static boolean equalObjects(TypeDecl type, Object left, Object right) {
    if (!(left instanceof Map<?, ?> a) || !(right instanceof Map<?, ?> b) || !a.keySet().equals(b.keySet())) {
      return false;
    }
    for (Map.Entry<?, ?> entry : a.entrySet()) {
      TypeDecl field = type.fields().get(entry.getKey());
      Object other = b.get(entry.getKey());
      boolean same = field == null ? Objects.equals(entry.getValue(), other)
          : equal(field, entry.getValue(), other);
      if (!same) {
        return false;
      }
    }
    return true;
  }
}
