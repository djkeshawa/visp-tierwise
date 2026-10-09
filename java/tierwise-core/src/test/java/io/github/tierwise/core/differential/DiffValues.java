package io.github.tierwise.core.differential;

import io.github.tierwise.core.internal.model.KeyDef;
import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

/**
 * Random type declarations and valid values for generated models (TYPE-12 to TYPE-70, NORM-9).
 *
 * <p>Every value this class makes passes the value checks of the bundle reader for its declaration. The
 * {@link #wrong(TypeDecl, Random)} values do not, and the generator uses them on purpose to test the error codes of
 * both evaluators.
 */
final class DiffValues {

  /** The shapes that a key or a field can take. Only {@link #SET}, {@link #LIST} and {@link #OBJECT} are compound. */
  enum Shape { BOOLEAN, INTEGER, DECIMAL, STRING, ENUM, SET, LIST, OBJECT }

  private static final Shape[] SCALARS = {Shape.BOOLEAN, Shape.INTEGER, Shape.DECIMAL, Shape.STRING, Shape.ENUM};

  private DiffValues() {
  }

  /**
   * Returns a random scalar type: boolean, integer, decimal, string or enum.
   *
   * @param r the random source
   * @return the declaration
   */
  static TypeDecl scalar(Random r) {
    return type(r, SCALARS[r.nextInt(SCALARS.length)]);
  }

  /**
   * Returns a random declaration of the given shape.
   *
   * @param r the random source
   * @param shape the shape
   * @return the declaration
   */
  static TypeDecl type(Random r, Shape shape) {
    return switch (shape) {
      case BOOLEAN -> TypeDecl.simple("boolean");
      case INTEGER -> integer(r, false);
      case DECIMAL -> TypeDecl.simple("decimal");
      case STRING -> string(r);
      case ENUM -> TypeDecl.enumeration(sortedDistinctText(r, 2 + r.nextInt(3)));
      case SET -> TypeDecl.set(distinctText(r, 2 + r.nextInt(4)));
      case LIST -> TypeDecl.list(scalar(r));
      case OBJECT -> TypeDecl.object(fields(r));
    };
  }

  /**
   * Returns an integer declaration whose minimum is present, so that a {@code deny} on a {@code min} key has its
   * bound (E_DENY_BOUND_REQUIRED).
   *
   * @param r the random source
   * @return the declaration
   */
  static TypeDecl integerWithMinimum(Random r) {
    return integer(r, true);
  }

  /**
   * Returns the fields of an object declaration: one to three fields, the later ones sometimes optional.
   *
   * @param r the random source
   * @return the fields by name
   */
  static Map<String, TypeDecl> fields(Random r) {
    int count = 1 + r.nextInt(3);
    Map<String, TypeDecl> out = new LinkedHashMap<>();
    for (int i = 0; i < count; i++) {
      TypeDecl field = scalar(r);
      out.put(DiffNames.identifier(r) + i, i > 0 && r.nextBoolean() ? field.asOptional() : field);
    }
    return out;
  }

  /**
   * Returns a valid value of a type.
   *
   * @param type the declaration
   * @param r the random source
   * @return a plain value: a boolean, a {@link Long}, a string, a list or a map
   */
  static Object value(TypeDecl type, Random r) {
    return switch (type.type()) {
      case "boolean" -> Boolean.valueOf(r.nextBoolean());
      case "integer" -> integerValue(type, r);
      case "decimal" -> decimalValue(r);
      case "string" -> stringValue(type, r);
      case "enum" -> type.values().get(r.nextInt(type.values().size()));
      case "set" -> setValue(type, r);
      case "list" -> listValue(type, r);
      case "object" -> objectValue(type, r, false);
      default -> throw new IllegalArgumentException("not a type of a generated model: " + type.type());
    };
  }

  /**
   * Returns the value that a key takes at a node: a partial object for a {@code deep_merge} key, otherwise a value of
   * its type.
   *
   * @param key the key
   * @param r the random source
   * @return the value
   */
  static Object valueFor(KeyDef key, Random r) {
    return "deep_merge".equals(key.combine()) ? objectValue(key.type(), r, true) : value(key.type(), r);
  }

  /**
   * Returns a value of the wrong type for a declaration. The declaration does not accept it, so an evaluator must
   * report it with the code of its situation: E_FACTS_INVALID for a fact, or a SOURCE_INVALID entry for a provider.
   *
   * @param type the declaration
   * @param r the random source
   * @return a value that the declaration does not accept
   */
  static Object wrong(TypeDecl type, Random r) {
    return switch (type.type()) {
      case "boolean" -> "true";
      case "integer" -> "7";
      case "decimal", "string" -> Long.valueOf(5L + r.nextInt(3));
      default -> "not a member";
    };
  }

  /**
   * Returns a sorted list of distinct scalar values of a non-boolean type, in bundle order.
   *
   * @param type the declaration
   * @param r the random source
   * @return the list, possibly empty
   */
  static List<Object> sortedDistinct(TypeDecl type, Random r) {
    Set<Object> seen = new LinkedHashSet<>();
    int count = r.nextInt(4);
    for (int i = 0; i < count; i++) {
      seen.add(value(type, r));
    }
    List<Object> out = new ArrayList<>(seen);
    out.sort(DiffValues::compareScalars);
    return out;
  }

  /**
   * Compares two scalars in the bundle order (BUNDLE-75): integers numerically, strings by code point.
   *
   * @param a the first scalar
   * @param b the second scalar
   * @return a negative number, zero or a positive number
   */
  static int compareScalars(Object a, Object b) {
    if (a instanceof Long x && b instanceof Long y) {
      return Long.compare(x, y);
    }
    return DiffNames.compareCodePoints(String.valueOf(a), String.valueOf(b));
  }

  /**
   * Returns an object value with every required field, plus each optional field with probability one half.
   *
   * <p>A partial value has a random subset of the fields and at least one of them.
   *
   * @param object an object declaration
   * @param r the random source
   * @param partial whether the value is partial
   * @return the field values by name
   */
  static Map<String, Object> objectValue(TypeDecl object, Random r, boolean partial) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, TypeDecl> field : object.fields().entrySet()) {
      boolean include = partial ? r.nextBoolean() : !field.getValue().optional() || r.nextBoolean();
      if (include) {
        out.put(field.getKey(), value(field.getValue(), r));
      }
    }
    if (partial && out.isEmpty()) {
      Map.Entry<String, TypeDecl> first = object.fields().entrySet().iterator().next();
      out.put(first.getKey(), value(first.getValue(), r));
    }
    return out;
  }

  private static TypeDecl integer(Random r, boolean requireMinimum) {
    Long min = requireMinimum || r.nextBoolean() ? Long.valueOf(r.nextInt(41) - 20) : null;
    Long max = r.nextBoolean() ? Long.valueOf(min == null ? r.nextInt(201) - 100 : min + r.nextInt(400)) : null;
    return TypeDecl.integer(min, max);
  }

  private static TypeDecl string(Random r) {
    if (r.nextInt(3) == 0) {
      return new TypeDecl("string", Optional.empty(), Optional.empty(),
          Optional.of("time_hhmm"), List.of(), List.of(), Optional.empty(), Map.of(), false);
    }
    return TypeDecl.simple("string");
  }

  private static Object integerValue(TypeDecl type, Random r) {
    long low = type.min().orElse(-1000L);
    long high = type.max().orElse(1000L);
    return Long.valueOf(low + r.nextInt((int) (high - low + 1)));
  }

  /** A canonical decimal string (NORM-9): an optional sign, no leading zeros, no trailing zeros, and never "-0". */
  private static String decimalValue(Random r) {
    long whole = r.nextInt(2001) - 1000;
    StringBuilder out = new StringBuilder();
    if (whole < 0) {
      out.append('-');
    }
    out.append(Math.abs(whole));
    if (r.nextBoolean()) {
      out.append('.');
      int digits = 1 + r.nextInt(3);
      for (int i = 0; i < digits; i++) {
        out.append((char) ('1' + r.nextInt(9)));
      }
    }
    return out.toString();
  }

  private static String stringValue(TypeDecl type, Random r) {
    if (type.format().isPresent()) {
      return pad2(r.nextInt(24)) + ":" + pad2(r.nextInt(60));
    }
    return DiffNames.text(r);
  }

  private static String pad2(int value) {
    return value < 10 ? "0" + value : String.valueOf(value);
  }

  private static List<String> setValue(TypeDecl type, Random r) {
    List<String> out = new ArrayList<>();
    for (String member : type.universe()) {
      if (r.nextBoolean()) {
        out.add(member);
      }
    }
    return out;
  }

  private static List<Object> listValue(TypeDecl type, Random r) {
    List<Object> out = new ArrayList<>();
    int count = r.nextInt(4);
    for (int i = 0; i < count; i++) {
      out.add(value(type.items().orElseThrow(), r));
    }
    return out;
  }

  private static List<String> sortedDistinctText(Random r, int count) {
    List<String> out = new ArrayList<>(distinctText(r, count));
    out.sort(DiffNames.CODE_POINT);
    return out;
  }

  private static List<String> distinctText(Random r, int count) {
    Set<String> seen = new LinkedHashSet<>();
    while (seen.size() < count) {
      seen.add(DiffNames.text(r));
    }
    return new ArrayList<>(seen);
  }
}
