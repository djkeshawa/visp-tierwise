/**
 * The evaluator: context validation, merge strategies, provider calls, key resolution and the decision
 * engine (specification sections 2, 5, 6, 8, 9 and 11).
 *
 * <p>Everything here works on the immutable {@link io.github.tierwise.core.internal.model.Model} of a
 * loaded bundle. No class in this package reads a domain name: levels, axes, keys, facts and features are
 * only ever data taken from the model.
 */
package io.github.tierwise.core.internal.eval;
