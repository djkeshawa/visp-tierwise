package io.github.tierwise.core.internal.check;

/**
 * <strong>(STUB)</strong> The feature, policy, rule and binding stage.
 *
 * <p>This stage builds nothing and reports nothing yet. It keeps its place in the fixed order of
 * {@link Compiler#standardStages()} and the {@link Stage} interface that the work item for sections 7 and 8 (FEAT, POL, BIND and RULE areas)
 * implements. Until then {@link ModelParts} stays incomplete and the compiler writes no bundle.
 *
 * <p>Owns: the FEAT, POL, BIND and RULE clauses for {@code features} and for the rules documents: E_UNKNOWN_FEATURE, E_UNKNOWN_POLICY, E_UNKNOWN_SETTING, E_INVALID_SETTING, E_ROOT_BINDING_MISSING, E_BINDING_NOT_ALLOWED, E_ILLEGAL_FACT_VALUE and the others. The duplicate rules documents of SRC-44 are already reported by the project loader.
 *
 * <p>Produces: {@link ModelParts#features(java.util.Map)}.
 *
 * <p>Reads: the {@code features} members of the schema documents, the rules documents, {@link ModelParts#facts()} and {@link ModelParts#hierarchy()}.
 */
public final class FeatureStage implements Stage {

  @Override
  public String name() {
    return "features";
  }

  @Override
  public void run(CheckContext context) {
    // STUB: intentionally empty. See the class comment for the contract of the real stage.
  }
}
