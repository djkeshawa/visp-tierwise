package io.github.visplabs.tierwise.core.internal.model;

import java.util.Objects;

/**
 * One condition of a rule (BUNDLE-98 to BUNDLE-104).
 *
 * @param fact the fact name
 * @param op {@code eq}, {@code in}, {@code all_in} or {@code any_in}
 * @param value a literal (for {@code eq}), a list of literals (for the other operators), or a setting
 *     reference: the string {@code $} followed by the setting name, standing for the whole operand
 */
public record ConditionDef(String fact, String op, Object value) {

  /** The prefix of a setting reference (BUNDLE-104). */
  public static final String SETTING_PREFIX = "$";

  /** Validates the components and copies the value deeply. */
  public ConditionDef {
    Objects.requireNonNull(fact, "fact");
    Objects.requireNonNull(op, "op");
    value = Plain.copy(value);
  }

  /**
   * Tells whether the operand is a setting reference.
   *
   * @return {@code true} when the whole operand is {@code $} followed by a setting name
   */
  public boolean isSettingReference() {
    return value instanceof String text && text.startsWith(SETTING_PREFIX);
  }

  /**
   * Returns the name of the referenced setting.
   *
   * @return the setting name without the {@code $}
   * @throws IllegalStateException if the operand is not a setting reference
   */
  public String settingName() {
    if (!isSettingReference()) {
      throw new IllegalStateException("the operand is a literal, not a reference");
    }
    return ((String) value).substring(SETTING_PREFIX.length());
  }
}
