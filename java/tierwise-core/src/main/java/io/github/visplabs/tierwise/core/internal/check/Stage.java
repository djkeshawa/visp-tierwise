package io.github.visplabs.tierwise.core.internal.check;

/**
 * One step of the compiler pipeline (section 13.1).
 *
 * <p>A stage reads {@link CheckContext#sources()}, reports through {@link CheckContext#findings()}, and
 * publishes what it built through {@link CheckContext#parts()} or {@link CheckContext#provide(Class,
 * Object)}. A stage must not run a check whose input failed to build: when a part it depends on is
 * absent, it skips every check that needs the part, so that one fault gives one finding (FIND-35). A
 * stage never throws for a fault in the project; it reports a finding.
 */
public interface Stage {

  /**
   * Returns a short name for diagnostics.
   *
   * @return the name
   */
  String name();

  /**
   * Tells whether the stage runs. The default is that it always runs. Examples and lint run only when no
   * other engine finding exists (POL-47, LINT-11).
   *
   * @param context the context with the findings so far
   * @return {@code true} to run the stage
   */
  default boolean enabled(CheckContext context) {
    return true;
  }

  /**
   * Runs the stage.
   *
   * @param context the shared context
   */
  void run(CheckContext context);
}
