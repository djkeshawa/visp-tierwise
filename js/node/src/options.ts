// Checks the options of createBundleLoader at the call, so that a mistake is reported where it is made and not at the
// first refresh. A wrong option is a TypeError, as for any programming error.

import type { EvaluatorOptions } from '@tierwise/evaluator';
import type { FetchFunction, SnapshotSource } from './types.js';

/** The longest delay that setTimeout honours. A longer one fires at once, so larger values are refused. */
const MAX_DELAY_MS = 2_147_483_647;

/** The defaults of the options that have one (requirements §8 and §14). */
export const DEFAULTS = Object.freeze({
  intervalMs: 30_000,
  staleAfterMs: 300_000,
  requestTimeoutMs: 10_000,
});

/** The options after they have been checked and defaulted. */
export interface CheckedOptions {
  readonly url: string;
  readonly intervalMs: number;
  readonly staleAfterMs: number;
  readonly requestTimeoutMs: number;
  readonly fetch: FetchFunction;
  readonly evaluatorOptions: EvaluatorOptions | undefined;
  readonly snapshot: SnapshotSource | undefined;
  readonly onError: ((error: unknown) => void) | undefined;
}

/**
 * Checks and defaults the options.
 *
 * @throws TypeError for an option that is missing, of the wrong type, or out of range
 */
export function checkOptions(input: unknown): CheckedOptions {
  if (typeof input !== 'object' || input === null) {
    throw new TypeError('createBundleLoader takes an options object');
  }
  const options = input as Record<string, unknown>;
  return Object.freeze({
    url: checkUrl(options.url),
    intervalMs: optionalInteger('intervalMs', options.intervalMs, DEFAULTS.intervalMs, MAX_DELAY_MS),
    staleAfterMs: optionalInteger('staleAfterMs', options.staleAfterMs, DEFAULTS.staleAfterMs, Number.MAX_SAFE_INTEGER),
    requestTimeoutMs: optionalInteger(
      'requestTimeoutMs',
      options.requestTimeoutMs,
      DEFAULTS.requestTimeoutMs,
      MAX_DELAY_MS,
    ),
    fetch: checkFetch(options.fetch),
    evaluatorOptions: checkEvaluatorOptions(options.evaluatorOptions),
    snapshot: checkSnapshot(options.snapshot),
    onError: checkOnError(options.onError),
  });
}

function checkUrl(value: unknown): string {
  if (typeof value !== 'string' || value === '') {
    throw new TypeError('url must be a non-empty string');
  }
  let parsed: URL;
  try {
    parsed = new URL(value);
  } catch {
    throw new TypeError('url must be an absolute URL');
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    throw new TypeError('url must use the http or https scheme');
  }
  return value;
}

function optionalInteger(name: string, value: unknown, fallback: number, max: number): number {
  if (value === undefined) return fallback;
  if (typeof value !== 'number' || !Number.isInteger(value) || value < 1 || value > max) {
    throw new TypeError(`${name} must be a positive integer no larger than ${max}`);
  }
  return value;
}

function checkFetch(value: unknown): FetchFunction {
  const fetchFunction = value === undefined ? globalThis.fetch : value;
  if (typeof fetchFunction !== 'function') {
    throw new TypeError('no fetch function: pass one as fetch, or run on Node 20 or later');
  }
  return fetchFunction as FetchFunction;
}

function checkEvaluatorOptions(value: unknown): EvaluatorOptions | undefined {
  if (value === undefined) return undefined;
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    throw new TypeError('evaluatorOptions must be an object');
  }
  return value as EvaluatorOptions;
}

function checkSnapshot(value: unknown): SnapshotSource | undefined {
  if (value === undefined) return undefined;
  if (typeof value === 'object' && value !== null) {
    const source = value as { bytes?: unknown; path?: unknown };
    if (source.bytes === undefined && typeof source.path === 'string' && source.path !== '') {
      return { path: source.path };
    }
    const bytes = source.bytes;
    if (
      source.path === undefined &&
      (bytes instanceof Uint8Array || bytes instanceof ArrayBuffer || typeof bytes === 'string')
    ) {
      return { bytes };
    }
  }
  throw new TypeError(
    'snapshot must be { bytes } (a Uint8Array, an ArrayBuffer or a string) or { path } (a file name)',
  );
}

function checkOnError(value: unknown): ((error: unknown) => void) | undefined {
  if (value === undefined) return undefined;
  if (typeof value !== 'function') {
    throw new TypeError('onError must be a function');
  }
  return value as (error: unknown) => void;
}
