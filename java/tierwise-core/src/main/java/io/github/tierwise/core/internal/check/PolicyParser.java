package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.model.ConditionDef;
import io.github.tierwise.core.internal.model.PolicyDef;
import io.github.tierwise.core.internal.model.Returns;
import io.github.tierwise.core.internal.model.RuleDef;
import io.github.tierwise.core.internal.model.SettingDef;
import io.github.tierwise.core.internal.project.CodePointOrder;
import io.github.tierwise.core.internal.project.Names;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Parses the {@code policies} of a rules file (POL-6 to POL-9, POL-26, POL-27, BUNDLE-83 to BUNDLE-93).
 *
 * <p>A policy declares {@code default} and {@code rules}, and may declare {@code settings}. Its
 * {@code requiredFacts} are derived from the conditions of all its rules, including rules that can never
 * run (BUNDLE-91).
 */
final class PolicyParser {

  private static final Set<String> MEMBERS = Set.of("default", "rules", "settings");

  private final Findings findings;
  private final FeatureInputs inputs;
  private final FeatureDecl feature;
  private final Optional<FeatureScope> scope;

  /**
   * Creates the parser.
   *
   * @param findings the accumulator that receives the findings
   * @param inputs what the earlier stages built
   * @param feature the feature that the rules file configures
   */
  PolicyParser(Findings findings, FeatureInputs inputs, FeatureDecl feature) {
    this.findings = findings;
    this.inputs = inputs;
    this.feature = feature;
    this.scope = feature.decidedPer().flatMap(decided -> inputs.entities().map(found -> FeatureScope.of(decided, found)));
  }

  /**
   * Parses a {@code policies} mapping.
   *
   * @param node the node
   * @return the policies by name in source order, or empty when the node is not a mapping (which is reported)
   */
  Optional<Map<String, PolicyInfo>> parse(PNode node) {
    if (!(node instanceof PMap map)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "policies is a map from policy name to policy (POL-6)");
      return Optional.empty();
    }
    Map<String, PolicyInfo> policies = new LinkedHashMap<>();
    for (Map.Entry<String, PNode> entry : map.entries().entrySet()) {
      String name = Names.nfc(entry.getKey());
      // A repeated policy name is a repeated key, which the loader reports (POL-10, SRC-3).
      policies.putIfAbsent(name, policy(name, entry.getValue()));
    }
    return Optional.of(policies);
  }

  private PolicyInfo policy(String name, PNode node) {
    if (!(node instanceof PMap map)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "a policy is a map with default and rules (POL-7, POL-8)");
      return new PolicyInfo(name, Optional.empty(), Optional.empty());
    }
    boolean ok = true;
    for (Map.Entry<String, PNode> member : map.entries().entrySet()) {
      if (!MEMBERS.contains(member.getKey())) {
        findings.error(Code.E_INVALID_DECLARATION, member.getValue(),
            "a policy has no member " + member.getKey() + " (POL-7 to POL-9, FEAT-2)");
        ok = false;
      }
    }
    for (String required : List.of("default", "rules")) {
      if (!map.has(required)) {
        findings.error(Code.E_INVALID_DECLARATION, map, "a policy declares " + required + " (POL-7, POL-8)");
        ok = false;
      }
    }
    Optional<Map<String, SettingEntry>> settings = Optional.of(Map.of());
    if (map.has("settings")) {
      settings = new SettingParser(findings, inputs, scope).parse(map.get("settings").orElseThrow());
      ok &= settings.isPresent();
    }
    Optional<FeatureEffect> defaultEffect = Optional.empty();
    Optional<Returns> returns = feature.returns();
    if (map.has("default") && returns.isPresent()) {
      defaultEffect = new FeatureEffects(findings).read(returns.get(), map.get("default").orElseThrow());
    }
    Optional<List<RuleDef>> rules = Optional.empty();
    if (map.has("rules")) {
      ConditionParser conditions = new ConditionParser(findings, inputs, scope, settings);
      rules = new RuleParser(findings, returns, conditions).parse(map.get("rules").orElseThrow());
    }
    ok &= settings.isPresent() && settings.get().values().stream().allMatch(entry -> entry.definition().isPresent());
    if (!ok || defaultEffect.isEmpty() || rules.isEmpty()) {
      return new PolicyInfo(name, settings, Optional.empty());
    }
    return new PolicyInfo(name, settings, Optional.of(build(settings.get(), defaultEffect.get(), rules.get())));
  }

  private static PolicyDef build(Map<String, SettingEntry> settings, FeatureEffect defaultEffect, List<RuleDef> rules) {
    Map<String, SettingDef> definitions = new LinkedHashMap<>();
    for (SettingEntry entry : settings.values()) {
      entry.definition().ifPresent(definition -> definitions.put(entry.name(), definition));
    }
    TreeSet<String> required = new TreeSet<>(CodePointOrder.ORDER);
    for (RuleDef rule : rules) {
      for (ConditionDef condition : rule.when()) {
        required.add(condition.fact());
      }
    }
    return new PolicyDef(definitions, defaultEffect.value(), List.copyOf(required), rules);
  }
}
