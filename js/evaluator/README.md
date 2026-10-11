# @visplabs/evaluator

The Tierwise evaluator for JavaScript and TypeScript. It loads a compiled bundle, resolves keys over a
context, and decides features over a FactSet. It has no runtime dependencies, and it runs in Node 20 or later
and in browsers.

> **Browser use is for display only.** A result that is computed in a browser can be tampered with by anyone who
> controls that browser. Use it to show what a decision is likely to be. Enforce every decision on a server,
> with the same bundle.

Install it from npm as `@visplabs/evaluator`.

## Supported versions

| What | Supported value |
| --- | --- |
| Spec version (`specVersion` in the bundle) | `"0.1"` |
| Bundle format version (`bundleFormatVersion`) | `"1"` |

Both are compared as exact strings. A bundle with any other value, of any JSON type, is rejected with
`E_BUNDLE_UNSUPPORTED`. A bundle with a version member missing is rejected with `E_BUNDLE_INTEGRITY`. The
format version is checked completely before the spec version, so a bundle in a future format reports
`E_BUNDLE_UNSUPPORTED` even if its `specVersion` is missing.

JavaScript never compiles sources. Bundles come from the compiler in `tierwise-core`.

## Usage

```ts
import { loadBundle, createEvaluator, TierwiseError } from '@visplabs/evaluator';

// Synchronous. Verifies the bytes, the versions, the digest and the structure.
const bundle = loadBundle(bytesOrString); // Uint8Array, ArrayBuffer or string

const tw = createEvaluator(bundle, {
  // One provider for each service-sourced level of the bundle, each with its own timeout.
  providers: {
    account: { fn: async (nodeId) => fetchAccountValues(nodeId), timeoutMs: 200 },
  },
  listener: (event) => metrics.record(event), // optional
});

const ctx = { levels: { division: 'south', account: 'acct-17' }, axes: { seller: 'acme' } };

// resolve: one key, with provenance. May call providers.
const r = await tw.resolve(ctx, 'checkout.shipping.allowed_methods');
r.value; // the result; null only for a min or max key that no scope sets
r.contributions; // one entry for each scope that set a value or failed to
r.source; // order-dependent keys only: "DEFAULT" or { scope, name, node? }

// decide: one feature, with a reason and a trace. Never calls a provider.
const d = await tw.decide(ctx, 'checkout.order_submission', {
  facts: { 'customer.active': true, 'customer.credit_hold': true },
  children: { item: [{ 'item.stock_type': 'STOCKED' }] },
});
d.effect; // an enum string, null (nullable enums only) or an object
d.reason; // { type: 'RULE', id, name } | { type: 'DEFAULT' } | { type: 'MISSING_FACT', facts }
d.trace; // the rules that were checked, in order

// Synchronous variants. decideSync is the same as decide. resolveSync never calls a provider.
tw.decideSync(ctx, 'checkout.order_submission', facts);
tw.resolveSync(ctx, 'checkout.pickup.lead_hours'); // throws E_PROVIDER_REQUIRED if a provider call is needed

try {
  tw.resolveSync(ctx, 'checkout.shipping.allowed_methods');
} catch (error) {
  if (error instanceof TierwiseError) console.log(error.code); // 'E_PROVIDER_REQUIRED'
}
```

The names above come from a sample domain. The package itself knows no domain names.

Callers go only through `resolve` and `decide`. No function returns one level's raw value. Results have
exactly the JSON shape of spec section 9, and a conditional field that does not apply is absent, never `null`.

### Inputs

- **Context.** `{ levels?: Record<string, string>, axes?: Record<string, string> }`. Node IDs are compared as
  received. They are never Unicode-normalised. An omitted context, or an omitted member, is empty. A context that
  names the root level raises `E_CONTEXT_INVALID`. A name that is not declared raises `E_UNKNOWN_SCOPE`.
- **FactSet.** `{ facts?, children? }`. A fact name is `<entity>.<attribute>`. A `null` or omitted fact is
  missing, which is not an error. A value of the wrong type is `E_FACTS_INVALID`, and nothing is coerced: the
  string `"true"` is not a boolean, and a number is not a decimal. Decimals are strings, such as `"1.50"`.
  Integers must lie within +/-(2^53 - 1).
- **Key and feature names.** An omitted, non-string or empty name raises `E_UNKNOWN_KEY` or
  `E_UNKNOWN_FEATURE`.

### Providers

A provider is a function from a node ID to a plain object that maps key names to values. It may be
asynchronous. It reports unavailability by throwing or rejecting.

- Every service-sourced level of the bundle needs exactly one registration, and the timeout is required. There
  is no default timeout (SVC-48). A bare function, a missing `timeoutMs`, or a `timeoutMs` that is not a
  positive integer raises `E_EVALUATOR_OPTIONS`.
- The evaluator calls a provider at most once for each level in one `resolve` call, and only when the context
  supplies an admitted node for that level and the key is settable there. Calls for several levels start
  together.
- A timeout above 2^31 - 1 ms (about 24.8 days) is capped at that value, because timers cannot be longer.
- A provider that throws, rejects, times out, or returns something that is not a plain object makes its key
  unavailable. A result that arrives after the timeout is not used. A failure is a contribution entry with
  `UNAVAILABLE` or `INVALID`, never an exception, and the key's `on_source_failure` decides whether it
  contributes the deny value or nothing.
- A `Map`, a class instance or an array is not a plain object. Return `{}` objects with the key names as
  properties.

### Listener events

`listener(event)` receives `{ type, bundleVersion, level?, node?, key? }`. The types are `BUNDLE_LOADED` (once,
when `createEvaluator` returns), `SOURCE_UNAVAILABLE`, `SOURCE_INVALID`, `UNKNOWN_KEY` and `IGNORED_ENTRY`.
A listener that throws, or returns a rejected promise, is ignored. `decide` and the synchronous variants report
no event.

### Errors

Every error is a `TierwiseError` with a stable `code`. The message is for people and may change.

| Code | Raised by |
| --- | --- |
| `E_BUNDLE_INTEGRITY` | `loadBundle`: bytes, envelope, digest or structure is wrong |
| `E_BUNDLE_UNSUPPORTED` | `loadBundle`: unsupported `bundleFormatVersion` or `specVersion` |
| `E_EVALUATOR_OPTIONS` | `createEvaluator`: malformed options, an unknown or non-service level, a bad timeout |
| `E_PROVIDER_MISSING` | `createEvaluator`: a service-sourced level has no provider |
| `E_CONTEXT_INVALID`, `E_UNKNOWN_SCOPE`, `E_UNKNOWN_NODE` | `resolve`, `decide` and their synchronous variants: the context |
| `E_UNKNOWN_KEY` | `resolve`, `resolveSync` |
| `E_UNKNOWN_FEATURE`, `E_FACTS_INVALID` | `decide`, `decideSync` |
| `E_PROVIDER_REQUIRED` | `resolveSync` only |

The async methods reject with these errors. The checks run in the order of spec section 11, and the first
failing check decides the code.

### Immutability

A loaded bundle is deeply frozen. Results are fresh objects that share nothing with the bundle, so changing a
result never changes a later call. The evaluator never modifies the context, the FactSet or a provider's result.

## Development

```
pnpm --dir js install --frozen-lockfile
pnpm --dir js run build   # ESM in dist/esm, CommonJS in dist/cjs, types in dist/types
pnpm --dir js test        # the root test script also runs this package's tests
```

The tests in `test/` run over the built output and use `node:test`:

- `conformance.test.mjs` reads `spec/conformance/canonical/` and `spec/conformance/evaluate/` in place and
  applies the matching rules of spec section 12. Every vector must pass.
- `sha256.test.mjs` compares the built-in SHA-256 with `node:crypto` over generated inputs.
- `properties.test.mjs` holds the property tests of requirements section 12 (fast-check).
- `bundle.test.mjs`, `evaluator.test.mjs` and `json.test.mjs` hold the unit tests.
- `packaging.test.mjs` checks that the built ESM imports nothing from outside the package, that no Node-only API
  is used, and that the CommonJS build runs in a context with only browser globals.

`yaml` and `fast-check` are development dependencies of the workspace and are used by the tests only.

### Checks beyond the Java loader

The loader makes every structural check of the Java `BundleReader`, so both reject the same bundles. It adds
two more, because spec [BUNDLE-118] asks a loader to reject any bundle that the bundle alone shows to be broken:

- a cycle in the oriented entity graph (ENTITY-14 to ENTITY-17);
- an empty level or axis name (SCOPE-8).

Neither can occur in a bundle that the compiler wrote.

The deny checks of [SVC-11] and [SVC-12] are not in this list, because both loaders make them. A key with
`onSourceFailure` `deny` must have a narrowing strategy (`all`, `intersect` or `min`), and a `min` key with `deny`
must have a `min` member in its integer type. Both loaders reject a bundle that breaks either rule with
`E_BUNDLE_INTEGRITY`, and the vectors `load/invalid/deny-non-narrowing` and `load/invalid/deny-min-without-bound`
check the rules for both.
