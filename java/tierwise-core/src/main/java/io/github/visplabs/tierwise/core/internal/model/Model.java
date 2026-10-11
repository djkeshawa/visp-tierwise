package io.github.visplabs.tierwise.core.internal.model;

import java.util.Map;
import java.util.Objects;

/**
 * The whole compiled model: the immutable counterpart of a bundle's {@code content} (BUNDLE-14).
 *
 * @param bundleFormatVersion the bundle format version, {@code "1"} in this version
 * @param specVersion the specification version, {@code "0.1"} in this version
 * @param hierarchy levels, axes and scopes
 * @param entities entity declarations by name
 * @param facts fact declarations by {@code <entity>.<attribute>} name
 * @param keys key declarations by name
 * @param values authored values by scope, node and key
 * @param features feature declarations by name
 */
public record Model(
    String bundleFormatVersion,
    String specVersion,
    Hierarchy hierarchy,
    Map<String, EntityDef> entities,
    Map<String, FactDef> facts,
    Map<String, KeyDef> keys,
    Values values,
    Map<String, FeatureDef> features) {

  /** The bundle format version that this version of the specification defines (BUNDLE-16). */
  public static final String FORMAT_VERSION = "1";

  /** The specification version that this implementation supports (BUNDLE-17). */
  public static final String SPEC_VERSION = "0.1";

  /** Validates the components and makes the maps unmodifiable. */
  public Model {
    Objects.requireNonNull(bundleFormatVersion, "bundleFormatVersion");
    Objects.requireNonNull(specVersion, "specVersion");
    Objects.requireNonNull(hierarchy, "hierarchy");
    Objects.requireNonNull(values, "values");
    entities = Plain.orderedMap(entities);
    facts = Plain.orderedMap(facts);
    keys = Plain.orderedMap(keys);
    features = Plain.orderedMap(features);
  }
}
