package io.github.visplabs.tierwise.core.internal.lint;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.BindingDef;
import io.github.visplabs.tierwise.core.internal.model.ConditionDef;
import io.github.visplabs.tierwise.core.internal.model.FeatureDef;
import io.github.visplabs.tierwise.core.internal.model.KeyDef;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.model.PolicyDef;
import io.github.visplabs.tierwise.core.internal.model.RuleDef;
import io.github.visplabs.tierwise.core.internal.model.ScopeDef;
import io.github.visplabs.tierwise.core.internal.model.SettingDef;
import io.github.visplabs.tierwise.core.internal.project.SourceDocument;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code schema/unused} (LINT-37 to LINT-43): a key, fact, setting or policy that nothing uses. A use is
 * counted wherever it is written, including from an unused declaration (LINT-42).
 */
final class UnusedRule implements LintRule {

  @Override
  public String id() {
    return Code.LINT_SCHEMA_UNUSED;
  }

  @Override
  public List<LintHit> check(LintContext context) {
    Model model = context.model();
    List<LintHit> hits = new ArrayList<>();
    Set<String> usedFacts = usedFacts(model);
    for (SourceIndex.Entry key : context.index().keys()) {
      KeyDef definition = model.keys().get(key.name());
      if (definition != null && !keyUsed(model, key.name(), definition)) {
        hits.add(new LintHit(key.node(), "the key " + key.name() + " is never used"));
      }
    }
    for (SourceIndex.Entry fact : context.index().facts()) {
      if (!usedFacts.contains(fact.name())) {
        hits.add(new LintHit(fact.node(), "the fact " + fact.name() + " is never used"));
      }
    }
    for (Map.Entry<String, FeatureDef> feature : model.features().entrySet()) {
      checkPolicies(context, feature.getKey(), feature.getValue(), hits);
    }
    return hits;
  }

  private static boolean keyUsed(Model model, String key, KeyDef definition) {
    for (String level : definition.settableAt()) {
      ScopeDef scope = model.hierarchy().scope(level);
      if (scope != null && scope.isServiceSourced()) {
        return true;
      }
    }
    for (Map<String, Map<String, Object>> nodes : model.values().byScope().values()) {
      for (Map<String, Object> entries : nodes.values()) {
        if (entries.containsKey(key)) {
          return true;
        }
      }
    }
    return false;
  }

  private static Set<String> usedFacts(Model model) {
    Set<String> used = new HashSet<>();
    for (FeatureDef feature : model.features().values()) {
      for (PolicyDef policy : feature.policies().values()) {
        for (RuleDef rule : policy.rules()) {
          for (ConditionDef condition : rule.when()) {
            used.add(condition.fact());
          }
        }
        for (SettingDef setting : policy.settings().values()) {
          used.add(setting.fact());
        }
      }
    }
    return used;
  }

  private static void checkPolicies(LintContext context, String feature, FeatureDef definition, List<LintHit> hits) {
    Set<String> bound = new HashSet<>();
    for (Map<String, BindingDef> level : definition.bindings().values()) {
      for (BindingDef binding : level.values()) {
        bound.add(binding.policy());
      }
    }
    PMap policies = context.index().rulesOf(feature)
        .map(document -> policyMap(document))
        .orElse(null);
    for (Map.Entry<String, PolicyDef> policy : definition.policies().entrySet()) {
      PNode node = policies == null ? null : policies.get(policy.getKey()).orElse(null);
      if (node == null) {
        continue;
      }
      if (!bound.contains(policy.getKey())) {
        hits.add(new LintHit(node, "the policy " + policy.getKey() + " is named by no binding"));
      }
      checkSettings(policy.getKey(), policy.getValue(), node, hits);
    }
  }

  private static void checkSettings(String policy, PolicyDef definition, PNode policyNode, List<LintHit> hits) {
    Set<String> referenced = new HashSet<>();
    for (RuleDef rule : definition.rules()) {
      for (ConditionDef condition : rule.when()) {
        if (condition.isSettingReference()) {
          referenced.add(condition.settingName());
        }
      }
    }
    if (!(policyNode instanceof PMap body)) {
      return;
    }
    PNode settings = body.get("settings").orElse(null);
    if (!(settings instanceof PMap declared)) {
      return;
    }
    for (String setting : definition.settings().keySet()) {
      if (!referenced.contains(setting)) {
        declared.get(setting).ifPresent(at -> hits.add(
            new LintHit(at, "the setting " + setting + " of " + policy + " is never used")));
      }
    }
  }

  private static PMap policyMap(SourceDocument document) {
    return document.root().get("policies").filter(PMap.class::isInstance).map(PMap.class::cast).orElse(null);
  }
}
