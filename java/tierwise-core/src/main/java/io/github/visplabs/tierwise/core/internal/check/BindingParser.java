package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.BindingDef;
import io.github.visplabs.tierwise.core.internal.model.FactDef;
import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.model.ScopeDef;
import io.github.visplabs.tierwise.core.internal.model.SettingDef;
import io.github.visplabs.tierwise.core.internal.project.NameSuggester;
import io.github.visplabs.tierwise.core.internal.project.Names;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses the {@code bindings} of a rules file (section 7.3: BIND-1 to BIND-13, with FEAT-19, FEAT-20,
 * SCOPE-33 to SCOPE-46).
 *
 * <p>The result maps each level to its bindings by node key, where the root level's single binding has
 * the empty node key (BUNDLE-66). A binding that names an unknown policy, or no policy, gets no setting
 * checks (BIND-7, ADR 0017). A binding at a level or axis that the feature does not allow is reported
 * once at each binding and gets no further check.
 */
final class BindingParser {

  private final Findings findings;
  private final FeatureInputs inputs;
  private final FeatureDecl feature;
  private final Optional<Map<String, PolicyInfo>> policies;
  private final SettingValues values;

  /**
   * Creates the parser.
   *
   * @param findings the accumulator that receives the findings
   * @param inputs what the earlier stages built
   * @param feature the feature that the rules file configures
   * @param policies the policies of the rules file, when they were read
   */
  BindingParser(Findings findings, FeatureInputs inputs, FeatureDecl feature,
      Optional<Map<String, PolicyInfo>> policies) {
    this.findings = findings;
    this.inputs = inputs;
    this.feature = feature;
    this.policies = policies;
    this.values = new SettingValues(findings);
  }

  /**
   * Parses a {@code bindings} mapping, including the check that a root binding exists (BIND-13).
   *
   * @param node the node
   * @return the bindings by level and node key, or empty when the mapping or a binding has a fault, or a
   *     check was skipped
   */
  Optional<Map<String, Map<String, BindingDef>>> parse(PNode node) {
    if (!(node instanceof PMap map)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "bindings is a map from level name to bindings (BIND-1)");
      return Optional.empty();
    }
    if (inputs.scopes().isEmpty()) {
      return Optional.empty();
    }
    ScopeIndex scopes = inputs.scopes().get();
    Hierarchy hierarchy = scopes.hierarchy();
    boolean ok = true;
    boolean root = false;
    Map<String, Map<String, BindingDef>> result = new LinkedHashMap<>();
    for (Map.Entry<String, PNode> entry : map.entries().entrySet()) {
      PNode value = entry.getValue();
      Optional<String> resolved = scopes.resolveScope(findings, value, entry.getKey());
      if (resolved.isEmpty()) {
        ok = false;
        continue;
      }
      String level = resolved.get();
      root |= level.equals(hierarchy.rootLevel());
      if (!allowed(scopes, level)) {
        notAllowed(hierarchy, level, value);
        ok = false;
        continue;
      }
      Optional<Map<String, BindingDef>> bindings = level.equals(hierarchy.rootLevel())
          ? rootBinding(value) : nodeBindings(scopes, level, value);
      if (bindings.isPresent()) {
        result.put(level, bindings.get());
      } else {
        ok = false;
      }
    }
    if (!root) {
      findings.error(Code.E_ROOT_BINDING_MISSING, node, "the feature has no root binding (BIND-13)");
      ok = false;
    }
    return ok ? Optional.of(result) : Optional.empty();
  }

  // ---- which scopes carry bindings (BIND-12, BIND-15, FEAT-19, FEAT-20) --------------------------

  private boolean allowed(ScopeIndex scopes, String scope) {
    if (scopes.hierarchy().isAxis(scope) || scopes.definition(scope).filter(ScopeDef::isServiceSourced).isPresent()) {
      return false;
    }
    return feature.listed().map(listed -> listed.contains(scope)).orElse(true);
  }

  private void notAllowed(Hierarchy hierarchy, String scope, PNode value) {
    String message = "a binding appears only at a level that binding_settable_at lists and that is not service-sourced (BIND-12, BIND-15)";
    if (scope.equals(hierarchy.rootLevel()) || !(value instanceof PMap nodes)) {
      findings.error(Code.E_BINDING_NOT_ALLOWED, value, message);
      return;
    }
    for (PNode binding : nodes.entries().values()) {
      findings.error(Code.E_BINDING_NOT_ALLOWED, binding, message);
    }
  }

  // ---- the shapes of BIND-2 and BIND-3 ----------------------------------------------------------

  private Optional<Map<String, BindingDef>> rootBinding(PNode node) {
    return binding(node).map(binding -> Map.of("", binding));
  }

  private Optional<Map<String, BindingDef>> nodeBindings(ScopeIndex scopes, String level, PNode node) {
    if (!(node instanceof PMap nodes)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "bindings at a non-root level is a map from node ID to binding (BIND-3)");
      return Optional.empty();
    }
    boolean ok = true;
    Map<String, BindingDef> result = new LinkedHashMap<>();
    Set<String> seen = new HashSet<>();
    for (Map.Entry<String, PNode> entry : nodes.entries().entrySet()) {
      String id = Names.nfc(entry.getKey());
      ok &= scopes.checkNode(findings, entry.getValue(), level, entry.getKey()) == ScopeIndex.NodeStatus.ADMITTED;
      // A node ID repeated after NFC normalisation is a repeated key, which the loader reports (POL-10, SRC-4).
      if (!seen.add(id)) {
        continue;
      }
      Optional<BindingDef> binding = binding(entry.getValue());
      if (binding.isPresent()) {
        result.put(id, binding.get());
      } else {
        ok = false;
      }
    }
    return ok ? Optional.of(result) : Optional.empty();
  }

  // ---- one binding (BIND-4 to BIND-9) -----------------------------------------------------------

  private Optional<BindingDef> binding(PNode node) {
    if (!(node instanceof PMap map)) {
      findings.error(Code.E_INVALID_DECLARATION, node, "a binding is a map with a policy entry (BIND-4)");
      return Optional.empty();
    }
    Optional<PNode> policyNode = map.get("policy");
    if (policyNode.isEmpty()) {
      findings.error(Code.E_INVALID_DECLARATION, map, "a binding contains a policy entry (BIND-4)");
      return Optional.empty();
    }
    Optional<String> policyName = FeatureNodes.name(policyNode.get()).filter(text -> !text.isEmpty());
    if (policyName.isEmpty()) {
      findings.error(Code.E_INVALID_DECLARATION, policyNode.get(), "the policy entry of a binding is a policy name (BIND-4, BIND-5)");
      return Optional.empty();
    }
    if (policies.isEmpty()) {
      return Optional.empty();
    }
    PolicyInfo policy = policies.get().get(policyName.get());
    if (policy == null) {
      findings.error(Code.E_UNKNOWN_POLICY, policyNode.get(), "the policy " + policyName.get() + " is not declared in the rules file (BIND-5, BIND-6)");
      return Optional.empty();
    }
    if (policy.settings().isEmpty()) {
      return Optional.empty();
    }
    return settings(map, policy);
  }

  /** Checks the setting entries of a binding against the settings of its policy (BIND-8, BIND-9). */
  private Optional<BindingDef> settings(PMap map, PolicyInfo policy) {
    Map<String, SettingEntry> declared = policy.settings().orElseThrow();
    boolean ok = true;
    Map<String, Object> given = new LinkedHashMap<>();
    for (Map.Entry<String, PNode> entry : map.entries().entrySet()) {
      if (entry.getKey().equals("policy")) {
        continue;
      }
      String name = Names.nfc(entry.getKey());
      SettingEntry setting = declared.get(name);
      if (setting == null) {
        findings.error(Code.E_UNKNOWN_SETTING, entry.getValue(),
            "the policy " + policy.name() + " has no setting " + name + " (BIND-8)",
            NameSuggester.closest(name, declared.keySet()));
        ok = false;
      } else if (setting.definition().isEmpty()) {
        ok = false;
      } else {
        SettingDef definition = setting.definition().get();
        FactDef fact = inputs.facts().orElseThrow().get(definition.fact());
        Optional<Object> value = values.read(definition.kind(), fact.type(), entry.getValue());
        if (value.isPresent()) {
          given.put(name, value.get());
        } else {
          ok = false;
        }
      }
    }
    return ok ? Optional.of(new BindingDef(policy.name(), given)) : Optional.empty();
  }
}
