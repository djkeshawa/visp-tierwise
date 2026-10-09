# ADR 0008: Merge strategies, axes, defaults and contribution effects

- Status: accepted for the Phase 1 specification draft (delegated technical decision)
- Date: 2026-10-08
- Resolves: open questions 3, 4, 5, 6, 7 and 16 (Phase 0 review)

## Context

The strategy table in the requirements gives each strategy's result, but not the
details both evaluators need to agree on: how several axes combine, what
`deep_merge` does with nested objects, where defaults are allowed, what `deny`
means for `min`, what `narrowing_only` checks, and how each contribution is
labelled in provenance.

## Decisions

### 1. Identities and absence

Each commutative strategy has a fixed **identity**: the result when nothing
contributes.

| Strategy | Identity | Notes |
| --- | --- | --- |
| `all` | `true` | |
| `any` | `false` | |
| `intersect` | the full `universe`, in universe order | |
| `union` | `[]` | |
| `min` | absent | "unlimited" |
| `max` | absent | |

- A commutative key **must not** declare `default` (`E_DEFAULT_NOT_ALLOWED`). Its
  identity is fixed by the strategy.
- An absent result is the JSON value `null` in conformance vectors and HTTP APIs.
  The JS API returns `value: null`. The Java API returns a `null` from `value()`,
  with `hasValue()` returning `false`. Only `min` and `max` can produce an absent
  result, because no key type accepts `null` as a value.

### 2. Order-dependent strategies (`override`, `deep_merge`)

- They need a `default`, or a value at the root level (`E_MISSING_DEFAULT`).
- **The default is the lowest fallback.** It applies only where no level and no
  axis sets a value.
- `override`: the hierarchy result `H` is the value from the most specific level
  that sets one.
- `deep_merge`: `H` folds the levels from root to most specific, starting from the
  default when there is one. Merging is **recursive** for fields declared as
  objects. Lists and every other value replace the earlier value. `null` is never
  a valid value, so it can't express deletion; v0.1 has no field deletion. The
  starting value (the default, or the root value when there is no default) must be
  a **complete** object, so every resolved result is complete. Values at other
  scopes may be partial.

### 3. Axes

For **commutative** strategies, every axis value is simply another input. The order
in which inputs are combined doesn't change the result.

For **order-dependent** strategies, a key settable on any axis must declare
`combine_across_axes` (`E_MISSING_AXIS_COMBINE`):

1. Compute `H` from the levels, ignoring the default.
2. Compute `A` from the axes in **declared axis order**, using the same strategy.
   For `override`, the last declared axis that sets a value wins. For `deep_merge`,
   later axes win field by field.
3. Combine:

| Case | Result |
| --- | --- |
| Neither `H` nor `A` exists | `default` |
| Only one exists | That one (merged over the default, for `deep_merge`) |
| Both, `axis_wins` | `A` (`deep_merge`: default ⊕ `H` ⊕ `A`) |
| Both, `hierarchy_wins` | `H` (`deep_merge`: default ⊕ `A` ⊕ `H`) |
| Both, `max` / `min` | The larger / smaller of `H` and `A`; on a tie, the hierarchy is the `source` |

- `max` and `min` combiners are allowed only on `integer` keys
  (`E_AXIS_COMBINE_INVALID`).
- Declaring `combine_across_axes` on a commutative key, or on a key not settable on
  any axis, is also `E_AXIS_COMBINE_INVALID`. An unnecessary declaration hints at a
  modeling mistake.

### 4. Source failure under `deny`

`deny` contributes the most restrictive value for the strategy, and it is allowed
only for narrowing strategies (`E_DENY_NOT_NARROWING`):

| Strategy | `deny` value |
| --- | --- |
| `all` | `false` |
| `intersect` | `[]` |
| `min` | the key's declared `min` bound. A `min` key using `deny` must declare `min` (`E_DENY_BOUND_REQUIRED`). |

This replaces the earlier synthetic `0`, which was wrong whenever negative values
were allowed and invalid whenever the declared minimum was positive.

### 5. `narrowing_only` is a per-value check

Under a kind with `narrowing_only: true`, every authored value must be able to
narrow the strategy's identity. It must not equal the identity:

| Strategy | Legal values |
| --- | --- |
| `all` | `false` only |
| `intersect` | any strict subset of the universe (including `[]`) |
| `min` | any value within the key's bounds |

A kind that sets `narrowing_only` and allows a non-narrowing strategy is
`E_KIND_DEFINITION`. The check is static. It is not relative to the inherited result,
because the inherited result depends on the runtime context. A value that is
redundant on a particular path is a lint matter, not an engine error.

Without a kind, identity values such as `true` under `all` are legal. The lint rule
`values/no-op` flags them.

### 6. Contribution effects describe the value, not its marginal impact

Each scope that supplies a value (or fails to) appears in `contributions`, ordered
from root to most specific, then axes in declared order. Its `effect` is computed
from **that value alone, compared with the identity**:

| Strategy | Value | Effect |
| --- | --- | --- |
| `all` | `false` / `true` | `VETO` / `IGNORED` |
| `any` | `true` / `false` | `WIDENED` / `IGNORED` |
| `intersect` | strict subset / full universe | `NARROWED` / `IGNORED` |
| `union` | non-empty / `[]` | `WIDENED` / `IGNORED` |
| `min` | any | `NARROWED` |
| `max` | any | `WIDENED` |
| `override`, `deep_merge` | any | `SET` |

Source failures appear as `UNAVAILABLE` (the provider failed or timed out) or
`INVALID` (the provider returned a bad value). They also carry
`onSourceFailure: skip | deny`. Under `deny`, the entry carries the synthetic
`value` used. Under `skip`, it carries no `value`.

The effect does not depend on the order in which values are combined. This matches
the commutativity of the strategies themselves and gives a stable answer to "who
restricted this?": every veto and every narrowing is listed, including a second
veto that didn't change the running result. For order-dependent keys, `source`
names the winning scope, or `DEFAULT`.

## Alternatives considered

| Option | Why not |
| --- | --- |
| Effects relative to the running result | The labels would depend on fold order, and a repeated veto would show as `IGNORED`, which hides restrictions from support staff |
| Default allowed on commutative keys | Two ways to express the identity, and `default: false` under `all` would silently veto everything |
| `hierarchy_wins` with the default counted as a hierarchy value | The axis could never apply on any key with a default, which makes the option useless |
| `null` deletes a field in `deep_merge` | Brings `null` into a type system that otherwise rejects it; deferred to an RFC if needed |
| `narrowing_only` relative to the inherited value | Depends on the context, so it can't be checked at compile time |
