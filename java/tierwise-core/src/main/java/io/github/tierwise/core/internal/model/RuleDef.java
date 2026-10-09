package io.github.tierwise.core.internal.model;

import java.util.List;
import java.util.Objects;

/**
 * A rule of a policy (BUNDLE-93 to BUNDLE-102).
 *
 * @param id the rule ID, unique within its policy
 * @param name the rule's name, which evaluation returns as part of the reason (BUNDLE-95)
 * @param when the conditions, one for each fact the rule names, sorted by fact in code point order; empty
 *     for a rule that always holds
 * @param then the effect of the rule; {@code null} is the null effect of a nullable enum
 */
public record RuleDef(String id, String name, List<ConditionDef> when, Object then) {

  /** Validates the components, copies the effect deeply and makes the conditions unmodifiable. */
  public RuleDef {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(name, "name");
    when = List.copyOf(when);
    then = Plain.copyEffect(then);
  }
}
