// The public API of @tierwise/node: a bundle loader that fetches the bundle over HTTP (ETag refresh, last-known-good,
// atomic swap) or starts from a snapshot, and serves the evaluator of the current bundle. The evaluator's error class
// and result types are re-exported, so that a caller needs one import. Browser evaluation is for display only: enforce
// every decision on a server.

export { createBundleLoader } from './loader.js';
export { BundleLoaderError } from './errors.js';
export type { BundleLoaderErrorCode } from './errors.js';
export type { BundleLoader, BundleLoaderOptions, BundleStatus, FetchFunction, SnapshotSource } from './types.js';
export { TierwiseError } from '@tierwise/evaluator';
export type {
  Context,
  Decision,
  EvaluationEvent,
  EvaluationListener,
  Evaluator,
  EvaluatorOptions,
  FactSet,
  ProviderFunction,
  ProviderRegistration,
  Resolved,
} from '@tierwise/evaluator';
