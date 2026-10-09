# 10. Bundle format and determinism

This section defines the canonical bundle: its envelope and byte form, the members of `content`, the digest that names it, the checks a loader applies, the provenance sidecar kept beside it, and the rules that make compilation reproducible. It does not define what each member means. Each member's meaning belongs to the clause area that owns the concept, and this section only fixes how the member is laid out. It does not define the server that stores bundles. The JSON Schema for `content` (`spec/schemas/`, work item 1.7) is derived from this section and is not normative.

## 10.1 Envelope and digest

**[BUNDLE-1]** A bundle MUST be a JSON object with exactly two members, `bundleVersion` and `content`.

**[BUNDLE-2]** A bundle MUST consist of the UTF-8 bytes of the JCS serialisation of its envelope (see [BUNDLE-7]).

**[BUNDLE-3]** A bundle MUST NOT begin with a byte-order mark.

**[BUNDLE-4]** A bundle MUST NOT have any byte after the end of the serialisation in [BUNDLE-2], including a newline.

**[BUNDLE-5]** `bundleVersion` MUST be the string `sha256:` followed by exactly 64 lowercase hexadecimal digits.

**[BUNDLE-6]** The 64 digits of `bundleVersion` MUST be the SHA-256 digest of the UTF-8 bytes of the JCS serialisation of `content` alone, not of the envelope.

## 10.2 JSON canonical form

**[BUNDLE-7]** The serialisation MUST follow RFC 8785 (JCS), with numbers restricted by [BUNDLE-11] and [BUNDLE-12].

**[BUNDLE-8]** The serialisation MUST contain no insignificant whitespace.

**[BUNDLE-9]** Object members MUST be sorted by the UTF-16 code units of their names, as RFC 8785 section 3.2.3 requires.

**[BUNDLE-10]** Strings MUST be written as RFC 8785 section 3.2.2.2 requires. U+0008, U+0009, U+000A, U+000C and U+000D MUST be written `\b`, `\t`, `\n`, `\f` and `\r`. `"` and `\` MUST be written `\"` and `\\`. Every other code point below U+0020 MUST be written as `\u` followed by four lowercase hexadecimal digits. Every other code point, including `/` and every non-ASCII code point, MUST be written unescaped as its UTF-8 bytes.

**[BUNDLE-11]** Every JSON number in a bundle MUST be an integer whose text matches `0|-?[1-9][0-9]*` in full.

> Non-normative: the pattern excludes fractions, exponents and `-0`.

**[BUNDLE-12]** The value of every JSON number in a bundle MUST lie within the range of [DET-4].

**[BUNDLE-13]** A string in a bundle MUST NOT contain an unpaired surrogate code point.

> Non-normative: such a string has no UTF-8 form, and [BUNDLE-10] allows no `\u` escape for it. Sources may not contain one either ([NORM-8]). The code a loader reports for an escaped one is given in [BUNDLE-116].

## 10.3 Content members

**[BUNDLE-14]** `content` MUST have no member other than `bundleFormatVersion`, `specVersion`, `hierarchy`, `entities`, `facts`, `keys`, `values` and `features`.

**[BUNDLE-15]** `content` MUST have every member that [BUNDLE-14] names.

**[BUNDLE-16]** `bundleFormatVersion` MUST be the string `"1"` in a bundle that conforms to this version of the specification.

**[BUNDLE-17]** `specVersion` MUST be the string `"0.1"` in a bundle that conforms to this version of the specification.

**[BUNDLE-18]** `specVersion` MUST equal the `specVersion` declared by the manifest that the bundle was compiled from (MANIFEST).

**[BUNDLE-19]** A REQUIRED member MUST be present even when it is empty.

**[BUNDLE-20]** An OPTIONAL member MUST be absent when it has no value.

**[BUNDLE-21]** A member MUST NOT use a placeholder value, such as `null`, to stand for an absent value.

**[BUNDLE-22]** A scope member under `values` or `bindings` MUST be absent when it has no node entries, and a node entry under `values` MUST be absent when it has no key values. An entry exists only when it holds at least one value or binding. [BUNDLE-19] still applies to the containers `values` and `bindings` themselves, which are present even when empty.

**[BUNDLE-23]** `null` MUST NOT appear in `content`, except as an effect that [BUNDLE-24] permits.

**[BUNDLE-24]** A `null` effect MAY appear only as a rule's `then`, a policy's `default` or a feature's `ifFactMissing`, and only where the feature's `returns` is nullable ([FEAT-9]).

> Non-normative: only the enum form of `returns` may be nullable ([FEAT-9], [FEAT-12]), so [BUNDLE-82] writes `nullable` on that form only. A setting value is never `null` ([POL-18]), and a key's default is never `null` ([BUNDLE-56]).

**[BUNDLE-25]** Each keyword member name MUST be spelled as in Table 10.1.

**[BUNDLE-26]** Each member name that Table 10.1 does not list MUST keep the spelling it has in the source, including every user-chosen name.

**[BUNDLE-27]** Each keyword value, such as a strategy name, `FIRST`, `skip`, `deny`, `open` or an operator, MUST keep its source spelling.

**[BUNDLE-28]** The member names `valueOf` and `listOf` MUST be spelled as Table 10.1 gives them.

**Table 10.1: member names that differ from the source**

| Source spelling | Content member | Where |
| --- | --- | --- |
| `decision_hierarchy` | `hierarchy` | `content` |
| `entity_hierarchy` | `entities` | `content` |
| `on_unknown_node` | `onUnknownNode` | scope |
| `belongs_to` | `belongsTo` | entity |
| `has_many` | `hasMany` | entity |
| `settable_at` | `settableAt` | key |
| `combine_across_axes` | `combineAcrossAxes` | key |
| `on_source_failure` | `onSourceFailure` | key |
| `decided_per` | `decidedPer` | feature |
| `hit_policy` | `hitPolicy` | feature |
| `if_fact_missing` | `ifFactMissing` | feature |
| `binding_settable_at` | `bindingSettableAt` | feature |
| `value_of` | `valueOf` | setting |
| `list_of` | `listOf` | setting |
| (derived, see [BUNDLE-91]) | `requiredFacts` | policy |

> Non-normative: some content member names have no spelling in the source, so [BUNDLE-26] cannot apply to them, and this section fixes them. They are `bundleVersion` and `bundleFormatVersion` (compiler output), `requiredFacts` (derived), `entity` in a fact (a fact's entity is the prefix of its name, FACT), `fact`, `op` and `value` in a condition (a source condition is written `<fact>: <operand>` or `<fact>: { <operator>: <operand> }`, RULE), and `settings` in a binding (a source binding writes its setting values beside `policy`, BIND). The names `levels`, `axes` and `scopes` and the keyword `open` already have source spellings (`decision_hierarchy.levels`, `nodes: open`), so [BUNDLE-26] and [BUNDLE-27] already apply to them.

### 10.3.1 Hierarchy

**[BUNDLE-29]** `hierarchy` MUST have exactly the members `levels`, `axes` and `scopes`, and `scopes` MUST be an object.

**[BUNDLE-30]** `levels` MUST be an array of level names in declared order, with the root level first.

**[BUNDLE-31]** `axes` MUST be an array of axis names in declared order, and MUST be empty when no axes are declared.

**[BUNDLE-32]** `scopes` MUST have exactly one member for each non-root level and each axis, and no other member (the SCOPE clauses).

**[BUNDLE-33]** Each scope MUST have a REQUIRED `values` member.

**[BUNDLE-34]** The value of each `values` member MUST be `"files"` or `"service"`.

**[BUNDLE-35]** The bundle MUST write each `values` member with the value that the SCOPE clauses give when the source omits it.

**[BUNDLE-36]** `"service"` MUST appear only as the value of a level's `values` member (the SCOPE and SVC clauses).

**[BUNDLE-37]** Each scope MUST have a REQUIRED `nodes` member, whose value is either the string `"open"` or an array.

**[BUNDLE-38]** A `nodes` array MUST hold the complete closed catalog, with each admitted node ID exactly once.

**[BUNDLE-39]** A `nodes` array MUST be in code point order ([DET-6]).

**[BUNDLE-40]** The catalog MUST be embedded in the bundle, and a bundle MUST NOT refer to an external file (the SCOPE clauses).

**[BUNDLE-41]** An empty closed catalog MUST be written as `[]`, because a closed catalog with no IDs is valid ([SCOPE-21]).

**[BUNDLE-42]** `onUnknownNode` MUST be present exactly when `nodes` is an array.

**[BUNDLE-43]** The value of `onUnknownNode` MUST be `"error"` or `"ignore"` (the SCOPE clauses).

### 10.3.2 Entities and facts

**[BUNDLE-44]** `entities` MUST have one member for each declared entity, and each member MUST be an object with exactly the arrays `belongsTo` and `hasMany`.

**[BUNDLE-45]** `belongsTo` and `hasMany` MUST hold the names that the entity's own `belongs_to` and `has_many` declarations name, that is, its direct edges (the ENTITY clauses).

**[BUNDLE-46]** A `belongs_to` or `has_many` declaration that the source writes as a single name MUST be written as a one-element array, and an absent one MUST be written as `[]`.

**[BUNDLE-47]** The `belongsTo` and `hasMany` arrays MUST NOT hold inverse edges or ancestor closures ([BUNDLE-110]).

**[BUNDLE-48]** `facts` MUST have one member for each declared fact, named `<entity>.<attribute>`.

**[BUNDLE-49]** Each fact member MUST be an object with exactly the members `entity`, the declared entity named by the prefix of the fact's name, and `type`.

**[BUNDLE-50]** The `type` of a fact MUST be a type declaration whose `type` is one of `boolean`, `integer`, `decimal`, `string` or `enum` (the FACT and TYPE clauses).

### 10.3.3 Keys

**[BUNDLE-51]** `keys` MUST have one member for each declared key, and each member MUST be an object.

**[BUNDLE-52]** Each key MUST have the REQUIRED members `type` (the key's type declaration: its `type` member together with the type members that the TYPE clauses allow for it, such as `min` and `max`), `combine` (the strategy name, the MERGE clauses) and `settableAt` (the scope names at which the key may be set, [BUNDLE-54]).

**[BUNDLE-53]** A key MUST NOT have any member other than those named in [BUNDLE-52] and the OPTIONAL members `default`, `combineAcrossAxes`, `onSourceFailure` and `kind`, which [BUNDLE-55], [BUNDLE-57], [BUNDLE-59] and [BUNDLE-62] define.

**[BUNDLE-54]** `settableAt` MUST list its scope names in scope order: the levels in declared order, followed by the axes in declared order. `bindingSettableAt` MUST list its level names in declared level order ([BUNDLE-81]).

**[BUNDLE-55]** A key's `default` MUST be present exactly when the key declares a default (the MERGE clauses).

**[BUNDLE-56]** A key's `default` MUST be a value of the key's type. No type admits `null` (the TYPE clauses), so the default is never `null`.

**[BUNDLE-57]** `combineAcrossAxes` MUST be present exactly when the key is order-dependent and settable at an axis (the MERGE clauses).

**[BUNDLE-58]** The value of `combineAcrossAxes` MUST be the declared one of `axis_wins`, `hierarchy_wins`, `max` or `min` (the MERGE clauses).

**[BUNDLE-59]** `onSourceFailure` MUST be present exactly when the key is settable at a service-sourced level (the SVC clauses).

**[BUNDLE-60]** The value of `onSourceFailure` MUST be `skip` or `deny`.

**[BUNDLE-61]** The value of `onSourceFailure` MUST be the behaviour in effect for the key, whether it is declared on the key or obtained through its kind.

> Non-normative: the bundle holds this value even though kinds are excluded ([BUNDLE-109]), because it is an evaluation input. Which value is in effect when a key and its kind both declare one is decided by the KIND and SVC clauses.

**[BUNDLE-62]** `kind` MUST be present exactly when the key opts into a kind (the KEY clauses).

**[BUNDLE-63]** The value of `kind` MUST be the name of that kind and nothing else.

### 10.3.4 Values

**[BUNDLE-64]** Each member of `values` MUST be named by a level or an axis that is not service-sourced, and its value MUST be an object.

**[BUNDLE-65]** Each object in [BUNDLE-64] MUST map each node key to an object, and each such object MUST map key names to values.

**[BUNDLE-66]** A node key MUST be the empty string `""` for the root level's implicit node, which has no node ID, and the node ID for every other scope (the SCOPE clauses).

> Non-normative: scope names are unique across levels and axes, and a node ID is never empty, so `""` occurs only under the root level.

**[BUNDLE-67]** `values` MUST NOT contain an entry for a level declared `values: service` (the SVC clauses).

**[BUNDLE-68]** A boolean value MUST be a JSON boolean.

**[BUNDLE-69]** An integer value MUST be a JSON integer.

**[BUNDLE-70]** A string value and an enum value MUST each be a JSON string.

**[BUNDLE-71]** A decimal value MUST be its canonical decimal string (the NORM clauses).

**[BUNDLE-72]** A list value MUST be a JSON array in the order of the source list (the TYPE clauses).

**[BUNDLE-73]** An object value MUST be a JSON object with one member for each field present in the value and no other member. An absent optional field has no member (the TYPE clauses).

**[BUNDLE-74]** A set value MUST be a JSON array of its members in universe order (the TYPE clauses).

> Non-normative: an effect, a setting value and a condition literal use the encodings above. A `listOf` value is encoded as [BUNDLE-89] and [BUNDLE-90] require.

**[BUNDLE-75]** Each of the following arrays has no order of its own and holds strings, decimal strings included ([BUNDLE-71]). Each MUST be in code point order ([DET-5]): the `values` of an `enum` type declaration, the `enum` list of a feature's `returns`, the `belongsTo` and `hasMany` arrays, a `listOf` value, and the array value of an `in`, `all_in` or `any_in` condition. The arrays of booleans and integers are governed by [BUNDLE-152]. The orders that are settled are not in question: `levels`, `axes`, `rules` and a `set` type's `universe` keep their declared order, `list` and `set` values follow [BUNDLE-72] and [BUNDLE-74], and `settableAt` and `bindingSettableAt` follow [BUNDLE-54].

**[BUNDLE-152]** A compiler MUST write an array of integer literals in ascending numeric order, and an array of boolean literals with `false` before `true`. Membership tests do not depend on this order; it only fixes the bytes (ADR 0014).

### 10.3.5 Features, policies and bindings

**[BUNDLE-76]** `features` MUST have one member for each declared feature, and each member MUST be an object with exactly the REQUIRED members `decidedPer`, `returns`, `hitPolicy`, `ifFactMissing`, `bindingSettableAt`, `policies` and `bindings`.

**[BUNDLE-77]** `decidedPer` MUST be the name of a declared entity, the entity that the decision is about (the FEAT clauses).

**[BUNDLE-78]** `hitPolicy` MUST be the string `"FIRST"`, the only hit policy in this version (the FEAT clauses).

**[BUNDLE-79]** `ifFactMissing` MUST be present and MUST be an effect valid for `returns` (the FEAT clauses).

**[BUNDLE-80]** Every feature MUST have an `ifFactMissing` member, because the source requires `if_fact_missing` ([FEAT-3]).

**[BUNDLE-81]** `bindingSettableAt` MUST hold level names only, in the order that [BUNDLE-54] gives (the FEAT and BIND clauses).

**[BUNDLE-82]** The value of `returns` MUST be either `{ "enum": [...] }` or `{ "object": { <field name>: <field declaration>, ... } }`. The enum list MUST be in the order that [BUNDLE-75] gives. The enum form MUST have a `nullable` member, with the value `true`, only when the source declares `nullable: true` ([FEAT-9]), and otherwise no `nullable` member. The object form MUST NOT have a `nullable` member ([FEAT-12]). Each field declaration MUST be a type declaration in the form that the TYPE clauses give an object field, with an `optional` member present only when the field is optional and its value is `true`.

**[BUNDLE-83]** `policies` MUST have one member for each policy that the feature declares, and each member MUST be an object with exactly the REQUIRED members `settings`, `default`, `requiredFacts` and `rules`.

**[BUNDLE-84]** `settings` MUST be an object keyed by setting name, and MAY be empty (the POL clauses).

**[BUNDLE-85]** A policy's `default` MUST be an effect valid for `returns` (the POL and FEAT clauses).

**[BUNDLE-86]** Each setting MUST be an object whose members are `default` ([BUNDLE-87]) and exactly one of `valueOf`, a fact name for a single value, and `listOf`, a fact name for a list of distinct values (the POL clauses).

**[BUNDLE-87]** Each setting MUST have a REQUIRED `default` (the POL clauses).

**[BUNDLE-88]** A `valueOf` setting's `default` MUST be a value of the type of its fact (the POL clauses).

**[BUNDLE-89]** A `listOf` value, as a `default` or in a binding, MUST be a JSON array of distinct values of the type of its fact (the POL clauses).

**[BUNDLE-90]** A `listOf` value MUST be in the order that [BUNDLE-75] and [BUNDLE-152] give.

**[BUNDLE-91]** `requiredFacts` MUST be the distinct fact names that appear as `fact` in any condition of any rule of the policy, including rules that can never run.

**[BUNDLE-92]** `requiredFacts` MUST be in code point order ([DET-6]).

> Non-normative: `requiredFacts` is the only derived member that the content holds ([BUNDLE-110]).

**[BUNDLE-93]** `rules` MUST be an array of rule objects in source order (the RULE clauses).

**[BUNDLE-94]** Each rule MUST have exactly the REQUIRED members `id`, `name`, `when` and `then`.

**[BUNDLE-95]** A rule's `name` MUST be kept in the content, because evaluation returns it as part of the rule's reason (the RULE clauses). The exclusion of descriptions in [BUNDLE-109] does not apply to it.

**[BUNDLE-96]** `when` MUST be an array with one condition for each fact that the source `when` names, sorted by `fact` in code point order ([DET-6]).

> Non-normative: a source `when` names each fact once (the RULE clauses), so no fact occurs in more than one condition.

**[BUNDLE-97]** A rule whose source `when` is empty MUST have `when` as `[]`.

**[BUNDLE-98]** Each condition MUST have exactly the members `fact`, `op` and `value`, where `fact` is the name of the fact that the source condition names.

**[BUNDLE-99]** `op` MUST be one of `eq`, `in`, `all_in` or `any_in` (the RULE clauses).

**[BUNDLE-100]** For `eq`, `value` MUST be a literal of the fact's type or a setting reference (the RULE clauses).

**[BUNDLE-101]** For `in`, `all_in` and `any_in`, `value` MUST be an array of literals, which MAY be empty, or a setting reference (the RULE clauses).

**[BUNDLE-102]** A literal in a condition's `value` MUST be encoded as [BUNDLE-68] to [BUNDLE-74] require.

**[BUNDLE-103]** A setting reference MUST stay symbolic in the bundle and MUST NOT be replaced by its value. It is resolved against the selected binding at evaluation time (the BIND and RULE clauses). Its encoding is [BUNDLE-104].

**[BUNDLE-104]** A setting reference MUST be written as the string `$` followed by the setting name, in place of the whole value (the literal for `eq`, or the array for the other operators). A string literal in a condition's `value` MUST NOT begin with `$`. Setting names contain no `$` or `.` ([POL-20]), and [RULE-4] reads an operand string that begins with `$` as a setting reference.

**[BUNDLE-105]** `bindings` MUST be an object keyed by level name, and each level entry MUST be an object keyed by node key ([BUNDLE-66]).

**[BUNDLE-106]** `bindings` MUST NOT contain an axis, because only levels carry bindings (the BIND clauses).

**[BUNDLE-107]** Each binding MUST be an object with exactly the members `policy`, the name of the policy, and `settings`, an object.

**[BUNDLE-108]** `settings` MUST contain exactly the settings that the source binding writes. The policy's defaults MUST NOT be copied into it (the BIND and POL clauses).

## 10.4 Exclusions and derived data

**[BUNDLE-109]** `content` MUST NOT contain compile-time-only data. That covers kind definitions, naming patterns, lint rules and severities, descriptions, examples, the source declared for a fact, presets, namespace and ownership declarations, a key's `owner`, and source file names, paths and positions. The only trace of a kind is its name ([BUNDLE-63]).

**[BUNDLE-110]** `content` MUST NOT contain a derived value other than `requiredFacts`.

> Non-normative: a *derived value* is one that an evaluator can compute from other members of `content`, for example a strategy identity, an entity ancestor closure, an inverse entity edge, or a policy default copied into a binding. A value that the compiler takes from a source default ([BUNDLE-35]) or from a kind ([BUNDLE-61]) is not derived in this sense. It is an evaluation input whose origin is excluded by [BUNDLE-109].

## 10.5 Loading and integrity

**[BUNDLE-111]** A loader MUST reject a document that has a duplicate property name at any depth, including inside `content`, and MUST report `E_BUNDLE_INTEGRITY`.

**[BUNDLE-112]** A loader MUST reject a document whose envelope has any member other than `bundleVersion` and `content`, and MUST report `E_BUNDLE_INTEGRITY`.

**[BUNDLE-113]** A loader MUST recompute the digest of `content` as defined in [BUNDLE-6]. If the result differs from `bundleVersion`, the loader MUST reject the bundle and report `E_BUNDLE_INTEGRITY`.

**[BUNDLE-114]** A loader MUST NOT evaluate anything from a bundle it has rejected.

**[BUNDLE-115]** A loader MUST reject a bundle whose `bundleFormatVersion` or `specVersion` it does not support, and MUST report `E_BUNDLE_UNSUPPORTED` ([EVAL-25]). The values it supports are given in [BUNDLE-137].

**[BUNDLE-116]** A loader MUST report `E_BUNDLE_INTEGRITY` for input that is not UTF-8, begins with a byte-order mark, is not well-formed JSON, lacks `bundleVersion` or `content`, contains a number that breaks [BUNDLE-11] or [BUNDLE-12] (a non-integer, or an integer outside the range of [DET-4]), contains an escaped unpaired surrogate ([BUNDLE-13]), or is not exactly the serialisation that [BUNDLE-2] requires. [EVAL-24] refers to this clause for the same question.

**[BUNDLE-117]** A loader MUST apply the checks of this section in the order that [EVAL-14] states, and MUST report the code that [EVAL-14] gives when a bundle fails more than one of them.

> Non-normative: [EVAL-14] owns the order of the checks, so this clause does not restate it.

**[BUNDLE-118]** A loader MUST reject a bundle whose `content` has a member, or a value, that does not match the members and forms that [BUNDLE-14] to [BUNDLE-108] define, and MUST report `E_BUNDLE_INTEGRITY`. [BUNDLE-16] and [BUNDLE-17] are excluded, because [BUNDLE-115] and [BUNDLE-137] govern the two version values. [BUNDLE-18] is excluded too, because a loader does not see the manifest. A loader MUST also reject, with `E_BUNDLE_INTEGRITY`, a bundle that violates a member rule of this section or a rule of the MERGE, KEY, SCOPE, ENTITY or BIND clauses that the bundle alone can show to be broken, such as a missing starting value (MERGE-7), an empty `settableAt` (KEY-7) or a missing root binding (BIND-13). Rules that need source information, such as RULE-13, RULE-14 and POL-16, are not rechecked (ADR 0018).

**[BUNDLE-119]** A loader MUST reject a `nodes`, `requiredFacts` or `when` array that is not in the order that [DET-6] requires, and MUST report `E_BUNDLE_INTEGRITY`.

**[BUNDLE-120]** A loader MUST reject a fact name that a condition's `fact`, a setting's `valueOf` or `listOf`, or a `requiredFacts` entry uses when the bundle declares no fact of that name, and MUST report `E_BUNDLE_INTEGRITY`.

**[BUNDLE-121]** A loader MUST reject an entity name that `decidedPer`, a fact's `entity`, `belongsTo` or `hasMany` uses when the bundle declares no entity of that name, and MUST report `E_BUNDLE_INTEGRITY`.

**[BUNDLE-122]** A loader MUST reject a fact member whose `entity` differs from the prefix of its name, and MUST report `E_BUNDLE_INTEGRITY` (the FACT clauses).

**[BUNDLE-123]** A loader MUST reject a binding whose `policy` names no policy declared in the same feature, and MUST report `E_BUNDLE_INTEGRITY`.

**[BUNDLE-124]** A loader MUST reject a setting in a binding that the binding's policy does not declare, and MUST report `E_BUNDLE_INTEGRITY`.

**[BUNDLE-125]** A loader MUST reject a setting reference in a condition that names no setting of the same policy, and MUST report `E_BUNDLE_INTEGRITY` (the RULE clauses).

**[BUNDLE-126]** A loader MUST reject a setting reference in any position that the RULE clauses do not allow, and MUST report `E_BUNDLE_INTEGRITY`. The allowed positions are: the whole operand of an `eq` condition, naming a `valueOf` setting of the condition's fact; and the whole operand of an `in`, `all_in` or `any_in` condition, naming a `listOf` setting of the condition's fact. A setting reference inside a literal list is never allowed.

**[BUNDLE-127]** A loader MUST reject a scope name in `settableAt`, `bindingSettableAt`, `values` or `bindings` that the hierarchy does not declare, and a name in `bindings` or `bindingSettableAt` that is not a level. It MUST report `E_BUNDLE_INTEGRITY` (the SCOPE and KEY clauses).

**[BUNDLE-128]** A loader MUST reject a `bindingSettableAt` that omits the root level, or that names a service-sourced level, and MUST report `E_BUNDLE_INTEGRITY`.

**[BUNDLE-129]** A loader MUST reject a node key under `values` or `bindings` that a closed catalog does not admit, a `""` node key under a non-root scope, or a node ID in a `nodes` array that is empty, and MUST report `E_BUNDLE_INTEGRITY`.

**[BUNDLE-130]** A loader MUST reject a key under `values` that the bundle does not declare, or whose `settableAt` does not include the scope it sits under, and MUST report `E_BUNDLE_INTEGRITY` (the KEY clauses).

**[BUNDLE-131]** A loader MUST reject a value under `values` that is not valid for the type of its key, and a setting default or binding value that is not valid for the type of its fact. It MUST report `E_BUNDLE_INTEGRITY` (the TYPE and POL clauses).

**[BUNDLE-132]** A loader MUST reject two rules of one policy with the same `id`, and MUST report `E_BUNDLE_INTEGRITY`.

**[BUNDLE-133]** A loader MUST reject a `nodes` array, or a `listOf` value, that repeats an element, and MUST report `E_BUNDLE_INTEGRITY`.

**[BUNDLE-134]** A loader MUST reject a `set` value that repeats a member or that has a member outside the universe of its type, and MUST report `E_BUNDLE_INTEGRITY`.

**[BUNDLE-135]** A loader MUST reject a decimal string that is not in canonical form (the NORM clauses), and MUST report `E_BUNDLE_INTEGRITY`.

**[BUNDLE-136]** A loader MUST reject a policy whose `requiredFacts` is not the distinct fact names that appear in its rules' conditions, in code point order ([BUNDLE-91], [BUNDLE-92]), and MUST report `E_BUNDLE_INTEGRITY`.

> Non-normative: for [BUNDLE-118] to [BUNDLE-136], a bundle that breaks a check was not compiled from valid sources. The sources report the same faults under their own codes, for example `E_UNKNOWN_FACT`, `E_UNKNOWN_ENTITY`, `E_UNKNOWN_KEY`, `E_NOT_SETTABLE_HERE`, `E_UNKNOWN_NODE`, `E_DUPLICATE_NODE`, `E_DUPLICATE_RULE_ID`, `E_NOT_IN_UNIVERSE`, `E_INVALID_SETTING` and `E_TYPE_MISMATCH`. [BUNDLE-136] is needed because the digest proves only that the bytes match `content`, not that the compiler derived `requiredFacts` correctly, and a wrong precheck changes the result of a decision (the RULE clauses).

**[BUNDLE-137]** A loader MUST support exactly the `bundleFormatVersion` value `"1"` and the `specVersion` value `"0.1"`, compared as exact JSON strings. It MUST treat any other string value as unsupported under [BUNDLE-115]. A member that is missing or is not a string is covered by [EVAL-56]. No range of versions is supported.

**[BUNDLE-138]** A loader MUST NOT check whether strings are in NFC form. Strings are compared as received ([NORM-4]).

> Non-normative: checking normalisation needs Unicode data that can differ between runtimes. A non-NFC string can only come from a defective compiler, and it matches only its exact code points.

## 10.6 Provenance sidecar

**[BUNDLE-139]** Provenance MUST be kept outside the bundle, in a sidecar file that sits beside the bundle file or in a server publication record ([BUNDLE-148]).

**[BUNDLE-140]** The sidecar MUST NOT be part of the bundle bytes, the digest or any evaluation.

**[BUNDLE-141]** The sidecar MUST be a JSON object with the REQUIRED members `bundleVersion` (the `bundleVersion` of the bundle it describes) and `createdAt` (a time in RFC 3339 form, in UTC).

**[BUNDLE-142]** The sidecar MAY have the OPTIONAL members `gitCommit` and `publisher`.

**[BUNDLE-143]** The sidecar MUST NOT have any member other than those that [BUNDLE-141] and [BUNDLE-142] define.

**[BUNDLE-144]** A tool MUST NOT attach a sidecar to a bundle whose `bundleVersion` differs from the sidecar's `bundleVersion`.

**[BUNDLE-145]** An evaluator MUST NOT read a sidecar to produce a result. The `bundleVersion` in a result comes from the bundle alone (the PROV clauses).

**[BUNDLE-146]** A sidecar MUST hold exactly one provenance record.

**[BUNDLE-147]** A tool MUST NOT replace or overwrite an existing sidecar.

**[BUNDLE-148]** Several provenance records MAY carry the same `bundleVersion`. Each one MUST be stored in a sidecar of its own, or in a server publication record, and a record MUST NOT replace another record in the store that holds it.

> Non-normative: server publication records have the same members as a sidecar ([BUNDLE-141] and [BUNDLE-142]). They are defined with the server, outside this section, and like sidecars they never change bundle bytes ([DET-3]).

**[BUNDLE-149]** The sidecar for a bundle MUST be named `<stem>.provenance.json`, where `<stem>` is the bundle file's name with its `.json` extension removed. A further sidecar for the same bundle ([BUNDLE-148]) MUST be named `<stem>.provenance.<label>.json`, where `<label>` is a lowercase label that the tool chooses, unique among that bundle's sidecars.

**[BUNDLE-150]** When present, the members of a sidecar MUST have these forms: (a) `gitCommit` is the full commit identifier: 40 or 64 lowercase hexadecimal digits; (b) `createdAt` is an RFC 3339 time that denotes a real calendar date and time, with an uppercase `T` separator and a `Z` offset, with seconds and without fractional seconds; (c) `publisher` is a string that is an opaque label.

> Non-normative: no evaluator reads `publisher`, and the sidecar does not authenticate it. Trust in publisher claims is outside this specification.

**[BUNDLE-151]** The spec tool `check` (implementation plan item 1.12), and from Phase 5 the CLI, MUST report `E_PROVENANCE_INVALID` for a sidecar that fails strict parsing under the duplicate-property rule of [BUNDLE-111], that breaks [BUNDLE-141] to [BUNDLE-143] or the forms of [BUNDLE-150], or whose `bundleVersion` differs from that of the bundle it is checked against ([BUNDLE-144]).

**[BUNDLE-153]** An evaluator MUST NOT report `E_PROVENANCE_INVALID`, because an evaluator never reads a sidecar ([BUNDLE-145]).

**Example (non-normative).** The envelope below is pretty-printed for readability. The canonical bytes have no whitespace and sort every object's members ([BUNDLE-7] to [BUNDLE-9]). Names come from a sample domain. The example is a small but complete `content`: it has one key, one feature with one policy, and that policy has one rule, so its `requiredFacts` is complete. The `bundleVersion` is a placeholder for the digest.

```json
{
  "bundleVersion": "sha256:<64 lowercase hexadecimal digits>",
  "content": {
    "bundleFormatVersion": "1",
    "specVersion": "0.1",
    "hierarchy": {
      "levels": ["global", "division"],
      "axes": ["seller"],
      "scopes": {
        "division": { "nodes": ["north", "south"], "onUnknownNode": "error", "values": "files" },
        "seller": { "nodes": ["acme"], "onUnknownNode": "error", "values": "files" }
      }
    },
    "entities": {
      "customer": { "belongsTo": [], "hasMany": [] },
      "item": { "belongsTo": [], "hasMany": [] },
      "order": { "belongsTo": ["customer"], "hasMany": ["item"] }
    },
    "facts": {
      "customer.active": { "entity": "customer", "type": { "type": "boolean" } },
      "customer.credit_hold": { "entity": "customer", "type": { "type": "boolean" } }
    },
    "keys": {
      "checkout.pickup.lead_hours": {
        "type": { "type": "integer", "min": 0, "max": 168 },
        "combine": "override",
        "default": 4,
        "settableAt": ["global", "division", "seller"],
        "combineAcrossAxes": "max",
        "kind": "configuration"
      }
    },
    "values": {
      "division": { "south": { "checkout.pickup.lead_hours": 6 } },
      "seller": { "acme": { "checkout.pickup.lead_hours": 8 } }
    },
    "features": {
      "checkout.order_submission": {
        "decidedPer": "order",
        "returns": { "enum": ["ALLOW", "DENY"] },
        "hitPolicy": "FIRST",
        "ifFactMissing": "DENY",
        "bindingSettableAt": ["global", "division"],
        "policies": {
          "credit_check": {
            "settings": {},
            "default": "DENY",
            "requiredFacts": ["customer.active", "customer.credit_hold"],
            "rules": [
              {
                "id": "good-credit",
                "name": "Good credit",
                "when": [
                  { "fact": "customer.active", "op": "eq", "value": true },
                  { "fact": "customer.credit_hold", "op": "eq", "value": false }
                ],
                "then": "ALLOW"
              }
            ]
          }
        },
        "bindings": {
          "global": { "": { "policy": "credit_check", "settings": {} } }
        }
      }
    }
  }
}
```

## 10.7 Determinism

**[DET-1]** Compiling the same project content with the same presets MUST produce identical bundle bytes.

**[DET-2]** The file names, paths and order of the source files, their YAML presentation and their comments MUST NOT affect the bundle bytes, except through the meaning that the MANIFEST, SRC and NS clauses give them.

> Non-normative: a path-mapped values file supplies a scope name and a node ID from its path (the SRC clauses), so moving it to another node's path changes the bundle. YAML presentation here means indentation, block or flow style, key order within a mapping, and quoting that yields the same parsed value.

**[DET-3]** Provenance MUST NOT change the bundle bytes or the digest ([BUNDLE-139]). Rebuilding the same content with different provenance therefore yields the same bundle bytes.

**[DET-4]** The bundle MUST NOT contain a number other than an integer within ±(2^53 − 1).

**[DET-5]** Where this specification requires code point order, an implementation MUST compare strings by Unicode code point, and MUST NOT compare them by UTF-16 code units. The one exception is the member order of JCS objects ([BUNDLE-9]), which uses UTF-16 code units.

> Implementation note: many language runtimes compare strings by UTF-16 code units by default. Code point order therefore needs an explicit comparison. The two orders differ only where a character from U+10000 upwards is compared with a character from U+E000 to U+FFFF.

**[DET-6]** Each `nodes` array ([BUNDLE-39]), each `requiredFacts` array ([BUNDLE-92]) and the conditions of each `when` array, sorted by `fact` ([BUNDLE-96]), MUST be in code point order.

**[DET-7]** Compilers and evaluators MUST NOT depend on locale or time zone. Comparison, sorting and matching are locale-independent. Time-zone names are handled only as the TYPE clauses define.

**[DET-8]** A bundle and a result MUST NOT depend on wall-clock time, on the host environment, or on the iteration order of an unordered collection.

**Order of arrays (non-normative).** The table below shows the order of each array in a bundle and the clause that owns it.

| Array | Order | Clause |
| --- | --- | --- |
| `levels`, `axes` | declared | [BUNDLE-30], [BUNDLE-31] |
| `nodes` | code point | [BUNDLE-39] |
| `settableAt`, `bindingSettableAt` | scope order; level order | [BUNDLE-54], [BUNDLE-81] |
| `belongsTo`, `hasMany`, enum `values`, `returns` enum list, `in` / `all_in` / `any_in` values, `listOf` values | code point for strings; boolean and integer elements are open | [BUNDLE-75], [BUNDLE-90], [BUNDLE-152] |
| `universe` of a `set` type | declared | the TYPE clauses |
| `list` values | source order | [BUNDLE-72] |
| `set` values | universe order | [BUNDLE-74] |
| `requiredFacts` | code point | [BUNDLE-92] |
| `rules` | source order | [BUNDLE-93] |
| `when` | code point, by fact | [BUNDLE-96] |

**Determinism rules owned elsewhere (non-normative).** These rules apply to bundle bytes, and the clause areas named below define them.

- Decimals: canonical form, canonicalisation of source and runtime decimals, and comparisons (the NORM and TYPE clauses).
- Strings: the form that the compiler gives them, exact code point equality, and the ban on runtime normalisation (the NORM clauses).
- Booleans and types: no coercion between types, and strict boolean values (the TYPE and NORM clauses).
- Result orders: each result keeps the orders that its owning clause areas define (the TYPE, MERGE, PROV and RULE clauses).

## Open points

- (none)
