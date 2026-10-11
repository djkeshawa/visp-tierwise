# @visplabs/node

The bundle loader for Node 20 or later. It fetches a compiled bundle over HTTP with `fetch`, refreshes it with an
ETag, swaps the evaluator atomically, and keeps the last good bundle when a refresh fails. It evaluates with
[`@visplabs/evaluator`](../evaluator/README.md), and it has no other runtime dependency.

> **Browser use is for display only.** Enforce every decision on a server with the same bundle. This package is for
> servers.

This package is private. Phase reviews precede publication.

## Usage

```ts
import { createBundleLoader, TierwiseError } from '@visplabs/node';

const loader = createBundleLoader({
  url: 'https://config.example.test/tierwise/bundle.json',
  intervalMs: 30_000, // the wait between two checks (default 30 s)
  staleAfterMs: 300_000, // staleness threshold for your alerts (default 5 min)
  evaluatorOptions: {
    // One provider for each service-sourced level of the bundle, each with its own timeout.
    providers: { account: { fn: async (nodeId) => fetchAccountValues(nodeId), timeoutMs: 200 } },
    listener: (event) => metrics.record(event), // optional
  },
  onError: (error) => log.warn({ err: error }, 'bundle refresh failed'), // optional
});

await loader.start(); // resolves after the first load; rejects if that load fails

const ctx = { levels: { region: 'coastal' }, axes: { brand: 'harbor_foods' } };

const r = await loader.resolve(ctx, 'some.key');
const d = await loader.decide(ctx, 'some.feature', { facts: { 'entity.attribute': true } });

const status = loader.status();
status.bundleVersion; // the version being served
status.stalenessMs; // milliseconds since the server last confirmed that version
status.stale; // true before a confirmed check, or when stalenessMs exceeds staleAfterMs

loader.stop(); // at shutdown
```

The names above are placeholders. The package itself knows no domain names.

### Calls

`resolve`, `resolveSync`, `decide` and `decideSync` take the same arguments and return the same results as the
evaluator's methods (see the [evaluator README](../evaluator/README.md)). Each call uses the evaluator of the bundle
that is current when the call is made. A call that has started finishes on that evaluator, so a swap never changes a
call in flight.

Before a bundle is loaded, `resolve` and `decide` reject, and `resolveSync` and `decideSync` throw, with
`E_BUNDLE_NOT_LOADED`.

`current()` returns the evaluator of the current bundle, or `undefined`. Hold it to pin one bundle across several
calls, for example across one request.

### Options

| Option | Default | Meaning |
| --- | --- | --- |
| `url` | required | An `http:` or `https:` URL. It answers `200` with the bundle bytes, or `304` when the entity tag matches. |
| `intervalMs` | `30000` | The wait between two checks. It is counted from the end of one check to the start of the next, so checks never overlap. Up to 2^31 - 1. |
| `staleAfterMs` | `300000` | The age of the last confirmed check after which the bundle counts as stale. |
| `requestTimeoutMs` | `10000` | The time allowed for one request, the body included. Up to 2^31 - 1. |
| `fetch` | `globalThis.fetch` | The fetch function. A replacement has the same signature. |
| `evaluatorOptions` | none | The options of every evaluator that the loader makes: providers and listener. |
| `snapshot` | none | `{ bytes }` or `{ path }`. A bundle to serve before the first check (see below). |
| `onError` | none | Called with each failed refresh, including a failed first load. A throw from it is ignored. |

A wrong option raises a `TypeError` when the loader is created.

The loader sends only `If-None-Match`. For a server that needs credentials, pass a `fetch` that adds them:

```ts
fetch: (url, init) => fetch(url, { ...init, headers: { ...init?.headers, Authorization: 'Bearer ' + token() } }),
```

### Loading and refreshing

- **Entity tag.** The held version is sent as `If-None-Match: "<bundleVersion>"`. The version in the bundle itself is
  the truth; the `ETag` header of a response is not read. A `304` confirms the held version. A `200` for a version
  that is already held keeps the same evaluator.
- **Atomic swap.** A new bundle is checked by `loadBundle`, and its evaluator is made with `evaluatorOptions`, before
  it replaces the current one. The replacement is one assignment.
- **Last known good.** A refresh that fails keeps the current bundle, reports the error to `onError` and counts it in
  `status().refreshFailures`. A failure is any of these: a network error, a timeout, a status other than `200` or `304`,
  a response that is not a valid bundle (`E_BUNDLE_INTEGRITY`, `E_BUNDLE_UNSUPPORTED`), or a bundle that the options
  cannot serve (`E_PROVIDER_MISSING`, `E_EVALUATOR_OPTIONS`). Checks continue at the normal interval, with no
  backoff.
- **Polling.** `start` arms the poll after a successful load. `refresh()` checks at once and rejects when the check
  fails. A check that is already running is shared, not repeated.

### Startup

Without a snapshot, `start()` makes the first check and waits for it. It resolves when a bundle is loaded, and it
rejects with the first failure (fail fast), without arming a poll. A failed start may be called again.

With a snapshot, `start()` resolves once the snapshot is installed, and the first check runs in the background:

```ts
const loader = createBundleLoader({ url, snapshot: { path: '/etc/app/bundle.json' } });
// or: snapshot: { bytes: someUint8Array }
```

A snapshot that is not a valid bundle, or a file that cannot be read, makes `start()` reject, and no request is made.
A snapshot does not count as a confirmation: `status().lastCheckedAt` stays unset, and `stale` stays `true` until the
server answers.

### Status and metrics

`status()` returns a snapshot of the held bundle:

| Field | Meaning |
| --- | --- |
| `bundleVersion` | The version being served, or `undefined` before any bundle. |
| `loadedAt` | When the served evaluator was installed (epoch milliseconds). The bundle age is `Date.now() - loadedAt`. |
| `lastCheckedAt` | When the server last confirmed the version with a `200` or a `304` (epoch milliseconds). |
| `stalenessMs` | Milliseconds since `lastCheckedAt`. `undefined` until the first confirmation. |
| `stale` | `true` before a confirmation, and when `stalenessMs` exceeds `staleAfterMs`. |
| `refreshFailures` | The failed refresh attempts since the loader was created. |

Log `bundleVersion` with each decision, for example from the `decide` result's `bundleVersion`, to tie it to the
bundle that made it.

### Stopping

`stop()` ends polling and aborts a request in flight. It is final: a stopped loader cannot be started or refreshed
again, and its `start()` rejects with `E_LOADER_STOPPED`. Calls keep working on the last bundle after a stop.

### Errors

Errors from the bundle checks are the evaluator's `TierwiseError`, with the evaluator's codes. The loader's own errors
are `BundleLoaderError`, with a `code` and, for a response status, a `status` property:

| Code | Raised by |
| --- | --- |
| `E_BUNDLE_FETCH` | A request failed, timed out, or answered with a status other than `200` or `304`. `status` is set for a status. |
| `E_BUNDLE_NOT_LOADED` | `resolve`, `resolveSync`, `decide` or `decideSync` before any bundle is loaded. |
| `E_LOADER_STOPPED` | `start` or `refresh` after `stop()`, or a check cut short by `stop()`. |

The loader's codes are local to this package. The messages are for people and may change.

## Development

```
pnpm --dir js install --frozen-lockfile
pnpm --dir js/node run build   # ESM in dist/esm, CommonJS in dist/cjs, types in dist/types
pnpm --dir js/node test        # runs over the built output
```

The build needs `@visplabs/evaluator` to be built first. The tests use `node:test`, a `node:http` stub server, and the
conformance bundles in `spec/conformance/bundles/`. They never copy those files. Where a test needs a second bundle,
it derives one by changing a root value and recomputing the digest, so the derived bundle is a valid bundle.

- `refresh.test.mjs`: the first load, the swap, `304` and `200` handling, polling, last-known-good for each failure
  kind, timeouts, and staleness.
- `startup.test.mjs`: startup without a bundle, retry, fail fast, snapshots from bytes and from a file, the background
  check, and `stop`.
- `swap.test.mjs`: the swap under concurrent calls, calls in flight across a swap, and the helpers.
- `options.test.mjs`: option checks and the injected fetch function.
- `packaging.test.mjs`: the ESM and CommonJS builds, and the runtime dependencies.
