package io.github.tierwise.core.internal.lint;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.model.BindingDef;
import io.github.tierwise.core.internal.model.ConditionDef;
import io.github.tierwise.core.internal.model.EntityDef;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.FeatureDef;
import io.github.tierwise.core.internal.model.Model;
import io.github.tierwise.core.internal.model.PolicyDef;
import io.github.tierwise.core.internal.model.RuleDef;
import io.github.tierwise.core.internal.model.SettingDef;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code examples/mixed-collection} (LINT-32 to LINT-36): a policy with an {@code all_in} or {@code any_in}
 * condition that no example of its feature exercises with a mixed child collection. Only a policy that some
 * binding names is checked (LINT-35).
 */
final class MixedCollectionRule implements LintRule {

  @Override
  public String id() {
    return Code.LINT_EXAMPLES_MIXED_COLLECTION;
  }

  @Override
  public List<LintHit> check(LintContext context) {
    Model model = context.model();
    List<LintHit> hits = new ArrayList<>();
    for (Map.Entry<String, FeatureDef> feature : model.features().entrySet()) {
      FeatureDef definition = feature.getValue();
      Set<String> bound = boundPolicies(definition);
      for (Map.Entry<String, PolicyDef> policy : definition.policies().entrySet()) {
        if (!bound.contains(policy.getKey())) {
          continue;
        }
        List<ConditionDef> conditions = membershipConditions(policy.getValue());
        if (conditions.isEmpty() || covered(context, feature.getKey(), definition, policy.getKey(), conditions)) {
          continue;
        }
        policyNode(context, feature.getKey(), policy.getKey()).ifPresent(at -> hits.add(
            new LintHit(at, "no example exercises a mixed child collection for " + policy.getKey())));
      }
    }
    return hits;
  }

  private static Set<String> boundPolicies(FeatureDef feature) {
    Set<String> names = new HashSet<>();
    for (Map<String, BindingDef> level : feature.bindings().values()) {
      for (BindingDef binding : level.values()) {
        names.add(binding.policy());
      }
    }
    return names;
  }

  private static List<ConditionDef> membershipConditions(PolicyDef policy) {
    List<ConditionDef> conditions = new ArrayList<>();
    for (RuleDef rule : policy.rules()) {
      for (ConditionDef condition : rule.when()) {
        if (condition.op().equals("all_in") || condition.op().equals("any_in")) {
          conditions.add(condition);
        }
      }
    }
    return conditions;
  }

  private static boolean covered(LintContext context, String feature, FeatureDef definition, String policy,
      List<ConditionDef> conditions) {
    for (LintExample example : context.examples()) {
      if (!example.feature().equals(feature)) {
        continue;
      }
      BindingChoice choice = BindingChoice.of(definition, context.model().hierarchy(), example.levels());
      if (!choice.policy().equals(policy)) {
        continue;
      }
      PolicyDef selected = definition.policies().get(policy);
      for (ConditionDef condition : conditions) {
        if (mixed(context.model(), definition, selected, choice.binding(), example, condition)) {
          return true;
        }
      }
    }
    return false;
  }

  /** A child collection is mixed for a condition when one child is a member and another is present and not (LINT-33). */
  private static boolean mixed(Model model, FeatureDef feature, PolicyDef policy, BindingDef binding,
      LintExample example, ConditionDef condition) {
    List<?> membership = membership(policy, binding, condition);
    FactDef fact = model.facts().get(condition.fact());
    if (membership == null || fact == null) {
      return false;
    }
    EntityDef decided = model.entities().get(feature.decidedPer());
    if (decided == null || !decided.hasMany().contains(fact.entity())) {
      return false;
    }
    List<Map<String, Object>> children = example.children().get(fact.entity());
    if (children == null) {
      return false;
    }
    boolean member = false;
    boolean other = false;
    for (Map<String, Object> child : children) {
      Object value = child.get(condition.fact());
      if (value == null) {
        continue;
      }
      if (membership.contains(value)) {
        member = true;
      } else {
        other = true;
      }
    }
    return member && other;
  }

  /** The list a condition tests: its literal, or the resolved value of the setting it references (LINT-34). */
  private static List<?> membership(PolicyDef policy, BindingDef binding, ConditionDef condition) {
    if (!condition.isSettingReference()) {
      return condition.value() instanceof List<?> literal ? literal : null;
    }
    String setting = condition.settingName();
    Object value = binding.settings().containsKey(setting) ? binding.settings().get(setting)
        : defaultOf(policy, setting);
    return value instanceof List<?> list ? list : null;
  }

  private static Object defaultOf(PolicyDef policy, String setting) {
    SettingDef declared = policy.settings().get(setting);
    return declared == null ? null : declared.defaultValue();
  }

  private static Optional<PNode> policyNode(LintContext context, String feature, String policy) {
    return context.index().rulesOf(feature)
        .map(document -> document.root().get("policies").orElse(null))
        .filter(PMap.class::isInstance)
        .map(PMap.class::cast)
        .flatMap(policies -> policies.get(policy));
  }
}
