/**
 * The compiler pipeline and the checks that turn a {@link io.github.tierwise.core.internal.project.SourceSet}
 * into a {@link io.github.tierwise.core.internal.model.Model} (specification sections 1 to 8 and 13).
 *
 * <p>{@link io.github.tierwise.core.internal.check.Compiler} runs the {@link
 * io.github.tierwise.core.internal.check.Stage}s in a fixed order over one {@link
 * io.github.tierwise.core.internal.check.CheckContext}. Each stage reads the sources, adds its findings to
 * the shared {@link io.github.tierwise.core.internal.check.Findings} accumulator, and, when its part of the
 * model is valid, stores that part in the {@link io.github.tierwise.core.internal.check.ModelParts}. A
 * stage skips the checks whose inputs failed to build (FIND-35). The type-declaration parser and the value
 * validator that several stages share live here too.
 */
package io.github.tierwise.core.internal.check;
