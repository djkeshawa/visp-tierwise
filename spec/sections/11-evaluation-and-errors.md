# 11. Evaluation operations and errors

This section defines the operations an evaluator offers: load bundle, create evaluator, resolve, decide, and the synchronous variants of resolve and decide (variants that return their result directly and never call a provider). It fixes the order of checks for each operation, which error is raised when several checks fail, the catalogue of evaluation error codes, the listener events tied to each operation, and the immutability, determinism and concurrency rules for loaded bundles and evaluators. It does not define what a valid context is (section 2, `CTX`), what a valid FactSet is (section 3, `FACT`), how providers, source failures and provider events behave (section 6, `SVC`), how values merge (section 5, `MERGE`), how bindings are selected and rules run (sections 7 and 8, `BIND` and `RULE`), what a result contains (section 9, `PROV`), or the byte format of a bundle and the loader checks (section 10, `BUNDLE` and `DET`). Those rules are cited here by clause ID and are not restated.

## 11.1 Operations

**[EVAL-1]** An implementation MUST offer the operations load bundle, create evaluator, resolve and decide.

**[EVAL-2]** Load bundle MUST take the bytes of one bundle, and MUST either return a loaded bundle or raise `E_BUNDLE_INTEGRITY` or `E_BUNDLE_UNSUPPORTED` ([EVAL-24], [EVAL-25]).

**[EVAL-3]** Load bundle MUST be synchronous.

> Implementation note: ADR 0012 §3 keeps the JavaScript `loadBundle` synchronous by bundling its own SHA-256. The JavaScript loader also accepts a string. It encodes the string as UTF-8 before any check, so the checks of section 10 apply to the encoded bytes.

**[EVAL-4]** Create evaluator MUST take a loaded bundle and evaluator options, and MUST either return an evaluator or raise `E_PROVIDER_MISSING` ([EVAL-26]). The errors for invalid provider registrations and options are open in [EVAL-27] and [EVAL-28]. The options supply one provider for each service-sourced level ([SVC-14]), a timeout for each provider ([SVC-38]), and the listener ([EVAL-41]).

**[EVAL-5]** Create evaluator MUST be synchronous.

**[EVAL-6]** Create evaluator MUST NOT call a provider. Providers are called only as [SVC-17] allows.

**[EVAL-7]** `resolve(context, key)` MUST either return a `Resolved` result ([PROV-10]) or raise an error of [EVAL-16]. It calls providers only as [SVC-17] to [SVC-19] define.

**[EVAL-8]** `decide(context, feature, factset)` MUST either return a `Decision` result ([PROV-62]) or raise an error of [EVAL-17]. It calls no provider ([SVC-22]).

**[EVAL-9]** *(OPEN)* Must every implementation offer the synchronous variants of resolve and decide? Requirements §7.2 gives them for one language only, and ADR 0009 §1 names only `resolveSync`. In the requirements §7.1 sketch, resolve returns directly and its providers are blocking calls, so that sketch has no variant that refuses to call a provider. Conformance vectors name operations, so a variant that one implementation lacks cannot be tested on it. Proposed: every implementation offers both variants, with the same codes, so that one set of evaluate vectors covers both. Until this is decided, [EVAL-10] to [EVAL-12] apply to an implementation that offers the variant.

**[EVAL-10]** For an input that needs no provider call under [SVC-42], the synchronous variant of resolve MUST return the same result, or raise the same error, as resolve.

**[EVAL-11]** For an input that needs a provider call under [SVC-42], the synchronous variant of resolve MUST run steps 1 to 3 of [EVAL-16] and raise the error of the first failing step. It raises `E_PROVIDER_REQUIRED` at step 3 ([EVAL-29]).

**[EVAL-12]** For every input, the synchronous variant of decide MUST return the same result, or raise the same error, as decide.

> Implementation note: the JavaScript evaluator exposes the synchronous variants as `resolveSync` and `decideSync`. The Java evaluator raises a `TierwiseException` whose `code()` is the evaluation error code, and the JavaScript evaluator throws a `TierwiseError` that exposes the code (ADR 0009 §4).

**[EVAL-13]** An operation MUST NOT have a parameter that selects one scope or one node for which it returns a value. A scope's own value appears only as a contribution inside a combined `Resolved` result ([PROV-20], [PROV-28]), as requirements §4.8 and §7 together allow.

## 11.2 Check order

**[EVAL-14]** *(OPEN)* In what order does load bundle apply the loader checks of section 10 ([BUNDLE-111] to [BUNDLE-116], and any adopted under [BUNDLE-118] to [BUNDLE-138]), and which error does it raise when several fail? [BUNDLE-117] defers to this clause. Two proposals exist. Section 10 proposes: well-formedness and strict parsing ([BUNDLE-111], [BUNDLE-112], [BUNDLE-116]), then the digest ([BUNDLE-113]), then the versions ([BUNDLE-115]), then structure. The section 11 review proposes: strict parsing and the envelope, then the versions, then the digest, then structure, so that a bundle in a future format reports `E_BUNDLE_UNSUPPORTED` and not `E_BUNDLE_INTEGRITY`. Under both, the first failing check decides the code.

**[EVAL-15]** *(OPEN)* In what order does create evaluator run its checks, and which error is raised when several fail? Its only decided check is the provider check of [SVC-14] ([EVAL-26]). The checks on the options are open in [EVAL-27] and [EVAL-28]. Proposed: the shape of the options first, then the provider registrations ([EVAL-27], [SVC-14]), then `E_PROVIDER_MISSING`, so that a provider registered under a misspelt level name is reported as that mistake and not only as a missing provider.

**[EVAL-16]** Resolve, and its synchronous variant, MUST run these steps in this order:

1. Context validation, in the steps and order of [CTX-9]. It completes before any lookup or provider call ([CTX-10], ADR 0007 §5). Its codes are given by [EVAL-30] to [EVAL-33].
2. Key lookup. An undeclared key raises `E_UNKNOWN_KEY` ([EVAL-34]).
3. Synchronous variant only: whether a provider call is needed under [SVC-42]. If one is needed, the variant raises `E_PROVIDER_REQUIRED` ([EVAL-29], [SVC-42]).
4. Resolve only: the provider calls of [SVC-17] to [SVC-19]. Their outcomes are governed by [SVC-23] to [SVC-37] and raise no evaluation error ([EVAL-39]).
5. Combination and provenance (`MERGE`, `PROV`). This step raises no error.

**[EVAL-17]** Decide, and its synchronous variant, MUST run these steps in this order:

1. Context validation, as in step 1 of [EVAL-16].
2. Feature lookup. An undeclared feature raises `E_UNKNOWN_FEATURE` ([EVAL-35]).
3. FactSet validation against the feature's decided entity, as [FACT-22] to [FACT-41] define. A failure raises `E_FACTS_INVALID` ([EVAL-36], [FACT-42]). This step needs the decided entity, so it follows feature lookup.
4. Binding selection ([BIND-16], [BIND-17]), which fixes the selected policy ([BIND-21]).
5. The missing-fact precheck on the selected policy ([RULE-40] to [RULE-45]).
6. Rule evaluation under the `FIRST` hit policy, or the policy default ([RULE-46] to [RULE-51]).

Steps 4 to 6 raise no error. A missing fact is not an error ([FACT-21], [RULE-42]).

**[EVAL-18]** When several checks of [EVAL-16] or [EVAL-17] would fail in one call, the operation MUST raise the error of the first failing step. Within step 1, the first failing step of [CTX-9] decides. This clause settles, for this section, the step-order part of [CTX-11] (ADR 0007 §5 orders the steps and puts context validation before any lookup) and the precedence that [FACT-44] states: a context error comes before a FactSet error, and a FactSet error is raised even when the missing-fact precheck would also apply, because a caller error is never turned into an effect (ADR 0009 §4, ADR 0010 §2).

> Example (non-normative): `resolve` is called with the context `{ levels: { warehouse: north } }`, where `warehouse` is not a declared level, and with an undeclared key. It raises `E_UNKNOWN_SCOPE`, not `E_UNKNOWN_KEY`, because context validation runs first.
>
> Example (non-normative): `decide` is called with an undeclared feature and a FactSet that also holds an undeclared fact. It raises `E_UNKNOWN_FEATURE`. The FactSet is never checked, because the feature lookup failed first.
>
> Example (non-normative): `decide` is called with a FactSet that gives `"true"` for a `boolean` fact and omits another fact that the selected policy needs. It raises `E_FACTS_INVALID`. It does not return the feature's `if_fact_missing` value.

**[EVAL-19]** *(OPEN)* Which code is raised when one context fails two checks of the same [CTX-9] step that have different codes? Only step 2 has two codes: naming the root level ([CTX-3], `E_CONTEXT_INVALID`) and naming an undeclared scope ([CTX-4], `E_UNKNOWN_SCOPE`). A declared name in the wrong map is `E_UNKNOWN_SCOPE` ([CTX-5]). This is the within-step part of [CTX-11], which owns the question. The answer must not depend on the order of map entries. Proposed: `E_CONTEXT_INVALID` wins, because naming the root is a contract violation, not a misspelt name.

**[EVAL-20]** After a check fails, an operation MUST NOT run a later check, MUST NOT call a provider, and MUST NOT raise a second error.

**[EVAL-21]** An operation MUST NOT raise an error because no binding applies. A compiled feature always has a selected binding ([BIND-13], ADR 0007 §1), so this specification defines no such error.

## 11.3 Evaluation errors

**[EVAL-22]** An operation that detects a caller error or an integrity failure MUST raise an error that carries its stable code. The code is normative. The error message is not.

**[EVAL-23]** An operation MUST NOT return a caller error or an integrity failure as a result, an effect, a reason, a contribution or a skipped scope ([PROV-9]).

**[EVAL-24]** `E_BUNDLE_INTEGRITY` MUST be raised only by load bundle, for the failures of [BUNDLE-111], [BUNDLE-112] and [BUNDLE-113]. Whether it also covers malformed, non-UTF-8, non-canonical or incomplete input, and content that breaks the structure of section 10, is open in [BUNDLE-116] and [BUNDLE-118] to [BUNDLE-136].

**[EVAL-25]** `E_BUNDLE_UNSUPPORTED` MUST be raised only by load bundle, for the failure of [BUNDLE-115]. Which versions an evaluator supports is open in [BUNDLE-137].

**[EVAL-26]** `E_PROVIDER_MISSING` MUST be raised only by create evaluator, for the failure of [SVC-14].

**[EVAL-27]** *(OPEN)* What does create evaluator raise when the options register a provider for a level that the bundle does not declare? [SVC-14] refers here for the related cases of a provider for a level that is not service-sourced and of two providers for one level, and requirements §6.2 has no code for any of them. Proposed: decide all three cases together in this clause, rejecting them at creation with one new code added by ADR, because ignoring them would hide a deployment mistake.

**[EVAL-28]** *(OPEN)* Which evaluator options are required, and how is an invalid or absent option reported? Open cases: absent options, a provider with no timeout, a timeout that is zero or negative, and an absent listener. ADR 0009 §3 leaves timeout defaults to the runtime integration, and requirements §6.2 has no code for an invalid option.

**[EVAL-29]** `E_PROVIDER_REQUIRED` MUST be raised only by the synchronous variant of resolve, at step 3 of [EVAL-16], under [SVC-42]. It is raised instead of the provider call.

**[EVAL-30]** `E_CONTEXT_INVALID` MUST be raised by resolve, decide and their synchronous variants for the failures of [CTX-1], [CTX-3] and [CTX-6]. A runtime node ID that is not in NFC is not such a failure ([CTX-7]).

**[EVAL-31]** A context argument that is present but is not a map, for example a list or a string, MUST raise `E_CONTEXT_INVALID`, because it is a malformed context (ADR 0009 §4).

**[EVAL-32]** `E_UNKNOWN_SCOPE` MUST be raised by resolve, decide and their synchronous variants for a context name that fails [CTX-4] and is not the root level. A context that names the root level raises `E_CONTEXT_INVALID` under [CTX-3] and [EVAL-30] (ADR 0007 §1). A declared name in the wrong map is such a failure ([CTX-5]).

**[EVAL-33]** `E_UNKNOWN_NODE` MUST be raised by resolve, decide and their synchronous variants for the failure of [CTX-12]. Under `on_unknown_node: ignore` no error is raised, and the scope is skipped as [CTX-14] to [CTX-16] define.

**[EVAL-34]** `E_UNKNOWN_KEY` MUST be raised by resolve and its synchronous variant when the key name is not declared in the bundle.

**[EVAL-35]** `E_UNKNOWN_FEATURE` MUST be raised by decide and its synchronous variant when the feature name is not declared in the bundle.

**[EVAL-36]** `E_FACTS_INVALID` MUST be raised by decide and its synchronous variant for the FactSet failures of [FACT-22] to [FACT-41], and for a FactSet argument that is present but is not a map, which is a malformed FactSet (ADR 0009 §4). A fact whose value is `null` is missing, not invalid, and never raises this code ([FACT-16], [FACT-19], [FACT-21]).

**[EVAL-37]** *(OPEN)* How is an absent or wrong-typed argument reported where no clause above gives a code? Open cases: an absent context; a context without `levels` or `axes` ([CTX-2]); an absent FactSet, or one without `facts` or `children` ([FACT-24]); a key or feature argument that is absent, not a string, or empty; and a bundle argument that load bundle did not return. Proposed: a key or feature argument that is not a non-empty string raises `E_UNKNOWN_KEY` or `E_UNKNOWN_FEATURE`, because no declared name matches it; an absent context or FactSet follows the answers to [CTX-2] and [FACT-24]; a bundle argument that load bundle did not return is outside the contract and has no vector.

**[EVAL-38]** The evaluation operations MUST raise only the ten codes of requirements §6.2, whose evaluation triggers [EVAL-24] to [EVAL-36] define. `E_UNKNOWN_SCOPE`, `E_UNKNOWN_NODE` and `E_UNKNOWN_KEY` are also compiler codes (requirements §6.1), reported for sources under the clause areas that own them. This section defines only their evaluation triggers.

**[EVAL-39]** A source failure ([SVC-23], [SVC-29], [SVC-34]) MUST NOT raise an evaluation error. It is recorded in the provenance of the result ([PROV-8], [PROV-39], [PROV-40]).

**[EVAL-40]** A key that a provider leaves out ([SVC-36]), and a key that a provider returns but the bundle does not declare ([SVC-27]), MUST NOT raise an evaluation error. Neither is a source failure.

## 11.4 Listener events

**[EVAL-41]** When a listener is configured, resolve MUST report to it the events that [SVC-43] to [SVC-46] define for the provider calls it makes.

**[EVAL-42]** Decide and the synchronous variants of resolve and decide MUST NOT report a provider event, because they call no provider ([SVC-22], [SVC-42]).

**[EVAL-43]** *(OPEN)* Requirements §7.1 and ADR 0012 §4 name bundle loads among the listener events. Load bundle, however, takes no listener and runs before any evaluator exists. How is a load event delivered? Options: a listener argument on load bundle, or an event that create evaluator emits for the bundle it receives.

**[EVAL-44]** *(OPEN)* Is each listener event delivered before the operation that caused it returns, and may listener calls from concurrent operations overlap? What is the payload of a provider event, how often does one occur, and how is a listener that fails treated? Section 6 refers to this clause for all of these. Proposed: events are delivered before the operation returns, with one event for each affected key in each call, and a listener that fails changes no result and raises no error, which keeps [EVAL-51] true.

**[EVAL-45]** *(OPEN)* Should raised errors, and the skipped scopes recorded in a result (`skippedScopes`), also be reported to the listener? The sources list only provider events and bundle loads.

## 11.5 Immutability and concurrency

**[EVAL-46]** An operation MUST NOT modify a loaded bundle (requirements §13).

> Implementation note: the JavaScript loader freezes the loaded bundle deeply. The Java loader uses immutable types, so a loaded bundle is safe to share between threads (requirements §13).

**[EVAL-47]** A value that an operation returns MUST NOT share mutable state with a loaded bundle or an evaluator.

**[EVAL-48]** An operation MUST NOT change the bundle, providers, timeouts or listener of an evaluator. They are fixed when it is created.

**[EVAL-49]** An operation MUST NOT modify its inputs: the context, the FactSet, or any value that a provider returns. A decision is a pure function of its bundle, context and facts (ADR 0007 §6).

**[EVAL-50]** A change that a caller makes to a returned result MUST NOT change the result of any later call.

**[EVAL-51]** Given the same bundle, context, key or feature, FactSet, and outcome of each provider call, an operation MUST return the same result or raise the same error, whatever other calls ran before it or run at the same time on the same evaluator or bundle. The outcome of a provider call is the map it returns, or its failure, where a call that exceeds its timeout is a failure ([SVC-39]). The order and concurrency of provider calls do not change a result ([SVC-21]).

**[EVAL-52]** When several calls run at the same time on one evaluator, or on evaluators that share one loaded bundle, each call MUST return the result, or raise the error, that it returns or raises when it runs alone with the same provider outcomes.

## Open points

- [EVAL-9]: whether every implementation must offer the synchronous variants of resolve and decide.
- [EVAL-14]: the order of load bundle checks, and the code when several fail (referenced by [BUNDLE-117]).
- [EVAL-15]: the order of create evaluator checks, which depends on [EVAL-27] and [EVAL-28].
- [EVAL-19]: the code when one context names the root level and an undeclared scope in the same [CTX-9] step (owned by [CTX-11]).
- [EVAL-27]: the code for a provider registered for an undeclared level, a level that is not service-sourced, or twice for one level ([SVC-14]).
- [EVAL-28]: which evaluator options are required, and how absent or invalid options are reported.
- [EVAL-37]: the code, if any, for absent or wrong-typed arguments not covered by other clauses.
- [EVAL-43]: how bundle-load events reach a listener.
- [EVAL-44]: delivery timing, overlap, payload and granularity of listener events, and a listener that fails.
- [EVAL-45]: whether raised errors and skipped scopes are reported to the listener.
