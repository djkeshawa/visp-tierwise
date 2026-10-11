package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import io.github.visplabs.tierwise.core.internal.project.Names;
import io.github.visplabs.tierwise.core.internal.yaml.OutOfRangeInteger;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PScalar;
import io.github.visplabs.tierwise.core.internal.yaml.PSeq;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses a type declaration from a source node into a {@link TypeDecl} (specification section 4,
 * TYPE-1 to TYPE-11, TYPE-13, TYPE-14, TYPE-22, TYPE-31, TYPE-40 to TYPE-43, TYPE-52 to TYPE-54,
 * TYPE-59, TYPE-60, TYPE-68, TYPE-69, NORM-1).
 *
 * <p>The grammar serves key, fact, {@code items} and object field declarations, and the fields of a
 * feature's {@code returns: { object: … }} through {@link #parseFields(PNode)} (TYPE-5). The parser
 * reports every fault of a declaration, nested declarations included, and does not stop at the first
 * (TYPE-23). A declaration with any fault is not built, so a caller can tell a valid declaration from a
 * faulty one by the result alone. Faults are located as the conformance cases require: a missing member
 * at the declaration, an invalid member at that member, an invalid list entry at that entry.
 *
 * <p>The parser knows only the type members. A key or fact declaration also has members of its own,
 * such as {@code combine} or {@code description}, which the stage that owns the declaration checks; the
 * parser leaves them alone. In an {@code items} or object field declaration, every member other than a
 * type member and {@code optional} is malformed (TYPE-11).
 */
public final class TypeDeclParser {

  /** Where a declaration sits, which decides what else it may contain. */
  public enum Position {
    /** A key declaration (KEY-2). The caller checks the members of the key. */
    KEY,
    /** A fact declaration (FACT-6). The caller checks the members of the fact. */
    FACT,
    /** A field of an object declaration, which may carry {@code optional} (TYPE-8). */
    FIELD,
    /** The {@code items} declaration of a list, which carries nothing besides the type members. */
    ITEMS
  }

  /** The largest integer the engine holds, 2^53 - 1 (TYPE-13, TYPE-18). */
  static final long MAX_SAFE = (1L << 53) - 1;

  private static final Set<String> TYPES =
      Set.of("boolean", "integer", "decimal", "string", "enum", "set", "list", "object");
  private static final Set<String> TYPE_MEMBERS =
      Set.of("type", "min", "max", "format", "values", "universe", "items", "fields");
  private static final Set<String> FORMATS = Set.of("time_hhmm", "iana_tz");

  private final Findings findings;

  /**
   * Creates a parser.
   *
   * @param findings the accumulator that receives the faults
   */
  public TypeDeclParser(Findings findings) {
    this.findings = findings;
  }

  /**
   * Parses a type declaration.
   *
   * @param node the declaration node
   * @param position where the declaration sits
   * @return the declaration, or empty when it, or a declaration nested in it, has a fault. Every fault is
   *     reported.
   */
  public Optional<TypeDecl> parse(PNode node, Position position) {
    if (!(node instanceof PMap map)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "a type declaration is a map (TYPE-1, TYPE-11)");
      return Optional.empty();
    }
    Optional<PNode> typeNode = map.get("type");
    if (typeNode.isEmpty()) {
      findings.error(Code.E_TYPE_REQUIRED, map, "a declaration has no type member (TYPE-3, TYPE-4)");
      return Optional.empty();
    }
    Optional<String> typeName = Names.text(typeNode.get()).filter(TYPES::contains);
    boolean ok = checkOtherMembers(map, typeName.orElse(null), position);
    if (typeName.isEmpty()) {
      findings.error(Code.E_INVALID_DECLARATION, typeNode.get(),
          "the type is one of boolean, integer, decimal, string, enum, set, list or object (TYPE-2, TYPE-11)");
      return Optional.empty();
    }
    // The type-specific members are checked even after another fault, so that every fault is reported.
    Optional<TypeDecl> built = build(map, typeName.get(), position);
    return ok ? built : Optional.empty();
  }

  /**
   * Parses the {@code fields} mapping of an object declaration, or the field mapping of a feature's
   * {@code returns: { object: … }} (TYPE-59, TYPE-60, TYPE-68).
   *
   * @param fieldsNode the mapping node, reported at itself when it is not a map or has no field
   * @return the fields by NFC-normalised name, or empty when the mapping or a field has a fault
   */
  public Optional<Map<String, TypeDecl>> parseFields(PNode fieldsNode) {
    if (!(fieldsNode instanceof PMap fields)) {
      findings.error(Code.E_INVALID_DECLARATION, fieldsNode, "fields is a map from field name to declaration (TYPE-59, TYPE-11)");
      return Optional.empty();
    }
    if (fields.entries().isEmpty()) {
      findings.error(Code.E_INVALID_DECLARATION, fields, "an object declares at least one field (TYPE-68)");
      return Optional.empty();
    }
    boolean ok = true;
    Map<String, TypeDecl> parsed = new LinkedHashMap<>();
    for (Map.Entry<String, PNode> field : fields.entries().entrySet()) {
      String name = Names.nfc(field.getKey());
      if (name.isEmpty()) {
        findings.error(Code.E_INVALID_DECLARATION, field.getValue(), "a field name is non-empty (TYPE-68)");
        ok = false;
        continue;
      }
      Optional<TypeDecl> decl = parse(field.getValue(), Position.FIELD);
      if (decl.isPresent()) {
        parsed.put(name, decl.get());
      } else {
        ok = false;
      }
    }
    return ok ? Optional.of(parsed) : Optional.empty();
  }

  /**
   * Parses the list of strings of an {@code enum} {@code values}, a {@code set} {@code universe}, or a
   * feature's {@code returns: { enum: … }} (TYPE-6, TYPE-41 to TYPE-43, TYPE-52, NORM-1).
   *
   * @param listNode the list node
   * @return the strings, NFC-normalised, in declared order; empty when the node is not a list, is
   *     empty, has an entry that is not a string, or repeats a string after normalisation
   */
  public Optional<List<String>> parseStrings(PNode listNode) {
    if (!(listNode instanceof PSeq list)) {
      findings.error(Code.E_INVALID_DECLARATION, listNode, "values and universe are lists of strings (TYPE-41, TYPE-11)");
      return Optional.empty();
    }
    if (list.items().isEmpty()) {
      findings.error(Code.E_INVALID_DECLARATION, listNode, "values and universe are non-empty (TYPE-42)");
      return Optional.empty();
    }
    boolean ok = true;
    List<String> strings = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (PNode item : list.items()) {
      Optional<String> text = Names.text(item);
      if (text.isEmpty() || ValueText.hasUnpairedSurrogate(text.get())) {
        findings.error(Code.E_INVALID_DECLARATION, item, "an entry of values or universe is a string (TYPE-41, NORM-8)");
        ok = false;
        continue;
      }
      String normal = Names.nfc(text.get());
      if (!seen.add(normal)) {
        findings.error(Code.E_DUPLICATE_VALUE, item, "a string is repeated (TYPE-43, TYPE-52)");
        ok = false;
      }
      strings.add(normal);
    }
    return ok ? Optional.of(strings) : Optional.empty();
  }

  // ---- members -----------------------------------------------------------------------------------

  /** Checks every member other than the type-specific ones. Returns false after a fault. */
  private boolean checkOtherMembers(PMap map, String typeName, Position position) {
    boolean ok = true;
    for (Map.Entry<String, PNode> member : map.entries().entrySet()) {
      String name = member.getKey();
      PNode node = member.getValue();
      if (name.equals("type")) {
        continue;
      }
      if (name.equals("optional")) {
        ok &= checkOptional(node, position);
      } else if (TYPE_MEMBERS.contains(name)) {
        if (typeName != null && !definedBy(typeName, name)) {
          findings.error(Code.E_INVALID_DECLARATION, node,
              "the type " + typeName + " has no member " + name + " (TYPE-7, TYPE-11)");
          ok = false;
        }
      } else if (position == Position.FIELD || position == Position.ITEMS) {
        findings.error(Code.E_INVALID_DECLARATION, node,
            "a field or items declaration has no member " + name + " (TYPE-11)");
        ok = false;
      }
    }
    return ok;
  }

  private boolean checkOptional(PNode node, Position position) {
    if (position != Position.FIELD) {
      findings.error(Code.E_INVALID_DECLARATION, node, "optional is allowed only in an object field declaration (TYPE-8)");
      return false;
    }
    if (!(node instanceof PScalar scalar) || scalar.kind() != PScalar.Kind.BOOLEAN) {
      findings.error(Code.E_INVALID_DECLARATION, node, "optional is a boolean (TYPE-9)");
      return false;
    }
    return true;
  }

  private static boolean definedBy(String type, String member) {
    return switch (type) {
      case "integer" -> member.equals("min") || member.equals("max");
      case "string" -> member.equals("format");
      case "enum" -> member.equals("values");
      case "set" -> member.equals("universe");
      case "list" -> member.equals("items");
      case "object" -> member.equals("fields");
      default -> false;
    };
  }

  // ---- building ----------------------------------------------------------------------------------

  private Optional<TypeDecl> build(PMap map, String type, Position position) {
    boolean optional = position == Position.FIELD && isTrue(map.get("optional").orElse(null));
    return switch (type) {
      case "integer" -> integer(map, optional);
      case "string" -> string(map, optional);
      case "enum" -> strings(map, "values").map(values -> withOptional(TypeDecl.enumeration(values), optional));
      case "set" -> strings(map, "universe").map(universe -> withOptional(TypeDecl.set(universe), optional));
      case "list" -> list(map, optional);
      case "object" -> object(map, optional);
      default -> Optional.of(withOptional(TypeDecl.simple(type), optional));
    };
  }

  private static boolean isTrue(PNode node) {
    return node instanceof PScalar scalar && scalar.kind() == PScalar.Kind.BOOLEAN && Boolean.TRUE.equals(scalar.value());
  }

  private static TypeDecl withOptional(TypeDecl decl, boolean optional) {
    return optional ? decl.asOptional() : decl;
  }

  private Optional<TypeDecl> integer(PMap map, boolean optional) {
    Optional<Long> min = Optional.empty();
    Optional<Long> max = Optional.empty();
    boolean ok = true;
    if (map.has("min")) {
      Optional<Long> bound = bound(map.get("min").orElseThrow());
      ok &= bound.isPresent();
      min = bound;
    }
    if (map.has("max")) {
      Optional<Long> bound = bound(map.get("max").orElseThrow());
      ok &= bound.isPresent();
      max = bound;
    }
    if (ok && min.isPresent() && max.isPresent() && min.get() > max.get()) {
      findings.error(Code.E_INVALID_DECLARATION, map, "min is greater than max (TYPE-14)");
      ok = false;
    }
    if (!ok) {
      return Optional.empty();
    }
    return Optional.of(withOptional(TypeDecl.integer(min.orElse(null), max.orElse(null)), optional));
  }

  /** Reads a bound: a source integer within the engine range (TYPE-13, TYPE-15, TYPE-22). */
  private Optional<Long> bound(PNode node) {
    if (!(node instanceof PScalar scalar) || scalar.kind() != PScalar.Kind.INTEGER) {
      findings.error(Code.E_INVALID_DECLARATION, node, "min and max are integers (TYPE-13, TYPE-15, TYPE-11)");
      return Optional.empty();
    }
    Object value = scalar.value();
    if (value instanceof Long number && number >= -MAX_SAFE && number <= MAX_SAFE) {
      return Optional.of(number);
    }
    if (value instanceof Long || value instanceof java.math.BigInteger || value == OutOfRangeInteger.INSTANCE) {
      findings.error(Code.E_INTEGER_RANGE, node, "a bound lies outside plus or minus 2^53 - 1 (TYPE-22)");
    }
    return Optional.empty();
  }

  private Optional<TypeDecl> string(PMap map, boolean optional) {
    if (!map.has("format")) {
      return Optional.of(withOptional(TypeDecl.simple("string"), optional));
    }
    PNode node = map.get("format").orElseThrow();
    Optional<String> format = Names.text(node).filter(FORMATS::contains);
    if (format.isEmpty()) {
      findings.error(Code.E_INVALID_DECLARATION, node, "format is time_hhmm or iana_tz (TYPE-31, TYPE-11)");
      return Optional.empty();
    }
    TypeDecl decl = new TypeDecl("string", Optional.empty(), Optional.empty(), format, List.of(), List.of(),
        Optional.empty(), Map.of(), optional);
    return Optional.of(decl);
  }

  private Optional<List<String>> strings(PMap map, String member) {
    Optional<PNode> node = map.get(member);
    if (node.isEmpty()) {
      findings.error(Code.E_INVALID_DECLARATION, map, "the declaration has no " + member + " member (TYPE-40, TYPE-11)");
      return Optional.empty();
    }
    return parseStrings(node.get());
  }

  private Optional<TypeDecl> list(PMap map, boolean optional) {
    Optional<PNode> items = map.get("items");
    if (items.isEmpty()) {
      findings.error(Code.E_INVALID_DECLARATION, map, "a list declaration has no items member (TYPE-53, TYPE-11)");
      return Optional.empty();
    }
    return parse(items.get(), Position.ITEMS).map(element -> withOptional(TypeDecl.list(element), optional));
  }

  private Optional<TypeDecl> object(PMap map, boolean optional) {
    Optional<PNode> fields = map.get("fields");
    if (fields.isEmpty()) {
      findings.error(Code.E_INVALID_DECLARATION, map, "an object declaration has no fields member (TYPE-59, TYPE-11)");
      return Optional.empty();
    }
    return parseFields(fields.get()).map(declared -> withOptional(TypeDecl.object(declared), optional));
  }
}
