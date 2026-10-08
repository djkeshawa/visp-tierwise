# 5. Keys, merge strategies, axes and kinds

This section defines what a key declaration contains, which scopes may hold values for a key, how each merge strategy computes a key's result over the evaluation path, how axes combine with the decision hierarchy, and how kinds constrain keys. The terms **commutative**, **order-dependent**, **narrowing**, **complete object**, **starting value**, **root value**, **contributes**, **hierarchy result**, **axis result**, **merge** and **authored value** are defined by the clauses of this section and listed in `spec.md` §Terminology. It does not define value types and their validation (`TYPE`), scope and context admission (`SCOPE`, `CTX`), service-sourced levels, source failures and `deny` values (`SVC`), the shape of provenance records and contribution effects (`PROV`), preset composition (`MANIFEST`), duplicate definitions (`SRC`) or lint rules (`LINT`). It refers to those areas and does not restate them.

## 5.1 Key declarations

**[KEY-1]** Each key name MUST be declared once in the compiled project. A second declaration of the same key name is reported as [SRC-40] requires (`E_DUPLICATE_DEFINITION`).

**[KEY-2]** A key declaration MUST contain the members `type`, `combine` and `settable_at`.

**[KEY-3]** A key declaration MAY contain the members `default`, `combine_across_axes`, `kind`, `on_source_failure`, `description` and `owner`, and the type members that [TYPE-7] allows for its `type`. It MUST NOT contain any other member. The code for a declaration that contains another member is given by [KEY-5].

**[KEY-4]** The meaning and validation of each member of a key declaration are defined by the clauses named below, and an implementation MUST apply those clauses:

- `type` and the type members: [TYPE-3] to [TYPE-7], and the type declaration grammar of the `TYPE` area;
- `combine` and `default`: [MERGE-1] to [MERGE-12];
- `settable_at`: [KEY-6], [KEY-7] and [KEY-11];
- `combine_across_axes`: [AXIS-2] to [AXIS-8];
- `kind`: [KIND-2] to [KIND-13];
- `on_source_failure`: [KEY-8];
- `description` and `owner`: [KEY-9].

**[KEY-5]** A key declaration or kind definition is reported as follows:
- A key declaration that omits `combine` or `settable_at`, that contains a member that [KEY-3] does not allow, or whose `settable_at` is not a list of names, MUST be reported as `E_INVALID_DECLARATION`.
- A key declaration whose `on_source_failure` is neither `skip` nor `deny` MUST be reported as `E_INVALID_DECLARATION` ([SVC-4]).
- A key declaration that omits `type` MUST be reported as `E_TYPE_REQUIRED` ([TYPE-4]).
- A key declaration whose `combine` names no strategy defined in [MERGE-1] MUST be reported as `E_INVALID_DECLARATION`.
- A kind definition that contains a member that [KIND-3] does not allow, or whose `strategies` list is not a list of strategies defined in [MERGE-1], MUST be reported as `E_KIND_DEFINITION`.

**[KEY-6]** The `settable_at` member MUST be a list of scope names. Each name MUST be the root level, a declared non-root level or a declared axis. A name that is not declared MUST be reported as `E_UNKNOWN_SCOPE`. A service-sourced level MAY appear in `settable_at`. Scope names are compared after NFC normalisation ([SCOPE-8]).

**[KEY-7]** The `settable_at` list MUST NOT be empty. An empty list MUST be reported as `E_INVALID_DECLARATION`. A scope name MUST NOT appear more than once in the list, and a repeated name MUST be reported as `E_DUPLICATE_VALUE`.

**[KEY-8]** The `on_source_failure` member takes a value that [SVC-3] permits, and a kind may supply it under [KIND-9]. A key that is settable at a service-sourced level and has no source-failure behaviour in effect is reported as [SVC-1] and [SVC-2] require (`E_MISSING_SOURCE_FAILURE`). A key-level `on_source_failure` on a key that is settable at no service-sourced level is accepted with no effect ([SVC-7]).

**[KEY-9]** The `description` and `owner` members are informational, and each MUST be a string when present; any other value MUST be reported as `E_INVALID_DECLARATION` (ADR 0015). Their presence, absence and content MUST NOT change any resolved value, decision or provenance record. The lint rules that require them are [LINT-44] (description) and [LINT-47] (owner). The `owner` member is one of the two ways a key has an owner, as [LINT-48] defines.

## 5.2 Values for keys

**[KEY-10]** A value for a key name that the compiled project does not declare MUST be reported as `E_UNKNOWN_KEY`.

**[KEY-11]** A value for a key MUST be written only at a scope listed in that key's `settable_at`. The root level counts as a scope for this rule. A value written at a declared scope that is not listed in the key's `settable_at` MUST be reported as `E_NOT_SETTABLE_HERE`. A value that names an undeclared scope is reported under [SCOPE-46]. A source value that targets a service-sourced level is an error, reported as `E_SOURCE_MAPPING` ([SCOPE-47]). A provider entry for a key that is not settable at its level is not reported under this clause; it is ignored, as [SVC-28] and [SVC-30] define.

**[KEY-12]** Two values for the same key at the same scope and node, from any sources, MUST be reported as `E_DUPLICATE_VALUE`. The root level has no node ID ([SCOPE-13]), so a key has at most one root value.

**[KEY-13]** Every value for a key MUST satisfy the rules for the key's declared type in `TYPE`.

## 5.3 Strategies and types

**[MERGE-1]** The strategies `all`, `any`, `intersect`, `union`, `min` and `max` are **commutative**. The strategies `override` and `deep_merge` are **order-dependent**. A key's strategy is the value of its `combine` member.

**[MERGE-2]** The strategies `all`, `intersect` and `min` are **narrowing**, as requirements §4.3 and ADR 0008 §4 classify them. No other strategy is narrowing.

**[MERGE-3]** The type of a key MUST be allowed for its strategy by this table:

| Strategy | Allowed types |
| --- | --- |
| `override` | any type |
| `deep_merge` | `object` |
| `all`, `any` | `boolean` |
| `intersect`, `union` | `set` |
| `min`, `max` | `integer` |

A key whose type is not allowed for its strategy MUST be reported as `E_STRATEGY_TYPE`.

## 5.4 Identities, defaults and absent results

**[MERGE-4]** A commutative key MUST NOT declare `default`. A commutative key that declares `default` MUST be reported as `E_DEFAULT_NOT_ALLOWED`.

**[MERGE-5]** The identity of a commutative strategy is the result when no scope contributes a value. The identities are:

| Strategy | Identity |
| --- | --- |
| `all` | `true` |
| `any` | `false` |
| `intersect` | the full universe, in universe order |
| `union` | the empty set |
| `min`, `max` | absent |

**[MERGE-6]** A resolved value is absent only when the key's strategy is `min` or `max` and no scope contributes a value. Absence is not a key value ([TYPE-76]).

> Implementation note: Conformance vectors and HTTP APIs express an absent value as JSON `null`. The Java evaluator returns `null` from `value()` and reports `hasValue()` as false. The JS evaluator returns `value: null`.

**[MERGE-7]** An order-dependent key MUST declare `default` or have a root value. A key that has neither MUST be reported as `E_MISSING_DEFAULT`. A **root value** is a value for the key written at the root level.

**[MERGE-8]** A declared `default` is a value of its key and MUST satisfy [KEY-13].

**[MERGE-9]** *Definition.* A **complete object** is an object value in which every field not declared `optional: true` has a value, and every object-typed field that has a value is itself a complete object.

**[MERGE-10]** For a `deep_merge` key, the **starting value** is the `default` when one is declared, and the root value otherwise. The starting value MUST be a complete object ([TYPE-62]). An absent required field in the starting value is a violation of [TYPE-62], reported as `E_TYPE_MISMATCH` ([TYPE-63]).

**[MERGE-11]** For a `deep_merge` key, every value other than the starting value MAY be a partial object. It MAY omit any field, including a field not declared `optional: true`.

*Basis:* ADR 0008 §2 ("Values at other scopes may be partial"), and requirements §4.3, which requires completeness of the starting value only.

**[MERGE-12]** For a `deep_merge` key, [TYPE-62] applies only to the starting value ([MERGE-10]), at every depth of that value. [TYPE-64] and [TYPE-65] apply to every present field of every value of the key, provider values included ([SVC-31], [SVC-32]).

*Basis:* requirements §4.3 requires completeness of the starting value only. ADR 0008 §2 allows partial values at other scopes. That accepted rule is more specific than the general wording of ADR 0011 §1, so it prevails. [TYPE-62] already excepts values that [MERGE-11] permits to be partial, which limits the exception to the same values as this clause.

## 5.5 Resolution over the evaluation path

**[MERGE-13]** The evaluation path is the path defined in [CTX-19]. Its order is the root level first, then the non-root levels from least to most specific ([SCOPE-6]), then the axes in declared order ([SCOPE-7]). A skipped scope is not on the path and contributes no value ([CTX-15]).

**[MERGE-14]** *Definition.* A scope on the evaluation path **contributes** a value for a key only when the key is settable at that scope and the scope supplies a value for it.

**[MERGE-15]** A source failure at a service-sourced level MUST contribute as [SVC-8] (under `skip`, no value) and [SVC-9] (under `deny`, the deny value) define. Only a source failure under `deny` contributes a value.

**[MERGE-16]** Provenance MUST record each contribution as the `PROV` area defines ([PROV-20], [PROV-39], [PROV-40]). The contribution labels of the recorded entries are those in [PROV-37] and [PROV-38], which are derived from the identities in [MERGE-5]. The labels do not change the result computed in this section.

**[MERGE-17]** For a commutative key, the result MUST be the combination of its contributed values, starting from the identity, using the operation of its strategy:

- `all`: logical AND;
- `any`: logical OR;
- `intersect`: set intersection, starting from the full universe;
- `union`: set union;
- `min` and `max`: the smaller or larger of the contributed values, which is absent when there are none.

A set result is returned in universe order ([TYPE-51]).

**[MERGE-18]** The result of a commutative key MUST NOT depend on the order in which its contributed values are combined.

**[MERGE-19]** *Definition.* For an `override` key, the **hierarchy result** H is the contributed value of the most specific level on the evaluation path that contributes one. H is absent when no level contributes.

**[MERGE-20]** *Definition.* For an `override` key, the **axis result** A is the contributed value of the last axis on the evaluation path, in declared order, that contributes one. A is absent when no axis contributes.

**[MERGE-21]** An `override` value MUST replace the key's whole value, including when the key's type is `object`. No field-level merge takes place.

**[MERGE-22]** *Definition.* The **merge** of a base object B and an overriding object O is the object formed from B in which each field f of O is set as follows. If f is declared with type `object` and B has a value for f, the field is merge(B[f], O[f]). Otherwise the field is O[f]. A list, a set or any other non-object value replaces the earlier value as a whole. A merge never removes a field, because `null` is not a valid value.

**[MERGE-23]** *Definition.* For a `deep_merge` key, the **hierarchy result** H is formed by merging the values contributed by the levels, in evaluation-path order from root to most specific. The first contributed value is the first element of the fold, and each later value is merged over the result by [MERGE-22]. The default D is not part of this fold. H is absent when no level contributes.

**[MERGE-24]** *Definition.* For a `deep_merge` key, the **axis result** A is formed in the same way from the values contributed by the axes, in declared order. The default D is not part of this fold. A is absent when no axis contributes.

**[MERGE-25]** For a `deep_merge` key, the default D is the base under the combined contributions. Whether H ([MERGE-23]) and A ([MERGE-24]) are present MUST be decided without D. [AXIS-7] then forms the result, and D supplies each field that no contribution sets. For an `override` key, the lowest-fallback rule holds as stated: D applies only where no level and no axis sets a value.

*Basis:* ADR 0008 §2 ("starting from the default when there is one", and "the default is the lowest fallback"), ADR 0008 §3 step 1 (H is computed "ignoring the default"), and the ADR 0008 §3 table ("merged over the default"; "default ⊕ H ⊕ A"). Requirements §4.3 states the lowest-fallback rule for both order-dependent strategies. For `deep_merge`, this clause gives it the field-by-field reading that ADR 0008 §3 requires.

> Note (non-normative): D ⊕ H equals the fold that starts from D. Merge ([MERGE-22]) is associative, because each field of the result is the fold of the values that set it, in order, and that fold is associative for object and non-object fields alike. So the statement "D ⊕ H" in [AXIS-7] and the fold that starts from the default in ADR 0008 §2 give the same result.

**[MERGE-26]** For a `deep_merge` key, a value that sets an optional object field which the starting value does not set MUST give a complete object for that field ([MERGE-9]). This applies at any depth of the value. The requirement is checked as follows:
- an authored value that breaks it MUST be reported as `E_TYPE_MISMATCH`, by the static check on authored values;
- a provider value that breaks it fails validation under [SVC-31] and has outcome `INVALID` ([SVC-34]).

*Basis:* ADR 0013 (optional object fields), and ADR 0008 §2, which intends every resolved result to be complete.

## 5.6 Axes

**[AXIS-1]** The axes are the entries of `decision_hierarchy.axes`, as `SCOPE` defines them. A key is settable on an axis when the axis name appears in its `settable_at`.

**[AXIS-2]** A commutative key MUST NOT declare `combine_across_axes`. Its axis contributions are ordinary inputs to its operation ([MERGE-17]). A commutative key that declares `combine_across_axes` MUST be reported as `E_AXIS_COMBINE_INVALID`.

**[AXIS-3]** An order-dependent key that is settable on at least one axis MUST declare `combine_across_axes`. Such a key that does not declare it MUST be reported as `E_MISSING_AXIS_COMBINE`.

**[AXIS-4]** The value of `combine_across_axes` MUST be `axis_wins`, `hierarchy_wins`, `max` or `min`. Any other value MUST be reported as `E_AXIS_COMBINE_INVALID`.

**[AXIS-5]** `combine_across_axes` MUST be set to `max` or `min` only on keys whose type is `integer`. Any other key that sets it to either value MUST be reported as `E_AXIS_COMBINE_INVALID`.

**[AXIS-6]** A key that is settable on no axis MUST NOT declare `combine_across_axes`. A key that does so MUST be reported as `E_AXIS_COMBINE_INVALID`.

**[AXIS-7]** For an order-dependent key, the result MUST be formed from the default D (absent when none is declared), the hierarchy result H and the axis result A, as the following table gives. A key that is settable on no axis has no axis result ([AXIS-1], [AXIS-6]), so for such a key only the first two rows apply. In the `deep_merge` column, X ⊕ Y is merge(X, Y) as [MERGE-22] defines it, ⊕ is applied from left to right, so X ⊕ Y ⊕ Z is merge(merge(X, Y), Z), and an absent D is omitted from the expression. The first and third rows can occur only when D is declared, because [MERGE-7] guarantees that otherwise H is present.

| H | A | `combine_across_axes` | Result for `override` | Result for `deep_merge` |
| --- | --- | --- | --- | --- |
| absent | absent | any | D | D |
| present | absent | any | H | D ⊕ H |
| absent | present | any | A | D ⊕ A |
| present | present | `axis_wins` | A | D ⊕ H ⊕ A |
| present | present | `hierarchy_wins` | H | D ⊕ A ⊕ H |
| present | present | `max` or `min` | the larger (`max`) or smaller (`min`) of H and A, per [AXIS-8] | not allowed: `E_AXIS_COMBINE_INVALID` ([AXIS-5]) |

> **Example (non-normative).** `checkout.pickup.lead_hours` in requirements §5.3 is an `integer` with `combine: override`, `default: 4` and `combine_across_axes: max`. Its division `south` sets `6` (requirements §5.4). Suppose, hypothetically, that the axis `seller: acme` sets `8`. So H = 6 and A = 8. Under `max` the result is `8`. Under `hierarchy_wins` the result would be `6`.

**[AXIS-8]** When `max` or `min` is declared and both H and A are present, the result is the larger or smaller of the two. On a tie, the result is H. The `source` on a tie is defined by [PROV-51].

## 5.7 Kinds

**[KIND-1]** A **kind** is a named set of constraints that keys opt into. The engine gives a kind's name no meaning, and it enforces only the members listed in [KIND-3]. A kind is declared by the project or by a preset. A later definition replaces an earlier one as [MANIFEST-32] and [MANIFEST-33] require. Two definitions of the same kind inside the project are reported as `E_DUPLICATE_DEFINITION` ([SRC-43]).

**[KIND-2]** A key opts into a kind by naming it in its `kind` member, and kind names are compared after NFC normalisation. A key with no `kind` member is not constrained by any kind. A key names at most one kind.

**[KIND-3]** A kind definition MAY contain these members: `strategies`, `narrowing_only`, `require_default`, `on_source_failure` and `naming`. A member that the definition omits imposes no constraint. An empty `strategies` list MUST be reported as `E_KIND_DEFINITION` (ADR 0015).

**[KIND-4]** When a kind definition has `strategies`, a key that uses the kind MUST use one of the listed strategies. A key that uses any other strategy MUST be reported as `E_KIND_VIOLATION`. A kind definition without `strategies` does not restrict the strategy.

**[KIND-5]** A kind definition with `narrowing_only: true` MUST allow only narrowing strategies ([MERGE-2]). A kind definition that allows any other strategy MUST be reported as `E_KIND_DEFINITION`. A definition with `narrowing_only: true` and no `strategies` member allows every strategy, and is therefore reported under this rule.

**[KIND-6]** An **authored value** is a value for a key written in a source, as opposed to a value returned by a provider or a `deny` value. For a key that uses a kind with `narrowing_only: true`, an authored value MUST NOT equal the identity of the key's strategy ([MERGE-5]). An authored value that does MUST be reported as `E_KIND_VIOLATION`. The legal authored values are:

| Strategy | Legal authored values |
| --- | --- |
| `all` | `false` only |
| `intersect` | every strict subset of the universe, including the empty set |
| `min` | every value valid for the key's type ([TYPE-18], [TYPE-19]), because the identity is absent |

**[KIND-7]** The check in [KIND-6] is static. It examines each authored value alone, and does not depend on the value inherited along an evaluation path. A value that is redundant only on a particular path is not an engine error, and no lint rule of this version flags it ([LINT-1]).

**[KIND-8]** A kind definition with `require_default: true` requires every key that uses it to declare `default`. A key that uses such a kind and does not declare `default` MUST be reported as `E_KIND_VIOLATION`.

**[KIND-9]** A key that declares no `on_source_failure` and whose kind declares one takes the kind's value as its `on_source_failure`, as [SVC-1] allows. Where the key and its kind both declare the member, [KIND-11] applies.

**[KIND-10]** A kind definition with `require_default: true` MUST NOT allow a commutative strategy ([MERGE-1]), because a commutative key MUST NOT declare `default` ([MERGE-4]). A kind definition that allows one MUST be reported as `E_KIND_DEFINITION`. A definition with `require_default: true` and no `strategies` member allows every strategy, and is therefore reported under this rule.

**[KIND-11]** A kind's `on_source_failure` member takes a single value. A value that [SVC-3] does not permit is reported as [SVC-4] defines. Where a key that uses the kind also declares `on_source_failure`, the key's value MUST equal the kind's value. A key whose value differs MUST be reported as `E_KIND_VIOLATION`.

**[KIND-12]** A kind's `naming` member MUST name one pattern declared under the top-level `naming` map of a shared schema file or a preset ([LINT-14]), and that pattern MUST have category `key` ([LINT-16]). A kind whose `naming` member names no declared pattern, or names a pattern whose category is not `key`, MUST be reported as `E_KIND_DEFINITION`. Whether a key's name matches the pattern is a lint rule of the `naming/*` family ([LINT-16]), not an engine check. The pattern language is defined by [LINT-17].

**[KIND-13]** A key whose `kind` member names a kind that the compiled project does not declare MUST be reported as `E_UNKNOWN_KIND`.

## Open points

- (none)
