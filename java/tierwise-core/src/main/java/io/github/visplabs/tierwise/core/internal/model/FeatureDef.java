package io.github.visplabs.tierwise.core.internal.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A feature declaration with its policies and bindings (BUNDLE-76 to BUNDLE-108).
 *
 * @param decidedPer the entity that the decision is about
 * @param returns the effect form
 * @param hitPolicy the hit policy, {@code FIRST} in this version
 * @param ifFactMissing the effect returned when a fact is missing; {@code null} is the null effect of a
 *     nullable enum (BUNDLE-24)
 * @param bindingSettableAt level names at which a binding may be set, in declared level order
 * @param policies policies by name
 * @param bindings bindings by level name and then by node key; the root level's node key is the empty
 *     string (BUNDLE-66)
 */
public record FeatureDef(
    String decidedPer,
    Returns returns,
    String hitPolicy,
    Object ifFactMissing,
    List<String> bindingSettableAt,
    Map<String, PolicyDef> policies,
    Map<String, Map<String, BindingDef>> bindings) {

  /** Validates the components, copies the effect deeply and makes the collections unmodifiable. */
  public FeatureDef {
    Objects.requireNonNull(decidedPer, "decidedPer");
    Objects.requireNonNull(returns, "returns");
    Objects.requireNonNull(hitPolicy, "hitPolicy");
    ifFactMissing = Plain.copyEffect(ifFactMissing);
    bindingSettableAt = List.copyOf(bindingSettableAt);
    policies = Plain.orderedMap(policies);
    Map<String, Map<String, BindingDef>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, BindingDef>> level : bindings.entrySet()) {
      copy.put(level.getKey(), Plain.orderedMap(level.getValue()));
    }
    bindings = Collections.unmodifiableMap(copy);
  }
}
