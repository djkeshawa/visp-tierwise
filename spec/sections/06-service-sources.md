# 6. Service-sourced levels and providers

This section defines how a service-sourced level gets its values at evaluation time. It covers which keys need a source-failure behaviour, what `skip` and `deny` do, the provider contract, how each provider outcome and each entry of a provider result are treated, runtime validation of provider values, timeouts, when a resolve needs a provider call, and which provider outcomes are reported to the evaluation listener. It does not define which scopes exist or how nodes are admitted (section 2, `SCOPE` and `CTX`), value types and string comparison (section 4, `TYPE` and `NORM`), keys, merge strategies and kinds (section 5, `KEY`, `MERGE` and `KIND`), bindings (section 7, `BIND`), the provenance entry format (section 9, `PROV`), or the evaluation operations, their check order, their error codes and the listener contract (section 11, `EVAL`). Service-sourced axes do not exist in this version (see [SCOPE-26]).

The terms *source-failure behaviour*, *source failure*, *deny value*, *provider result*, *entry*, *evaluation call*, *keys being resolved*, *key being resolved at a level* and *admitted* are defined in `spec.md` §Terminology.

## 6.1 Source-failure declarations

**[SVC-1]** Every key that is settable at one or more service-sourced levels MUST have a source-failure behaviour in effect, declared on the key or obtained through its kind ([KIND-9]).

**[SVC-2]** A compiler MUST report `E_MISSING_SOURCE_FAILURE` for a key that is settable at a service-sourced level and has no source-failure behaviour in effect. No default behaviour applies.

**[SVC-3]** A source-failure behaviour MUST be either `skip` or `deny`.

**[SVC-4]** A compiler MUST report `E_KIND_DEFINITION` for a kind whose `on_source_failure` is neither `skip` nor `deny`. A compiler MUST report `E_INVALID_DECLARATION` for a key whose `on_source_failure` is neither `skip` nor `deny` ([KEY-5]). Both rules apply whether or not the key is settable at a service-sourced level ([SVC-7]).

**[SVC-5]** When a key and its kind both declare `on_source_failure`, the two values MUST be equal, and that common value applies. A key cannot override its kind. A compiler MUST report `E_KIND_VIOLATION` for a key whose `on_source_failure` differs from its kind's ([KIND-11], [KIND-9]).

**[SVC-6]** An evaluator MUST NOT record a source failure for a level that is not service-sourced.

**[SVC-7]** A key-level `on_source_failure` on a key that is settable at no service-sourced level is accepted and has no runtime effect. Its value is still checked under [SVC-3] and [SVC-4]. A canonical bundle omits `onSourceFailure` for such a key ([BUNDLE-59]). [SVC-11] and [SVC-12] apply only to keys settable at a service-sourced level, so a `deny` kind can be shared with file-only keys of any strategy.

## 6.2 Skip and deny

**[SVC-8]** Under `skip`, a level whose outcome for a key is a source failure MUST contribute no value to that key. Resolution continues as if the level had set no value for that key.

**[SVC-9]** Under `deny`, a level whose outcome for a key is a source failure MUST contribute the key's deny value, which depends on the key's strategy (ADR 0008 §4):
- `false` for `all`;
- the empty set `[]` for `intersect`;
- the `min` member of the key's `integer` type declaration ([TYPE-13]) for `min`.

**[SVC-10]** An evaluator MUST NOT present a deny value as a value that a provider returned. Its provenance entry is defined by [PROV-29] and [PROV-41].

**[SVC-11]** A compiler MUST report `E_DENY_NOT_NARROWING` for a key that is settable at one or more service-sourced levels, has `deny` in effect, and has a strategy that is not narrowing. The narrowing strategies are `all`, `intersect` and `min` ([MERGE-2]).

**[SVC-12]** A compiler MUST report `E_DENY_BOUND_REQUIRED` for a key that is settable at one or more service-sourced levels, has `deny` in effect, has the strategy `min`, and whose `integer` type declaration has no `min` member.

## 6.3 Provider contract

**[SVC-13]** An evaluator MUST take the values of a service-sourced level only from that level's provider. A canonical bundle holds no values for such a level ([BUNDLE-67]). Whether a source may give one is [SCOPE-47].

**[SVC-14]** An evaluator MUST have exactly one provider for each service-sourced level declared in its bundle, including a level at which no key is settable. Create evaluator checks this ([EVAL-4], [EVAL-26]). A provider registered for a level that the bundle does not declare, for a level that is not service-sourced, or twice for one level MUST make create evaluator raise `E_EVALUATOR_OPTIONS` ([EVAL-27]). That error takes precedence over `E_PROVIDER_MISSING` ([EVAL-15]).

**[SVC-15]** An evaluator MUST pass a provider the node ID that the context supplies for its level, exactly as the context supplied it.

**[SVC-16]** An evaluator MUST match the key name of each entry against the declared key names by exact code-point equality, without normalisation ([NORM-3], [NORM-4]).

**[SVC-17]** An evaluator MUST NOT call the provider for a service-sourced level unless both of the following hold: the context supplies an admitted node for that level, and at least one key being resolved is settable at that level. A skipped scope is not admitted, so it triggers no call and no source failure ([CTX-15]). No call starts before context validation completes ([CTX-10]).

**[SVC-18]** An evaluator MUST call the provider for a service-sourced level whenever both conditions of [SVC-17] hold, even when another scope supplies the winning value. Provenance lists every scope that supplies a value or fails to ([PROV-20], [PROV-39], [PROV-40], ADR 0008 §6), so the call cannot be skipped.

**[SVC-19]** An evaluator MUST call the provider for a service-sourced level at most once per evaluation call.

**[SVC-20]** A request that resolves several keys at once, such as the server's `/v1/resolve` with `keys[]` (requirements §9), is one evaluation call. Every key that it requests is a key being resolved, so each service-sourced level is called at most once for the whole request ([SVC-19]).

**[SVC-21]** An evaluator MUST NOT let a result depend on the order or concurrency of provider calls (see also [EVAL-51]).

**[SVC-22]** Decide MUST NOT call any provider. Bindings cannot be placed at service-sourced levels ([FEAT-19], ADR 0007 §6), so a decision needs no provider values.

## 6.4 Provider outcomes

Each outcome applies to one provider call for one level in one evaluation call. The summary table at the end of this subsection is non-normative.

**[SVC-23]** If a provider throws, rejects, or times out, each key being resolved at that level MUST have a source failure with outcome `UNAVAILABLE` at that level. Its provenance entry is defined by [PROV-39].

**[SVC-24]** For each source failure, an evaluator MUST apply the key's source-failure behaviour under [SVC-8] or [SVC-9].

**[SVC-25]** A provider result that is not a mapping, such as `null`, a list or a scalar, is a provider failure. [SVC-23] then applies, so each key being resolved at that level MUST have a source failure with outcome `UNAVAILABLE` at that level.

**[SVC-26]** An evaluator MUST classify each entry of a provider result by the first of the following clauses that applies to it, in this order: [SVC-27], [SVC-28], [SVC-29], [SVC-34]. An entry to which [SVC-27], [SVC-28] or [SVC-29] applies contributes no value and is not a source failure. An entry to which none of these clauses applies is valid, and its value is the level's contribution for its key ([MERGE-14]).

**[SVC-27]** An entry whose key the bundle does not declare MUST NOT affect any result, whatever its value, including `null`. It is not a source failure.

**[SVC-28]** An evaluator MUST NOT validate an entry for a declared key that is not a key being resolved. Such an entry gets no outcome, cannot affect any result, and is not reported to the listener.

**[SVC-29]** An entry for a key being resolved that is not settable at that level MUST be ignored. Its value does not affect any result, whatever it is, and it is not a source failure. It is reported to the listener under [SVC-47].

**[SVC-30]** An entry ignored under [SVC-29] contributes no value for its key ([MERGE-14]) and adds no entry to that key's `contributions`, for any contribution label ([PROV-43], [PROV-44]). Because it is not a source failure, no deny value contributes at that level.

**[SVC-31]** An evaluator MUST validate the value of each entry for a key being resolved at that level against the key's type declaration, under the rules of the `TYPE` area for values received at runtime ([TYPE-70], [TYPE-73], [TYPE-75]).

> Note (non-normative): under those rules an evaluator does not normalise strings ([NORM-3]), canonicalises decimal strings before comparing them ([NORM-15]), checks `iana_tz` values against the syntax only ([TYPE-37]), and rejects a set value with a repeated member or a member outside the universe ([TYPE-46], [TYPE-47]).

**[SVC-32]** For a key whose strategy is `deep_merge`, the validation of [SVC-31] MUST accept a partial object value, as [MERGE-11] allows for every value other than the starting value. A service-sourced level is never the root level, so a provider never supplies the starting value. How the object rules of `TYPE` apply to partial values is [MERGE-12].

**[SVC-33]** The `narrowing_only` check of [KIND-6] does not apply to provider values. A provider value equal to the identity of the key's strategy, such as `true` under `all`, is valid. It is a value entry labelled `IGNORED` ([PROV-21]) and cannot widen the result.

**[SVC-34]** An entry whose value fails the validation of [SVC-31] is a source failure for its key only, with outcome `INVALID`. Its provenance entry is defined by [PROV-40].

**[SVC-35]** An entry for a key being resolved at that level whose value is `null` MUST fail the validation of [SVC-31] ([TYPE-76]). It is not treated as an omitted key.

**[SVC-36]** When a provider result has no entry for a key being resolved at that level, the level MUST set no value for that key. This is not a source failure, and the level makes no contribution for that key ([PROV-42]).

**[SVC-37]** An entry classified under [SVC-29] or [SVC-34] MUST NOT change the outcome for any other key.

| Provider outcome | Effect on the key | Provenance | Clauses |
| --- | --- | --- | --- |
| Throws, rejects, or times out | Source failure for each key being resolved at the level | `UNAVAILABLE` | [SVC-23], [SVC-39] |
| Result is not a mapping | Source failure for each key being resolved at the level | `UNAVAILABLE` | [SVC-25], [SVC-23] |
| Entry for an undeclared key | Ignored; reported as an unknown key | none | [SVC-27], [SVC-45] |
| Entry for a declared key not being resolved | None; not validated and not reported | none | [SVC-28] |
| Entry for a key being resolved, not settable at the level | Ignored; reported to the listener; no value | none (not a source failure) | [SVC-29], [SVC-30], [SVC-47] |
| Entry whose value fails validation, including `null` | Source failure for that key | `INVALID` | [SVC-34], [SVC-35] |
| Valid entry for a key being resolved at the level | Contributes its value | value entry | [SVC-26], [SVC-31], [MERGE-14] |
| No entry for a key being resolved at the level | None | none | [SVC-36] |

## 6.5 Timeouts

**[SVC-38]** Every evaluator MUST support a timeout for each provider. The timeout is supplied in the evaluator options ([EVAL-4]) and is not part of the bundle. A timeout that is not a positive integer number of milliseconds MUST make create evaluator raise `E_EVALUATOR_OPTIONS` ([EVAL-28]). A provider with no timeout is handled by [SVC-48].

**[SVC-39]** A provider call that has not returned when its timeout expires MUST be treated as unavailable under [SVC-23].

**[SVC-40]** An evaluator MUST NOT use a provider result that arrives after the call's timeout has expired.

**[SVC-41]** A timeout is a positive integer number of milliseconds no greater than 2^53−1 (9007199254740991), the integer range of [TYPE-18] (ADR 0025). It is measured from the start of the provider call until the provider result is available to the evaluator. The call has returned at the point its result is available to the evaluator.

Default timeout values belong to the runtime integration, not to this specification (ADR 0009 §3).

**[SVC-48]** A provider registered with no timeout MUST make create evaluator raise `E_EVALUATOR_OPTIONS` ([EVAL-28]). An evaluator MUST NOT supply a default timeout; a runtime integration that offers a default MUST apply it before creating the evaluator (ADR 0014).

## 6.6 Provider need and synchronous resolution

**[SVC-42]** Step 3 of [EVAL-16] MUST find that a provider call is needed if and only if the conditions of [SVC-17] hold for at least one service-sourced level. The synchronous variant of resolve then raises `E_PROVIDER_REQUIRED` instead of calling a provider ([EVAL-11], [EVAL-29]). Every implementation offers that variant ([EVAL-9]).

> Implementation note: the requirements' API sketches (§7.1 and §7.2) name the JavaScript synchronous variant `resolveSync`, and give a synchronous Java provider interface, `LevelValueProvider`, that reports unavailability by throwing `SourceUnavailableException`. Under that sketch the exception is a provider failure under [SVC-23]. These names are not yet implemented.

## 6.7 Events reported to an evaluation listener

[EVAL-41] requires resolve to report provider events to a configured listener. The clauses below fix which provider outcomes those events report. Event timing, overlap, payload, granularity, and the treatment of a listener that fails, are defined by [EVAL-44].

**[SVC-43]** The source failures that [EVAL-41] reports are exactly those under [SVC-23] and [SVC-34].

**[SVC-44]** A timeout MUST be reported as a source failure under [SVC-23].

**[SVC-45]** The unknown keys that [EVAL-41] reports are exactly the entries under [SVC-27].

**[SVC-46]** An evaluator MUST NOT report a key under [SVC-36], which the provider result leaves out, as a source failure or an unknown key.

**[SVC-47]** An entry that [SVC-29] ignores MUST be reported to the listener under [EVAL-41]. It is neither a source failure ([SVC-43]) nor an unknown key ([SVC-45]).

## Open points

- (none)
