package io.github.tierwise.core.internal.eval;

import io.github.tierwise.core.ScopeKind;
import io.github.tierwise.core.internal.model.BindingDef;
import io.github.tierwise.core.internal.model.FeatureDef;
import io.github.tierwise.core.internal.model.PolicyDef;
import io.github.tierwise.core.internal.model.SettingDef;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Binding selection and setting values for one decision (BIND-14 to BIND-24).
 *
 * <p>The candidates are the root binding and, for each admitted non-root level, the binding written for
 * the node the context names (BIND-16). Axes carry no bindings (BIND-15), and omitted or skipped scopes
 * are not on the path, so they contribute no candidate. The candidate at the latest level is selected and
 * replaces all others entirely (BIND-17, BIND-18).
 */
final class BindingSelection {

  /**
   * The selected binding.
   *
   * @param scope the level, and the node the context names for it ({@code null} for the root level)
   * @param binding the binding
   */
  record Selected(PathScope scope, BindingDef binding) {
  }

  private BindingSelection() {
  }

  /**
   * Selects the binding of a decision.
   *
   * @param feature the feature
   * @param path the evaluation path in order (root level first)
   * @return the selected binding
   * @throws IllegalStateException if the bundle holds no root binding, which a loaded bundle never lacks
   *     (BIND-13)
   */
  static Selected select(FeatureDef feature, List<PathScope> path) {
    for (int i = path.size() - 1; i >= 0; i--) {
      PathScope scope = path.get(i);
      if (scope.kind() != ScopeKind.LEVEL) {
        continue;
      }
      Map<String, BindingDef> written = feature.bindings().get(scope.name());
      BindingDef binding = written == null ? null : written.get(scope.nodeKey());
      if (binding != null) {
        return new Selected(scope, binding);
      }
    }
    throw new IllegalStateException("the bundle has no root binding for the feature");
  }

  /**
   * Resolves the settings of the selected policy (BIND-23, BIND-24): the value the selected binding
   * gives, else the setting's default. No value comes from another binding.
   *
   * @param policy the selected policy
   * @param binding the selected binding
   * @return the resolved value of each setting, by setting name
   */
  static Map<String, Object> resolveSettings(PolicyDef policy, BindingDef binding) {
    Map<String, Object> resolved = new LinkedHashMap<>();
    for (Map.Entry<String, SettingDef> setting : policy.settings().entrySet()) {
      Object given = binding.settings().get(setting.getKey());
      resolved.put(setting.getKey(), given != null ? given : setting.getValue().defaultValue());
    }
    return Collections.unmodifiableMap(resolved);
  }
}
