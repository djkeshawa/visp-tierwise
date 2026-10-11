package io.github.visplabs.tierwise.core.internal.model;

import java.util.List;
import java.util.Map;

/**
 * The {@code returns} declaration of a feature (BUNDLE-82): either an enum of effect names, which may
 * be nullable, or an object with typed fields.
 *
 * @param enumValues the effect names of the enum form, in code point order; empty for the object form
 * @param nullable {@code true} when the enum form is nullable (FEAT-9); always {@code false} for the
 *     object form
 * @param fields the field declarations of the object form; empty for the enum form
 * @param objectForm {@code true} for the object form
 */
public record Returns(List<String> enumValues, boolean nullable, Map<String, TypeDecl> fields, boolean objectForm) {

  /** Validates the components and makes the collections unmodifiable. */
  public Returns {
    enumValues = List.copyOf(enumValues);
    fields = Plain.orderedMap(fields);
    if (objectForm && (nullable || !enumValues.isEmpty())) {
      throw new IllegalArgumentException("the object form has neither enum values nor nullable");
    }
    if (!objectForm && !fields.isEmpty()) {
      throw new IllegalArgumentException("the enum form has no fields");
    }
  }

  /**
   * Creates the enum form.
   *
   * @param values the effect names
   * @param nullable whether {@code null} is also an effect
   * @return the declaration
   */
  public static Returns enumeration(List<String> values, boolean nullable) {
    return new Returns(values, nullable, Map.of(), false);
  }

  /**
   * Creates the object form.
   *
   * @param fields the field declarations by name
   * @return the declaration
   */
  public static Returns object(Map<String, TypeDecl> fields) {
    return new Returns(List.of(), false, fields, true);
  }
}
