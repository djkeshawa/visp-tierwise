package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.model.BindingDef;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.PolicyDef;
import io.github.tierwise.core.internal.model.SettingDef;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the bindings of a feature (BUNDLE-105 to BUNDLE-108, BUNDLE-123, BUNDLE-124, BUNDLE-129, BUNDLE-131) and
 * the root binding that every feature needs (BIND-13).
 */
final class BindingReader {

  private BindingReader() {
  }

  /**
   * Reads the bindings of one feature.
   *
   * @param value the {@code bindings} member
   * @param hierarchy the hierarchy
   * @param bindingLevels the feature's {@code bindingSettableAt} levels, in declared order
   * @param policies the feature's policies
   * @param facts the declared facts
   * @param at the location of the bindings
   * @return the bindings by level, then by node key
   */
  static Map<String, Map<String, BindingDef>> read(Object value, Hierarchy hierarchy, List<String> bindingLevels,
      Map<String, PolicyDef> policies, Map<String, FactDef> facts, String at) {
    Map<String, Object> levels = Members.object(value, at);
    Map<String, Map<String, BindingDef>> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> levelEntry : levels.entrySet()) {
      String level = levelEntry.getKey();
      String levelAt = at + "." + level;
      if (hierarchy.isAxis(level)) {
        throw BundleFault.integrity(levelAt + ": only levels carry bindings (BUNDLE-106)");
      }
      if (!hierarchy.isLevel(level)) {
        throw BundleFault.integrity(levelAt + " names an undeclared scope (BUNDLE-127)");
      }
      if (!bindingLevels.contains(level)) {
        throw BundleFault.integrity(levelAt + " is not one of the feature's bindingSettableAt levels (BUNDLE-81)");
      }
      out.put(level, readLevel(level, levelEntry.getValue(), hierarchy, policies, facts, levelAt));
    }
    Map<String, BindingDef> root = out.get(hierarchy.rootLevel());
    if (root == null || !root.containsKey("")) {
      throw BundleFault.integrity(at + " lacks the root binding (BIND-13, E_ROOT_BINDING_MISSING)");
    }
    return out;
  }

  private static Map<String, BindingDef> readLevel(String level, Object value, Hierarchy hierarchy,
      Map<String, PolicyDef> policies, Map<String, FactDef> facts, String at) {
    Map<String, Object> nodes = Members.object(value, at);
    if (nodes.isEmpty()) {
      throw BundleFault.integrity(at + " has no node entry, so it must be absent (BUNDLE-22)");
    }
    Map<String, BindingDef> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> node : nodes.entrySet()) {
      String nodeAt = at + "." + node.getKey();
      NodeIds.requireAdmitted(hierarchy, level, node.getKey(), nodeAt);
      out.put(node.getKey(), readBinding(node.getValue(), policies, facts, nodeAt));
    }
    return out;
  }

  private static BindingDef readBinding(Object value, Map<String, PolicyDef> policies, Map<String, FactDef> facts,
      String at) {
    Map<String, Object> members = Members.object(value, at);
    Members.members(members, at, Set.of("policy", "settings"), Set.of());
    String policyName = Members.string(members.get("policy"), at + ".policy");
    PolicyDef policy = policies.get(policyName);
    if (policy == null) {
      throw BundleFault.integrity(at + " names the policy " + policyName + ", which the feature does not declare (BUNDLE-123)");
    }
    Map<String, Object> raw = Members.object(members.get("settings"), at + ".settings");
    Map<String, Object> settings = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      String settingAt = at + ".settings." + entry.getKey();
      SettingDef setting = policy.settings().get(entry.getKey());
      if (setting == null) {
        throw BundleFault.integrity(settingAt + " is not a setting of its policy (BUNDLE-124)");
      }
      FactDef fact = facts.get(setting.fact());
      if (setting.kind() == SettingDef.Kind.VALUE_OF) {
        ValueChecker.check(fact.type(), entry.getValue(), ValueChecker.Mode.COMPLETE, settingAt);
      } else {
        SettingReader.requireListValue(fact.type(), entry.getValue(), settingAt);
      }
      settings.put(entry.getKey(), entry.getValue());
    }
    return new BindingDef(policyName, settings);
  }
}
