package io.github.tierwise.core.internal.check;

/**
 * <strong>(STUB)</strong> The example stage (POL-41 to POL-49, FIND-2).
 *
 * <p>This stage builds nothing and reports nothing yet. The real stage runs every example of every
 * feature with the existing evaluator over the in-memory model, which it assembles from
 * {@link ModelParts}, and reports {@code E_EXAMPLE_FAILED} at the failing example's {@code expect} entry
 * (FIND-34). It adds nothing to the model, because examples are compile-time data and are not written to
 * the bundle.
 *
 * <p>Examples run only when validation has found no other engine finding (POL-47), so this stage is
 * disabled as soon as {@link Findings#hasEngineFindings()} holds. It also needs a complete model, which
 * only exists when every earlier stage built its part.
 */
public final class ExampleStage implements Stage {

  @Override
  public String name() {
    return "examples";
  }

  @Override
  public boolean enabled(CheckContext context) {
    return !context.findings().hasEngineFindings();
  }

  @Override
  public void run(CheckContext context) {
    // STUB: intentionally empty. See the class comment for the contract of the real stage.
  }
}
