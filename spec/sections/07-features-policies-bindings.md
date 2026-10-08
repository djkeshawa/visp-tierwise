# 7. Features, policies and bindings

This section covers feature declarations, the structure and validation of a rules file (its policies, settings, rules, bindings and examples), and the selection of a binding and the resolution of its setting values for one decision. It does not cover the condition syntax, operators and evaluation order of rules (section 8, `RULE`), the scope, admission and context rules that bindings depend on (section 2, `SCOPE` and `CTX`), entities and facts (section 3, `ENTITY` and `FACT`), value types (section 4, `TYPE`), service-sourced levels (section 6, `SVC`), the parsing rules shared by all source files (section 1, `SRC`), or the provenance that reports the selected binding (section 9, `PROV`).

## 7.1 Feature declarations

**[FEAT-1]** A feature declaration MUST declare `decided_per`, `returns`, `hit_policy`, `binding_settable_at` and `if_fact_missing`, and MAY declare `description`, an informational string ([LINT-44]) (ADR 0015). A missing field is reported as [FEAT-2] decides.

**[FEAT-2]** A declaration in this section MUST be reported as `E_INVALID_DECLARATION` when it lacks a required member, has a member that its declaration does not allow, has a member of the wrong shape, or has an empty list where a non-empty list is required. This applies to every feature, rules file, policy, setting, rule, binding and example declaration in this section. Where another clause of this section names a code for the same case, that clause's code applies instead.

**[FEAT-3]** A feature MUST declare `if_fact_missing`. Its effect is returned when the missing-fact precheck fails ([RULE-42], [RULE-43]). A feature without it is reported under [FEAT-2].

**[FEAT-4]** `decided_per` MUST be one entity name. A feature is decided for exactly one entity.

**[FEAT-5]** If `decided_per` names an entity that is not declared, the compiler MUST report `E_UNKNOWN_ENTITY`.

**[FEAT-6]** `returns` MUST take exactly one of two forms: the enum form ([FEAT-7]) or the object form ([FEAT-11]).

**[FEAT-7]** The enum form is `{ enum: [<effect>, ...] }`, optionally with `nullable: true`. The list follows [TYPE-6]. Each listed string is an effect.

**[FEAT-8]** An enum-form list ([TYPE-6]) that is empty, or that has an entry that is not a string, MUST be reported as `E_INVALID_DECLARATION`. An enum-form list that repeats a string MUST be reported as `E_DUPLICATE_VALUE`.

**[FEAT-9]** The effect `null` is legal only when the enum form declares `nullable: true`. It is then an effect in addition to the listed strings ([TYPE-77] and [TYPE-78] leave this meaning of `null` to this section).

**[FEAT-10]** No effect string has a special meaning. A string is an effect only when the enum form lists it.

**[FEAT-11]** The object form is `{ object: <fields> }`, where `<fields>` follows the object declaration grammar of section 4 ([TYPE-5], [TYPE-59], [TYPE-60]). Every effect of this form is a complete object value that is valid under that grammar.

**[FEAT-12]** The object form MUST NOT declare `nullable`. A `nullable` member in the object form is a member that the declaration does not allow, and is reported under [FEAT-2].

**[FEAT-13]** Every effect written in a feature or in a policy MUST be a valid value of `returns`. This covers the feature's `if_fact_missing` ([FEAT-3]), each policy's `default` ([POL-7]) and each rule's `then` ([POL-22]).

**[FEAT-14]** An effect that breaks [FEAT-13] MUST be reported as `E_EFFECT_MISMATCH`.

**[FEAT-15]** `hit_policy` MUST be `FIRST`. Spec v0.x defines no other value. The meaning of `FIRST` is defined by [RULE-46] and [RULE-47].

**[FEAT-16]** A `hit_policy` value other than `FIRST` MUST be reported as `E_INVALID_DECLARATION`.

**[FEAT-17]** `binding_settable_at` MUST be a list of level names. An entry that names no declared level or axis MUST be reported as `E_UNKNOWN_SCOPE`, as for `settable_at` ([KEY-6]).

**[FEAT-18]** `binding_settable_at` MUST include the root level ([SCOPE-10]). If it does not, the compiler MUST report `E_ROOT_BINDING_NOT_ALLOWED`.

**[FEAT-19]** `binding_settable_at` MUST NOT include a service-sourced level (section 6, `SVC`). If it does, the compiler MUST report `E_BINDING_NOT_ALLOWED`.

**[FEAT-20]** An entry of `binding_settable_at` that names a declared axis MUST be reported as `E_BINDING_NOT_ALLOWED`. Only levels carry bindings ([BIND-15]).

**[FEAT-21]** `binding_settable_at` MUST NOT name the same level twice. A repeated entry MUST be reported as `E_DUPLICATE_VALUE`.

## 7.2 Rules file and policies

**[POL-1]** A rules file MUST declare `feature`, `policies` and `bindings`. It MAY declare `examples`. [SRC-15] limits a rules file to these top-level keys.

**[POL-2]** A rules file MUST configure exactly one feature, the one named by `feature`. Its policies, bindings and examples apply to that feature only.

**[POL-3]** A rules file whose `feature` names no declared feature MUST be reported as `E_UNKNOWN_FEATURE`.

**[POL-4]** The name of a rules file MAY differ from the feature it names. A compiler MUST NOT require the two to match. The `feature` entry alone decides which feature the file configures ([POL-2]).

**[POL-5]** A feature MUST be configured by at most one rules file. Two rules files that name the same feature MUST be reported as `E_DUPLICATE_DEFINITION`. This is the case that [SRC-44] refers to.

**[POL-6]** `policies` MUST be a map from policy name to policy.

**[POL-7]** A policy MUST declare `default`, an effect ([FEAT-13]). It is the effect when no rule matches ([RULE-50]).

**[POL-8]** A policy MUST declare `rules`, an ordered list of rules. The list MAY be empty.

**[POL-9]** A policy MAY declare `settings`, a map from setting name to setting declaration. When `settings` is absent, the policy has no settings.

**[POL-10]** Within one rules file, a repeated policy name, setting name, level name under `bindings` or node ID under a level is a repeated mapping key, and is reported as [SRC-3] requires. Repetition across rules files is the subject of [SRC-37] and [POL-5].

**[POL-11]** A setting declaration MUST contain `default`. A declaration without one MUST be reported as `E_INVALID_SETTING`.

**[POL-12]** A setting declaration MUST contain exactly one of `value_of: <fact>` and `list_of: <fact>`. The entry it contains is the setting's form. A declaration with both or neither MUST be reported as `E_INVALID_SETTING`.

**[POL-13]** A setting declaration MUST NOT contain any entry other than `default` and its form. Such an entry MUST be reported as `E_INVALID_SETTING`.

**[POL-14]** The fact named by `value_of` or `list_of` MUST be declared ([FACT-1]). If it is not, the compiler MUST report `E_UNKNOWN_FACT`.

**[POL-15]** A `value_of` setting holds one value of its fact's type. A `list_of` setting holds a list of distinct values of its fact's type. An empty list is a valid `list_of` value.

**[POL-16]** The fact named by `value_of` or `list_of` MUST be an eligible fact of the decided entity ([FACT-15]). A declared fact that is not eligible MUST be reported as `E_FACT_NOT_IN_SCOPE`. When the test of [RULE-11] holds for that fact, the code is `E_DEPTH_UNSUPPORTED` instead. An undeclared fact is reported under [POL-14].

**[POL-17]** The `default` of each setting, and each value that a binding gives a setting ([BIND-9]), MUST be a valid value of the setting's form ([POL-15]) under section 4 (`TYPE`). An invalid value MUST be reported as `E_INVALID_SETTING`, which is the more specific code that [TYPE-71] allows.

**[POL-18]** `null` is never a valid setting value ([TYPE-76]). A `null` default or binding value breaks [POL-17].

**[POL-19]** A `list_of` default or binding value that repeats an element MUST be reported as `E_DUPLICATE_VALUE`, in place of `E_INVALID_SETTING` under [POL-17]. Any other invalid element is still reported as `E_INVALID_SETTING`.

**[POL-20]** A setting name MUST be a non-empty string that contains neither `$` nor `.`. A compiler MUST NFC-normalise setting names before it compares them. The name `policy` is reserved, because it is the policy entry of a binding ([BIND-4]), so a setting MUST NOT use it. A setting declaration whose name is empty, contains `$` or `.`, is not a string, or is `policy` MUST be reported as `E_INVALID_SETTING`.

**[POL-21]** Each rule MUST declare `id`, `name`, `when` and `then`. Rules are checked in their listed order ([RULE-46]).

**[POL-22]** A rule's `when` is a condition mapping ([RULE-1]). A rule's `then` is an effect ([FEAT-13]).

**[POL-23]** A rule's `name` is for display. It MUST NOT affect evaluation.

**[POL-24]** Within one policy, each rule `id` MUST be unique. A repeated `id` MUST be reported as `E_DUPLICATE_RULE_ID`. Rules in different policies MAY share an `id`.

**[POL-25]** A rule's `id` and `name` MUST each be a non-empty string. Any other value, including a YAML integer such as `id: 1`, MUST be reported as `E_INVALID_DECLARATION`. No other grammar or length limit applies to them.

**[POL-26]** Each `$name` reference in a condition is checked by [RULE-22] to [RULE-25] against the settings that the same policy declares ([POL-9]).

**[POL-27]** A rule's `then` MUST NOT be a setting reference. A `then` whose first code point is `$` MUST be reported as `E_INVALID_SETTING`, and not as `E_EFFECT_MISMATCH`. Setting references are defined only for conditions ([RULE-4]).

**Example (non-normative)**: a rules file for the sample domain, with two policies, three bindings (one at the root level `global` and two at the `division` level) and one example. It illustrates [POL-1], [POL-6] to [POL-9], [BIND-2], [BIND-3] and [POL-28].

```yaml
feature: checkout.order_submission

policies:
  credit_check:
    settings:
      allowed_on_credit_hold: { list_of: item.stock_type, default: [] }
    default: DENY
    rules:
      - id: good-credit
        name: Good credit
        when:
          customer.active: true
          customer.credit_hold: false
        then: ALLOW
      - id: credit-hold-allowed-stock
        name: On credit hold, every item is an allowed stock type
        when:
          customer.active: true
          customer.credit_hold: true
          item.stock_type: { all_in: $allowed_on_credit_hold }
        then: ALLOW
  allow_all:
    default: ALLOW
    rules: []

bindings:
  global: { policy: credit_check }
  division:
    north: { policy: allow_all }
    south: { policy: credit_check, allowed_on_credit_hold: [STOCKED, SPECIAL_ORDER] }

examples:
  - name: Credit hold with only allowed stock is allowed
    context: { levels: { division: south } }
    facts: { customer.active: true, customer.credit_hold: true, item.stock_type: [STOCKED, SPECIAL_ORDER] }
    expect: ALLOW
```

## 7.3 Bindings

**[BIND-1]** `bindings` MUST be a map from level name to the bindings written at that level, in the shape that [BIND-2] or [BIND-3] gives for that level.

**[BIND-2]** Under the root level name, the value MUST be one binding, with no node ID ([SCOPE-13]): `bindings: { <root level>: <binding> }`.

**[BIND-3]** Under the name of any other level, the value MUST be a map from node ID to binding: `bindings: { <level>: { <node>: <binding> } }`. The root level is known from `levels` ([SCOPE-10]), so the level name alone decides which shape applies.

**[BIND-4]** A binding MUST be a map that contains a `policy` entry. Its other entries are setting values ([BIND-8]).

**[BIND-5]** The `policy` entry of a binding MUST name a policy declared in the same rules file.

**[BIND-6]** A binding whose `policy` entry names no policy declared in the same rules file MUST be reported as `E_UNKNOWN_POLICY`.

**[BIND-7]** When the `policy` entry of a binding names no declared policy ([BIND-6]), the other entries of that binding are not checked against settings: [BIND-8] and [BIND-9] do not apply to it. The binding is still reported under [BIND-6].

**[BIND-8]** Every entry of a binding other than `policy` MUST name a setting declared by the policy that the binding names. The entry name and the setting names are compared after NFC normalisation ([POL-20]). An entry that names no such setting MUST be reported as `E_UNKNOWN_SETTING`.

**[BIND-9]** The value of each setting entry of a binding MUST satisfy [POL-17].

**[BIND-10]** The node ID of a binding at a non-root level MUST be one that the level admits under [SCOPE-38], [SCOPE-43] and [SCOPE-45]. Those clauses give the codes.

**[BIND-11]** A key of `bindings` that names no declared level or axis is reported under [SCOPE-46] as `E_UNKNOWN_SCOPE`.

**[BIND-12]** A binding MUST appear only at a level listed in its feature's `binding_settable_at`. A binding at a declared level or axis that is not listed MUST be reported as `E_BINDING_NOT_ALLOWED`.

**[BIND-13]** Every feature MUST have a root binding. A feature without one MUST be reported as `E_ROOT_BINDING_MISSING`. This includes a feature that no rules file names, since such a feature has no bindings.

## 7.4 Binding selection and setting values

**[BIND-14]** A decision uses exactly one binding, the selected binding. Its policy and its setting values are the only ones that the decision uses.

**[BIND-15]** Only levels carry bindings. An axis never carries a binding. An axis in a context neither selects a binding nor contributes a setting value.

**[BIND-16]** The candidate bindings for a decision are the root binding and, for each non-root level on the evaluation path ([CTX-19]), the binding written for the node that the context names at that level, if one exists. Omitted levels ([CTX-21]) and skipped scopes ([CTX-15]) are not on the evaluation path, so they contribute no candidate.

**[BIND-17]** The selected binding is the candidate whose level is latest in declared order ([SCOPE-6]). Each candidate is at a different level, so exactly one candidate is selected.

**[BIND-18]** The selected binding replaces all other candidates entirely. No rule and no setting value is merged from a less specific binding.

**[BIND-19]** The root binding is always a candidate, because the root level is on every evaluation path ([CTX-20]) and every feature has a root binding ([BIND-13]). Selection therefore always yields a binding for a compiled feature (see also [EVAL-21]).

**[BIND-20]** An evaluator MUST NOT use a policy's `default` in place of a selected binding. A policy's `default` applies only after its binding has been selected ([POL-7]). Substituting another node is excluded by [CTX-23].

**[BIND-21]** The policy of the selected binding is the policy whose facts are collected for the precheck ([RULE-40]) and whose rules are checked ([RULE-46]).

**[BIND-22]** The result of a decision reports the selected binding and its policy (see [PROV-68] to [PROV-71]).

**[BIND-23]** The resolved value of each setting of the selected policy is the value that the selected binding gives the setting, if it gives one. Otherwise the resolved value is the setting's `default`. Setting references in conditions take this resolved value ([RULE-27]).

**[BIND-24]** A setting that the selected binding omits MUST NOT take a value from any other binding.

## 7.5 Examples

**[POL-28]** `examples` MAY be declared. When declared, it MUST be a list of examples. Each example MUST declare `name`, `context`, `facts` and `expect`, and MAY declare `children` ([POL-32]).

**[POL-29]** An example's `name` identifies the example in findings.

**[POL-30]** Two examples of one rules file MUST NOT have the same `name`. A repeated name MUST be reported as `E_DUPLICATE_VALUE`.

**[POL-31]** The `context` of an example MUST be a context that section 2 accepts, including [CTX-3] (no root level). The code for an example context that breaks this is given by [POL-43].

**[POL-32]** An example that declares `children` beside `facts` is in the explicit form. The explicit form has no shorthand: a list value under `facts` is never shorthand in it, and is reported under [POL-45]. An example that does not declare `children` is in the shorthand form, where a list value under `facts` is read under [POL-33]. The `facts` and `children` entries are the FactSet entries of [FACT-23] and [FACT-24]. A FactSet is not nested under `facts`.

**[POL-33]** In an example's `facts`, a key that names a child fact of the decided entity ([FACT-14]), with a list value, is shorthand. It stands for one child map per list element, each holding that one fact with that element as its value, placed under `children` for that child entity.

**[POL-34]** An empty shorthand list stands for a child collection that is present and empty ([FACT-18]).

**[POL-35]** A child entity that the example gives no child maps, by shorthand or in the explicit form, has an omitted child collection, so its child facts are missing ([FACT-17]).

**[POL-36]** An example MUST NOT contain two shorthand keys for the same child entity. Such a repetition MUST be reported as `E_INVALID_DECLARATION`. Shorthand and explicit child maps cannot appear in one example, because a list under `facts` is never shorthand in the explicit form ([POL-32]).

**[POL-37]** A list value in an example's `facts` is valid only as shorthand under [POL-33]. Any other list value is a FactSet structure violation ([POL-45]).

**[POL-38]** The FactSet built from an example MUST satisfy section 3 (`FACT`). A fact that is not declared MUST be reported as `E_UNKNOWN_FACT`.

**[POL-39]** A fact value in an example that is not a valid value of the fact's type MUST be reported as `E_ILLEGAL_FACT_VALUE`. A `null` value is a missing fact ([FACT-16], [FACT-19]), not an illegal value.

**[POL-40]** `expect` is the effect that the example expects. It MUST be a valid value of `returns` ([FEAT-13]). An invalid value MUST be reported as `E_EFFECT_MISMATCH`.

**[POL-41]** An example passes when the decide operation ([EVAL-8]), given the example's context and FactSet, returns an effect equal to `expect`. Only the effect is compared, because `expect` is an effect ([POL-40]). The reason, the binding and the trace are not compared.

**[POL-42]** An example for which the decide operation returns an effect not equal to `expect` MUST be reported as `E_EXAMPLE_FAILED`. Like every engine error, it has severity `error` ([FIND-15]) and cannot be disabled by a preset or a setting.

**[POL-43]** An example context that breaks [POL-31] MUST be reported with the source code that the same name receives in a source value or binding: `E_UNKNOWN_SCOPE` for a level or axis that is not declared ([SCOPE-46]), and `E_INVALID_NODE_ID` for a malformed node ID ([SCOPE-38]). A context that names the root level ([CTX-3]), or that has a shape error (a member other than `levels` and `axes`, or a map of the wrong shape), MUST be reported as `E_INVALID_DECLARATION`.

**[POL-44]** An example context that names a node ID that a closed catalog does not list MUST be reported as `E_UNKNOWN_NODE`, under either value of `on_unknown_node` ([SCOPE-43]).

**[POL-45]** A FactSet structure violation in an example that [POL-38] and [POL-39] do not cover MUST be reported as `E_INVALID_DECLARATION`. Examples are a list value that is not shorthand ([POL-37]), such as a list on a single-value fact, and a declared fact that is not eligible for the decided entity ([FACT-15]). At runtime the same violation is `E_FACTS_INVALID`.

**[POL-46]** An example whose decision raises an evaluation error ([EVAL-22]) instead of returning an effect MUST be reported as `E_EXAMPLE_FAILED`. The message of that finding MUST include the code of the evaluation error.

**[POL-47]** Validation and compilation MUST run every example ([POL-41]). A failing example is reported as `E_EXAMPLE_FAILED` ([POL-42]). That engine error makes validation fail, so a compiler MUST NOT write a canonical bundle ([FIND-7]). Examples are compile-time-only data and are not written to the bundle ([BUNDLE-109]). No evaluator and no server runs examples. Examples run only when validation has found no other engine finding; otherwise they are not run and nothing is reported for them (ADR 0014).

**[POL-48]** Two object values are equal when they have the same field names and, for each field name, the two field values are equal under the equality that [NORM-19] defines for that field's type. The order of fields is not significant. [POL-41] compares object-valued effects this way.

**[POL-49]** A tool that reports example results, such as a `test` command, MUST base them on the same example checks as validation ([POL-47]), MUST identify each example by its name ([POL-29]), and MUST report a failing example with its code ([POL-42]). This specification defines no commands (see the implementation note in section 13).

## Open points

- (none)
