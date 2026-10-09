package io.github.tierwise.core.internal.lint;

import io.github.tierwise.core.internal.model.BindingDef;
import io.github.tierwise.core.internal.model.FeatureDef;
import io.github.tierwise.core.internal.model.Hierarchy;
import java.util.Map;

/**
 * The binding that a context selects for a feature (BIND-16, BIND-17): the candidate at the latest level in
 * declared order, where the root binding is the candidate of the root level and each non-root level
 * contributes the binding of the node that the context names there, if one exists.
 *
 * <p>An example's context is valid (POL-31), so no scope on its path is skipped, and this choice equals the
 * binding that the evaluator selects for it.
 *
 * @param level the level of the selected binding
 * @param node the node of the selected binding; empty for the root level
 * @param binding the selected binding
 */
public record BindingChoice(String level, String node, BindingDef binding) {

  /**
   * Selects the binding of a context.
   *
   * @param feature the feature
   * @param hierarchy the decision hierarchy
   * @param levels the non-root levels that the context names, with their node IDs
   * @return the selected binding
   */
  public static BindingChoice of(FeatureDef feature, Hierarchy hierarchy, Map<String, String> levels) {
    String level = hierarchy.rootLevel();
    String node = "";
    for (int index = 1; index < hierarchy.levels().size(); index++) {
      String candidate = hierarchy.levels().get(index);
      String named = levels.get(candidate);
      Map<String, BindingDef> atLevel = feature.bindings().get(candidate);
      if (named != null && atLevel != null && atLevel.containsKey(named)) {
        level = candidate;
        node = named;
      }
    }
    BindingDef binding = feature.bindings().get(level).get(node);
    return new BindingChoice(level, node, binding);
  }

  /**
   * Returns the name of the selected policy (BIND-21).
   *
   * @return the policy name
   */
  public String policy() {
    return binding.policy();
  }
}
