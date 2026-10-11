# @visplabs/openfeature

[OpenFeature](https://openfeature.dev) providers over the Tierwise evaluator (`@visplabs/evaluator`): a server
provider for `@openfeature/server-sdk` and a web provider for `@openfeature/web-sdk`. A flag is the name of a
Tierwise key or feature. The mapping of types, evaluation-context attributes, reasons, flag metadata and error
codes is defined in [`docs/openfeature-mapping.md`](../../docs/openfeature-mapping.md), and the Java module
`tierwise-openfeature` implements the same mapping.

> **Browser evaluation is for display only.** A result computed in a browser can be tampered with by anyone who
> controls that browser, and the bundle is visible to them. Use the web provider to show what a decision is
> likely to be, such as hiding a button. Enforce every decision on a server, with the same bundle, through the
> server provider or the evaluator.

This package is private. Phase reviews precede publication. The OpenFeature packages are peer dependencies
(`@openfeature/core` always, plus the SDK you use), so one build serves both SDKs and the application chooses
the versions.

## Usage

```ts
import { OpenFeature } from '@openfeature/server-sdk';
import { createEvaluator, loadBundle } from '@visplabs/evaluator';
import { TierwiseServerProvider } from '@visplabs/openfeature';

const evaluator = createEvaluator(loadBundle(bundleBytes), { providers: { /* service-sourced levels */ } });
await OpenFeature.setProviderAndWait(new TierwiseServerProvider(evaluator));
const client = OpenFeature.getClient();

// The Tierwise context and facts travel in three structure attributes of the evaluation context.
const context = {
  'tierwise.levels': { division: 'south' },
  'tierwise.axes': { seller: 'acme' },
  'tierwise.facts': {
    facts: { 'customer.active': true, 'customer.credit_hold': true },
    children: { item: [{ 'item.stock_type': 'STOCKED' }] },
  },
};

const hours = await client.getNumberValue('checkout.pickup.lead_hours', 0, context); // a key
const effect = await client.getStringValue('checkout.order_submission', 'DENY', context); // a feature
const details = await client.getBooleanDetails('checkout.container_order.allowed', false, context);
details.reason; // STATIC, DEFAULT or TARGETING_MATCH
details.flagMetadata.bundleVersion; // the bundle that answered
details.flagMetadata.contributions; // who contributed, as a JSON string
```

The names above come from a sample domain. The package itself knows no domain names.

The web SDK keeps one static context, and its evaluations are synchronous:

```ts
import { OpenFeature } from '@openfeature/web-sdk';
import { TierwiseWebProvider } from '@visplabs/openfeature';

await OpenFeature.setProviderAndWait(new TierwiseWebProvider(evaluator), context);
const showButton = OpenFeature.getClient().getBooleanValue('checkout.container_order.allowed', false);
await OpenFeature.setContext({ ...context, 'tierwise.levels': { division: 'north' } }); // the next evaluation sees it
```

The web provider calls `resolveSync` and `decideSync`. A key that needs a provider call (a service-sourced level
for a node the context names) fails with the general error code and the metadata `tierwiseCode: 'E_PROVIDER_REQUIRED'`.

### Evaluator source

Both providers take an evaluator, or a function that returns the current one on every evaluation. Use a function
with a source that swaps bundles:

```ts
new TierwiseServerProvider(() => bundles.currentEvaluator());
```

A function that returns `null` or `undefined` makes evaluations fail with `PROVIDER_NOT_READY`.

## Flags

| Evaluation | Reads |
| --- | --- |
| boolean | a boolean key |
| string | a string, enum or decimal key (a decimal is its canonical string), or a feature with an enum effect |
| number | an integer key |
| object | a set or list key (an array), an object key, or a feature with an object effect |

A name is looked up as a key first and as a feature second. A key read as the wrong type, or a feature read as a
boolean or number, is a `TYPE_MISMATCH` resolution error with the caller's default as the value.

## Evaluation-context attributes

| Attribute | Value |
| --- | --- |
| `tierwise.levels` | object: non-root level name to node ID |
| `tierwise.axes` | object: axis name to node ID |
| `tierwise.facts` | object with the optional members `facts` and `children`; read for features only |

The targeting key and every other attribute are ignored. OpenFeature merges contexts attribute by attribute, so
an attribute set at a later layer replaces the same attribute from an earlier layer as a whole. The attribute
values go to the evaluator as they are; it alone judges them.

## Reasons and metadata

`STATIC`: the value comes from the root level alone. `DEFAULT`: no scope supplied the value, or a policy default
or `if_fact_missing` effect applied. `TARGETING_MATCH`: a named node or a matching rule shaped the result.
`ERROR`: the resolution failed. `TierwiseMetadata` names the metadata entries. A key has `bundleVersion`,
`flagKind`, `strategy`, `kind`, `source`, `sourceNode`, `contributions` and `skippedScopes`; a feature has
`bundleVersion`, `flagKind`, `decisionReason`, `ruleId`, `ruleName`, `missingFacts`, `policy`, `bindingLevel`,
`bindingNode` and `skippedScopes`. The mapping document lists when each is set. A feature that matched a rule has
the rule id as its variant.

## Errors

| Tierwise code | OpenFeature error code |
| --- | --- |
| `E_UNKNOWN_KEY`, `E_UNKNOWN_FEATURE` | `FLAG_NOT_FOUND` |
| `E_CONTEXT_INVALID`, `E_UNKNOWN_SCOPE`, `E_UNKNOWN_NODE`, `E_FACTS_INVALID` | `INVALID_CONTEXT` |
| every other code | `GENERAL` |

A resolution error has the reason `ERROR`, the caller's default as its value, and the metadata entry
`tierwiseCode`. A source failure at a service-sourced level is not an error; it shows in `contributions`. An
error that is not a `TierwiseError` propagates to the SDK.

## Development

```
pnpm --dir js run build          # ESM in dist/esm, CommonJS in dist/cjs, types in dist/types
pnpm --dir js/openfeature test   # type check, then node --test over the built output
```

The tests run over the built output in `dist/` and use `node:test` with both SDKs from the workspace:
`server.test.mjs` and `web.test.mjs` go through the SDK clients, `mapping.test.mjs` covers the pure mapping, the
CommonJS build and the package's imports, and `types.check.ts` checks at compile time that the providers satisfy
the `Provider` interfaces of both SDKs. The fixture bundle is the same as the Java module's.
