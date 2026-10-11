/**
 * The immutable compiled model.
 *
 * <p>The compiler builds a {@link io.github.visplabs.tierwise.core.internal.model.Model} from sources and writes
 * it as a bundle. The bundle reader builds the same {@code Model} from bundle bytes, and the evaluator
 * only ever sees a {@code Model} that came from a bundle. The records mirror the layout of the bundle
 * {@code content} (specification section 10), so every compile-time-only datum (descriptions, kinds,
 * source positions) is absent here by design.
 *
 * <p>Runtime values are plain values: {@code Boolean}, {@code Long}, {@code String},
 * {@code List<Object>} and {@code Map<String, Object>}, always unmodifiable. The one place where
 * {@code null} appears is an <em>effect</em> of a nullable enum feature (BUNDLE-24).
 */
package io.github.visplabs.tierwise.core.internal.model;
