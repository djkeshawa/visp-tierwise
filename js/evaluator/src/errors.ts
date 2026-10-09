// The error type of the evaluator and its stable codes (EVAL-22, EVAL-38).

/** The eleven codes that the evaluation operations may raise (EVAL-38). */
export type ErrorCode =
  | 'E_BUNDLE_INTEGRITY'
  | 'E_BUNDLE_UNSUPPORTED'
  | 'E_PROVIDER_MISSING'
  | 'E_PROVIDER_REQUIRED'
  | 'E_CONTEXT_INVALID'
  | 'E_UNKNOWN_SCOPE'
  | 'E_UNKNOWN_NODE'
  | 'E_UNKNOWN_KEY'
  | 'E_UNKNOWN_FEATURE'
  | 'E_FACTS_INVALID'
  | 'E_EVALUATOR_OPTIONS';

/** Every code that an evaluation operation may raise. */
export const ERROR_CODES: readonly ErrorCode[] = Object.freeze([
  'E_BUNDLE_INTEGRITY',
  'E_BUNDLE_UNSUPPORTED',
  'E_PROVIDER_MISSING',
  'E_PROVIDER_REQUIRED',
  'E_CONTEXT_INVALID',
  'E_UNKNOWN_SCOPE',
  'E_UNKNOWN_NODE',
  'E_UNKNOWN_KEY',
  'E_UNKNOWN_FEATURE',
  'E_FACTS_INVALID',
  'E_EVALUATOR_OPTIONS',
]);

/**
 * An evaluation error. The `code` is normative and stable. The message is for people and may change
 * (EVAL-22).
 */
export class TierwiseError extends Error {
  readonly code: ErrorCode;

  constructor(code: ErrorCode, message: string) {
    super(`${code}: ${message}`);
    this.name = 'TierwiseError';
    this.code = code;
  }
}

/** Creates the error for a bundle that fails a load check other than the version check. */
export function integrity(message: string): TierwiseError {
  return new TierwiseError('E_BUNDLE_INTEGRITY', message);
}
