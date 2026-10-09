package io.github.tierwise.cli.codegen;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The declared type of a key, or of a list item or an object field, as the bundle records it
 * (BUNDLE-68 to BUNDLE-73).
 */
sealed interface TypeDecl {

  /** A boolean value. */
  record BooleanType() implements TypeDecl {
  }

  /** An integer value. */
  record IntegerType() implements TypeDecl {
  }

  /** A decimal value, written as a canonical string. */
  record DecimalType() implements TypeDecl {
  }

  /** A string value. */
  record StringType() implements TypeDecl {
  }

  /**
   * An enum value, one of the declared strings.
   *
   * @param values the declared members, in code point order
   */
  record EnumType(List<String> values) implements TypeDecl {

    /** Copies the members. */
    public EnumType {
      values = List.copyOf(values);
    }
  }

  /**
   * A set value: distinct members of the universe.
   *
   * @param universe the declared universe, in declared order
   */
  record SetType(List<String> universe) implements TypeDecl {

    /** Copies the universe. */
    public SetType {
      universe = List.copyOf(universe);
    }
  }

  /**
   * A list value.
   *
   * @param items the type of each element
   */
  record ListType(TypeDecl items) implements TypeDecl {

    /** Validates the components. */
    public ListType {
      Objects.requireNonNull(items, "items");
    }
  }

  /**
   * An object value.
   *
   * @param fields the fields by name, in code point order
   */
  record ObjectType(Map<String, Field> fields) implements TypeDecl {

    /** Copies the fields, keeping their order. {@code Map.copyOf} would not: its order is randomised. */
    public ObjectType {
      fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
    }
  }

  /**
   * One field of an object type.
   *
   * @param type the type of the field
   * @param optional whether the field may be absent
   */
  record Field(TypeDecl type, boolean optional) {

    /** Validates the components. */
    public Field {
      Objects.requireNonNull(type, "type");
    }
  }
}
