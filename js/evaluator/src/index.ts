// The public API of @tierwise/evaluator: load a bundle, create an evaluator, resolve and decide. Callers go only
// through resolve and decide. No API exposes one scope's raw value (EVAL-13).

export { loadBundle, SUPPORTED_BUNDLE_FORMAT_VERSION, SUPPORTED_SPEC_VERSION } from './bundle.js';
export { createEvaluator } from './evaluator.js';
export { TierwiseError, ERROR_CODES } from './errors.js';
export type {
  Context,
  Contribution,
  ContributionLabel,
  Decision,
  DecisionBinding,
  ErrorCode,
  EvaluationEvent,
  EvaluationEventType,
  EvaluationListener,
  Evaluator,
  EvaluatorOptions,
  FactSet,
  FactValue,
  JsonValue,
  LoadedBundle,
  ProviderFunction,
  ProviderRegistration,
  ProviderResult,
  Reason,
  Resolved,
  ScopeKind,
  SkippedScope,
  Source,
  TraceEntry,
} from './types.js';
