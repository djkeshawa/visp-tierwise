// The public types of @visplabs/node: the options of createBundleLoader, the status of the held bundle and the
// loader itself. The evaluation types come from @visplabs/evaluator and are not redefined here.

import type { Context, Decision, Evaluator, EvaluatorOptions, FactSet, Resolved } from '@visplabs/evaluator';

/**
 * The function that fetches the bundle. It has the signature of the built-in fetch, and it is called with the URL as
 * a string. A replacement must resolve to a Response.
 */
export type FetchFunction = (input: string, init?: RequestInit) => Promise<Response>;

/**
 * A startup snapshot: the bytes of a bundle that the application holds, or the path of a file that holds them. A
 * snapshot lets start resolve before the server has answered, and the first check then runs in the background.
 */
export type SnapshotSource = { readonly bytes: Uint8Array | ArrayBuffer | string } | { readonly path: string };

/** The options of createBundleLoader. Only `url` is required. Durations are in milliseconds. */
export interface BundleLoaderOptions {
  /** The http or https URL of the bundle. It answers 200 with the bundle bytes, or 304 when the entity tag matches. */
  readonly url: string;
  /**
   * The wait between two checks, counted from the end of one check to the start of the next. A positive integer no
   * larger than 2^31 - 1. Default 30000.
   */
  readonly intervalMs?: number;
  /**
   * The age of the last confirmed check after which the bundle counts as stale. A positive integer. Default 300000.
   */
  readonly staleAfterMs?: number;
  /**
   * The time allowed for one request, including the body. A positive integer no larger than 2^31 - 1. Default 10000.
   */
  readonly requestTimeoutMs?: number;
  /** The fetch function. Default: the global fetch. */
  readonly fetch?: FetchFunction;
  /** The options of every evaluator that the loader creates: the providers and the listener. */
  readonly evaluatorOptions?: EvaluatorOptions;
  /** A bundle to serve before the first check. Without one, start waits for the server. */
  readonly snapshot?: SnapshotSource;
  /**
   * Called with each failed refresh, including a failed first load. A throw from it is ignored, because it must not
   * disturb the loader.
   */
  readonly onError?: (error: unknown) => void;
}

/** What the application needs for its own metrics: the version, the age and the failures (requirements §8). */
export interface BundleStatus {
  /** The version of the bundle that is served, or undefined before any bundle is loaded. */
  readonly bundleVersion: string | undefined;
  /** When the served evaluator was installed, in milliseconds since the epoch. Undefined before any load. */
  readonly loadedAt: number | undefined;
  /**
   * When the server last confirmed the served version, with a 200 or a 304, in milliseconds since the epoch. A
   * snapshot does not count as a confirmation. Undefined until one occurs.
   */
  readonly lastCheckedAt: number | undefined;
  /** The milliseconds since lastCheckedAt. Undefined until a check has been confirmed. */
  readonly stalenessMs: number | undefined;
  /** True when no check has been confirmed yet, or when stalenessMs is larger than staleAfterMs. */
  readonly stale: boolean;
  /** The number of refresh attempts that failed since the loader was created. */
  readonly refreshFailures: number;
}

/**
 * A bundle loader. The evaluation methods always use the evaluator of the bundle that is current at the moment of the
 * call. A call that has started keeps the evaluator it started with, so a swap never changes a call in flight.
 */
export interface BundleLoader {
  /**
   * Loads the bundle. Without a snapshot, it resolves after the first successful load and rejects if that load fails;
   * then polling does not start. With a snapshot, it resolves once the snapshot is installed, and the first check runs
   * in the background. Calling it again returns the same promise.
   */
  start(): Promise<void>;
  /** Stops polling and aborts a request in flight. It is final: a stopped loader cannot be started again. */
  stop(): void;
  /**
   * Checks the server now. It resolves when the held bundle is confirmed or replaced, and rejects when the check fails,
   * in which case the held bundle is kept. A check already in flight is shared.
   */
  refresh(): Promise<void>;
  /** The evaluator of the current bundle, or undefined before a bundle is loaded. Hold it to pin one bundle. */
  current(): Evaluator | undefined;
  /** The status of the held bundle. */
  status(): BundleStatus;
  /** Resolves a key with the current evaluator (Evaluator.resolve). Rejects with E_BUNDLE_NOT_LOADED before a load. */
  resolve(context: Context | undefined, key: string): Promise<Resolved>;
  /** Resolves a key without a provider call (Evaluator.resolveSync). Throws E_BUNDLE_NOT_LOADED before a load. */
  resolveSync(context: Context | undefined, key: string): Resolved;
  /** Decides a feature with the current evaluator (Evaluator.decide). */
  decide(context: Context | undefined, feature: string, facts?: FactSet): Promise<Decision>;
  /** Decides a feature with the current evaluator (Evaluator.decideSync). Throws E_BUNDLE_NOT_LOADED before a load. */
  decideSync(context: Context | undefined, feature: string, facts?: FactSet): Decision;
}
