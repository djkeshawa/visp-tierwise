// The errors that the loader raises itself. A bundle that fails a check raises the evaluator's TierwiseError, which the
// loader passes on unchanged.

/**
 * The codes of the loader's own errors.
 *
 * - `E_BUNDLE_FETCH`: a request failed, timed out, or answered with a status other than 200 or 304 (or a 304 with no
 *   bundle to confirm).
 * - `E_BUNDLE_NOT_LOADED`: a call needs the current bundle, and none has been loaded yet.
 * - `E_LOADER_STOPPED`: the loader was stopped, so the call cannot complete.
 */
export type BundleLoaderErrorCode = 'E_BUNDLE_FETCH' | 'E_BUNDLE_NOT_LOADED' | 'E_LOADER_STOPPED';

/** An error that the bundle loader raises. The `code` is stable; the message is for people and may change. */
export class BundleLoaderError extends Error {
  /** The stable code of the error. */
  readonly code: BundleLoaderErrorCode;
  /** The HTTP status of a response that was not 200 or 304. Undefined for a network failure or a timeout. */
  readonly status: number | undefined;
  /** The underlying error, when there is one. */
  readonly cause: unknown;

  constructor(code: BundleLoaderErrorCode, message: string, options: { status?: number; cause?: unknown } = {}) {
    super(`${code}: ${message}`);
    this.name = 'BundleLoaderError';
    this.code = code;
    this.status = options.status;
    this.cause = options.cause;
  }
}
