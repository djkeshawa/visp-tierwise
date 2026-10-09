package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a type declaration of a bundle (specification section 4.1, BUNDLE-52, BUNDLE-82). It enforces the
 * declaration grammar: the type names of TYPE-2, the type members of TYPE-7, the {@code optional} rules of
 * TYPE-8 to TYPE-10, and the list and set rules of TYPE-40 to TYPE-43, TYPE-68 and TYPE-14.
 */
final class TypeReader {

  /** The eight type names of TYPE-2. */
  static final Set<String> TYPE_NAMES = Set.of("boolean", "integer", "decimal", "string", "enum", "set", "list",
      "object");

  private TypeReader() {
  }

  /**
   * Reads one type declaration.
   *
   * @param value the declaration
   * @param at the location of the declaration
   * @param objectField {@code true} for an object field declaration, the only place where {@code optional}
   *     may appear (TYPE-8)
   * @return the declaration
   */
  static TypeDecl read(Object value, String at, boolean objectField) {
    Map<String, Object> members = Members.object(value, at);
    if (!members.containsKey("type")) {
      throw BundleFault.integrity(at + " lacks the member type (TYPE-3)");
    }
    String name = Members.oneOf(members.get("type"), TYPE_NAMES, at + ".type");
    Set<String> allowed = new HashSet<>(typeMembers(name));
    allowed.add("type");
    if (objectField) {
      allowed.add("optional");
    }
    for (String member : members.keySet()) {
      if (!allowed.contains(member)) {
        throw BundleFault.integrity(at + " has the member " + member + ", which its type does not define (TYPE-7, TYPE-8)");
      }
    }
    boolean optional = readOptional(members, at);
    return switch (name) {
      case "integer" -> integer(members, at, optional);
      case "string" -> string(members, at, optional);
      case "enum" -> enumeration(members, at, optional);
      case "set" -> set(members, at, optional);
      case "list" -> list(members, at, optional);
      case "object" -> object(members, at, optional);
      default -> new TypeDecl(name, Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(),
          Optional.empty(), Map.of(), optional);
    };
  }

  private static Set<String> typeMembers(String name) {
    return switch (name) {
      case "integer" -> Set.of("min", "max");
      case "string" -> Set.of("format");
      case "enum" -> Set.of("values");
      case "set" -> Set.of("universe");
      case "list" -> Set.of("items");
      case "object" -> Set.of("fields");
      default -> Set.of();
    };
  }

  private static boolean readOptional(Map<String, Object> members, String at) {
    if (!members.containsKey("optional")) {
      return false;
    }
    if (!(members.get("optional") instanceof Boolean flag)) {
      throw BundleFault.integrity(at + ".optional must be a boolean (TYPE-9)");
    }
    if (!flag) {
      throw BundleFault.integrity(at + ".optional is false, which the bundle omits (BUNDLE-20, BUNDLE-82)");
    }
    return true;
  }

  private static TypeDecl integer(Map<String, Object> members, String at, boolean optional) {
    Optional<Long> min = optionalInteger(members, "min", at);
    Optional<Long> max = optionalInteger(members, "max", at);
    if (min.isPresent() && max.isPresent() && min.get() > max.get()) {
      throw BundleFault.integrity(at + ": min is greater than max (TYPE-14)");
    }
    return new TypeDecl("integer", min, max, Optional.empty(), List.of(), List.of(), Optional.empty(), Map.of(),
        optional);
  }

  private static Optional<Long> optionalInteger(Map<String, Object> members, String name, String at) {
    if (!members.containsKey(name)) {
      return Optional.empty();
    }
    return Optional.of(Members.integer(members.get(name), at + "." + name));
  }

  private static TypeDecl string(Map<String, Object> members, String at, boolean optional) {
    Optional<String> format = Optional.empty();
    if (members.containsKey("format")) {
      format = Optional.of(Members.oneOf(members.get("format"), Set.of("time_hhmm", "iana_tz"), at + ".format"));
    }
    return new TypeDecl("string", Optional.empty(), Optional.empty(), format, List.of(), List.of(),
        Optional.empty(), Map.of(), optional);
  }

  private static TypeDecl enumeration(Map<String, Object> members, String at, boolean optional) {
    List<String> values = distinctNonEmpty(members.get("values"), at + ".values");
    if (!Order.strictlyIncreasing(values)) {
      throw BundleFault.integrity(at + ".values is not in code point order (BUNDLE-75)");
    }
    return new TypeDecl("enum", Optional.empty(), Optional.empty(), Optional.empty(), values, List.of(),
        Optional.empty(), Map.of(), optional);
  }

  private static TypeDecl set(Map<String, Object> members, String at, boolean optional) {
    List<String> universe = distinctNonEmpty(members.get("universe"), at + ".universe");
    return new TypeDecl("set", Optional.empty(), Optional.empty(), Optional.empty(), List.of(), universe,
        Optional.empty(), Map.of(), optional);
  }

  private static TypeDecl list(Map<String, Object> members, String at, boolean optional) {
    if (!members.containsKey("items")) {
      throw BundleFault.integrity(at + " lacks the member items (TYPE-53)");
    }
    TypeDecl items = read(members.get("items"), at + ".items", false);
    return new TypeDecl("list", Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(),
        Optional.of(items), Map.of(), optional);
  }

  private static TypeDecl object(Map<String, Object> members, String at, boolean optional) {
    if (!members.containsKey("fields")) {
      throw BundleFault.integrity(at + " lacks the member fields (TYPE-59)");
    }
    Map<String, Object> raw = Members.object(members.get("fields"), at + ".fields");
    if (raw.isEmpty()) {
      throw BundleFault.integrity(at + ".fields declares no field (TYPE-68)");
    }
    Map<String, TypeDecl> fields = new LinkedHashMap<>();
    for (Map.Entry<String, Object> field : raw.entrySet()) {
      if (field.getKey().isEmpty()) {
        throw BundleFault.integrity(at + ".fields has an empty field name (TYPE-68)");
      }
      fields.put(field.getKey(), read(field.getValue(), at + ".fields." + field.getKey(), true));
    }
    return new TypeDecl("object", Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(),
        Optional.empty(), fields, optional);
  }

  /**
   * Reads a non-empty array of distinct strings (TYPE-41 to TYPE-43, FEAT-8).
   *
   * @param value the array
   * @param at the location of the array
   * @return the strings, in document order
   */
  static List<String> distinctNonEmpty(Object value, String at) {
    List<String> names = Members.stringArray(value, at);
    if (names.isEmpty()) {
      throw BundleFault.integrity(at + " is empty (TYPE-42)");
    }
    if (new HashSet<>(names).size() != names.size()) {
      throw BundleFault.integrity(at + " repeats a string (TYPE-43)");
    }
    return names;
  }
}
