/**
 * An OpenFeature provider over the Tierwise evaluator.
 *
 * <p>{@link io.github.tierwise.openfeature.TierwiseProvider} answers OpenFeature flag evaluations from an
 * {@link io.github.tierwise.core.Evaluator}. A flag is the name of a key or of a feature. The evaluation
 * context carries the Tierwise context in the attributes {@code tierwise.levels} and {@code tierwise.axes}
 * and the facts of a decision in {@code tierwise.facts}. Reasons, metadata and error codes follow
 * {@code docs/openfeature-mapping.md}, and {@link io.github.tierwise.openfeature.TierwiseMetadata} names
 * the flag metadata entries.
 */
package io.github.tierwise.openfeature;
