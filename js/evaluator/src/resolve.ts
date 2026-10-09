// resolve and resolveSync (EVAL-7, EVAL-9 to EVAL-11, EVAL-16): the steps run in the order of EVAL-16 and the
// first failing step decides the error (EVAL-18, EVAL-20).

import { type AdmittedContext, type ContextValidator, type PathScope, isServiceSourced } from './context.js';
import { TierwiseError } from './errors.js';
import type { JsonValue } from './json.js';
import type { KeyDef, Model } from './model.js';
import { type ProviderCalls, type ProviderOutcomes, NO_OUTCOMES, neededLevels } from './providers.js';
import {
  type Contributed,
  combine,
  combineDeepMerge,
  combineOverride,
  denyValue,
  identity,
  isCommutative,
  label,
} from './strategies.js';
import { has } from './text.js';
import type { Contribution, Resolved, Source } from './types.js';
import { cloneValue } from './values.js';

/** One scope's slot for a key: a value it contributes, or a source failure at a service-sourced level. */
type Slot =
  | { readonly scope: PathScope; readonly value: JsonValue; readonly failure: null }
  | { readonly scope: PathScope; readonly value: null; readonly failure: 'UNAVAILABLE' | 'INVALID' };

function sourceOf(scope: PathScope): Source {
  const source: { scope: PathScope['kind']; name: string; node?: string } = { scope: scope.kind, name: scope.name };
  if (scope.node !== null) {
    source.node = scope.node;
  }
  return source;
}

/** Resolves keys: builds the result of a key from the bundle's values and the provider outcomes. */
export class Resolver {
  constructor(
    private readonly model: Model,
    private readonly bundleVersion: string,
    private readonly validator: ContextValidator,
    private readonly providers: ProviderCalls,
  ) {}

  /** Steps 1 and 2 of EVAL-16: context validation, then the key lookup. */
  private prepare(context: unknown, key: unknown): { admitted: AdmittedContext; name: string } {
    const admitted = this.validator.validate(context);
    // A key that is omitted, is not a string or is empty matches no declared name (EVAL-37).
    if (typeof key !== 'string' || !has(this.model.keys, key)) {
      throw new TierwiseError('E_UNKNOWN_KEY', `no such key: ${String(key)}`);
    }
    return { admitted, name: key };
  }

  /** The synchronous variant of resolve: steps 1 to 3, then step 5 (EVAL-11, EVAL-16). */
  resolveSync(context: unknown, key: unknown): Resolved {
    const { admitted, name } = this.prepare(context, key);
    if (neededLevels(this.model, admitted, [name]).length > 0) {
      throw new TierwiseError('E_PROVIDER_REQUIRED', 'resolving needs a provider call');
    }
    return this.build(name, admitted, NO_OUTCOMES);
  }

  /** resolve: all five steps of EVAL-16. */
  async resolve(context: unknown, key: unknown): Promise<Resolved> {
    const { admitted, name } = this.prepare(context, key);
    const outcomes = await this.providers.call(admitted, [name]);
    return this.build(name, admitted, outcomes);
  }

  /** Step 5 of EVAL-16: combination and provenance. It raises no error. */
  private build(name: string, admitted: AdmittedContext, outcomes: ProviderOutcomes): Resolved {
    const key = this.model.keys[name] as KeyDef;
    const slots = this.collect(name, key, admitted, outcomes);
    // A key that cannot carry a deny value (SVC-11, SVC-12) never reaches a bundle with `deny`. If one did, its
    // failure would contribute nothing, as under `skip`.
    const deny = key.onSourceFailure === 'deny' ? denyValue(key.combine, key.type) : undefined;
    const behaviour = deny === undefined ? 'skip' : 'deny';
    const contributions: Contribution[] = slots.map((slot) => {
      const entry: { -readonly [P in keyof Contribution]: Contribution[P] } = {
        scope: slot.scope.kind,
        name: slot.scope.name,
        effect: slot.failure === null ? label(key.combine, key.type, slot.value) : slot.failure,
      };
      if (slot.scope.node !== null) entry.node = slot.scope.node;
      if (slot.failure === null) {
        entry.value = cloneValue(slot.value);
      } else {
        if (deny !== undefined) entry.value = cloneValue(deny);
        entry.onSourceFailure = behaviour;
      }
      return entry;
    });

    const result: { -readonly [P in keyof Resolved]: Resolved[P] } = {
      key: name,
      strategy: key.combine,
      value: null,
      bundleVersion: this.bundleVersion,
      skippedScopes: admitted.skippedScopes.map((skipped) => ({ ...skipped })),
      contributions,
    };
    if (key.kind !== null) {
      result.kind = key.kind;
    }
    if (isCommutative(key.combine)) {
      result.value = this.combineCommutative(key, slots, deny);
    } else {
      const levels: Contributed<PathScope>[] = [];
      const axes: Contributed<PathScope>[] = [];
      for (const slot of slots) {
        if (slot.failure === null) {
          (slot.scope.kind === 'level' ? levels : axes).push({ scope: slot.scope, value: slot.value });
        }
      }
      const combined =
        key.combine === 'override'
          ? combineOverride(key.defaultValue, levels, axes, key.combineAcrossAxes)
          : combineDeepMerge(key.type, key.defaultValue, levels, axes, key.combineAcrossAxes);
      result.value = combined.value;
      result.source = combined.source === null ? 'DEFAULT' : sourceOf(combined.source);
    }
    return result;
  }

  /** Collects, in evaluation-path order, the scopes that contribute for a key (MERGE-14, MERGE-15). */
  private collect(name: string, key: KeyDef, admitted: AdmittedContext, outcomes: ProviderOutcomes): Slot[] {
    const slots: Slot[] = [];
    for (const scope of admitted.path) {
      if (!key.settableAt.includes(scope.name)) {
        continue;
      }
      if (scope.node !== null && isServiceSourced(this.model, scope.name)) {
        const outcome = outcomes.get(scope.name)?.get(name);
        if (outcome === undefined) {
          continue; // The provider left the key out, or no provider was called (SVC-36).
        }
        slots.push(
          outcome.kind === 'supplied'
            ? { scope, value: outcome.value, failure: null }
            : { scope, value: null, failure: outcome.label },
        );
      } else {
        const authored = this.authored(scope, name);
        if (authored !== undefined) {
          slots.push({ scope, value: authored, failure: null });
        }
      }
    }
    return slots;
  }

  private authored(scope: PathScope, key: string): JsonValue | undefined {
    const byNode = has(this.model.values, scope.name) ? this.model.values[scope.name] : undefined;
    const entries = byNode !== undefined && has(byNode, scope.node ?? '') ? byNode[scope.node ?? ''] : undefined;
    return entries !== undefined && has(entries, key) ? entries[key] : undefined;
  }

  private combineCommutative(key: KeyDef, slots: readonly Slot[], deny: JsonValue | undefined): JsonValue {
    const contributed: JsonValue[] = [];
    for (const slot of slots) {
      if (slot.failure === null) {
        contributed.push(slot.value);
      } else if (deny !== undefined) {
        contributed.push(deny);
      }
    }
    const value = contributed.length === 0 ? identity(key.combine, key.type) : combine(key.combine, key.type, contributed);
    return cloneValue(value);
  }
}
