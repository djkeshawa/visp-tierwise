package io.github.tierwise.core.internal.eval;

import io.github.tierwise.core.internal.model.TypeDecl;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Validation of values that arrive at runtime: fact values (FACT-35) and provider values (SVC-31).
 *
 * <p>A runtime value is never reported with a code of section 4 (TYPE-73); the caller decides what an
 * invalid value means. A valid value is returned in normal form: integers as {@code Long}, decimals in
 * canonical form (NORM-15), set members in universe order (TYPE-51), and all collections as fresh
 * unmodifiable copies, so a result shares no state with an input (EVAL-47, EVAL-49). Strings are never
 * Unicode-normalised (NORM-3), and {@code iana_tz} values are checked against the syntax only
 * (TYPE-37, TYPE-38). {@code null} is not a valid value of any type (TYPE-76).
 */
final class RuntimeValues {

  /** The largest magnitude of an integer value (TYPE-18). */
  static final long MAX_SAFE_INTEGER = (1L << 53) - 1;

  private RuntimeValues() {
  }

  /**
   * Validates a value against a type declaration; every required field of an object must be present.
   *
   * @param type the declaration
   * @param value the value as received
   * @return the value in normal form, or empty when it is not a valid value of the type
   */
  static Optional<Object> validate(TypeDecl type, Object value) {
    return Optional.ofNullable(check(type, value, false, null));
  }

  /**
   * Validates a value that MERGE-11 allows to be partial: a provider value of a {@code deep_merge} key.
   *
   * <p>Required fields may be missing at every depth (MERGE-12). An optional object field that the
   * starting value does not set must be complete when the value sets it (MERGE-26).
   *
   * @param type the declaration of the key
   * @param value the value as received
   * @param startingValue the key's starting value (MERGE-10): its default, or else its root value
   * @return the value in normal form, or empty when it is not valid
   */
  static Optional<Object> validatePartial(TypeDecl type, Object value, Object startingValue) {
    return Optional.ofNullable(check(type, value, true, startingValue));
  }

  /**
   * Returns the normal form of a value, or {@code null} for an invalid value.
   *
   * @param partial whether required object fields may be missing
   * @param start the starting value at this position; meaningful only when {@code partial}
   */
  private static Object check(TypeDecl type, Object value, boolean partial, Object start) {
    if (value == null) {
      return null;
    }
    switch (type.type()) {
      case "boolean":
        return value instanceof Boolean ? value : null;
      case "integer":
        return checkInteger(type, value);
      case "decimal":
        return value instanceof String text && Decimals.matches(text) ? Decimals.canonical(text) : null;
      case "string":
        return checkString(type, value);
      case "enum":
        return value instanceof String text && type.values().contains(text) ? text : null;
      case "set":
        return checkSet(type, value);
      case "list":
        return checkList(type, value);
      case "object":
        return checkObject(type, value, partial, start);
      default:
        return null;
    }
  }

  private static Object checkInteger(TypeDecl type, Object value) {
    Long integer = asInteger(value);
    if (integer == null || integer > MAX_SAFE_INTEGER || integer < -MAX_SAFE_INTEGER) {
      return null;
    }
    if (type.min().isPresent() && integer < type.min().get()) {
      return null;
    }
    if (type.max().isPresent() && integer > type.max().get()) {
      return null;
    }
    return integer;
  }

  /**
   * Reads a number whose mathematical value is an integer (TYPE-17). A value too large for a
   * {@code long} comes back as a bound that the range check then rejects.
   */
  private static Long asInteger(Object value) {
    if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
      return ((Number) value).longValue();
    }
    if (value instanceof BigInteger big) {
      return big.bitLength() < Long.SIZE ? big.longValue() : (big.signum() > 0 ? Long.MAX_VALUE : Long.MIN_VALUE);
    }
    if (value instanceof BigDecimal decimal) {
      try {
        return asInteger(decimal.toBigIntegerExact());
      } catch (ArithmeticException e) {
        return null;
      }
    }
    if (value instanceof Double || value instanceof Float) {
      double number = ((Number) value).doubleValue();
      if (!Double.isFinite(number) || number != Math.rint(number)) {
        return null;
      }
      return asInteger(new BigDecimal(number));
    }
    return null;
  }

  private static Object checkString(TypeDecl type, Object value) {
    if (!(value instanceof String text) || CodePoints.hasUnpairedSurrogate(text)) {
      return null;
    }
    if (type.format().isPresent()) {
      switch (type.format().get()) {
        case "time_hhmm":
          return isTimeHhmm(text) ? text : null;
        case "iana_tz":
          return isTimeZoneSyntax(text) ? text : null;
        default:
          return null;
      }
    }
    return text;
  }

  /** TYPE-33: {@code ^([01][0-9]|2[0-3]):[0-5][0-9]$} matched in full, with ASCII digits. */
  static boolean isTimeHhmm(String text) {
    if (text.length() != 5 || text.charAt(2) != ':') {
      return false;
    }
    char h1 = text.charAt(0);
    char h2 = text.charAt(1);
    boolean hour = ((h1 == '0' || h1 == '1') && isDigit(h2)) || (h1 == '2' && h2 >= '0' && h2 <= '3');
    char m1 = text.charAt(3);
    return hour && m1 >= '0' && m1 <= '5' && isDigit(text.charAt(4));
  }

  /** TYPE-37: {@code ^[A-Za-z0-9_+-]+(/[A-Za-z0-9_+-]+)*$} matched in full. */
  static boolean isTimeZoneSyntax(String text) {
    boolean segmentHasChar = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '/') {
        if (!segmentHasChar) {
          return false;
        }
        segmentHasChar = false;
      } else if (isDigit(c) || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || c == '_' || c == '+' || c == '-') {
        segmentHasChar = true;
      } else {
        return false;
      }
    }
    return segmentHasChar;
  }

  private static Object checkSet(TypeDecl type, Object value) {
    if (!(value instanceof Collection<?> members)) {
      return null;
    }
    Set<String> seen = new HashSet<>();
    for (Object member : members) {
      if (!(member instanceof String text) || !type.universe().contains(text) || !seen.add(text)) {
        return null;
      }
    }
    List<Object> ordered = new ArrayList<>();
    for (String candidate : type.universe()) {
      if (seen.contains(candidate)) {
        ordered.add(candidate);
      }
    }
    return Collections.unmodifiableList(ordered);
  }

  private static Object checkList(TypeDecl type, Object value) {
    if (!(value instanceof List<?> elements) || type.items().isEmpty()) {
      return null;
    }
    List<Object> checked = new ArrayList<>(elements.size());
    for (Object element : elements) {
      Object normal = check(type.items().get(), element, false, null);
      if (normal == null) {
        return null;
      }
      checked.add(normal);
    }
    return Collections.unmodifiableList(checked);
  }

  private static Object checkObject(TypeDecl type, Object value, boolean partial, Object start) {
    if (!(value instanceof Map<?, ?> members)) {
      return null;
    }
    Map<String, Object> checked = new LinkedHashMap<>();
    for (Map.Entry<?, ?> member : members.entrySet()) {
      if (!(member.getKey() instanceof String name)) {
        return null;
      }
      TypeDecl field = type.fields().get(name);
      if (field == null) {
        return null;
      }
      Object normal = checkField(field, member.getValue(), partial, startOf(start, name));
      if (normal == null) {
        return null;
      }
      checked.put(name, normal);
    }
    if (!partial) {
      for (Map.Entry<String, TypeDecl> field : type.fields().entrySet()) {
        if (!field.getValue().optional() && !checked.containsKey(field.getKey())) {
          return null;
        }
      }
    }
    return Collections.unmodifiableMap(checked);
  }

  /**
   * Checks one field. In a partial value an object field stays partial, except an optional field that
   * the starting value does not set, which must be complete (MERGE-26).
   */
  private static Object checkField(TypeDecl field, Object value, boolean partial, Object fieldStart) {
    if (!partial || !field.type().equals("object")) {
      return check(field, value, false, null);
    }
    boolean startSetsIt = fieldStart instanceof Map<?, ?>;
    if (field.optional() && !startSetsIt) {
      return check(field, value, false, null);
    }
    return check(field, value, true, fieldStart);
  }

  private static Object startOf(Object start, String field) {
    return start instanceof Map<?, ?> map ? map.get(field) : null;
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }
}
