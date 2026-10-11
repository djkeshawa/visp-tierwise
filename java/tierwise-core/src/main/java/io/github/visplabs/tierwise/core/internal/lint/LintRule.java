package io.github.visplabs.tierwise.core.internal.lint;

import java.util.List;

/**
 * One lint rule of section 13.6. A rule reports the places it flags. The runner gives them the resolved
 * severity and drops the rule when that severity is {@code off} (LINT-6, LINT-7).
 */
public interface LintRule {

  /**
   * Returns the rule identifier of LINT-1, for example {@code values/no-op} or {@code naming/key}.
   *
   * @return the identifier
   */
  String id();

  /**
   * Checks the project.
   *
   * @param context the sources, the model and the examples
   * @return the places that the rule flags, in any order; an empty list when none
   */
  List<LintHit> check(LintContext context);
}
