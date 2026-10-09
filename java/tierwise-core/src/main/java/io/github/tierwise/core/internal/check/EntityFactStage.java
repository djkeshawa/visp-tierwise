package io.github.tierwise.core.internal.check;

/**
 * <strong>(STUB)</strong> The entity and fact declaration stage.
 *
 * <p>This stage builds nothing and reports nothing yet. It keeps its place in the fixed order of
 * {@link Compiler#standardStages()} and the {@link Stage} interface that the work item for section 3 (ENTITY and FACT areas)
 * implements. Until then {@link ModelParts} stays incomplete and the compiler writes no bundle.
 *
 * <p>Owns: the ENTITY and FACT clauses: {@code entity_hierarchy} and {@code facts} (E_ENTITY_GRAPH, E_UNKNOWN_ENTITY, E_TYPE_REQUIRED, E_INVALID_DECLARATION, E_DUPLICATE_VALUE). Fact types are parsed with {@link TypeDeclParser}.
 *
 * <p>Produces: {@link ModelParts#entities(java.util.Map)} and {@link ModelParts#facts(java.util.Map)}.
 *
 * <p>Reads: the {@code entity_hierarchy} and {@code facts} members of the schema documents.
 */
public final class EntityFactStage implements Stage {

  @Override
  public String name() {
    return "entities-and-facts";
  }

  @Override
  public void run(CheckContext context) {
    // STUB: intentionally empty. See the class comment for the contract of the real stage.
  }
}
