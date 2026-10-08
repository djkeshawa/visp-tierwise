# 12. Conformance vectors

This section defines the conformance suite: its directory layout, its three kinds of vector (evaluate vectors, canonical vectors and compile cases), the fields that every vector carries, what each expected result means, how a runner loads the suite, and how the suite is tied to clause IDs and error codes. It does not define the behaviour that vectors test. Each vector cites the clauses that define that behaviour, and its expected results use the field names and shapes those clauses define. The conformance classes in `spec.md` decide which vector kinds each implementation must pass. The JSON Schemas for vectors (work item 1.7), the spec tool that checks the suite (work item 1.12) and the coverage record (work item 1.18) belong to other items. This section states only what they must check.

## 12.1 Terms and directory layout

The terms *vector*, *evaluate vector*, *canonical vector*, *compile case*, *suite*, *runner*, *stub*, *fail outcome*, *provider invocation*, *runner failure*, *bundle file* and *fault* are defined in `spec.md` §Terminology.

**[VEC-1]** The suite MUST be stored under `spec/conformance/`, in the directories `bundles/`, `bundles/invalid/`, `evaluate/`, `canonical/` and `compile/`. Enforcer: check tool.

> **Example (non-normative)**
>
> ```text
> spec/conformance/
> ├── bundles/commerce.json
> ├── bundles/retail-pos.json
> ├── bundles/invalid/duplicate-property.json
> ├── evaluate/decide/mixed-basket-on-credit-hold.yaml
> ├── evaluate/resolve/account-values-from-provider.yaml
> ├── canonical/jcs/key-order-utf16.yaml
> ├── canonical/sha256/empty-input.yaml
> └── compile/duplicate-key-in-values/
>     ├── expected.yaml
>     └── project/tierwise.yaml
> ```

**[VEC-2]** Every `.yaml` file at any depth under `evaluate/` MUST be one evaluate vector, and every `.yaml` file at any depth under `canonical/` MUST be one canonical vector. Other files under those directories, such as `README.md`, are not vectors. Enforcer: check tool.

**[VEC-3]** Every directory directly under `compile/` MUST be one compile case. A file directly under `compile/`, such as `README.md`, is not a compile case. Enforcer: check tool.

**[VEC-4]** Every compile case MUST contain an `expected.yaml` file. Enforcer: check tool.

**[VEC-5]** A compile case whose `expected.yaml` does not set `project` ([VEC-91]) MUST contain a `project/` directory, which is a complete project root with its own `tierwise.yaml` ([MANIFEST-1]). Enforcer: check tool.

**[VEC-6]** *(OPEN)* Which encoding and byte-order mark rules apply to vector files and to `expected.yaml` files? Proposal: the rules that [SRC-10] settles for source files, which are UTF-8 with a byte-order mark rejected. Dependency: [SRC-10] (OPEN).

**[VEC-7]** A runner MUST parse each vector file and each `expected.yaml` under the YAML rules that SRC defines for source files: the YAML 1.2 core schema ([SRC-1]), no alias and no merge key ([SRC-2]), and no duplicate mapping key at any depth ([SRC-3]). A violation is a runner failure.

**[VEC-8]** *(OPEN)* May a vector file or an `expected.yaml` use an explicit YAML tag, or an anchor without an alias? Proposal: no. Each one is a runner failure, as [SRC-7] proposes for source files, so that no scalar takes a type outside the core schema. Dependency: [SRC-7] (OPEN).

**[VEC-9]** Every bundle file directly under `bundles/` MUST be a canonical bundle, with exactly the bytes that BUNDLE and DET define. Enforcer: check tool (digest and canonical form).

**[VEC-10]** Evaluate vectors and compile cases MAY reference the same bundle file.

## 12.2 Common fields

Every vector, and the `expected.yaml` of every compile case, carries the fields in this subsection.

**[VEC-11]** Each vector MUST have an `id` string. The `id` MUST be unique among the vectors of its kind (evaluate, canonical or compile). A vector is identified across the suite by its kind and its `id`. Enforcer: check tool (uniqueness).

**[VEC-12]** For an evaluate or canonical vector, the `id` MUST equal the file's path relative to `evaluate/` or `canonical/`, without the `.yaml` extension. Enforcer: check tool (id equal to path).

**[VEC-13]** For a compile case, the `id` MUST equal the name of the case's directory directly under `compile/`. Enforcer: check tool (id equal to path).

**[VEC-14]** Each vector MUST have `specVersion`, a string that names the version of this specification the vector targets. For this version the value is `"0.1"`.

**[VEC-15]** A runner MUST report as a runner failure any vector whose `specVersion` it does not support.

**[VEC-16]** Each vector MUST have `clauses`, a non-empty list of clause IDs. Each entry MUST be a clause defined in `spec.md` or in a section, and MUST NOT be a clause marked *(OPEN)*. Enforcer: the schema checks the list shape; the check tool checks that each entry exists and is not OPEN.

**[VEC-17]** Each vector MUST have `description`, a non-empty, single-line string that says what the vector checks.

**[VEC-18]** A vector under `evaluate/` MUST have `call` and MUST NOT have `primitive`. A vector under `canonical/` MUST have `primitive` and MUST NOT have `call`. A compile case MUST have neither. A mismatch between the directory and these fields is a runner failure.

**[VEC-19]** A vector or `expected.yaml` that has a field the schema does not define, at any depth, including inside `expect`, `providers` and each stub outcome, MUST be a runner failure. Enforcer: the schema, and the runner for any field that the schema admits only by pattern.

**[VEC-20]** The JSON Schemas for vectors MUST set `additionalProperties` to `false` in every object that has a fixed set of fields. Enforcer: check tool (validates the schemas).

## 12.3 Evaluate vectors

An evaluate vector runs one `resolve` or `decide` call against a bundle file and checks either the result or the error. The operations are defined by EVAL, the result fields by PROV, and the decision behaviour by FEAT, POL, BIND and RULE.

**[VEC-21]** The `call` field MUST be `resolve` or `decide`. It selects the one operation that the runner invokes ([EVAL-7], [EVAL-8]).

**[VEC-22]** The `bundle` field MUST be a relative path. The runner MUST resolve it against the directory that contains the vector file.

**[VEC-23]** The file that the `bundle` path resolves to MUST lie inside `bundles/`, in `bundles/` itself or in `bundles/invalid/`. Any other path is a runner failure.

**[VEC-24]** Every evaluate vector MUST run three steps in this order. First, load bundle, on the bytes of the resolved file, passed without change ([EVAL-2]). Second, create evaluator, with one provider for each service-sourced level, built from the stubs of the vector ([EVAL-4]). Third, the operation under test. An error raised by any of these steps is the outcome of the vector.

**[VEC-25]** The optional `sync` field MUST be a boolean that defaults to `false`. When it is `true`, the runner MUST invoke the synchronous variant of the operation ([EVAL-10], [EVAL-11], [EVAL-12]). Whether every implementation offers that variant is [EVAL-9] (OPEN).

**[VEC-26]** When `sync` is `true`, `call` is `resolve`, the context and key pass steps 1 and 2 of [EVAL-16], and a provider call would be needed, the expected outcome MUST be `E_PROVIDER_REQUIRED` ([EVAL-11], [EVAL-29], [SVC-42]). The runner MUST then treat any provider invocation in that vector as a runner failure, whatever `providerCalls` says.

**[VEC-27]** *(OPEN)* What does a runner whose implementation has no synchronous variant do with `sync: true`? Proposal: nothing special, because [EVAL-9] proposes that every implementation offers both variants. Until [EVAL-9] is accepted, such a runner reports the vector as a runner failure. Dependency: [EVAL-9] (OPEN).

**[VEC-28]** The optional `context` field, if present, MUST be a mapping. The runner MUST pass the operation a context that has both members `levels` and `axes`. An omitted `context`, or an omitted member, is an empty map. The shape rules of [CTX-1] then apply to what the vector supplies. Because the runner always passes both members, no vector depends on [CTX-2] (OPEN).

**[VEC-29]** A `resolve` vector MUST have a `key` field and MUST NOT have a `feature` field. A `decide` vector MUST have a `feature` field and MUST NOT have a `key` field. The value of either field MUST be a string ([EVAL-7], [EVAL-8]).

**[VEC-30]** A `decide` vector MUST have an `input` field.

**[VEC-31]** The runner MUST pass the value of `input` to decide as the FactSet without conversion, whatever its YAML type. Its shape is checked by FACT, and any failure is an outcome of the operation ([FACT-22], [EVAL-36]).

**[VEC-32]** A `resolve` vector MUST NOT have an `input` field.

**[VEC-33]** Context values, FactSet values and provider values MUST reach the operation as the YAML 1.2 core schema produces them ([SRC-1]), with no conversion to another type ([NORM-17]). A vector that tests a malformed value MUST write the malformed value.

**[VEC-34]** An integer token in a context, a FactSet or a provider value MUST NOT exceed 2^53 in magnitude. Every integer up to 2^53 in magnitude has an exact IEEE 754 double-precision representation, and 2^53 is the first integer outside the range of [TYPE-18], so a vector can test that boundary. Enforcer: check tool.

**[VEC-35]** A float token in a context, a FactSet or a provider value MUST reach the operation as a non-integer number. The suite uses floats only as malformed values, and only with a non-integral value such as `1.5`, because some hosts cannot keep a float with an integral value, such as `1.0`, apart from an integer. Examples of malformed uses are a number given for a decimal fact ([FACT-38], [TYPE-25]) or a number given for a node ID in a context ([CTX-1]).

**[VEC-36]** Each level name and node ID that is a mapping key in `context`, `providers` or `providerCalls` MUST be a YAML string scalar. A numeric-looking node ID MUST be quoted. A mapping key that is not a string scalar is a runner failure. Enforcer: runner, and the check tool for the quoting convention.

**[VEC-37]** The optional `providers` field, if present, MUST be a mapping from a level name to either the marker `none` or a stub. A stub MUST be a mapping from node ID to an outcome. Any other value is a runner failure.

**[VEC-38]** Every level name in `providers` or in `providerCalls` ([VEC-65]) MUST name a service-sourced level of the bundle in `bundle`. A vector that names any other level in either field is a runner failure. Dependency: [EVAL-27] (OPEN), for what an evaluator does with such a registration, and [SVC-13], which gives a service-sourced level its values only from its provider.

**[VEC-39]** A service-sourced level of the bundle that `providers` does not name MUST behave as a stub with no nodes. Any provider invocation on that level is then a call to a node that the stub does not list ([VEC-46]) ([SVC-14]).

**[VEC-40]** A level entry of `none` MUST make the runner register no provider for that level, so that creating the evaluator fails. The vector MUST then expect `error: E_PROVIDER_MISSING` ([EVAL-4], [SVC-14]).

**[VEC-41]** Each node outcome in a stub MUST have exactly one of two forms: `values`, or `fail`. Any other shape is a runner failure.

**[VEC-42]** The `values` form MUST be a mapping from key names to YAML values, which the provider returns for that node. The runner MUST pass each value through without conversion ([VEC-33]).

**[VEC-43]** The `fail` form MUST take the value `unavailable` or `timeout`, and the provider fails for that node with that outcome. Any other value is a runner failure. Each key being resolved at that level then has a source failure with outcome `UNAVAILABLE` ([SVC-23], [SVC-39]).

**[VEC-44]** For each evaluate vector whose stub uses one fail outcome, the suite MUST contain a paired vector that differs from it only in `id`, `description`, `clauses` and the fail value, and whose `expect` is identical. The pair shows that `unavailable` and `timeout` give identical results, because [SVC-23] and [SVC-39] treat both as unavailability.

**[VEC-45]** *(OPEN)* How is a `timeout` outcome produced in a test without waiting for a wall-clock timeout? Proposal: the stub signals the evaluator's timeout path at once, through a hook that test harnesses use. Timeout durations belong to the runtime integration (ADR 0009, section 3) and do not appear in vectors. Dependency: [SVC-41] (OPEN) for the interval that a timeout measures.

**[VEC-46]** A provider invocation for a node that the stub does not list MUST be a runner failure, whatever `expect` says. The runner MUST NOT treat such a call as an empty result or as omitted keys ([SVC-36]).

**[VEC-47]** Every provider invocation MUST count toward `providerCalls`, including an invocation whose outcome is a fail outcome ([SVC-19], [SVC-23]).

**[VEC-48]** The `expect` field MUST be a mapping of one of two forms. The first form has `error`, with one error code and no result field. The second form has no `error`. Either form MAY also have `providerCalls` ([VEC-65]).

**[VEC-49]** The second form of `expect` MUST contain at least one result field. An `expect` that has neither `error` nor a result field, or that has only `providerCalls`, is a runner failure, because every evaluate vector checks either its result or its error.

**[VEC-50]** An `error` expectation MUST be met only when one of the three steps of [VEC-24] raises an error with exactly that code ([EVAL-18], [EVAL-20]). A result, or an error with another code, fails the vector. For `E_PROVIDER_MISSING` the error is raised when the evaluator is created, before any call ([EVAL-4], [SVC-14]).

**[VEC-51]** `expect.error` MUST name a code that the evaluation operations are allowed to raise ([EVAL-38]). Any other code is a runner failure.

**[VEC-52]** Error messages MUST NOT be compared. Only the code is normative ([EVAL-22]).

**[VEC-53]** Result field names and shapes in `expect` MUST be the ones that PROV defines for the result kind ([PROV-10], [PROV-11], [PROV-62], [PROV-63]) and that EVAL defines for the operation ([EVAL-7], [EVAL-8]).

**[VEC-54]** Unless `exact` is `true`, a result field is compared only when `expect` names it. A named mapping matches when each of its named keys matches, recursively. Unnamed keys are not compared.

**[VEC-55]** A key named in `expect` that is absent from the actual mapping never matches, even when the expected value is `null`. Under [PROV-3] an absent conditional field is absent, not `null`.

**[VEC-56]** An expected `null` MUST match only an actual `null` ([PROV-3], [PROV-17]).

**[VEC-57]** A scalar matches only when the actual value has the same JSON type and an equal value. Booleans match only booleans, and integers compare by value ([TYPE-12], [TYPE-16], [NORM-17]).

**[VEC-58]** Strings compare by exact code-point sequence, without normalisation ([NORM-3], [NORM-6]).

**[VEC-59]** A decimal value is a string and is compared as a string, by equality only ([TYPE-26], [TYPE-27], [RULE-37]).

**[VEC-60]** Each decimal in `expect` MUST be written in canonical form ([NORM-9]). The runner MUST NOT canonicalise expected values. For a `resolve` vector whose key the bundle declares as `decimal`, the check tool MUST reject an expected value that is not canonical. Enforcer: check tool.

**[VEC-61]** *(OPEN)* How do sequences match? Proposal: under subset matching, an expected sequence matches an actual sequence of the same length whose items match one-to-one and in order, each item matched by the rules of this subsection. An expected empty list then matches only an empty actual list. The orders of contributions, skipped scopes, trace entries and set members are fixed by [PROV-34], [PROV-58], [PROV-82] and [PROV-86]. This is stricter than the subset match that requirements §5.7 sets for `expect` fields, because an expected sequence must then list every actual item. If the review prefers partial lists under subset matching, the alternative is an ordered subsequence match for sequences of mappings only, with scalar sequences kept exact so that an expected `[]` never matches an arbitrary list.

**[VEC-62]** The optional top-level `exact` field MUST be a boolean that defaults to `false`.

**[VEC-63]** When `exact` is `true`, every mapping in the actual result MUST have exactly the keys that `expect` names at that position, recursively ([PROV-1], [PROV-3]). Its sequences match as [VEC-61] defines, so this clause depends on that OPEN clause.

**[VEC-64]** When `exact` is `true`, a member that the result omits under its provenance clause MUST be absent from `expect` at that position. Examples are `node` on a root contribution ([PROV-25]), `value` on a failure entry under `skip` ([PROV-30]), `onSourceFailure` on a value entry ([PROV-32]) and `source` for a commutative key ([PROV-45]).

**[VEC-65]** The optional `providerCalls` field MUST be a mapping from level name to a mapping from node ID to a non-negative integer. Each count is the number of provider invocations for that level and node during the operation under test. A count of `0` asserts that the node was not called ([SVC-19]).

**[VEC-66]** Unless `exact` is `true`, only the level and node pairs that `providerCalls` names are compared.

**[VEC-67]** When `exact` is `true`, every provider invocation for a level and node pair that `providerCalls` does not name MUST count as a mismatch.

**[VEC-68]** The outcome of each case in the table below MUST be as the table states.

| Case | Outcome | Clauses |
| --- | --- | --- |
| `context: null` | Runner failure | [VEC-28] |
| `context: { levels: null }` | `E_CONTEXT_INVALID`; a valid vector | [CTX-1] |
| A context node ID written as an integer | `E_CONTEXT_INVALID` | [CTX-1], [CTX-6] |
| `input: {}` on `decide`, with both members omitted | Withheld while [FACT-24] is OPEN. The suite uses `input: { facts: {} }`, which is a valid FactSet with every fact absent | [FACT-24], [FACT-16] |
| `input: null` on `decide` | The operation receives `null` as the FactSet and raises `E_FACTS_INVALID` | [FACT-22], [EVAL-36] |
| `expect: {}`, or `expect` with only `providerCalls` | Runner failure | [VEC-49] |
| `expect` with both `error` and a result field | Runner failure | [VEC-48] |
| `value: null` expected, with no contributing scope | Valid; matches only an actual `null` | [MERGE-6], [PROV-17] |
| A `null` value in a stub's `values`, for a key being resolved at that level | An `INVALID` source failure for that key | [SVC-34], [SVC-35] |
| A `null` value in a stub's `values`, for a declared key that the call does not resolve | Withheld: the suite MUST NOT contain it while [SVC-28] is OPEN | [SVC-28] (OPEN) |
| `values: {}` in a node outcome | Valid; the provider returns no entries, so no key is set and no contribution is made | [SVC-36] |
| `values: null` in a node outcome, or a node outcome that is `null` | Runner failure | [VEC-41] |
| A stub with no nodes for a service-sourced level, and any invocation | Runner failure | [VEC-46] |
| A `providers` entry of `none` | Creating the evaluator fails with `E_PROVIDER_MISSING` | [EVAL-4], [SVC-14] |
| `providers` names a level that is not service-sourced | Runner failure | [VEC-38] |
| `providerCalls` names a level that is not service-sourced | Runner failure | [VEC-38] |
| `providerCalls` names a node that the stub does not list | Valid; a count above zero can never be met, because the call would be a runner failure | [VEC-46] |
| `providerCalls: {}` | Valid; asserts nothing unless `exact` is `true` | [VEC-66], [VEC-67] |
| A key named in `expect` that the actual mapping lacks | Never matches | [VEC-55] |

**[VEC-69]** Each bundle file under `bundles/invalid/` MUST contain exactly one fault from the list in [VEC-71], and MUST be otherwise a canonical bundle with the bytes that BUNDLE and DET define.

**[VEC-70]** A bundle file under `bundles/invalid/` MUST be referenced only by evaluate vectors whose expected outcome is the load error that its fault raises ([EVAL-24], [EVAL-25]).

**[VEC-71]** For each of the four faults below, the suite MUST contain a bundle file under `bundles/invalid/` that has that fault, and an evaluate vector that loads it and expects the code shown: a duplicate property name at any depth, expecting `E_BUNDLE_INTEGRITY` ([BUNDLE-111], [EVAL-24]); a member of the envelope other than `bundleVersion` and `content`, expecting `E_BUNDLE_INTEGRITY` ([BUNDLE-112], [EVAL-24]); a `content` that does not match its `bundleVersion`, which is changed content with an old digest, expecting `E_BUNDLE_INTEGRITY` ([BUNDLE-113], [EVAL-24]); and an unsupported `bundleFormatVersion` or `specVersion`, expecting `E_BUNDLE_UNSUPPORTED` ([BUNDLE-115], [EVAL-25]). In the unsupported-version file the digest MUST match the content, so that the version is the only fault, and the unsupported value MUST NOT be a version that this specification defines ([BUNDLE-16], [BUNDLE-17]), so that the outcome does not depend on [BUNDLE-137] (OPEN).

**[VEC-72]** A bundle file under `bundles/invalid/` MUST NOT be invalid only by a byte-level fault, such as a byte-order mark, a trailing newline or non-canonical whitespace, because [BUNDLE-116] is OPEN.

> **Note (non-normative):** The order in which load checks run is not yet defined ([EVAL-14], [BUNDLE-117]). No vector depends on that order, as [VEC-114] requires.

**[VEC-73]** An evaluate vector that expects an error MUST contain exactly one fault, unless it tests the order of checks under [VEC-74].

**[VEC-74]** A vector that tests the order of checks MAY contain more than one fault. Its expected code MUST be the code of the first failing check in the order that [EVAL-16] and [EVAL-17] give, and, within context validation, the order that [CTX-9] gives. It MUST NOT place two of its faults in one step of [CTX-9], because [CTX-11] and [EVAL-19] are OPEN for that case.

> **Example (non-normative): decide success**
>
> ```yaml
> id: decide/mixed-basket-on-credit-hold
> specVersion: "0.1"
> clauses: [RULE-31, RULE-50]
> description: A mixed basket on a credit hold takes the default outcome.
> call: decide
> bundle: ../../bundles/commerce.json
> context: { levels: { division: south } }
> feature: checkout.order_submission
> input:
>   facts: { customer.active: true, customer.credit_hold: true }
>   children:
>     item:
>       - { item.stock_type: STOCKED }
>       - { item.stock_type: DROP_SHIP }
> expect:
>   effect: DENY
>   reason: { type: DEFAULT }
>   binding: { level: division, node: south }
> ```
>
> The vector omits `providers`, so every service-sourced level gets an empty stub ([VEC-39]). A stricter variant names an account in `context` and adds `providerCalls: { account: { acct-17: 0 } }`. Decide never calls a provider, so that count is always `0`.

> **Example (non-normative): provider values**
>
> ```yaml
> id: resolve/account-values-from-provider
> specVersion: "0.1"
> clauses: [SVC-15, SVC-17]
> description: The account provider is called once for a key settable at account, and its value narrows the result.
> call: resolve
> bundle: ../../bundles/commerce.json
> context: { levels: { division: south, account: acct-17 } }
> key: checkout.shipping.allowed_methods
> providers:
>   account:
>     acct-17:
>       values: { checkout.shipping.allowed_methods: [GROUND] }
> expect:
>   value: [GROUND]
>   providerCalls: { account: { acct-17: 1 } }
> ```

> **Example (non-normative): context rejected before any call**
>
> ```yaml
> id: decide/unknown-division-before-any-call
> specVersion: "0.1"
> clauses: [CTX-10, EVAL-33]
> description: An unlisted division is rejected before the account provider is called.
> call: decide
> bundle: ../../bundles/commerce.json
> context: { levels: { division: north-pole, account: acct-17 } }
> feature: checkout.order_submission
> input: { facts: { customer.active: true } }
> providers:
>   account:
>     acct-17:
>       values: {}
> expect:
>   error: E_UNKNOWN_NODE
>   providerCalls: { account: { acct-17: 0 } }
> ```

> **Example (non-normative): synchronous call that would need a provider**
>
> ```yaml
> id: resolve/sync-provider-required
> specVersion: "0.1"
> clauses: [EVAL-11, EVAL-29]
> description: The synchronous variant of resolve raises E_PROVIDER_REQUIRED and calls no provider.
> call: resolve
> sync: true
> bundle: ../../bundles/commerce.json
> context: { levels: { division: south, account: acct-17 } }
> key: checkout.shipping.allowed_methods
> providers:
>   account:
>     acct-17:
>       values: { checkout.shipping.allowed_methods: [GROUND] }
> expect:
>   error: E_PROVIDER_REQUIRED
> ```
>
> Any invocation of the account stub fails the vector, because the synchronous variant must not make one ([VEC-26]).

Evaluate vectors do not assert listener events ([EVAL-41]), because those events are not part of a result.

## 12.4 Canonical vectors

A canonical vector checks the two primitives that the bundle clauses rely on: JSON Canonicalization Scheme (JCS, as [BUNDLE-7] and [DET-5] define) and SHA-256. A canonical vector is independent of any bundle.

**[VEC-75]** A canonical vector MUST have `primitive`, whose value is `jcs` or `sha256`. The fields that it then has are set by [VEC-77] and [VEC-81].

**[VEC-76]** A canonical vector MUST NOT have a `bundle` field.

**[VEC-77]** A `jcs` vector MUST have `input`, a value of the JSON data model written in YAML, and `expect.output`, the expected bytes.

**[VEC-78]** The runner MUST compute the JCS encoding of `input` and compare the resulting bytes with `expect.output` exactly ([BUNDLE-2], [BUNDLE-7]).

**[VEC-79]** The `input` of a `jcs` vector MUST consist only of strings without an unpaired surrogate, booleans, `null`, arrays, objects, and integers within ±(2^53 − 1) ([BUNDLE-11], [BUNDLE-12], [BUNDLE-13], [DET-4]). Any other input is a runner failure.

**[VEC-80]** *(OPEN)* How should a `jcs` vector treat non-integer numbers and integers outside ±(2^53 − 1)? Proposal: no canonical vector tests them until [BUNDLE-116] (OPEN) decides which code applies. Duplicate property names cannot be written in a YAML mapping, because vector files reject duplicate keys ([SRC-3]), so they are tested as bundle files under `bundles/invalid/` ([VEC-71]). Dependency: [BUNDLE-116] (OPEN).

**[VEC-81]** A `sha256` vector MUST have `input`, the bytes to hash, and `expect.digest`, the expected digest.

**[VEC-82]** The runner MUST compute the SHA-256 digest of the input bytes and compare it with `expect.digest` exactly ([BUNDLE-6]).

**[VEC-83]** `expect.digest` MUST be 64 lowercase hexadecimal characters with no prefix, written as a quoted YAML string. Enforcer: schema (pattern and string type).

**[VEC-84]** *(OPEN)* How are bytes written in YAML, for `input` and `expect.output`? Proposal: each byte field takes exactly one of two keys. `utf8` takes a YAML string whose parsed value is encoded as UTF-8 exactly, with no added newline and no normalisation. Block scalars add a trailing newline, so authors write `utf8` values as quoted scalars or with `|-`. `hex` takes lowercase hexadecimal of even length, and the empty string for no bytes. Use `hex` for any byte sequence that is not valid UTF-8. A `hex` value MUST be a quoted string, so that YAML does not read it as a number.

**[VEC-85]** The `jcs` vectors MUST include a case in which member names sort differently by UTF-16 code units and by code points, as the example below shows ([BUNDLE-9], [DET-5]).

**[VEC-86]** The `jcs` vectors MUST include a case with non-ASCII member names ([BUNDLE-9], [BUNDLE-10]).

**[VEC-87]** The `jcs` vectors MUST include a case with string escapes ([BUNDLE-10]).

**[VEC-88]** The `jcs` vectors MUST include cases with integers at 2^53 − 1 and at −(2^53 − 1) ([BUNDLE-11], [BUNDLE-12], [DET-4]).

**[VEC-89]** The `sha256` vectors MUST include an empty input, and inputs of 55, 56 and 64 bytes, which lie on the padding boundaries of the algorithm ([BUNDLE-6]).

> **Example (non-normative): canonical encodings**
>
> ```yaml
> id: jcs/key-order-utf16
> specVersion: "0.1"
> clauses: [BUNDLE-9, DET-5]
> description: Member names U+E000 and U+1F600 sort by UTF-16 code units, so U+1F600 comes first although it is the larger code point.
> primitive: jcs
> input: { "\uE000": 1, "\U0001F600": 2 }
> expect:
>   output: { utf8: "{\"\U0001F600\":2,\"\uE000\":1}" }
> ```
>
> ```yaml
> id: sha256/empty-input
> specVersion: "0.1"
> clauses: [BUNDLE-6]
> description: The digest of zero bytes.
> primitive: sha256
> input: { utf8: "" }
> expect:
>   digest: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
> ```

## 12.5 Compile cases

A compile case compiles its project and compares the outcome with `expect`. The compile behaviour and finding codes are defined by the clause areas for sources, checks and bundles (SRC, MANIFEST, NS, FIND and BUNDLE, among others). The bundle bytes are defined by BUNDLE and DET. A compile case lists every finding that the compiler reports ([FIND-3]).

**[VEC-90]** The `expected.yaml` of a compile case MUST have the fields of [VEC-11] to [VEC-17], an `expect` mapping that has exactly one of `bundle` or `findings`, and no other field except the optional `project` ([VEC-91]) and, if [VEC-107] adopts it, `build`.

**[VEC-91]** The optional `project` field of `expected.yaml`, if present, MUST be a path relative to the directory of `expected.yaml` that resolves to a directory inside the repository's `samples/` directory. The case then compiles that sample in place, and the case MUST NOT contain a `project/` directory. This is the sample exception to [VEC-5].

**[VEC-92]** The runner MUST compile the case's project directory, which is `project/` or the directory named by `project`, as the project root. The project of a compile case MUST NOT name a path that resolves outside its project root, because whether that is allowed is [MANIFEST-12] (OPEN).

**[VEC-93]** `expect.bundle` MUST be a relative path that resolves, from the directory of `expected.yaml`, to a file inside `bundles/`, and not to a file inside `bundles/invalid/`.

**[VEC-94]** A `bundle` expectation MUST be met only when the compiler reports no finding of severity `error` ([FIND-5], [FIND-8]) and its output bytes are identical to that file ([DET-1]).

**[VEC-95]** *(OPEN)* Which finding severities does a compile case compare? Proposal: all of them. Findings of severity `warn` and `info` then have to be listed under `findings`. Under this proposal a `bundle` expectation also requires zero findings of any severity, and a sample that yields an `info` finding, such as `presets/override`, must list it or change its configuration. The alternative compares only `error` findings, which lets a `bundle` expectation tolerate warnings. [FIND-8] leaves that tolerance to this clause, and [FIND-6] makes `warn` and `info` findings non-fatal.

**[VEC-96]** `expect.findings` MUST be a non-empty list in which no two entries are equal. Enforcer: check tool (duplicate entries).

**[VEC-97]** Each entry of `expect.findings` MUST have exactly the fields `code`, `file`, `path` and `line`. The entry MUST NOT include a message, a suggestion or a severity ([FIND-10], [FIND-24]).

**[VEC-98]** A `findings` expectation MUST be met only when the set of `(code, file, path, line)` tuples that the compiler reports equals the set of tuples that the case lists, with no extra tuple and no missing tuple. The compared tuple is the finding key of [FIND-24]. The comparison is a set, not a multiset: a tuple that the compiler reports more than once matches one listed entry. The outcome therefore does not depend on whether a compiler may report one finding key twice, which is [FIND-25] (OPEN).

**[VEC-99]** Codes MUST be compared exactly ([FIND-24]).

**[VEC-100]** Each code in `expect.findings` MUST be an engine error code of requirements §6.1 or a lint rule identifier ([FIND-12], [FIND-13], [LINT-1]). Enforcer: schema (enumeration or pattern).

**[VEC-101]** Messages and suggestions MUST NOT be compared ([FIND-10], [FIND-28]).

**[VEC-102]** `file` MUST be the source path relative to the project root, with `/` as the separator ([FIND-17]).

**[VEC-103]** `line` MUST be a positive integer, the 1-based line that [FIND-21] gives for the finding. Enforcer: the schema checks the integer type and the minimum of 1; the runner checks the value. Dependency: [FIND-22] (OPEN, shared with [SRC-9]), which decides the line of a node.

**[VEC-104]** *(OPEN)* How is a finding's YAML path written? Proposal: the `path` field is a list. A string item is a mapping key, compared by exact code point. A non-negative integer item is a zero-based sequence index. The root is the empty list `[]`. A list avoids escaping, because fact and key names contain dots. Example: `[features, "a.b", rules, 1]`. Dependency: [FIND-20] (OPEN), which asks the same question for findings and must be decided with this clause. [FIND-19] fixes the node that a path identifies.

**[VEC-105]** For each sample that has a bundle file in `bundles/`, the suite MUST contain a compile case that compiles that sample in place ([VEC-91]) and whose `bundle` expectation names that bundle file. Dependency: [DET-1].

**[VEC-106]** The suite MUST include a compile case in which the node IDs `no`, `yes`, `on` and `off`, spelled exactly so, each compile as strings. Its `bundle` expectation MUST name a bundle file in `bundles/` that holds each of them as a string ([SRC-1], [BUNDLE-38], [BUNDLE-66]).

**[VEC-107]** *(OPEN)* How does a case supply build metadata, to test that provenance does not change bundle bytes? Proposal: `expected.yaml` may hold an optional `build` mapping, with `gitCommit`, `createdAt` and `publisher`, which the runner passes to the provenance writer. Two cases with the same project and different `build` mappings must both expect the same bundle file ([DET-3]). The provenance record itself is not compared, because [BUNDLE-139] keeps it outside the bundle. This clause tests bundle bytes only.

> **Example (non-normative): compile findings**
>
> ```yaml
> id: duplicate-key-in-values
> specVersion: "0.1"
> clauses: [SRC-3]
> description: A repeated key in a values file is reported at the repeated key.
> expect:
>   findings:
>     - code: E_DUPLICATE_KEY
>       file: values/division/south.yaml
>       path: [checkout.pickup.lead_hours]
>       line: 7
> ```
>
> The `path` form is the proposal in [VEC-104] (OPEN), and the `line` depends on [SRC-9] (OPEN). The example is illustrative until both are settled.

## 12.6 Runners, loading and coverage

**[VEC-108]** Runners MUST read vectors and bundle files in place from `spec/conformance/`.

**[VEC-109]** Runners MUST NOT copy the suite into another directory tree, and MUST NOT embed copies of it in code.

**[VEC-110]** A runner MUST NOT write into `spec/conformance/`. Output that a run produces, such as a compiled bundle, MUST go to a temporary location.

**[VEC-111]** A runner MUST execute every vector of each kind that its implementation is required to pass under the conformance classes in `spec.md`. It MUST report as a runner failure any such vector that it cannot execute.

**[VEC-112]** Every vector file, including each compile case `expected.yaml`, MUST validate against the JSON Schema for its kind in `spec/schemas/`. Enforcer: schema, run by the check tool.

**[VEC-113]** An implementation MUST NOT change a vector, its expected result or its bundle file in order to make a vector pass.

**[VEC-114]** A vector or compile case MUST NOT depend on the outcome of a clause marked *(OPEN)*. Where the expected outcome would change with the answer, the vector is withheld until the clause is settled, and the coverage record lists the clause as uncovered.

**[VEC-115]** Every normative clause of this specification that is not marked *(OPEN)* MUST be cited in the `clauses` list of at least one vector. Enforcer: check tool, which computes the clause-to-vector mapping for work item 1.18.

**[VEC-116]** Each engine error code of requirements §6.1 MUST have a positive and a negative compile case. The positive case's `findings` expect the code. The negative case is a negative control ([VEC-120]) that does not produce it. Until the catalogue `spec/error-codes.md` (work item 1.6) exists, the list is requirements §6.1. The code `E_EXAMPLE_FAILED` is covered as [VEC-121] defines.

**[VEC-117]** Each evaluation error code of requirements §6.2 MUST have a positive and a negative evaluate vector. The positive vector expects the code. The negative control is an evaluate vector of the same operation that does not raise it.

**[VEC-118]** A code that is listed in both requirements §6.1 and §6.2, namely `E_UNKNOWN_SCOPE`, `E_UNKNOWN_NODE` and `E_UNKNOWN_KEY`, MUST have a positive and a negative compile case and a positive and a negative evaluate vector.

**[VEC-119]** Each lint rule identifier that [FIND-13] makes the code of a lint finding MUST have a positive and a negative compile case. The identifiers are those that [LINT-1] lists, including each naming rule `naming/<name>` that [LINT-12] gives. A rule whose check depends on an OPEN clause of section 13, such as [LINT-14] for where naming patterns are declared, [LINT-38] for when a key is used, or [LINT-42] for references from unused declarations, is covered when that clause is settled.

**[VEC-120]** *(OPEN)* What is a negative control for an engine or evaluation error code? Proposal: a compile case whose project equals a positive case except for the minimal change that removes the trigger, and whose expectation is a `bundle`, or a `findings` list that does not contain the code. For an evaluate vector, the negative control is the same operation with an input that does not trigger the code. The roadmap's Phase 1 exit criterion and implementation plan item 1.12 ask for a positive and a negative compile case per code.

**[VEC-121]** *(OPEN)* How is `E_EXAMPLE_FAILED` covered? [POL-42] defines the code: an example whose decided effect differs from its `expect` is reported with it. [POL-47] (OPEN) decides which operations run examples: validation and compilation, a separate test operation, publication, or several of these. [POL-46] (OPEN) decides which code an example gets when its decision raises an evaluation error. If validation or compilation runs the examples, compile cases cover the code under [VEC-116]. If only a separate test operation runs them (implementation plan item 3.7), the proposal is to add an `examples/` directory of cases with the same `expect` forms, which runs the example operation and has the two polarities of [VEC-117]. Only if that proposal is accepted does `examples/` join the directories in [VEC-1]. Until [POL-47] is decided, the code is uncovered and [VEC-116] fails for it. Dependencies: [POL-46] (OPEN) and [POL-47] (OPEN).

**[VEC-122]** The evaluate vectors MUST include cases that use a bundle for each of the two sample domains, domain A and domain B, as requirements §1.1 defines them.

**[VEC-123]** The suite MUST include at least one case for each of the following families, and each case MUST cite the clauses listed for its family:

1. An ignored non-root scope and an omitted non-root scope give the same resolved value, decision, binding and contributions, apart from `skippedScopes` ([CTX-14], [CTX-21], [PROV-56]).
2. An invalid later scope is rejected before any provider call, including for an earlier scope that is eligible ([CTX-9], [CTX-10]).
3. An ignored service-sourced scope makes zero provider invocations and triggers no source-failure handling under `skip` or `deny` ([CTX-15], [SVC-17]).
4. An independent later scope stays eligible after another scope is ignored ([CTX-17]).
5. An unknown source ID in a closed catalog is `E_UNKNOWN_NODE` under both `on_unknown_node` policies. This is a compile case ([SCOPE-43], [SCOPE-44]).
6. An open catalog admits a new valid ID and calls the configured provider for it ([CTX-13], [SVC-17]).
7. Several ignored scopes give `skippedScopes` in level-then-axis order, with no contribution entry for any of them ([PROV-58], [PROV-61], [CTX-16]).
8. The root binding is always present: a decision with no non-root binding uses the root binding ([BIND-13], [EVAL-21]).
9. A string given where a boolean fact is declared is `E_FACTS_INVALID` ([FACT-35], [NORM-17]).
10. A mixed child collection under a restrictive policy takes the policy's default ([RULE-31], [RULE-50]).
11. A child fact in an `eq` or `in` condition is `E_QUANTIFIER_REQUIRED`. This is a compile case ([RULE-13]).
12. The missing-fact precheck returns `if_fact_missing` with the same reason whatever the rule order ([RULE-40], [RULE-42], [RULE-44]).
13. The provider-outcome matrix: values, omitted key, unknown key, invalid entry, unavailable and timeout, each crossed with `skip` and `deny` ([SVC-8], [SVC-9], [SVC-23], [SVC-27], [SVC-34], [SVC-36], [SVC-39], [PROV-27], [PROV-29], [PROV-30], [PROV-39], [PROV-40], [PROV-41], [PROV-42]). Omitted and unknown keys produce no failure, so their cells show that `skip` and `deny` have no effect.
14. A node ID of the wrong type, or an unknown scope name, under `on_unknown_node: ignore` is rejected ([CTX-18], [CTX-1], [CTX-4]).
15. A generated catalog: a compile case compiles a scope with `nodes: { from: ... }` to a bundle file, and evaluate vectors on that bundle admit a catalog ID and handle an unlisted ID by its `on_unknown_node` ([SCOPE-31], [CTX-12], [CTX-14]).
16. A context that names the root level is `E_CONTEXT_INVALID` under either `on_unknown_node` setting of the other scopes ([CTX-3], [CTX-18]).

**[VEC-124]** *(OPEN)* Where are the provenance-linkage cases tested? They are [BUNDLE-144] (a sidecar that names another `bundleVersion`) and [BUNDLE-148] (several records for one version). No vector kind covers sidecars. Proposal: add a `provenance/` family in which each case names a bundle file and a sidecar file, and expects acceptance, or rejection with the code that [BUNDLE-151] settles. Until this is decided, [BUNDLE-139] is covered through [VEC-107], and [BUNDLE-144] and [BUNDLE-148] are uncovered, so [VEC-115] fails for them. Dependency: [BUNDLE-151] (OPEN).

**[VEC-125]** *(OPEN)* How is a clause that no vector can observe covered? [VEC-115] requires every normative clause that is not OPEN to be cited by a vector, but some clauses have no observable case. Examples are [VEC-108] to [VEC-110] (runner behaviour), [EVAL-46] to [EVAL-52] (immutability and concurrency), [FIND-29] (the explanation catalogue), [SVC-29] under a single-key `resolve` (see [SVC-17]), and the directory and identifier rules that the check tool enforces. Proposal: the coverage record lists each such clause with its enforcer, which is the check tool, the schema, or a named unit or property test from requirements §12. The check tool accepts a clause that has either a citing vector or a listed enforcer. Dependency: [VEC-115].

> Implementation note: the Java runner uses JUnit dynamic tests, and the JS runner uses `node:test`. Both locate `spec/conformance/` from the repository layout and read it in place. The spec tool `check` validates the schemas, bundle digests, clause citations and error-code coverage, and writes the coverage record from the clause-to-vector mapping.

---

## Open points

Each item below is an OPEN clause, with the proposal that the clause states.

- [VEC-6]: encoding and byte-order mark of vector files (proposal: those that [SRC-10] settles).
- [VEC-8]: explicit tags and anchors in vector files (proposal: runner failure, as [SRC-7] proposes).
- [VEC-27]: runner without a synchronous variant (proposal: follow [EVAL-9]).
- [VEC-45]: how a `timeout` outcome is produced without waiting (proposal: an immediate hook).
- [VEC-61]: how sequences match under subset and exact matching (proposal: one-to-one and in order, stricter than the requirements §5.7 subset match).
- [VEC-80]: canonical inputs outside the JSON data model domain (proposal: not tested until [BUNDLE-116] is decided).
- [VEC-84]: how bytes are written in YAML (proposal: `utf8` or `hex`, quoted).
- [VEC-95]: which finding severities a compile case compares (proposal: all of them, so a `bundle` expectation needs zero findings).
- [VEC-104]: how a finding's YAML path is written (proposal: a list; depends on [FIND-20]).
- [VEC-107]: how a case supplies build metadata for the bundle-byte independence check (proposal: a `build` mapping).
- [VEC-120]: what counts as a negative control for an error code (proposal: a minimal change that removes the trigger).
- [VEC-121]: where `E_EXAMPLE_FAILED` is tested, since [POL-47] is OPEN (proposal: an `examples/` family).
- [VEC-124]: where the provenance-linkage cases are tested (proposal: a `provenance/` family).
- [VEC-125]: how clauses that no vector can observe are covered (proposal: the coverage record names an enforcer for each, and the check tool accepts a citing vector or an enforcer).

## Drafting notes for the reviewer (non-normative)

- The sample exception (VEC-91) is written as normative, on the review's recommendation. VEC-105 depends on it. Confirm it at acceptance.
- VEC-106 is extended to `yes`, `on` and `off`, which SRC-1 also names. Confirm the extension.
- Until VEC-124 is decided, BUNDLE-144 and BUNDLE-148 are uncovered, so the coverage check of VEC-115 fails for them.
- The consultation rows on an explicitly ignored unknown root and on a missing eligible feature binding are superseded by ADR 0007 §1, and are covered by VEC-123 items 8 and 16. The skipped-root reinsertion case cannot occur, and the "no eligible binding" error is unreachable, so the spec drops it.
- No numbered clause names the SHA-256 algorithm on its own; BUNDLE-6 names it for the digest, and VEC-82 and VEC-89 cite that clause. Section 10 may want a clause that cites FIPS 180-4.
