import type { EvaluationContext, JsonValue, Logger, ProviderMetadata, ResolutionDetails } from '@openfeature/core';
import type { Evaluator, Resolved } from '@tierwise/evaluator';
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
 * The OpenFeature web provider over a Tierwise evaluator. It implements the `Provider` interface of
 * `@openfeature/web-sdk`, whose evaluations are synchronous, so it calls `resolveSync` and `decideSync`. A key
 * that needs a service-sourced level for the nodes the context names therefore fails with `E_PROVIDER_REQUIRED`,
 * which maps to the general error code.
 *
 * **Use this provider for display only.** A result computed in a browser can be tampered with by anyone who
 * controls the browser, and the bundle is visible to them. Use it to show what a decision is likely to be, such
 * as hiding a button, and enforce every decision on a server with the same bundle.
 *
 * The mapping is the one of {@link TierwiseServerProvider}: the same flags, context attributes, reasons,
 * metadata and error codes.
 */
export class TierwiseWebProvider {
  readonly metadata: ProviderMetadata = Object.freeze({ name: PROVIDER_NAME });
  readonly runsOn = 'client' as const;

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
  ): ResolutionDetails<boolean> {
    return this.evaluate(flagKey, defaultValue, context, 'boolean');
  }

  resolveStringEvaluation(
    flagKey: string,
    defaultValue: string,
    context: EvaluationContext,
    _logger?: Logger,
  ): ResolutionDetails<string> {
    return this.evaluate(flagKey, defaultValue, context, 'string');
  }

  resolveNumberEvaluation(
    flagKey: string,
    defaultValue: number,
    context: EvaluationContext,
    _logger?: Logger,
  ): ResolutionDetails<number> {
    return this.evaluate(flagKey, defaultValue, context, 'number');
  }

  resolveObjectEvaluation<T extends JsonValue>(
    flagKey: string,
    defaultValue: T,
    context: EvaluationContext,
    _logger?: Logger,
  ): ResolutionDetails<T> {
    return this.evaluate(flagKey, defaultValue, context, 'object');
  }

  private evaluate<T>(
    flag: string,
    fallback: T,
    context: EvaluationContext | undefined,
    target: Target,
  ): ResolutionDetails<T> {
    const evaluator = currentEvaluator(this.source);
    if (evaluator === undefined) return notReadyDetails(fallback);
    try {
      const tierwiseContext = contextOf(context);
      let resolved: Resolved;
      try {
        resolved = evaluator.resolveSync(tierwiseContext, flag);
      } catch (error) {
        if (!isTierwiseError(error) || error.code !== 'E_UNKNOWN_KEY') throw error;
        return this.feature(evaluator, flag, fallback, context, target);
      }
      return resolvedDetails(resolved, fallback, target);
    } catch (error) {
      if (isTierwiseError(error)) return errorDetails(error, fallback);
      throw error;
    }
  }

  private feature<T>(
    evaluator: Evaluator,
    flag: string,
    fallback: T,
    context: EvaluationContext | undefined,
    target: Target,
  ): ResolutionDetails<T> {
    const tierwiseContext = contextOf(context);
    try {
      if (!readsFeatures(target)) {
        const decision = evaluator.decideSync(tierwiseContext, flag);
        return featureOfWrongType(flag, decision, fallback, target);
      }
      const decision = evaluator.decideSync(tierwiseContext, flag, factsOf(context));
      return decisionDetails(flag, decision, fallback, target);
    } catch (error) {
      if (isTierwiseError(error) && error.code === 'E_UNKNOWN_FEATURE') throw notFound(flag);
      throw error;
    }
  }
}
