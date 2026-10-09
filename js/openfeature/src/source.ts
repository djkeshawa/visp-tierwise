import type { Evaluator } from '@tierwise/evaluator';

/**
 * Where a provider gets its evaluator: one fixed evaluator, or a function that returns the current one on every
 * evaluation. Use a function with a source that swaps bundles, so that a refreshed bundle is seen at once.
 * A function that returns `null` or `undefined` makes the evaluation fail with `PROVIDER_NOT_READY`.
 */
export type EvaluatorSource = Evaluator | (() => Evaluator | null | undefined);

/** Checks the argument of a provider constructor. */
export function checkSource(source: unknown): EvaluatorSource {
  if (typeof source === 'function') return source as EvaluatorSource;
  if (
    typeof source === 'object' &&
    source !== null &&
    typeof (source as Evaluator).resolve === 'function' &&
    typeof (source as Evaluator).decide === 'function'
  ) {
    return source as EvaluatorSource;
  }
  throw new TypeError('a Tierwise provider needs an evaluator or a function that returns one');
}

/** Reads the current evaluator from a source. */
export function currentEvaluator(source: EvaluatorSource): Evaluator | undefined {
  const evaluator = typeof source === 'function' ? source() : source;
  return evaluator ?? undefined;
}
