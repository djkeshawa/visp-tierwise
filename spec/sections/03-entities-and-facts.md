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

**[ENTITY-7]** The compiler MUST report `E_INVALID_DECLARATION` when an entity declaration is not a map, including a YAML null, when it has a member other than `belongs_to` and `has_many`, when the value of `belongs_to` or `has_many` is a map, or when a list value has an element that is a map or a list.

**[ENTITY-8]** An empty `belongs_to` or `has_many` list is legal, and means the same as an omitted member. A name repeated within one list, compared after NFC normalisation ([FACT-3]), MUST be reported as `E_DUPLICATE_VALUE`.

**[ENTITY-9]** Each entity named in the `belongs_to` of entity E is a *parent* of E. An entity MAY have several parents.

**[ENTITY-10]** The *ancestors* of E are the entities reachable from E by one or more `belongs_to` edges: the parents of E, and the ancestors of each parent.

**[ENTITY-11]** Each entity named in the `has_many` of entity E is a *child* of E. The children of E are exactly these entities. A child of a child of E is not a child of E.

**[ENTITY-12]** A compiler and an evaluator MUST NOT infer a relationship that the project does not declare. A `has_many` entry on E that names C does not make E a parent or an ancestor of C. A `belongs_to` entry on E that names P does not make E a child of P.

**[ENTITY-13]** A project MAY declare both P in the `belongs_to` of E and E in the `has_many` of P. The compiler MUST NOT report `E_ENTITY_GRAPH` for that pair alone.

> **Example (non-normative).** ADR 0010 gives this pair: `invoice: { has_many: order }` with `order: { belongs_to: [customer, invoice] }`. A decision about `invoice` then has `order.*` as child facts, and a decision about `order` has `invoice.*` as single-value facts.

**[ENTITY-14]** The compiler MUST report `E_ENTITY_GRAPH` when `belongs_to` edges form a cycle. An entity that names itself in `belongs_to` forms a cycle.

**[ENTITY-15]** The compiler MUST report `E_ENTITY_GRAPH` when `has_many` edges form a cycle. An entity that names itself in `has_many` forms a cycle.

**[ENTITY-16]** The compiler MUST report `E_ENTITY_GRAPH` when, for some entity E, one entity is both an ancestor of E and a child of E.

**[ENTITY-17]** The compiler MUST orient every edge from parent to child: for an entity E with `belongs_to: P`, the edge is P → E; for an entity P with `has_many: C`, the edge is P → C. The compiler MUST report `E_ENTITY_GRAPH` when the oriented graph contains a cycle. This covers a cycle through `belongs_to` edges alone, through `has_many` edges alone, or through both kinds ([ENTITY-14] to [ENTITY-16]). The pair in [ENTITY-13] is not a cycle, because both of its edges run from P to E.

> **Example (non-normative).** `A: { has_many: B, belongs_to: C }`, `B: { has_many: C }`, `C: {}`. C is an ancestor of A and a grandchild of A. No `belongs_to` cycle and no `has_many` cycle exists, but the oriented graph has the cycle C → A → B → C, so the compiler reports `E_ENTITY_GRAPH` under [ENTITY-17].

**[ENTITY-18]** An entity name MUST be non-empty and MUST NOT contain `.`. The compiler MUST report a declared entity name that breaks this rule as `E_INVALID_DECLARATION`. A name in a `belongs_to` or `has_many` value that breaks it is not a declared entity, so [ENTITY-6] applies.

**[ENTITY-19]** Where an entity name is expected, a number, a boolean or a null MUST NOT be converted to a string. The compiler MUST report it as `E_TYPE_MISMATCH`. For a number or a boolean, the message MUST suggest quoting the value. The positions where an entity name is expected are the value of `belongs_to` or `has_many`, and each element of a list value.

## 3.2 Fact declarations

**[FACT-1]** Each fact MUST be declared as one entry of a `facts` map. The entry name is the fact name, in the form `<entity>.<attribute>`. The fact is declared on that `<entity>`.

**[FACT-2]** The `<entity>` part of a fact name MUST be a declared entity. If it is not, the compiler MUST report `E_UNKNOWN_ENTITY`.

**[FACT-3]** Declared entity and attribute names MUST be compared after NFC normalisation. A fact name is split at its first `.`: the text before it is the entity part, and the text after it is the attribute part, which MAY contain `.`. A fact name with no `.` MUST be reported as `E_UNKNOWN_ENTITY`. A fact name whose attribute part is empty MUST be reported as `E_INVALID_DECLARATION`. Entity names contain no `.` ([ENTITY-18]), so the entity part always ends at the first `.`.

**[FACT-4]** Facts MUST be declared only in shared files ([NS-3]). A `facts` map in a namespaced file is reported as [NS-4] requires.

**[FACT-5]** Each fact name MUST be declared once. A repeat within one mapping is reported as `E_DUPLICATE_KEY` under [SRC-3]. A repeat across sources is reported as `E_DUPLICATE_DEFINITION` under [SRC-40].

**[FACT-6]** A fact declaration MUST have a `type` member. A declaration without one is reported as `E_TYPE_REQUIRED` under [TYPE-4].

**[FACT-7]** The `type` of a fact MUST be one of `boolean`, `string`, `integer`, `decimal` or `enum`, declared in the grammar of section 4 ([TYPE-5]).

**[FACT-8]** A fact declaration whose `type` is `set`, `list` or `object` MUST be reported as `E_INVALID_DECLARATION`. A type name that section 4 does not define is covered by [TYPE-11].

**[FACT-9]** A fact declaration MAY use the members that [TYPE-7] defines for its type, with the meaning given there; for example, `min` and `max` for `integer`, or `format` for `string`. A FactSet value outside the range that those members define is not a valid value of the type, so [FACT-35] applies.

**[FACT-10]** The `source` member of a fact declaration is documentation only. A compiler and an evaluator MUST NOT use it to validate or to evaluate.

**[FACT-11]** The `source` member of a fact declaration is optional. When it is present, it MUST be a string. Any other value MUST be reported as `E_INVALID_DECLARATION`.

**[FACT-12]** A compiler MUST NOT report an engine error for a declared fact that no rule references. Unused facts are a lint matter ([LINT-37], [LINT-39]).

**[FACT-45]** A fact declaration MAY contain `type`, `source` ([FACT-10], [FACT-11]), `description`, and the type members that [TYPE-7] allows for its `type` ([FACT-9]). The `description` member is informational and is required only by the lint rule `schema/description-required` ([LINT-44]). A fact declaration with any other member MUST be reported as `E_INVALID_DECLARATION`.

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

**[FACT-24]** A FactSet MAY omit `facts`, `children`, or both. An omitted `facts` has no entries, so every single-value fact is absent ([FACT-16]). An omitted `children` has no entry for any child entity, so every child fact is missing ([FACT-17]).

**[FACT-25]** The value of `facts` MUST be a map. A value that is neither a map nor `null` MUST raise `E_FACTS_INVALID`. A `null` value is covered by [FACT-30].

**[FACT-26]** Each entry name of `facts` MUST be a declared fact. Any other entry MUST raise `E_FACTS_INVALID`, whatever its value, including `null`.

**[FACT-27]** Each entry name of `facts` that is a declared fact MUST be a single-value fact for the decision ([FACT-13]). Any other entry MUST raise `E_FACTS_INVALID`, whatever its value, including `null`.

**[FACT-28]** The value of `children` MUST be a map. A value that is neither a map nor `null` MUST raise `E_FACTS_INVALID`. A `null` value is covered by [FACT-30].

**[FACT-29]** Each entry name of `children` MUST be a child of the decided entity ([ENTITY-11]). Any other entry MUST raise `E_FACTS_INVALID`, whatever its value.

**[FACT-30]** A `null` value of `facts`, of `children`, or of a `children` entry MUST raise `E_FACTS_INVALID`. This does not apply to a `null` single-value fact, which is missing under [FACT-16]. A child collection is left out by omission, not by `null`, so an omitted collection is missing under [FACT-17].

**[FACT-31]** The value of each `children` entry MUST be a list. A value that is neither a list nor `null` MUST raise `E_FACTS_INVALID`. A `null` value is covered by [FACT-30].

**[FACT-32]** Each element of a `children` list MUST be a map. Any other element, including `null`, MUST raise `E_FACTS_INVALID`.

**[FACT-33]** Each entry name of a child map MUST be a declared fact of the child entity that names the list holding that map. Any other entry MUST raise `E_FACTS_INVALID`, whatever its value, including `null`.

> **Example (non-normative).** In a child map under `children.item`, each of these entry names raises `E_FACTS_INVALID` under [FACT-33]: an undeclared name; a fact of the decided entity, such as `order.priority`; a fact of an ancestor of the decided entity, such as `customer.credit_hold`; a fact of an ancestor of `item` itself, if `item` had a parent; and a fact of a child of `item`.

**[FACT-34]** A conformance vector runner MUST reject a vector whose FactSet repeats an entry name within one map, and MUST report `E_DUPLICATE_KEY` as [SRC-3] does for sources. An evaluator is not required to detect repeated entry names in a FactSet.

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

- (none)
