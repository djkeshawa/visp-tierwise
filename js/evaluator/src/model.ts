// The typed model of a loaded bundle. The loader builds it from the validated content and freezes it
// deeply (EVAL-46). Collections that are keyed by a user-chosen name are null-prototype records, so a name
// such as "constructor" is data; use `has` from text.ts to look a name up.

import type { JsonValue } from './json.js';

export type TypeName = 'boolean' | 'integer' | 'decimal' | 'string' | 'enum' | 'set' | 'list' | 'object';

export type Strategy = 'all' | 'any' | 'intersect' | 'union' | 'min' | 'max' | 'override' | 'deep_merge';

/** A frozen record from a name to a value. */
export type Dict<T> = { readonly [name: string]: T };

/** A type declaration (section 4). A member that the type does not define is null or empty. */
export interface TypeDecl {
  readonly type: TypeName;
  readonly min: number | null;
  readonly max: number | null;
  readonly format: 'time_hhmm' | 'iana_tz' | null;
  readonly values: readonly string[];
  readonly universe: readonly string[];
  readonly items: TypeDecl | null;
  readonly fields: Dict<TypeDecl>;
  readonly optional: boolean;
}

export interface ScopeDef {
  readonly values: 'files' | 'service';
  /** True for a `nodes: open` catalog. */
  readonly open: boolean;
  readonly nodes: readonly string[];
  readonly onUnknownNode: 'error' | 'ignore' | null;
}

export interface Hierarchy {
  readonly levels: readonly string[];
  readonly axes: readonly string[];
  /** One definition for each non-root level and each axis. */
  readonly scopes: Dict<ScopeDef>;
}

export interface EntityDef {
  readonly belongsTo: readonly string[];
  readonly hasMany: readonly string[];
}

export interface FactDef {
  readonly entity: string;
  readonly type: TypeDecl;
}

export interface KeyDef {
  readonly type: TypeDecl;
  readonly combine: Strategy;
  /** The declared default; null when the key declares none (no type admits null, TYPE-76). */
  readonly defaultValue: JsonValue;
  readonly settableAt: readonly string[];
  readonly combineAcrossAxes: 'axis_wins' | 'hierarchy_wins' | 'max' | 'min' | null;
  readonly onSourceFailure: 'skip' | 'deny' | null;
  readonly kind: string | null;
}

/** Values by scope name, then node key ("" for the root level), then key name. */
export type ValuesByScope = Dict<Dict<Dict<JsonValue>>>;

export interface Returns {
  readonly objectForm: boolean;
  readonly enumValues: readonly string[];
  readonly nullable: boolean;
  readonly fields: Dict<TypeDecl>;
}

export interface SettingDef {
  readonly kind: 'valueOf' | 'listOf';
  readonly fact: string;
  readonly defaultValue: JsonValue;
}

export type Operator = 'eq' | 'in' | 'all_in' | 'any_in';

export interface ConditionDef {
  readonly fact: string;
  readonly op: Operator;
  /** The literal operand, or the `$name` string when the operand is a setting reference. */
  readonly value: JsonValue;
  /** The referenced setting name, or null for a literal operand. */
  readonly reference: string | null;
}

export interface RuleDef {
  readonly id: string;
  readonly name: string;
  readonly when: readonly ConditionDef[];
  readonly then: JsonValue;
}

export interface PolicyDef {
  readonly settings: Dict<SettingDef>;
  readonly defaultEffect: JsonValue;
  readonly requiredFacts: readonly string[];
  readonly rules: readonly RuleDef[];
}

export interface BindingDef {
  readonly policy: string;
  readonly settings: Dict<JsonValue>;
}

export interface FeatureDef {
  readonly decidedPer: string;
  readonly returns: Returns;
  readonly ifFactMissing: JsonValue;
  readonly bindingSettableAt: readonly string[];
  readonly policies: Dict<PolicyDef>;
  /** Bindings by level name, then node key ("" for the root level). */
  readonly bindings: Dict<Dict<BindingDef>>;
}

export interface Model {
  readonly hierarchy: Hierarchy;
  readonly entities: Dict<EntityDef>;
  readonly facts: Dict<FactDef>;
  readonly keys: Dict<KeyDef>;
  readonly values: ValuesByScope;
  readonly features: Dict<FeatureDef>;
}

/** Freezes a value and everything reachable from it. */
export function deepFreeze<T>(value: T): T {
  if (typeof value === 'object' && value !== null && !Object.isFrozen(value)) {
    Object.freeze(value);
    for (const name of Object.keys(value)) {
      deepFreeze((value as { readonly [name: string]: unknown })[name]);
    }
  }
  return value;
}

/** Creates a null-prototype record from entries, so that any name is data. */
export function record<T>(entries: Iterable<readonly [string, T]>): { [name: string]: T } {
  const out: { [name: string]: T } = Object.create(null) as { [name: string]: T };
  for (const [name, value] of entries) {
    Object.defineProperty(out, name, { value, writable: true, enumerable: true, configurable: true });
  }
  return out;
}
