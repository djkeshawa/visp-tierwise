# 8. Rule evaluation

This section defines the condition syntax of rules, the compile-time checks on conditions and on the setting references inside them, the meaning of each operator (including empty collections and empty membership lists), the missing-fact precheck over the selected policy, the `FIRST` hit policy, the `DEFAULT` outcome and which rules and conditions the trace records. Subsections 8.1 to 8.5 bind the compiler. Subsections 8.6 to 8.9 bind the evaluator. It does not define feature, policy, setting and binding declarations or binding selection (section 7: `FEAT`, `POL`, `BIND`), the entity graph, the fact kinds and FactSet validation (section 3: `ENTITY`, `FACT`), value types and canonical forms (section 4: `TYPE`, `NORM`), the shape of results, reasons and traces (section 9: `PROV`), or the order in which decide runs its checks (section 11: `EVAL`).

## 8.1 Rules and conditions

**[RULE-1]** A rule's `when` MUST be a mapping from fact names to conditions. [POL-21] requires every rule to declare `when`. Because `when` is a mapping, a rule has at most one condition per fact, and a repeated fact name is reported under [SRC-3].

**[RULE-2]** A rule whose `when` is the empty mapping MUST match.

**[RULE-3]** A condition MUST take one of the forms in Table 8.1. The *operand* of a condition is the value written after the fact name in the `eq` form, and the value written after the operator key in the other forms. A *literal* is an operand, or an element of a list operand, that is not a setting reference ([RULE-4]).

Table 8.1: Condition forms

| Form | Fact kind | Trace `op` | Operand |
| --- | --- | --- | --- |
| `fact: <literal>` or `fact: $name` | single-value ([FACT-13]) | `eq` | one value |
| `fact: { in: [<literal>, ...] }` or `fact: { in: $name }` | single-value ([FACT-13]) | `in` | a list of values |
| `fact: { all_in: [<literal>, ...] }` or `fact: { all_in: $name }` | child ([FACT-14]) | `all_in` | a list of values |
| `fact: { any_in: [<literal>, ...] }` or `fact: { any_in: $name }` | child ([FACT-14]) | `any_in` | a list of values |

The meaning of each form is defined in 8.6.

**[RULE-4]** An operand, or an element of a list operand, that is a string whose first code point is `$` MUST be read as a setting reference (requirements §4.6: "Rules reference settings as `$name`"). The reference names the setting whose name is the rest of the string, which may be empty. A setting reference is not a literal, so 8.3 does not apply to it. Setting references are checked under 8.4.

**[RULE-5]** Spec v0.1 defines no escape for a literal string that begins with `$`. Under [RULE-4] every such string is a setting reference, so a literal that begins with `$` cannot be written. A `$`-string that names no declared setting is reported under [RULE-22].

**[RULE-6]** A condition with malformed syntax MUST be reported as `E_INVALID_DECLARATION`. The cases are: a `when` that is not a mapping ([RULE-1]); an operator key outside Table 8.1 (for example a numeric comparison, which spec v0.x does not define); an operator mapping with more than one operator key, or none; and an operand of the wrong shape: a list or mapping in the `eq` form, or a single value or `null` where Table 8.1 requires a list. A rule with no `when` is a missing required member, covered by [POL-21] and [FEAT-2].

**[RULE-7]** A rule MUST match if and only if every one of its conditions holds.

**[RULE-8]** The evaluator MUST check the conditions of a rule in ascending code-point order of fact name, whatever order the source or the bundle gives them ([ADR 0010](../../docs/adr/0010-entities-facts-and-rules.md) §5). Source order of conditions has no meaning.

## 8.2 Facts and forms

**[RULE-9]** Every fact named in a condition MUST be a declared fact ([FACT-1]). A name that is not a declared fact MUST be reported as `E_UNKNOWN_FACT`, except that a name without the `<entity>.<attribute>` form is reported as [FACT-3] gives. The grammar of fact names is defined in [FACT-3].

**[RULE-10]** A condition fact name that contains `.` and whose entity part is not a declared entity MUST be reported as `E_UNKNOWN_FACT`, under [RULE-9]; `E_UNKNOWN_ENTITY` is not reported for it. A condition fact name with no `.` follows [FACT-3] (ADR 0015).

**[RULE-11]** A condition fact that is out of scope for the decision ([FACT-15]), and whose entity is reachable from the decided entity ([FEAT-4]) through two or more `has_many` edges ([ENTITY-11]), MUST be reported as `E_DEPTH_UNSUPPORTED`, whatever the form of the condition.

**[RULE-12]** A declared condition fact that is out of scope for the decision ([FACT-15]) and is not covered by [RULE-11] MUST be reported as `E_FACT_NOT_IN_SCOPE`. Examples are a fact of a sibling entity, and a fact of an entity that names the decided entity in `has_many` without being its ancestor ([FACT-15], [ENTITY-12]). The same code applies to the fact of a setting ([POL-16]).

**[RULE-13]** A condition of the form `eq` or `in` on a child fact ([FACT-14]) MUST be reported as `E_QUANTIFIER_REQUIRED`.

**[RULE-14]** A condition of the form `all_in` or `any_in` on a single-value fact ([FACT-13]) MUST be reported as `E_QUANTIFIER_NOT_ALLOWED`.

**[RULE-15]** A condition on a `decimal` fact MUST use an equality or membership form: `eq` or `in` on a single-value fact, and `all_in` or `any_in` on a child fact ([RULE-13], [RULE-14]). The membership in [TYPE-27] covers `in`, `all_in` and `any_in`. No ordering form exists, so a decimal condition in any other form is malformed and is reported under [RULE-6]. Every form compares canonical strings ([RULE-37]).

**[RULE-16]** A condition of the form `all_in` or `any_in` on a `decimal` child fact is legal, and it is not an error. Its test is membership by canonical string equality: [RULE-31] or [RULE-32] applies, with the comparison of [RULE-37].

## 8.3 Condition literals

**[RULE-17]** Every literal MUST be a legal value of its fact's declared type ([FACT-7]; [TYPE-12], [TYPE-15] to [TYPE-19], [TYPE-24], [TYPE-25], [TYPE-28] to [TYPE-39], [TYPE-44]). This applies to the operand of the `eq` form and to every element of a literal list.

**[RULE-18]** Subject to [RULE-20], a literal that breaks [RULE-17] MUST be reported as `E_ILLEGAL_FACT_VALUE`. For condition literals, this code takes the place of `E_TYPE_MISMATCH`, which [TYPE-71] gives other source values. Examples are an enum literal that is not one of the fact's declared `values`, the string `yes` for a `boolean` fact ([NORM-17]), and a YAML number for a `decimal` fact ([TYPE-25]).

**[RULE-19]** A `null` literal, written `null` or `~`, and a `null` element of a literal list, MUST be reported under [RULE-18]. `null` is not a value of any type ([TYPE-76]), and no clause gives it a meaning in a condition.

**[RULE-20]** An `integer` literal outside ±(2^53 − 1), used as the operand of a condition on an `integer` fact or as an element of such a list, MUST be reported as `E_INTEGER_RANGE`, as [TYPE-20] gives for integer source values. This code replaces `E_ILLEGAL_FACT_VALUE` under [RULE-18] for that case. An out-of-range integer literal on a fact of another type is reported under [RULE-18]. The runtime bound on `integer` fact values is settled by [FACT-41].

**[RULE-21]** A literal list MUST NOT repeat an element. A repeated element has no effect on any operator in 8.6, but it MUST be reported as `E_DUPLICATE_VALUE`. The same code applies to repeated setting values ([POL-19]).

## 8.4 Setting references

**[RULE-22]** A setting reference MUST name a setting declared by the same policy ([POL-9]). A reference that names no such setting, including one with an empty name, MUST be reported as `E_UNKNOWN_SETTING`.

**[RULE-23]** A setting reference MUST be the whole operand of a condition in one of the forms of Table 8.2. A setting reference anywhere else, including as an element of a literal list, MUST be reported as `E_INVALID_SETTING`.

Table 8.2: Setting references

| Position | Setting kind | Fact of the setting |
| --- | --- | --- |
| `fact: $name` (form `eq`) | `value_of` | the condition's fact |
| `fact: { in: $name }` | `list_of` | the condition's fact |
| `fact: { all_in: $name }` | `list_of` | the condition's fact |
| `fact: { any_in: $name }` | `list_of` | the condition's fact |

**[RULE-24]** The setting that a reference names MUST be of the kind that Table 8.2 gives for its position ([POL-12]). A setting of the other kind MUST be reported as `E_INVALID_SETTING`.

**[RULE-25]** The setting that a reference names MUST be declared for the condition's fact ([POL-12]). A setting declared for any other fact MUST be reported as `E_INVALID_SETTING`, even when the two facts have the same type.

**[RULE-26]** [RULE-11], [RULE-13], [RULE-14] and [RULE-15] MUST apply to a condition whose operand is a setting reference exactly as they apply to the same form with a literal operand.

**[RULE-27]** At evaluation, a setting reference MUST take the resolved value of its setting under [BIND-23].

## 8.5 Several failures in one condition

**[RULE-28]** When one condition breaks more than one check of 8.1 to 8.4, the compiler MUST report one code: the code of the first failing check in the following order.
1. Fact checks: [RULE-9], [RULE-10], [RULE-11], [RULE-12].
2. Form checks: [RULE-6], [RULE-13], [RULE-14].
3. Setting checks: [RULE-22], [RULE-23], [RULE-24], [RULE-25].
4. Literal checks: [RULE-20], [RULE-17], [RULE-18], [RULE-19], [RULE-21].

Within each group, the checks are taken in the order listed. [RULE-20] comes first among the literal checks, so that an out-of-range integer literal reports `E_INTEGER_RANGE`.

## 8.6 Operator semantics

**[RULE-29]** A condition of the form `eq` MUST hold if and only if the fact's value equals the operand value.

**[RULE-30]** A condition of the form `in` MUST hold if and only if the fact's value equals at least one element of the operand list.

**[RULE-31]** On a child collection with at least one child, a condition of the form `all_in` MUST hold if and only if every child's value of the fact equals at least one element of the operand list.

**[RULE-32]** On a child collection with at least one child, a condition of the form `any_in` MUST hold if and only if at least one child's value of the fact equals at least one element of the operand list.

**[RULE-33]** On a child collection that is present and empty ([FACT-18]), a condition of the form `all_in` or `any_in` MUST NOT hold.

**[RULE-34]** A condition of the form `in`, `all_in` or `any_in` whose operand list is empty, whether a literal `[]` or a setting whose resolved value is `[]`, MUST NOT hold. Such a rule can never match. A literal `[]` is flagged by `rules/dead-rule` ([LINT-26]); a setting reference is not ([LINT-28]).

**[RULE-35]** `boolean`, `enum` and `string` values MUST be compared by code-point equality ([NORM-6]). Literal strings are NFC-normalised by the compiler ([NORM-1]). Fact values are compared as received ([NORM-3], [NORM-4]).

**[RULE-36]** `integer` values MUST be compared by numeric value.

**[RULE-37]** `decimal` values MUST be compared as canonical decimal strings ([TYPE-26]). Two decimal values are equal if and only if their canonical strings are equal. A literal is canonicalised by the compiler ([NORM-13]), and a fact value by the evaluator ([NORM-15]). No numeric comparison applies.

**[RULE-38]** An evaluator MUST NOT coerce a fact value or an operand value to another type before comparing them ([NORM-17], [NORM-18]). For example, the boolean `true` never equals the string `"true"`.

**[RULE-39]** Once the precheck has passed ([RULE-45]), evaluating a condition MUST yield true or false, and MUST NOT raise an error.

## 8.7 Missing-fact precheck

The precheck runs after FactSet validation has passed ([EVAL-17]), so a value that fails validation is never counted as missing ([FACT-43]).

**[RULE-40]** Before it checks any rule, the evaluator MUST collect the *precheck set*: every fact named in the `when` mapping of any rule of the selected policy ([BIND-21]). This includes rules that the evaluator would never reach, so the result never depends on rule order ([ADR 0010](../../docs/adr/0010-entities-facts-and-rules.md) §3).

**[RULE-41]** A fact named only by a setting declaration ([POL-12]) MUST NOT be added to the precheck set. [ADR 0010](../../docs/adr/0010-entities-facts-and-rules.md) §3 limits the set to `when` mappings, which narrows "referenced anywhere in the selected policy" in requirements §4.7(6).

**[RULE-42]** If any fact in the precheck set is missing ([FACT-16], [FACT-17], [FACT-19]), the evaluator MUST NOT check any rule.

**[RULE-43]** When [RULE-42] applies, the effect MUST be the feature's `if_fact_missing` ([FEAT-13]). That member is required for every feature ([FEAT-3]).

**[RULE-44]** When [RULE-42] applies, the reason MUST be `MISSING_FACT`, and its `facts` MUST be exactly the missing facts of the precheck set, in ascending code-point order. [PROV-77] gives the shape of `facts`.

**[RULE-45]** When no fact in the precheck set is missing, including when the set is empty, the evaluator MUST treat the precheck as passed and check the rules under 8.8.

## 8.8 Hit policy and default

**[RULE-46]** Under the hit policy `FIRST` ([FEAT-15]), the evaluator MUST check the rules of the selected policy one at a time, in the order of its `rules` list ([POL-8]).

**[RULE-47]** The evaluator MUST stop at the first rule that matches, and MUST NOT check any later rule. That rule is the *winner*.

**[RULE-48]** When there is a winner, the effect MUST be the winner's `then`.

**[RULE-49]** When there is a winner, the reason MUST be `RULE` with the winner's `id` and `name` ([PROV-74]).

**[RULE-50]** When no rule matches, the effect MUST be the selected policy's `default`.

**[RULE-51]** When no rule matches, the reason MUST be `DEFAULT` ([PROV-75]).

A policy with no rules therefore returns its `default` with reason `DEFAULT` whenever the precheck passes, and its precheck set is empty.

## 8.9 Trace

The shape of the trace and of its entries is defined in [PROV-78] to [PROV-81].

**[RULE-52]** The trace MUST contain one entry for each rule that the evaluator checked under 8.8, in the order checked, and no other entry. It therefore lists the rules up to and including the winner ([PROV-83]), every rule for `DEFAULT`, and no rule for `MISSING_FACT` ([PROV-84]).

**[RULE-53]** The `matched` flag of an entry MUST be `true` if and only if the rule matched. Only the winner has `matched: true`.

**[RULE-54]** For a rule that does not match, `failed.fact` MUST be the fact of the first condition, in the order of [RULE-8], that does not hold.

**[RULE-55]** For a rule that does not match, `failed.op` MUST be the trace `op` that Table 8.1 gives for the form of that condition. A condition whose operand is a setting reference has the same `op` as the same form with a literal.

## Examples

**Example (non-normative): mixed basket under the restrictive policy.** The `credit_check` policy of the sample domain is bound at `division: south`, with `allowed_on_credit_hold: [STOCKED, SPECIAL_ORDER]`. The facts are `customer.active: true` and `customer.credit_hold: true`, and the children of `item` have `item.stock_type` values `STOCKED` and `DROP_SHIP`. All three facts in the precheck set are present, so the precheck passes ([RULE-45]). In the first rule, `customer.active` is checked first and holds, and then `customer.credit_hold` fails ([RULE-8]). In the second rule, `customer.active` and `customer.credit_hold` hold, and `item.stock_type`, which comes last in code-point order, fails, because `DROP_SHIP` is not an element of the bound list ([RULE-31]). No rule matches, so the effect is the policy default and the reason is `DEFAULT` ([RULE-50], [RULE-51]).

```yaml
trace:
  - { id: good-credit, matched: false, failed: { fact: customer.credit_hold, op: eq } }
  - { id: credit-hold-allowed-stock, matched: false, failed: { fact: item.stock_type, op: all_in } }
```

**Example (non-normative): missing fact.** The same binding, with the facts `customer.active: true` and one `item` child with `item.stock_type: STOCKED`, and no `customer.credit_hold`. The precheck finds `customer.credit_hold` missing, so no rule is checked ([RULE-42]). The effect is the feature's `if_fact_missing`, the reason is `{ type: MISSING_FACT, facts: [customer.credit_hold] }` ([RULE-44]), and the trace is empty ([RULE-52]). Reordering the rules does not change this result ([RULE-40]).

**Example (non-normative): empty child collection.** The same binding, with the facts `customer.active: true` and `customer.credit_hold: true`, and `children: { item: [] }`. The collection is present and empty, so no fact is missing ([FACT-18]). The first rule fails on `customer.credit_hold`. In the second rule, the `all_in` condition does not hold on an empty collection ([RULE-33]). No rule matches, and the result is `DEFAULT`.

## Open points

- (none)
