# 11. Evaluation operations and errors

This section defines the operations an evaluator offers: load bundle, create evaluator, resolve, decide, and the synchronous variants of resolve and decide (variants that return their result directly and never call a provider). It fixes the order of checks for each operation, which error is raised when several checks fail, the catalogue of evaluation error codes, the listener events tied to each operation, and the immutability, determinism and concurrency rules for loaded bundles and evaluators. It does not define what a valid context is (section 2, `CTX`), what a valid FactSet is (section 3, `FACT`), how providers, source failures and provider events behave (section 6, `SVC`), how values merge (section 5, `MERGE`), how bindings are selected and rules run (sections 7 and 8, `BIND` and `RULE`), what a result contains (section 9, `PROV`), or the byte format of a bundle and the loader checks (section 10, `BUNDLE` and `DET`). Those rules are cited here by clause ID and are not restated.

## 11.1 Operations

**[EVAL-1]** An implementation MUST offer the operations load bundle, create evaluator, resolve and decide.

**[EVAL-2]** Load bundle MUST take the bytes of one bundle, and MUST either return a loaded bundle or raise `E_BUNDLE_INTEGRITY` or `E_BUNDLE_UNSUPPORTED` ([EVAL-24], [EVAL-25]).

**[EVAL-3]** Load bundle MUST be synchronous.

> Implementation note: ADR 0012 §3 keeps the JavaScript `loadBundle` synchronous by bundling its own SHA-256. The JavaScript loader also accepts a string. It encodes the string as UTF-8 before any check, so the checks of section 10 apply to the encoded bytes.

**[EVAL-4]** Create evaluator MUST take a loaded bundle and evaluator options, and MUST either return an evaluator or raise `E_PROVIDER_MISSING` ([EVAL-26]) or `E_EVALUATOR_OPTIONS` ([EVAL-27], [EVAL-28]). The options supply one provider for each service-sourced level ([SVC-14]), a timeout for each provider ([SVC-38]), and the listener ([EVAL-41]).

**[EVAL-5]** Create evaluator MUST be synchronous.

**[EVAL-6]** Create evaluator MUST NOT call a provider. Providers are called only as [SVC-17] allows.

**[EVAL-7]** `resolve(context, key)` MUST either return a `Resolved` result ([PROV-10]) or raise an error of [EVAL-16]. It calls providers only as [SVC-17] to [SVC-19] define.

**[EVAL-8]** `decide(context, feature, factset)` MUST either return a `Decision` result ([PROV-62]) or raise an error of [EVAL-17]. It calls no provider ([SVC-22]).

**[EVAL-9]** Every implementation MUST offer both a synchronous variant of resolve, which never calls a provider and raises `E_PROVIDER_REQUIRED` where [SVC-42] requires a call, and the provider-capable resolve of [EVAL-7]. Decide never calls a provider ([SVC-22]), so every implementation MUST offer decide in a form that returns its result directly. That form is the synchronous variant of decide that [EVAL-12] describes. The error codes are the same in every variant.

**[EVAL-10]** For an input that needs no provider call under [SVC-42], the synchronous variant of resolve MUST return the same result, or raise the same error, as resolve.

**[EVAL-11]** For an input that needs a provider call under [SVC-42], the synchronous variant of resolve MUST run steps 1 to 3 of [EVAL-16] and raise the error of the first failing step. It raises `E_PROVIDER_REQUIRED` at step 3 ([EVAL-29]).

**[EVAL-12]** For every input, the synchronous variant of decide MUST return the same result, or raise the same error, as decide.

> Implementation note: the JavaScript evaluator exposes the synchronous variants as `resolveSync` and `decideSync`. The Java evaluator raises a `TierwiseException` whose `code()` is the evaluation error code, and the JavaScript evaluator throws a `TierwiseError` that exposes the code (ADR 0009 §4).

**[EVAL-13]** An operation MUST NOT have a parameter that selects one scope or one node for which it returns a value. A scope's own value appears only as a contribution inside a combined `Resolved` result ([PROV-20], [PROV-28]), as requirements §4.8 and §7 together allow.

## 11.2 Check order

**[EVAL-14]** Load bundle MUST apply the checks of section 10 in this order, and MUST raise the code of the first check that fails ([EVAL-20]):

1. Strict parsing and the envelope: [BUNDLE-111], [BUNDLE-112] and [BUNDLE-116]. A failure raises `E_BUNDLE_INTEGRITY`.
2. Versions: [BUNDLE-115], with the supported values of [EVAL-25]. A failure raises `E_BUNDLE_UNSUPPORTED`.
3. Digest: [BUNDLE-113]. A failure raises `E_BUNDLE_INTEGRITY`.
4. Structure: [BUNDLE-118] to [BUNDLE-136]. A failure raises `E_BUNDLE_INTEGRITY`.

A bundle in a future format therefore reports `E_BUNDLE_UNSUPPORTED`, not a digest mismatch. [BUNDLE-117] takes this order. [EVAL-56] covers a version member that is missing or is not a string.

**[EVAL-15]** Create evaluator MUST apply these checks in this order, and MUST raise the code of the first check that fails:

1. The options, including each timeout ([EVAL-28]). A failure raises `E_EVALUATOR_OPTIONS`.
2. The provider registrations ([EVAL-27]). A failure raises `E_EVALUATOR_OPTIONS`.
3. The provider check of [SVC-14] ([EVAL-26]). A failure raises `E_PROVIDER_MISSING`.

A provider registered under a misspelt level name is therefore reported as `E_EVALUATOR_OPTIONS`, not only as a missing provider.

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

**[EVAL-19]** When one context, in one step of [CTX-9], fails the root-level check of [CTX-3] and also a check that raises `E_UNKNOWN_SCOPE` ([CTX-4], [CTX-5]), the operation MUST raise `E_CONTEXT_INVALID`, whatever the order of the map entries. Naming the root level is a contract violation, not a misspelt name. [CTX-11] refers to this clause for that case.

**[EVAL-20]** After a check fails, an operation MUST NOT run a later check, MUST NOT call a provider, and MUST NOT raise a second error.

**[EVAL-21]** An operation MUST NOT raise an error because no binding applies. A compiled feature always has a selected binding ([BIND-13], ADR 0007 §1), so this specification defines no such error.

**[EVAL-56]** When `bundleFormatVersion` or `specVersion` is missing from `content`, load bundle MUST raise `E_BUNDLE_INTEGRITY`. When either is present with a value other than exactly the string `"1"` or `"0.1"` respectively, whatever its JSON type, load bundle MUST raise `E_BUNDLE_UNSUPPORTED` ([BUNDLE-137]) (ADR 0014). The two members are checked in turn: `bundleFormatVersion` completely first (missing, then unsupported), then `specVersion`. A bundle in an unsupported format therefore reports `E_BUNDLE_UNSUPPORTED` even if `specVersion` is missing (ADR 0018).

## 11.3 Evaluation errors

**[EVAL-22]** An operation that detects a caller error or an integrity failure MUST raise an error that carries its stable code. The code is normative. The error message is not.

**[EVAL-23]** An operation MUST NOT return a caller error or an integrity failure as a result, an effect, a reason, a contribution or a skipped scope ([PROV-9]).

**[EVAL-24]** `E_BUNDLE_INTEGRITY` MUST be raised only by load bundle, for the failures of [BUNDLE-111], [BUNDLE-112], [BUNDLE-113], [BUNDLE-116] and [BUNDLE-118] to [BUNDLE-136].

**[EVAL-25]** `E_BUNDLE_UNSUPPORTED` MUST be raised only by load bundle, for the failure of [BUNDLE-115]. A bundle is supported only when its `bundleFormatVersion` is the string `"1"` and its `specVersion` is the string `"0.1"`, compared as exact strings ([BUNDLE-16], [BUNDLE-17], [BUNDLE-137]). Any other string is unsupported. [EVAL-56] covers a member that is missing or is not a string.

**[EVAL-26]** `E_PROVIDER_MISSING` MUST be raised only by create evaluator, for the failure of [SVC-14].

**[EVAL-27]** Create evaluator MUST raise `E_EVALUATOR_OPTIONS` when the options register a provider for a level that the bundle does not declare, for a declared level that is not service-sourced, or twice for one level ([SVC-14]). Only create evaluator raises this code.

**[EVAL-28]** An omitted options argument MUST count as empty options, with no provider and no listener. The listener is optional. Options that are present but are not a map MUST raise `E_EVALUATOR_OPTIONS`. A timeout is valid only when it is a positive integer number of milliseconds ([SVC-38], [SVC-41]). A timeout that is not valid MUST raise `E_EVALUATOR_OPTIONS`. A provider registered with no timeout is handled by [SVC-48].

**[EVAL-29]** `E_PROVIDER_REQUIRED` MUST be raised only by the synchronous variant of resolve, at step 3 of [EVAL-16], under [SVC-42]. It is raised instead of the provider call.

**[EVAL-30]** `E_CONTEXT_INVALID` MUST be raised by resolve, decide and their synchronous variants for the failures of [CTX-1], [CTX-2], [CTX-3] and [CTX-6]. A runtime node ID that is not in NFC is not such a failure ([CTX-7]).

**[EVAL-31]** A context argument that is present but is not a map, for example a list or a string, MUST raise `E_CONTEXT_INVALID`, because it is a malformed context (ADR 0009 §4).

**[EVAL-32]** `E_UNKNOWN_SCOPE` MUST be raised by resolve, decide and their synchronous variants for a context name that fails [CTX-4] and is not the root level. A context that names the root level raises `E_CONTEXT_INVALID` under [CTX-3] and [EVAL-30] (ADR 0007 §1). A declared name in the wrong map is such a failure ([CTX-5]).

**[EVAL-33]** `E_UNKNOWN_NODE` MUST be raised by resolve, decide and their synchronous variants for the failure of [CTX-12]. Under `on_unknown_node: ignore` no error is raised, and the scope is skipped as [CTX-14] to [CTX-16] define.

**[EVAL-34]** `E_UNKNOWN_KEY` MUST be raised by resolve and its synchronous variant when the key name is not declared in the bundle.

**[EVAL-35]** `E_UNKNOWN_FEATURE` MUST be raised by decide and its synchronous variant when the feature name is not declared in the bundle.

**[EVAL-36]** `E_FACTS_INVALID` MUST be raised by decide and its synchronous variant for the FactSet failures of [FACT-22] to [FACT-41], and for a FactSet argument that is present but is not a map, which is a malformed FactSet (ADR 0009 §4). A fact whose value is `null` is missing, not invalid, and never raises this code ([FACT-16], [FACT-19], [FACT-21]).

**[EVAL-37]** A key or feature argument that is omitted, is not a string, or is an empty string MUST raise `E_UNKNOWN_KEY` in resolve, at the lookup step of [EVAL-16], or `E_UNKNOWN_FEATURE` in decide, at the lookup step of [EVAL-17], because no declared name matches it.

**[EVAL-38]** The evaluation operations MUST raise only the eleven codes of requirements §6.2: `E_BUNDLE_INTEGRITY`, `E_BUNDLE_UNSUPPORTED`, `E_PROVIDER_MISSING`, `E_PROVIDER_REQUIRED`, `E_CONTEXT_INVALID`, `E_UNKNOWN_SCOPE`, `E_UNKNOWN_NODE`, `E_UNKNOWN_KEY`, `E_UNKNOWN_FEATURE`, `E_FACTS_INVALID` and `E_EVALUATOR_OPTIONS`. The triggers of [EVAL-24] to [EVAL-37] define when each code is raised. `E_UNKNOWN_SCOPE`, `E_UNKNOWN_NODE`, `E_UNKNOWN_KEY` and `E_UNKNOWN_FEATURE` are also engine error codes (requirements §6.1), reported for sources under the clause areas that own them. This section defines only their evaluation triggers.

**[EVAL-39]** A source failure ([SVC-23], [SVC-29], [SVC-34]) MUST NOT raise an evaluation error. It is recorded in the provenance of the result ([PROV-8], [PROV-39], [PROV-40]).

**[EVAL-40]** A key that a provider leaves out ([SVC-36]), and a key that a provider returns but the bundle does not declare ([SVC-27]), MUST NOT raise an evaluation error. Neither is a source failure.

**[EVAL-54]** An omitted context argument MUST be treated as an empty context, and an omitted FactSet argument as an empty FactSet, as [CTX-2] and [FACT-24] define for a context without maps and a FactSet without `facts` or `children`.

**[EVAL-55]** An operation's bundle argument MUST be a loaded bundle that [EVAL-2] returned. Any other value is outside the contract of this section, and no clause defines a code for it.

## 11.4 Listener events

**[EVAL-41]** When a listener is configured, resolve MUST report to it the events that [SVC-43] to [SVC-47] define for the provider calls it makes.

**[EVAL-42]** Decide and the synchronous variants of resolve and decide MUST NOT report a provider event, because they call no provider ([SVC-22], [SVC-42]).

**[EVAL-43]** Create evaluator MUST emit one load event to the listener, when one is configured, for the bundle it receives. It emits this event only when it returns an evaluator, and before it returns ([EVAL-44]). Load bundle takes no listener and emits no event.

**[EVAL-44]** An operation MUST deliver each listener event before it returns. A call MUST report one event for each affected key, meaning each key that one of the events of [SVC-43] to [SVC-47] concerns. A listener that throws is ignored: it changes no result and raises no error. This keeps [EVAL-51] true.

**[EVAL-45]** An operation MUST NOT report a raised error, or a skipped scope recorded in a result (`skippedScopes`), to the listener. The events of [EVAL-41] and [EVAL-43] are the only listener events.

**[EVAL-53]** An evaluator MUST NOT serialise listener calls; a listener MUST tolerate concurrent calls from concurrent operations. Each event MUST carry `type` and `bundleVersion`, and, where they apply, `level`, `node` and `key`; an implementation MAY add members. Conformance vectors do not assert listener events (ADR 0014).

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

- (none)
