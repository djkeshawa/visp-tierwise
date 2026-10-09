# tierwise-openfeature

An [OpenFeature](https://openfeature.dev) provider over the Tierwise evaluator, for Java 17 and later. A flag is
the name of a Tierwise key or feature. The mapping of types, evaluation-context attributes, reasons, flag
metadata and error codes is defined in [`docs/openfeature-mapping.md`](../../docs/openfeature-mapping.md), and
the JavaScript package `@tierwise/openfeature` implements the same mapping.

The module depends on `tierwise-core` and on the OpenFeature Java SDK (`dev.openfeature:sdk`, version in ADR 0020).
It does not depend on Spring.

## Usage

```java
import dev.openfeature.sdk.*;
import io.github.tierwise.core.*;
import io.github.tierwise.openfeature.TierwiseProvider;

Evaluator evaluator = Tierwise.evaluator(Tierwise.loadBundle(bundleBytes), EvaluatorOptions.none());

OpenFeatureAPI api = OpenFeatureAPI.getInstance();
api.setProviderAndWait(TierwiseProvider.of(evaluator));
Client client = api.getClient();

// The Tierwise context and facts travel in three structure attributes of the evaluation context.
EvaluationContext context = new ImmutableContext(Map.of(
    "tierwise.levels", Value.objectToValue(Map.of("division", "south")),
    "tierwise.axes", Value.objectToValue(Map.of("seller", "acme")),
    "tierwise.facts", Value.objectToValue(Map.of(
        "facts", Map.of("customer.active", true, "customer.credit_hold", true),
        "children", Map.of("item", List.of(Map.of("item.stock_type", "STOCKED")))))));

int hours = client.getIntegerValue("checkout.pickup.lead_hours", 0, context);          // a key
String effect = client.getStringValue("checkout.order_submission", "DENY", context);   // a feature
FlagEvaluationDetails<Boolean> details =
    client.getBooleanDetails("checkout.container_order.allowed", false, context);
details.getReason();                                                   // STATIC, DEFAULT or TARGETING_MATCH
details.getFlagMetadata().getString("bundleVersion");                  // the bundle that answered
details.getFlagMetadata().getString("contributions");                  // who contributed, as JSON
```

The names above come from a sample domain. The module itself knows no domain names.

### With the Spring Boot starter

The provider takes a supplier of the current evaluator and reads it on every evaluation, so a refreshed bundle
is used by the next evaluation:

```java
api.setProviderAndWait(new TierwiseProvider(evaluators::current)); // evaluators is the starter's TierwiseEvaluators
```

A supplier that returns `null` makes evaluations fail with `PROVIDER_NOT_READY`.

## Flags

| Evaluation | Reads |
| --- | --- |
| boolean | a boolean key |
| string | a string, enum or decimal key (a decimal is its canonical string), or a feature with an enum effect |
| integer | an integer key within the `int` range; a value outside it is a `TYPE_MISMATCH` |
| double | an integer key that a double holds exactly (up to 2^53) |
| long | read through the double evaluation by the SDK |
| object | a set or list key (a list value), an object key (a structure), or a feature with an object effect |

A name is looked up as a key first and as a feature second. A key read as the wrong type, or a feature read as a
boolean, integer or double, is a `TYPE_MISMATCH` resolution error with the caller's default as the value.

## Evaluation-context attributes

| Attribute | Value |
| --- | --- |
| `tierwise.levels` | structure: non-root level name to node ID |
| `tierwise.axes` | structure: axis name to node ID |
| `tierwise.facts` | structure with the optional members `facts` and `children`; read for features only |

The targeting key and every other attribute are ignored. OpenFeature merges contexts attribute by attribute, so
an attribute set at a later layer replaces the same attribute from an earlier layer as a whole.

## Reasons and metadata

`STATIC`: the value comes from the root level alone. `DEFAULT`: no scope supplied the value, or a policy default
or `if_fact_missing` effect applied. `TARGETING_MATCH`: a named node or a matching rule shaped the result.
`ERROR`: the resolution failed. The names of the metadata entries are constants of `TierwiseMetadata`. A key has
`bundleVersion`, `flagKind`, `strategy`, `kind`, `source`, `sourceNode`, `contributions` and `skippedScopes`; a
feature has `bundleVersion`, `flagKind`, `decisionReason`, `ruleId`, `ruleName`, `missingFacts`, `policy`,
`bindingLevel`, `bindingNode` and `skippedScopes`. The mapping document lists when each is set.

## Errors

| Tierwise code | OpenFeature error code |
| --- | --- |
| `E_UNKNOWN_KEY`, `E_UNKNOWN_FEATURE` | `FLAG_NOT_FOUND` |
| `E_CONTEXT_INVALID`, `E_UNKNOWN_SCOPE`, `E_UNKNOWN_NODE`, `E_FACTS_INVALID` | `INVALID_CONTEXT` |
| every other code | `GENERAL` |

A resolution error has the reason `ERROR`, the caller's default as its value, and the metadata entry
`tierwiseCode`. A source failure at a service-sourced level is not an error; it shows in `contributions`.

## Build notes

- The module targets Java 17 (`maven.compiler.release` of the parent) and builds on JDK 21.
- The OpenFeature SDK classes carry SpotBugs annotations whose classes are an optional dependency of the SDK.
  The module's `pom.xml` therefore switches off the `classfile` lint category (and only that) for `-Xlint:all
  -Werror`, instead of adding a dependency.
