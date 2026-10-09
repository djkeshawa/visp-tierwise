// create evaluator (EVAL-4, EVAL-5, EVAL-6, EVAL-15, EVAL-43): checks the options, builds the evaluator and
// emits the load event. It is synchronous and calls no provider.

import { modelOf } from './bundle.js';
import { ContextValidator } from './context.js';
import { DecisionEngine } from './decide.js';
import { EventSink, ProviderCalls, checkOptions } from './providers.js';
import { Resolver } from './resolve.js';
import type { Context, Decision, Evaluator, EvaluatorOptions, FactSet, LoadedBundle, Resolved } from './types.js';

/**
 * Creates an evaluator for a loaded bundle.
 *
 * @param bundle a bundle returned by loadBundle
 * @param options one provider registration (function and timeout) for each service-sourced level of the bundle,
 *     and an optional listener. An omitted argument counts as empty options (EVAL-28).
 * @throws TierwiseError E_EVALUATOR_OPTIONS or E_PROVIDER_MISSING
 */
export function createEvaluator(bundle: LoadedBundle, options?: EvaluatorOptions): Evaluator {
  const model = modelOf(bundle);
  const checked = checkOptions(model, options);
  const events = new EventSink(checked.listener, bundle.bundleVersion);
  const validator = new ContextValidator(model);
  const resolver = new Resolver(model, bundle.bundleVersion, validator, new ProviderCalls(model, checked.registrations, events));
  const decisions = new DecisionEngine(model, bundle.bundleVersion, validator);
  events.loaded();
  return Object.freeze({
    bundleVersion: bundle.bundleVersion,
    resolve: (context: Context | undefined, key: string): Promise<Resolved> => resolver.resolve(context, key),
    resolveSync: (context: Context | undefined, key: string): Resolved => resolver.resolveSync(context, key),
    decide: async (context: Context | undefined, feature: string, facts?: FactSet): Promise<Decision> =>
      decisions.decide(context, feature, facts),
    decideSync: (context: Context | undefined, feature: string, facts?: FactSet): Decision =>
      decisions.decide(context, feature, facts),
  });
}
