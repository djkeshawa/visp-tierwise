package io.github.tierwise.core.internal.model;

import java.util.Map;
import java.util.Objects;

/**
 * The selection of a policy at the root level or at a node of a non-root level (BUNDLE-105 to
 * BUNDLE-108).
 *
 * @param policy the name of the selected policy
 * @param settings exactly the setting values that the source binding writes; policy defaults are not
 *     copied into it (BUNDLE-108)
 */
public record BindingDef(String policy, Map<String, Object> settings) {

  /** Validates the components and copies the settings deeply. */
  public BindingDef {
    Objects.requireNonNull(policy, "policy");
    settings = Plain.copyMap(settings);
  }
}
