// Providers, the options of createEvaluator and the listener (section 6, EVAL-4, EVAL-15, EVAL-27, EVAL-28,
// EVAL-41 to EVAL-45). A provider is an asynchronous function for one service-sourced level. The evaluator
// calls it at most once per evaluation call (SVC-19), with a timeout (SVC-38), and turns each outcome into a
// source outcome for each key being resolved (SVC-23 to SVC-37).

import { TierwiseError } from './errors.js';
import type { JsonValue } from './json.js';
import type { KeyDef, Model } from './model.js';
import { type AdmittedContext, type PathScope, isServiceSourced } from './context.js';
import { compareCodePoints, has, isPlainObject } from './text.js';
import type {
  EvaluationEvent,
  EvaluationEventType,
  EvaluationListener,
  ProviderFunction,
} from './types.js';
import { validatePartial, validateValue } from './values.js';

/** A registered provider with its validated timeout. */
export interface Registration {
  readonly fn: ProviderFunction;
  readonly timeoutMs: number;
}

/** The checked options of an evaluator. */
export interface CheckedOptions {
  readonly registrations: ReadonlyMap<string, Registration>;
  readonly listener: EvaluationListener | null;
}

function options(message: string): TierwiseError {
  return new TierwiseError('E_EVALUATOR_OPTIONS', message);
}

const OPTION_MEMBERS = ['providers', 'listener'];

/**
 * Checks the options of create evaluator in the order of EVAL-15: the options and each timeout (EVAL-28), the
 * provider registrations (EVAL-27), then the providers that the bundle needs (SVC-14, EVAL-26).
 *
 * @param supplied the options argument; `undefined` counts as empty options (EVAL-28)
 * @throws TierwiseError E_EVALUATOR_OPTIONS or E_PROVIDER_MISSING
 */
export function checkOptions(model: Model, supplied: unknown): CheckedOptions {
  if (supplied === undefined) {
    return checkOptions(model, {});
  }
  if (!isPlainObject(supplied)) {
    throw options('the options are not an object');
  }
  for (const member of Object.keys(supplied)) {
    if (!OPTION_MEMBERS.includes(member)) {
      throw options(`unknown option: ${member}`);
    }
  }
  const listener = supplied.listener;
  if (listener !== undefined && typeof listener !== 'function') {
    throw options('the listener is not a function');
  }
  const providers = supplied.providers === undefined ? {} : supplied.providers;
  if (!isPlainObject(providers)) {
    throw options('providers is not an object');
  }
  // Step 1 of EVAL-15: every registration and its timeout.
  const registrations = new Map<string, Registration>();
  for (const level of Object.keys(providers)) {
    const registration = providers[level];
    if (!isPlainObject(registration)) {
      throw options(`the registration for ${level} is not an object with fn and timeoutMs`);
    }
    for (const member of Object.keys(registration)) {
      if (member !== 'fn' && member !== 'timeoutMs') {
        throw options(`the registration for ${level} has an unknown member: ${member}`);
      }
    }
    if (typeof registration.fn !== 'function') {
      throw options(`the provider for ${level} is not a function`);
    }
    const timeoutMs = registration.timeoutMs;
    if (typeof timeoutMs !== 'number' || !Number.isSafeInteger(timeoutMs) || timeoutMs <= 0) {
      throw options(`the timeout of the provider for ${level} is not a positive integer number of milliseconds`);
    }
    registrations.set(level, { fn: registration.fn as ProviderFunction, timeoutMs });
  }
  // Step 2: each registered level must be a declared, service-sourced level.
  for (const level of registrations.keys()) {
    const declared = model.hierarchy.levels.includes(level) || model.hierarchy.axes.includes(level);
    if (!declared) {
      throw options(`the bundle does not declare the level ${level}`);
    }
    if (!isServiceSourced(model, level)) {
      throw options(`the level ${level} is not service-sourced`);
    }
  }
  // Step 3: exactly one provider for each service-sourced level (SVC-14).
  for (const name of Object.keys(model.hierarchy.scopes)) {
    if (isServiceSourced(model, name) && !registrations.has(name)) {
      throw new TierwiseError('E_PROVIDER_MISSING', `no provider is registered for the service-sourced level ${name}`);
    }
  }
  return { registrations, listener: (listener as EvaluationListener | undefined) ?? null };
}

/** Delivers events to the listener of an evaluator. A listener that throws is ignored (EVAL-44). */
export class EventSink {
  constructor(
    private readonly listener: EvaluationListener | null,
    private readonly bundleVersion: string,
  ) {}

  /** The load event of create evaluator (EVAL-43). */
  loaded(): void {
    this.deliver({ type: 'BUNDLE_LOADED', bundleVersion: this.bundleVersion });
  }

  /** An event about one key at one level and node (EVAL-41, SVC-43 to SVC-47). */
  forKey(type: EvaluationEventType, level: string, node: string, key: string): void {
    this.deliver({ type, bundleVersion: this.bundleVersion, level, node, key });
  }

  private deliver(event: EvaluationEvent): void {
    if (this.listener === null) {
      return;
    }
    try {
      const outcome: unknown = this.listener(event);
      if (typeof outcome === 'object' && outcome !== null && typeof (outcome as PromiseLike<unknown>).then === 'function') {
        // An asynchronous listener that rejects is ignored too.
        (outcome as PromiseLike<unknown>).then(undefined, () => undefined);
      }
    } catch {
      // A listener that throws changes no result and raises no error (EVAL-44).
    }
  }
}

/** The outcome of one key at one service-sourced level: a valid value, or a source failure (SVC-26). */
export type SourceOutcome =
  | { readonly kind: 'supplied'; readonly value: JsonValue }
  | { readonly kind: 'failed'; readonly label: 'UNAVAILABLE' | 'INVALID' };

/** The outcomes by level name, then by key name. A key with no entry contributes nothing (SVC-36). */
export type ProviderOutcomes = ReadonlyMap<string, ReadonlyMap<string, SourceOutcome>>;

export const NO_OUTCOMES: ProviderOutcomes = new Map();

/** The largest delay that setTimeout accepts; a longer one would fire at once. */
const MAX_TIMER_MS = 2147483647;

const FAILURE = Symbol('failed');

/**
 * Calls one provider and settles with its result, or with FAILURE when it throws, rejects or does not return in
 * time (SVC-23, SVC-39). The timeout is measured from the start of the call until the result is available (SVC-41),
 * so a result that arrives after the timeout is not used (SVC-40), even when the timer has not fired yet because
 * the provider blocked the thread.
 */
function invoke(registration: Registration, node: string): Promise<unknown> {
  return new Promise((resolve) => {
    let settled = false;
    const finish = (outcome: unknown): void => {
      if (!settled) {
        settled = true;
        clearTimeout(timer);
        resolve(outcome);
      }
    };
    const timer = setTimeout(() => finish(FAILURE), Math.min(registration.timeoutMs, MAX_TIMER_MS));
    const started = Date.now();
    const arrive = (result: unknown): void => finish(Date.now() - started > registration.timeoutMs ? FAILURE : result);
    try {
      Promise.resolve(registration.fn(node)).then(arrive, () => finish(FAILURE));
    } catch {
      finish(FAILURE);
    }
  });
}

/**
 * Finds the service-sourced levels that need a provider call: an admitted node for the level and at least one
 * key being resolved that is settable there (SVC-17, SVC-42).
 */
export function neededLevels(model: Model, admitted: AdmittedContext, keys: readonly string[]): PathScope[] {
  return admitted.path.filter(
    (scope) =>
      scope.node !== null &&
      isServiceSourced(model, scope.name) &&
      keysAt(model, scope.name, keys).length > 0,
  );
}

function keysAt(model: Model, level: string, keys: readonly string[]): string[] {
  return keys.filter((key) => (model.keys[key] as KeyDef).settableAt.includes(level));
}

/** Calls the providers that the call needs and settles every outcome. */
export class ProviderCalls {
  constructor(
    private readonly model: Model,
    private readonly registrations: ReadonlyMap<string, Registration>,
    private readonly events: EventSink,
  ) {}

  /**
   * Calls the provider of each needed level once, all at the same time, and classifies the results
   * (SVC-17 to SVC-37). The outcomes do not depend on the order or timing of the calls (SVC-21).
   */
  async call(admitted: AdmittedContext, keys: readonly string[]): Promise<ProviderOutcomes> {
    const needed = neededLevels(this.model, admitted, keys);
    if (needed.length === 0) {
      return NO_OUTCOMES;
    }
    const running = needed.map((level) => {
      const registration = this.registrations.get(level.name) as Registration;
      return invoke(registration, level.node as string);
    });
    const results = await Promise.all(running);
    const outcomes = new Map<string, ReadonlyMap<string, SourceOutcome>>();
    needed.forEach((level, index) => {
      outcomes.set(level.name, this.settle(level, keysAt(this.model, level.name, keys), keys, results[index]));
    });
    return outcomes;
  }

  private settle(
    level: PathScope,
    keysAtLevel: readonly string[],
    requested: readonly string[],
    result: unknown,
  ): ReadonlyMap<string, SourceOutcome> {
    const outcomes = new Map<string, SourceOutcome>();
    const pending: Array<readonly [EvaluationEventType, string]> = [];
    let usable = result !== FAILURE && isPlainObject(result);
    if (usable) {
      try {
        this.classify(level, keysAtLevel, requested, result as { readonly [name: string]: unknown }, outcomes, pending);
      } catch {
        // A result that cannot be read as a mapping of entries counts as no mapping (SVC-25).
        usable = false;
      }
    }
    if (!usable) {
      outcomes.clear();
      pending.length = 0;
      for (const key of keysAtLevel) {
        outcomes.set(key, { kind: 'failed', label: 'UNAVAILABLE' });
        pending.push(['SOURCE_UNAVAILABLE', key]);
      }
    }
    for (const [type, key] of pending) {
      this.events.forKey(type, level.name, level.node as string, key);
    }
    return outcomes;
  }

  private classify(
    level: PathScope,
    keysAtLevel: readonly string[],
    requested: readonly string[],
    result: { readonly [name: string]: unknown },
    outcomes: Map<string, SourceOutcome>,
    pending: Array<readonly [EvaluationEventType, string]>,
  ): void {
    // The entries of the result are classified in a fixed order, so the events do not depend on the order
    // in which the provider built its mapping.
    const names = Object.keys(result).sort(compareCodePoints);
    for (const name of names) {
      if (!has(this.model.keys, name)) {
        pending.push(['UNKNOWN_KEY', name]); // SVC-27, SVC-45
      } else if (requested.includes(name) && !(this.model.keys[name] as KeyDef).settableAt.includes(level.name)) {
        pending.push(['IGNORED_ENTRY', name]); // SVC-29, SVC-47
      }
    }
    for (const key of keysAtLevel) {
      if (!has(result, key)) {
        continue; // SVC-36: no entry, no contribution and no failure.
      }
      const valid = this.validate(key, result[key]);
      if (valid !== undefined) {
        outcomes.set(key, { kind: 'supplied', value: valid });
      } else {
        outcomes.set(key, { kind: 'failed', label: 'INVALID' });
        pending.push(['SOURCE_INVALID', key]);
      }
    }
  }

  /** Validates a provider value against its key (SVC-31, SVC-32, SVC-35). */
  private validate(key: string, value: unknown): JsonValue | undefined {
    const definition = this.model.keys[key] as KeyDef;
    if (definition.combine !== 'deep_merge') {
      return validateValue(definition.type, value);
    }
    const root = this.model.hierarchy.levels[0] as string;
    const rootValues = has(this.model.values, root) ? (this.model.values[root] as { [node: string]: { [key: string]: JsonValue } })[''] : undefined;
    const starting =
      definition.defaultValue !== null
        ? definition.defaultValue
        : rootValues !== undefined && has(rootValues, key)
          ? rootValues[key]
          : undefined;
    return validatePartial(definition.type, value, starting);
  }
}
