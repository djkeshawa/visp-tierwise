package io.github.visplabs.tierwise.core.internal.eval;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.Decision;
import io.github.visplabs.tierwise.core.FactSet;
import io.github.visplabs.tierwise.core.TierwiseException;
import io.github.visplabs.tierwise.core.internal.model.FactDef;
import io.github.visplabs.tierwise.core.internal.model.FeatureDef;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.model.PolicyDef;
import io.github.visplabs.tierwise.core.internal.model.RuleDef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The decide operation (EVAL-8, EVAL-12, EVAL-17).
 *
 * <p>The steps run in this order, and the first failing step decides the error (EVAL-18, EVAL-20):
 * context validation; feature lookup; FactSet validation against the decided entity; binding selection;
 * the missing-fact precheck on the selected policy; rule evaluation under {@code FIRST}, or the policy
 * default. The last three steps raise no error. Decide calls no provider (SVC-22).
 *
 * <p>The precheck set is the facts of the {@code when} mappings of every rule of the selected policy,
 * not only the rules that would be reached (RULE-40). Facts named only by settings are not part of it
 * (RULE-41). If any of them is missing, no rule is checked and the feature's {@code if_fact_missing}
 * is the effect, with the missing facts in ascending code-point order (RULE-42 to RULE-44).
 */
final class DecisionEngine {

  private final Model model;
  private final String bundleVersion;
  private final ContextValidator validator;
  private final FactSetValidator factValidator;
  private final RuleMatcher matcher;
  private final Map<String, EntityScope> scopes;

  /**
   * Creates the decision engine of an evaluator.
   *
   * @param model the model of the bundle
   * @param bundleVersion the digest of the bundle
   * @param validator the context validator
   */
  DecisionEngine(Model model, String bundleVersion, ContextValidator validator) {
    this.model = model;
    this.bundleVersion = bundleVersion;
    this.validator = validator;
    this.factValidator = new FactSetValidator(model);
    this.matcher = new RuleMatcher(model);
    Map<String, EntityScope> byEntity = new HashMap<>();
    for (FeatureDef feature : model.features().values()) {
      byEntity.computeIfAbsent(feature.decidedPer(), entity -> EntityScope.of(model, entity));
    }
    this.scopes = Map.copyOf(byEntity);
  }

  /**
   * Decides a feature.
   *
   * @param context the context; {@code null} counts as empty (EVAL-54)
   * @param featureName the declared feature name; an omitted or empty name is an unknown feature
   *     (EVAL-37)
   * @param facts the FactSet; {@code null} counts as empty
   * @return the decision
   */
  Decision decide(Context context, String featureName, FactSet facts) {
    AdmittedContext admitted = validator.validate(context);
    FeatureDef feature = featureName == null ? null : model.features().get(featureName);
    if (feature == null) {
      throw new TierwiseException(Code.E_UNKNOWN_FEATURE, "no such feature: " + featureName);
    }
    EntityScope scope = scopes.get(feature.decidedPer());
    ValidatedFacts validated = factValidator.validate(scope, facts);
    BindingSelection.Selected selected = BindingSelection.select(feature, admitted.path());
    PolicyDef policy = feature.policies().get(selected.binding().policy());
    if (policy == null) {
      throw new IllegalStateException("the selected binding names an undeclared policy");
    }
    Decision.Binding binding = new Decision.Binding(selected.scope().name(), selected.scope().optionalNode());
    List<String> missing = missingFacts(policy, scope, validated);
    if (!missing.isEmpty()) {
      return ResultFactory.decision(featureName, feature.ifFactMissing(), bundleVersion, admitted.skippedScopes(),
          binding, selected.binding().policy(), Decision.Reason.missingFact(missing), List.of());
    }
    return evaluateRules(featureName, feature, policy, selected, binding, admitted, validated);
  }

  private Decision evaluateRules(
      String featureName,
      FeatureDef feature,
      PolicyDef policy,
      BindingSelection.Selected selected,
      Decision.Binding binding,
      AdmittedContext admitted,
      ValidatedFacts facts) {
    Map<String, Object> settings = BindingSelection.resolveSettings(policy, selected.binding());
    List<Decision.TraceEntry> trace = new ArrayList<>();
    for (RuleDef rule : policy.rules()) {
      Optional<RuleMatcher.FailedCondition> failed = matcher.firstFailure(rule, facts, settings);
      if (failed.isEmpty()) {
        trace.add(Decision.TraceEntry.matched(rule.id()));
        return ResultFactory.decision(featureName, rule.then(), bundleVersion, admitted.skippedScopes(), binding,
            selected.binding().policy(), Decision.Reason.rule(rule.id(), rule.name()), trace);
      }
      trace.add(Decision.TraceEntry.failed(rule.id(), failed.get().fact(), failed.get().op()));
    }
    return ResultFactory.decision(featureName, policy.defaultEffect(), bundleVersion, admitted.skippedScopes(), binding,
        selected.binding().policy(), Decision.Reason.defaultEffect(), trace);
  }

  /** The facts of the precheck set that are missing, in ascending code-point order (RULE-40 to RULE-44). */
  private List<String> missingFacts(PolicyDef policy, EntityScope scope, ValidatedFacts facts) {
    List<String> missing = new ArrayList<>();
    for (String fact : policy.requiredFacts()) {
      if (isMissing(fact, scope, facts)) {
        missing.add(fact);
      }
    }
    missing.sort(CodePoints.ORDER);
    return missing;
  }

  private boolean isMissing(String fact, EntityScope scope, ValidatedFacts facts) {
    if (scope.isSingleValue(fact)) {
      return facts.facts().get(fact) == null;
    }
    FactDef declared = model.facts().get(fact);
    List<Map<String, Object>> children = declared == null ? null : facts.children().get(declared.entity());
    if (children == null) {
      return true;
    }
    for (Map<String, Object> child : children) {
      if (child.get(fact) == null) {
        return true;
      }
    }
    return false;
  }
}
