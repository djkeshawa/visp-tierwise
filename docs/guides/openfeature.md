# OpenFeature guide

[OpenFeature](https://openfeature.dev) is a vendor-neutral API for reading flags. Tierwise provides an OpenFeature
provider in each language, so code that already reads flags through OpenFeature can read Tierwise keys and decisions
without learning another API:

| Language | Package | Provider |
| --- | --- | --- |
| Java 17 or later | `io.github.visplabs:tierwise-openfeature` | `io.github.visplabs.tierwise.openfeature.TierwiseProvider` |
| JavaScript and TypeScript | `@visplabs/openfeature` | `TierwiseServerProvider` for `@openfeature/server-sdk`, `TierwiseWebProvider` for `@openfeature/web-sdk` |

Both implement one mapping, defined in [OpenFeature mapping](../openfeature-mapping.md). That page is the reference:
this guide shows how to use the providers and what to expect, and does not repeat the clauses.

## When to use it

Tierwise is not a feature-flag tool: it has no percentage rollouts, experiments or kill switches, and it is not a
substitute for one. Use the provider when you want to read Tierwise answers through the OpenFeature client you
already have, for example to read a limit next to your other flags, or to put Tierwise behind the same wrapper that
logs and instruments flag evaluations. A flag is the name of a Tierwise key or feature. If you only use Tierwise,
call `resolve` and `decide` directly: you get the full provenance, typed results and no mapping in between.

The names in this guide (`checkout.pickup.lead_hours`, `division`, …) come from the `commerce` sample project and are
examples. Every output was produced by the providers, as built from the working tree, over the compiled bundle of that
sample.

> **Browser use is for display only.** The web provider runs the evaluator in a browser, where a result can be changed
> by anyone who controls that browser, and where the bundle is visible. Use it to show what a decision is likely to be,
> such as hiding a button. Enforce every decision on a server, with the same bundle, through the server provider or the
> evaluator. See the [security model](security-model.md).

## Flags and types

A flag key is the declared name of a key or of a feature. A name is looked up as a key first and as a feature second.
The type of the evaluation decides which values the flag can have:

| OpenFeature evaluation | Reads |
| --- | --- |
| boolean | a boolean key |
| string | a string, enum or decimal key (a decimal is its canonical string), or a feature with an enum effect |
| integer (Java), number (JavaScript) | an integer key. In Java, within the `int` range; a double evaluation reads an integer that a double holds exactly (up to 2^53) |
| object | a set or list key (a list), an object key (a structure), or a feature with an object effect |

A key read as the wrong type, or a feature read as a boolean or a number, is a `TYPE_MISMATCH` resolution error, and the
caller's default is the value. There is no conversion between types.

## The evaluation context

Tierwise needs a context and, for decisions, facts. They travel in three attributes of the OpenFeature evaluation
context. The targeting key and every other attribute are ignored.

| Attribute | Value |
| --- | --- |
| `tierwise.levels` | structure: non-root level name to node ID |
| `tierwise.axes` | structure: axis name to node ID |
| `tierwise.facts` | structure with the optional members `facts` and `children`; read for features only |

OpenFeature merges contexts from the API, a transaction, the client and the call, attribute by attribute: an attribute
set at a later layer replaces the same attribute from an earlier layer as a whole. The members of a structure are not
merged. So to change only the division, repeat the whole `tierwise.levels` structure.

## Java

Create the evaluator as usual, wrap it in the provider, and register the provider:

```java
EvaluatorOptions options = EvaluatorOptions.builder()
    .provider("account", nodeId -> Map.of(), Duration.ofMillis(200))
    .provider("user", nodeId -> Map.of(), Duration.ofMillis(200))
    .build();
Evaluator evaluator = Tierwise.evaluator(Tierwise.loadBundle(Files.readAllBytes(Path.of("commerce.json"))), options);

OpenFeatureAPI api = OpenFeatureAPI.getInstance();
api.setProviderAndWait(TierwiseProvider.of(evaluator));
Client client = api.getClient();

EvaluationContext context = new ImmutableContext(Map.of(
    "tierwise.levels", Value.objectToValue(Map.of("division", "south")),
    "tierwise.axes", Value.objectToValue(Map.of("seller", "acme")),
    "tierwise.facts", Value.objectToValue(Map.of(
        "facts", Map.of("customer.active", true, "customer.credit_hold", true),
        "children", Map.of("item", List.of(
            Map.of("item.stock_type", "STOCKED"),
            Map.of("item.stock_type", "DROP_SHIP")))))));

int hours = client.getIntegerValue("checkout.pickup.lead_hours", 0, context);           // a key
String effect = client.getStringValue("checkout.order_submission", "DENY", context);    // a feature
FlagEvaluationDetails<Boolean> details =
    client.getBooleanDetails("checkout.container_order.allowed", false, context);
```

The module depends on `tierwise-core` and the OpenFeature Java SDK (`dev.openfeature:sdk`) and not on Spring. Its output for
this program:

```text
lead_hours = 8
order_submission = DENY
container_order.allowed = true reason=DEFAULT
bundleVersion = sha256:ab1cd3a96be4ab7569ccaba61cb65dbd14b5efd8239de35f16643ab791af6859
contributions = []
```

`bundleVersion` and `contributions` are entries of `details.getFlagMetadata()`.

### With the Spring Boot starter

The provider takes a supplier of the current evaluator and reads it on every evaluation, so a refreshed bundle is used
by the next evaluation:

```java
api.setProviderAndWait(new TierwiseProvider(evaluators::current)); // evaluators is the starter's TierwiseEvaluators
```

A supplier that returns `null` makes evaluations fail with `PROVIDER_NOT_READY`. See the
[runtime and operations guide](runtime-and-operations.md) for the starter.

## JavaScript, on a server

```ts
import { readFileSync } from 'node:fs';
import { OpenFeature } from '@openfeature/server-sdk';
import { createEvaluator, loadBundle } from '@visplabs/evaluator';
import { TierwiseServerProvider } from '@visplabs/openfeature';

const providers = { account: { fn: async () => ({}), timeoutMs: 200 }, user: { fn: async () => ({}), timeoutMs: 200 } };
const evaluator = createEvaluator(loadBundle(readFileSync('commerce.json')), { providers });
await OpenFeature.setProviderAndWait(new TierwiseServerProvider(evaluator));
const client = OpenFeature.getClient();

const context = {
  'tierwise.levels': { division: 'south' },
  'tierwise.axes': { seller: 'acme' },
  'tierwise.facts': {
    facts: { 'customer.active': true, 'customer.credit_hold': true },
    children: { item: [{ 'item.stock_type': 'STOCKED' }, { 'item.stock_type': 'DROP_SHIP' }] },
  },
};

const hours = await client.getNumberValue('checkout.pickup.lead_hours', 0, context);
const effect = await client.getStringValue('checkout.order_submission', 'DENY', context);
const details = await client.getBooleanDetails('checkout.container_order.allowed', false, context);
```

The result is the same as in Java:

```text
lead_hours = 8
order_submission = DENY
container_order.allowed = true reason=DEFAULT
bundleVersion = sha256:ab1cd3a96be4ab7569ccaba61cb65dbd14b5efd8239de35f16643ab791af6859
contributions = []
```

Use `details.flagMetadata.bundleVersion` and `details.flagMetadata.contributions` (a JSON string). The OpenFeature
packages are **peer dependencies** of `@visplabs/openfeature` (`@openfeature/core` always, plus the SDK you use), so one
build serves both SDKs and your application chooses the versions.

With `@visplabs/node`, give the provider a function that returns the current evaluator, so that a refreshed bundle
is used by the next evaluation:

```ts
new TierwiseServerProvider(() => loader.current());
```

A function that returns `null` or `undefined` makes evaluations fail with `PROVIDER_NOT_READY`.

## JavaScript, in a browser (display only)

The web SDK keeps one static context, and its evaluations are synchronous:

```ts
import { OpenFeature } from '@openfeature/web-sdk';
import { TierwiseWebProvider } from '@visplabs/openfeature';

await OpenFeature.setProviderAndWait(new TierwiseWebProvider(evaluator), context);
const showButton = OpenFeature.getClient().getBooleanValue('checkout.container_order.allowed', false);
await OpenFeature.setContext({ ...context, 'tierwise.levels': { division: 'north' } }); // the next evaluation sees it
```

The web provider calls `resolveSync` and `decideSync`. A key that needs a provider call (a service-sourced level for a
node that the context names) fails with the general error code and the metadata `tierwiseCode: 'E_PROVIDER_REQUIRED'`.
Because the browser never calls a provider, a browser cannot show a value that depends on one.

**Example (non-normative):** division `south` and then division `north`, with the sample bundle.

```text
south 100                          getNumberValue('checkout.order.allowed_max_lines', 0)
north false                        getBooleanValue('checkout.container_order.allowed', true), after setContext
wrong type false TYPE_MISMATCH     getBooleanDetails('checkout.shipping.allowed_methods', false)
```

## Reasons

| Reason | Means |
| --- | --- |
| `STATIC` | The value comes from the root level alone |
| `DEFAULT` | No scope supplied the value, or a policy default or `if_fact_missing` effect applied |
| `TARGETING_MATCH` | A named node or a matching rule shaped the result |
| `ERROR` | The resolution failed |

A feature that matched a rule has the rule's `id` as its variant. In the example above, `container_order.allowed`
is `DEFAULT`: no scope supplied a value, so the result is the identity of the strategy (`true` under `all`) and the
contributions are empty.

## Metadata

The names of the metadata entries are constants of `TierwiseMetadata` in both languages. A key has `bundleVersion`,
`flagKind`, `strategy`, `kind`, `source`, `sourceNode`, `contributions` and `skippedScopes`. A feature has
`bundleVersion`, `flagKind`, `decisionReason`, `ruleId`, `ruleName`, `missingFacts`, `policy`, `bindingLevel`,
`bindingNode` and `skippedScopes`. [OpenFeature mapping](../openfeature-mapping.md) lists when each is set.

Metadata is how the provenance of Tierwise reaches OpenFeature hooks and logging. Log `bundleVersion` with the flag
evaluation to tie each answer to the bundle that made it.

## Errors

| Tierwise code | OpenFeature error code |
| --- | --- |
| `E_UNKNOWN_KEY`, `E_UNKNOWN_FEATURE` | `FLAG_NOT_FOUND` |
| `E_CONTEXT_INVALID`, `E_UNKNOWN_SCOPE`, `E_UNKNOWN_NODE`, `E_FACTS_INVALID` | `INVALID_CONTEXT` |
| every other code | `GENERAL` |

A resolution error has the reason `ERROR`, the caller's default as its value, and the metadata entry `tierwiseCode`.
A source failure at a service-sourced level is not an error: it shows in `contributions`. An error that is not a
`TierwiseError`, for example a bug, propagates to the SDK instead of being hidden behind a default.

**Example (non-normative):**

```text
missing = 7 FLAG_NOT_FOUND ERROR     getIntegerDetails("no.such.flag", 7, context)
```

Because an error returns the caller's default, choose defaults that are safe. A default of `true` for a veto key turns
a lookup mistake into a grant.

## What the mapping does not do

- It does not give a flag per key and per context for a vendor dashboard. The context is the Tierwise context, and the
  targeting key is ignored.
- A key and a feature that share a name cannot both be reached: the key is found first. *(OPEN)* This is a
  provisional rule of both providers (clause OFMAP-19 in the mapping), pending a compiler rule or a prefix convention.
- A rule-by-rule explanation is not in the OpenFeature result. Use `decide` for the trace.

## Open points

- *(OPEN)* Whether a key and a feature may share one name (clause OFMAP-19 of the mapping). The providers find the key first.
