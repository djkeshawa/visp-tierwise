// Entities and FactSets at run time (sections 3.3 to 3.7): which facts are eligible for a decision, and the
// validation of the caller's FactSet against the decided entity (FACT-22 to FACT-42). Every failure is
// E_FACTS_INVALID (FACT-42), a missing fact is not a failure (FACT-21), and an entry that fails is never ignored
// or treated as missing (FACT-43).

import { TierwiseError } from './errors.js';
import type { JsonValue } from './json.js';
import type { EntityDef, FactDef, Model } from './model.js';
import { has, isPlainObject } from './text.js';
import { validateValue } from './values.js';

function invalid(message: string): TierwiseError {
  return new TierwiseError('E_FACTS_INVALID', message);
}

/** The facts that are eligible for decisions about one entity (FACT-13 to FACT-15). */
export class EntityScope {
  /** The single-value facts: the facts of the decided entity and of each of its ancestors. */
  readonly singleValueFacts: ReadonlySet<string>;
  /** The child entities, with the facts that each declares. */
  readonly childFacts: ReadonlyMap<string, ReadonlySet<string>>;

  constructor(
    readonly entity: string,
    model: Model,
  ) {
    // The entities of the decided entity and its ancestors, reached over `belongsTo` edges (ENTITY-10).
    const owners = new Set<string>([entity]);
    const toVisit: string[] = [entity];
    while (toVisit.length > 0) {
      const current = model.entities[toVisit.pop() as string] as EntityDef;
      for (const parent of current.belongsTo) {
        if (!owners.has(parent)) {
          owners.add(parent);
          toVisit.push(parent);
        }
      }
    }
    const children = new Map<string, Set<string>>();
    for (const child of (model.entities[entity] as EntityDef).hasMany) {
      children.set(child, new Set());
    }
    const single = new Set<string>();
    for (const name of Object.keys(model.facts)) {
      const owner = (model.facts[name] as FactDef).entity;
      if (owners.has(owner)) {
        single.add(name);
      } else {
        children.get(owner)?.add(name);
      }
    }
    this.singleValueFacts = single;
    this.childFacts = children;
  }
}

/** A FactSet that passed validation. A missing single-value fact has no entry. */
export interface ValidatedFacts {
  readonly facts: ReadonlyMap<string, JsonValue>;
  /** The child maps by child entity. A child entity with no entry has an omitted collection (FACT-17). */
  readonly children: ReadonlyMap<string, readonly ReadonlyMap<string, JsonValue>[]>;
}

const EMPTY: ValidatedFacts = { facts: new Map(), children: new Map() };

function checkValue(model: Model, fact: string, value: unknown): JsonValue | null {
  if (value === null || value === undefined) {
    return null; // A null fact value is missing, not invalid (FACT-16, FACT-19).
  }
  const valid = validateValue((model.facts[fact] as FactDef).type, value);
  if (valid === undefined) {
    throw invalid(`not a valid value of the fact ${fact}`);
  }
  return valid;
}

function checkFacts(
  model: Model,
  value: unknown,
  accepts: (name: string) => boolean,
  what: string,
): Map<string, JsonValue> {
  if (!isPlainObject(value)) {
    throw invalid(`${what} is not a map`);
  }
  const checked = new Map<string, JsonValue>();
  for (const name of Object.keys(value)) {
    if (!accepts(name)) {
      throw invalid(`not a fact that ${what} may hold: ${name}`);
    }
    const normal = checkValue(model, name, value[name]);
    if (normal !== null) {
      checked.set(name, normal);
    }
  }
  return checked;
}

/**
 * Validates a FactSet against the decided entity of a feature, at step 3 of EVAL-17.
 *
 * @param factSet the caller's FactSet; `undefined` is the empty FactSet (EVAL-54)
 * @throws TierwiseError E_FACTS_INVALID
 */
export function validateFactSet(model: Model, scope: EntityScope, factSet: unknown): ValidatedFacts {
  if (factSet === undefined) {
    return EMPTY;
  }
  if (!isPlainObject(factSet)) {
    throw invalid('the FactSet is not a map');
  }
  for (const member of Object.keys(factSet)) {
    if (member !== 'facts' && member !== 'children') {
      throw invalid(`the FactSet has an unknown member: ${member}`);
    }
  }
  const facts =
    factSet.facts === undefined
      ? new Map<string, JsonValue>()
      : checkFacts(model, factSet.facts, (name) => scope.singleValueFacts.has(name), `facts of ${scope.entity}`);
  const children = new Map<string, readonly ReadonlyMap<string, JsonValue>[]>();
  if (factSet.children !== undefined) {
    const raw = factSet.children;
    if (!isPlainObject(raw)) {
      throw invalid('children is not a map');
    }
    for (const entity of Object.keys(raw)) {
      const permitted = scope.childFacts.get(entity);
      if (permitted === undefined) {
        throw invalid(`not a child of ${scope.entity}: ${entity}`);
      }
      const list = raw[entity];
      if (!Array.isArray(list)) {
        throw invalid(`the children of ${entity} are not a list`);
      }
      const maps: Map<string, JsonValue>[] = [];
      for (let index = 0; index < list.length; index++) {
        maps.push(checkFacts(model, list[index], (name) => permitted.has(name), `a child map of ${entity}`));
      }
      children.set(entity, maps);
    }
  }
  return { facts, children };
}

/**
 * Tells whether a fact of the precheck set is missing (FACT-16 to FACT-19): a single-value fact that is absent
 * or null, a child fact whose collection is omitted, or a child fact that one child map lacks.
 */
export function isMissing(model: Model, scope: EntityScope, validated: ValidatedFacts, fact: string): boolean {
  if (scope.singleValueFacts.has(fact)) {
    return !validated.facts.has(fact);
  }
  if (!has(model.facts, fact)) {
    return true;
  }
  const maps = validated.children.get((model.facts[fact] as FactDef).entity);
  if (maps === undefined) {
    return true;
  }
  return maps.some((map) => !map.has(fact));
}
