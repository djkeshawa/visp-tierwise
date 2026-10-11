package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.ConditionDef;
import io.github.visplabs.tierwise.core.internal.model.Returns;
import io.github.visplabs.tierwise.core.internal.model.RuleDef;
import io.github.visplabs.tierwise.core.internal.project.CodePointOrder;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PSeq;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses the {@code rules} of a policy (POL-8, POL-21 to POL-27, RULE-1, RULE-2).
 *
 * <p>The conditions of a rule are sorted by fact in code point order, whatever order the source gives
 * them (RULE-8, BUNDLE-96). Source order of rules is kept (BUNDLE-93).
 */
final class RuleParser {

  private static final Set<String> MEMBERS = Set.of("id", "name", "when", "then");

  private final Findings findings;
  private final Optional<Returns> returns;
  private final ConditionParser conditions;
  private final FeatureEffects effects;

  /**
   * Creates the parser.
   *
   * @param findings the accumulator that receives the findings
   * @param returns the feature's effect form, when it is valid
   * @param conditions the parser of the conditions of the policy
   */
  RuleParser(Findings findings, Optional<Returns> returns, ConditionParser conditions) {
    this.findings = findings;
    this.returns = returns;
    this.conditions = conditions;
    this.effects = new FeatureEffects(findings);
  }

  /**
   * Parses a {@code rules} list.
   *
   * @param node the list node
   * @return the rules in source order, or empty when the list or a rule has a fault (which is reported)
   */
  Optional<List<RuleDef>> parse(PNode node) {
    if (!(node instanceof PSeq list)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "rules is a list of rules (POL-8)");
      return Optional.empty();
    }
    boolean ok = true;
    List<RuleDef> rules = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (PNode item : list.items()) {
      Optional<RuleDef> rule = rule(item, ids);
      if (rule.isPresent()) {
        rules.add(rule.get());
      } else {
        ok = false;
      }
    }
    return ok ? Optional.of(rules) : Optional.empty();
  }

  private Optional<RuleDef> rule(PNode node, Set<String> ids) {
    if (!(node instanceof PMap map)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "a rule is a map (POL-21, FEAT-2)");
      return Optional.empty();
    }
    boolean ok = true;
    for (Map.Entry<String, PNode> member : map.entries().entrySet()) {
      if (!MEMBERS.contains(member.getKey())) {
        findings.error(Code.E_INVALID_DECLARATION, member.getValue(), "a rule has no member " + member.getKey() + " (POL-21, FEAT-2)");
        ok = false;
      }
    }
    for (String required : List.of("id", "name", "when", "then")) {
      if (!map.has(required)) {
        findings.error(Code.E_INVALID_DECLARATION, map, "a rule declares " + required + " (POL-21)");
        ok = false;
      }
    }
    Optional<String> id = text(map, "id");
    Optional<String> name = text(map, "name");
    if (id.isPresent() && !ids.add(id.get())) {
      findings.error(Code.E_DUPLICATE_RULE_ID, map.get("id").orElseThrow(),
          "the rule id " + id.get() + " is used twice in one policy (POL-24)");
      ok = false;
    }
    Optional<List<ConditionDef>> when = map.get("when").flatMap(this::when);
    Optional<FeatureEffect> then = Optional.empty();
    if (map.has("then") && returns.isPresent()) {
      then = effects.readThen(returns.get(), map.get("then").orElseThrow());
    }
    if (!ok || id.isEmpty() || name.isEmpty() || when.isEmpty() || then.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new RuleDef(id.get(), name.get(), when.get(), then.get().value()));
  }

  /** Reads {@code id} or {@code name}: a non-empty string (POL-25). */
  private Optional<String> text(PMap map, String member) {
    Optional<PNode> node = map.get(member);
    if (node.isEmpty()) {
      return Optional.empty();
    }
    Optional<String> text = FeatureNodes.name(node.get()).filter(found -> !found.isEmpty());
    if (text.isEmpty()) {
      findings.error(Code.E_INVALID_DECLARATION, node.get(), "a rule's " + member + " is a non-empty string (POL-25)");
    }
    return text;
  }

  /** Reads {@code when}: a mapping from fact name to condition (RULE-1, RULE-2). */
  private Optional<List<ConditionDef>> when(PNode node) {
    if (!(node instanceof PMap map)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "when is a mapping from fact name to condition (RULE-1, RULE-6)");
      return Optional.empty();
    }
    boolean ok = true;
    List<ConditionDef> parsed = new ArrayList<>();
    for (Map.Entry<String, PNode> entry : map.entries().entrySet()) {
      Optional<ConditionDef> condition = conditions.parse(entry.getKey(), entry.getValue());
      if (condition.isPresent()) {
        parsed.add(condition.get());
      } else {
        ok = false;
      }
    }
    parsed.sort(Comparator.comparing(ConditionDef::fact, CodePointOrder.ORDER));
    return ok ? Optional.of(parsed) : Optional.empty();
  }
}
