# spec/conformance/bundles

Canonical bundle files for the Phase 1 conformance suite (section 12, [VEC-1], [VEC-9]).
Each file here is the exact byte form that section 10 defines ([BUNDLE-2], [DET-1]).
This README indexes the bundles, names the sample each one compiles, and records the
compilation decisions a reader needs to check a bundle against the clauses.

The `commerce.json` bundle is documented in the section below. The other two bundles are
documented in sibling files in this directory (that is the layout on disk), which this index
links but does not review. Files under `invalid/` are documented in `invalid/README.md`.

| Bundle | Source sample | Documented in |
| --- | --- | --- |
| `commerce.json` | `samples/commerce/` (sample domain A) | [commerce.json](#commercejson) in this file |
| `retail-pos.json` | `samples/retail-pos/` (sample domain B) | [README-retail-pos.md](README-retail-pos.md) |
| `saas-entitlements.json` | `samples/saas-entitlements/` | [README-saas-entitlements.md](README-saas-entitlements.md) |

Each file was produced by hand, because no reference compiler exists yet. The content object
was written as JSON outside the repository, then turned into canonical bytes by
`js/spec-tools/canonicalize.mjs`. The bytes and the digest are those that [BUNDLE-2] and
[BUNDLE-6] define.

> Implementation note (non-normative): the canonical tool is a JavaScript file in the
> workspace. It checks the content against `spec/schemas/bundle.schema.json` first and writes
> nothing when that check fails. The bytes it writes are the same for any compiler, because
> [DET-1] fixes them.

## commerce.json

> Example (non-normative). This section records one bundle and the decisions taken while
> compiling its sample. It does not define the sample domain.

- Source: `samples/commerce/`, extending `tierwise:recommended`.
- Digest (`bundleVersion`): `sha256:ab1cd3a96be4ab7569ccaba61cb65dbd14b5efd8239de35f16643ab791af6859`
  ([BUNDLE-5], [BUNDLE-6]). The file has no trailing newline ([BUNDLE-4]).
- Content members present: `bundleFormatVersion` `"1"` ([BUNDLE-16]), `specVersion` `"0.1"`
  ([BUNDLE-17], [BUNDLE-18]), `hierarchy`, `entities`, `facts`, `keys`, `values`, `features`
  ([BUNDLE-14], [BUNDLE-15]).
- Contents: 6 levels and 1 axis, 4 entities, 4 facts, 5 keys, 2 features with 3 policies in
  total and 5 bindings. The `values` scopes are `global`, `channel`, `division`, `site` and `seller`.

### Compilation decisions

| Decision | Clauses | Result in the bundle |
| --- | --- | --- |
| Preset kinds are not written. Each key keeps only its kind name | [BUNDLE-62], [BUNDLE-63], [BUNDLE-109] | `"kind": "capability"` on three keys, `"kind": "configuration"` on two. The kind definitions, `naming` and `lint` are dropped |
| A key settable at a service-sourced level takes its kind's source-failure behaviour | [KIND-9], [BUNDLE-59], [BUNDLE-61], [SVC-1] | `onSourceFailure: "deny"` on `checkout.shipping.allowed_methods` and `checkout.container_order.allowed`, both settable at `account` |
| A key not settable at a service-sourced level omits `onSourceFailure`, even though its kind supplies a behaviour (`skip` for `configuration`, `deny` for `capability`). The behaviour applies only where the key is settable at a service-sourced level | [BUNDLE-59], [KIND-9], [SVC-7] | No `onSourceFailure` on `checkout.order.allowed_max_lines` (kind `capability`) or on `checkout.pickup.lead_hours` and `checkout.pickup.window` (kind `configuration`) |
| Only the order-dependent key settable on an axis gets `combineAcrossAxes` | [AXIS-3], [AXIS-5], [AXIS-6], [BUNDLE-57] | `combineAcrossAxes: "max"` on `checkout.pickup.lead_hours` only. It is settable at `seller`. The deep-merged `checkout.pickup.window` is not settable on an axis, so it has none |
| Commutative keys have no `default` | [MERGE-4], [BUNDLE-55] | No `default` on the three capability keys. `checkout.pickup.lead_hours` (`override`) and `checkout.pickup.window` (`deep_merge`) carry one |
| An omitted `values` member means `files` | [SCOPE-25], [BUNDLE-35] | `values: "files"` on every scope that the source does not mark `service` |
| `account` and `user` are service-sourced and open | [SCOPE-24], [SCOPE-26], [BUNDLE-34], [BUNDLE-36], [BUNDLE-42] | `nodes: "open"`, `values: "service"`, no `onUnknownNode`. Neither has a `values` entry ([BUNDLE-67], [SVC-13]) |
| `settableAt` lists levels in declared order, then axes | [BUNDLE-54] | `checkout.shipping.allowed_methods`: `[global, channel, division, site, account, seller]` |
| Closed catalogs are sorted by code point and carry `onUnknownNode`. The `site` catalog is embedded | [BUNDLE-38], [BUNDLE-39], [BUNDLE-40], [BUNDLE-42], [BUNDLE-43], [DET-6], [SCOPE-31], [SCOPE-32] | `channel` nodes `["partner_portal", "web"]` (the source lists `web` first). `site` nodes `["site_001", "site_002"]` from `catalogs/sites.json`, with `onUnknownNode: "ignore"` |
| The root level's values and bindings sit under the level name `global`, with node key `""` | [BUNDLE-64], [BUNDLE-66], [BUNDLE-105], [BIND-2] | `values.global[""]` and `bindings.global[""]` |
| A scope with no node entries is absent from `values` | [BUNDLE-22] (first sentence), [BUNDLE-67] | No `values` entry for `account` or `user`, which are service-sourced and hold no node entries |
| A node entry that holds no key values is absent | [BUNDLE-22] (second sentence) | No entry for `channel` node `partner_portal`, `division` node `west` or `site` node `site_002`. They are in the catalogs but no source file gives them a value |
| Set universes keep their declared order. Set values follow universe order | [BUNDLE-74], [BUNDLE-75], [TYPE-50], [TYPE-51] | `universe: ["GROUND", "PICKUP", "NEXT_DAY"]`. Set values such as `["GROUND", "NEXT_DAY"]` follow it |
| An `enum` value list, and the `returns` list, is in code point order | [BUNDLE-75], [BUNDLE-82] | `item.stock_type`: `["DROP_SHIP", "SPECIAL_ORDER", "STOCKED"]`. `order.payment_state`: `["FAILED", "PAID", "PENDING"]`. `invoice` returns `["HOLD", "RELEASE"]`, from the source order `RELEASE, HOLD` |
| A `listOf` value, in a binding or a default, is in code point order | [BUNDLE-75], [BUNDLE-89], [BUNDLE-90] | The division `south` binding writes `allowed_on_credit_hold: ["SPECIAL_ORDER", "STOCKED"]`, from the source order `[STOCKED, SPECIAL_ORDER]`. The `released_states` binding writes `["PAID", "PENDING"]`, which is already in order |
| A single-name `belongs_to` or `has_many` is a one-element array, and an absent one is `[]` | [BUNDLE-46], [BUNDLE-47] | `order.belongsTo: ["customer"]`, `invoice.hasMany: ["order"]`, `order.hasMany: ["item"]`. Only direct edges are written |
| Type members stay inside the key's `type` object | [BUNDLE-52], [BUNDLE-53], [TYPE-7], [BUNDLE-73], [BUNDLE-74] | `{type: "integer", min: 1}` on `checkout.order.allowed_max_lines`. `{type: "integer", min: 0, max: 168}` on `checkout.pickup.lead_hours`. `{type: "set", universe: [...]}` on the set key. `fields` with `format` and `optional` inside the object type of `checkout.pickup.window` |
| Key `description` and `owner` are dropped. A key keeps only `type`, `combine`, `settableAt` and the optional members | [KEY-9], [BUNDLE-53], [BUNDLE-109] | The five keys carry no `description` or `owner` member |
| A fact's entity is the prefix of its name. Its `source` and `description` are dropped | [BUNDLE-49], [FACT-3], [FACT-10], [FACT-45], [BUNDLE-109] | Four facts, each `{entity, type}` only |
| An optional object field is written only when optional, and omitted from a default that does not set it | [BUNDLE-82], [TYPE-10], [TYPE-66], [MERGE-10] | `slot_minutes` has `optional: true`. The default `{open: "08:00", close: "18:00"}` is complete, so `slot_minutes` is absent |
| Partial object values are stored as the source gives them | [MERGE-11], [MERGE-12], [BUNDLE-73] | The root value `{open: "09:00"}` and the `north` value `{close: "15:00"}` of `checkout.pickup.window`. The `default` is the starting value, so only it has to be complete ([MERGE-10]) |
| A rule's `when` is sorted by fact, and rules keep source order | [BUNDLE-93], [BUNDLE-96], [RULE-8] | `customer.active` before `customer.credit_hold` before `item.stock_type`. Rules `good-credit` then `credit-hold-allowed-stock` |
| Setting references stay symbolic | [BUNDLE-103], [BUNDLE-104], [RULE-4] | `"value": "$allowed_on_credit_hold"`, `"$held_states"`, `"$released_states"` |
| A setting is `{default, listOf}` or `{default, valueOf}` | [BUNDLE-86], [BUNDLE-87], [POL-15] | `allowed_on_credit_hold` has `default: []` (a valid empty list). `held_states` and `released_states` have list defaults |
| `requiredFacts` is derived from every rule of the policy, including rules that cannot run, in code point order | [BUNDLE-91], [BUNDLE-92], [BUNDLE-110], [RULE-40] | `credit_check`: `["customer.active", "customer.credit_hold", "item.stock_type"]`. `payment_gate`: `["order.payment_state"]`. `allow_all`: `[]` |
| Binding `settings` are exactly those the source binding writes. Policy defaults are not copied | [BUNDLE-107], [BUNDLE-108], [BIND-23] | The root bindings and `division north` write `settings: {}`. The `invoice` root binding also writes `{}`, so its `held_states` and `released_states` take the defaults |
| `binding_settable_at` lists levels in level order and includes the root | [BUNDLE-81], [FEAT-18], [BUNDLE-54] | `checkout.order_submission`: `[global, division, site]`. `checkout.invoice_release`: `[global, division]` |
| Feature `hitPolicy` and `ifFactMissing` are written as declared | [BUNDLE-78], [BUNDLE-79], [BUNDLE-80] | `hitPolicy: "FIRST"`. `ifFactMissing: "DENY"` (order), `"HOLD"` (invoice) |
| Feature and rule `description` text is dropped. A rule `name` is kept | [BUNDLE-95], [BUNDLE-109], [FEAT-1] | The `name` of each rule is written. No feature has a `description` member |
| Examples are compile-time only. Each one was run by hand, and all twelve pass | [POL-47], [POL-41], [POL-42], [BUNDLE-109] | See the example check below. No example is written to the bundle |
| No provenance and no sidecar is written | [BUNDLE-139], [BUNDLE-140], [DET-3] | The bundle holds no provenance. Its bytes do not depend on build data |
| Namespace, ownership, lint, naming and presets are not written | [BUNDLE-109], [NS-6] | The `checkout` namespace and the `tierwise:recommended` preset leave no trace beyond the kind names |

### Example check (non-normative)

Each example was evaluated by hand against the bundle, under section 8 and the selection rules of
section 7. No tool ran it. [POL-47] requires that all examples pass before a bundle is written.

| Feature | Example | Expected | Derived result and reason |
| --- | --- | --- | --- |
| `checkout.order_submission` | Mixed basket on credit hold is denied | `DENY` | `division: south` binding. Precheck passes. `good-credit` fails on `credit_hold`. `credit-hold-allowed-stock` fails on `DROP_SHIP` ([RULE-31]). Default `DENY` |
| `checkout.order_submission` | Credit hold with only allowed stock is allowed | `ALLOW` | `credit-hold-allowed-stock` holds ([RULE-31]) |
| `checkout.order_submission` | Empty order on credit hold is denied | `DENY` | Present empty collection passes the precheck ([FACT-18]). `all_in` on empty does not hold ([RULE-33]). Default `DENY` |
| `checkout.order_submission` | Unknown credit status is denied | `DENY` | `customer.credit_hold` missing, so `if_fact_missing` ([RULE-42], [RULE-43]) |
| `checkout.order_submission` | North allows everything | `ALLOW` | `division: north` binds `allow_all`. Empty precheck ([RULE-45]). No rules, default `ALLOW` ([RULE-50]) |
| `checkout.order_submission` | Good credit on the web channel is allowed under the root binding | `ALLOW` | `channel: web` has no binding, so the root binding is selected ([BIND-16], [BIND-17]). `good-credit` holds |
| `checkout.invoice_release` | A pending order holds the invoice under the root binding | `HOLD` | `any-held-order` holds: `PENDING` is in the default `held_states` ([RULE-32]) |
| `checkout.invoice_release` | Paid orders release the invoice under the root binding | `RELEASE` | `any-held-order` fails. `all-orders-released` holds: `PAID` is in `released_states` ([RULE-31]) |
| `checkout.invoice_release` | Pending and paid orders release the invoice at division south | `RELEASE` | South `held_states` is `[FAILED]`, so `any-held-order` fails. `PENDING` and `PAID` are both in the south `released_states` |
| `checkout.invoice_release` | A failed payment holds the invoice at division south | `HOLD` | `FAILED` is in the south `held_states` ([RULE-32]) |
| `checkout.invoice_release` | An invoice with an empty order list is held | `HOLD` | Empty collection passes the precheck. Neither `any_in` nor `all_in` holds on it ([RULE-33]). Default `HOLD` |
| `checkout.invoice_release` | An invoice with no order data is held | `HOLD` | `order.payment_state` missing, so `if_fact_missing: HOLD` ([FACT-17], [RULE-42]) |

### Checks run

- `pnpm --dir js run spec:check -- --no-write` passes, with no problem for this file. It was run
  with `--no-write`, so `spec/conformance/COVERAGE.md` was not rewritten (it is owned by another item).
- A scratch check outside the repository confirmed the clauses that the schema does not state:
  code point order of each array, the derived `requiredFacts`, `when` order, symbolic setting
  references and their kinds, catalog admission of every node, `settableAt` order, the
  identity rule of [KIND-6] on the capability keys, and the required `combineAcrossAxes`,
  `default` and `onSourceFailure` members. It found no problem.

## Open points

- (none) No compilation decision for this bundle is marked (OPEN).
- The sample's own open points are listed in `samples/commerce/README.md`, "Open points". Only
  two touch this bundle. The source-failure value that the `capability` kind supplies is
  followed under [KIND-9] and [BUNDLE-61]. The empty membership list of the root binding is
  stored as an empty `listOf` default ([RULE-34], [LINT-26], [LINT-28]).
