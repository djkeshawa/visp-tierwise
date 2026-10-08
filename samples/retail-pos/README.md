# samples/retail-pos (domain B)

Sample domain B: a retail point-of-sale project with levels
`platform → region → store → terminal`, the axis `brand`, and `terminal` as a
service-sourced level. The entities are `shopper` and `basket` (a basket belongs to a
shopper and has many `line` items). The one feature, `pos.basket_check`, is decided per
basket and returns `APPROVE`, `REVIEW` or `REJECT`.

The project is written to be read and checked against the specification. It is domain B of
the two unrelated domains that the conformance suite uses (requirements §1.1). It shares no
names with domain A (`samples/commerce/`), and it uses no preset: the kinds and naming
patterns are declared in the project's own schema.

## Layout

| File | What it declares | Main clauses |
| --- | --- | --- |
| `tierwise.yaml` | `specVersion`, sources, the `pos` namespace, lint settings. No `extends`. | [MANIFEST-1], [MANIFEST-31], [NS-2], [MANIFEST-37], [LINT-4] |
| `schema/core.yaml` | Levels, axis, scopes, entities, facts, custom kinds, naming patterns (shared file) | [SCOPE-6], [SCOPE-10], [SCOPE-14], [ENTITY-1], [FACT-1], [KIND-1], [LINT-14] |
| `schema/components/pos.yaml` | Namespace `pos`: seven keys and the feature `pos.basket_check` | [NS-4], [NS-5], [KEY-2], [FEAT-1] |
| `values/platform.yaml` | Root values file: one value per key at the root level | [SRC-26], [SCOPE-13] |
| `values/overrides.yaml` | Multi-node values file for `region`, `store` and `brand` | [SRC-29], [SRC-31], [SCOPE-47] |
| `rules/pos.basket_check.yaml` | Two policies, two bindings and ten examples for the feature | [POL-1], [POL-6], [BIND-2], [BIND-3], [POL-28] |

## Hierarchy and keys

- **Levels.** `platform` (the root), `region` (closed: `coastal`, `inland`, `upland`),
  `store` (closed with `on_unknown_node: ignore`: `harbor_street`, `meadow_square`), and
  `terminal` (`nodes: open`, `values: service`). [SCOPE-10], [SCOPE-19], [SCOPE-22], [SCOPE-24].
- **Axis.** `brand` (closed: `harbor_foods`, `meadow_goods`). Only the keys whose
  `settable_at` lists `brand` are affected by it. [AXIS-1], [AXIS-3].
- **Keys.** Together the keys use `override`, `deep_merge`, `min`, `max`, `any`, `union` and
  `intersect`. The strategy `all` is allowed by the `guard` kind but no key uses it:

| Key | Kind | Type | Strategy | Notes |
| --- | --- | --- | --- | --- |
| `pos.basket.line_limit` | `guard` | integer 1..500 | `min` | Settable at `terminal`; deny gives the bound 1 ([SVC-9], [SVC-12]) |
| `pos.tender.allowed` | `guard` | set | `intersect` | Authored values must be strict subsets of the universe ([KIND-6]); deny gives `[]` |
| `pos.fraud.review_reasons` | none | set | `union` | Settable at `brand`, which is an axis input for a commutative key ([AXIS-2]) |
| `pos.offline.allowed` | none | boolean | `any` | Key-level `on_source_failure: skip` for `terminal` ([KEY-8]) |
| `pos.pricing.max_discount_percent` | none | integer 0..100 | `max` | Commutative, so no `combine_across_axes` ([AXIS-2]) |
| `pos.receipt.header` | `setting` | object | `deep_merge` | `combine_across_axes: axis_wins`; optional `footer_text` ([TYPE-62], [MERGE-11]) |
| `pos.receipt.tax_label` | `setting` | string | `override` | `combine_across_axes: axis_wins`; settable at `terminal` (skip) |

- **Kinds** (declared in `schema/core.yaml`, [KIND-1]):
  - `guard`: `all`, `intersect` and `min`, `narrowing_only`, `on_source_failure: deny`,
    naming pattern `guard_key`.
  - `setting`: `override` and `deep_merge`, `require_default`, `on_source_failure: skip`.
- **Feature.** `pos.basket_check`: decided per `basket`, `returns: { enum: [APPROVE, REVIEW, REJECT] }`,
  `hit_policy: FIRST`, `if_fact_missing: REJECT`, `binding_settable_at: [platform, store]`.

## Facts and the decision

- Single-value facts for a basket decision: `shopper.tier`, `shopper.fraud_flag`,
  `basket.tender`. Child facts: `line.category` (one per line). [FACT-13], [FACT-14].
- The policy `standard` (bound at `platform`) has the rules, in order:
  1. a flagged shopper is `REJECT` (`shopper.fraud_flag: true`);
  2. any line whose category is in `$restricted_categories` gives `REVIEW` (`any_in`);
  3. a cash basket whose lines are all in `$cash_categories` is `APPROVE` (`all_in`);
  4. a trusted tier paying with `$trusted_tender` is `APPROVE` (`in` on a list setting, and a
     `value_of` setting on `basket.tender`).
  Its default is `REVIEW`.
- The policy `strict` (bound at `store: harbor_street`) has rules 1 to 3 only, a default of
  `REVIEW`, and a narrower `cash_categories` default. The store binding also overrides
  `restricted_categories`. A binding replaces the root binding entirely ([BIND-18]).
- Missing facts are checked before any rule ([RULE-40], [RULE-42]). Empty `all_in` and
  `any_in` collections never hold ([RULE-33]).

## Examples

All ten examples pass under the spec: each expected effect is what the rules above give.

| Example | Binding | Effect | Shows |
| --- | --- | --- | --- |
| Mixed basket with a restricted line is reviewed | `standard` | `REVIEW` | Mixed collection for `any_in` ([LINT-33]) |
| Cash basket of cash categories is approved | `standard` | `APPROVE` | `all_in` holds |
| Empty basket on cash is reviewed by default | `standard` | `REVIEW` | Empty collection, `DEFAULT` reason |
| Flagged shopper is rejected | `standard` | `REJECT` | First rule wins |
| Trusted shopper paying by card is approved | `standard` | `APPROVE` | `in` on a list setting, `value_of` setting |
| Missing shopper tier rejects the basket | `standard` | `REJECT` | Missing single-value fact, `if_fact_missing` |
| Missing line children reject the basket | `standard` | `REJECT` | Omitted child collection ([FACT-17]) |
| Strict store reviews a general cash basket | `strict` | `REVIEW` | Mixed collection for `all_in`; the store binding replaces the root binding |
| Strict store approves a grocery-only cash basket | `strict` | `APPROVE` | Store binding with its own default |
| Terminal in context does not change the store decision | `strict` | `REVIEW` | Service-sourced level in context; `decide` calls no provider ([SVC-22]) |

## How this was checked

- The compiler is not implemented in this repository yet, so no validation run has been made.
- The manifest, the two schema files, the values files and the rules file were checked
  against the JSON Schemas in `spec/schemas/`, and all of them pass.
- A scratch script, not part of the repository, evaluated the ten examples under sections 7
  and 8 (precheck, `FIRST` hit policy, bindings and settings). All ten give the expected
  effect. The same script checked the cross-file rules of sections 1, 2, 5, 6 and 13: keys
  and their `settable_at`, defaults and bounds, `on_source_failure`, kind membership, naming
  patterns, binding coverage, mixed collections and unused declarations. It found no problem.
  Expected result: zero findings, including lint under the settings in `tierwise.yaml`.
  This is not a substitute for a conformance run once a compiler exists.

## Open points

- **Feature `description`.** [LINT-44] requires a `description` on features, but section 7
  ([FEAT-1], [FEAT-2]) lists no optional member for a feature declaration. The sample carries
  a feature `description`, as `spec/schemas/schema-file.schema.json` allows and as domain A
  does, and sets `schema/description-required: error` in `tierwise.yaml`. The spec should
  say in section 7 that a feature may carry `description`.
- **`**` in source globs.** [SRC-18] does not say whether `**` matches zero directories. The
  sample names its files without `**`: `schema/core.yaml` and `schema/components/*.yaml`.
