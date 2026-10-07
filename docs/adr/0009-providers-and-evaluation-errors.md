# ADR 0009: Service-sourced providers and evaluation errors

- Status: accepted for the Phase 1 specification draft (delegated technical decision)
- Date: 2026-10-08
- Resolves: open question 8 ([history](../history/open-questions-2026-10-07.md)) and defines the runtime error model

## Context

Levels declared with `values: service` (ADR 0007) get their values from a provider
at runtime. Several things were undefined: what happens when a provider is missing,
leaves out a key, returns an unknown key or a bad entry, or times out; and how
evaluation errors differ from source-failure handling.

## Decisions

### 1. Provider contract

- A provider is registered per service-sourced level. Given a node ID, it returns a
  map from key name to value. It is called **at most once per scope per evaluation
  call**, and **only** when a key being resolved is settable at that level and the
  context supplies an admitted node for it.
- `decide` never calls providers (ADR 0007). `resolveSync` (JS) raises
  `E_PROVIDER_REQUIRED` instead of calling one; it never blocks or substitutes a
  value.
- Creating an evaluator fails with `E_PROVIDER_MISSING` if any service-sourced
  level has no provider. A missing provider is a deployment bug, so it is reported
  at startup rather than as a runtime source failure.

### 2. What a provider response means

| Provider outcome | Treatment | Provenance |
| --- | --- | --- |
| Throws, rejects or times out | Source failure for **every** key being resolved at that level | `UNAVAILABLE` |
| Leaves out a key | The node sets no value for that key. This is **not** a failure. | none |
| Returns a key not in the bundle | Ignored, logged and counted (deploy skew between provider and bundle) | none |
| Returns a key not settable at that level | Source failure for that key only | `INVALID` |
| Returns a value that fails type or range validation | Source failure for that key only | `INVALID` |

A bad entry fails only its own key, so one invalid value cannot take down unrelated
keys.

Each source failure applies the key's declared `on_source_failure` (`skip` or
`deny`, as in ADR 0008).

### 3. Timeouts

Timeouts are configured per provider on the runtime integration, not in the
bundle. A timeout is treated as unavailability. The spec requires every
implementation to support a per-provider timeout. Default values belong to the
runtime integration phase, not the spec.

### 4. Evaluation errors are raised, not returned

Results describe **valid** evaluations. Caller mistakes and integrity failures
raise an error (an exception in Java, a thrown `TierwiseError` in JS), each with a
stable code. A decision is never quietly turned into a default effect because the
caller passed bad input.

| Code | Raised when |
| --- | --- |
| `E_BUNDLE_INTEGRITY` | The recomputed content digest doesn't match `bundleVersion`; duplicate JSON properties; unknown envelope fields |
| `E_BUNDLE_UNSUPPORTED` | The bundle's `bundleFormatVersion` or `specVersion` isn't supported by this evaluator |
| `E_PROVIDER_MISSING` | An evaluator is created without a provider for a service-sourced level |
| `E_PROVIDER_REQUIRED` | `resolveSync` would need a provider |
| `E_CONTEXT_INVALID` | Malformed context; names the root level; ID fails the structural contract |
| `E_UNKNOWN_SCOPE` | The context names an undeclared level or axis |
| `E_UNKNOWN_NODE` | Unlisted ID in a closed catalog with `on_unknown_node: error` |
| `E_UNKNOWN_KEY`, `E_UNKNOWN_FEATURE` | A string-typed API call names something not in the bundle |
| `E_FACTS_INVALID` | Malformed FactSet; undeclared fact; a fact belonging to an entity that isn't the decided entity, one of its ancestors or one of its children; a value of the wrong type |

The Java and JS evaluators raise the same code for the same input, and conformance
vectors assert codes (`expect: { error: E_… }`). Messages may differ.

Source failures (section 2) are **not** evaluation errors. They are governed
behaviour and are recorded in provenance.

## Alternatives considered

| Option | Why not |
| --- | --- |
| Treat a missing provider as a runtime source failure | Hides a deployment bug behind `skip` and fails silently |
| Fail the whole provider response on one bad entry | One bad key would degrade unrelated keys |
| Treat unknown returned keys as `INVALID` | Every rolling deploy that adds a key would produce failures |
| Return caller errors as a special decision effect | Mixes caller bugs with policy outcomes; callers could enforce on a bogus `DENY` and never notice the bug |
