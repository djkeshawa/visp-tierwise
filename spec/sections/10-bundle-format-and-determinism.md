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

> Non-normative: such a string has no UTF-8 form, and [BUNDLE-10] allows no `\u` escape for it. Whether a source may contain one is an open point of the NORM clauses. The code a loader reports for an escaped one is open in [BUNDLE-116].

## 10.3 Content members

**[BUNDLE-14]** `content` MUST have no member other than `bundleFormatVersion`, `specVersion`, `hierarchy`, `entities`, `facts`, `keys`, `values` and `features`.

**[BUNDLE-15]** `content` MUST have every member that [BUNDLE-14] names.

**[BUNDLE-16]** `bundleFormatVersion` MUST be the string `"1"` in a bundle that conforms to this version of the specification.

**[BUNDLE-17]** `specVersion` MUST be the string `"0.1"` in a bundle that conforms to this version of the specification.

**[BUNDLE-18]** `specVersion` MUST equal the `specVersion` declared by the manifest that the bundle was compiled from (MANIFEST).

**[BUNDLE-19]** A REQUIRED member MUST be present even when it is empty.

**[BUNDLE-20]** An OPTIONAL member MUST be absent when it has no value.

**[BUNDLE-21]** A member MUST NOT use a placeholder value, such as `null`, to stand for an absent value.

**[BUNDLE-22]** *(OPEN)* Are empty entries written under `values` and `bindings`? An empty entry here is a scope entry under `values` or `bindings` with no node entries, or a node entry under `values` with no key values. [BUNDLE-19] already requires every REQUIRED container, including an empty object, so only these entries are in question. Proposal: omit them, so that an entry exists only when it holds at least one value or binding.

**[BUNDLE-23]** `null` MUST NOT appear in `content`, except as an effect that [BUNDLE-24] permits.

**[BUNDLE-24]** A `null` effect MAY appear only as a rule's `then`, a policy's `default` or a feature's `ifFactMissing`, and only where the feature's `returns` is nullable ([FEAT-9]).

> Non-normative: whether the object form of `returns` may be nullable is open in [FEAT-12], and this clause gains that case if [FEAT-12] allows it. A setting value is never `null` ([POL-18]), and a key's default is never `null` ([BUNDLE-56]).

**[BUNDLE-25]** Each keyword member name MUST be spelled as in Table 10.1.

**[BUNDLE-26]** Each member name that Table 10.1 does not list MUST keep the spelling it has in the source, including every user-chosen name.

**[BUNDLE-27]** Each keyword value, such as a strategy name, `FIRST`, `skip`, `deny`, `open` or an operator, MUST keep its source spelling.

**[BUNDLE-28]** *(OPEN)* Should `valueOf` and `listOf` be the member spellings that Table 10.1 gives? Proposal: yes. They are camelCase, as `onSourceFailure` is. Requirements §4.8 already uses `onSourceFailure` for the contribution field, and section 06 uses it for the key member of the bundle ([BUNDLE-59]). The implementation plan's layout uses camelCase for `belongsTo`, `hasMany`, `settableAt` and `decidedPer`, but does not show `valueOf` or `listOf`.

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

**[BUNDLE-41]** An empty closed catalog MUST be written as `[]` wherever the SCOPE clauses allow one. Whether they allow one is an open point of the SCOPE clauses.

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

**[BUNDLE-54]** *(OPEN)* In what order are `settableAt` and `bindingSettableAt` written? Proposal: `settableAt` in scope order, meaning the levels in declared order followed by the axes in declared order. That is the order of contributions, and it matches the examples in the requirements. `bindingSettableAt` holds only levels ([BUNDLE-81]), so it is written in declared level order.

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

**[BUNDLE-64]** Each member of `values` MUST be named by a level or an axis that is not service-sourced, and its value MUST be an object. Whether such a scope has a member when it holds no values is open in [BUNDLE-22].

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

**[BUNDLE-75]** *(OPEN)* In what order are the following arrays written, none of which has an order of its own? They are the `values` of an `enum` type declaration, the `enum` list of a feature's `returns`, the `belongsTo` and `hasMany` arrays, the value of a `listOf` setting (as its `default` or in a binding), and the array `value` of an `in`, `all_in` or `any_in` condition. Proposal: code point order for all of them. None of them carries meaning in its order, and membership tests are order-free. The orders that are settled are not in question: `levels`, `axes`, `rules` and a `set` type's `universe` keep their declared order, `list` and `set` values follow [BUNDLE-72] and [BUNDLE-74], and `settableAt` and `bindingSettableAt` follow [BUNDLE-54].

### 10.3.5 Features, policies and bindings

**[BUNDLE-76]** `features` MUST have one member for each declared feature, and each member MUST be an object with exactly the REQUIRED members `decidedPer`, `returns`, `hitPolicy`, `ifFactMissing`, `bindingSettableAt`, `policies` and `bindings`.

**[BUNDLE-77]** `decidedPer` MUST be the name of a declared entity, the entity that the decision is about (the FEAT clauses).

**[BUNDLE-78]** `hitPolicy` MUST be the string `"FIRST"`, the only hit policy in this version (the FEAT clauses).

**[BUNDLE-79]** `ifFactMissing` MUST be present and MUST be an effect valid for `returns` (the FEAT clauses).

**[BUNDLE-80]** *(OPEN)* Is `if_fact_missing` required in the source? This is [FEAT-3]. If it is, [BUNDLE-79] stands as written. If a source may omit it, the FEAT clauses must define the value that the compiler writes when the source omits it, and this section keeps `ifFactMissing` REQUIRED with that value. Proposal: require it in the source, as `hit_policy` and `binding_settable_at` are required. The missing-fact precheck of the RULE clauses needs an effect to return whenever the selected policy names a fact, and an invented default would be a silent interpretation.

**[BUNDLE-81]** `bindingSettableAt` MUST hold level names only, in the order that [BUNDLE-54] gives (the FEAT and BIND clauses).

**[BUNDLE-82]** *(OPEN)* What is the JSON shape of `returns`? Proposal: `{ "enum": [...] }`, with `"nullable": true` added only when the source declares it; or `{ "object": { <field name>: <type declaration>, ... } }`, where each field declaration follows the object grammar of the TYPE clauses, and its `optional` flag is written only when it is true. Whether the object form may carry `nullable` is [FEAT-12]. The order of the `enum` list is [BUNDLE-75].

**[BUNDLE-83]** `policies` MUST have one member for each policy that the feature declares, and each member MUST be an object with exactly the REQUIRED members `settings`, `default`, `requiredFacts` and `rules`.

**[BUNDLE-84]** `settings` MUST be an object keyed by setting name, and MAY be empty (the POL clauses).

**[BUNDLE-85]** A policy's `default` MUST be an effect valid for `returns` (the POL and FEAT clauses).

**[BUNDLE-86]** Each setting MUST be an object whose members are `default` ([BUNDLE-87]) and exactly one of `valueOf`, a fact name for a single value, and `listOf`, a fact name for a list of distinct values (the POL clauses).

**[BUNDLE-87]** Each setting MUST have a REQUIRED `default` (the POL clauses).

**[BUNDLE-88]** A `valueOf` setting's `default` MUST be a value of the type of its fact (the POL clauses).

**[BUNDLE-89]** A `listOf` value, as a `default` or in a binding, MUST be a JSON array of distinct values of the type of its fact (the POL clauses).

**[BUNDLE-90]** A `listOf` value MUST be in the order that [BUNDLE-75] gives.

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

**[BUNDLE-104]** *(OPEN)* How is a setting reference written in a condition's `value`? Proposal: as the source writes it, the string `$` followed by the setting name, in place of the whole value (the literal for `eq`, or the array for the other operators). The RULE clauses read every operand string that starts with `$` as a setting reference, so no literal in a bundle starts with `$`, and the form is unambiguous. The alternative, an object `{ "setting": "<name>" }`, also cannot be confused with a literal, because fact types are scalar ([BUNDLE-50]), but it departs from the source form.

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

**[BUNDLE-115]** A loader MUST reject a bundle whose `bundleFormatVersion` or `specVersion` it does not support, and MUST report `E_BUNDLE_UNSUPPORTED` ([EVAL-25]). Which values it supports is open in [BUNDLE-137].

**[BUNDLE-116]** *(OPEN)* Which code is reported for input that is not UTF-8, begins with a byte-order mark, is not well-formed JSON, lacks `bundleVersion` or `content`, contains a number that breaks [BUNDLE-11] or [BUNDLE-12] (a non-integer, or an integer outside the range of [DET-4]), contains an escaped unpaired surrogate ([BUNDLE-13]), or is not exactly the serialisation that [BUNDLE-2] requires? [EVAL-24] refers to this clause for the same question, so one resolution answers both. Proposal: `E_BUNDLE_INTEGRITY` for all of these. They are integrity failures of the same kind as [BUNDLE-111] to [BUNDLE-113], and no new code is needed.

**[BUNDLE-117]** The order in which a loader applies the checks of this section, and the code it reports when a bundle fails more than one of them, MUST be the order that [EVAL-14] states.

> Non-normative: the order is open in [EVAL-14], which owns it and records this section's proposal. Until it is settled, no vector depends on it ([VEC-114]).

**[BUNDLE-118]** *(OPEN)* Should a loader reject any member of `content`, and any value, that does not match the members and forms of [BUNDLE-14] to [BUNDLE-108]? Proposal: yes, with `E_BUNDLE_INTEGRITY`.

**[BUNDLE-119]** *(OPEN)* Should a loader reject a `nodes`, `requiredFacts` or `when` array that is not in the order that [DET-6] requires? Proposal: yes, with `E_BUNDLE_INTEGRITY`, because the order is part of the canonical form.

**[BUNDLE-120]** *(OPEN)* Should a loader reject a fact name that a condition's `fact`, a setting's `valueOf` or `listOf`, or `requiredFacts` uses, when no fact of that name is declared? Proposal: yes, with `E_BUNDLE_INTEGRITY`. The sources report such a name as `E_UNKNOWN_FACT` (the RULE and POL clauses), so a bundle that contains one was not produced from valid sources.

**[BUNDLE-121]** *(OPEN)* Should a loader reject an entity name that `decidedPer`, a fact's `entity`, `belongsTo` or `hasMany` uses, when no entity of that name is declared? Proposal: yes, with `E_BUNDLE_INTEGRITY`.

**[BUNDLE-122]** *(OPEN)* Should a loader reject a fact member whose `entity` differs from the prefix of its name? Proposal: yes, with `E_BUNDLE_INTEGRITY`, because the name defines the entity (the FACT clauses).

**[BUNDLE-123]** *(OPEN)* Should a loader reject a binding whose `policy` names no policy declared in the same feature? Proposal: yes, with `E_BUNDLE_INTEGRITY`.

**[BUNDLE-124]** *(OPEN)* Should a loader reject a setting in a binding that the binding's policy does not declare? Proposal: yes, with `E_BUNDLE_INTEGRITY`.

**[BUNDLE-125]** *(OPEN)* Should a loader reject a setting reference in a condition that names no setting of the same policy? Proposal: yes, with `E_BUNDLE_INTEGRITY` (the RULE clauses).

**[BUNDLE-126]** *(OPEN)* Should a loader reject a setting reference in a position that the RULE clauses forbid? The allowed positions are: an `eq` operand that names a `valueOf` setting of the condition's fact; an `in`, `all_in` or `any_in` operand that names a `listOf` setting of the condition's fact; the whole operand and nothing else; and never an element of a literal list. Proposal: yes, with `E_BUNDLE_INTEGRITY`. The sources report these positions as `E_INVALID_SETTING` (the RULE clauses).

**[BUNDLE-127]** *(OPEN)* Should a loader reject a scope name in `settableAt`, `bindingSettableAt`, `values` or `bindings` that the hierarchy does not declare, and a name in `bindings` or `bindingSettableAt` that is not a level? Proposal: yes, with `E_BUNDLE_INTEGRITY` (the SCOPE and KEY clauses).

**[BUNDLE-128]** *(OPEN)* Should a loader reject a `bindingSettableAt` that omits the root level, or that names a service-sourced level? Proposal: yes, with `E_BUNDLE_INTEGRITY`. The sources report these cases under the FEAT and BIND clauses.

**[BUNDLE-129]** *(OPEN)* Should a loader reject a node key under `values` or `bindings` that a closed catalog does not admit, a `""` node key under a non-root scope, or an empty node ID? Proposal: yes, with `E_BUNDLE_INTEGRITY`. ADR 0005 requires compile-time values and bindings to use known closed-catalog IDs under both policies, so a bundle that breaks this was not compiled from valid sources.

**[BUNDLE-130]** *(OPEN)* Should a loader reject a key under `values` that is undeclared, or whose `settableAt` does not include the scope it sits under? Proposal: yes, with `E_BUNDLE_INTEGRITY` (the KEY clauses; the sources report `E_UNKNOWN_KEY` and `E_NOT_SETTABLE_HERE`).

**[BUNDLE-131]** *(OPEN)* Should a loader reject a value that is not valid for the type of its key (under `values`) or of its fact (in a setting or a binding)? Proposal: yes, with `E_BUNDLE_INTEGRITY` (the TYPE and POL clauses).

**[BUNDLE-132]** *(OPEN)* Should a loader reject two rules of one policy with the same `id`? Proposal: yes, with `E_BUNDLE_INTEGRITY`. The sources report this as `E_DUPLICATE_RULE_ID`.

**[BUNDLE-133]** *(OPEN)* Should a loader reject a `nodes` array, or a `listOf` value, that repeats an element? Proposal: yes, with `E_BUNDLE_INTEGRITY`. The sources report a repeated node ID as `E_DUPLICATE_NODE`. The code for a repeated `listOf` value is open in the POL clauses.

**[BUNDLE-134]** *(OPEN)* Should a loader reject a `set` value that repeats a member or that has a member outside the universe of its type? Proposal: yes, with `E_BUNDLE_INTEGRITY`. The sources report a member outside the universe as `E_NOT_IN_UNIVERSE`. The code for a repeated member is open in the TYPE clauses.

**[BUNDLE-135]** *(OPEN)* Should a loader reject a decimal string that is not in canonical form (the NORM clauses)? Proposal: yes, with `E_BUNDLE_INTEGRITY`, since a non-canonical decimal can match a different value at evaluation.

**[BUNDLE-136]** *(OPEN)* Should a loader check that `requiredFacts` equals the distinct facts of the conditions, sorted as [BUNDLE-92] requires? Proposal: yes, with `E_BUNDLE_INTEGRITY`. The digest proves only that the bytes match `content`, not that the compiler derived `requiredFacts` correctly, and a wrong precheck changes the result of a decision (the RULE clauses). The check is cheap. The alternative, not checking, rests on the digest alone, which does not show that the compiler derived `requiredFacts` correctly.

**[BUNDLE-137]** *(OPEN)* Which exact `bundleFormatVersion` and `specVersion` values does this version of the specification support? Proposal: exactly `"1"` and `"0.1"` ([BUNDLE-16] and [BUNDLE-17]). Version 0.1 has no ranges and no compatibility rules, and any other value is `E_BUNDLE_UNSUPPORTED` under [BUNDLE-115]. The declaration of the supported set belongs to the EVAL clauses.

**[BUNDLE-138]** *(OPEN)* Should a loader verify that strings are in NFC form? Proposal: no. A loader does not normalise strings (the NORM clauses), and checking normalisation needs Unicode data that can differ between runtimes. A non-NFC string can only come from a defective compiler, and it matches only its exact code points.

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

**[BUNDLE-149]** *(OPEN)* What is the sidecar's file name, and how is a second sidecar for the same bundle named? The requirements give `<bundle>.provenance.json`, which is ambiguous when the bundle file is `example.json`. Proposal: remove the `.json` extension from the bundle file name first, so that `example.json` has the sidecar `example.provenance.json`. A further sidecar for the same bundle ([BUNDLE-148]) is `example.provenance.<label>.json`, where `<label>` is a lowercase label that the tool chooses and that is unique among that bundle's sidecars.

**[BUNDLE-150]** *(OPEN)* What formats apply to `gitCommit`, `createdAt` and `publisher`? Proposal: `gitCommit` is the full commit identifier in lowercase hexadecimal. `createdAt` is RFC 3339 with an uppercase `T` and a `Z` offset, with seconds present and no fractional seconds. `publisher` is an opaque label. No evaluator reads it, and the sidecar does not authenticate it. Trust in publisher claims is outside this specification.

**[BUNDLE-151]** *(OPEN)* Which tool parses a sidecar, which code does it report for a fault, and is the sidecar parsed strictly? Proposal: the sidecar is parsed under the duplicate-property rule of [BUNDLE-111] and the member rule of [BUNDLE-143], and a fault under those rules, or a mismatch under [BUNDLE-144], is reported by the spec tool `check` (implementation plan item 1.12), and from Phase 5 by the CLI. None of the listed codes fits. `E_BUNDLE_INTEGRITY` is reserved for load bundle ([EVAL-24]), and an evaluator never reads a sidecar ([BUNDLE-145]). Proposal: an ADR adds a code, `E_PROVENANCE_INVALID`, for sidecar faults. The alternative is to amend [EVAL-24] so that these tools may report `E_BUNDLE_INTEGRITY`.

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

**Order of arrays (non-normative).** The table below shows the order of each array in a bundle and the clause that owns it. Only [DET-6] names a code point order in this section.

| Array | Order | Clause |
| --- | --- | --- |
| `levels`, `axes` | declared | [BUNDLE-30], [BUNDLE-31] |
| `nodes` | code point | [BUNDLE-39] |
| `settableAt`, `bindingSettableAt` | scope order; level order | [BUNDLE-54], [BUNDLE-81] |
| `belongsTo`, `hasMany`, enum `values`, `returns` enum list, `in` / `all_in` / `any_in` values, `listOf` values | code point | [BUNDLE-75], [BUNDLE-90] |
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

- [BUNDLE-22]: whether empty entries under `values` and `bindings` are written (proposal: omit them).
- [BUNDLE-28]: spelling of `valueOf` and `listOf` (proposal: camelCase, as Table 10.1 gives).
- [BUNDLE-54]: order of `settableAt` and `bindingSettableAt` (proposal: scope order, and level order for `bindingSettableAt`).
- [BUNDLE-75]: order of arrays without an order of their own (proposal: code point order). [BUNDLE-90] follows it.
- [BUNDLE-80]: whether the source must write `if_fact_missing` ([FEAT-3]). This section follows the answer.
- [BUNDLE-82]: JSON shape of `returns` (proposal given in the clause). Whether the object form may be nullable is [FEAT-12].
- [BUNDLE-104]: encoding of a setting reference in a condition's `value` (proposal: the source string `$name`).
- [BUNDLE-116]: code for malformed, non-UTF-8, non-canonical or incomplete input (proposal: `E_BUNDLE_INTEGRITY`; the same question as [EVAL-24]).
- [BUNDLE-117] depends on [EVAL-14] (OPEN), which owns the order of load checks and records this section's proposal.
- [BUNDLE-118] to [BUNDLE-136]: structural checks that a loader makes beyond the digest, each with a proposal in its clause (`E_BUNDLE_INTEGRITY`). [BUNDLE-136] (whether a loader checks `requiredFacts`) proposes yes.
- [BUNDLE-137]: which exact `bundleFormatVersion` and `specVersion` strings are supported (proposal: `"1"` and `"0.1"`). The declaration belongs to the EVAL clauses.
- [BUNDLE-138]: whether a loader verifies NFC (proposal: no).
- [BUNDLE-149]: sidecar file name, and the name of a further sidecar for the same bundle (proposal given in the clause).
- [BUNDLE-150]: formats of `gitCommit`, `createdAt` and `publisher` (proposal given in the clause).
- [BUNDLE-151]: the tool that parses sidecars and the code it reports (proposal: an ADR adds `E_PROVENANCE_INVALID`; the alternative amends [EVAL-24]).
