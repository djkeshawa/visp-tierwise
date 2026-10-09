package io.github.tierwise.core.internal.model;

import java.util.List;
import java.util.Map;

/**
 * A policy of a feature (BUNDLE-83 to BUNDLE-93).
 *
 * @param settings settings by name; may be empty
 * @param defaultEffect the effect returned when no rule matches; {@code null} is the null effect of a
 *     nullable enum (BUNDLE-24)
 * @param requiredFacts the distinct fact names in the conditions of all rules, in code point order
 *     (BUNDLE-91, BUNDLE-92)
 * @param rules the rules in source order
 */
public record PolicyDef(
    Map<String, SettingDef> settings, Object defaultEffect, List<String> requiredFacts, List<RuleDef> rules) {

  /** Copies the effect deeply and makes the collections unmodifiable. */
  public PolicyDef {
    settings = Plain.orderedMap(settings);
    defaultEffect = Plain.copyEffect(defaultEffect);
    requiredFacts = List.copyOf(requiredFacts);
    rules = List.copyOf(rules);
  }
}
