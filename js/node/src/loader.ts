// createBundleLoader: fetches a bundle over HTTP with ETag refresh, swaps the evaluator atomically and keeps the last
// good bundle when a refresh fails (requirements §8).

import { createEvaluator, loadBundle } from '@visplabs/evaluator';
import type { Context, Decision, Evaluator, FactSet, LoadedBundle, Resolved } from '@visplabs/evaluator';
import { BundleLoaderError } from './errors.js';
import { fetchBundle } from './fetch.js';
import { checkOptions } from './options.js';
import { readSnapshot } from './snapshot.js';
import type { BundleLoader, BundleLoaderOptions, BundleStatus } from './types.js';

/** The evaluator that the loader holds, with the bundle that it was made from. */
interface Installed {
  readonly evaluator: Evaluator;
  readonly bundleVersion: string;
  readonly loadedAt: number;
}

/**
 * Creates a bundle loader. Nothing is fetched until start or refresh is called.
 *
 * @param options the URL and the optional settings; see BundleLoaderOptions
 * @throws TypeError for an option that is missing, of the wrong type, or out of range
 */
export function createBundleLoader(options: BundleLoaderOptions): BundleLoader {
  const config = checkOptions(options);
  const abort = new AbortController();
  let current: Installed | undefined;
  let lastCheckedAt: number | undefined;
  let refreshFailures = 0;
  let stopped = false;
  let timer: ReturnType<typeof setTimeout> | undefined;
  let starting: Promise<void> | undefined;
  let checking: Promise<void> | undefined;

  /**
   * Makes the evaluator of a bundle the current one, unless that version is already held. The swap is one assignment:
   * a call that began earlier keeps the evaluator it began with, and a later call gets the new one.
   *
   * @throws the evaluator's error when the options do not fit the bundle, for example E_PROVIDER_MISSING
   */
  function install(bundle: LoadedBundle): void {
    if (current?.bundleVersion === bundle.bundleVersion) return;
    const evaluator = createEvaluator(bundle, config.evaluatorOptions);
    current = Object.freeze({ evaluator, bundleVersion: bundle.bundleVersion, loadedAt: Date.now() });
  }

  function requireCurrent(): Evaluator {
    if (current === undefined) {
      throw new BundleLoaderError(
        'E_BUNDLE_NOT_LOADED',
        'no bundle is loaded yet; call start() and wait for it to resolve',
      );
    }
    return current.evaluator;
  }

  function stoppedError(): BundleLoaderError {
    return new BundleLoaderError('E_LOADER_STOPPED', 'the loader has been stopped');
  }

  function report(error: unknown): void {
    const onError = config.onError;
    if (onError === undefined) return;
    try {
      onError(error);
    } catch {
      // A failing callback must not disturb the loader.
    }
  }

  /** One check: a request, then an install or a confirmation. A failure is reported and keeps the held bundle. */
  async function attempt(): Promise<void> {
    try {
      const outcome = await fetchBundle({
        fetch: config.fetch,
        url: config.url,
        heldVersion: current?.bundleVersion,
        timeoutMs: config.requestTimeoutMs,
        stopSignal: abort.signal,
      });
      if (stopped) throw stoppedError();
      if (outcome.kind === 'bundle') {
        install(loadBundle(outcome.bytes));
      } else if (current === undefined) {
        throw new BundleLoaderError('E_BUNDLE_FETCH', 'the server answered 304 Not Modified, but no bundle is held');
      }
      lastCheckedAt = Date.now();
    } catch (error) {
      // A stop is not a failure of the server, so it is neither counted nor reported.
      if (stopped) throw stoppedError();
      refreshFailures += 1;
      report(error);
      throw error;
    }
  }

  /** Checks the server now. A check already in flight is shared, so two callers never fetch twice at once. */
  function refresh(): Promise<void> {
    if (stopped) return Promise.reject(stoppedError());
    if (checking === undefined) {
      checking = attempt().finally(() => {
        checking = undefined;
      });
    }
    return checking;
  }

  /** Arms the next poll. The timer is armed only after a check ends, so checks never overlap. */
  function schedule(): void {
    if (stopped) return;
    timer = setTimeout(tick, config.intervalMs);
  }

  function tick(): void {
    timer = undefined;
    // A failed check was already reported, and the next one is armed either way.
    refresh().then(schedule, schedule);
  }

  async function startOnce(): Promise<void> {
    if (config.snapshot !== undefined) {
      const bytes = await readSnapshot(config.snapshot);
      if (stopped) throw stoppedError();
      install(loadBundle(bytes));
      // The snapshot already serves calls, so the first check runs in the background.
      refresh().catch(() => undefined).then(schedule);
      return;
    }
    await refresh();
    schedule();
  }

  function start(): Promise<void> {
    if (stopped) return Promise.reject(stoppedError());
    if (starting === undefined) {
      starting = startOnce().catch((error: unknown) => {
        // A failed start may be retried, and the failure reaches the caller.
        starting = undefined;
        throw error;
      });
    }
    return starting;
  }

  function stop(): void {
    if (stopped) return;
    stopped = true;
    if (timer !== undefined) clearTimeout(timer);
    timer = undefined;
    abort.abort();
  }

  function status(): BundleStatus {
    const stalenessMs = lastCheckedAt === undefined ? undefined : Math.max(0, Date.now() - lastCheckedAt);
    return Object.freeze({
      bundleVersion: current?.bundleVersion,
      loadedAt: current?.loadedAt,
      lastCheckedAt,
      stalenessMs,
      stale: stalenessMs === undefined || stalenessMs > config.staleAfterMs,
      refreshFailures,
    });
  }

  // Each call reads `current` when it is made, so the evaluator it uses is fixed from then on. The asynchronous
  // helpers are async functions, so that a missing bundle rejects the promise and does not throw.
  const loader: BundleLoader = {
    start,
    stop,
    refresh,
    current: () => current?.evaluator,
    status,
    async resolve(context: Context | undefined, key: string): Promise<Resolved> {
      return requireCurrent().resolve(context, key);
    },
    resolveSync(context: Context | undefined, key: string): Resolved {
      return requireCurrent().resolveSync(context, key);
    },
    async decide(context: Context | undefined, feature: string, facts?: FactSet): Promise<Decision> {
      return requireCurrent().decide(context, feature, facts);
    },
    decideSync(context: Context | undefined, feature: string, facts?: FactSet): Decision {
      return requireCurrent().decideSync(context, feature, facts);
    },
  };
  return Object.freeze(loader);
}
