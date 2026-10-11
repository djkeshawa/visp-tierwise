import type { EvaluationContext, JsonValue, Logger, ProviderMetadata, ResolutionDetails } from '@openfeature/core';
import type { Evaluator, Resolved } from '@visplabs/evaluator';
import {
  contextOf,
  decisionDetails,
  errorDetails,
  factsOf,
  featureOfWrongType,
  isTierwiseError,
  notFound,
  notReadyDetails,
  readsFeatures,
  resolvedDetails,
} from './mapping.js';
import type { Target } from './mapping.js';
import { PROVIDER_NAME } from './metadata.js';
import { checkSource, currentEvaluator } from './source.js';
import type { EvaluatorSource } from './source.js';

/**
 * The OpenFeature server provider over a Tierwise evaluator. It implements the `Provider` interface of
 * `@openfeature/server-sdk` and calls the evaluator's asynchronous `resolve` and `decide`, so it also serves
 * keys at service-sourced levels.
 *
 * A flag key is the declared name of a Tierwise key or feature:
 *
 * - boolean evaluation: a boolean key;
 * - string evaluation: a string, enum or decimal key, or a feature with an enum effect;
 * - number evaluation: an integer key;
 * - object evaluation: a set, list or object key, or a feature with an object effect.
 *
 * A name is looked up as a key first and as a feature second. The Tierwise context comes from the structure
 * attributes `tierwise.levels` and `tierwise.axes` of the evaluation context, and the facts of a decision from
 * `tierwise.facts`. The reason, the flag metadata and the error codes follow `docs/openfeature-mapping.md`.
 */
export class TierwiseServerProvider {
  readonly metadata: ProviderMetadata = Object.freeze({ name: PROVIDER_NAME });
  readonly runsOn = 'server' as const;

  private readonly source: EvaluatorSource;

  /**
   * @param source one evaluator, or a function that returns the current evaluator on every evaluation
   */
  constructor(source: EvaluatorSource) {
    this.source = checkSource(source);
  }

  resolveBooleanEvaluation(
    flagKey: string,
    defaultValue: boolean,
    context: EvaluationContext,
    _logger?: Logger,
  ): Promise<ResolutionDetails<boolean>> {
    return this.evaluate(flagKey, defaultValue, context, 'boolean');
  }

  resolveStringEvaluation(
    flagKey: string,
    defaultValue: string,
    context: EvaluationContext,
    _logger?: Logger,
  ): Promise<ResolutionDetails<string>> {
    return this.evaluate(flagKey, defaultValue, context, 'string');
  }

  resolveNumberEvaluation(
    flagKey: string,
    defaultValue: number,
    context: EvaluationContext,
    _logger?: Logger,
  ): Promise<ResolutionDetails<number>> {
    return this.evaluate(flagKey, defaultValue, context, 'number');
  }

  resolveObjectEvaluation<T extends JsonValue>(
    flagKey: string,
    defaultValue: T,
    context: EvaluationContext,
    _logger?: Logger,
  ): Promise<ResolutionDetails<T>> {
    return this.evaluate(flagKey, defaultValue, context, 'object');
  }

  private async evaluate<T>(
    flag: string,
    fallback: T,
    context: EvaluationContext | undefined,
    target: Target,
  ): Promise<ResolutionDetails<T>> {
    const evaluator = currentEvaluator(this.source);
    if (evaluator === undefined) return notReadyDetails(fallback);
    try {
      const tierwiseContext = contextOf(context);
      let resolved: Resolved;
      try {
        resolved = await evaluator.resolve(tierwiseContext, flag);
      } catch (error) {
        if (!isTierwiseError(error) || error.code !== 'E_UNKNOWN_KEY') throw error;
        return await this.feature(evaluator, flag, fallback, context, target);
      }
      return resolvedDetails(resolved, fallback, target);
    } catch (error) {
      if (isTierwiseError(error)) return errorDetails(error, fallback);
      throw error;
    }
  }

  private async feature<T>(
    evaluator: Evaluator,
    flag: string,
    fallback: T,
    context: EvaluationContext | undefined,
    target: Target,
  ): Promise<ResolutionDetails<T>> {
    const tierwiseContext = contextOf(context);
    try {
      if (!readsFeatures(target)) {
        // A feature of that name makes the request a type mismatch; no name makes the flag missing.
        const decision = await evaluator.decide(tierwiseContext, flag);
        return featureOfWrongType(flag, decision, fallback, target);
      }
      const decision = await evaluator.decide(tierwiseContext, flag, factsOf(context));
      return decisionDetails(flag, decision, fallback, target);
    } catch (error) {
      if (isTierwiseError(error) && error.code === 'E_UNKNOWN_FEATURE') throw notFound(flag);
      throw error;
    }
  }
}
