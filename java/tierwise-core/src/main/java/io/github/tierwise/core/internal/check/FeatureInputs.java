package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.internal.model.EntityDef;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.Hierarchy;
import java.util.Map;
import java.util.Optional;

/**
 * What the feature checks read from the stages that run before them: the hierarchy, the entities and the
 * facts. A part that an earlier stage did not build is empty, and a check that needs it is skipped so
 * that one fault gives one finding (FIND-35).
 *
 * @param hierarchy the decision hierarchy, when it was built
 * @param scopes the scope and node lookup that the hierarchy stage publishes, when the hierarchy was built
 * @param entities the entities by name, when they were built
 * @param facts the facts by name, when they were built
 */
record FeatureInputs(
    Optional<Hierarchy> hierarchy,
    Optional<ScopeIndex> scopes,
    Optional<Map<String, EntityDef>> entities,
    Optional<Map<String, FactDef>> facts) {

  /**
   * Reads the inputs from what the earlier stages built.
   *
   * @param context the context of the compilation
   * @return the inputs
   */
  static FeatureInputs of(CheckContext context) {
    ModelParts parts = context.parts();
    return new FeatureInputs(parts.hierarchy(), context.scopes(), parts.entities(), parts.facts());
  }

  /**
   * Tells whether every input was built, so that no check had to be skipped.
   *
   * @return {@code true} when the hierarchy, the entities and the facts are present
   */
  boolean complete() {
    return hierarchy.isPresent() && scopes.isPresent() && entities.isPresent() && facts.isPresent();
  }
}
