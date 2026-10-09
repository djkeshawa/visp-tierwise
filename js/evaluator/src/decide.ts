// decide and decideSync (EVAL-8, EVAL-12, EVAL-17): context validation, feature lookup, FactSet validation,
// binding selection, the missing-fact precheck and rule evaluation under the FIRST hit policy. The steps run in
// order and the first failing step decides the error (EVAL-18, EVAL-20). A decision calls no provider (SVC-22).

import type { AdmittedContext, ContextValidator, PathScope } from './context.js';
import { TierwiseError } from './errors.js';
import { EntityScope, type ValidatedFacts, isMissing, validateFactSet } from './facts.js';
import type { JsonValue } from './json.js';
import type {
  BindingDef,
  ConditionDef,
  Dict,
  FactDef,
  FeatureDef,
  Model,
  PolicyDef,
  RuleDef,
} from './model.js';
import { compareCodePoints, has } from './text.js';
import type { Decision, DecisionBinding, Reason, TraceEntry } from './types.js';
import { cloneValue, scalarEquals } from './values.js';

interface Selected {
  readonly scope: PathScope;
  readonly binding: BindingDef;
}

/**
 * Selects the binding (BIND-14 to BIND-19): the candidate at the latest level of the evaluation path. An axis
 * carries no binding (BIND-15). The root binding is always a candidate.
 */
function selectBinding(feature: FeatureDef, path: readonly PathScope[]): Selected {
  for (let index = path.length - 1; index >= 0; index--) {
    const scope = path[index] as PathScope;
    if (scope.kind !== 'level') {
      continue;
    }
    const written = has(feature.bindings, scope.name) ? feature.bindings[scope.name] : undefined;
    const nodeKey = scope.node ?? '';
    if (written !== undefined && has(written, nodeKey)) {
      return { scope, binding: written[nodeKey] as BindingDef };
    }
  }
  throw new TypeError('the bundle has no root binding for the feature');
}

/** The resolved value of each setting of the selected policy (BIND-23, BIND-24). */
function resolveSettings(policy: PolicyDef, binding: BindingDef): ReadonlyMap<string, JsonValue> {
  const resolved = new Map<string, JsonValue>();
  for (const name of Object.keys(policy.settings)) {
    const given = has(binding.settings, name) ? binding.settings[name] : undefined;
    resolved.set(name, given !== undefined ? given : (policy.settings[name] as { defaultValue: JsonValue }).defaultValue);
  }
  return resolved;
}

/** Decides features. */
export class DecisionEngine {
  private readonly scopes = new Map<string, EntityScope>();

  constructor(
    private readonly model: Model,
    private readonly bundleVersion: string,
    private readonly validator: ContextValidator,
  ) {
    for (const name of Object.keys(model.features)) {
      const entity = (model.features[name] as FeatureDef).decidedPer;
      if (!this.scopes.has(entity)) {
        this.scopes.set(entity, new EntityScope(entity, model));
      }
    }
  }

  /** Runs the steps of EVAL-17. */
  decide(context: unknown, featureName: unknown, factSet: unknown): Decision {
    const admitted = this.validator.validate(context);
    // A feature that is omitted, is not a string or is empty matches no declared name (EVAL-37).
    if (typeof featureName !== 'string' || !has(this.model.features, featureName)) {
      throw new TierwiseError('E_UNKNOWN_FEATURE', `no such feature: ${String(featureName)}`);
    }
    const feature = this.model.features[featureName] as FeatureDef;
    const scope = this.scopes.get(feature.decidedPer) as EntityScope;
    const validated = validateFactSet(this.model, scope, factSet);
    const selected = selectBinding(feature, admitted.path);
    const policy = feature.policies[selected.binding.policy] as PolicyDef;
    const binding: DecisionBinding =
      selected.scope.node === null
        ? { level: selected.scope.name }
        : { level: selected.scope.name, node: selected.scope.node };

    const missing = policy.requiredFacts
      .filter((fact) => isMissing(this.model, scope, validated, fact))
      .sort(compareCodePoints);
    if (missing.length > 0) {
      return this.result(featureName, feature.ifFactMissing, admitted, binding, selected, { type: 'MISSING_FACT', facts: missing }, []);
    }
    return this.evaluateRules(featureName, policy, admitted, binding, selected, validated);
  }

  private evaluateRules(
    featureName: string,
    policy: PolicyDef,
    admitted: AdmittedContext,
    binding: DecisionBinding,
    selected: Selected,
    facts: ValidatedFacts,
  ): Decision {
    const settings = resolveSettings(policy, selected.binding);
    const trace: TraceEntry[] = [];
    for (const rule of policy.rules) {
      const failed = this.firstFailure(rule, facts, settings);
      if (failed === null) {
        trace.push({ id: rule.id, matched: true });
        return this.result(featureName, rule.then, admitted, binding, selected, { type: 'RULE', id: rule.id, name: rule.name }, trace);
      }
      trace.push({ id: rule.id, matched: false, failed });
    }
    return this.result(featureName, policy.defaultEffect, admitted, binding, selected, { type: 'DEFAULT' }, trace);
  }

  private result(
    feature: string,
    effect: JsonValue,
    admitted: AdmittedContext,
    binding: DecisionBinding,
    selected: Selected,
    reason: Reason,
    trace: readonly TraceEntry[],
  ): Decision {
    return {
      feature,
      effect: cloneValue(effect),
      bundleVersion: this.bundleVersion,
      skippedScopes: admitted.skippedScopes.map((skipped) => ({ ...skipped })),
      binding,
      policy: selected.binding.policy,
      reason,
      trace,
    };
  }

  /** The first condition, in code point order of fact name, that does not hold (RULE-8, RULE-54, RULE-55). */
  private firstFailure(
    rule: RuleDef,
    facts: ValidatedFacts,
    settings: ReadonlyMap<string, JsonValue>,
  ): { readonly fact: string; readonly op: string } | null {
    // The bundle gives the conditions in this order already (BUNDLE-96, BUNDLE-119); sorting a copy keeps the
    // rule correct whatever order the model holds.
    const conditions = [...rule.when].sort((a, b) => compareCodePoints(a.fact, b.fact));
    for (const condition of conditions) {
      if (!this.holds(condition, facts, settings)) {
        return { fact: condition.fact, op: condition.op };
      }
    }
    return null;
  }

  private holds(condition: ConditionDef, facts: ValidatedFacts, settings: ReadonlyMap<string, JsonValue>): boolean {
    const operand: JsonValue | undefined =
      condition.reference !== null ? settings.get(condition.reference) : condition.value;
    switch (condition.op) {
      case 'eq':
        return scalarEquals(facts.facts.get(condition.fact), operand);
      case 'in':
        return isMember(facts.facts.get(condition.fact), operand);
      case 'all_in':
        return this.quantified(condition.fact, facts, operand, true);
      default:
        return this.quantified(condition.fact, facts, operand, false);
    }
  }

  /** `all_in` and `any_in` over a child collection (RULE-31 to RULE-33). */
  private quantified(fact: string, facts: ValidatedFacts, operand: JsonValue | undefined, every: boolean): boolean {
    const entity = ((this.model.facts as Dict<FactDef>)[fact] as FactDef).entity;
    const children = facts.children.get(entity);
    if (children === undefined || children.length === 0) {
      return false;
    }
    return every
      ? children.every((child) => isMember(child.get(fact), operand))
      : children.some((child) => isMember(child.get(fact), operand));
  }
}

/** Membership of a value in an operand list; an empty list never matches (RULE-34). */
function isMember(value: JsonValue | undefined, operand: JsonValue | undefined): boolean {
  if (!Array.isArray(operand)) {
    return false;
  }
  return (operand as readonly JsonValue[]).some((element) => scalarEquals(value, element));
}
