package io.github.tierwise.core.internal.check;

/**
 * <strong>(STUB)</strong> The kind declaration stage.
 *
 * <p>This stage builds nothing and reports nothing yet. It keeps its place in the fixed order of
 * {@link Compiler#standardStages()} and the {@link Stage} interface that the work item for section 5 (KIND area)
 * implements. Until then {@link ModelParts} stays incomplete and the compiler writes no bundle.
 *
 * <p>Owns: the KIND clauses for {@code kinds}: E_KIND_DEFINITION and the layer replacement of MANIFEST-32 and MANIFEST-33. A kind table is compile-time data and is not part of the model.
 *
 * <p>Produces: a kind table for {@link KeyStage}, with {@link CheckContext#provide(Class, Object)}.
 *
 * <p>Reads: the {@code kinds} and {@code naming} members of the schema documents, presets included, in layer order.
 */
public final class KindStage implements Stage {

  @Override
  public String name() {
    return "kinds";
  }

  @Override
  public void run(CheckContext context) {
    // STUB: intentionally empty. See the class comment for the contract of the real stage.
  }
}
