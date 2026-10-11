package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.internal.lint.LintExample;
import java.util.List;

/**
 * The valid examples of a project, published for the lint stage (LINT-29 to LINT-35).
 *
 * @param examples the examples whose checks all passed
 */
record ExampleSet(List<LintExample> examples) {
}
