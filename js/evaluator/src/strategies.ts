// The merge strategies (section 5): one pure function per strategy and axis combination. A commutative
// strategy combines the contributed values in any order (MERGE-17, MERGE-18). An order-dependent strategy
// forms its result from the default, the hierarchy result and the axis result (AXIS-7).

import type { JsonValue } from './json.js';
import type { Strategy, TypeDecl } from './model.js';
import { has, setOwn } from './text.js';
import type { ContributionLabel } from './types.js';
import { cloneValue } from './values.js';

/** Tells whether a strategy is commutative (MERGE-1). */
export function isCommutative(strategy: Strategy): boolean {
  return strategy !== 'override' && strategy !== 'deep_merge';
}

/**
 * The identity of a commutative strategy: the result when no scope contributes (MERGE-5). It is `null` for
 * `min` and `max`, whose result is then absent (MERGE-6).
 */
export function identity(strategy: Strategy, type: TypeDecl): JsonValue {
  switch (strategy) {
    case 'all':
      return true;
    case 'any':
      return false;
    case 'intersect':
      return [...type.universe];
    case 'union':
      return [];
    default:
      return null;
  }
}

function members(value: JsonValue): readonly string[] {
  return value as readonly string[];
}

/** Combines the contributed values of a commutative strategy (MERGE-17). The list is not empty. */
export function combine(strategy: Strategy, type: TypeDecl, values: readonly JsonValue[]): JsonValue {
  switch (strategy) {
    case 'all':
      return values.every((value) => value === true);
    case 'any':
      return values.some((value) => value === true);
    case 'intersect': {
      const kept = type.universe.filter((member) => values.every((value) => members(value).includes(member)));
      return kept;
    }
    case 'union': {
      const joined = new Set<string>();
      values.forEach((value) => members(value).forEach((member) => joined.add(member)));
      return type.universe.filter((member) => joined.has(member));
    }
    case 'min':
      return Math.min(...(values as readonly number[]));
    case 'max':
      return Math.max(...(values as readonly number[]));
    default:
      throw new TypeError(`${strategy} is not commutative`);
  }
}

/**
 * The deny value of a narrowing strategy (SVC-9): `false` for `all`, the empty set for `intersect` and the `min`
 * member of the type for `min`. It is `undefined` when the strategy has none.
 */
export function denyValue(strategy: Strategy, type: TypeDecl): JsonValue | undefined {
  switch (strategy) {
    case 'all':
      return false;
    case 'intersect':
      return [];
    case 'min':
      return type.min === null ? undefined : type.min;
    default:
      return undefined;
  }
}

/** The contribution label of a value entry, from its value alone (PROV-37, PROV-38). */
export function label(strategy: Strategy, type: TypeDecl, value: JsonValue): ContributionLabel {
  switch (strategy) {
    case 'all':
      return value === true ? 'IGNORED' : 'VETO';
    case 'any':
      return value === true ? 'WIDENED' : 'IGNORED';
    case 'intersect':
      return members(value).length >= type.universe.length ? 'IGNORED' : 'NARROWED';
    case 'union':
      return members(value).length === 0 ? 'IGNORED' : 'WIDENED';
    case 'min':
      return 'NARROWED';
    case 'max':
      return 'WIDENED';
    default:
      return 'SET';
  }
}

/** A value that one scope contributes for an order-dependent key. */
export interface Contributed<S> {
  readonly scope: S;
  readonly value: JsonValue;
}

/** The result of an order-dependent key and the scope that supplied it; the scope is null for the default. */
export interface Combined<S> {
  /** The value; `null` only when it is absent, which an order-dependent key cannot have (MERGE-7). */
  readonly value: JsonValue;
  readonly source: S | null;
}

type AcrossAxes = 'axis_wins' | 'hierarchy_wins' | 'max' | 'min';

/**
 * The result of an `override` key (MERGE-19 to MERGE-21, AXIS-7, PROV-50). `levels` and `axes` hold the
 * contributed values in evaluation-path order; the last of each is the hierarchy result and the axis result.
 */
export function combineOverride<S>(
  defaultValue: JsonValue,
  levels: readonly Contributed<S>[],
  axes: readonly Contributed<S>[],
  across: AcrossAxes | null,
): Combined<S> {
  const hierarchy = levels[levels.length - 1];
  const axis = axes[axes.length - 1];
  if (hierarchy === undefined && axis === undefined) {
    return { value: cloneValue(defaultValue), source: null };
  }
  let winner: Contributed<S>;
  if (hierarchy === undefined) {
    winner = axis as Contributed<S>;
  } else if (axis === undefined) {
    winner = hierarchy;
  } else {
    switch (across ?? 'axis_wins') {
      case 'hierarchy_wins':
        winner = hierarchy;
        break;
      case 'max':
        // On a tie the hierarchy wins (AXIS-8, PROV-51).
        winner = (axis.value as number) > (hierarchy.value as number) ? axis : hierarchy;
        break;
      case 'min':
        winner = (axis.value as number) < (hierarchy.value as number) ? axis : hierarchy;
        break;
      default:
        winner = axis;
    }
  }
  return { value: cloneValue(winner.value), source: winner.scope };
}

function isObject(value: JsonValue | undefined): value is { readonly [name: string]: JsonValue } {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/**
 * The merge of a base object and an overriding object (MERGE-22): a field that is declared as an object is
 * merged when both sides hold an object, and any other field of the overriding object replaces the earlier one.
 */
export function mergeObjects(
  type: TypeDecl,
  base: { readonly [name: string]: JsonValue },
  over: { readonly [name: string]: JsonValue },
): { [name: string]: JsonValue } {
  const merged: { [name: string]: JsonValue } = {};
  for (const name of Object.keys(base)) {
    setOwn(merged, name, base[name]);
  }
  for (const name of Object.keys(over)) {
    const declared = has(type.fields, name) ? type.fields[name] : undefined;
    const earlier = has(merged, name) ? merged[name] : undefined;
    const later = over[name] as JsonValue;
    if (declared !== undefined && declared.type === 'object' && isObject(earlier) && isObject(later)) {
      setOwn(merged, name, mergeObjects(declared, earlier, later));
    } else {
      setOwn(merged, name, later);
    }
  }
  return merged;
}

interface Term<S> {
  readonly value: JsonValue;
  readonly source: S;
}

/** The fold of the values that the levels (or the axes) contribute, in evaluation-path order (MERGE-23, MERGE-24). */
function fold<S>(type: TypeDecl, contributions: readonly Contributed<S>[]): Term<S> | null {
  const first = contributions[0];
  const last = contributions[contributions.length - 1];
  if (first === undefined || last === undefined) {
    return null;
  }
  let value = first.value;
  for (const next of contributions.slice(1)) {
    value = mergeObjects(type, value as { readonly [name: string]: JsonValue }, next.value as { readonly [name: string]: JsonValue });
  }
  return { value, source: last.scope };
}

/**
 * The result of a `deep_merge` key: `D + H + A` under `axis_wins` and `D + A + H` under `hierarchy_wins`, where
 * an absent term is omitted (AXIS-7, MERGE-25, PROV-53). `source` is the last present term.
 */
export function combineDeepMerge<S>(
  type: TypeDecl,
  defaultValue: JsonValue,
  levels: readonly Contributed<S>[],
  axes: readonly Contributed<S>[],
  across: AcrossAxes | null,
): Combined<S> {
  const hierarchy = fold(type, levels);
  const axis = fold(type, axes);
  const hierarchyLast = across === 'hierarchy_wins';
  const terms = hierarchyLast ? [axis, hierarchy] : [hierarchy, axis];
  let value: JsonValue = defaultValue;
  let source: S | null = null;
  for (const term of terms) {
    if (term !== null) {
      value =
        value === null
          ? term.value
          : mergeObjects(type, value as { readonly [name: string]: JsonValue }, term.value as { readonly [name: string]: JsonValue });
      source = term.source;
    }
  }
  return { value: cloneValue(value), source };
}
