package io.github.tierwise.core.internal.check;

/**
 * <strong>(STUB)</strong> The decision hierarchy and node admission stage.
 *
 * <p>This stage builds nothing and reports nothing yet. It keeps its place in the fixed order of
 * {@link Compiler#standardStages()} and the {@link Stage} interface that the work item for section 2 (SCOPE area)
 * implements. Until then {@link ModelParts} stays incomplete and the compiler writes no bundle.
 *
 * <p>Owns: the SCOPE clauses: the {@code decision_hierarchy} declaration (E_SCOPE_DECLARATION, SCOPE-48 when no hierarchy is declared and the findings so far include none of E_MANIFEST, E_PARSE or E_SOURCE_MAPPING), node catalogs (E_INVALID_NODE_ID, E_DUPLICATE_NODE), and the scope and node checks of values documents (SRC-32: E_UNKNOWN_SCOPE, E_UNKNOWN_NODE).
 *
 * <p>Produces: {@link ModelParts#hierarchy(io.github.tierwise.core.internal.model.Hierarchy)}, set only when the declaration is valid.
 *
 * <p>Reads: the {@code decision_hierarchy} member of the schema documents of {@code SourceSet}, and the {@code ValueGroup}s of the values documents.
 */
public final class HierarchyStage implements Stage {

  @Override
  public String name() {
    return "hierarchy";
  }

  @Override
  public void run(CheckContext context) {
    // STUB: intentionally empty. See the class comment for the contract of the real stage.
  }
}
