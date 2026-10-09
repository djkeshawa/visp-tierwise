package io.github.tierwise.core.internal.check;

/**
 * <strong>(STUB)</strong> The key declaration stage.
 *
 * <p>This stage builds nothing and reports nothing yet. It keeps its place in the fixed order of
 * {@link Compiler#standardStages()} and the {@link Stage} interface that the work item for section 5 (KEY and MERGE areas) and section 6 (SVC area, declarations)
 * implements. Until then {@link ModelParts} stays incomplete and the compiler writes no bundle.
 *
 * <p>Owns: the KEY, MERGE and SVC declaration clauses for {@code keys}: E_TYPE_REQUIRED, E_INVALID_DECLARATION, E_STRATEGY_TYPE, E_MISSING_DEFAULT, E_AXIS_COMBINE_INVALID, E_KIND_VIOLATION, E_UNKNOWN_KIND, E_UNKNOWN_SCOPE and the others. A key whose {@code type} or {@code combine} is missing or invalid receives no further check (FIND-35). Key types are parsed with {@link TypeDeclParser}.
 *
 * <p>Produces: {@link ModelParts#keys(java.util.Map)}.
 *
 * <p>Reads: the {@code keys} member of the schema documents, {@link ModelParts#hierarchy()} and the kind table of {@link KindStage}.
 */
public final class KeyStage implements Stage {

  @Override
  public String name() {
    return "keys";
  }

  @Override
  public void run(CheckContext context) {
    // STUB: intentionally empty. See the class comment for the contract of the real stage.
  }
}
