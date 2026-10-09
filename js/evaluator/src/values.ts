// Values that arrive at run time: fact values and provider values. They are validated against a type
// declaration under the rules of the TYPE area for runtime values (TYPE-70, TYPE-73 to TYPE-75, FACT-35). The
// validators return a normalised copy: decimals in canonical form (NORM-15), sets in universe order (TYPE-51).
// Strings are never normalised (NORM-3) and `null` is never a valid value (TYPE-76).

import type { JsonValue } from './json.js';
import { MAX_SAFE_INTEGER } from './json.js';
import type { TypeDecl } from './model.js';
import { has, hasUnpairedSurrogate, isPlainObject, setOwn } from './text.js';

const DECIMAL = /^[+-]?[0-9]+(?:\.[0-9]+)?$/;
const TIME_HHMM = /^(?:[01][0-9]|2[0-3]):[0-5][0-9]$/;
const IANA_SYNTAX = /^[A-Za-z0-9_+-]+(?:\/[A-Za-z0-9_+-]+)*$/;

/** The result of a check that failed. */
const INVALID = Symbol('invalid');
type Checked = JsonValue | typeof INVALID;

/** Tells whether a string matches the runtime syntax of a decimal (TYPE-24). */
export function isDecimal(text: string): boolean {
  return DECIMAL.test(text);
}

/** Canonicalises a string that matches the decimal syntax (NORM-10). */
export function canonicalDecimal(text: string): string {
  let rest = text;
  let negative = false;
  if (rest.startsWith('+') || rest.startsWith('-')) {
    negative = rest.startsWith('-');
    rest = rest.slice(1);
  }
  const dot = rest.indexOf('.');
  let whole = dot < 0 ? rest : rest.slice(0, dot);
  let fraction = dot < 0 ? '' : rest.slice(dot + 1);
  whole = whole.replace(/^0+(?=.)/, '');
  fraction = fraction.replace(/0+$/, '');
  const body = fraction === '' ? whole : `${whole}.${fraction}`;
  return negative && body !== '0' ? `-${body}` : body;
}

/** The universe positions of a set type, cached because every check of a set value looks members up. */
const universeCache = new WeakMap<readonly string[], ReadonlySet<string>>();

function universeSet(type: TypeDecl): ReadonlySet<string> {
  let set = universeCache.get(type.universe);
  if (set === undefined) {
    set = new Set(type.universe);
    universeCache.set(type.universe, set);
  }
  return set;
}

function checkInteger(type: TypeDecl, value: unknown): Checked {
  if (typeof value !== 'number' || !Number.isInteger(value) || Math.abs(value) > MAX_SAFE_INTEGER) {
    return INVALID;
  }
  if (type.min !== null && value < type.min) return INVALID;
  if (type.max !== null && value > type.max) return INVALID;
  return value === 0 ? 0 : value; // Folds -0 into 0.
}

function checkString(type: TypeDecl, value: unknown): Checked {
  if (typeof value !== 'string' || hasUnpairedSurrogate(value)) return INVALID;
  if (type.format === 'time_hhmm') return TIME_HHMM.test(value) ? value : INVALID;
  if (type.format === 'iana_tz') return IANA_SYNTAX.test(value) ? value : INVALID;
  return value;
}

function checkSet(type: TypeDecl, value: unknown): Checked {
  if (!Array.isArray(value)) return INVALID;
  const universe = universeSet(type);
  const seen = new Set<string>();
  for (const member of value as readonly unknown[]) {
    if (typeof member !== 'string' || !universe.has(member) || seen.has(member)) return INVALID;
    seen.add(member);
  }
  return type.universe.filter((member) => seen.has(member));
}

function checkList(type: TypeDecl, value: unknown): Checked {
  if (!Array.isArray(value) || type.items === null) return INVALID;
  const checked: JsonValue[] = [];
  for (const element of value as readonly unknown[]) {
    const normal = check(type.items, element, false, undefined);
    if (normal === INVALID) return INVALID;
    checked.push(normal);
  }
  return checked;
}

/**
 * Checks one field of an object. Inside a partial object an object-typed field stays partial, except an optional
 * field that the starting value does not set, which must be complete (MERGE-26).
 */
function checkField(field: TypeDecl, value: unknown, partial: boolean, start: unknown): Checked {
  if (!partial || field.type !== 'object') {
    return check(field, value, false, undefined);
  }
  const startSetsIt = isPlainObject(start);
  if (field.optional && !startSetsIt) {
    return check(field, value, false, undefined);
  }
  return check(field, value, true, start);
}

function checkObject(type: TypeDecl, value: unknown, partial: boolean, start: unknown): Checked {
  if (!isPlainObject(value)) return INVALID;
  const checked: { [name: string]: JsonValue } = {};
  for (const name of Object.keys(value)) {
    if (!has(type.fields, name)) return INVALID;
    const startField = isPlainObject(start) && has(start, name) ? start[name] : undefined;
    const normal = checkField(type.fields[name] as TypeDecl, value[name], partial, startField);
    if (normal === INVALID) return INVALID;
    setOwn(checked, name, normal);
  }
  if (!partial) {
    for (const name of Object.keys(type.fields)) {
      if (!(type.fields[name] as TypeDecl).optional && !has(checked, name)) return INVALID;
    }
  }
  return checked;
}

function check(type: TypeDecl, value: unknown, partial: boolean, start: unknown): Checked {
  if (value === null || value === undefined) return INVALID;
  switch (type.type) {
    case 'boolean':
      return typeof value === 'boolean' ? value : INVALID;
    case 'integer':
      return checkInteger(type, value);
    case 'decimal':
      return typeof value === 'string' && DECIMAL.test(value) ? canonicalDecimal(value) : INVALID;
    case 'string':
      return checkString(type, value);
    case 'enum':
      return typeof value === 'string' && type.values.includes(value) ? value : INVALID;
    case 'set':
      return checkSet(type, value);
    case 'list':
      return checkList(type, value);
    case 'object':
      return checkObject(type, value, partial, start);
  }
}

/**
 * Validates a runtime value against a type declaration.
 *
 * @returns a normalised copy, or `undefined` when the value is not valid
 */
export function validateValue(type: TypeDecl, value: unknown): JsonValue | undefined {
  const checked = check(type, value, false, undefined);
  return checked === INVALID ? undefined : checked;
}

/**
 * Validates the value of a `deep_merge` key. A partial object is accepted (SVC-32, MERGE-11, MERGE-12), but a
 * value that sets an optional object field which the starting value does not set must be complete (MERGE-26).
 *
 * @param startingValue the starting value of the key: its default, or its root value (MERGE-10)
 * @returns a normalised copy, or `undefined` when the value is not valid
 */
export function validatePartial(type: TypeDecl, value: unknown, startingValue: unknown): JsonValue | undefined {
  const checked = check(type, value, true, startingValue);
  return checked === INVALID ? undefined : checked;
}

/** Compares two scalar values of one type: the same JSON type and an equal value (NORM-17, NORM-18, RULE-38). */
export function scalarEquals(left: unknown, right: unknown): boolean {
  return left !== null && right !== null && typeof left === typeof right && left === right;
}

/** Copies a value into plain arrays and objects, so a result never shares state with a bundle (EVAL-47). */
export function cloneValue(value: JsonValue): JsonValue {
  if (Array.isArray(value)) {
    return (value as readonly JsonValue[]).map(cloneValue);
  }
  if (typeof value === 'object' && value !== null) {
    const source = value as { readonly [name: string]: JsonValue };
    const copy: { [name: string]: JsonValue } = {};
    for (const name of Object.keys(source)) {
      setOwn(copy, name, cloneValue(source[name] as JsonValue));
    }
    return copy;
  }
  return value;
}
