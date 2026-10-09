package io.github.tierwise.core.internal.lint;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.project.SourceDocument;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import io.github.tierwise.core.internal.yaml.PSeq;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code rules/dead-rule} (LINT-26 to LINT-28): a rule whose {@code when} holds a membership test on a
 * literal empty list, or a rule that follows a rule with an empty {@code when} in the same policy. The
 * finding is at the rule item, and a rule gets at most one finding.
 */
final class DeadRuleRule implements LintRule {

  @Override
  public String id() {
    return Code.LINT_RULES_DEAD_RULE;
  }

  @Override
  public List<LintHit> check(LintContext context) {
    List<LintHit> hits = new ArrayList<>();
    for (SourceDocument document : context.sources().rules()) {
      if (!(document.root().get("policies").orElse(null) instanceof PMap policies)) {
        continue;
      }
      for (Map.Entry<String, PNode> policy : policies.entries().entrySet()) {
        if (policy.getValue() instanceof PMap body && body.get("rules").orElse(null) instanceof PSeq rules) {
          checkPolicy(rules, hits);
        }
      }
    }
    return hits;
  }

  private static void checkPolicy(PSeq rules, List<LintHit> hits) {
    boolean afterEmptyWhen = false;
    for (PNode item : rules.items()) {
      if (!(item instanceof PMap rule)) {
        continue;
      }
      PNode when = rule.get("when").orElse(null);
      boolean emptyWhen = when instanceof PMap conditions && conditions.entries().isEmpty();
      if (afterEmptyWhen || hasLiteralEmptyMembership(when)) {
        hits.add(new LintHit(item, "the rule can never run"));
      }
      afterEmptyWhen |= emptyWhen;
    }
  }

  private static boolean hasLiteralEmptyMembership(PNode when) {
    if (!(when instanceof PMap conditions)) {
      return false;
    }
    for (PNode condition : conditions.entries().values()) {
      if (condition instanceof PMap operators) {
        for (Map.Entry<String, PNode> operator : operators.entries().entrySet()) {
          boolean membership = switch (operator.getKey()) {
            case "in", "all_in", "any_in" -> true;
            default -> false;
          };
          if (membership && operator.getValue() instanceof PSeq list && list.items().isEmpty()) {
            return true;
          }
        }
      }
    }
    return false;
  }
}
