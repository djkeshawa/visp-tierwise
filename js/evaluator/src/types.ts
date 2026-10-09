// The public types. A result has exactly the JSON shape that section 9 defines (PROV-1 to PROV-86): a
// conditional field whose condition is false is absent, never null (PROV-3).

import type { ErrorCode } from './errors.js';
import type { JsonValue } from './json.js';

export type { ErrorCode, JsonValue };

/** A loaded bundle (EVAL-2). Its content is private to this package, and it cannot be changed (EVAL-46). */
export interface LoadedBundle {
  /** `sha256:` followed by 64 lower-case hexadecimal digits (BUNDLE-5). */
  readonly bundleVersion: string;
  /** The bundle format version, always `"1"` in this release (BUNDLE-137). */
  readonly bundleFormatVersion: '1';
  /** The spec version, always `"0.1"` in this release (BUNDLE-137). */
  readonly specVersion: '0.1';
}

/** The nodes that a call is about: a node ID for each non-root level and each axis (CTX-1). */
export interface Context {
  readonly levels?: Readonly<Record<string, string>>;
  readonly axes?: Readonly<Record<string, string>>;
}

/** A fact value: a boolean, an integer, a string (also a decimal or an enum member). `null` is missing. */
export type FactValue = boolean | number | string | null;

/** The facts for one decision (FACT-22 to FACT-41). */
export interface FactSet {
  /** The single-value facts, by fact name. */
  readonly facts?: Readonly<Record<string, FactValue>>;
  /** The child maps, by child entity name. */
  readonly children?: Readonly<Record<string, ReadonlyArray<Readonly<Record<string, FactValue>>>>>;
}

/** The result of an asynchronous provider function: a mapping from key name to value (SVC-25). */
export type ProviderResult = Readonly<Record<string, unknown>>;

/**
 * The provider of one service-sourced level. It receives the node ID that the context supplies for its level,
 * exactly as supplied (SVC-15). It reports unavailability by throwing or rejecting (SVC-23).
 */
export type ProviderFunction = (nodeId: string) => ProviderResult | PromiseLike<ProviderResult>;

/** One provider registration: the function and its timeout (SVC-38, SVC-48). */
export interface ProviderRegistration {
  readonly fn: ProviderFunction;
  /** A positive integer number of milliseconds. There is no default (SVC-48). */
  readonly timeoutMs: number;
}

/** The type of a listener event (EVAL-41, EVAL-43, SVC-43 to SVC-47). */
export type EvaluationEventType =
  | 'BUNDLE_LOADED'
  | 'SOURCE_UNAVAILABLE'
  | 'SOURCE_INVALID'
  | 'UNKNOWN_KEY'
  | 'IGNORED_ENTRY';

/** An event that the evaluator reports to the listener (EVAL-53). */
export interface EvaluationEvent {
  readonly type: EvaluationEventType;
  readonly bundleVersion: string;
  readonly level?: string;
  readonly node?: string;
  readonly key?: string;
}

export type EvaluationListener = (event: EvaluationEvent) => void;

/** The options of createEvaluator (EVAL-4). */
export interface EvaluatorOptions {
  /** One registration for each service-sourced level of the bundle, by level name (SVC-14). */
  readonly providers?: Readonly<Record<string, ProviderRegistration>>;
  readonly listener?: EvaluationListener;
}

export type ScopeKind = 'level' | 'axis';

/** A scope that admission skipped under `on_unknown_node: ignore` (PROV-54 to PROV-61). */
export interface SkippedScope {
  readonly scope: ScopeKind;
  readonly name: string;
  readonly node: string;
  readonly reason: 'UNKNOWN_NODE';
}

export type ContributionLabel =
  | 'VETO'
  | 'NARROWED'
  | 'WIDENED'
  | 'SET'
  | 'IGNORED'
  | 'UNAVAILABLE'
  | 'INVALID';

/** One element of `contributions` (PROV-19 to PROV-44). */
export interface Contribution {
  readonly scope: ScopeKind;
  readonly name: string;
  /** Present if and only if the scope is not the root level. */
  readonly node?: string;
  /** Present for a value entry and for a failure entry under `deny`. */
  readonly value?: JsonValue;
  /** The contribution label. */
  readonly effect: ContributionLabel;
  /** Present if and only if the entry is a failure entry. */
  readonly onSourceFailure?: 'skip' | 'deny';
}

/** The scope that supplied the value of an order-dependent key (PROV-45 to PROV-53). */
export type Source =
  | 'DEFAULT'
  | { readonly scope: ScopeKind; readonly name: string; readonly node?: string };

/** The result of resolve (PROV-10 to PROV-18). */
export interface Resolved {
  readonly key: string;
  readonly strategy: string;
  /** Present if and only if the key opts into a kind. */
  readonly kind?: string;
  /** The result. `null` only when a `min` or `max` key has no contributing scope (MERGE-6). */
  readonly value: JsonValue;
  readonly bundleVersion: string;
  readonly skippedScopes: readonly SkippedScope[];
  readonly contributions: readonly Contribution[];
  /** Present if and only if the strategy is `override` or `deep_merge`. */
  readonly source?: Source;
}

/** The binding that a decision used (PROV-68 to PROV-70). */
export interface DecisionBinding {
  readonly level: string;
  /** Present if and only if the level is not the root level. */
  readonly node?: string;
}

export type Reason =
  | { readonly type: 'RULE'; readonly id: string; readonly name: string }
  | { readonly type: 'DEFAULT' }
  | { readonly type: 'MISSING_FACT'; readonly facts: readonly string[] };

export interface TraceEntry {
  readonly id: string;
  readonly matched: boolean;
  /** Present if and only if `matched` is false. */
  readonly failed?: { readonly fact: string; readonly op: string };
}

/** The result of decide (PROV-62 to PROV-84). */
export interface Decision {
  readonly feature: string;
  /** The effect: an enum string, `null` for a nullable enum, or an object (PROV-66). */
  readonly effect: JsonValue;
  readonly bundleVersion: string;
  readonly skippedScopes: readonly SkippedScope[];
  readonly binding: DecisionBinding;
  readonly policy: string;
  readonly reason: Reason;
  readonly trace: readonly TraceEntry[];
}

/** An evaluator (EVAL-4). It is fixed when it is created (EVAL-48). */
export interface Evaluator {
  /** The `bundleVersion` of the bundle that this evaluator evaluates. */
  readonly bundleVersion: string;
  /** Resolves a key; calls the providers of service-sourced levels as SVC-17 allows (EVAL-7). */
  resolve(context: Context | undefined, key: string): Promise<Resolved>;
  /** Resolves a key without a provider call; raises E_PROVIDER_REQUIRED where a call is needed (EVAL-9). */
  resolveSync(context: Context | undefined, key: string): Resolved;
  /** Decides a feature. It never calls a provider (SVC-22). */
  decide(context: Context | undefined, feature: string, facts?: FactSet): Promise<Decision>;
  /** The same as decide, returning its result directly (EVAL-12). */
  decideSync(context: Context | undefined, feature: string, facts?: FactSet): Decision;
}
