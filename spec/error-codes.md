# Error-code catalogue

This catalogue gives one entry for each error code of the specification and for each lint rule of [LINT-1]. It is the source for the explanations that `tierwise explain` prints ([FIND-29], [FIND-30]). Clause IDs in brackets name the clauses that define each entry. The clauses are authoritative. This catalogue only restates their triggers, so when the two differ, the clause wins.

Each entry gives the class, a one-sentence meaning, the conditions under which the code is raised, a minimal example, and a fix. Examples are non-normative. Names such as `level_a`, `key_x` and `entity_a` are generic.

Every engine error has severity `error`, and no lint setting or preset can change it ([FIND-15], [LINT-9]). Lint rules run only when a validation has no engine finding ([LINT-11]).

## Engine errors

The four codes `E_UNKNOWN_FEATURE`, `E_UNKNOWN_KEY`, `E_UNKNOWN_NODE` and `E_UNKNOWN_SCOPE` are also evaluation errors. Their entries are in this group, with class `engine, evaluation` ([EVAL-38]).

### E_AXIS_COMBINE_INVALID
**Class:** engine
**Meaning:** A key's `combine_across_axes` member has a value that is not allowed, or the member is used where it is not allowed.
**Raised when:**
- the value is not one of `axis_wins`, `hierarchy_wins`, `max` or `min` ([AXIS-4]);
- the key is commutative, which is not allowed to set the member ([AXIS-2]);
- the value is `max` or `min` and the key's type is not `integer` ([AXIS-5]);
- the key is settable on no axis ([AXIS-6]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: string
    combine: override
    default: "a"
    settable_at: [level_a, axis_a]
    combine_across_axes: max   # max is allowed only for integer keys
```
**Fix:** Use one of the four values, and use `max` or `min` only on integer keys. Remove the member from keys that are settable on no axis or that are commutative.

### E_BINDING_NOT_ALLOWED
**Class:** engine
**Meaning:** A binding is written at a scope that its feature does not allow.
**Raised when:**
- a binding sits at a declared level or axis that is not listed in the feature's `binding_settable_at` ([BIND-12]);
- `binding_settable_at` lists a service-sourced level ([FEAT-19]);
- `binding_settable_at` lists a declared axis ([FEAT-20]).

**Minimal example (non-normative):**
```yaml
feature: feature_x          # binding_settable_at lists only root_level
bindings:
  root_level:
    policy: policy_a
  level_b:                  # level_b is not listed
    node_x:
      policy: policy_a
```
**Fix:** Move the binding to a listed level, or add the level to `binding_settable_at` if it is a non-service level.

### E_DEFAULT_NOT_ALLOWED
**Class:** engine
**Meaning:** A commutative key declares a `default`.
**Raised when:**
- a key with a commutative strategy declares `default` ([MERGE-4], [MERGE-1]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: integer
    combine: max                  # max is commutative ([MERGE-1])
    default: 0
    settable_at: [level_a]
```
**Fix:** Remove `default`. A commutative key has an identity instead ([MERGE-5]).

### E_DENY_BOUND_REQUIRED
**Class:** engine
**Meaning:** A key with `deny` in effect at a service-sourced level, using the `min` strategy, has no lower bound.
**Raised when:**
- the key is settable at one or more service-sourced levels, has `deny` in effect, has the `min` strategy, and its `integer` declaration has no `min` member ([SVC-12]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: integer
    combine: min
    settable_at: [level_b]        # level_b is service-sourced
    on_source_failure: deny       # no min member
```
**Fix:** Add a `min` member to the integer declaration.

### E_DENY_NOT_NARROWING
**Class:** engine
**Meaning:** A key with `deny` in effect at a service-sourced level uses a strategy that is not narrowing.
**Raised when:**
- the key is settable at one or more service-sourced levels, has `deny` in effect, and has a strategy other than `all`, `intersect` or `min` ([SVC-11], [MERGE-2]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: boolean
    combine: any                  # any is not narrowing ([MERGE-2])
    settable_at: [level_b]
    on_source_failure: deny
```
**Fix:** Use a narrowing strategy, or use `skip` as the source-failure behaviour.

### E_DEPTH_UNSUPPORTED
**Class:** engine
**Meaning:** A condition or setting uses a fact that is reachable only through too many `has_many` links.
**Raised when:**
- a condition fact is out of scope for the decision and its entity is reachable from the decided entity through two or more `has_many` links ([RULE-11]);
- a `value_of` or `list_of` fact is not an eligible fact of the decided entity, because of its depth ([POL-16]).

**Minimal example (non-normative):** A feature decided per `entity_a`, with a rule that tests `entity_d.fact_x`, where `entity_d` is a grandchild of `entity_a` through `has_many` links.
**Fix:** Move the fact to a shallower entity, or decide the feature per the entity that owns the fact.

### E_DUPLICATE_DEFINITION
**Class:** engine
**Meaning:** A definition that may be declared only once is declared again.
**Raised when:**
- a second `decision_hierarchy` or `entity_hierarchy` is declared in any source, presets included ([SRC-39]);
- an entity, fact, key or feature is declared twice across sources, even when the two declarations are identical ([ENTITY-3], [FACT-5], [KEY-1], [SRC-40], [SRC-41]);
- two kinds, or two naming patterns, with the same name are declared in the project's own sources ([SRC-43]);
- two rules documents name the same feature ([POL-5], [SRC-44]).

A kind or naming pattern that a preset defines and the project replaces is a replacement, not this error ([MANIFEST-32], [LINT-49]).

**Minimal example (non-normative):**
```yaml
# schema/a.yaml
keys:
  key_x: { type: boolean, combine: all, settable_at: [level_a] }
# schema/b.yaml
keys:
  key_x: { type: boolean, combine: all, settable_at: [level_a] }
```
**Fix:** Keep one declaration and delete the other, or rename one of the two names.

### E_DUPLICATE_KEY
**Class:** engine
**Meaning:** A mapping contains the same key twice.
**Raised when:**
- a mapping at any depth, in any source file or the manifest, repeats a key; two string keys are the same when they are equal after NFC normalisation ([SRC-3], [SRC-4]);
- the finding is located at the second occurrence ([SRC-9]);
- a catalog JSON file repeats a property name ([SCOPE-30]);
- an entity or fact name repeats within one mapping ([ENTITY-3], [FACT-5]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x: { type: boolean, combine: all, settable_at: [level_a] }
  key_x: { type: boolean, combine: all, settable_at: [level_a] }
```
**Fix:** Delete the repeated entry. Keys that differ only by Unicode normalisation are also the same key.

### E_DUPLICATE_NODE
**Class:** engine
**Meaning:** Two node IDs in one catalog are the same ID.
**Raised when:**
- two node IDs in one catalog are equal after NFC normalisation ([SCOPE-42]).

**Minimal example (non-normative):**
```yaml
levels: [root_level, level_a]
scopes:
  level_a:
    nodes: [node_x, node_x]
```
**Fix:** Remove the repeated node ID.

### E_DUPLICATE_RULE_ID
**Class:** engine
**Meaning:** Two rules of one policy have the same `id`.
**Raised when:**
- a rule `id` repeats within one policy ([POL-24]). Rules in different policies may share an `id`.

**Minimal example (non-normative):**
```yaml
policies:
  policy_a:
    rules:
      - { id: rule_1, name: "first", when: {}, then: effect_a }
      - { id: rule_1, name: "second", when: {}, then: effect_b }
```
**Fix:** Give each rule of the policy a unique `id`.

### E_DUPLICATE_VALUE
**Class:** engine
**Meaning:** A value, or an item in a list, is given twice where only one is allowed.
**Raised when:**
- two values for one key at one scope and node come from any sources ([KEY-12], [SRC-42]);
- a scope name repeats in a key's `settable_at` ([KEY-7]);
- a name repeats in one `belongs_to` or `has_many` list ([ENTITY-8]);
- a level repeats in `binding_settable_at` ([FEAT-21]);
- a string repeats in an enum-form list, or in the `values` or `universe` of a declaration, or a set value repeats a member ([FEAT-8], [TYPE-52]);
- a literal list in a condition repeats an element ([RULE-21]);
- a `list_of` default or binding value repeats an element ([POL-19]);
- two examples of one rules file have the same `name` ([POL-30]).

**Minimal example (non-normative):** Two values files that each write `key_x` at the root level.
```yaml
# values/root_a.yaml
key_x: true
# values/root_b.yaml
key_x: false
```
**Fix:** Keep one value for each key and scope, or move one of the two to another scope.

### E_EFFECT_MISMATCH
**Class:** engine
**Meaning:** An effect is used that is not a value of the feature's `returns`.
**Raised when:**
- a feature's `if_fact_missing`, a policy's `default` or a rule's `then` is not a value of `returns` ([FEAT-14], [FEAT-13]);
- an example's `expect` is not a value of `returns` ([POL-40]).

A `then` that is a setting reference is reported as `E_INVALID_SETTING` instead ([POL-27]).

**Minimal example (non-normative):**
```yaml
# schema file
features:
  feature_x:
    returns: { enum: [effect_a, effect_b] }
    # other feature members omitted
# rules file
feature: feature_x
policies:
  policy_a:
    default: effect_c           # not in returns
```
**Fix:** Use a value that `returns` lists, or add the value to `returns`.

### E_ENTITY_GRAPH
**Class:** engine
**Meaning:** The entity relations form a cycle, or one entity is both an ancestor and a child of another.
**Raised when:**
- `belongs_to` edges form a cycle, including an entity that names itself ([ENTITY-14]);
- `has_many` edges form a cycle, including an entity that names itself ([ENTITY-15]);
- for some entity E, one entity is both an ancestor of E and a child of E ([ENTITY-16]);
- the graph whose edges all run from parent to child (P → E for `belongs_to: P` on E, P → C for `has_many: C` on P) contains a cycle, including one that mixes both kinds of edge ([ENTITY-17]).

A pair of entities that name each other in opposite lists, which is one edge in each direction, is not a cycle by itself ([ENTITY-13]).

**Minimal example (non-normative):**
```yaml
entity_hierarchy:
  entity_a: { belongs_to: [entity_b] }
  entity_b: { belongs_to: [entity_a] }
```
**Fix:** Remove one edge that closes the cycle or the ancestor/child conflict ([ENTITY-14] to [ENTITY-17]).

### E_EXAMPLE_FAILED
**Class:** engine
**Meaning:** An example of a rules file does not produce its expected effect.
**Raised when:**
- the decision for an example returns an effect that is not equal to `expect` ([POL-42]);
- the decision for an example raises an evaluation error instead of returning an effect ([POL-46]).

Validation runs every example ([POL-47], [FIND-2]). The code cannot be disabled ([POL-42]).

**Minimal example (non-normative):** `policy_a` has one rule that returns `effect_a` for every FactSet. An example in the same file sets `expect: effect_b`.
**Fix:** Correct the `expect` value, or correct the rules, the policy default or the example's FactSet.

### E_FACT_NOT_IN_SCOPE
**Class:** engine
**Meaning:** A condition or a setting uses a declared fact that is not in the scope of the decision.
**Raised when:**
- a condition fact is declared, is out of scope for the decision, and is not covered by [RULE-11] ([RULE-12], [FACT-15]);
- a `value_of` or `list_of` fact is declared but is not an eligible fact of the decided entity ([POL-16]).

An eligible fact is a single-value fact of the decided entity or one of its ancestors, or a child fact of the decided entity ([FACT-15]).

**Minimal example (non-normative):** A feature decided per `entity_a` has a rule with `when: { entity_d.fact_x: true }`, where `entity_d` is unrelated to `entity_a`.
**Fix:** Use a fact of the decided entity, one of its ancestors, or one of its children.

### E_ILLEGAL_FACT_VALUE
**Class:** engine
**Meaning:** A literal in a condition, or a fact value in an example, is not a valid value of the fact's type.
**Raised when:**
- a literal in a condition breaks the literal check of [RULE-17] and is not an integer range case ([RULE-18]);
- a fact value in an example is not valid for the fact's type ([POL-39]).

For condition literals this code takes the place of `E_TYPE_MISMATCH` ([RULE-18]). An out-of-range integer literal on an `integer` fact is `E_INTEGER_RANGE` ([RULE-20]). Other source values that fail validation take the codes of [TYPE-71].

**Minimal example (non-normative):**
```yaml
policies:
  policy_a:
    rules:
      - { id: rule_1, name: "flag", when: { entity_a.fact_flag: "true" }, then: effect_a }
# entity_a.fact_flag is declared as boolean; "true" is a string
```
**Fix:** Write a literal that is valid for the fact's type, such as `true` without quotes.

### E_INTEGER_RANGE
**Class:** engine
**Meaning:** An integer value or bound is outside the range that the engine supports.
**Raised when:**
- an `integer` source value is outside ±(2^53 − 1) ([TYPE-20]);
- a `min` or `max` of an `integer` declaration is outside that range ([TYPE-22]);
- an integer literal in a condition on an `integer` fact, or in a list for such a fact, is outside that range ([RULE-20]);
- both this code and `E_OUT_OF_RANGE` apply, and then both are reported ([TYPE-79]).

At runtime an integer fact outside the range is reported as `E_FACTS_INVALID` ([FACT-41]), not with this code.

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: integer
    combine: max
    settable_at: [level_a]
values:
  level_a: { node_x: { key_x: 9007199254740993 } }
```
**Fix:** Use an integer within ±(2^53 − 1).

### E_INVALID_DECLARATION
**Class:** engine
**Meaning:** A declaration is malformed, or a value in an example or a declaration has the wrong structure.
**Raised when:**
- a type declaration is malformed, a `min` is greater than a `max`, or an `enum` or `set` list is empty ([TYPE-11], [TYPE-14], [TYPE-42]);
- a key lacks `combine` or `settable_at`, has a member that [KEY-3] does not allow, has a `settable_at` that is not a list of names, or has a `combine` that names no strategy of [MERGE-1] ([KEY-3], [KEY-5]);
- a key's `settable_at` is empty ([KEY-7]);
- a key's `on_source_failure` is neither `skip` nor `deny` ([KEY-5], [SVC-4]);
- an entity declaration is not a map, or an entity name is empty or contains `.` ([ENTITY-7], [ENTITY-18]);
- a fact `source` is not a string, a fact's type is `set`, `list` or `object`, or a fact declaration has a member that [FACT-45] does not allow ([FACT-11], [FACT-8], [FACT-45]);
- a fact name has an empty attribute part ([FACT-3]);
- a feature lacks a required member, or its `hit_policy` is not `FIRST` ([FEAT-2], [FEAT-16]);
- a feature's enum-form `returns` list is empty or has an entry that is not a string ([FEAT-8]);
- a rule's `id` or `name` is not a non-empty string, or a condition has malformed syntax ([POL-25], [RULE-6]);
- a schema file has a top-level key that is not allowed ([SRC-16]);
- a naming pattern is malformed, or its `category` is not `key`, `enum_value`, `node_id` or `feature` ([LINT-14], [LINT-15], [LINT-17]);
- an example's FactSet has a structure violation ([POL-36], [POL-45]);
- an example's context names the root level or has a shape error ([POL-43]).

The list is not exhaustive. The cited clauses are authoritative. A kind's invalid `on_source_failure` is `E_KIND_DEFINITION` ([SVC-4]). A fact name with no `.` is `E_UNKNOWN_ENTITY` ([FACT-3]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: integer
    min: 5
    max: 1                      # min greater than max
    combine: max
    settable_at: [level_a]
```
**Fix:** Correct the declaration to the form that the cited clause defines.

### E_INVALID_NODE_ID
**Class:** engine
**Meaning:** A node ID written in a source does not have a valid form.
**Raised when:**
- a source node ID breaks the form of [SCOPE-33] or [SCOPE-34], or contains an unpaired surrogate ([SCOPE-37], [SCOPE-38], [SCOPE-39]), including a string entry of an inline `nodes` list ([SCOPE-20]);
- a node ID in an example's context is malformed ([POL-43]).

At runtime an invalid node ID in a context is reported as `E_CONTEXT_INVALID` ([CTX-6]).

**Minimal example (non-normative):**
```yaml
levels: [root_level, level_a]
scopes:
  level_a:
    nodes: [""]                 # an empty node ID breaks SCOPE-33
```
**Fix:** Use a node ID in the form that [SCOPE-33] and [SCOPE-34] define.

### E_INVALID_SETTING
**Class:** engine
**Meaning:** A setting declaration, a setting value or a setting reference is invalid.
**Raised when:**
- a setting declaration has no `default` ([POL-11]);
- a setting declaration has neither or both of `value_of` and `list_of` ([POL-12]), or has another entry besides `default` and its form ([POL-13]);
- a `default` or a binding's value for a setting is not valid for the setting's form ([POL-17], [BIND-9]);
- a setting name is empty, is not a string, contains `$` or `.`, or is the reserved name `policy` ([POL-20]);
- a rule's `then` is a setting reference ([POL-27]);
- a setting reference is in a position, of a kind, or for a fact that the table of [RULE-23] to [RULE-25] does not allow.

A duplicate in a `list_of` value is `E_DUPLICATE_VALUE` ([POL-19]).

**Minimal example (non-normative):**
```yaml
policies:
  policy_a:
    settings:
      setting_a:
        value_of: entity_a.fact_x   # no default
```
**Fix:** Add a `default` of the right form, and use a setting reference only where the table of [RULE-23] allows.

### E_KIND_DEFINITION
**Class:** engine
**Meaning:** A kind definition is internally inconsistent or uses a value that is not allowed.
**Raised when:**
- a kind with `narrowing_only: true` allows a strategy that is not narrowing ([KIND-5]);
- a kind with `require_default: true` allows a commutative strategy ([KIND-10]);
- a kind's `naming` member is not a string, or does not name a declared pattern of category `key` ([KIND-12], [LINT-16]);
- a kind's `on_source_failure` is neither `skip` nor `deny` ([SVC-4]);
- a kind definition has a member that [KIND-3] does not allow, or a `strategies` list with a non-strategy item ([KEY-5]).

**Minimal example (non-normative):**
```yaml
kinds:
  kind_a:
    narrowing_only: true
    strategies: [union]          # union is not narrowing ([MERGE-2])
```
**Fix:** Correct the kind definition so that its members are consistent with each other.

### E_KIND_VIOLATION
**Class:** engine
**Meaning:** A key that uses a kind breaks one of the kind's constraints.
**Raised when:**
- a key uses a strategy that is not in the kind's `strategies` ([KIND-4]);
- a kind with `narrowing_only: true` has an authored value equal to the identity of the key's strategy ([KIND-6]);
- a kind with `require_default: true` is used by a key that declares no `default` ([KIND-8]);
- a key's `on_source_failure` differs from its kind's value ([KIND-11], [SVC-5]).

**Minimal example (non-normative):**
```yaml
kinds:
  kind_a: { strategies: [override], require_default: true }
keys:
  key_x:
    kind: kind_a                 # no default declared
    type: integer
    combine: override
    settable_at: [level_a]
```
**Fix:** Make the key comply with its kind, or change the key's kind.

### E_MANIFEST
**Class:** engine
**Meaning:** The manifest is missing, malformed or names a source that cannot be used.
**Raised when:**
- the project root has no manifest, or the manifest root is not a mapping ([MANIFEST-2]), or the manifest holds more than one YAML document ([SRC-6]);
- the manifest has a YAML syntax error ([SRC-5]), or is not valid UTF-8 or starts with a byte-order mark ([SRC-10]);
- the manifest has a top-level key that is not allowed ([MANIFEST-3]);
- `specVersion` is missing or is not a string ([MANIFEST-4]), or names no version the compiler implements ([MANIFEST-5]);
- one of the manifest members has the wrong shape: `sources` ([MANIFEST-10], [MANIFEST-11]), a path that does not resolve in the project root ([MANIFEST-12]), inline `schema`, `values` or `rules` ([MANIFEST-14] to [MANIFEST-16]), `extends` ([MANIFEST-19], [MANIFEST-20], [MANIFEST-26]), `lint` or `lint.rules` ([MANIFEST-37], [MANIFEST-38]), or `namespaces` ([NS-1], [NS-2], [NS-9]);
- a values entry or a namespace schema path names a file that cannot be used ([SRC-17]);
- the manifest's lint setting names no rule and is not a valid wildcard ([LINT-10] (b), (c)).

**Minimal example (non-normative):**
```yaml
specVersion: 0.1      # a plain 0.1 is a YAML float, not a string
```
**Fix:** Quote the value, for example `specVersion: "0.1"`, and correct the member to the shape that the cited clause defines.

### E_MISSING_AXIS_COMBINE
**Class:** engine
**Meaning:** An order-dependent key that is settable on an axis has no `combine_across_axes`.
**Raised when:**
- an order-dependent key is settable at least on one axis and declares no `combine_across_axes` ([AXIS-3]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: integer
    combine: override             # order-dependent strategy
    default: 0
    settable_at: [level_a, axis_a]
```
**Fix:** Add `combine_across_axes` with a value that [AXIS-4] allows.

### E_MISSING_DEFAULT
**Class:** engine
**Meaning:** An order-dependent key has no starting value.
**Raised when:**
- an order-dependent key declares no `default` and has no root value ([MERGE-7]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: integer
    combine: override             # order-dependent, no default, no root value
    settable_at: [level_a]
```
**Fix:** Declare a `default`, or write a root value for the key.

### E_MISSING_SOURCE_FAILURE
**Class:** engine
**Meaning:** A key that is settable at a service-sourced level has no source-failure behaviour.
**Raised when:**
- a key is settable at a service-sourced level and has no `on_source_failure` in effect, neither on the key nor through its kind ([SVC-2], [KEY-8], [KIND-9]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: boolean
    combine: all
    settable_at: [level_b]        # level_b is service-sourced; no on_source_failure
```
**Fix:** Declare `on_source_failure` as `skip` or `deny`, on the key or on its kind.

### E_NAMESPACE_VIOLATION
**Class:** engine
**Meaning:** A namespaced schema file contains something that its namespace does not allow, or a shared file declares a namespace.
**Raised when:**
- a namespaced file has a top-level key other than `keys`, `features` and `namespace`, reported under this code and not under [SRC-16] ([NS-4]);
- a key or feature in a namespaced file is outside that file's namespace ([NS-5]);
- the `namespace` member of a namespaced file is missing, or does not match the name under which `namespaces` lists it ([NS-7]);
- a shared file, including inline `schema` content in the manifest, contains the `namespace` key ([NS-7]).

**Minimal example (non-normative):**
```yaml
# schema file listed under namespaces: ns_a
namespace: ns_a
facts:                          # shared definitions are not allowed here
  entity_a.fact_x: { type: boolean }
```
**Fix:** Move shared definitions to a shared file, and keep the namespaced file to `keys`, `features` and `namespace`.

### E_NOT_IN_UNIVERSE
**Class:** engine
**Meaning:** A member of a `set` value is not in the `universe` of its type.
**Raised when:**
- a member of a source `set` value is not an entry of the `universe` ([TYPE-48]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: set
    universe: [member_a, member_b]
    combine: union
    settable_at: [level_a]
values:
  level_a: { node_x: { key_x: [member_c] } }  # member_c is not in universe
```
**Fix:** Use members of the universe, or add the member to the universe.

### E_NOT_SETTABLE_HERE
**Class:** engine
**Meaning:** A key has a value at a scope that is not in its `settable_at`.
**Raised when:**
- a value for a key is written at a declared scope that is not in the key's `settable_at`. The root level counts as a scope ([KEY-11]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: boolean
    combine: all
    settable_at: [level_a]
values:
  level_b: { node_x: { key_x: true } }   # level_b is not in settable_at
```
**Fix:** Write the value at a listed scope, or add the scope to `settable_at`.

### E_OUT_OF_RANGE
**Class:** engine
**Meaning:** An integer source value is outside its declared bounds.
**Raised when:**
- an `integer` source value is outside its declared `min` or `max` ([TYPE-21]);
- both this and `E_INTEGER_RANGE` apply to one value, and then both are reported ([TYPE-79]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: integer
    min: 0
    max: 10
    combine: max
    settable_at: [level_a]
values:
  level_a: { node_x: { key_x: 11 } }
```
**Fix:** Use a value within the declared bounds, or change the bounds.

### E_PARSE
**Class:** engine
**Meaning:** A source file other than the manifest, or a catalog file, cannot be parsed.
**Raised when:**
- a source file or preset other than the manifest has a YAML syntax error ([SRC-5]);
- such a file holds more than one YAML document, has a root node that is not a mapping, or is empty ([SRC-6]);
- such a file is not valid UTF-8, or starts with a byte-order mark ([SRC-10]);
- a catalog file is not well-formed JSON, is not valid UTF-8 or starts with a byte-order mark ([SCOPE-30]).

For the manifest, the same faults are `E_MANIFEST` ([SRC-5], [MANIFEST-2]).

**Minimal example (non-normative):** A values file whose root is a list.
```yaml
- key_x
- key_y
```
**Fix:** Write the file as a single YAML mapping in UTF-8, without a byte-order mark.

### E_PRESET
**Class:** engine
**Meaning:** A preset reference, a preset file or a lint setting in a preset is invalid.
**Raised when:**
- a built-in preset reference names no built-in preset ([MANIFEST-22]);
- a path preset reference names no file ([MANIFEST-24]), or a URL preset cannot be retrieved ([MANIFEST-27]), or its content does not match its digest ([MANIFEST-28]);
- `extends` names the same preset twice ([MANIFEST-30]);
- a preset contains `extends` ([MANIFEST-35]), or has a top-level key that is not allowed ([MANIFEST-36]);
- a lint setting names an engine error code, in a preset or in the manifest ([LINT-8]);
- a lint setting in a preset names no rule, or has an invalid severity ([LINT-10] (b), (c));
- a naming pattern in a preset is malformed or has an invalid form ([LINT-14], [LINT-17]).

**Minimal example (non-normative):**
```yaml
extends:
  - "tierwise:unknown_preset"    # not a built-in preset name
```
**Fix:** Use a built-in name, a path that exists, or a preset that follows the form of the cited clause. Lint settings name only lint rules.

### E_QUANTIFIER_NOT_ALLOWED
**Class:** engine
**Meaning:** A quantifier is used on a single-value fact.
**Raised when:**
- an `all_in` or `any_in` condition is used on a single-value fact ([RULE-14], [FACT-13]).

**Minimal example (non-normative):**
```yaml
when:
  entity_a.fact_x: { any_in: [1, 2] }    # fact_x is a single-value fact
```
**Fix:** Use `in` or the `eq` form for a single-value fact.

### E_QUANTIFIER_REQUIRED
**Class:** engine
**Meaning:** A child fact is tested without a quantifier.
**Raised when:**
- an `eq` or `in` condition is used on a child fact ([RULE-13], [FACT-14]).

**Minimal example (non-normative):**
```yaml
when:
  entity_b.fact_y: { in: [1, 2] }        # fact_y is a child fact
```
**Fix:** Use `all_in` or `any_in` for a child fact.

### E_ROOT_BINDING_MISSING
**Class:** engine
**Meaning:** A feature has no root binding.
**Raised when:**
- a feature has no root binding, including a feature that no rules file names ([BIND-13]).

**Minimal example (non-normative):** A feature `feature_x` is declared, and no binding is written at the root level for it.
**Fix:** Write a root binding that names a policy of the feature.

### E_ROOT_BINDING_NOT_ALLOWED
**Class:** engine
**Meaning:** A feature's `binding_settable_at` does not include the root level.
**Raised when:**
- `binding_settable_at` does not include the root level ([FEAT-18], [SCOPE-10]).

**Minimal example (non-normative):**
```yaml
features:
  feature_x:
    binding_settable_at: [level_a]   # root_level is missing
    # other feature members omitted
```
**Fix:** Add the root level to `binding_settable_at`.

### E_SCOPE_DECLARATION
**Class:** engine
**Meaning:** The decision hierarchy or a scope entry is malformed.
**Raised when:**
- `levels` or `axes` is not a list, `levels` is empty, or an entry of either is not a string, including a scalar that the core schema reads as a number or boolean ([SCOPE-1], [SCOPE-2], [SCOPE-9], [SRC-11]);
- a level or axis name is empty or contains an unpaired surrogate ([SCOPE-8], [SCOPE-9]);
- a name repeats across `levels` and `axes` after NFC normalisation ([SCOPE-3], [SCOPE-4], [SCOPE-8]);
- the root level is listed in `axes` ([SCOPE-11]), or has an entry in `scopes` ([SCOPE-12]);
- a non-root level or axis lacks exactly one entry in `scopes` ([SCOPE-14], [SCOPE-15]);
- a scope entry lacks `nodes`, or has a member other than `nodes`, `on_unknown_node` and `values` ([SCOPE-17], [SCOPE-18]);
- `nodes` is not an inline list, a `from` object, or `open`, or an inline entry is not a string, including a number or boolean ([SCOPE-19], [SCOPE-20], [SRC-11]);
- a closed catalog (an inline list or a `from` object) has no `on_unknown_node` ([SCOPE-22]);
- `on_unknown_node` is not `error` or `ignore` ([SCOPE-23]), or appears on an open catalog ([SCOPE-24]);
- `values` is neither `files` nor `service` when present ([SCOPE-25]), or an axis declares `values: service` ([SCOPE-26]);
- a `from` path is absolute, leaves the project, names no file or names a directory ([SCOPE-28]);
- a catalog file has a defect that no other code names ([SCOPE-30]).

**Minimal example (non-normative):**
```yaml
levels: []        # the levels list must have at least one entry
```
**Fix:** Correct the hierarchy declaration to the form that [SCOPE-9] and the cited clause define.

### E_SOURCE_MAPPING
**Class:** engine
**Meaning:** A source file is mapped to a scope or a node in a way that is not allowed.
**Raised when:**
- a file is selected by more than one `sources.values` item ([SRC-34]);
- an item of `sources.values` does not have one of the allowed shapes ([SRC-21]), or a path pattern does not have exactly one `{scope}` and one `{node}` placeholder ([SRC-23]);
- a file is selected by entries of two different roles ([SRC-20]);
- a root entry names a non-root level ([SRC-27]), or a path pattern captures the root level's name ([SRC-28]);
- in a multi-node values file, a scope other than the root level has a value that is not a mapping of nodes, a node has a value that is not a mapping of entries, or a root-level entry has a value that is not a mapping of entries ([SRC-31]);
- a values file gives values for a level declared `values: service` ([SRC-33]);
- a source value targets a service-sourced level ([SCOPE-47], [KEY-11]).

**Minimal example (non-normative):**
```yaml
sources:
  values:
    - pattern: "values/{scope}/{node}.yaml"
    - file: values/level_a/node_x.yaml        # the same file, selected twice
```
**Fix:** Select each file with one entry, and map each file to one scope and node.

### E_STRATEGY_TYPE
**Class:** engine
**Meaning:** A key's type is not allowed for its merge strategy.
**Raised when:**
- a key's type is not allowed for its strategy ([MERGE-3]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: string
    combine: min                  # min is defined for integer keys
    settable_at: [level_a]
```
**Fix:** Choose a strategy that accepts the key's type, as section 5 defines.

### E_TYPE_MISMATCH
**Class:** engine
**Meaning:** A source value does not have the form of its declared type.
**Raised when:**
- a source value has the wrong form for its type, such as a non-boolean for `boolean` or a non-integer scalar for `integer` ([TYPE-12], [TYPE-15]);
- a source value is `null` ([TYPE-76], [TYPE-77]);
- a `set` source value is not a list ([TYPE-45]);
- a number is used as a `decimal` value, or a `decimal` string does not match its form ([TYPE-25], [NORM-14]);
- an `iana_tz` or `enum` source value is not in its allowed set ([TYPE-35], [TYPE-44]);
- a source string contains an unpaired surrogate ([NORM-8]);
- a source object value has a member that the type does not declare ([TYPE-64]) or lacks a field that the type requires ([TYPE-63]), including an incomplete starting value of a `deep_merge` key ([MERGE-10]);
- a name is a scalar that the core schema reads as a non-string, where a name is expected and no enclosing declaration has its own code ([SRC-11]);
- an entity name is a number, boolean or null ([ENTITY-19]);
- an authored value of a `deep_merge` key sets an optional field to an incomplete object ([MERGE-26]);
- no other code applies to a failed source value: this is the fallback of [TYPE-71].

A code that the clause owning the value names takes precedence ([TYPE-71]). Where an enclosing declaration has a code, [SRC-11] gives that code instead: a non-string level or axis name or `nodes` entry is `E_SCOPE_DECLARATION` ([SCOPE-2], [SCOPE-20]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: boolean
    combine: all
    settable_at: [level_a]
values:
  level_a: { node_x: { key_x: 5 } }     # a number for a boolean key
```
**Fix:** Write a value of the declared type. For a scalar name that the core schema reads as a non-string, quote it.

### E_TYPE_REQUIRED
**Class:** engine
**Meaning:** A declaration has no `type`.
**Raised when:**
- a key, fact, object field or `items` declaration has no `type` member ([TYPE-4], [KEY-5], [FACT-6]). This code takes precedence over `E_INVALID_DECLARATION` for such a declaration ([TYPE-11]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    combine: all
    settable_at: [level_a]        # no type
```
**Fix:** Add a `type` member.

### E_UNKNOWN_ENTITY
**Class:** engine
**Meaning:** A name refers to an entity that is not declared.
**Raised when:**
- a `belongs_to` or `has_many` value names an undeclared entity ([ENTITY-6]);
- the `<entity>` part of a declared fact name is not a declared entity ([FACT-2]);
- a fact name has no `.` ([FACT-3]);
- a feature's `decided_per` names an undeclared entity ([FEAT-5]).

A condition whose fact has an undeclared entity part is `E_UNKNOWN_FACT`, not this code ([RULE-10]). See the open points for the dot-less condition name.

**Minimal example (non-normative):**
```yaml
facts:
  "entity_z.fact_x": { type: integer }   # entity_z is not declared
```
**Fix:** Declare the entity, or correct the name.

### E_UNKNOWN_FACT
**Class:** engine
**Meaning:** A fact name refers to a fact that is not declared.
**Raised when:**
- a fact named in a condition is not declared ([RULE-9]), or its entity part is not a declared entity ([RULE-10]);
- a `value_of` or `list_of` fact is not declared ([POL-14]);
- a fact used in an example's FactSet is not declared ([POL-38]).

A condition fact name without the `<entity>.<attribute>` form is reported as [FACT-3] gives: `E_UNKNOWN_ENTITY` when it has no `.`, and `E_INVALID_DECLARATION` when its attribute part is empty ([RULE-9]).

**Minimal example (non-normative):**
```yaml
policies:
  policy_a:
    rules:
      - { id: rule_1, name: "check", when: { entity_a.fact_missing: true }, then: effect_a }
```
**Fix:** Declare the fact, or correct the name.

### E_UNKNOWN_FEATURE
**Class:** engine, evaluation
**Meaning:** A name refers to a feature that is not declared.
**Raised when:**
- a rules file's `feature` names no declared feature ([POL-3]). Validation reports this as a finding.
- at runtime, decide is called with a feature name that the bundle does not declare, or with a feature argument that is omitted, not a string, or empty ([EVAL-35], [EVAL-37], [EVAL-17]).

**Minimal example (non-normative):**
```yaml
feature: feature_missing      # no feature with this name is declared
```
```text
decide(context, "feature_missing", facts)
```
**Fix:** Declare the feature, or correct the name.

### E_UNKNOWN_KEY
**Class:** engine, evaluation
**Meaning:** A name refers to a key that is not declared.
**Raised when:**
- a value is written for a key name that the compiled project does not declare ([KEY-10]);
- at runtime, resolve is called with a key name that the bundle does not declare, or with a key argument that is omitted, not a string, or empty ([EVAL-34], [EVAL-37], [EVAL-16]).

**Minimal example (non-normative):**
```yaml
values:
  level_a: { node_x: { key_missing: true } }   # key_missing is not declared
```
```text
resolve(context, "key_missing")
```
**Fix:** Declare the key, or correct the name. The finding may suggest a declared name ([FIND-27]).

### E_UNKNOWN_KIND
**Class:** engine
**Meaning:** A key names a kind that is not declared.
**Raised when:**
- a key's `kind` names a kind that the compiled project does not declare ([KIND-13]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: boolean
    kind: kind_missing
    combine: all
    settable_at: [level_a]
```
**Fix:** Declare the kind, or correct the name.

### E_UNKNOWN_NODE
**Class:** engine, evaluation
**Meaning:** A node ID is not a member of a closed catalog.
**Raised when:**
- a source value or a binding uses a node ID that is not a member of its closed catalog, after NFC normalisation ([SCOPE-43]);
- an example's context names a node ID that a closed catalog does not list, under either value of `on_unknown_node` ([POL-44]);
- at runtime, a context supplies a node ID that is not a member of a closed catalog whose `on_unknown_node` is `error` ([CTX-12], [EVAL-33]). Under `ignore` no error is raised and the scope is skipped ([CTX-14] to [CTX-16]).

**Minimal example (non-normative):**
```yaml
levels: [root_level, level_a]
scopes:
  level_a:
    nodes: [node_x]
values:
  level_a: { node_y: { key_x: true } }      # node_y is not in the catalog
```
**Fix:** Use a node ID that the catalog lists, or add it to the catalog.

### E_UNKNOWN_POLICY
**Class:** engine
**Meaning:** A binding names a policy that is not declared in its rules file.
**Raised when:**
- a binding's `policy` names no policy declared in the same rules file ([BIND-5], [BIND-6]).

**Minimal example (non-normative):**
```yaml
feature: feature_x
bindings:
  root_level:                    # the root level's name
    policy: policy_missing
```
**Fix:** Declare the policy in the same rules file, or correct the name.

### E_UNKNOWN_SCOPE
**Class:** engine, evaluation
**Meaning:** A name refers to a level or axis that is not declared, or a context names an undeclared scope.
**Raised when:**
- a source value, a binding, a key's `settable_at` entry, or a `binding_settable_at` entry names an undeclared level or axis ([SCOPE-46], [KEY-6], [BIND-11], [FEAT-17]);
- an example's context names an undeclared level or axis ([POL-43]);
- at runtime, a context name is neither the root level nor a declared non-root level, or a declared axis is supplied under `levels`, or a declared level is supplied under `axes` ([CTX-4], [CTX-5], [EVAL-32]).

If the context also names the root level, `E_CONTEXT_INVALID` is raised instead ([EVAL-19]).

**Minimal example (non-normative):**
```yaml
values:
  level_z: { node_x: { key_x: true } }    # level_z is not declared
```
```text
resolve({ levels: { level_z: "node_x" } }, "key_x")
```
**Fix:** Use a declared level or axis name. The finding may suggest a declared name ([FIND-27]).

### E_UNKNOWN_SETTING
**Class:** engine
**Meaning:** A binding or a setting reference names a setting that the policy does not declare.
**Raised when:**
- an entry of a binding other than `policy` names no setting of the policy ([BIND-8]);
- a setting reference names no setting of the same policy ([RULE-22]).

**Minimal example (non-normative):**
```yaml
bindings:
  root_level:
    policy: policy_a
    setting_missing: 1           # policy_a declares no such setting
```
**Fix:** Name a setting that the policy declares, or declare the setting. The finding may suggest a declared name ([FIND-27]).

### E_YAML_FEATURE
**Class:** engine
**Meaning:** A source uses a YAML feature that is outside the portable subset.
**Raised when:**
- a source file has an alias node (`*name`) or a merge key `<<` ([SRC-2]);
- a source file has an explicit tag or an anchor (`&name`), in the manifest, a source or a preset ([SRC-7]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    default: !!str 1            # explicit tag
```
**Fix:** Remove the alias, merge key, tag or anchor, and write the value directly.

## Evaluation errors

These seven codes are raised by an evaluator at runtime and are never reported as findings ([FIND-9], [EVAL-22]). Each is raised by the operations that [EVAL-38] names, and only where [EVAL-24] to [EVAL-37] allow. The other four evaluation codes are also engine errors, and are in the engine group.

### E_BUNDLE_INTEGRITY
**Class:** evaluation (load bundle)
**Meaning:** The bytes of a bundle fail a strict-parsing, digest or structure check.
**Raised when:**
- the input is not UTF-8, starts with a byte-order mark, or is not well-formed JSON, or lacks `bundleVersion` or `content` ([BUNDLE-116]);
- the input contains a number that is not an integer or is outside the range of [DET-4] ([BUNDLE-116], [BUNDLE-11], [BUNDLE-12]);
- the input contains an escaped unpaired surrogate ([BUNDLE-116], [BUNDLE-13]);
- the input is not exactly the serialisation that [BUNDLE-2] requires ([BUNDLE-116]);
- a property name repeats at any depth ([BUNDLE-111]), or the envelope has a member other than `bundleVersion` and `content` ([BUNDLE-112]);
- the recomputed digest differs from `bundleVersion` ([BUNDLE-113]);
- the `content` fails a structure check of [BUNDLE-118] to [BUNDLE-136];
- `bundleFormatVersion` or `specVersion` is missing from `content` ([EVAL-56]).

The checks run in the order of [EVAL-14]. A bundle with an unsupported version reports `E_BUNDLE_UNSUPPORTED` first ([EVAL-14]).

**Minimal example (non-normative):** `load bundle(bytes)`, where the envelope holds an extra member `"note"` next to `bundleVersion` and `content`.
**Fix:** Use the bytes that the compiler wrote. Editing a bundle by hand changes its digest.

### E_BUNDLE_UNSUPPORTED
**Class:** evaluation (load bundle)
**Meaning:** The bundle's format or spec version is not one that this evaluator supports.
**Raised when:**
- `bundleFormatVersion` is not the string `"1"`, or `specVersion` is not the string `"0.1"` ([BUNDLE-115], [EVAL-25]);
- either member is present with a value of another JSON type ([EVAL-56]).

**Minimal example (non-normative):** `load bundle(bytes)`, where `content.bundleFormatVersion` is `"2"`.
**Fix:** Load the bundle with an evaluator that supports its format, or recompile it with a compiler of this spec version.

### E_CONTEXT_INVALID
**Class:** evaluation (resolve, decide and their synchronous variants)
**Meaning:** The context argument is malformed, or it names the root level.
**Raised when**, at runtime ([POL-43] gives the validation-time codes for an example's context):
- the context is present but is not a map ([EVAL-31]);
- a `levels` or `axes` member is not a map from strings to strings ([CTX-1]), or the context has any other member ([CTX-2]);
- the context names the root level in either map ([CTX-3], [EVAL-19]);
- a node ID value is not valid under [SCOPE-33] and [SCOPE-34], or contains an unpaired surrogate ([CTX-6]).

A node ID that is not in NFC is not an error ([CTX-7]). Context validation runs before any other step ([EVAL-16], [EVAL-17], [CTX-11]).

**Minimal example (non-normative):**
```text
resolve({ levels: { root_level: "node_x" } }, "key_x")
```
**Fix:** Remove the root level from the context. Pass the other scopes by name.

### E_EVALUATOR_OPTIONS
**Class:** evaluation (create evaluator)
**Meaning:** The options given to create evaluator are invalid.
**Raised when:**
- the options are present but are not a map ([EVAL-28]);
- a timeout is not a positive integer number of milliseconds no greater than 2^53−1 ([EVAL-28], [SVC-38], [SVC-41]);
- a provider is registered for a level that the bundle does not declare, for a declared level that is not service-sourced, or twice for one level ([EVAL-27], [SVC-14]);
- a provider is registered with no timeout ([SVC-48]).

A provider under a misspelt level name is reported with this code, not with `E_PROVIDER_MISSING` ([EVAL-15]).

**Minimal example (non-normative):**
```text
create evaluator(bundle, { providers: { level_undeclared: provider_a } })
```
**Fix:** Register one provider for each service-sourced level, with a positive timeout, using the level names that the bundle declares.

### E_FACTS_INVALID
**Class:** evaluation (decide and its synchronous variant)
**Meaning:** The FactSet given to decide is malformed or does not match the decided entity.
**Raised when:**
- the FactSet is not a map, including `null` ([FACT-22], [EVAL-36]);
- it has an entry other than `facts` and `children` ([FACT-23]);
- `facts` or `children` is not a map, or is `null` ([FACT-25], [FACT-28], [FACT-30]);
- a `facts` entry is not a declared single-value fact of the decided entity ([FACT-26], [FACT-27]);
- a `children` entry is not a child of the decided entity, or a child map holds a fact that is not declared for that child ([FACT-29], [FACT-33]);
- a child list is not a list, or an element is not a map ([FACT-31], [FACT-32]);
- a value does not match its fact's type ([FACT-35], [FACT-37], [FACT-38], [FACT-40], [FACT-41]).

A missing fact is not this error ([FACT-21]). This check runs before the missing-fact precheck, even when that precheck would also apply ([FACT-44], [EVAL-18]).

**Minimal example (non-normative):**
```text
decide(context, "feature_x", { facts: { "entity_a.fact_x": "true" } })   # fact_x is boolean
```
**Fix:** Pass a map of declared facts with values of their types. For a missing single-value fact, omit it or pass `null`; that is a missing fact, not an error.

### E_PROVIDER_MISSING
**Class:** evaluation (create evaluator)
**Meaning:** No provider is registered for a service-sourced level.
**Raised when:**
- create evaluator has no provider for a service-sourced level declared in the bundle ([SVC-14], [EVAL-26]).

**Minimal example (non-normative):**
```text
create evaluator(bundle, {})       # the bundle declares service-sourced level_b
```
**Fix:** Register one provider for each service-sourced level.

### E_PROVIDER_REQUIRED
**Class:** evaluation (synchronous variant of resolve)
**Meaning:** The synchronous variant of resolve would need a provider call.
**Raised when:**
- the input needs a provider call under [SVC-42]. The synchronous variant raises this at step 3 of [EVAL-16] instead of calling the provider ([EVAL-11], [EVAL-29]).

**Minimal example (non-normative):**
```text
resolveSync(context, "key_x")      # key_x is settable at a service-sourced level with an admitted node
```
**Fix:** Call the provider-capable resolve, or pass a context and key that need no provider call.

## Tooling error

### E_PROVENANCE_INVALID
**Class:** tooling (the spec check tool and, from phase 5, the CLI; never an evaluator)
**Meaning:** A provenance sidecar does not match the bundle that it is kept beside.
**Raised when:**
- the sidecar fails strict parsing under the duplicate-property rule of [BUNDLE-111] ([BUNDLE-151]);
- the sidecar breaks [BUNDLE-141] to [BUNDLE-143], or one of the forms of [BUNDLE-150] ([BUNDLE-151]);
- the sidecar's `bundleVersion` differs from that of the bundle it is checked against ([BUNDLE-151], [BUNDLE-144]).

An evaluator never reads a sidecar and never reports this code ([BUNDLE-145], [BUNDLE-153]). The provenance cases of [VEC-124], [VEC-129] to [VEC-131] test it.

**Minimal example (non-normative):** A sidecar whose `bundleVersion` is the digest of another bundle.
**Fix:** Regenerate the sidecar from the bundle it describes.

## Lint rules

Each lint rule has one entry. The resolved severity of a rule comes from the lint settings of the presets in layer order and then from the manifest ([LINT-4]). A rule whose resolved severity is `off` does not run ([LINT-6]). The default severities for the built-in presets `recommended` and `minimal` are listed in each class line. They are the settings of `presets/recommended.yaml` and `presets/minimal.yaml`. This specification does not define the contents of those presets ([MANIFEST-21]), so the defaults are informative. A project that extends no preset runs no lint rule ([LINT-5]). The lint findings take the resolved severity ([FIND-16]).

### naming/*
**Class:** lint. Default: `recommended` error; `minimal` off.
**Meaning:** A name of a declared category does not match the naming pattern that applies to it.
**Raised when:**
- a naming pattern is declared in the project or inherited from a preset it extends, and it gives one rule `naming/<name>` ([LINT-12]);
- the pattern matches no part of the name, by search, with `^` and `$` anchoring to the true start and end ([LINT-18]);
- it checks key names of keys whose `kind` names a kind that uses the pattern, or all keys when no kind names it ([LINT-16], [LINT-19]);
- it checks enum values of `enum` types and enum-form features' `returns` entries, but not entries of a `set` universe ([LINT-19], [LINT-20]);
- it checks node IDs of closed catalogs and feature names ([LINT-19]).

The finding is located at the entry that writes the name ([LINT-21]). No naming rule runs when no pattern is declared ([LINT-13]). The explanation of `naming/<name>` is the pattern's `description`, or the explanation of the naming family followed by the pattern ([FIND-30]).

**Minimal example (non-normative):**
```yaml
naming:
  enum_value: { category: enum_value, pattern: '^[A-Z][A-Z0-9_]*$' }
facts:
  entity_a.fact_x: { type: enum, values: [VALUE_A, value_b] }   # value_b does not match
```
**Fix:** Rename the name so that it matches the pattern, or change the pattern in the shared schema file or the preset.

### examples/mixed-collection
**Class:** lint. Default: `recommended` warn; `minimal` off.
**Meaning:** A policy that uses `all_in` or `any_in` has no example that tests a mixed child collection.
**Raised when:**
- a policy has a rule with `all_in` or `any_in`, and no example whose selected binding names that policy has a mixed child collection for at least one of those conditions ([LINT-32]);
- a child collection is mixed when one child's value is in the membership list and another child's present value is not ([LINT-33]);
- a setting reference in the membership list is replaced by its value for the example's binding ([LINT-34]);
- a policy that no binding names is not checked here ([LINT-35]);
- a policy gets at most one finding ([LINT-32]).

The finding is located at the policy's entry under `policies` ([LINT-36]).

**Minimal example (non-normative):** A policy with `when: { entity_b.fact_y: { all_in: [1, 2] } }`, and examples whose children all hold values in the list.
**Fix:** Add an example with a child collection that mixes values in and out of the list.

### examples/required-per-binding
**Class:** lint. Default: `recommended` error; `minimal` off.
**Meaning:** A binding has no example.
**Raised when:**
- a binding of a feature, the root binding or a node entry under a non-root level, is the selected binding of no example ([LINT-29], [LINT-30]).

The finding is located at the binding's entry ([LINT-31]).

**Minimal example (non-normative):** A feature has a root binding and a binding for `node_x` at `level_b`, and its examples all select the root binding.
**Fix:** Add an example whose context selects the binding.

### presets/override
**Class:** lint. Default: `recommended` info; `minimal` info.
**Meaning:** A preset kind or naming pattern is replaced by a later definition.
**Raised when:**
- a kind or naming pattern is defined again in a later layer and replaces an earlier one ([LINT-49], [MANIFEST-32]). A name defined in n layers gives n − 1 findings.

A second definition within the project's own sources is `E_DUPLICATE_DEFINITION`, not this finding ([LINT-49], [SRC-43]). The finding is located at the replacing definition, and its message names the source it replaces ([LINT-50]).

**Minimal example (non-normative):** The manifest extends `preset_a` and `preset_b`, and both define `kind_a`. The finding is at the definition in `preset_b`, and names `preset_a`.
**Fix:** Rename one definition, or set the rule to `off` in the manifest's `lint.rules` ([MANIFEST-38]).

### rules/dead-rule
**Class:** lint. Default: `recommended` warn; `minimal` warn.
**Meaning:** A rule can never match.
**Raised when:**
- a condition is `in`, `all_in` or `any_in` with a literal empty list ([LINT-26], [RULE-34]);
- a rule comes after a rule with an empty `when` in the same policy, because a rule with an empty `when` always holds and stops the first-hit scan ([LINT-27], [RULE-2], [RULE-46], [RULE-47]).

A membership list given by a setting reference is not considered ([LINT-28]). A rule gets at most one finding, located at its item in the policy's `rules` list ([LINT-28]).

**Minimal example (non-normative):**
```yaml
rules:
  - { id: rule_1, name: "empty", when: { }, then: effect_a }
  - { id: rule_2, name: "after", when: { entity_a.fact_x: { in: [] } }, then: effect_b }
```
**Fix:** Remove the rule, or give it a condition that can hold.

### schema/description-required
**Class:** lint. Default: `recommended` warn; `minimal` off.
**Meaning:** A key, feature or fact declaration has no description.
**Raised when:**
- a key, feature or fact declaration has no `description` member ([LINT-44]);
- its `description` is empty, or is only ASCII space, tab, carriage return and line feed ([LINT-45]).

The finding is located at the declaration's entry ([LINT-44]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: boolean
    combine: all
    settable_at: [level_a]        # no description
```
**Fix:** Add a `description` that says what the declaration means.

### schema/owner-required
**Class:** lint. Default: `recommended` error; `minimal` off.
**Meaning:** A key has no owner while namespaces are in use.
**Raised when:**
- the manifest's `namespaces` has at least one entry, and a key is declared in a shared file with no `owner` member ([LINT-46], [LINT-47], [LINT-48]).

A key declared in a namespaced file has an owner ([LINT-48]). The finding is located at the key's declaration entry ([LINT-47]).

**Minimal example (non-normative):** The manifest has `namespaces: { ns_a: { schema: schema/ns_a.yaml } }`, and `key_x` is declared in `schema/shared.yaml` without `owner`.
**Fix:** Declare the key in a namespaced file, or add an `owner` member.

### schema/unused
**Class:** lint. Default: `recommended` warn; `minimal` off.
**Meaning:** A key, fact, setting or policy is declared and never used.
**Raised when:**
- a key has no values source and is not settable at a service-sourced level ([LINT-38]);
- a fact is named by no rule `when` and by no setting's `value_of` or `list_of` ([LINT-39]);
- a setting is referred to by no condition in its own policy ([LINT-40]);
- a policy is named by no binding ([LINT-41]).

A reference counts as a use wherever it is written, even from an unused declaration ([LINT-42]). The finding is located at the declaration's entry ([LINT-43]).

**Minimal example (non-normative):**
```yaml
keys:
  key_x:
    type: boolean
    combine: all
    settable_at: [level_a]        # no values file writes key_x
```
**Fix:** Remove the declaration, or use it. A key that application code reads only through its `default` is flagged by design. The project can set the rule `schema/unused` to `off` in the manifest's `lint.rules` ([LINT-38]).

### values/no-op
**Class:** lint. Default: `recommended` error; `minimal` warn.
**Meaning:** An authored value is the identity of its key's strategy, so it changes nothing.
**Raised when:**
- an authored value, after normalisation, equals the identity of its key's strategy ([LINT-23], [MERGE-5]).

A key whose strategy has no identity is never flagged ([LINT-23]). A value that an engine finding already reports is not flagged ([LINT-25]). The finding is located at the entry that writes the value ([LINT-24]).

**Minimal example (non-normative):** A boolean key with `combine: any`, whose identity is `false` ([MERGE-5]), and a root value of `false`.
```yaml
keys:
  key_x: { type: boolean, combine: any, settable_at: [root_level] }
values:
  root_level: { key_x: false }
```
**Fix:** Remove the value.

## Index

Entries are sorted by code. Clause IDs are the primary defining clauses named in each entry.

| Code | Class | Clause IDs |
| --- | --- | --- |
| E_AXIS_COMBINE_INVALID | engine | AXIS-2, AXIS-4, AXIS-5, AXIS-6, AXIS-7 |
| E_BINDING_NOT_ALLOWED | engine | BIND-12, FEAT-19, FEAT-20 |
| E_BUNDLE_INTEGRITY | evaluation | EVAL-2, EVAL-14, EVAL-24, EVAL-56, BUNDLE-2, BUNDLE-11, BUNDLE-12, BUNDLE-13, BUNDLE-111, BUNDLE-112, BUNDLE-113, BUNDLE-116, BUNDLE-118 to BUNDLE-136 |
| E_BUNDLE_UNSUPPORTED | evaluation | EVAL-2, EVAL-14, EVAL-25, EVAL-56, BUNDLE-115 |
| E_CONTEXT_INVALID | evaluation | EVAL-19, EVAL-30, EVAL-31, CTX-1, CTX-2, CTX-3, CTX-6, CTX-7, CTX-11 |
| E_DEFAULT_NOT_ALLOWED | engine | MERGE-4 |
| E_DENY_BOUND_REQUIRED | engine | SVC-12 |
| E_DENY_NOT_NARROWING | engine | SVC-11 |
| E_DEPTH_UNSUPPORTED | engine | RULE-11, POL-16 |
| E_DUPLICATE_DEFINITION | engine | ENTITY-3, FACT-5, KEY-1, POL-5, SRC-39, SRC-40, SRC-41, SRC-43, SRC-44 |
| E_DUPLICATE_KEY | engine | SRC-3, SRC-4, SRC-9, SCOPE-30, ENTITY-3, FACT-5 |
| E_DUPLICATE_NODE | engine | SCOPE-42 |
| E_DUPLICATE_RULE_ID | engine | POL-24 |
| E_DUPLICATE_VALUE | engine | KEY-7, KEY-12, SRC-42, ENTITY-8, FEAT-8, FEAT-21, TYPE-52, RULE-21, POL-19, POL-30 |
| E_EFFECT_MISMATCH | engine | FEAT-13, FEAT-14, POL-40 |
| E_ENTITY_GRAPH | engine | ENTITY-13, ENTITY-14, ENTITY-15, ENTITY-16, ENTITY-17 |
| E_EVALUATOR_OPTIONS | evaluation | EVAL-4, EVAL-15, EVAL-27, EVAL-28, SVC-14, SVC-38, SVC-48 |
| E_EXAMPLE_FAILED | engine | POL-42, POL-46, POL-47 |
| E_FACT_NOT_IN_SCOPE | engine | RULE-12, POL-16, FACT-15 |
| E_FACTS_INVALID | evaluation | EVAL-17, EVAL-36, FACT-21, FACT-22, FACT-23, FACT-25 to FACT-33, FACT-35, FACT-37, FACT-38, FACT-40, FACT-41, FACT-44 |
| E_ILLEGAL_FACT_VALUE | engine | RULE-18, POL-39 |
| E_INTEGER_RANGE | engine | TYPE-20, TYPE-22, TYPE-79, RULE-20 |
| E_INVALID_DECLARATION | engine | TYPE-11, TYPE-14, TYPE-42, KEY-3, KEY-5, KEY-7, ENTITY-7, ENTITY-18, FACT-3, FACT-8, FACT-11, FACT-45, FEAT-2, FEAT-8, FEAT-16, POL-25, POL-36, POL-43, POL-45, RULE-6, SRC-16, SVC-4, LINT-14, LINT-15, LINT-17 |
| E_INVALID_NODE_ID | engine | SCOPE-20, SCOPE-33, SCOPE-34, SCOPE-37, SCOPE-38, SCOPE-39, POL-43 |
| E_INVALID_SETTING | engine | POL-11, POL-12, POL-13, POL-17, POL-20, POL-27, BIND-9, RULE-23, RULE-24, RULE-25 |
| E_KIND_DEFINITION | engine | KIND-5, KIND-10, KIND-12, KEY-5, SVC-4, LINT-16 |
| E_KIND_VIOLATION | engine | KIND-4, KIND-6, KIND-8, KIND-11, SVC-5 |
| E_MANIFEST | engine | MANIFEST-2, MANIFEST-3, MANIFEST-4, MANIFEST-5, MANIFEST-10 to MANIFEST-12, MANIFEST-14 to MANIFEST-16, MANIFEST-19, MANIFEST-20, MANIFEST-26, MANIFEST-37, MANIFEST-38, NS-1, NS-2, NS-9, SRC-5, SRC-6, SRC-10, SRC-17, LINT-10 |
| E_MISSING_AXIS_COMBINE | engine | AXIS-3 |
| E_MISSING_DEFAULT | engine | MERGE-7 |
| E_MISSING_SOURCE_FAILURE | engine | SVC-2, KEY-8, KIND-9 |
| E_NAMESPACE_VIOLATION | engine | NS-4, NS-5, NS-7 |
| E_NOT_IN_UNIVERSE | engine | TYPE-48 |
| E_NOT_SETTABLE_HERE | engine | KEY-11 |
| E_OUT_OF_RANGE | engine | TYPE-21, TYPE-79 |
| E_PARSE | engine | SRC-5, SRC-6, SRC-10, SCOPE-30 |
| E_PRESET | engine | MANIFEST-22, MANIFEST-24, MANIFEST-27, MANIFEST-28, MANIFEST-30, MANIFEST-35, MANIFEST-36, LINT-8, LINT-10, LINT-14, LINT-17 |
| E_PROVENANCE_INVALID | tooling | BUNDLE-111, BUNDLE-141, BUNDLE-142, BUNDLE-143, BUNDLE-144, BUNDLE-145, BUNDLE-150, BUNDLE-151, BUNDLE-153, VEC-124, VEC-129, VEC-130, VEC-131 |
| E_PROVIDER_MISSING | evaluation | EVAL-4, EVAL-15, EVAL-26, SVC-14 |
| E_PROVIDER_REQUIRED | evaluation | EVAL-9, EVAL-11, EVAL-16, EVAL-29, SVC-42 |
| E_QUANTIFIER_NOT_ALLOWED | engine | RULE-14 |
| E_QUANTIFIER_REQUIRED | engine | RULE-13 |
| E_ROOT_BINDING_MISSING | engine | BIND-13 |
| E_ROOT_BINDING_NOT_ALLOWED | engine | FEAT-18 |
| E_SCOPE_DECLARATION | engine | SCOPE-1, SCOPE-2, SCOPE-3, SCOPE-4, SCOPE-8, SCOPE-9, SCOPE-11, SCOPE-12, SCOPE-14 to SCOPE-20, SCOPE-22 to SCOPE-26, SCOPE-28, SCOPE-30 |
| E_SOURCE_MAPPING | engine | SRC-20, SRC-21, SRC-23, SRC-27, SRC-28, SRC-31, SRC-33, SRC-34, SCOPE-47, KEY-11 |
| E_STRATEGY_TYPE | engine | MERGE-3 |
| E_TYPE_MISMATCH | engine | SRC-11, TYPE-12, TYPE-15, TYPE-25, TYPE-35, TYPE-44, TYPE-45, TYPE-63, TYPE-64, TYPE-71, TYPE-76, TYPE-77, NORM-8, NORM-14, ENTITY-19, MERGE-26 |
| E_TYPE_REQUIRED | engine | TYPE-4, TYPE-11, KEY-5, FACT-6 |
| E_UNKNOWN_ENTITY | engine | ENTITY-6, FACT-2, FACT-3, FEAT-5 |
| E_UNKNOWN_FACT | engine | RULE-9, RULE-10, FACT-3, POL-14, POL-38 |
| E_UNKNOWN_FEATURE | engine, evaluation | POL-3, EVAL-35, EVAL-37 |
| E_UNKNOWN_KEY | engine, evaluation | KEY-10, EVAL-34, EVAL-37 |
| E_UNKNOWN_KIND | engine | KIND-13 |
| E_UNKNOWN_NODE | engine, evaluation | SCOPE-43, POL-44, CTX-12, EVAL-33 |
| E_UNKNOWN_POLICY | engine | BIND-5, BIND-6 |
| E_UNKNOWN_SCOPE | engine, evaluation | SCOPE-46, KEY-6, BIND-11, FEAT-17, POL-43, CTX-4, CTX-5, EVAL-19, EVAL-32 |
| E_UNKNOWN_SETTING | engine | BIND-8, RULE-22 |
| E_YAML_FEATURE | engine | SRC-2, SRC-7 |
| examples/mixed-collection | lint | LINT-32 to LINT-36 |
| examples/required-per-binding | lint | LINT-29 to LINT-31 |
| naming/* | lint | LINT-12, LINT-13, LINT-15 to LINT-21, FIND-30 |
| presets/override | lint | LINT-49, LINT-50, MANIFEST-32 |
| rules/dead-rule | lint | LINT-26 to LINT-28 |
| schema/description-required | lint | LINT-44, LINT-45 |
| schema/owner-required | lint | LINT-46 to LINT-48 |
| schema/unused | lint | LINT-37 to LINT-43 |
| values/no-op | lint | LINT-23 to LINT-25 |

## Open points

- **(OPEN) Default lint severities for the presets `recommended` and `minimal`.** Section 1 states that this specification does not define the contents of the built-in presets ([MANIFEST-21]). The class lines of the lint entries repeat the settings of `presets/recommended.yaml` and `presets/minimal.yaml`, which agree with docs/requirements.md §6.3. They are informative. Recommended resolution: keep them as informative text, and let the preset files be the source of truth.
- **(OPEN) Dot-less fact name in a condition.** [RULE-9] defers a name without the `<entity>.<attribute>` form to [FACT-3], which gives `E_UNKNOWN_ENTITY` for a name with no `.`. [RULE-10] says that `E_UNKNOWN_ENTITY` is not reported for a condition fact. Recommended resolution: state in [RULE-10] that it applies only to a name that has an entity part, so that a dot-less condition name is `E_UNKNOWN_ENTITY`. The entries `E_UNKNOWN_ENTITY` and `E_UNKNOWN_FACT` follow [FACT-3] and [RULE-9] until then.
