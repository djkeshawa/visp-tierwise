package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.json.CanonicalJson;
import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks a plain value against its type declaration: the value rules of TYPE-12 to TYPE-70 and NORM-9, as a
 * bundle needs them (BUNDLE-131, BUNDLE-134, BUNDLE-135). A failure is an integrity fault.
 */
final class ValueChecker {

  /**
   * Whether an object must have every field that is not optional.
   */
  enum Mode {
    /** Every required field must be present at every depth (TYPE-62). */
    COMPLETE,
    /** Required fields may be missing: a partial object of a {@code deep_merge} key (MERGE-11, MERGE-12). */
    PARTIAL
  }

  /** A canonical decimal string (NORM-9): no leading zeros and no trailing zeros in the fraction. */
  private static final Pattern DECIMAL = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]*[1-9])?");
  private static final Pattern TIME_HHMM = Pattern.compile("([01][0-9]|2[0-3]):[0-5][0-9]");
  private static final Pattern IANA_SYNTAX = Pattern.compile("[A-Za-z0-9_+-]+(/[A-Za-z0-9_+-]+)*");

  private ValueChecker() {
  }

  /**
   * Requires that a value is valid for a type declaration.
   *
   * @param type the declaration
   * @param value the value; {@code null} is never valid (TYPE-76)
   * @param mode whether required fields may be missing
   * @param at the location of the value, for the message
   */
  static void check(TypeDecl type, Object value, Mode mode, String at) {
    if (value == null) {
      throw BundleFault.integrity(at + " is null, which is not a value (TYPE-76)");
    }
    switch (type.type()) {
      case "boolean" -> requireBoolean(value, at);
      case "integer" -> checkInteger(type, value, at);
      case "decimal" -> checkDecimal(value, at);
      case "string" -> checkString(type, value, at);
      case "enum" -> checkEnum(type, value, at);
      case "set" -> checkSet(type, value, at);
      case "list" -> checkList(type, value, mode, at);
      case "object" -> checkObject(type, value, mode, at);
      default -> throw BundleFault.integrity(at + ": unknown type " + type.type());
    }
  }

  private static void requireBoolean(Object value, String at) {
    if (!(value instanceof Boolean)) {
      throw BundleFault.integrity(at + " must be a boolean (TYPE-12)");
    }
  }

  private static void checkInteger(TypeDecl type, Object value, String at) {
    if (!(value instanceof Long number)) {
      throw BundleFault.integrity(at + " must be an integer (TYPE-17)");
    }
    if (number < -CanonicalJson.MAX_SAFE_INTEGER || number > CanonicalJson.MAX_SAFE_INTEGER) {
      throw BundleFault.integrity(at + " is outside +/-(2^53 - 1) (TYPE-18)");
    }
    if (type.min().isPresent() && number < type.min().get()) {
      throw BundleFault.integrity(at + " is below the declared minimum (TYPE-19)");
    }
    if (type.max().isPresent() && number > type.max().get()) {
      throw BundleFault.integrity(at + " is above the declared maximum (TYPE-19)");
    }
  }

  private static void checkDecimal(Object value, String at) {
    if (!(value instanceof String text) || !DECIMAL.matcher(text).matches() || "-0".equals(text)) {
      throw BundleFault.integrity(at + " is not a canonical decimal string (NORM-9, BUNDLE-135)");
    }
  }

  private static void checkString(TypeDecl type, Object value, String at) {
    if (!(value instanceof String text)) {
      throw BundleFault.integrity(at + " must be a string (TYPE-28)");
    }
    if (type.format().isEmpty()) {
      return;
    }
    boolean valid = switch (type.format().get()) {
      case "time_hhmm" -> TIME_HHMM.matcher(text).matches();
      case "iana_tz" -> IANA_SYNTAX.matcher(text).matches();
      default -> false;
    };
    if (!valid) {
      throw BundleFault.integrity(at + " does not satisfy the format " + type.format().get() + " (TYPE-32)");
    }
  }

  private static void checkEnum(TypeDecl type, Object value, String at) {
    if (!(value instanceof String text) || !type.values().contains(text)) {
      throw BundleFault.integrity(at + " is not one of the values of its enum (TYPE-44)");
    }
  }

  private static void checkSet(TypeDecl type, Object value, String at) {
    if (!(value instanceof List<?> members)) {
      throw BundleFault.integrity(at + " must be an array, the value of a set (TYPE-45)");
    }
    Set<Object> seen = new HashSet<>();
    int previous = -1;
    for (Object member : members) {
      if (!(member instanceof String text)) {
        throw BundleFault.integrity(at + " has a member that is not a string (TYPE-45)");
      }
      int position = type.universe().indexOf(text);
      if (position < 0) {
        throw BundleFault.integrity(at + " has a member outside the universe (BUNDLE-134)");
      }
      if (!seen.add(text)) {
        throw BundleFault.integrity(at + " repeats a member (BUNDLE-134)");
      }
      if (position < previous) {
        throw BundleFault.integrity(at + " is not in universe order (BUNDLE-74)");
      }
      previous = position;
    }
  }

  private static void checkList(TypeDecl type, Object value, Mode mode, String at) {
    if (!(value instanceof List<?> items)) {
      throw BundleFault.integrity(at + " must be an array, the value of a list (TYPE-55)");
    }
    TypeDecl itemType = type.items().orElseThrow();
    for (int i = 0; i < items.size(); i++) {
      check(itemType, items.get(i), mode, at + "[" + i + "]");
    }
  }

  private static void checkObject(TypeDecl type, Object value, Mode mode, String at) {
    if (!(value instanceof Map<?, ?> members)) {
      throw BundleFault.integrity(at + " must be an object (TYPE-61)");
    }
    for (Map.Entry<?, ?> member : members.entrySet()) {
      TypeDecl field = type.fields().get(String.valueOf(member.getKey()));
      if (field == null) {
        throw BundleFault.integrity(at + " has the field " + member.getKey() + ", which is not declared (TYPE-64)");
      }
      check(field, member.getValue(), mode, at + "." + member.getKey());
    }
    if (mode == Mode.COMPLETE) {
      for (Map.Entry<String, TypeDecl> field : type.fields().entrySet()) {
        if (!field.getValue().optional() && !members.containsKey(field.getKey())) {
          throw BundleFault.integrity(at + " lacks the required field " + field.getKey() + " (TYPE-62)");
        }
      }
    }
  }
}
