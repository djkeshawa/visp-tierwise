// The structural checks of load bundle (EVAL-14 step 4, BUNDLE-14 to BUNDLE-136) and the typed model that
// they produce. Every check here is also made by the Java BundleReader, so both loaders reject the same
// bundles (ADR 0018, BUNDLE-118). The few checks that go beyond the Java reader are marked "Beyond the
// Java reader" and listed in the README.
//
// Every failure is E_BUNDLE_INTEGRITY. The readers run in the order of section 10, because each one needs
// the declarations that come before it.

import { integrity } from './errors.js';
import type { JsonValue } from './json.js';
import {
  type BindingDef,
  type ConditionDef,
  type Dict,
  type EntityDef,
  type FactDef,
  type FeatureDef,
  type Hierarchy,
  type KeyDef,
  type Model,
  type Operator,
  type PolicyDef,
  type Returns,
  type RuleDef,
  type ScopeDef,
  type SettingDef,
  type Strategy,
  type TypeDecl,
  type TypeName,
  type ValuesByScope,
  deepFreeze,
  record,
} from './model.js';
import {
  compareCodePoints,
  has,
  isNodeId,
  isPlainObject,
  strictlyIncreasing,
} from './text.js';
import { MAX_SAFE_INTEGER } from './json.js';

type Obj = { readonly [name: string]: unknown };

function fail(message: string): never {
  throw integrity(message);
}

// ---------------------------------------------------------------------------
// Typed accessors for the parsed JSON
// ---------------------------------------------------------------------------

function object(value: unknown, at: string): Obj {
  if (!isPlainObject(value)) fail(`${at} must be an object`);
  return value as Obj;
}

function array(value: unknown, at: string): readonly unknown[] {
  if (!Array.isArray(value)) fail(`${at} must be an array`);
  return value as readonly unknown[];
}

function string(value: unknown, at: string): string {
  if (typeof value !== 'string') fail(`${at} must be a string`);
  return value as string;
}

function stringArray(value: unknown, at: string): string[] {
  return array(value, at).map((element, index) => string(element, `${at}[${index}]`));
}

function integer(value: unknown, at: string): number {
  if (typeof value !== 'number' || !Number.isInteger(value)) fail(`${at} must be an integer`);
  return value as number;
}

function oneOf<T extends string>(value: unknown, accepted: readonly T[], at: string): T {
  const text = string(value, at);
  if (!(accepted as readonly string[]).includes(text)) fail(`${at} has the value ${text}, which is not accepted`);
  return text as T;
}

/** Requires the exact member set of an object (BUNDLE-14, BUNDLE-53, BUNDLE-76 and the other closed sets). */
function members(map: Obj, at: string, required: readonly string[], optional: readonly string[]): void {
  for (const name of Object.keys(map)) {
    if (!required.includes(name) && !optional.includes(name)) {
      fail(`${at} has the member ${name}, which the format does not allow`);
    }
  }
  for (const name of required) {
    if (!has(map, name)) fail(`${at} lacks the required member ${name}`);
  }
}

function distinctNonEmpty(value: unknown, at: string): string[] {
  const names = stringArray(value, at);
  if (names.length === 0) fail(`${at} is empty (TYPE-42)`);
  if (new Set(names).size !== names.length) fail(`${at} repeats a string (TYPE-43)`);
  return names;
}

// ---------------------------------------------------------------------------
// Orders of BUNDLE-75 and BUNDLE-152
// ---------------------------------------------------------------------------

/** Compares two scalar literals of one type: false before true, integers numerically, strings by code point. */
function compareScalars(a: unknown, b: unknown): number {
  if (typeof a === 'boolean' && typeof b === 'boolean') return a === b ? 0 : a ? 1 : -1;
  if (typeof a === 'number' && typeof b === 'number') return a === b ? 0 : a < b ? -1 : 1;
  if (typeof a === 'string' && typeof b === 'string') return compareCodePoints(a, b);
  fail('scalars of different types cannot be ordered');
}

// ---------------------------------------------------------------------------
// Value checks (BUNDLE-131, BUNDLE-134, BUNDLE-135; TYPE-12 to TYPE-70)
// ---------------------------------------------------------------------------

const DECIMAL = /^-?(?:0|[1-9][0-9]*)(?:\.[0-9]*[1-9])?$/;
const TIME_HHMM = /^(?:[01][0-9]|2[0-3]):[0-5][0-9]$/;
const IANA_SYNTAX = /^[A-Za-z0-9_+-]+(?:\/[A-Za-z0-9_+-]+)*$/;

/** A set of the universe positions of a type, cached because the check of a set value looks members up. */
const universeCache = new WeakMap<readonly string[], Map<string, number>>();

function universeIndex(type: TypeDecl): Map<string, number> {
  let index = universeCache.get(type.universe);
  if (index === undefined) {
    index = new Map(type.universe.map((member, position) => [member, position] as const));
    universeCache.set(type.universe, index);
  }
  return index;
}

/**
 * Requires that a parsed value is valid for a type declaration. `partial` lets an object lack required fields,
 * at any depth: the partial object of a `deep_merge` key (MERGE-11, MERGE-12). `null` is never valid (TYPE-76).
 */
function checkValue(type: TypeDecl, value: unknown, partial: boolean, at: string): void {
  if (value === null || value === undefined) fail(`${at} is null, which is not a value (TYPE-76)`);
  switch (type.type) {
    case 'boolean':
      if (typeof value !== 'boolean') fail(`${at} must be a boolean (TYPE-12)`);
      return;
    case 'integer':
      checkInteger(type, value, at);
      return;
    case 'decimal':
      if (typeof value !== 'string' || !DECIMAL.test(value) || value === '-0') {
        fail(`${at} is not a canonical decimal string (NORM-9, BUNDLE-135)`);
      }
      return;
    case 'string':
      checkString(type, value, at);
      return;
    case 'enum':
      if (typeof value !== 'string' || !type.values.includes(value)) {
        fail(`${at} is not one of the values of its enum (TYPE-44)`);
      }
      return;
    case 'set':
      checkSet(type, value, at);
      return;
    case 'list': {
      if (!Array.isArray(value)) fail(`${at} must be an array, the value of a list (TYPE-55)`);
      const itemType = type.items as TypeDecl;
      (value as readonly unknown[]).forEach((element, index) => checkValue(itemType, element, partial, `${at}[${index}]`));
      return;
    }
    case 'object':
      checkObject(type, value, partial, at);
      return;
  }
}

function checkInteger(type: TypeDecl, value: unknown, at: string): void {
  if (typeof value !== 'number' || !Number.isInteger(value)) fail(`${at} must be an integer (TYPE-17)`);
  const number = value as number;
  if (Math.abs(number) > MAX_SAFE_INTEGER) fail(`${at} is outside +/-(2^53 - 1) (TYPE-18)`);
  if (type.min !== null && number < type.min) fail(`${at} is below the declared minimum (TYPE-19)`);
  if (type.max !== null && number > type.max) fail(`${at} is above the declared maximum (TYPE-19)`);
}

function checkString(type: TypeDecl, value: unknown, at: string): void {
  if (typeof value !== 'string') fail(`${at} must be a string (TYPE-28)`);
  if (type.format === 'time_hhmm' && !TIME_HHMM.test(value as string)) {
    fail(`${at} does not satisfy the format time_hhmm (TYPE-33)`);
  }
  if (type.format === 'iana_tz' && !IANA_SYNTAX.test(value as string)) {
    fail(`${at} does not satisfy the format iana_tz (TYPE-37)`);
  }
}

function checkSet(type: TypeDecl, value: unknown, at: string): void {
  if (!Array.isArray(value)) fail(`${at} must be an array, the value of a set (TYPE-45)`);
  const index = universeIndex(type);
  const seen = new Set<string>();
  let previous = -1;
  for (const member of value as readonly unknown[]) {
    if (typeof member !== 'string') fail(`${at} has a member that is not a string (TYPE-45)`);
    const position = index.get(member as string);
    if (position === undefined) fail(`${at} has a member outside the universe (BUNDLE-134)`);
    if (seen.has(member as string)) fail(`${at} repeats a member (BUNDLE-134)`);
    seen.add(member as string);
    if ((position as number) < previous) fail(`${at} is not sorted by universe position (BUNDLE-74)`);
    previous = position as number;
  }
}

function checkObject(type: TypeDecl, value: unknown, partial: boolean, at: string): void {
  const map = object(value, at);
  for (const name of Object.keys(map)) {
    if (!has(type.fields, name)) fail(`${at} has the field ${name}, which is not declared (TYPE-64)`);
    checkValue(type.fields[name] as TypeDecl, map[name], partial, `${at}.${name}`);
  }
  if (!partial) {
    for (const name of Object.keys(type.fields)) {
      if (!(type.fields[name] as TypeDecl).optional && !has(map, name)) {
        fail(`${at} lacks the required field ${name} (TYPE-62)`);
      }
    }
  }
}

/** Requires a list of distinct values of a fact's type, in bundle order (BUNDLE-89, BUNDLE-90, BUNDLE-133). */
function checkListValue(type: TypeDecl, value: unknown, at: string): void {
  const items = array(value, at);
  items.forEach((element, index) => {
    checkValue(type, element, false, `${at}[${index}]`);
    if (index > 0) {
      const cmp = compareScalars(items[index - 1], element);
      if (cmp === 0) fail(`${at} repeats an element (BUNDLE-133)`);
      if (cmp > 0) fail(`${at} is not sorted as the bundle requires (BUNDLE-90, BUNDLE-75, BUNDLE-152)`);
    }
  });
}

// ---------------------------------------------------------------------------
// Type declarations (BUNDLE-52, BUNDLE-82; TYPE-2 to TYPE-14, TYPE-40 to TYPE-43, TYPE-68)
// ---------------------------------------------------------------------------

const TYPE_NAMES: readonly TypeName[] = ['boolean', 'integer', 'decimal', 'string', 'enum', 'set', 'list', 'object'];

const TYPE_MEMBERS: { readonly [type: string]: readonly string[] } = {
  integer: ['min', 'max'],
  string: ['format'],
  enum: ['values'],
  set: ['universe'],
  list: ['items'],
  object: ['fields'],
};

const NO_FIELDS: Dict<TypeDecl> = record<TypeDecl>([]);

function typeDecl(partial: Partial<TypeDecl> & { readonly type: TypeName }): TypeDecl {
  return {
    min: null,
    max: null,
    format: null,
    values: [],
    universe: [],
    items: null,
    fields: NO_FIELDS,
    optional: false,
    ...partial,
  };
}

function readType(value: unknown, at: string, objectField: boolean): TypeDecl {
  const map = object(value, at);
  if (!has(map, 'type')) fail(`${at} lacks the member type (TYPE-3)`);
  const type = oneOf(map.type, TYPE_NAMES, `${at}.type`);
  const accepted = ['type', ...(TYPE_MEMBERS[type] ?? []), ...(objectField ? ['optional'] : [])];
  for (const name of Object.keys(map)) {
    if (!accepted.includes(name)) {
      fail(`${at} has the member ${name}, which its type does not define (TYPE-7, TYPE-8)`);
    }
  }
  let optional = false;
  if (has(map, 'optional')) {
    if (typeof map.optional !== 'boolean') fail(`${at}.optional must be a boolean (TYPE-9)`);
    if (map.optional === false) fail(`${at}.optional is false, which the bundle omits (BUNDLE-20, BUNDLE-82)`);
    optional = true;
  }
  switch (type) {
    case 'integer': {
      const min = has(map, 'min') ? integer(map.min, `${at}.min`) : null;
      const max = has(map, 'max') ? integer(map.max, `${at}.max`) : null;
      if (min !== null && max !== null && min > max) fail(`${at}: min is greater than max (TYPE-14)`);
      return typeDecl({ type, min, max, optional });
    }
    case 'string': {
      const format = has(map, 'format') ? oneOf(map.format, ['time_hhmm', 'iana_tz'] as const, `${at}.format`) : null;
      return typeDecl({ type, format, optional });
    }
    case 'enum': {
      const values = distinctNonEmpty(map.values, `${at}.values`);
      if (!strictlyIncreasing(values)) fail(`${at}.values is not sorted by code point (BUNDLE-75)`);
      return typeDecl({ type, values, optional });
    }
    case 'set':
      return typeDecl({ type, universe: distinctNonEmpty(map.universe, `${at}.universe`), optional });
    case 'list': {
      if (!has(map, 'items')) fail(`${at} lacks the member items (TYPE-53)`);
      return typeDecl({ type, items: readType(map.items, `${at}.items`, false), optional });
    }
    case 'object': {
      if (!has(map, 'fields')) fail(`${at} lacks the member fields (TYPE-59)`);
      return typeDecl({ type, fields: readFields(map.fields, `${at}.fields`), optional });
    }
    default:
      return typeDecl({ type, optional });
  }
}

/** Reads the fields of an object type or of the object form of `returns` (TYPE-68). */
function readFields(value: unknown, at: string): Dict<TypeDecl> {
  const raw = object(value, at);
  const names = Object.keys(raw);
  if (names.length === 0) fail(`${at} declares no field (TYPE-68)`);
  return record(
    names.map((name) => {
      if (name === '') fail(`${at} has an empty field name (TYPE-68)`);
      return [name, readType(raw[name], `${at}.${name}`, true)] as const;
    }),
  );
}

// ---------------------------------------------------------------------------
// Hierarchy (BUNDLE-29 to BUNDLE-43)
// ---------------------------------------------------------------------------

function isLevel(h: Hierarchy, name: string): boolean {
  return h.levels.includes(name);
}

function isAxis(h: Hierarchy, name: string): boolean {
  return h.axes.includes(name);
}

function isService(h: Hierarchy, name: string): boolean {
  return has(h.scopes, name) && (h.scopes[name] as ScopeDef).values === 'service';
}

function scopeOrder(h: Hierarchy): readonly string[] {
  return [...h.levels, ...h.axes];
}

function requireDistinctNames(names: readonly string[], at: string): void {
  if (new Set(names).size !== names.length) fail(`${at} repeats a name (SCOPE-3)`);
  // Beyond the Java reader: SCOPE-8 makes an empty level or axis name a defect that a bundle alone shows.
  if (names.includes('')) fail(`${at} has an empty name (SCOPE-8)`);
}

function readHierarchy(value: unknown): Hierarchy {
  const at = 'content.hierarchy';
  const map = object(value, at);
  members(map, at, ['levels', 'axes', 'scopes'], []);
  const levels = stringArray(map.levels, `${at}.levels`);
  const axes = stringArray(map.axes, `${at}.axes`);
  if (levels.length === 0) fail(`${at}.levels lacks the root level (BUNDLE-30)`);
  requireDistinctNames(levels, `${at}.levels`);
  requireDistinctNames(axes, `${at}.axes`);
  for (const axis of axes) {
    if (levels.includes(axis)) fail(`${at}: ${axis} is both a level and an axis (SCOPE-3)`);
  }
  const scopes = object(map.scopes, `${at}.scopes`);
  const expected = [...levels.slice(1), ...axes];
  const present = Object.keys(scopes);
  if (present.length !== expected.length || !expected.every((name) => has(scopes, name))) {
    fail(`${at}.scopes must have exactly one member for each non-root level and each axis (BUNDLE-32)`);
  }
  const definitions = [
    ...levels.slice(1).map((name) => [name, readScope(scopes[name], `${at}.scopes.${name}`, false)] as const),
    ...axes.map((name) => [name, readScope(scopes[name], `${at}.scopes.${name}`, true)] as const),
  ];
  return { levels, axes, scopes: record(definitions) };
}

function readScope(value: unknown, at: string, axis: boolean): ScopeDef {
  const map = object(value, at);
  members(map, at, ['values', 'nodes'], ['onUnknownNode']);
  const values = oneOf(map.values, ['files', 'service'] as const, `${at}.values`);
  if (axis && values === 'service') fail(`${at}.values is service, which only a level may be (BUNDLE-36)`);
  if (map.nodes === 'open') {
    if (has(map, 'onUnknownNode')) {
      fail(`${at} has onUnknownNode, which only a closed catalog has (BUNDLE-42)`);
    }
    return { values, open: true, nodes: [], onUnknownNode: null };
  }
  if (!Array.isArray(map.nodes)) fail(`${at}.nodes is neither open nor an array (BUNDLE-37)`);
  const nodes = stringArray(map.nodes, `${at}.nodes`);
  for (const id of nodes) requireNodeId(id, `${at}.nodes`);
  if (!strictlyIncreasing(nodes)) {
    fail(`${at}.nodes is not sorted by code point without repeats (BUNDLE-38, BUNDLE-39, BUNDLE-133)`);
  }
  if (!has(map, 'onUnknownNode')) fail(`${at} lacks onUnknownNode, which a closed catalog has (BUNDLE-42)`);
  const onUnknownNode = oneOf(map.onUnknownNode, ['error', 'ignore'] as const, `${at}.onUnknownNode`);
  return { values, open: false, nodes, onUnknownNode };
}

/** Requires a node ID: non-empty, at most 128 code points, free of control characters (SCOPE-33, SCOPE-34). */
function requireNodeId(id: string, at: string): void {
  if (id === '') fail(`${at} is an empty node ID (BUNDLE-129)`);
  if (!isNodeId(id)) fail(`${at} holds a malformed node ID (SCOPE-33, SCOPE-34)`);
}

/** The closed catalogs by scope name, as sets. */
interface Catalogs {
  readonly hierarchy: Hierarchy;
  readonly closed: ReadonlyMap<string, ReadonlySet<string>>;
}

function catalogsOf(hierarchy: Hierarchy): Catalogs {
  const closed = new Map<string, ReadonlySet<string>>();
  for (const name of Object.keys(hierarchy.scopes)) {
    const scope = hierarchy.scopes[name] as ScopeDef;
    if (!scope.open) closed.set(name, new Set(scope.nodes));
  }
  return { hierarchy, closed };
}

/**
 * Requires that a node key is one that the scope admits: the empty string under the root level only, and for any
 * other scope a node ID that its closed catalog lists, or any node ID when the catalog is open (BUNDLE-66,
 * BUNDLE-129).
 */
function requireAdmitted(catalogs: Catalogs, scope: string, nodeKey: string, at: string): void {
  if (scope === catalogs.hierarchy.levels[0]) {
    if (nodeKey !== '') fail(`${at}: the root level has only the empty node key (BUNDLE-66)`);
    return;
  }
  if (nodeKey === '') fail(`${at}: the empty node key is only for the root level (BUNDLE-129)`);
  requireNodeId(nodeKey, at);
  const closed = catalogs.closed.get(scope);
  if (closed !== undefined && !closed.has(nodeKey)) {
    fail(`${at}: the closed catalog of ${scope} does not admit ${nodeKey} (BUNDLE-129)`);
  }
}

// ---------------------------------------------------------------------------
// Entities and facts (BUNDLE-44 to BUNDLE-50, BUNDLE-121, BUNDLE-122)
// ---------------------------------------------------------------------------

const FACT_TYPES: readonly TypeName[] = ['boolean', 'integer', 'decimal', 'string', 'enum'];

function sortedEdges(value: unknown, at: string): string[] {
  const names = stringArray(value, at);
  if (!strictlyIncreasing(names)) fail(`${at} is not sorted by code point without repeats (BUNDLE-75)`);
  return names;
}

function readEntities(value: unknown): Dict<EntityDef> {
  const raw = object(value, 'content.entities');
  const entities = record(
    Object.keys(raw).map((name) => {
      const at = `content.entities.${name}`;
      if (name === '' || name.includes('.')) fail(`${at} is not a valid entity name (ENTITY-18)`);
      const map = object(raw[name], at);
      members(map, at, ['belongsTo', 'hasMany'], []);
      const def: EntityDef = {
        belongsTo: sortedEdges(map.belongsTo, `${at}.belongsTo`),
        hasMany: sortedEdges(map.hasMany, `${at}.hasMany`),
      };
      return [name, def] as const;
    }),
  );
  for (const name of Object.keys(entities)) {
    const entity = entities[name] as EntityDef;
    for (const edge of [...entity.belongsTo, ...entity.hasMany]) {
      if (!has(entities, edge)) fail(`the edge names the undeclared entity ${edge} (BUNDLE-121)`);
    }
  }
  requireAcyclic(entities);
  return entities;
}

/**
 * Beyond the Java reader: ENTITY-14 to ENTITY-17 make a cycle in the oriented entity graph a defect that a bundle
 * alone shows (BUNDLE-118). Every edge runs from parent to child: P to E for `belongsTo`, P to C for `hasMany`.
 * Kahn's algorithm, so a long chain cannot exhaust the stack.
 */
function requireAcyclic(entities: Dict<EntityDef>): void {
  const names = Object.keys(entities);
  const incoming = new Map<string, number>(names.map((name) => [name, 0] as const));
  const outgoing = new Map<string, string[]>(names.map((name) => [name, []] as const));
  const addEdge = (parent: string, child: string): void => {
    (outgoing.get(parent) as string[]).push(child);
    incoming.set(child, (incoming.get(child) as number) + 1);
  };
  for (const name of names) {
    const entity = entities[name] as EntityDef;
    entity.belongsTo.forEach((parent) => addEdge(parent, name));
    entity.hasMany.forEach((child) => addEdge(name, child));
  }
  const ready = names.filter((name) => incoming.get(name) === 0);
  let visited = 0;
  while (ready.length > 0) {
    const name = ready.pop() as string;
    visited++;
    for (const child of outgoing.get(name) as string[]) {
      const left = (incoming.get(child) as number) - 1;
      incoming.set(child, left);
      if (left === 0) ready.push(child);
    }
  }
  if (visited !== names.length) fail('the entity graph has a cycle (ENTITY-14 to ENTITY-17)');
}

function readFacts(value: unknown, entities: Dict<EntityDef>): Dict<FactDef> {
  const raw = object(value, 'content.facts');
  return record(
    Object.keys(raw).map((name) => {
      const at = `content.facts.${name}`;
      const dot = name.indexOf('.');
      if (dot < 0 || dot === name.length - 1) fail(`${at} is not named <entity>.<attribute> (FACT-3)`);
      const prefix = name.slice(0, dot);
      const map = object(raw[name], at);
      members(map, at, ['entity', 'type'], []);
      const entity = string(map.entity, `${at}.entity`);
      if (entity !== prefix) fail(`${at}.entity differs from the prefix of the fact name (BUNDLE-122)`);
      if (!has(entities, entity)) fail(`${at} names the undeclared entity ${entity} (BUNDLE-121)`);
      const type = readType(map.type, `${at}.type`, false);
      if (!FACT_TYPES.includes(type.type)) {
        fail(`${at} has the type ${type.type}, which a fact may not have (BUNDLE-50)`);
      }
      const def: FactDef = { entity, type };
      return [name, def] as const;
    }),
  );
}

// ---------------------------------------------------------------------------
// Keys (BUNDLE-51 to BUNDLE-63)
// ---------------------------------------------------------------------------

const ORDER_DEPENDENT: readonly Strategy[] = ['override', 'deep_merge'];
const STRATEGIES: readonly Strategy[] = ['all', 'any', 'intersect', 'union', 'min', 'max', 'override', 'deep_merge'];

function requireStrategyType(combine: Strategy, type: TypeDecl, at: string): void {
  let fits: boolean;
  switch (combine) {
    case 'override':
      fits = true;
      break;
    case 'deep_merge':
      fits = type.type === 'object';
      break;
    case 'all':
    case 'any':
      fits = type.type === 'boolean';
      break;
    case 'intersect':
    case 'union':
      fits = type.type === 'set';
      break;
    default:
      fits = type.type === 'integer';
  }
  if (!fits) fail(`${at}: the strategy ${combine} does not allow the type ${type.type} (MERGE-3)`);
}

function readSettableAt(value: unknown, at: string, hierarchy: Hierarchy): string[] {
  const names = stringArray(value, at);
  if (names.length === 0) fail(`${at} is empty (KEY-7)`);
  const scopes = scopeOrder(hierarchy);
  let previous = -1;
  for (const name of names) {
    const position = scopes.indexOf(name);
    if (position < 0) fail(`${at} names the undeclared scope ${name} (BUNDLE-127)`);
    if (position <= previous) fail(`${at} is not sorted by scope position without repeats (BUNDLE-54)`);
    previous = position;
  }
  return names;
}

/** The narrowing strategies (MERGE-2), the only ones that may carry a deny value (SVC-11). */
const NARROWING: readonly Strategy[] = ['all', 'intersect', 'min'];

/**
 * Requires that a key whose failure policy is deny has a deny value that narrows (SVC-11, SVC-12, BUNDLE-118). The
 * bundle holds onSourceFailure exactly for a key settable at a service-sourced level (BUNDLE-59), so the policy is
 * the one in effect (BUNDLE-61). A min key needs a min member in its integer type, because its deny value is that
 * member (SVC-9). The same checks are made by the Java KeyReader.
 */
function requireDenyIsDefined(
  combine: Strategy,
  type: TypeDecl,
  onSourceFailure: KeyDef['onSourceFailure'],
  at: string,
): void {
  if (onSourceFailure !== 'deny') return;
  if (!NARROWING.includes(combine)) {
    fail(`${at}: deny needs a narrowing strategy, and ${combine} is not narrowing (SVC-11, MERGE-2)`);
  }
  if (combine === 'min' && type.min === null) {
    fail(`${at}: deny under min needs a min member in the integer type (SVC-12)`);
  }
}

function readKey(name: string, value: unknown, hierarchy: Hierarchy): KeyDef {
  const at = `content.keys.${name}`;
  const map = object(value, at);
  members(map, at, ['type', 'combine', 'settableAt'], ['default', 'combineAcrossAxes', 'onSourceFailure', 'kind']);
  const type = readType(map.type, `${at}.type`, false);
  const combine = oneOf(map.combine, STRATEGIES, `${at}.combine`);
  requireStrategyType(combine, type, at);
  const settableAt = readSettableAt(map.settableAt, `${at}.settableAt`, hierarchy);
  const orderDependent = ORDER_DEPENDENT.includes(combine);
  const settableAtAxis = settableAt.some((scope) => isAxis(hierarchy, scope));
  const settableAtService = settableAt.some((scope) => isService(hierarchy, scope));

  let defaultValue: JsonValue = null;
  if (has(map, 'default')) {
    if (!orderDependent) fail(`${at} declares a default, which a commutative key may not have (MERGE-4)`);
    checkValue(type, map.default, false, `${at}.default`);
    defaultValue = map.default as JsonValue;
  }

  let combineAcrossAxes: KeyDef['combineAcrossAxes'] = null;
  const acrossRequired = orderDependent && settableAtAxis;
  if (has(map, 'combineAcrossAxes') !== acrossRequired) {
    fail(
      `${at} ${acrossRequired ? 'lacks' : 'has'} combineAcrossAxes, which an override or deep_merge key settable at an axis ` +
        'has and no other key has (BUNDLE-57)',
    );
  }
  if (acrossRequired) {
    combineAcrossAxes = oneOf(
      map.combineAcrossAxes,
      ['axis_wins', 'hierarchy_wins', 'max', 'min'] as const,
      `${at}.combineAcrossAxes`,
    );
  }

  let onSourceFailure: KeyDef['onSourceFailure'] = null;
  if (has(map, 'onSourceFailure') !== settableAtService) {
    fail(
      `${at} ${settableAtService ? 'lacks' : 'has'} onSourceFailure, which a key settable at a service-sourced ` +
        'level has and no other key has (BUNDLE-59)',
    );
  }
  if (settableAtService) {
    onSourceFailure = oneOf(map.onSourceFailure, ['skip', 'deny'] as const, `${at}.onSourceFailure`);
  }
  requireDenyIsDefined(combine, type, onSourceFailure, at);

  const kind = has(map, 'kind') ? string(map.kind, `${at}.kind`) : null;
  return { type, combine, defaultValue, settableAt, combineAcrossAxes, onSourceFailure, kind };
}

function readKeys(value: unknown, hierarchy: Hierarchy): Dict<KeyDef> {
  const raw = object(value, 'content.keys');
  return record(Object.keys(raw).map((name) => [name, readKey(name, raw[name], hierarchy)] as const));
}

// ---------------------------------------------------------------------------
// Values (BUNDLE-22, BUNDLE-64 to BUNDLE-67, BUNDLE-129 to BUNDLE-131; MERGE-7, MERGE-10 to MERGE-12)
// ---------------------------------------------------------------------------

function readValues(value: unknown, catalogs: Catalogs, keys: Dict<KeyDef>): ValuesByScope {
  const hierarchy = catalogs.hierarchy;
  const root = hierarchy.levels[0] as string;
  const scopes = object(value, 'content.values');
  return record(
    Object.keys(scopes).map((scope) => {
      const at = `content.values.${scope}`;
      if (!isLevel(hierarchy, scope) && !isAxis(hierarchy, scope)) {
        fail(`${at} names an undeclared scope (BUNDLE-127)`);
      }
      if (scope !== root && isService(hierarchy, scope)) {
        fail(`${at} holds values for a service-sourced level (BUNDLE-67)`);
      }
      const nodes = object(scopes[scope], at);
      const names = Object.keys(nodes);
      if (names.length === 0) fail(`${at} has no node entry, so it must be absent (BUNDLE-22)`);
      const byNode = record(
        names.map((node) => {
          const nodeAt = `${at}.${node}`;
          requireAdmitted(catalogs, scope, node, nodeAt);
          return [node, readNodeValues(scope, node, nodes[node], nodeAt, hierarchy, keys)] as const;
        }),
      );
      return [scope, byNode] as const;
    }),
  );
}

function readNodeValues(
  scope: string,
  node: string,
  value: unknown,
  at: string,
  hierarchy: Hierarchy,
  keys: Dict<KeyDef>,
): Dict<JsonValue> {
  const entries = object(value, at);
  const names = Object.keys(entries);
  if (names.length === 0) fail(`${at} has no key value, so it must be absent (BUNDLE-22)`);
  return record(
    names.map((name) => {
      const keyAt = `${at}.${name}`;
      if (!has(keys, name)) fail(`${keyAt} names an undeclared key (BUNDLE-130)`);
      const key = keys[name] as KeyDef;
      if (!key.settableAt.includes(scope)) {
        fail(`${keyAt} is set at ${scope}, where the key is not settable (BUNDLE-130)`);
      }
      // A deep_merge value is partial, except the starting value: the default when one is declared, and the
      // root value otherwise (MERGE-10 to MERGE-12).
      const root = scope === hierarchy.levels[0] && node === '';
      const partial = key.combine === 'deep_merge' && (key.defaultValue !== null || !root);
      checkValue(key.type, entries[name], partial, keyAt);
      return [name, entries[name] as JsonValue] as const;
    }),
  );
}

/** Requires a root value for each order-dependent key that declares no default (MERGE-7). */
function requireStartingValues(values: ValuesByScope, keys: Dict<KeyDef>, hierarchy: Hierarchy): void {
  const root = hierarchy.levels[0] as string;
  const rootValues = has(values, root) ? (values[root] as Dict<Dict<JsonValue>>)[''] : undefined;
  for (const name of Object.keys(keys)) {
    const key = keys[name] as KeyDef;
    if (
      ORDER_DEPENDENT.includes(key.combine) &&
      key.defaultValue === null &&
      (rootValues === undefined || !has(rootValues, name))
    ) {
      fail(`content.keys.${name} is an override or deep_merge key, so it needs a default or a root value (MERGE-7)`);
    }
  }
}

// ---------------------------------------------------------------------------
// Features, policies and bindings (BUNDLE-76 to BUNDLE-108, BUNDLE-120 to BUNDLE-128, BUNDLE-132, BUNDLE-136)
// ---------------------------------------------------------------------------

/** Checks an effect against a feature's `returns` (FEAT-9, FEAT-11, FEAT-13, BUNDLE-24). */
function checkEffect(returns: Returns, effect: unknown, at: string): void {
  if (effect === null || effect === undefined) {
    if (returns.objectForm || !returns.nullable) {
      fail(`${at} is null, but the feature's returns is not nullable (BUNDLE-24, FEAT-9)`);
    }
    return;
  }
  if (returns.objectForm) {
    checkValue(typeDecl({ type: 'object', fields: returns.fields }), effect, false, at);
    return;
  }
  if (typeof effect !== 'string' || !returns.enumValues.includes(effect)) {
    fail(`${at} is not an effect of the feature's returns (FEAT-13)`);
  }
}

function readReturns(value: unknown, at: string): Returns {
  const map = object(value, at);
  const enumForm = has(map, 'enum');
  if (enumForm === has(map, 'object')) fail(`${at} takes exactly one of enum and object (FEAT-6)`);
  if (enumForm) {
    members(map, at, ['enum'], ['nullable']);
    const enumValues = distinctNonEmpty(map.enum, `${at}.enum`);
    if (!strictlyIncreasing(enumValues)) fail(`${at}.enum is not sorted by code point (BUNDLE-75, BUNDLE-82)`);
    let nullable = false;
    if (has(map, 'nullable')) {
      if (map.nullable !== true) fail(`${at}.nullable is present only when true (BUNDLE-82)`);
      nullable = true;
    }
    return { objectForm: false, enumValues, nullable, fields: NO_FIELDS };
  }
  members(map, at, ['object'], []);
  return { objectForm: true, enumValues: [], nullable: false, fields: readFields(map.object, `${at}.object`) };
}

function readBindingLevels(value: unknown, at: string, hierarchy: Hierarchy): string[] {
  const names = stringArray(value, `${at}.bindingSettableAt`);
  if (names.length === 0) fail(`${at}.bindingSettableAt is empty (FEAT-2)`);
  let previous = -1;
  for (const name of names) {
    const position = hierarchy.levels.indexOf(name);
    if (position < 0) {
      fail(`${at}.bindingSettableAt names ${name}, which is not a level (BUNDLE-127, BUNDLE-106)`);
    }
    if (position <= previous) fail(`${at}.bindingSettableAt is not sorted by level position without repeats (BUNDLE-81)`);
    if (isService(hierarchy, name)) {
      fail(`${at}.bindingSettableAt names the service-sourced level ${name} (BUNDLE-128)`);
    }
    previous = position;
  }
  if (!names.includes(hierarchy.levels[0] as string)) fail(`${at}.bindingSettableAt omits the root level (BUNDLE-128)`);
  return names;
}

function requireSettingName(name: string, at: string): void {
  if (name === '' || name.includes('$') || name.includes('.') || name === 'policy') {
    fail(`${at} is not a valid name for a settings entry (POL-20)`);
  }
}

function readSettings(value: unknown, facts: Dict<FactDef>, at: string): Dict<SettingDef> {
  const raw = object(value, `${at}.settings`);
  return record(
    Object.keys(raw).map((name) => {
      const settingAt = `${at}.settings.${name}`;
      requireSettingName(name, settingAt);
      const map = object(raw[name], settingAt);
      const single = has(map, 'valueOf');
      const form = single ? 'valueOf' : 'listOf';
      members(map, settingAt, ['default', form], []);
      const factName = string(map[form], `${settingAt}.${form}`);
      if (!has(facts, factName)) fail(`${settingAt} names the undeclared fact ${factName} (BUNDLE-120)`);
      const fact = facts[factName] as FactDef;
      if (single) {
        checkValue(fact.type, map.default, false, `${settingAt}.default`);
      } else {
        checkListValue(fact.type, map.default, `${settingAt}.default`);
      }
      const def: SettingDef = { kind: form, fact: factName, defaultValue: map.default as JsonValue };
      return [name, def] as const;
    }),
  );
}

const SETTING_PREFIX = '$';

function isReference(operand: unknown): operand is string {
  return typeof operand === 'string' && operand.startsWith(SETTING_PREFIX);
}

const OPERATORS: readonly Operator[] = ['eq', 'in', 'all_in', 'any_in'];

function readCondition(value: unknown, settings: Dict<SettingDef>, facts: Dict<FactDef>, at: string): ConditionDef {
  const map = object(value, at);
  members(map, at, ['fact', 'op', 'value'], []);
  const factName = string(map.fact, `${at}.fact`);
  if (!has(facts, factName)) fail(`${at} names the undeclared fact ${factName} (BUNDLE-120)`);
  const fact = facts[factName] as FactDef;
  const op = oneOf(map.op, OPERATORS, `${at}.op`);
  const operand = map.value;
  const valueAt = `${at}.value`;
  let reference: string | null = null;
  if (isReference(operand)) {
    reference = operand.slice(SETTING_PREFIX.length);
    if (!has(settings, reference)) fail(`${valueAt} names no settings entry of its policy (BUNDLE-125)`);
    const entry = settings[reference] as SettingDef;
    const accepted = op === 'eq' ? entry.kind === 'valueOf' : entry.kind === 'listOf';
    if (!accepted || entry.fact !== factName) {
      fail(`${valueAt} is a reference to a settings entry in a position that RULE does not allow (BUNDLE-126)`);
    }
  } else if (op === 'eq') {
    checkValue(fact.type, operand, false, valueAt);
  } else {
    // The operand list of in, all_in and any_in: literals of the fact's type, with no setting reference, in
    // bundle order. A repeated element is accepted (ADR 0018).
    const items = array(operand, valueAt);
    items.forEach((element, index) => {
      if (isReference(element)) fail(`${valueAt}[${index}] is a reference to a settings entry inside a literal list (BUNDLE-126)`);
      checkValue(fact.type, element, false, `${valueAt}[${index}]`);
      if (index > 0 && compareScalars(items[index - 1], element) > 0) {
        fail(`${valueAt} is not sorted as the bundle requires (BUNDLE-75, BUNDLE-152)`);
      }
    });
  }
  return { fact: factName, op, value: operand as JsonValue, reference };
}

function readRules(
  value: unknown,
  settings: Dict<SettingDef>,
  facts: Dict<FactDef>,
  returns: Returns,
  at: string,
): RuleDef[] {
  const ids = new Set<string>();
  return array(value, at).map((element, index) => {
    const ruleAt = `${at}[${index}]`;
    const map = object(element, ruleAt);
    members(map, ruleAt, ['id', 'name', 'when', 'then'], []);
    const id = string(map.id, `${ruleAt}.id`);
    if (ids.has(id)) fail(`${ruleAt} repeats the rule id ${id} in its policy (BUNDLE-132)`);
    ids.add(id);
    const name = string(map.name, `${ruleAt}.name`);
    let previous: string | null = null;
    const when = array(map.when, `${ruleAt}.when`).map((raw, position) => {
      const condition = readCondition(raw, settings, facts, `${ruleAt}.when[${position}]`);
      if (previous !== null && compareCodePoints(previous, condition.fact) >= 0) {
        fail(`${ruleAt}.when[${position}] is not sorted by fact, with no repeats (BUNDLE-96, BUNDLE-119)`);
      }
      previous = condition.fact;
      return condition;
    });
    checkEffect(returns, map.then, `${ruleAt}.then`);
    return { id, name, when, then: map.then as JsonValue };
  });
}

function readPolicy(value: unknown, facts: Dict<FactDef>, returns: Returns, at: string): PolicyDef {
  const map = object(value, at);
  members(map, at, ['settings', 'default', 'requiredFacts', 'rules'], []);
  const settings = readSettings(map.settings, facts, at);
  checkEffect(returns, map.default, `${at}.default`);
  const requiredFacts = stringArray(map.requiredFacts, `${at}.requiredFacts`);
  if (!strictlyIncreasing(requiredFacts)) {
    fail(`${at}.requiredFacts is not sorted by code point without repeats (BUNDLE-92)`);
  }
  for (const fact of requiredFacts) {
    if (!has(facts, fact)) fail(`${at}.requiredFacts names the undeclared fact ${fact} (BUNDLE-120)`);
  }
  const rules = readRules(map.rules, settings, facts, returns, `${at}.rules`);
  const derived = [...new Set(rules.flatMap((rule) => rule.when.map((condition) => condition.fact)))].sort(
    compareCodePoints,
  );
  if (derived.length !== requiredFacts.length || derived.some((fact, index) => fact !== requiredFacts[index])) {
    fail(`${at}.requiredFacts is not the distinct facts of its rules (BUNDLE-136)`);
  }
  return { settings, defaultEffect: map.default as JsonValue, requiredFacts, rules };
}

function readBinding(value: unknown, policies: Dict<PolicyDef>, facts: Dict<FactDef>, at: string): BindingDef {
  const map = object(value, at);
  members(map, at, ['policy', 'settings'], []);
  const policyName = string(map.policy, `${at}.policy`);
  if (!has(policies, policyName)) {
    fail(`${at} names the policy ${policyName}, which the feature does not declare (BUNDLE-123)`);
  }
  const policy = policies[policyName] as PolicyDef;
  const raw = object(map.settings, `${at}.settings`);
  const settings = record(
    Object.keys(raw).map((name) => {
      const settingAt = `${at}.settings.${name}`;
      if (!has(policy.settings, name)) fail(`${settingAt} is not a settings entry of its policy (BUNDLE-124)`);
      const entry = policy.settings[name] as SettingDef;
      const fact = facts[entry.fact] as FactDef;
      if (entry.kind === 'valueOf') {
        checkValue(fact.type, raw[name], false, settingAt);
      } else {
        checkListValue(fact.type, raw[name], settingAt);
      }
      return [name, raw[name] as JsonValue] as const;
    }),
  );
  return { policy: policyName, settings };
}

function readBindings(
  value: unknown,
  catalogs: Catalogs,
  bindingLevels: readonly string[],
  policies: Dict<PolicyDef>,
  facts: Dict<FactDef>,
  at: string,
): Dict<Dict<BindingDef>> {
  const hierarchy = catalogs.hierarchy;
  const levels = object(value, at);
  const bindings = record(
    Object.keys(levels).map((level) => {
      const levelAt = `${at}.${level}`;
      if (isAxis(hierarchy, level)) fail(`${levelAt}: only levels carry bindings (BUNDLE-106)`);
      if (!isLevel(hierarchy, level)) fail(`${levelAt} names an undeclared scope (BUNDLE-127)`);
      if (!bindingLevels.includes(level)) {
        fail(`${levelAt} is not one of the feature's bindingSettableAt levels (BUNDLE-81)`);
      }
      const nodes = object(levels[level], levelAt);
      const names = Object.keys(nodes);
      if (names.length === 0) fail(`${levelAt} has no node entry, so it must be absent (BUNDLE-22)`);
      const byNode = record(
        names.map((node) => {
          const nodeAt = `${levelAt}.${node}`;
          requireAdmitted(catalogs, level, node, nodeAt);
          return [node, readBinding(nodes[node], policies, facts, nodeAt)] as const;
        }),
      );
      return [level, byNode] as const;
    }),
  );
  const root = hierarchy.levels[0] as string;
  if (!has(bindings, root) || !has(bindings[root] as Dict<BindingDef>, '')) {
    fail(`${at} lacks the root binding (BIND-13)`);
  }
  return bindings;
}

function readFeature(
  name: string,
  value: unknown,
  catalogs: Catalogs,
  entities: Dict<EntityDef>,
  facts: Dict<FactDef>,
): FeatureDef {
  const at = `content.features.${name}`;
  const map = object(value, at);
  members(map, at, ['decidedPer', 'returns', 'hitPolicy', 'ifFactMissing', 'bindingSettableAt', 'policies', 'bindings'], []);
  const decidedPer = string(map.decidedPer, `${at}.decidedPer`);
  if (!has(entities, decidedPer)) fail(`${at} is decided per the undeclared entity ${decidedPer} (BUNDLE-121)`);
  const returns = readReturns(map.returns, `${at}.returns`);
  oneOf(map.hitPolicy, ['FIRST'] as const, `${at}.hitPolicy`);
  checkEffect(returns, map.ifFactMissing, `${at}.ifFactMissing`);
  const bindingSettableAt = readBindingLevels(map.bindingSettableAt, at, catalogs.hierarchy);
  const rawPolicies = object(map.policies, `${at}.policies`);
  const policies = record(
    Object.keys(rawPolicies).map(
      (policy) => [policy, readPolicy(rawPolicies[policy], facts, returns, `${at}.policies.${policy}`)] as const,
    ),
  );
  const bindings = readBindings(map.bindings, catalogs, bindingSettableAt, policies, facts, `${at}.bindings`);
  return { decidedPer, returns, ifFactMissing: map.ifFactMissing as JsonValue, bindingSettableAt, policies, bindings };
}

function readFeatures(
  value: unknown,
  catalogs: Catalogs,
  entities: Dict<EntityDef>,
  facts: Dict<FactDef>,
): Dict<FeatureDef> {
  const raw = object(value, 'content.features');
  return record(
    Object.keys(raw).map((name) => [name, readFeature(name, raw[name], catalogs, entities, facts)] as const),
  );
}

// ---------------------------------------------------------------------------
// The content
// ---------------------------------------------------------------------------

const CONTENT_MEMBERS = [
  'bundleFormatVersion',
  'specVersion',
  'hierarchy',
  'entities',
  'facts',
  'keys',
  'values',
  'features',
] as const;

/**
 * Reads the `content` object of a bundle into a deeply frozen model, applying the structural checks of
 * BUNDLE-14 to BUNDLE-136. The version members must already have been checked.
 *
 * @throws TierwiseError E_BUNDLE_INTEGRITY for a structural failure
 */
export function readModel(content: unknown): Model {
  const map = object(content, 'content');
  members(map, 'content', CONTENT_MEMBERS, []);
  const hierarchy = readHierarchy(map.hierarchy);
  const catalogs = catalogsOf(hierarchy);
  const entities = readEntities(map.entities);
  const facts = readFacts(map.facts, entities);
  const keys = readKeys(map.keys, hierarchy);
  const values = readValues(map.values, catalogs, keys);
  requireStartingValues(values, keys, hierarchy);
  const features = readFeatures(map.features, catalogs, entities, facts);
  return deepFreeze({ hierarchy, entities, facts, keys, values, features });
}
