# ADR 0010: Entity graph, FactSets, settings, effects and traces

- Status: accepted for the Phase 1 specification draft (delegated technical decision)
- Date: 2026-10-08
- Resolves: open questions 9, 10, 11 and 12 ([history](../history/open-questions-2026-10-07.md))

## Context

The decision model has to be exact enough for two evaluators to produce the same
effect, reason and trace. Several points were open: what `belongs_to` and
`has_many` imply, whether an omitted child collection counts as empty or missing,
which fact a `MISSING_FACT` reason names, where `$setting` can appear, and how
nullable or object effects are spelled.

## Decisions

### 1. Entity graph

- `belongs_to: <entity>` or a list of entities declares **to-one parents**.
  `has_many: <entity>` or a list declares **child collections**.
- **No inverse is inferred.** `invoice: { has_many: order }` gives invoice-level
  decisions a collection of orders. It does not make invoice an ancestor of order.
  To get that, declare `order: { belongs_to: [customer, invoice] }`.
- For a decision about entity `E`:
  - **single-value facts** are the facts of `E` and of every entity reachable through
    `belongs_to` (transitively);
  - **child facts** are the facts of the entities `E` directly `has_many`.
- Any of the following is `E_ENTITY_GRAPH`:
  - a cycle through `belongs_to`;
  - a cycle through `has_many`;
  - an entity that is both an ancestor and a child of the same entity.

  An undeclared entity name is `E_UNKNOWN_ENTITY`.

### 2. FactSet

```yaml
facts:     { <fact>: <value>, ... }               # E and its ancestors
children:  { <child entity>: [ { <fact>: <value>, ... }, ... ] }
```

- An absent fact and a fact set to `null` are both **missing**.
- An **omitted** `children.<entity>` makes every child fact of that entity
  **missing**. An empty list `[]` is **present with no children**, so
  `all_in` and `any_in` over it are false.
- A child fact is missing if **any** child lacks it.
- Each child map may contain only that child entity's own facts.
- Undeclared facts, facts outside the scopes above, and values of the wrong type
  are `E_FACTS_INVALID` (ADR 0009). They are caller errors and never count as
  "missing".

### 3. Missing-fact precheck (unchanged rule, exact reason)

Before any rule runs, collect every fact referenced in any `when` clause of the
**selected** policy. If any is missing, return `if_fact_missing` with:

```yaml
reason: { type: MISSING_FACT, facts: [<all missing facts, sorted by code point>] }
```

The reason lists **all** missing facts in code-point order, so it can't depend on
rule order. This replaces the singular `fact` field from the earlier draft.

### 4. Settings are typed by facts

A policy setting borrows its type from a declared fact:

```yaml
settings:
  min_tier:              { value_of: customer.tier, default: SILVER }
  allowed_on_credit_hold: { list_of: item.stock_type, default: [] }
```

- `value_of: <fact>` holds one value of that fact's type. `list_of: <fact>` holds a
  list of distinct values.
- `default` is **required** (`E_INVALID_SETTING`), because bindings may leave out
  any setting.
- Where a setting may be used:

  | Condition | Setting allowed |
  | --- | --- |
  | Equality: `fact: $s` | `value_of` that same fact |
  | `in`, `all_in`, `any_in` | `list_of` that same fact |

  Any other use is `E_INVALID_SETTING`. Using a setting for a fact other than its
  own is also `E_INVALID_SETTING`, even if the types match. This keeps a setting's
  meaning tied to one fact.
- This replaces the earlier free-standing setting types (`enum`, `boolean`,
  `integer`, `string`), which needed their own value lists and could drift from
  the facts they were compared with.

### 5. Edge cases in conditions

- An empty `when: {}` is legal and always matches. It is the explicit catch-all.
- An empty membership list is legal and never matches. `in: []` and `any_in: []`
  have nothing to match. `all_in: []` fails on any non-empty collection, and
  `all_in` is false on an empty collection by definition. `rules/dead-rule` flags
  all three.
- Conditions in a rule are ANDed. For the trace, the compiler stores them as a list
  **sorted by fact name in code-point order**, and evaluators check them in that
  order. YAML map order is not preserved through canonical JSON, so source order
  can't be relied on.

### 6. Effects

- `returns: { enum: [...] }` makes effects strings from that list. With
  `nullable: true`, JSON `null` (YAML `null` or `~`) is also a legal effect.
  There is no special `NONE` literal; if a model wants the string `NONE`, it lists
  it as an ordinary enum value.
- `returns: { object: <fields> }` uses the object type grammar from ADR 0011.
  `then`, `default` and `if_fact_missing` must each be a complete valid value.
- The policy's `default`, every rule's `then`, and the feature's `if_fact_missing`
  are validated against `returns` (`E_EFFECT_MISMATCH`).

### 7. Trace

Under `FIRST`, the trace lists rules in order **up to and including the winner**:

```yaml
trace:
  - { id: good-credit, matched: false, failed: { fact: customer.credit_hold, op: eq } }
  - { id: credit-hold-allowed-stock, matched: true }
```

- `op` is one of `eq`, `in`, `all_in`, `any_in`.
- A `DEFAULT` result traces every rule.
- A `MISSING_FACT` result has an empty trace, because no rule ran.

## Alternatives considered

| Option | Why not |
| --- | --- |
| `has_many` implies the inverse `belongs_to` | Not every child has the parent as a to-one owner (an order may never be invoiced); implicit edges change which facts are in scope |
| Treat an omitted child collection as empty | An empty collection makes `all_in` false and can silently `DENY`, so a caller who forgot the children would be misled |
| Name only the first missing fact by rule order | The reason would change when rules are reordered |
| Keep free-standing setting types | A second type system, out of sync with fact enums |
| Keep source order of conditions | Lost in canonical JSON; also a hidden source of cross-language differences |
