package io.github.tierwise.core.internal.check;

import java.util.Objects;
import java.util.Optional;

/**
 * How {@link ValueValidator} reports and what it accepts for one value.
 *
 * @param ownerCode the code that the clause owning the value names for an invalid value, which replaces
 *     every code of section 4 (TYPE-71 (1)), such as {@code E_INVALID_SETTING} for a setting value; empty
 *     when section 4 decides the code
 * @param partialObjects {@code true} when an object value may lack required fields, at any depth, as
 *     MERGE-11 and MERGE-12 permit for a partial value of a {@code deep_merge} key (TYPE-62)
 */
public record ValueRules(Optional<String> ownerCode, boolean partialObjects) {

  /** Section 4 decides the codes, and objects must be complete. */
  public static final ValueRules DEFAULT = new ValueRules(Optional.empty(), false);

  /** Validates the components. */
  public ValueRules {
    Objects.requireNonNull(ownerCode, "ownerCode");
  }

  /**
   * Returns the rules with an owning-clause code.
   *
   * @param code the code of the owning clause
   * @return the rules
   */
  public ValueRules withOwnerCode(String code) {
    return new ValueRules(Optional.of(code), partialObjects);
  }

  /**
   * Returns the rules that allow partial objects.
   *
   * @return the rules
   */
  public ValueRules partial() {
    return new ValueRules(ownerCode, true);
  }
}
