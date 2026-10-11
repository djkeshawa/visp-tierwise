package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.internal.model.EntityDef;
import io.github.visplabs.tierwise.core.internal.model.FactDef;
import io.github.visplabs.tierwise.core.internal.model.FeatureDef;
import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.model.KeyDef;
import io.github.visplabs.tierwise.core.internal.model.Plain;
import io.github.visplabs.tierwise.core.internal.model.Values;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The parts of the {@link io.github.visplabs.tierwise.core.internal.model.Model} that the stages have built.
 *
 * <p>A part is present only when its stage built it completely and without a fault. A part that is
 * absent either was not built yet or failed to build, and a stage that depends on it skips the checks
 * that need it (FIND-35). A project that declares no entity has an empty entity map, which is present.
 */
public final class ModelParts {

  private Hierarchy hierarchy;
  private Map<String, EntityDef> entities;
  private Map<String, FactDef> facts;
  private Map<String, KeyDef> keys;
  private Values values;
  private Map<String, FeatureDef> features;

  /**
   * Returns the decision hierarchy.
   *
   * @return the hierarchy, or empty
   */
  public Optional<Hierarchy> hierarchy() {
    return Optional.ofNullable(hierarchy);
  }

  /**
   * Stores the decision hierarchy.
   *
   * @param value the hierarchy
   */
  public void hierarchy(Hierarchy value) {
    this.hierarchy = Objects.requireNonNull(value, "hierarchy");
  }

  /**
   * Returns the entities.
   *
   * @return the entities by name, or empty
   */
  public Optional<Map<String, EntityDef>> entities() {
    return Optional.ofNullable(entities);
  }

  /**
   * Stores the entities.
   *
   * @param value the entities by name
   */
  public void entities(Map<String, EntityDef> value) {
    this.entities = Plain.orderedMap(Objects.requireNonNull(value, "entities"));
  }

  /**
   * Returns the facts.
   *
   * @return the facts by name, or empty
   */
  public Optional<Map<String, FactDef>> facts() {
    return Optional.ofNullable(facts);
  }

  /**
   * Stores the facts.
   *
   * @param value the facts by name
   */
  public void facts(Map<String, FactDef> value) {
    this.facts = Plain.orderedMap(Objects.requireNonNull(value, "facts"));
  }

  /**
   * Returns the keys.
   *
   * @return the keys by name, or empty
   */
  public Optional<Map<String, KeyDef>> keys() {
    return Optional.ofNullable(keys);
  }

  /**
   * Stores the keys.
   *
   * @param value the keys by name
   */
  public void keys(Map<String, KeyDef> value) {
    this.keys = Plain.orderedMap(Objects.requireNonNull(value, "keys"));
  }

  /**
   * Returns the authored values.
   *
   * @return the values, or empty
   */
  public Optional<Values> values() {
    return Optional.ofNullable(values);
  }

  /**
   * Stores the authored values.
   *
   * @param value the values
   */
  public void values(Values value) {
    this.values = Objects.requireNonNull(value, "values");
  }

  /**
   * Returns the features.
   *
   * @return the features by name, or empty
   */
  public Optional<Map<String, FeatureDef>> features() {
    return Optional.ofNullable(features);
  }

  /**
   * Stores the features.
   *
   * @param value the features by name
   */
  public void features(Map<String, FeatureDef> value) {
    this.features = Plain.orderedMap(Objects.requireNonNull(value, "features"));
  }

  /**
   * Tells whether every part is present, so that a model can be assembled.
   *
   * @return {@code true} when all six parts were built
   */
  public boolean complete() {
    return hierarchy != null && entities != null && facts != null && keys != null && values != null
        && features != null;
  }
}
