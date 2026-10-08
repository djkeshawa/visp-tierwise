# samples/commerce (sample domain A)

A complete Tierwise project for an order-to-cash domain. It is the sample that the
specification and the conformance suite use as domain A. It shows every clause area
that a project needs: a hierarchy with a generated catalog, an axis, service-sourced
levels, entities with child facts, keys that use five of the eight merge strategies
(`intersect`, `all`, `min`, `override` and `deep_merge`, including a deep-merged object
key), two features with rules and examples, and bindings. Domain B
(`samples/retail-pos/`) uses the other three, `union`, `any` and `max`.

The project extends `tierwise:recommended` ([MANIFEST-21]). That preset supplies the
`configuration` and `capability` kinds, the naming patterns and the strict lint
severities. The project declares none of them, so it causes no `presets/override`
finding.

## Layout

| Path | Role | Clauses |
| --- | --- | --- |
| `tierwise.yaml` | Manifest: preset, sources and namespace | [MANIFEST-1], [MANIFEST-19], [SRC-12], [NS-2] |
| `schema/core.yaml` | Shared schema: hierarchy, entities and facts | [SCOPE-10] to [SCOPE-26], [ENTITY-1], [FACT-1] |
| `schema/components/checkout.yaml` | Namespace `checkout`: keys and features | [NS-3] to [NS-7], [KEY-2], [FEAT-1] |
| `catalogs/sites.json` | Generated catalog for the `site` scope | [SCOPE-27], [SCOPE-29] |
| `values/global.yaml` | Root values file (no node) | [SRC-26] |
| `values/channel/web.yaml` | Path-mapped values, scope `channel`, node `web` | [SRC-22] |
| `values/division/north.yaml`, `south.yaml` | Path-mapped values, scope `division` | [SRC-22] |
| `values/site/site_001.yaml` | Path-mapped values, scope `site`, node `site_001` | [SRC-22], [SCOPE-43] |
| `values/seller/acme.yaml` | Path-mapped values for the axis `seller` | [SRC-22], [AXIS-1] |
| `rules/checkout.order_submission.yaml` | Feature decided per `order` | [POL-1], [BIND-2], [BIND-3] |
| `rules/checkout.invoice_release.yaml` | Feature decided per `invoice`, using `any_in` | [RULE-32], [FACT-14] |

## The hierarchy

- Levels, least to most specific: `global` (root), `channel`, `division`, `site`,
  `account`, `user`.
- Axis: `seller`, which has no place in the order of specificity ([SCOPE-7]).
- `channel` and `division` are closed catalogs with `on_unknown_node: error`.
- `site` is a closed catalog generated from `catalogs/sites.json`, with
  `on_unknown_node: ignore`. A context that names an unlisted site skips that scope
  entirely, with no value, binding or provider call ([CTX-14], [CTX-15]).
- `account` and `user` are open and service-sourced (`values: service`). No file may
  give them values ([SCOPE-47]). Their values come from providers at evaluation time
  ([SVC-13]). Keys settable there get their source-failure behaviour from the
  `capability` kind, which is `deny` ([KIND-9], [SVC-9]).

## Entities and facts

- `customer` (no relations). `invoice` has many `order`. `order` belongs to `customer`
  and has many `item` ([ENTITY-9] to [ENTITY-11]).
- `customer.active` and `customer.credit_hold` are single-value facts for an order
  decision ([FACT-13]).
- `item.stock_type` is a child fact of `order` ([FACT-14]). `order.payment_state` is a
  child fact of `invoice`. Both are enums.
- Fact descriptions are required by lint ([LINT-44]).

## Keys

| Key | Kind | Type and strategy | Settable at | Values in this project |
| --- | --- | --- | --- | --- |
| `checkout.shipping.allowed_methods` | `capability` | set, `intersect` | global, channel, division, site, account, seller | global `[GROUND, PICKUP]`; web `[GROUND, NEXT_DAY]`; south `[GROUND, PICKUP]` |
| `checkout.container_order.allowed` | `capability` | boolean, `all` | channel, division, site, account | north `false` (veto) |
| `checkout.order.allowed_max_lines` | `capability` | integer, `min` | global, division, site | global `500`; south `100`; site_001 `25` |
| `checkout.pickup.lead_hours` | `configuration` | integer, `override`, `combine_across_axes: max` | global, division, site, seller | global `2`; south `6`; seller acme `8` |
| `checkout.pickup.window` | `configuration` | object, `deep_merge`, complete default | global, division, site | default `{ open 08:00, close 18:00 }`; global `open 09:00`; north `close 15:00`; site_001 `close 16:00` |

- The three `capability` keys use the `capability` kind of `tierwise:recommended`
  ([KIND-2]). Under that kind no authored value equals the identity of its strategy
  ([KIND-6]), so no value sets `true` under `all`, or the full universe under `intersect`.
- `checkout.pickup.lead_hours` is settable on an axis, so it declares
  `combine_across_axes` ([AXIS-3]). Its axis result and hierarchy result are compared
  with `max` ([AXIS-7], [AXIS-8]).
- `checkout.pickup.window` is not settable on an axis, so it must not declare
  `combine_across_axes` ([AXIS-6]). It does not. Its default is a complete object, and its optional
  field `slot_minutes` may be omitted ([MERGE-10], [TYPE-62]). Scopes may give partial
  objects ([MERGE-11]).

## Features and rules

### `checkout.order_submission` (per order)

- Returns `ALLOW` or `DENY`. `if_fact_missing: DENY`. Bindings at the root and at
  `division`.
- The `credit_check` policy allows an order on good credit. On credit hold, it allows
  an order only when every item's stock type is in the `allowed_on_credit_hold` setting
  ([RULE-31]). A binding may supply the `list_of` setting. A binding that omits it
  takes the setting's default ([BIND-23]).
- The root binding omits `allowed_on_credit_hold`, so its default `[]` applies. A
  condition whose list resolves to `[]` does not hold ([RULE-34]), so under the root
  binding an order on credit hold is denied whatever its stock types. Division `south`
  supplies a list and so allows its own stock types. `rules/dead-rule` does not flag the
  rule, because it ignores membership lists given by a setting reference ([LINT-28]).
- Division `north` binds `allow_all`, which has no rules and allows every order.

### `checkout.invoice_release` (per invoice)

- Returns `RELEASE` or `HOLD`. `if_fact_missing: HOLD`. Bindings at the root and at
  `division` `south`.
- The facts are the `order.payment_state` values of the invoice's child orders. Each
  condition needs a quantifier because the facts are children ([RULE-13]).
- A rule holds `HOLD` when any order's state is in `held_states` ([RULE-32]). It holds
  `RELEASE` when every order's state is in `released_states` ([RULE-31]). An empty
  order list therefore falls through to the default `HOLD` ([RULE-33]).
- At `south`, a pending order does not hold the invoice. A failed payment still does.

## Examples

Every binding of both features has at least one example, as
`examples/required-per-binding` requires ([LINT-29], [LINT-30]).

- `checkout.order_submission`: six examples. The root binding has one (channel `web`).
  `north` and `south` each have examples. The mixed basket covers the `all_in`
  mixed-collection check ([LINT-33]). Two examples test the missing-fact precheck and
  the empty child collection.
- `checkout.invoice_release`: six examples. The root binding has four, and `south` has
  two. The `any_in` mixed-collection check ([LINT-33]) is covered under the root
  binding (`PENDING` held, `PAID` not) and under `south` (`FAILED` held, `PAID` not).

## Results derived from the clauses (non-normative)

These results were worked out by hand from the clauses above. No tool has run them.
They are the expected values for a resolve or decide call, for a reader who wants to
check the project against the merge rules.

| Call | Context | Result | Why |
| --- | --- | --- | --- |
| resolve `checkout.shipping.allowed_methods` | `{}` | `[GROUND, PICKUP]` | Root value only ([MERGE-17]) |
| resolve `checkout.shipping.allowed_methods` | `channel: web`, `division: south` | `[GROUND]` | Intersection of three sets ([MERGE-17]) |
| resolve `checkout.container_order.allowed` | `division: north` | `false` | One veto ([MERGE-17]) |
| resolve `checkout.container_order.allowed` | `division: south` | `true` | No contributor, so the identity ([MERGE-5]) |
| resolve `checkout.order.allowed_max_lines` | `division: south`, `site: site_001` | `25` | Smallest bound ([MERGE-17]) |
| resolve `checkout.pickup.lead_hours` | `{}` | `2` | Root value; no axis value ([AXIS-7]) |
| resolve `checkout.pickup.lead_hours` | `division: south` | `6` | Hierarchy result only |
| resolve `checkout.pickup.lead_hours` | `division: south`, `seller: acme` | `8` | `max(6, 8)` ([AXIS-8]) |
| resolve `checkout.pickup.window` | `{}` | `{ open: "09:00", close: "18:00" }` | Default merged with the root partial ([MERGE-25]) |
| resolve `checkout.pickup.window` | `division: north`, `site: site_001` | `{ open: "09:00", close: "16:00" }` | Root, north and site fields merged in path order ([MERGE-23]) |
| decide `checkout.order_submission` | `division: south`; `customer.active: true`; customer on credit hold; items `STOCKED`, `DROP_SHIP` | `DENY` | Mixed basket. No rule matches, so the reason is `DEFAULT` ([RULE-50], [RULE-51]) |
| decide `checkout.invoice_release` | `channel: web`; orders `PAID`, `PENDING` | `HOLD` | `any_in` over the root `held_states` |

A site that the catalog does not list, such as `site_099`, is skipped under
`on_unknown_node: ignore`. It is recorded in `skippedScopes` and triggers no provider
call ([CTX-14], [CTX-16]).

## Validation status

No compiler is in this repository yet. The `java/` modules hold only package stubs, so
`tierwise validate` cannot run on this project. The project is written to produce zero
findings under the specification, and the reasoning is recorded above. The examples
have been checked by hand. The manifest, the two schema files, the two rules files and
the values files also match the structure schemas in `spec/schemas/`, which check
structure only. Only a compiler run can confirm the zero-findings claim.

The claim also rests on one reading of section 7: that a feature declaration may carry
`description` (see the first open point).

## Open points

- (OPEN) Feature `description`. The prose of [FEAT-1] and [FEAT-2] lists the required
  members of a feature and does not name `description` as an allowed one. [LINT-44]
  requires a `description` on each feature, and the `featureDeclaration` definition in
  `spec/schemas/schema-file.schema.json` allows it. This project declares one on each
  feature, following the schema and [LINT-44]. Recommended resolution: add `description`
  to the members that [FEAT-1] allows, so that the prose and the schema agree.
- (OPEN) The `capability` kind of `tierwise:recommended` supplies
  `on_source_failure: deny` for the keys `checkout.shipping.allowed_methods` and
  `checkout.container_order.allowed`. The keys declare none themselves ([KIND-9]). If
  the preset stops supplying it, these keys are reported as `E_MISSING_SOURCE_FAILURE`
  ([SVC-1], [SVC-2]) and need a declaration of their own.
- (OPEN) [RULE-34] says that a rule whose list setting resolves to `[]` is flagged by
  `rules/dead-rule` ([LINT-26]). [LINT-26] flags only a literal empty list, and
  [LINT-28] excludes setting references. This project follows [LINT-28] and expects no
  finding for `credit-hold-allowed-stock` under the root binding. Recommended
  resolution: change [RULE-34] to cite [LINT-26] and [LINT-28] for the literal case only.
