package io.github.visplabs.tierwise.core.internal.eval;

import io.github.visplabs.tierwise.core.internal.model.ConditionDef;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.model.RuleDef;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Condition and rule evaluation (RULE-7, RULE-8, RULE-29 to RULE-39, RULE-53 to RULE-55).
 *
 * <p>This runs only after the missing-fact precheck has passed, so every fact that a condition names is
 * present (RULE-45). A condition then yields true or false and raises no error (RULE-39). Values are
 * compared as received and never coerced (RULE-38). A setting reference takes the resolved value of its
 * setting (RULE-27).
 */
final class RuleMatcher {

  /**
   * The first condition of a rule that does not hold.
   *
   * @param fact the fact of the condition
   * @param op the operator of the condition
   */
  record FailedCondition(String fact, String op) {
  }

  private final Model model;

  /**
   * Creates a matcher.
   *
   * @param model the model of the bundle
   */
  RuleMatcher(Model model) {
    this.model = model;
  }

  /**
   * Checks the conditions of a rule in ascending code-point order of fact name (RULE-8).
   *
   * @param rule the rule
   * @param facts the validated facts
   * @param settings the resolved settings of the selected policy
   * @return the first condition that does not hold, or empty when the rule matches (RULE-7, RULE-54)
   */
  Optional<FailedCondition> firstFailure(RuleDef rule, ValidatedFacts facts, Map<String, Object> settings) {
    for (ConditionDef condition : inCodePointOrder(rule.when())) {
      if (!holds(condition, facts, settings)) {
        return Optional.of(new FailedCondition(condition.fact(), condition.op()));
      }
    }
    return Optional.empty();
  }

  private static List<ConditionDef> inCodePointOrder(List<ConditionDef> conditions) {
    boolean sorted = true;
    for (int i = 1; sorted && i < conditions.size(); i++) {
      sorted = CodePoints.compare(conditions.get(i - 1).fact(), conditions.get(i).fact()) <= 0;
    }
    if (sorted) {
      return conditions;
    }
    List<ConditionDef> copy = new ArrayList<>(conditions);
    copy.sort((a, b) -> CodePoints.compare(a.fact(), b.fact()));
    return copy;
  }

  private boolean holds(ConditionDef condition, ValidatedFacts facts, Map<String, Object> settings) {
    Object operand = condition.isSettingReference() ? settings.get(condition.settingName()) : condition.value();
    switch (condition.op()) {
      case "eq":
        return ValueEquality.scalarEquals(facts.facts().get(condition.fact()), operand);
      case "in":
        return isMember(facts.facts().get(condition.fact()), operand);
      case "all_in":
        return quantified(condition.fact(), facts, operand, true);
      case "any_in":
        return quantified(condition.fact(), facts, operand, false);
      default:
        throw new IllegalStateException("unknown operator in the bundle: " + condition.op());
    }
  }

  /**
   * RULE-31 to RULE-33: on a present, empty collection neither form holds; otherwise {@code all_in}
   * needs every child's value in the operand and {@code any_in} needs at least one.
   */
  private boolean quantified(String fact, ValidatedFacts facts, Object operand, boolean every) {
    List<Map<String, Object>> children = facts.children().get(model.facts().get(fact).entity());
    if (children == null || children.isEmpty()) {
      return false;
    }
    for (Map<String, Object> child : children) {
      boolean member = isMember(child.get(fact), operand);
      if (every && !member) {
        return false;
      }
      if (!every && member) {
        return true;
      }
    }
    return every;
  }

  /** RULE-30, RULE-34: equal to an element of the operand list; an empty list holds for no value. */
  private static boolean isMember(Object value, Object operand) {
    if (!(operand instanceof List<?> elements)) {
      return false;
    }
    for (Object element : elements) {
      if (ValueEquality.scalarEquals(value, element)) {
        return true;
      }
    }
    return false;
  }
}
