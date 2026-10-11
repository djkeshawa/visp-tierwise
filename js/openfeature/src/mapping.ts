// The mapping between Tierwise results and OpenFeature resolution details, shared by the server provider and the
// web provider (docs/openfeature-mapping.md). Everything here is pure: the providers do the lookups and hand
// the results to these functions, so both providers answer identically.

import { ErrorCode, StandardResolutionReasons } from '@openfeature/core';
import type { EvaluationContext, FlagMetadata, ResolutionDetails } from '@openfeature/core';
import { TierwiseError } from '@visporg/evaluator';
import type { Context, Decision, FactSet, JsonValue, Resolved } from '@visporg/evaluator';
import { AXES_ATTRIBUTE, FACTS_ATTRIBUTE, LEVELS_ATTRIBUTE, TierwiseMetadata } from './metadata.js';

/** The type an OpenFeature evaluation asks for. */
export type Target = 'boolean' | 'string' | 'number' | 'object';

/** Tells whether a feature can answer an evaluation of this type: only string and object evaluations can. */
export function readsFeatures(target: Target): boolean {
  return target === 'string' || target === 'object';
}

// ---------------------------------------------------------------------------
// Context
// ---------------------------------------------------------------------------

/**
 * Reads the Tierwise context from `tierwise.levels` and `tierwise.axes`. The values are handed to the evaluator
 * as they are, so that the evaluator alone judges their shape and raises `E_CONTEXT_INVALID` for a malformed
 * one. The targeting key and every other attribute are ignored.
 */
export function contextOf(source: EvaluationContext | undefined): Context {
  const out: Record<string, unknown> = {};
  const levels = source?.[LEVELS_ATTRIBUTE];
  const axes = source?.[AXES_ATTRIBUTE];
  if (levels !== undefined) out['levels'] = levels;
  if (axes !== undefined) out['axes'] = axes;
  return out as Context;
}

/**
 * Reads the FactSet from `tierwise.facts`. The value is handed to the evaluator as it is, so that the evaluator
 * alone judges it and raises `E_FACTS_INVALID` for a malformed one. An absent attribute is `undefined`, which
 * is the empty FactSet.
 */
export function factsOf(source: EvaluationContext | undefined): FactSet | undefined {
  const facts = source?.[FACTS_ATTRIBUTE];
  return facts === undefined ? undefined : (facts as FactSet);
}

// ---------------------------------------------------------------------------
// Errors
// ---------------------------------------------------------------------------

/** Tells whether a thrown value is a Tierwise evaluation error, whichever copy of the evaluator raised it. */
export function isTierwiseError(error: unknown): error is TierwiseError {
  return error instanceof Error && error.name === 'TierwiseError' && typeof (error as { code?: unknown }).code === 'string';
}

/**
 * Maps a Tierwise evaluation error code to an OpenFeature error code. An unknown key or feature is a missing
 * flag. An invalid context, an unknown scope or node, and invalid facts are an invalid context. Every other
 * code is a general error.
 */
export function errorCodeOf(code: string): ErrorCode {
  switch (code) {
    case 'E_UNKNOWN_KEY':
    case 'E_UNKNOWN_FEATURE':
      return ErrorCode.FLAG_NOT_FOUND;
    case 'E_CONTEXT_INVALID':
    case 'E_UNKNOWN_SCOPE':
    case 'E_UNKNOWN_NODE':
    case 'E_FACTS_INVALID':
      return ErrorCode.INVALID_CONTEXT;
    default:
      return ErrorCode.GENERAL;
  }
}

/** The resolution for a Tierwise error: the caller's default value, reason ERROR and the mapped code. */
export function errorDetails<T>(error: TierwiseError, fallback: T): ResolutionDetails<T> {
  return {
    value: fallback,
    reason: StandardResolutionReasons.ERROR,
    errorCode: errorCodeOf(error.code),
    errorMessage: error.message,
    flagMetadata: { [TierwiseMetadata.TIERWISE_CODE]: error.code },
  };
}

/** The resolution when no evaluator is available. */
export function notReadyDetails<T>(fallback: T): ResolutionDetails<T> {
  return {
    value: fallback,
    reason: StandardResolutionReasons.ERROR,
    errorCode: ErrorCode.PROVIDER_NOT_READY,
    errorMessage: 'the evaluator source returned no evaluator',
  };
}

/** The error that reports a name that is neither a key nor a feature. */
export function notFound(flag: string): TierwiseError {
  return new TierwiseError('E_UNKNOWN_FEATURE', `no key or feature is named '${flag}'`);
}

function typeMismatch<T>(message: string, fallback: T, flagMetadata: FlagMetadata): ResolutionDetails<T> {
  return {
    value: fallback,
    reason: StandardResolutionReasons.ERROR,
    errorCode: ErrorCode.TYPE_MISMATCH,
    errorMessage: message,
    flagMetadata,
  };
}

// ---------------------------------------------------------------------------
// Value conversion
// ---------------------------------------------------------------------------

type Converted = { readonly ok: true; readonly value: JsonValue } | { readonly ok: false; readonly message: string };

function kindOf(value: JsonValue): string {
  if (typeof value === 'boolean') return 'a boolean';
  if (typeof value === 'number') return 'an integer';
  if (typeof value === 'string') return 'a string';
  if (Array.isArray(value)) return 'a list';
  return 'an object';
}

/**
 * Checks that a result can be read as the requested type. A boolean evaluation takes a boolean, a string
 * evaluation a string, a number evaluation an integer (the evaluator only holds integers within +/-(2^53 - 1))
 * and an object evaluation a list or an object.
 */
export function convert(target: Target, value: JsonValue): Converted {
  const ok =
    target === 'boolean'
      ? typeof value === 'boolean'
      : target === 'string'
        ? typeof value === 'string'
        : target === 'number'
          ? typeof value === 'number' && Number.isSafeInteger(value)
          : Array.isArray(value) || (typeof value === 'object' && value !== null);
  return ok ? { ok: true, value } : { ok: false, message: `the value is ${kindOf(value)}, not ${target}` };
}

// ---------------------------------------------------------------------------
// Keys
// ---------------------------------------------------------------------------

/**
 * Maps a key's provenance to a reason. A key that no scope supplied a value for (an `override` or `deep_merge`
 * key at its declared default, or a commutative key at its identity) is DEFAULT. A value that comes only from
 * the root level is STATIC, because it does not depend on the context. Anything else depends on a level or axis
 * node that the context named, so it is TARGETING_MATCH.
 */
export function keyReason(resolved: Resolved): string {
  if (resolved.source !== undefined) {
    if (resolved.source === 'DEFAULT') return StandardResolutionReasons.DEFAULT;
    return resolved.source.node !== undefined ? StandardResolutionReasons.TARGETING_MATCH : StandardResolutionReasons.STATIC;
  }
  if (resolved.contributions.length === 0) return StandardResolutionReasons.DEFAULT;
  return resolved.contributions.some((entry) => entry.node !== undefined)
    ? StandardResolutionReasons.TARGETING_MATCH
    : StandardResolutionReasons.STATIC;
}

function keyMetadata(resolved: Resolved): Record<string, string | boolean> {
  const metadata: Record<string, string | boolean> = {
    [TierwiseMetadata.BUNDLE_VERSION]: resolved.bundleVersion,
    [TierwiseMetadata.FLAG_KIND]: TierwiseMetadata.KEY,
    [TierwiseMetadata.STRATEGY]: resolved.strategy,
  };
  if (resolved.kind !== undefined) metadata[TierwiseMetadata.KIND] = resolved.kind;
  if (resolved.source !== undefined) {
    if (resolved.source === 'DEFAULT') {
      metadata[TierwiseMetadata.SOURCE] = 'DEFAULT';
    } else {
      metadata[TierwiseMetadata.SOURCE] = `${resolved.source.scope}:${resolved.source.name}`;
      if (resolved.source.node !== undefined) metadata[TierwiseMetadata.SOURCE_NODE] = resolved.source.node;
    }
  }
  metadata[TierwiseMetadata.CONTRIBUTIONS] = JSON.stringify(
    resolved.contributions.map((entry) => ({
      scope: entry.scope,
      name: entry.name,
      ...(entry.node !== undefined ? { node: entry.node } : {}),
      effect: entry.effect,
      ...(entry.onSourceFailure !== undefined ? { onSourceFailure: entry.onSourceFailure } : {}),
    })),
  );
  addSkippedScopes(metadata, resolved.skippedScopes);
  return metadata;
}

function addSkippedScopes(metadata: Record<string, string | boolean>, skipped: Resolved['skippedScopes']): void {
  if (skipped.length === 0) return;
  metadata[TierwiseMetadata.SKIPPED_SCOPES] = JSON.stringify(
    skipped.map((entry) => ({ scope: entry.scope, name: entry.name, node: entry.node, reason: entry.reason })),
  );
}

/** Maps the result of `resolve` to resolution details for an evaluation of the given type. */
export function resolvedDetails<T>(resolved: Resolved, fallback: T, target: Target): ResolutionDetails<T> {
  const metadata = keyMetadata(resolved);
  if (resolved.value === null) {
    metadata[TierwiseMetadata.VALUE_ABSENT] = true;
    return { value: fallback, reason: StandardResolutionReasons.DEFAULT, flagMetadata: metadata };
  }
  const converted = convert(target, resolved.value);
  if (!converted.ok) {
    return typeMismatch(`the key '${resolved.key}' cannot be read as ${target}: ${converted.message}`, fallback, metadata);
  }
  return { value: converted.value as T, reason: keyReason(resolved), flagMetadata: metadata };
}

// ---------------------------------------------------------------------------
// Features
// ---------------------------------------------------------------------------

function decisionMetadata(decision: Decision): Record<string, string | boolean> {
  const metadata: Record<string, string | boolean> = {
    [TierwiseMetadata.BUNDLE_VERSION]: decision.bundleVersion,
    [TierwiseMetadata.FLAG_KIND]: TierwiseMetadata.FEATURE,
    [TierwiseMetadata.DECISION_REASON]: decision.reason.type,
    [TierwiseMetadata.POLICY]: decision.policy,
    [TierwiseMetadata.BINDING_LEVEL]: decision.binding.level,
  };
  if (decision.binding.node !== undefined) metadata[TierwiseMetadata.BINDING_NODE] = decision.binding.node;
  if (decision.reason.type === 'RULE') {
    metadata[TierwiseMetadata.RULE_ID] = decision.reason.id;
    metadata[TierwiseMetadata.RULE_NAME] = decision.reason.name;
  }
  if (decision.reason.type === 'MISSING_FACT') {
    metadata[TierwiseMetadata.MISSING_FACTS] = JSON.stringify(decision.reason.facts);
  }
  addSkippedScopes(metadata, decision.skippedScopes);
  return metadata;
}

/** Maps the result of `decide` to resolution details for a string or object evaluation. */
export function decisionDetails<T>(flag: string, decision: Decision, fallback: T, target: Target): ResolutionDetails<T> {
  const metadata = decisionMetadata(decision);
  if (decision.effect === null) {
    metadata[TierwiseMetadata.EFFECT_NULL] = true;
    return { value: fallback, reason: StandardResolutionReasons.DEFAULT, flagMetadata: metadata };
  }
  const converted = convert(target, decision.effect);
  if (!converted.ok) {
    return typeMismatch(`the feature '${flag}' cannot be read as ${target}: ${converted.message}`, fallback, metadata);
  }
  const rule = decision.reason.type === 'RULE';
  return {
    value: converted.value as T,
    reason: rule ? StandardResolutionReasons.TARGETING_MATCH : StandardResolutionReasons.DEFAULT,
    ...(decision.reason.type === 'RULE' ? { variant: decision.reason.id } : {}),
    flagMetadata: metadata,
  };
}

/**
 * The resolution for a feature that was asked for as a type that features cannot have (a boolean or number
 * evaluation): a type mismatch, not a missing flag.
 */
export function featureOfWrongType<T>(flag: string, decision: Decision, fallback: T, target: Target): ResolutionDetails<T> {
  return typeMismatch(
    `'${flag}' is a feature, which cannot be read as ${target}; read it as a string or object flag`,
    fallback,
    decisionMetadata(decision),
  );
}
