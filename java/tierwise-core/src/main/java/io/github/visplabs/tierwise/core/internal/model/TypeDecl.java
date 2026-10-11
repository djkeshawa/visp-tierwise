package io.github.visplabs.tierwise.core.internal.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A type declaration (specification section 4): the {@code type} name with the type members that it
 * defines (TYPE-7). A member that the type does not define is empty.
 *
 * @param type one of {@code boolean}, {@code integer}, {@code decimal}, {@code string}, {@code enum},
 *     {@code set}, {@code list} or {@code object}
 * @param min the inclusive lower bound of an {@code integer}
 * @param max the inclusive upper bound of an {@code integer}
 * @param format the format of a {@code string} ({@code time_hhmm} or {@code iana_tz})
 * @param values the members of an {@code enum}, in the order the bundle holds them
 * @param universe the members of a {@code set}, in declared order
 * @param items the element declaration of a {@code list}
 * @param fields the field declarations of an {@code object}, by field name
 * @param optional {@code true} for an optional object field; always {@code false} elsewhere (TYPE-8)
 */
public record TypeDecl(
    String type,
    Optional<Long> min,
    Optional<Long> max,
    Optional<String> format,
    List<String> values,
    List<String> universe,
    Optional<TypeDecl> items,
    Map<String, TypeDecl> fields,
    boolean optional) {

  /** Validates the components and makes the collections unmodifiable. */
  public TypeDecl {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(min, "min");
    Objects.requireNonNull(max, "max");
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(items, "items");
    values = List.copyOf(values);
    universe = List.copyOf(universe);
    fields = Plain.orderedMap(fields);
  }

  /**
   * Creates a declaration of a type that has no type members.
   *
   * @param type {@code boolean}, {@code decimal} or {@code string} without a format
   * @return the declaration
   */
  public static TypeDecl simple(String type) {
    return new TypeDecl(type, Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(),
        Optional.empty(), Map.of(), false);
  }

  /**
   * Creates an {@code integer} declaration.
   *
   * @param min the inclusive lower bound, or {@code null}
   * @param max the inclusive upper bound, or {@code null}
   * @return the declaration
   */
  public static TypeDecl integer(Long min, Long max) {
    return new TypeDecl("integer", Optional.ofNullable(min), Optional.ofNullable(max), Optional.empty(),
        List.of(), List.of(), Optional.empty(), Map.of(), false);
  }

  /**
   * Creates an {@code enum} declaration.
   *
   * @param values the enum members
   * @return the declaration
   */
  public static TypeDecl enumeration(List<String> values) {
    return new TypeDecl("enum", Optional.empty(), Optional.empty(), Optional.empty(), values, List.of(),
        Optional.empty(), Map.of(), false);
  }

  /**
   * Creates a {@code set} declaration.
   *
   * @param universe the members of the universe in declared order
   * @return the declaration
   */
  public static TypeDecl set(List<String> universe) {
    return new TypeDecl("set", Optional.empty(), Optional.empty(), Optional.empty(), List.of(), universe,
        Optional.empty(), Map.of(), false);
  }

  /**
   * Creates a {@code list} declaration.
   *
   * @param items the element declaration
   * @return the declaration
   */
  public static TypeDecl list(TypeDecl items) {
    return new TypeDecl("list", Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(),
        Optional.of(items), Map.of(), false);
  }

  /**
   * Creates an {@code object} declaration.
   *
   * @param fields the field declarations by name
   * @return the declaration
   */
  public static TypeDecl object(Map<String, TypeDecl> fields) {
    return new TypeDecl("object", Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(),
        Optional.empty(), fields, false);
  }

  /**
   * Returns a copy of this declaration marked as an optional object field.
   *
   * @return the optional variant
   */
  public TypeDecl asOptional() {
    return new TypeDecl(type, min, max, format, values, universe, items, fields, true);
  }
}
