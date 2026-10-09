/**
 * The lint rules of specification section 13.6 (LINT-1 to LINT-50).
 *
 * <p>{@link io.github.tierwise.core.internal.lint.LintRunner} resolves the severity of each rule from the lint
 * settings of the presets and the manifest, and runs every rule that is not {@code off}. Each rule is a
 * {@link io.github.tierwise.core.internal.lint.LintRule} that reads a {@link
 * io.github.tierwise.core.internal.lint.LintContext}: the loaded sources, the assembled model, the valid
 * examples, a {@link io.github.tierwise.core.internal.lint.SourceIndex} of the raw declarations, which locates
 * each finding in its source, and the items of the generated catalogs, which the check stage reads because the
 * package reads no project file (LINT-19). The only check-stage type the package reads is
 * {@link io.github.tierwise.core.internal.check.NamingRegex}, the one translator of naming patterns
 * (LINT-17), so that lint and the engine cannot disagree on a pattern. The compiler stage that owns the lint
 * step (in {@code check}) is the only one that connects the package to the findings accumulator.
 */
package io.github.tierwise.core.internal.lint;
