// One HTTP request for the bundle. It sends the held version as If-None-Match, and it reads 200 and 304 answers. The
// timeout covers the body too, and a stop of the loader aborts the request (requirements §8, ETag = bundleVersion).

import { BundleLoaderError } from './errors.js';
import type { FetchFunction } from './types.js';

/** The answer to one request: the bundle bytes, or confirmation that the held version is current. */
export type FetchOutcome = { readonly kind: 'bundle'; readonly bytes: Uint8Array } | { readonly kind: 'not-modified' };

/** One request. */
export interface FetchRequest {
  readonly fetch: FetchFunction;
  readonly url: string;
  /** The version that the loader holds. It is sent as the entity tag, and omitted before the first load. */
  readonly heldVersion: string | undefined;
  /** The time allowed for the request, the body included. */
  readonly timeoutMs: number;
  /** Aborted when the loader stops. */
  readonly stopSignal: AbortSignal;
}

/**
 * Requests the bundle once.
 *
 * @throws BundleLoaderError E_BUNDLE_FETCH for a timeout, a network failure, or a status other than 200 or 304
 * @throws the signal's abort error when the loader stopped during the request
 */
export async function fetchBundle(request: FetchRequest): Promise<FetchOutcome> {
  const controller = new AbortController();
  let timedOut = false;
  const abortOnStop = (): void => controller.abort();
  request.stopSignal.addEventListener('abort', abortOnStop);
  if (request.stopSignal.aborted) controller.abort();
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, request.timeoutMs);

  const headers: Record<string, string> = {};
  if (request.heldVersion !== undefined) {
    headers['If-None-Match'] = `"${request.heldVersion}"`;
  }

  try {
    const response = await request.fetch(request.url, { headers, signal: controller.signal });
    if (response.status === 304) return { kind: 'not-modified' };
    if (response.status !== 200) {
      await response.arrayBuffer().catch(() => undefined);
      throw new BundleLoaderError('E_BUNDLE_FETCH', `the server answered HTTP ${response.status}`, {
        status: response.status,
      });
    }
    return { kind: 'bundle', bytes: new Uint8Array(await response.arrayBuffer()) };
  } catch (error) {
    if (error instanceof BundleLoaderError) throw error;
    if (timedOut) {
      throw new BundleLoaderError('E_BUNDLE_FETCH', `the request did not complete within ${request.timeoutMs} ms`, {
        cause: error,
      });
    }
    if (request.stopSignal.aborted) throw error;
    throw new BundleLoaderError('E_BUNDLE_FETCH', `the request failed: ${describe(error)}`, { cause: error });
  } finally {
    clearTimeout(timer);
    request.stopSignal.removeEventListener('abort', abortOnStop);
  }
}

function describe(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}
