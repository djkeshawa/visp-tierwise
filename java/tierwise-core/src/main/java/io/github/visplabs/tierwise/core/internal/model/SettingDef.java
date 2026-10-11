package io.github.visplabs.tierwise.core.internal.model;

import java.util.Objects;

/**
 * A policy setting: exactly one of {@code valueOf} (a single value) and {@code listOf} (a list of
 * distinct values), each naming a fact (BUNDLE-86 to BUNDLE-90).
 *
 * @param kind whether the setting is {@code valueOf} or {@code listOf}
 * @param fact the fact the setting is typed by
 * @param defaultValue the default, a value of the fact's type, or a list of such values for
 *     {@code listOf}; never null
 */
public record SettingDef(Kind kind, String fact, Object defaultValue) {

  /** The two forms of a setting. */
  public enum Kind {
    /** The setting holds one value of its fact's type. */
    VALUE_OF,
    /** The setting holds a list of distinct values of its fact's type. */
    LIST_OF
  }

  /** Validates the components and copies the default deeply. */
  public SettingDef {
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(fact, "fact");
    defaultValue = Plain.copy(defaultValue);
  }
}
