package io.github.tierwise.core.internal.check;

/**
 * <strong>(STUB)</strong> The values stage.
 *
 * <p>This stage builds nothing and reports nothing yet. It keeps its place in the fixed order of
 * {@link Compiler#standardStages()} and the {@link Stage} interface that the work item for section 5 (KEY and MERGE areas, values) and section 2 (SRC-32)
 * implements. Until then {@link ModelParts} stays incomplete and the compiler writes no bundle.
 *
 * <p>Owns: the checks of authored values: E_UNKNOWN_KEY, E_NOT_SETTABLE_HERE, E_DUPLICATE_VALUE for a repeated value (KEY-12) and E_INVALID_DECLARATION or the TYPE codes for an invalid value, checked with {@link ValueValidator}, and E_KIND_VIOLATION for an identity value.
 *
 * <p>Produces: {@link ModelParts#values(io.github.tierwise.core.internal.model.Values)}.
 *
 * <p>Reads: the {@code ValueGroup}s of the values documents, {@link ModelParts#keys()} and {@link ModelParts#hierarchy()}.
 */
public final class ValueStage implements Stage {

  @Override
  public String name() {
    return "values";
  }

  @Override
  public void run(CheckContext context) {
    // STUB: intentionally empty. See the class comment for the contract of the real stage.
  }
}
