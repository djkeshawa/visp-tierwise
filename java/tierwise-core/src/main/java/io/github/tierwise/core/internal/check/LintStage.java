package io.github.tierwise.core.internal.check;

/**
 * <strong>(STUB)</strong> The lint stage (LINT-1 to LINT-50).
 *
 * <p>This stage builds nothing and reports nothing yet. The real stage resolves the severity of each lint
 * rule once, with {@link io.github.tierwise.core.internal.project.LintSettings#severityOf(String)}, runs
 * each rule whose severity is not {@code off} (LINT-6, LINT-7), and reports lint findings through
 * {@link Findings#lint(String, io.github.tierwise.core.Severity,
 * io.github.tierwise.core.internal.yaml.PNode, String)}.
 *
 * <p>Lint rules run only when validation has found no engine finding (LINT-11), so this stage is
 * disabled as soon as {@link Findings#hasEngineFindings()} holds. The invalid lint settings of LINT-8 and
 * LINT-10 are engine findings that the project loader already reports.
 */
public final class LintStage implements Stage {

  @Override
  public String name() {
    return "lint";
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
