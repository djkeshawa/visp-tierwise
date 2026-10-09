# OpenFeature mapping

This appendix defines how a Tierwise evaluator is exposed as an OpenFeature provider: which flags exist and what types they have, which evaluation-context attributes carry the Tierwise context and facts, how provenance becomes a resolution reason, which flag metadata is set, and how Tierwise errors become OpenFeature error codes. It does not define the evaluator operations (section 11), the provenance shape (section 9) or the value encoding (section 10). It reads their results and maps them. Requirements §8 ("OpenFeature providers") asks for this appendix, and the provider for each language implements it: `tierwise-openfeature` for the JVM and `@tierwise/openfeature` for JavaScript (a server provider and a web provider).

The clauses follow the conventions of `spec/AUTHORING.md`. The area prefix is `OFMAP`. The appendix is not yet part of the numbered specification (see Open points). Its clause IDs may be renumbered until it is accepted.

Terms. A **flag** is what an OpenFeature evaluation names with a flag key. An **evaluation** has a **type**: boolean, string, integer, number or object. An evaluation type that the language does not offer has no row in the tables below. A **resolution** is the provider's answer to one evaluation: a value, a reason, optional metadata, and an error code when it failed. The **caller's default** is the default value that the caller passed to the evaluation.

## 1 General

**[OFMAP-1]** A provider MUST obtain every result by calling the evaluator operations `resolve` or `decide` ([EVAL-7], [EVAL-8]), or their synchronous variants where the OpenFeature runtime requires a synchronous answer ([EVAL-9], [EVAL-12]).

**[OFMAP-2]** A provider MUST NOT expose one scope's own value other than as the contribution summary of [OFMAP-30] ([EVAL-13]).

**[OFMAP-3]** A provider MUST accept either one evaluator or a source that returns the current evaluator.

**[OFMAP-4]** A provider that has a source MUST read it on every evaluation, so that an evaluator swapped between evaluations is used by the next one.

**[OFMAP-5]** A provider MUST NOT keep a result or a context between evaluations.

**[OFMAP-6]** A provider's name, as OpenFeature provider metadata reports it, MUST be `Tierwise`.

> Implementation note: with the Spring Boot starter, the source is a method of its evaluators object that returns the evaluator in use. With a fixed bundle, the source is the evaluator itself.

## 2 Flags and types

**[OFMAP-7]** A flag key MUST be the declared name of a key or of a feature ([KEY-1], [FEAT-1]).

**[OFMAP-8]** The type of an evaluation MUST decide which values the flag can have, as Table 1 defines.

**Table 1: type mapping.** The first column is what the evaluator returns.

| Tierwise result | boolean | string | integer | number | object |
| --- | --- | --- | --- | --- | --- |
| key of type `boolean` | value | mismatch | mismatch | mismatch | mismatch |
| key of type `string` or `enum` | mismatch | value | mismatch | mismatch | mismatch |
| key of type `decimal` | mismatch | its canonical string | mismatch | mismatch | mismatch |
| key of type `integer` | mismatch | mismatch | value when in range | value when exact | mismatch |
| key of type `set` | mismatch | mismatch | mismatch | mismatch | list of its members |
| key of type `list` | mismatch | mismatch | mismatch | mismatch | list of its elements |
| key of type `object` | mismatch | mismatch | mismatch | mismatch | structure |
| feature with an `enum` effect | mismatch | the effect | mismatch | mismatch | mismatch |
| feature with an `object` effect | mismatch | mismatch | mismatch | mismatch | structure |

"Mismatch" means the resolution error `TYPE_MISMATCH` ([OFMAP-37]). "In range" and "exact" are defined by [OFMAP-11] and [OFMAP-12].

**[OFMAP-9]** A provider MUST NOT convert a value to another Tierwise type or to another OpenFeature type, except as Table 1 shows.

**[OFMAP-10]** A decimal key MUST resolve as its canonical decimal string ([BUNDLE-71]) and MUST NOT resolve as a floating-point number.

**[OFMAP-11]** An integer evaluation whose result type is a 32-bit signed integer MUST reject a key value outside that range with `TYPE_MISMATCH`.

**[OFMAP-12]** A number evaluation whose result type is a double-precision floating-point number MUST reject an integer key value whose magnitude is above 2^53 with `TYPE_MISMATCH`.

> Implementation note: JavaScript has one number type. Its number evaluation takes an integer key value, and its evaluator holds an integer as a number, so an integer key value is within ±(2^53 − 1). The JVM provider has an integer evaluation (32-bit) and a number evaluation (double); the SDK's 64-bit evaluation reads a key through the number evaluation, so it has the limit of [OFMAP-12].

**[OFMAP-13]** An object evaluation MUST return the value of a `set` key as a list of strings in the declaration order of `universe` ([PROV-86]), and the value of a `list` key as a list in source order ([BUNDLE-72]).

**[OFMAP-14]** An object evaluation MUST return the value of an `object` key, or an object effect, as a structure with one member for each member of the value. Member order is not significant ([PROV-85]).

**[OFMAP-15]** A provider MUST look a flag key up as a key first, by calling `resolve`.

**[OFMAP-16]** When the key lookup raises `E_UNKNOWN_KEY` and the evaluation type is string or object, a provider MUST look the flag key up as a feature, by calling `decide` with the facts of [OFMAP-21].

**[OFMAP-17]** When the key lookup raises `E_UNKNOWN_KEY` and the evaluation type is boolean, integer or number, a provider MUST call `decide` with no facts to learn whether a feature has that name. If one does, the resolution MUST be `TYPE_MISMATCH` with the feature's metadata ([OFMAP-31]). If none does, the resolution MUST be `FLAG_NOT_FOUND`.

**[OFMAP-18]** When both lookups find nothing, the resolution MUST be `FLAG_NOT_FOUND` and its message MUST say that no key or feature has the name.

**[OFMAP-19]** *(OPEN)* May a key and a feature share one name? [SRC-40] makes each declaration unique within its kind only. Provisional rule of both providers: the key is found first ([OFMAP-15]), so the feature is not reachable through OpenFeature. A compiler rule or a prefix convention for flag keys would remove the ambiguity.

## 3 Evaluation context

**Table 2: evaluation-context attributes.** Every other attribute, and the targeting key, is ignored.

| Attribute | Value | Meaning | Read for |
| --- | --- | --- | --- |
| `tierwise.levels` | structure from level name to node ID (string) | the `levels` member of the context ([CTX-1]) | keys and features |
| `tierwise.axes` | structure from axis name to node ID (string) | the `axes` member of the context ([CTX-1]) | keys and features |
| `tierwise.facts` | structure with the optional members `facts` and `children` | the FactSet ([FACT-22] to [FACT-33]) | features only |

**[OFMAP-20]** A provider MUST build the context of an evaluation from `tierwise.levels` and `tierwise.axes` only. A missing attribute is a missing member, which counts as an empty map ([CTX-2]).

**[OFMAP-21]** A provider MUST build the FactSet of a decision from `tierwise.facts`. A missing attribute is the empty FactSet ([EVAL-54]).

**[OFMAP-22]** A provider MUST ignore `tierwise.facts` when the flag is a key.

**[OFMAP-23]** A provider MUST hand the attribute values to the evaluator unchanged in meaning, so that the evaluator alone judges their shape and types: a structure becomes a map, a list becomes a list, a number becomes an integer when the language carries it as an integer, and a null value stays null ([FACT-16], [FACT-19]).

**[OFMAP-24]** A provider MUST NOT repair a malformed attribute. A malformed `tierwise.levels` or `tierwise.axes` therefore raises `E_CONTEXT_INVALID` ([EVAL-30], [EVAL-31]), and a malformed or ill-typed `tierwise.facts` raises `E_FACTS_INVALID` ([EVAL-36]).

**[OFMAP-25]** A provider MUST validate the context before it looks a flag key up, as the evaluator does ([EVAL-16], [EVAL-18]). A context error therefore wins over an unknown flag.

OpenFeature merges the contexts of its layers (API, transaction, client, invocation) attribute by attribute. A provider sees one merged context, in which an attribute that a later layer sets replaces the same attribute from an earlier layer as a whole. A provider does not merge the members of a structure.

**Example (non-normative)**: evaluation-context attributes for a decision, written as JSON. The names come from a sample domain.

```json
{
  "tierwise.levels": { "division": "south" },
  "tierwise.axes": { "seller": "acme" },
  "tierwise.facts": {
    "facts": { "customer.active": true, "customer.credit_hold": true },
    "children": { "item": [{ "item.stock_type": "STOCKED" }] }
  }
}
```

## 4 Reasons

**[OFMAP-26]** The reason of a successful resolution MUST be `STATIC`, `DEFAULT` or `TARGETING_MATCH`, and MUST follow Table 3. The reason of a failed resolution MUST be `ERROR` ([OFMAP-37]).

**Table 3: reasons.** Rows are tried in order for a key, then for a feature, and the first row that applies decides.

| Result | Condition | Reason | Value |
| --- | --- | --- | --- |
| key | the value is absent ([MERGE-6]) | `DEFAULT` | the caller's default |
| key | `source` is `DEFAULT` ([PROV-48]) | `DEFAULT` | the key's value |
| key | `source` names a scope that has no `node`, the root level ([PROV-47]) | `STATIC` | the key's value |
| key | `source` names a scope that has a `node` | `TARGETING_MATCH` | the key's value |
| key | no `source`, and `contributions` is empty | `DEFAULT` | the key's value (the identity, [MERGE-5]) |
| key | no `source`, and some contribution entry has a `node` ([PROV-25]) | `TARGETING_MATCH` | the key's value |
| key | no `source`, and no contribution entry has a `node` | `STATIC` | the key's value |
| feature | the effect is null ([PROV-66]) | `DEFAULT` | the caller's default |
| feature | `reason.type` is `RULE` ([PROV-74]) | `TARGETING_MATCH` | the effect |
| feature | `reason.type` is `DEFAULT` ([PROV-75]) | `DEFAULT` | the effect |
| feature | `reason.type` is `MISSING_FACT` ([PROV-76]) | `DEFAULT` | the effect |

**[OFMAP-27]** *(OPEN)* Which provenance facts should select `STATIC`, `DEFAULT` and `TARGETING_MATCH`? Requirements §8 names the reasons but not the rule. Provisional rule of both providers, shown in Table 3: `STATIC` means the value comes from the root level alone, so it does not depend on the context. `DEFAULT` means no scope supplied the value, or a policy default or an `if_fact_missing` effect applied. `TARGETING_MATCH` means a named node or a matching rule shaped the result. A source failure is not an error ([PROV-8]), so a value that a `deny` failure produced is a `TARGETING_MATCH` and its failure entry shows in the contribution summary.

**[OFMAP-28]** *(OPEN)* How should OpenFeature carry a value it cannot express: the null effect of a nullable `enum` feature ([PROV-66]) and the absent value of a `min` or `max` key ([MERGE-6])? OpenFeature has no null flag value. Provisional rule of both providers: the resolution is not an error, its value is the caller's default, its reason is `DEFAULT`, and the metadata entry `effectNull` or `valueAbsent` is `true`.

**[OFMAP-29]** A resolution that returns the effect of a feature whose `reason.type` is `RULE` MUST set the variant to the rule `id`. Every other resolution MUST leave the variant unset, including the resolution of [OFMAP-28] for a null effect, whose value is the caller's default and not the effect of the rule.

## 5 Flag metadata

**[OFMAP-30]** A resolution MUST set the metadata entries of Table 4 when their condition holds, and MUST NOT set any other entry.

**[OFMAP-31]** The metadata of a `TYPE_MISMATCH` resolution that follows a successful lookup MUST be the metadata of the lookup's result.

**Table 4: flag metadata.** Every value is a string or a boolean, because OpenFeature flag metadata holds nothing richer. A structured entry is a compact JSON string with the member order shown.

| Entry | Type | Set when | Content |
| --- | --- | --- | --- |
| `bundleVersion` | string | the evaluator returned a result | the `bundleVersion` of the result ([PROV-18]) |
| `flagKind` | string | the evaluator returned a result | `key` or `feature` |
| `strategy` | string | key | the merge strategy ([PROV-13]) |
| `kind` | string | key that opts into a kind | the kind name ([PROV-15]) |
| `source` | string | key with the strategy `override` or `deep_merge` | `DEFAULT`, or `<scope>:<name>` for the winning scope, such as `level:division` ([PROV-46]) |
| `sourceNode` | string | `source` names a scope that has a node | the node ID ([PROV-47]) |
| `contributions` | string (JSON) | key | an array with one object for each contribution entry, in the order of [PROV-34], with the members `scope`, `name`, `node` (when the entry has one), `effect` and `onSourceFailure` (when the entry has one). The contributed values are not included. |
| `skippedScopes` | string (JSON) | the result has skipped scopes | an array of objects with the members `scope`, `name`, `node` and `reason` ([PROV-54]) |
| `valueAbsent` | boolean | key with an absent value | `true` |
| `decisionReason` | string | feature | `reason.type`: `RULE`, `DEFAULT` or `MISSING_FACT` ([PROV-73]) |
| `ruleId` | string | feature with `reason.type` `RULE` | the winning rule's `id` |
| `ruleName` | string | feature with `reason.type` `RULE` | the winning rule's `name` |
| `missingFacts` | string (JSON) | feature with `reason.type` `MISSING_FACT` | the array `reason.facts` ([PROV-77]) |
| `policy` | string | feature | the policy name ([PROV-71]) |
| `bindingLevel` | string | feature | `binding.level` ([PROV-68]) |
| `bindingNode` | string | feature with a binding at a non-root level | `binding.node` ([PROV-69]) |
| `effectNull` | boolean | feature whose effect is null | `true` |
| `tierwiseCode` | string | a Tierwise error caused the failure | the error code, such as `E_UNKNOWN_SCOPE` |

**[OFMAP-32]** *(OPEN)* Should the metadata entries carry a prefix such as `tierwise.`? OpenFeature flag metadata is flat and has no namespace. Provisional rule of both providers: unprefixed camel-case names, with `bundleVersion` as requirements §8 spells it.

**Example (non-normative)**: the metadata of a number evaluation for `checkout.pickup.lead_hours` (kind `configuration`, strategy `override`) in the `south` division with the `acme` seller. The `bundleVersion` is abbreviated. The resolution has the reason `TARGETING_MATCH` and the value 8.

```json
{
  "bundleVersion": "sha256:<digest>",
  "flagKind": "key",
  "strategy": "override",
  "kind": "configuration",
  "source": "axis:seller",
  "sourceNode": "acme",
  "contributions": "[{\"scope\":\"level\",\"name\":\"division\",\"node\":\"south\",\"effect\":\"SET\"},{\"scope\":\"axis\",\"name\":\"seller\",\"node\":\"acme\",\"effect\":\"SET\"}]"
}
```

## 6 Errors

**[OFMAP-33]** A provider MUST map the evaluation errors of requirements §6.2 to OpenFeature error codes as Table 5 defines.

**Table 5: error codes.**

| Tierwise code | OpenFeature error code |
| --- | --- |
| `E_UNKNOWN_KEY`, `E_UNKNOWN_FEATURE` | `FLAG_NOT_FOUND` |
| `E_CONTEXT_INVALID`, `E_UNKNOWN_SCOPE`, `E_UNKNOWN_NODE`, `E_FACTS_INVALID` | `INVALID_CONTEXT` |
| `E_PROVIDER_REQUIRED`, `E_PROVIDER_MISSING`, `E_EVALUATOR_OPTIONS`, `E_BUNDLE_INTEGRITY`, `E_BUNDLE_UNSUPPORTED` | `GENERAL` |

**[OFMAP-34]** A provider MUST map any other code to `GENERAL`.

**[OFMAP-35]** A provider MUST report `E_UNKNOWN_KEY` as `FLAG_NOT_FOUND` only when no feature of the name exists either ([OFMAP-16], [OFMAP-17], [OFMAP-18]).

**[OFMAP-36]** A provider MUST NOT turn a source failure into an error ([PROV-8], [EVAL-39]).

**[OFMAP-37]** A failed resolution MUST have the reason `ERROR`, the caller's default as its value, an OpenFeature error code, and a message. When a Tierwise error caused it, the message MUST include the Tierwise error message, which starts with the code ([EVAL-22]), and the metadata entry `tierwiseCode` MUST hold the code.

**[OFMAP-38]** A provider MUST resolve a result that cannot be read as the requested type to the error code `TYPE_MISMATCH`, with a message that names the flag and the two types.

**[OFMAP-39]** A provider whose source returns no evaluator MUST resolve to the error code `PROVIDER_NOT_READY`.

**[OFMAP-40]** A provider MUST NOT catch a failure that is not a Tierwise evaluation error. It propagates to the OpenFeature SDK, which maps it to `GENERAL`.

**Example (non-normative)**: a string evaluation of `checkout.order_submission` with a level that the bundle does not declare. The evaluator raises `E_UNKNOWN_SCOPE` ([EVAL-32]). The resolution has the caller's default as its value, the reason `ERROR`, the error code `INVALID_CONTEXT`, and the metadata `{ "tierwiseCode": "E_UNKNOWN_SCOPE" }`.

## 7 Providers for synchronous runtimes

**[OFMAP-41]** A provider for an OpenFeature runtime whose evaluations are synchronous MUST use the synchronous variants of `resolve` and `decide` ([EVAL-9], [EVAL-12]).

**[OFMAP-42]** Such a provider MUST resolve a key that needs a provider call to the error code `GENERAL`, with the metadata `tierwiseCode` `E_PROVIDER_REQUIRED` ([EVAL-29]).

**[OFMAP-43]** Such a provider MUST give the same reason, metadata and error code as the asynchronous provider for every input that needs no provider call.

**[OFMAP-44]** The documentation of such a provider MUST state that it is for display only: a result computed in a browser can be tampered with, so every decision that matters is enforced on a server with the same bundle (requirements §1, §3.2).

> Implementation note: the web provider of `@tierwise/openfeature` is the only such provider. Its context is the static context that the web SDK holds, so a change of context takes effect on the next evaluation.

## 8 Consistency between languages

**[OFMAP-45]** Providers for different languages MUST give the same reason, variant, error code and metadata entries for the same bundle, context, facts and provider outcomes, with the exceptions that Table 1 allows for numeric types ([OFMAP-11], [OFMAP-12]).

**[OFMAP-46]** The strings of the JSON metadata entries MUST be identical between languages: the member order of Table 4, no whitespace, and string escapes as `JSON.stringify` writes them.

**[OFMAP-47]** *(OPEN)* Should the conformance suite hold provider vectors (an evaluation, a context and the expected resolution)? Requirements §12 lists no provider vectors. Provisional rule: each provider has its own tests over the same fixture bundle, and [OFMAP-45] is checked by reviewing the two test suites together.

## Open points
- [OFMAP-19] *(OPEN)* May a key and a feature share one name? Keys win today.
- [OFMAP-27] *(OPEN)* The provenance facts that select `STATIC`, `DEFAULT` and `TARGETING_MATCH`.
- [OFMAP-28] *(OPEN)* How OpenFeature carries a null effect and an absent value.
- [OFMAP-32] *(OPEN)* Whether the metadata entries carry a prefix.
- [OFMAP-47] *(OPEN)* Whether the conformance suite holds provider vectors.
- Link needed: `spec/spec.md` lists the sections of the specification. This appendix is not part of the numbered specification, so the owner of `spec/spec.md` decides whether to add a one-line link to it, and `mkdocs.yml` needs a navigation entry if the page is published.
