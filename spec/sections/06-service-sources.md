# 6. Service-sourced levels and providers

This section defines how a service-sourced level gets its values at evaluation time. It covers which keys need a source-failure behaviour, what `skip` and `deny` do, the provider contract, how each provider outcome and each entry of a provider result are treated, runtime validation of provider values, timeouts, when a resolve needs a provider call, and which provider outcomes are reported to the evaluation listener. It does not define which scopes exist or how nodes are admitted (section 2, `SCOPE` and `CTX`), value types and string comparison (section 4, `TYPE` and `NORM`), keys, merge strategies and kinds (section 5, `KEY`, `MERGE` and `KIND`), bindings (section 7, `BIND`), the provenance entry format (section 9, `PROV`), or the evaluation operations, their check order, their error codes and the listener contract (section 11, `EVAL`). Service-sourced axes do not exist in this version (see [SCOPE-26]).

The terms *source-failure behaviour*, *source failure*, *deny value*, *provider result*, *entry*, *evaluation call*, *keys being resolved*, *key being resolved at a level* and *admitted* are defined in `spec.md` §Terminology.

## 6.1 Source-failure declarations

**[SVC-1]** Every key that is settable at one or more service-sourced levels MUST have a source-failure behaviour in effect, declared on the key or obtained through its kind ([KIND-9]).

**[SVC-2]** A compiler MUST report `E_MISSING_SOURCE_FAILURE` for a key that is settable at a service-sourced level and has no source-failure behaviour in effect. No default behaviour applies.

**[SVC-3]** A source-failure behaviour MUST be either `skip` or `deny`.

**[SVC-4]** *(OPEN)* Which code a compiler reports when a declared `on_source_failure` is neither `skip` nor `deny`. For a declaration in a kind, the candidate is `E_KIND_DEFINITION` (requirements §6.1). For a declaration on a key, no listed code fits; the same gap exists for other malformed key members ([KEY-5]).

**[SVC-5]** *(OPEN)* Which declaration applies when a key and its kind both declare `on_source_failure` with different values. The answer depends on the shape of the kind's member ([KIND-11]).

**[SVC-6]** An evaluator MUST NOT record a source failure for a level that is not service-sourced.

**[SVC-7]** *(OPEN)* Whether a key-level `on_source_failure` on a key that is settable at no service-sourced level is an error. A canonical bundle omits `onSourceFailure` for such a key ([BUNDLE-59]). The answer must agree with whether [SVC-11] and [SVC-12] apply to such a key; ADR 0008 §4 states the `deny` restrictions without that condition.

## 6.2 Skip and deny

**[SVC-8]** Under `skip`, a level whose outcome for a key is a source failure MUST contribute no value to that key. Resolution continues as if the level had set no value for that key.

**[SVC-9]** Under `deny`, a level whose outcome for a key is a source failure MUST contribute the key's deny value, which depends on the key's strategy (ADR 0008 §4):
- `false` for `all`;
- the empty set `[]` for `intersect`;
- the `min` member of the key's `integer` type declaration ([TYPE-13]) for `min`.

**[SVC-10]** An evaluator MUST NOT present a deny value as a value that a provider returned. Its provenance entry is defined by [PROV-29] and [PROV-41].

**[SVC-11]** A compiler MUST report `E_DENY_NOT_NARROWING` for a key that has `deny` in effect and a strategy that is not narrowing. The narrowing strategies are `all`, `intersect` and `min` ([MERGE-2]).

**[SVC-12]** A compiler MUST report `E_DENY_BOUND_REQUIRED` for a key that has `deny` in effect, has the strategy `min`, and whose `integer` type declaration has no `min` member.

## 6.3 Provider contract

**[SVC-13]** An evaluator MUST take the values of a service-sourced level only from that level's provider. A canonical bundle holds no values for such a level ([BUNDLE-67]). Whether a source may give one is [SCOPE-47].

**[SVC-14]** An evaluator MUST have exactly one provider for each service-sourced level declared in its bundle, including a level at which no key is settable. Create evaluator checks this ([EVAL-4], [EVAL-26]). Providers registered for other levels, or twice for one level, are [EVAL-27].

**[SVC-15]** An evaluator MUST pass a provider the node ID that the context supplies for its level, exactly as the context supplied it.

**[SVC-16]** An evaluator MUST match the key name of each entry against the declared key names by exact code-point equality, without normalisation ([NORM-3], [NORM-4]).

**[SVC-17]** An evaluator MUST NOT call the provider for a service-sourced level unless both of the following hold: the context supplies an admitted node for that level, and at least one key being resolved is settable at that level. A skipped scope is not admitted, so it triggers no call and no source failure ([CTX-15]). No call starts before context validation completes ([CTX-10]).

**[SVC-18]** An evaluator MUST call the provider for a service-sourced level whenever both conditions of [SVC-17] hold, even when another scope supplies the winning value. Provenance lists every scope that supplies a value or fails to ([PROV-20], [PROV-39], [PROV-40], ADR 0008 §6), so the call cannot be skipped.

**[SVC-19]** An evaluator MUST call the provider for a service-sourced level at most once per evaluation call.

**[SVC-20]** *(OPEN)* Whether one request that resolves several keys at once, such as the server's `/v1/resolve` with `keys[]` (requirements §9), is one evaluation call for [SVC-19], and so whether its keys are all keys being resolved.

**[SVC-21]** An evaluator MUST NOT let a result depend on the order or concurrency of provider calls (see also [EVAL-51]).

**[SVC-22]** Decide MUST NOT call any provider. Bindings cannot be placed at service-sourced levels ([FEAT-19], ADR 0007 §6), so a decision needs no provider values.

## 6.4 Provider outcomes

Each outcome applies to one provider call for one level in one evaluation call. The summary table at the end of this subsection is non-normative.

**[SVC-23]** If a provider throws, rejects, or times out, each key being resolved at that level MUST have a source failure with outcome `UNAVAILABLE` at that level. Its provenance entry is defined by [PROV-39].

**[SVC-24]** For each source failure, an evaluator MUST apply the key's source-failure behaviour under [SVC-8] or [SVC-9].

**[SVC-25]** *(OPEN)* Whether a provider result that is not a mapping, such as `null`, a list or a scalar, is handled under [SVC-23], so that each key being resolved at that level is `UNAVAILABLE`.

**[SVC-26]** An evaluator MUST give each entry of a provider result exactly one outcome: the outcome of the first of the following clauses that applies to it, in this order: [SVC-27], [SVC-28], [SVC-29], [SVC-34]. An entry to which none of them applies is valid, and its value is the level's contribution for its key ([MERGE-14]).

**[SVC-27]** An entry whose key the bundle does not declare MUST NOT affect any result, whatever its value, including `null`. It is not a source failure.

**[SVC-28]** *(OPEN)* How an evaluator treats an entry for a declared key that is not a key being resolved: whether the entry is validated, whether it can be a source failure, and whether it is reported to the listener.

**[SVC-29]** An entry for a key being resolved that is not settable at that level is a source failure for that key only, with outcome `INVALID` (ADR 0009 §2).

**[SVC-30]** *(OPEN)* What a source failure under [SVC-29] records and contributes. A scope where the key is not settable contributes no value ([MERGE-14]) and does not appear in contributions unless [PROV-44] decides otherwise ([PROV-43]). The key may also have no source-failure behaviour, because [SVC-1] requires one only for keys settable at a service-sourced level. Open: whether a failure entry appears, which behaviour applies, and whether a deny value can contribute at that scope.

**[SVC-31]** An evaluator MUST validate the value of each entry for a key being resolved at that level against the key's type declaration, under the rules of the `TYPE` area for values received at runtime ([TYPE-70], [TYPE-73], [TYPE-75]).

> Note (non-normative): under those rules an evaluator does not normalise strings ([NORM-3]), canonicalises decimal strings before comparing them ([NORM-15]), checks `iana_tz` values against the syntax only ([TYPE-37]), and rejects a set value with a repeated member or a member outside the universe ([TYPE-46], [TYPE-47]).

**[SVC-32]** For a key whose strategy is `deep_merge`, the validation of [SVC-31] MUST accept a partial object value, as [MERGE-11] allows for every value other than the starting value. A service-sourced level is never the root level, so a provider never supplies the starting value. How the object rules of `TYPE` apply to partial values is [MERGE-12].

**[SVC-33]** *(OPEN)* Whether `narrowing_only` ([KIND-6]) applies to provider values, so that a provider value equal to the strategy's identity, such as `true` under `all`, is `INVALID`.

**[SVC-34]** An entry whose value fails the validation of [SVC-31] is a source failure for its key only, with outcome `INVALID`. Its provenance entry is defined by [PROV-40].

**[SVC-35]** An entry for a key being resolved at that level whose value is `null` MUST fail the validation of [SVC-31] ([TYPE-76]). It is not treated as an omitted key.

**[SVC-36]** When a provider result has no entry for a key being resolved at that level, the level MUST set no value for that key. This is not a source failure, and the level makes no contribution for that key ([PROV-42]).

**[SVC-37]** An entry whose outcome is a source failure under [SVC-29] or [SVC-34] MUST NOT change the outcome for any other key.

| Provider outcome | Effect on the key | Provenance | Clauses |
| --- | --- | --- | --- |
| Throws, rejects, or times out | Source failure for each key being resolved at the level | `UNAVAILABLE` | [SVC-23], [SVC-39] |
| Result is not a mapping | Open | Open | [SVC-25] |
| Entry for an undeclared key | Ignored; reported as an unknown key | none | [SVC-27], [SVC-45] |
| Entry for a declared key not being resolved | Open | Open | [SVC-28] |
| Entry for a key being resolved, not settable at the level | Source failure for that key | `INVALID`; placement open | [SVC-29], [SVC-30] |
| Entry whose value fails validation, including `null` | Source failure for that key | `INVALID` | [SVC-34], [SVC-35] |
| No entry for a key being resolved at the level | None | none | [SVC-36] |

## 6.5 Timeouts

**[SVC-38]** Every evaluator MUST support a timeout for each provider. The timeout is supplied in the evaluator options ([EVAL-4]) and is not part of the bundle. A provider with no timeout, and a zero or negative timeout, are [EVAL-28].

**[SVC-39]** A provider call that has not returned when its timeout expires MUST be treated as unavailable under [SVC-23].

**[SVC-40]** An evaluator MUST NOT use a provider result that arrives after the call's timeout has expired.

**[SVC-41]** *(OPEN)* The unit of a timeout, and the interval it measures: from which point a provider call is timed, and at which point it counts as returned.

Default timeout values belong to the runtime integration, not to this specification (ADR 0009 §3).

## 6.6 Provider need and synchronous resolution

**[SVC-42]** Step 3 of [EVAL-16] MUST find that a provider call is needed if and only if the conditions of [SVC-17] hold for at least one service-sourced level. The synchronous variant of resolve then raises `E_PROVIDER_REQUIRED` instead of calling a provider ([EVAL-11], [EVAL-29]). Whether every implementation offers that variant is [EVAL-9].

> Implementation note: the requirements' API sketches (§7.1 and §7.2) name the JavaScript synchronous variant `resolveSync`, and give a synchronous Java provider interface, `LevelValueProvider`, that reports unavailability by throwing `SourceUnavailableException`. Under that sketch the exception is a provider failure under [SVC-23]. These names are not yet implemented.

## 6.7 Events reported to an evaluation listener

[EVAL-41] requires resolve to report provider events to a configured listener. The clauses below fix which provider outcomes those events report. Event timing, overlap, payload and granularity, and the treatment of a listener that fails, are open in [EVAL-44].

**[SVC-43]** The source failures that [EVAL-41] reports are exactly those under [SVC-23], [SVC-29] and [SVC-34].

**[SVC-44]** A timeout MUST be reported as a source failure under [SVC-23].

**[SVC-45]** The unknown keys that [EVAL-41] reports are exactly the entries under [SVC-27].

**[SVC-46]** An evaluator MUST NOT report a key under [SVC-36], which the provider result leaves out, as a source failure or an unknown key.

## Open points

- **[SVC-4]**: The code for an `on_source_failure` value that is neither `skip` nor `deny`. Recommendation: `E_KIND_DEFINITION` for a kind; for a key, the same code that [KEY-5] chooses for malformed key members, decided by one ADR for both.
- **[SVC-5]**: Which declaration applies when a key and its kind differ. Recommendation: the kind constrains its keys (requirements §4.3), so a key whose declaration conflicts with its kind's is `E_KIND_VIOLATION`, and a key cannot override its kind. Decide together with [KIND-11].
- **[SVC-7]**: A key-level declaration on a key with no service-sourced scope. Recommendation: accept it with no runtime effect, as [BUNDLE-25] already implies, so that kinds with `skip` keep working for file-only keys. Apply [SVC-11] and [SVC-12] only to keys settable at a service-sourced level, so a `deny` kind can be shared with file-only keys of any strategy. No lint rule covers this case, and [LINT-1] allows no other rule in v0.1, so a warning would need a new rule by ADR.
- **[SVC-20]**: Whether a multi-key request is one evaluation call. Recommendation: yes, so each level is called at most once per request and every requested key is a key being resolved.
- **[SVC-25]**: A provider result that is not a mapping. Recommendation: treat it as a provider failure under [SVC-23].
- **[SVC-28]**: An entry for a declared key that is not being resolved. Recommendation: do not validate it, give it no outcome, and do not report it. It cannot affect any result, and validating it would report failures for keys nobody asked for. Under this reading, [SVC-29] arises only for a request that resolves several keys ([SVC-20]).
- **[SVC-30]**: What a failure under [SVC-29] records. Recommendation: report it to the listener, contribute no value, and add no entry to the key's contributions, which resolves [PROV-44] as "no entry". This narrows the provenance column of ADR 0009 §2, so it needs an ADR.
- **[SVC-33]**: Whether `narrowing_only` applies to provider values. Recommendation: no. ADR 0008 §5 and [KIND-7] make it a static check on authored values, and an identity value from a provider is labelled `IGNORED` and cannot widen the result.
- **[SVC-41]**: Timeout unit and measured interval. Recommendation: milliseconds, measured from the start of the provider call until the provider result is available to the evaluator. Decide together with [EVAL-28].
