/**
 * The public API of the Tierwise Java core.
 *
 * <p>{@link io.github.tierwise.core.Tierwise} is the entry point: it compiles a project into canonical
 * bundle bytes, loads a bundle and creates an {@link io.github.tierwise.core.Evaluator}. Findings carry
 * a stable {@link io.github.tierwise.core.Code}, and every evaluation error is raised as a
 * {@link io.github.tierwise.core.TierwiseException} with such a code. Results mirror specification
 * section 9 and serialise to exactly its JSON shape.
 *
 * <p>Runtime values are plain values: {@code Boolean}, {@code Long} (every integer), {@code String}
 * (strings, enums and canonical decimals), {@code List<Object>} (sets and lists) and
 * {@code Map<String, Object>} (objects). They are unmodifiable.
 */
package io.github.tierwise.core;
