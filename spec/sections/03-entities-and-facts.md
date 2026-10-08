# 3. Entities, facts and FactSets

This section defines how entities are declared and related, how facts are declared, which facts a decision may read, when a fact is missing, and how an evaluator validates the caller's FactSet. It owns the clause areas `ENTITY` and `FACT`. It does not define value types, normalisation or comparison (section 4, `TYPE` and `NORM`), which files are shared or namespaced (section 1, `NS`), duplicate detection across sources (section 1, `SRC`), the entity a feature is decided per (section 7, `FEAT`), rule conditions or the missing-fact precheck (section 8, `RULE`), or the check order of `decide` and how errors are raised (section 11, `EVAL`). It references those areas and does not restate them. Subsections 3.1 and 3.2 bind the compiler. Subsection 3.3 binds the evaluator, and binds the compiler through section 8. Subsections 3.4 to 3.7 bind the evaluator.

The terms *parent*, *ancestor*, *child*, *decided entity*, *single-value fact*, *child fact*, *eligible*, *child map*, *absent* and *missing* are listed in the [terminology](../spec.md#terminology), and the clauses below give their precise meaning. An *entry name* is the name of one entry of a map; it is not a configuration key.

## 3.1 Entity declarations

**[ENTITY-1]** Each entity MUST be declared as one entry of the `entity_hierarchy` map. The entry name is the entity name, and the entry value is the entity declaration.

**[ENTITY-2]** Entities MUST be declared only in shared files ([NS-3]). An `entity_hierarchy` in a namespaced file is reported as [NS-4] requires.

**[ENTITY-3]** Each entity name MUST be declared once. A repeat within one mapping is reported as `E_DUPLICATE_KEY` under [SRC-3]. A second `entity_hierarchy`, or a repeat across sources, is reported as `E_DUPLICATE_DEFINITION` under [SRC-39] and [SRC-40].

**[ENTITY-4]** An entity declaration MUST be a map whose members are drawn from `belongs_to` and `has_many`. Each member is optional, and a declaration with neither member, written `{}`, is legal.

**[ENTITY-5]** The value of `belongs_to`, and the value of `has_many`, MUST be either one entity name written as a string, or a list of entity names.

**[ENTITY-6]** Each name in a `belongs_to` or `has_many` value MUST be a declared entity. If it is not, the compiler MUST report `E_UNKNOWN_ENTITY`.

**[ENTITY-7]** *(OPEN)* Which code reports each of the following: an entity declaration that is not a map, including a YAML null (`customer:` with no value); a member other than `belongs_to` and `has_many`; a `belongs_to` or `has_many` value that is neither a string nor a list; and a list element that is not a string? Requirements §6.1 names no code for a malformed entity declaration. The candidate is the structural declaration code that [FEAT-2] and [TYPE-11] also need. [SRC-11] proposes `E_TYPE_MISMATCH` for a non-string scalar where a name is expected.

**[ENTITY-8]** *(OPEN)* Is an empty `belongs_to` or `has_many` list legal, and is a name repeated within one list an error? Candidates: an empty list is legal and means the same as an omitted member; a repeated name is `E_DUPLICATE_VALUE`, by analogy with the proposal in [TYPE-52].

**[ENTITY-9]** Each entity named in the `belongs_to` of entity E is a *parent* of E. An entity MAY have several parents.

**[ENTITY-10]** The *ancestors* of E are the entities reachable from E by one or more `belongs_to` edges: the parents of E, and the ancestors of each parent.

**[ENTITY-11]** Each entity named in the `has_many` of entity E is a *child* of E. The children of E are exactly these entities. A child of a child of E is not a child of E.

**[ENTITY-12]** A compiler and an evaluator MUST NOT infer a relationship that the project does not declare. A `has_many` entry on E that names C does not make E a parent or an ancestor of C. A `belongs_to` entry on E that names P does not make E a child of P.

**[ENTITY-13]** A project MAY declare both P in the `belongs_to` of E and E in the `has_many` of P. The compiler MUST NOT report `E_ENTITY_GRAPH` for that pair alone.

> **Example (non-normative).** ADR 0010 gives this pair: `invoice: { has_many: order }` with `order: { belongs_to: [customer, invoice] }`. A decision about `invoice` then has `order.*` as child facts, and a decision about `order` has `invoice.*` as single-value facts.

**[ENTITY-14]** The compiler MUST report `E_ENTITY_GRAPH` when `belongs_to` edges form a cycle. An entity that names itself in `belongs_to` forms a cycle.

**[ENTITY-15]** The compiler MUST report `E_ENTITY_GRAPH` when `has_many` edges form a cycle. An entity that names itself in `has_many` forms a cycle.

**[ENTITY-16]** The compiler MUST report `E_ENTITY_GRAPH` when, for some entity E, one entity is both an ancestor of E and a child of E.

**[ENTITY-17]** *(OPEN)* Is a cycle that combines both edge kinds, and that [ENTITY-14] to [ENTITY-16] do not catch, an `E_ENTITY_GRAPH` error? Requirements §4.2 says "cycles" without qualification. ADR 0010 §1 lists a cycle through `belongs_to` and a cycle through `has_many`. Recommended: orient every edge from parent to child (P to E for `E: { belongs_to: P }`, and P to C for `P: { has_many: C }`), and report `E_ENTITY_GRAPH` when the oriented graph has a cycle. That rule includes [ENTITY-14] to [ENTITY-16] and keeps the pair in [ENTITY-13] legal.

> **Example (non-normative).** `A: { has_many: B, belongs_to: C }`, `B: { has_many: C }`, `C: {}`. C is an ancestor of A and a grandchild of A. No `belongs_to` or `has_many` cycle exists on its own, and no entity is both an ancestor and a child of the same entity, so [ENTITY-14] to [ENTITY-16] do not apply.

## 3.2 Fact declarations

**[FACT-1]** Each fact MUST be declared as one entry of a `facts` map. The entry name is the fact name, in the form `<entity>.<attribute>`. The fact is declared on that `<entity>`.

**[FACT-2]** The `<entity>` part of a fact name MUST be a declared entity. If it is not, the compiler MUST report `E_UNKNOWN_ENTITY`.

**[FACT-3]** *(OPEN)* What is the identifier grammar for entity names and attribute names? Which code reports a fact name without the `<entity>.<attribute>` form? May an attribute contain `.`, and if so, is the entity part everything before the first `.`? Are entity and fact names NFC-normalised by the compiler ([NORM-1] leaves identifiers to the declaring section)? Candidates: `E_UNKNOWN_ENTITY` for a name with no `.`; entity names contain no `.`, so the entity part ends at the first `.`; names are NFC-normalised, as ADR 0011 §3 does for source strings.

**[FACT-4]** Facts MUST be declared only in shared files ([NS-3]). A `facts` map in a namespaced file is reported as [NS-4] requires.

**[FACT-5]** Each fact name MUST be declared once. A repeat within one mapping is reported as `E_DUPLICATE_KEY` under [SRC-3]. A repeat across sources is reported as `E_DUPLICATE_DEFINITION` under [SRC-40].

**[FACT-6]** A fact declaration MUST have a `type` member. A declaration without one is reported as `E_TYPE_REQUIRED` under [TYPE-4].

**[FACT-7]** The `type` of a fact MUST be one of `boolean`, `string`, `integer`, `decimal` or `enum`, declared in the grammar of section 4 ([TYPE-5]).

**[FACT-8]** *(OPEN)* Which code reports a fact whose `type` is a type of section 4 that [FACT-7] excludes (`set`, `list` or `object`)? A type name that section 4 does not define is covered by [TYPE-11]. The candidate is the code proposed there, `E_TYPE_DECLARATION`.

**[FACT-9]** *(OPEN)* May a fact declaration use the members that [TYPE-7] defines for its type (`min` and `max` for `integer`, `format` for `string`)? [TYPE-5] applies the type grammar to facts, while requirements §4.5 names only the type. Recommended: yes. A FactSet value outside them then fails [FACT-35].

**[FACT-10]** The `source` member of a fact declaration is documentation only. A compiler and an evaluator MUST NOT use it to validate or to evaluate.

**[FACT-11]** *(OPEN)* Is `source` required on every fact declaration, and must it be a string? Requirements §4.5 lists `source` as part of a fact declaration, but no code exists for a missing one. Recommended: optional, and a string when present.

**[FACT-12]** A compiler MUST NOT report an engine error for a declared fact that no rule references. Unused facts are a lint matter ([LINT-37], [LINT-39]).

## 3.3 Facts eligible for a decision

A decision is about one entity, the *decided entity* E, named by the feature's `decided_per` ([FEAT-4]). The caller supplies one FactSet for that decision. Section 8 applies the rules below to rule conditions ([RULE-11], [RULE-12]).

**[FACT-13]** The *single-value facts* for a decision about E are the facts declared on E and on each ancestor of E ([ENTITY-10]).

**[FACT-14]** The *child facts* for a decision about E are the facts declared on each child of E ([ENTITY-11]). Child facts are grouped by child entity.

**[FACT-15]** A fact is *eligible* for a decision about E when it is a single-value fact or a child fact for E. Every other declared fact is not eligible. This includes the facts of a grandchild of E, and the facts of an entity that names E in `has_many` without being an ancestor of E ([ENTITY-12]).

> **Example (non-normative).** With the entity hierarchy below, a decision about `order` has `customer.*` and `order.*` as single-value facts and `item.*` as child facts. `invoice.*` is not eligible: `invoice` declares `has_many: order`, but it is not an ancestor of `order` ([ENTITY-12]). Under [ENTITY-14] to [ENTITY-16], no fact can be both a single-value fact and a child fact for the same decision.
>
> ```yaml
> entity_hierarchy:
>   customer: {}
>   invoice:  { has_many: order }
>   order:    { belongs_to: customer, has_many: item }
>   item:     {}
> ```

## 3.4 Missing facts

In a FactSet, each element of a `children` list is a *child map*. A single-value fact is *absent* when `facts` has no entry with its name. A child fact is *absent* from a child map when that map has no entry with its name. A fact that is absent, or whose entry holds `null`, is *missing*. The clauses below define when a fact is missing. [RULE-42] defines what missing facts cause.

**[FACT-16]** A single-value fact MUST be treated as missing when it is absent from `facts`, or when its entry holds `null`.

**[FACT-17]** Every child fact of child entity C MUST be treated as missing when `children` has no entry for C.

**[FACT-18]** When the `children` entry for C is an empty list, no child fact of C is missing. The collection is present and has no child maps.

**[FACT-19]** When the `children` entry for C holds one or more child maps, a child fact of C MUST be treated as missing when at least one of those child maps has no entry for it or holds `null` for it.

**[FACT-20]** The order of the child maps in a `children` list MUST NOT affect which facts are missing, or whether the FactSet is valid.

**[FACT-21]** A missing fact MUST NOT raise `E_FACTS_INVALID` or any other evaluation error.

## 3.5 FactSet shape

The evaluator validates the FactSet of each `decide` call against the decided entity, at the step that [EVAL-17] sets. Subsections 3.5 to 3.7 define a valid FactSet.

**[FACT-22]** A FactSet MUST be a map. Any other value, including `null`, MUST raise `E_FACTS_INVALID`.

**[FACT-23]** A FactSet MUST NOT have entries other than `facts` and `children`. An entry with any other name MUST raise `E_FACTS_INVALID`.

**[FACT-24]** *(OPEN)* Must `facts` and `children` both be present, or may either be omitted? Recommended: either may be omitted. An omitted `facts` makes every single-value fact absent ([FACT-16]). An omitted `children` has no entry for any child entity ([FACT-17]).

**[FACT-25]** The value of `facts` MUST be a map. A value that is neither a map nor `null` MUST raise `E_FACTS_INVALID`. A `null` value is covered by [FACT-30].

**[FACT-26]** Each entry name of `facts` MUST be a declared fact. Any other entry MUST raise `E_FACTS_INVALID`, whatever its value, including `null`.

**[FACT-27]** Each entry name of `facts` that is a declared fact MUST be a single-value fact for the decision ([FACT-13]). Any other entry MUST raise `E_FACTS_INVALID`, whatever its value, including `null`.

**[FACT-28]** The value of `children` MUST be a map. A value that is neither a map nor `null` MUST raise `E_FACTS_INVALID`. A `null` value is covered by [FACT-30].

**[FACT-29]** Each entry name of `children` MUST be a child of the decided entity ([ENTITY-11]). Any other entry MUST raise `E_FACTS_INVALID`, whatever its value.

**[FACT-30]** *(OPEN)* Is `null` as the value of `facts`, of `children`, or of a `children` entry (as in `children: { item: null }`) malformed, or does it count as omitted? Recommended: malformed, raising `E_FACTS_INVALID`. ADR 0010 §2 makes `null` mean missing only for a fact value, and makes omission, not `null`, mean missing for a child collection.

**[FACT-31]** The value of each `children` entry MUST be a list. A value that is neither a list nor `null` MUST raise `E_FACTS_INVALID`. A `null` value is covered by [FACT-30].

**[FACT-32]** Each element of a `children` list MUST be a map. Any other element, including `null`, MUST raise `E_FACTS_INVALID`.

**[FACT-33]** Each entry name of a child map MUST be a declared fact of the child entity that names the list holding that map. Any other entry MUST raise `E_FACTS_INVALID`, whatever its value, including `null`.

> **Example (non-normative).** In a child map under `children.item`, each of these entry names raises `E_FACTS_INVALID` under [FACT-33]: an undeclared name; a fact of the decided entity, such as `order.priority`; a fact of an ancestor of the decided entity, such as `customer.credit_hold`; a fact of an ancestor of `item` itself, if `item` had a parent; and a fact of a child of `item`.

**[FACT-34]** *(OPEN)* How is a FactSet map that holds the same entry name twice reported? A FactSet reaches an evaluator as a map of the host language, which holds each name once, but serialised input such as JSON or a vector file can repeat a name, and parsers differ. Recommended: an evaluator need not detect repeats, and a conformance vector runner rejects them as [SRC-3] does for sources.

## 3.6 FactSet values

**[FACT-35]** A value that is not `null`, in an entry of `facts` or of a child map, MUST be a valid value of its fact's declared type (section 4). If it is not, the evaluator MUST raise `E_FACTS_INVALID`. This is the section 3 path that [TYPE-74] refers to. A `null` value is not checked against the type.

**[FACT-36]** An evaluator MUST compare entry names and string values in a FactSet as received, under [NORM-3] and [NORM-4]. An entry name names a declared fact or entity only when the two are equal code point for code point.

**[FACT-37]** A string given for an `enum` fact that is not equal to an entry of its `values` under [TYPE-44] MUST raise `E_FACTS_INVALID`.

**[FACT-38]** A number given for a `decimal` fact MUST raise `E_FACTS_INVALID` ([TYPE-25], [NORM-17]).

**[FACT-39]** A string given for a `decimal` fact that matches the runtime syntax of [TYPE-24] MUST be accepted, whether or not it is in the canonical form of [NORM-9]. [NORM-15] governs its canonicalisation.

**[FACT-40]** A string given for a `decimal` fact that does not match the syntax of [TYPE-24] MUST raise `E_FACTS_INVALID`.

**[FACT-41]** An integer given for an `integer` fact outside ±(2^53 − 1) ([TYPE-18]) MUST raise `E_FACTS_INVALID`. `E_INTEGER_RANGE` applies only to source values.

> **Example (non-normative).** Assume a `decimal` fact `order.total` and an `integer` fact `order.lines`, alongside the `boolean` fact `customer.credit_hold` and the `enum` fact `item.stock_type` of sample domain A.
>
> | Entry | Result |
> | --- | --- |
> | `order.total: "+1.50"` | Valid; compared as `"1.5"` |
> | `order.total: "1."`, `".5"`, `"1e3"` | `E_FACTS_INVALID` ([FACT-40]) |
> | `order.total: 1.5` (a number) | `E_FACTS_INVALID` ([FACT-38]) |
> | `customer.credit_hold: "true"` | `E_FACTS_INVALID` ([FACT-35], [NORM-17]) |
> | `item.stock_type: stocked` | `E_FACTS_INVALID` ([FACT-37]); comparison is case-sensitive |
> | `order.lines: 9007199254740992` | `E_FACTS_INVALID` ([FACT-41]) |
> | `order.total: null` | Valid; `order.total` is missing ([FACT-16]) |

## 3.7 Validation outcome

**[FACT-42]** FactSet validation MUST report every failure as `E_FACTS_INVALID`, and no other code. Engine error codes such as `E_UNKNOWN_FACT`, `E_ILLEGAL_FACT_VALUE`, `E_UNKNOWN_ENTITY` and `E_TYPE_MISMATCH` apply to sources, such as rule conditions ([RULE-9], [RULE-18]) and examples ([POL-38], [POL-39]).

**[FACT-43]** An entry that fails validation MUST NOT be ignored, and MUST NOT be treated as missing.

**[FACT-44]** A FactSet that fails validation MUST raise `E_FACTS_INVALID` even when the missing-fact precheck ([RULE-40], [RULE-42]) would find a missing fact. Validation runs before the precheck, and after context validation and feature lookup ([EVAL-17], [EVAL-18]).

> **Example (non-normative).** For a decision about `order` in the hierarchy of 3.3, this FactSet is valid. `item.stock_type` is a child fact because `item` is a child of `order`.
>
> ```yaml
> facts:
>   customer.credit_hold: true
> children:
>   item:
>     - { item.stock_type: STOCKED }
>     - { item.stock_type: DROP_SHIP }
> ```
>
> Each of the following raises `E_FACTS_INVALID`:
> - `facts: { item.stock_type: STOCKED }`, because a child fact is placed among the single-value facts ([FACT-27]);
> - `children: { invoice: [ … ] }`, because `invoice` is not a child of `order` ([FACT-29]);
> - a child map in `children.item` that contains `customer.credit_hold`, because that fact is not declared on `item` ([FACT-33]);
> - `facts: { customer.nickname: null }`, where `customer.nickname` is not declared, because an undeclared entry is invalid whatever its value ([FACT-26]).

## Open points

- `[ENTITY-7]` Codes for a malformed entity declaration (not a map, unknown member, bad `belongs_to`/`has_many` value or element).
- `[ENTITY-8]` Whether empty `belongs_to`/`has_many` lists are legal, and the code for a name repeated in one list.
- `[ENTITY-17]` Whether a cycle that combines both edge kinds is `E_ENTITY_GRAPH`.
- `[FACT-3]` Identifier grammar and NFC normalisation for entity and attribute names; the code for a fact name without the `<entity>.<attribute>` form.
- `[FACT-8]` The code for a fact declaring `set`, `list` or `object`.
- `[FACT-9]` Whether `min`, `max` and `format` are permitted on fact declarations.
- `[FACT-11]` Whether `source` is required, and its shape.
- `[FACT-24]` Whether `facts` and `children` may be omitted from a FactSet.
- `[FACT-30]` Whether `null` as `facts`, `children` or a `children` entry is malformed or omitted.
- `[FACT-34]` How a repeated entry name in a FactSet map is reported.
