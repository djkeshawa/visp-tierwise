package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.model.Plain;
import io.github.tierwise.core.internal.model.TypeDecl;
import io.github.tierwise.core.internal.project.Names;
import io.github.tierwise.core.internal.yaml.OutOfRangeInteger;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import io.github.tierwise.core.internal.yaml.PScalar;
import io.github.tierwise.core.internal.yaml.PSeq;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks a source value against a {@link TypeDecl} and returns the normalised plain value (specification
 * section 4, TYPE-12 to TYPE-27, TYPE-28 to TYPE-35, TYPE-44 to TYPE-52, TYPE-55 to TYPE-67, TYPE-70,
 * TYPE-71, TYPE-76, NORM-1, NORM-8, NORM-13, NORM-14, NORM-17).
 *
 * <p>The result uses the plain values of the model: {@code Boolean}, {@code Long}, {@code String},
 * {@code List<Object>} (a {@code set} in the declaration order of its universe, TYPE-50) and
 * {@code Map<String, Object>}. Strings are NFC-normalised (NORM-1) and decimals canonical (NORM-13).
 * The result is empty when the value, or a part of it, has a fault. Every fault is reported with its own
 * finding (TYPE-23), at the node that has it: a list gives one finding for each invalid element, and an
 * object with an unknown field and a missing required field gives two.
 *
 * <p>The code is the first that applies (TYPE-71): the owning clause's, when {@link ValueRules} names
 * one; otherwise {@code E_INTEGER_RANGE}, {@code E_OUT_OF_RANGE}, {@code E_NOT_IN_UNIVERSE} or
 * {@code E_DUPLICATE_VALUE} for the failures that section 4 names; otherwise {@code E_TYPE_MISMATCH}.
 * An integer outside both the engine range and its declared bounds gets two findings (TYPE-79). No value
 * is coerced to another type (NORM-17), and {@code null} is never a value (TYPE-76).
 */
public final class ValueValidator {

  private static final BigInteger MAX_SAFE = BigInteger.valueOf(TypeDeclParser.MAX_SAFE);

  private final Findings findings;

  /**
   * Creates a validator.
   *
   * @param findings the accumulator that receives the faults
   */
  public ValueValidator(Findings findings) {
    this.findings = findings;
  }

  /**
   * Validates a value with the default rules: section 4 decides the codes, and objects must be complete.
   *
   * @param type the declared type
   * @param value the source node
   * @return the normalised plain value, or empty when it has a fault (which is reported)
   */
  public Optional<Object> validate(TypeDecl type, PNode value) {
    return validate(type, value, ValueRules.DEFAULT);
  }

  /**
   * Validates a value.
   *
   * @param type the declared type
   * @param value the source node
   * @param rules the code of the owning clause and whether objects may be partial
   * @return the normalised plain value, or empty when it has a fault (which is reported)
   */
  public Optional<Object> validate(TypeDecl type, PNode value, ValueRules rules) {
    return check(type, value, rules).map(Plain::copy);
  }

  private Optional<Object> check(TypeDecl type, PNode node, ValueRules rules) {
    return switch (type.type()) {
      case "boolean" -> bool(node, rules);
      case "integer" -> integer(type, node, rules);
      case "decimal" -> decimal(node, rules);
      case "string" -> string(type, node, rules);
      case "enum" -> enumeration(type, node, rules);
      case "set" -> set(type, node, rules);
      case "list" -> list(type, node, rules);
      case "object" -> object(type, node, rules);
      default -> throw new IllegalArgumentException("unknown type " + type.type());
    };
  }

  private Optional<Object> fail(String defaultCode, ValueRules rules, PNode node, String message) {
    findings.error(rules.ownerCode().orElse(defaultCode), node, message);
    return Optional.empty();
  }

  private Optional<Object> mismatch(PNode node, ValueRules rules, String expected) {
    return fail(Code.E_TYPE_MISMATCH, rules, node, "the value is not " + expected + " (TYPE-70)");
  }

  private static Optional<String> stringOf(PNode node) {
    return Names.text(node).filter(text -> !ValueText.hasUnpairedSurrogate(text)).map(Names::nfc);
  }

  // ---- scalars -----------------------------------------------------------------------------------

  private Optional<Object> bool(PNode node, ValueRules rules) {
    if (node instanceof PScalar scalar && scalar.kind() == PScalar.Kind.BOOLEAN) {
      return Optional.of(scalar.value());
    }
    return mismatch(node, rules, "a boolean (TYPE-12)");
  }

  private Optional<Object> integer(TypeDecl type, PNode node, ValueRules rules) {
    if (!(node instanceof PScalar scalar) || scalar.kind() != PScalar.Kind.INTEGER) {
      return mismatch(node, rules, "an integer (TYPE-15)");
    }
    Object value = scalar.value();
    BigInteger exact = value instanceof Long number ? BigInteger.valueOf(number)
        : value instanceof BigInteger big ? big : null;
    boolean negative = exact != null ? exact.signum() < 0 : scalar.text().startsWith("-");
    boolean failed = false;
    if (value == OutOfRangeInteger.INSTANCE || exact != null && exact.abs().compareTo(MAX_SAFE) > 0) {
      fail(Code.E_INTEGER_RANGE, rules, node, "an integer lies outside plus or minus 2^53 - 1 (TYPE-18, TYPE-20)");
      failed = true;
    }
    boolean below = type.min().isPresent() && (exact != null ? exact.compareTo(BigInteger.valueOf(type.min().get())) < 0 : negative);
    boolean above = type.max().isPresent() && (exact != null ? exact.compareTo(BigInteger.valueOf(type.max().get())) > 0 : !negative);
    if (below || above) {
      fail(Code.E_OUT_OF_RANGE, rules, node, "an integer lies outside its declared bounds (TYPE-19, TYPE-21)");
      failed = true;
    }
    return failed ? Optional.empty() : Optional.of(exact.longValueExact());
  }

  private Optional<Object> decimal(PNode node, ValueRules rules) {
    Optional<String> text = node instanceof PScalar scalar && scalar.kind() == PScalar.Kind.STRING
        ? Optional.of((String) scalar.value()) : Optional.empty();
    if (text.isEmpty() || !ValueText.isDecimal(text.get())) {
      return mismatch(node, rules, "a decimal string; quote a number (TYPE-24, TYPE-25, NORM-14)");
    }
    return Optional.of(ValueText.canonicalDecimal(text.get()));
  }

  private Optional<Object> string(TypeDecl type, PNode node, ValueRules rules) {
    Optional<String> text = stringOf(node);
    if (text.isEmpty()) {
      return mismatch(node, rules, "a string (TYPE-28, NORM-8)");
    }
    String format = type.format().orElse(null);
    if ("time_hhmm".equals(format) && !ValueText.isTimeHhmm(text.get())) {
      return mismatch(node, rules, "a time of the form HH:MM (TYPE-33)");
    }
    if ("iana_tz".equals(format) && !TimeZoneNames.contains(text.get())) {
      return mismatch(node, rules, "a zone or link name of the pinned tz database (TYPE-34, TYPE-35)");
    }
    return Optional.of(text.get());
  }

  private Optional<Object> enumeration(TypeDecl type, PNode node, ValueRules rules) {
    Optional<String> text = stringOf(node);
    if (text.isEmpty() || !type.values().contains(text.get())) {
      return mismatch(node, rules, "one of the enum values (TYPE-44)");
    }
    return Optional.of(text.get());
  }

  // ---- collections -------------------------------------------------------------------------------

  private Optional<Object> set(TypeDecl type, PNode node, ValueRules rules) {
    if (!(node instanceof PSeq list)) {
      return mismatch(node, rules, "a list of strings (TYPE-45)");
    }
    boolean ok = true;
    Set<String> members = new HashSet<>();
    for (PNode item : list.items()) {
      Optional<String> text = stringOf(item);
      if (text.isEmpty()) {
        mismatch(item, rules, "a string (TYPE-45)");
        ok = false;
      } else if (!members.add(text.get())) {
        fail(Code.E_DUPLICATE_VALUE, rules, item, "a set member is repeated (TYPE-46, TYPE-52)");
        ok = false;
      } else if (!type.universe().contains(text.get())) {
        fail(Code.E_NOT_IN_UNIVERSE, rules, item, "a set member is not in the universe (TYPE-47, TYPE-48)");
        ok = false;
      }
    }
    if (!ok) {
      return Optional.empty();
    }
    List<Object> ordered = new ArrayList<>();
    for (String member : type.universe()) {
      if (members.contains(member)) {
        ordered.add(member);
      }
    }
    return Optional.of(ordered);
  }

  private Optional<Object> list(TypeDecl type, PNode node, ValueRules rules) {
    if (!(node instanceof PSeq sequence)) {
      return mismatch(node, rules, "a list (TYPE-55)");
    }
    TypeDecl items = type.items().orElseThrow();
    boolean ok = true;
    List<Object> elements = new ArrayList<>();
    for (PNode item : sequence.items()) {
      Optional<Object> element = check(items, item, rules);
      if (element.isPresent()) {
        elements.add(element.get());
      } else {
        ok = false;
      }
    }
    return ok ? Optional.of(elements) : Optional.empty();
  }

  private Optional<Object> object(TypeDecl type, PNode node, ValueRules rules) {
    if (!(node instanceof PMap map)) {
      return mismatch(node, rules, "an object (TYPE-61)");
    }
    boolean ok = true;
    Map<String, PNode> present = new LinkedHashMap<>();
    for (Map.Entry<String, PNode> entry : map.entries().entrySet()) {
      String name = Names.nfc(entry.getKey());
      if (type.fields().containsKey(name)) {
        present.put(name, entry.getValue());
      } else {
        fail(Code.E_TYPE_MISMATCH, rules, entry.getValue(),
            "the object has the field " + name + ", which is not declared (TYPE-64)");
        ok = false;
      }
    }
    Map<String, Object> value = new LinkedHashMap<>();
    for (Map.Entry<String, TypeDecl> field : type.fields().entrySet()) {
      PNode fieldNode = present.get(field.getKey());
      if (fieldNode == null) {
        if (!field.getValue().optional() && !rules.partialObjects()) {
          fail(Code.E_TYPE_MISMATCH, rules, map, "the object lacks the required field " + field.getKey() + " (TYPE-62, TYPE-63)");
          ok = false;
        }
        continue;
      }
      Optional<Object> checked = check(field.getValue(), fieldNode, rules);
      if (checked.isPresent()) {
        value.put(field.getKey(), checked.get());
      } else {
        ok = false;
      }
    }
    return ok ? Optional.of(value) : Optional.empty();
  }
}
