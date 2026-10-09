package io.github.tierwise.core.internal.lint;

import io.github.tierwise.core.Severity;
import io.github.tierwise.core.internal.project.LintSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Runs the lint rules of a project (LINT-4 to LINT-7). The severity of each rule is resolved once from the
 * lint settings of the presets and the manifest; a rule with no severity is {@code off} and does not run.
 */
public final class LintRunner {

  private LintRunner() {
  }

  /**
   * Runs every rule that is not {@code off}.
   *
   * @param context the lint context
   * @return the findings, each with the resolved severity of its rule
   */
  public static List<LintFinding> run(LintContext context) {
    LintSettings settings = context.sources().lint();
    List<LintFinding> findings = new ArrayList<>();
    for (LintRule rule : LintRules.of(context)) {
      Optional<Severity> severity = settings.severityOf(rule.id());
      if (severity.isEmpty()) {
        continue;
      }
      for (LintHit hit : rule.check(context)) {
        findings.add(new LintFinding(rule.id(), severity.get(), hit.node(), hit.message()));
      }
    }
    return findings;
  }
}
