package io.github.tierwise.core.internal.check;

/**
 * An effect that is valid for a feature's {@code returns}. The value is a string, a plain object value,
 * or {@code null} for the null effect of a nullable enum (FEAT-9); the wrapper tells a null effect from an
 * absent one.
 *
 * @param value the effect value
 */
record FeatureEffect(Object value) {
}
