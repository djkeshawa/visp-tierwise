# Edge bundle for the facts, effects and operation-error vectors

`edge-facts.json` is a small focused bundle for the evaluate vectors under `evaluate/facts/`. It is not
compiled from a sample. Its content was written by hand as JSON in a scratch directory outside the
repository, then turned into canonical bytes by `js/spec-tools/canonicalize.mjs`, which checks the content
against `bundle.schema.json` first. Nothing in the file was edited after canonicalization.

- `bundleVersion`: `sha256:f9de5c2b27288aae83c618a1e780f5a42837a47cdabe5f7c58b2fe5d96a193dc`
  ([BUNDLE-5], [BUNDLE-6]). The file has no trailing newline ([BUNDLE-4]).
- Content members: `bundleFormatVersion` `"1"`, `specVersion` `"0.1"`, `hierarchy`, `entities`, `facts`,
  `keys` (empty), `values` (empty), `features` (four features, all with one root binding).
- Check: `pnpm --dir js run spec:check -- --no-write` validates this file (structure, digest, canonical
  bytes, clause citations). That check runs no loader. The loader rules that this bundle could break
  ([BUNDLE-119] to [BUNDLE-136]) were checked only by a scratch script outside the repository, which cannot be
  reproduced from it. They stay unverified until a loader exists ([BUNDLE-118] to [BUNDLE-136] have unit-test
  enforcers in `enforcers.d/E7.yaml`).
- Open point (OPEN-E6-1): whether a vector may write an unpaired surrogate as a YAML escape such as `"\uD800"`.
  [VEC-6] and [VEC-7] do not settle it, and the check tool's loader accepts the escape. No vector carries one, so
  [NORM-8] for FactSet strings has an enforcer in `enforcers.d/E6.yaml`. Recommended resolution: decide in
  section 12 whether the escape is well-formed. If it is, add a `decide` vector on `edge.order_gate` with
  `order.note: "\uD800"` expecting `E_FACTS_INVALID` ([FACT-35], [NORM-8]) once both runners' YAML parsers are
  confirmed to accept it, and drop the enforcer. If it is not, make the loader reject it with `E_PARSE`.

## Hierarchy and entities

| Element | Declaration | Clauses |
| --- | --- | --- |
| Levels | `global` (root), `region`. No axes. | BUNDLE-30, BUNDLE-31 |
| Scope `region` | Closed catalog `north`, `south`, `onUnknownNode: error`, `values: files`. A context node outside the catalog raises `E_UNKNOWN_NODE`. | BUNDLE-38, BUNDLE-43, CTX-12 |
| `customer` | No edges. An ancestor of `order`, so its facts are single-value facts of an order decision. | ENTITY-10, FACT-13 |
| `order` | `belongsTo: customer`, `hasMany: line`. The decided entity of every feature. | ENTITY-9, ENTITY-11 |
| `line` | `hasMany: part`. The child entity of `order`. | ENTITY-11, FACT-14 |
| `part` | No edges. A grandchild of `order`, so its facts are not eligible. | FACT-15 |
| `invoice` | `hasMany: order`, but not an ancestor of `order`. Its facts are not eligible. | ENTITY-12, FACT-15 |

## Facts

| Fact | Type | Role in the vectors |
| --- | --- | --- |
| `customer.active` | boolean | Single-value fact of an order decision, through the ancestor `customer`. |
| `order.label` | enum `café`, `tea`, `water` | Enum with a non-ASCII NFC value. Tests NFC and exact comparison, and null effects. |
| `order.note` | string | String with no format. Tests exact comparison of a non-NFC value. |
| `order.total` | decimal | Decimal single-value fact. Tests canonical comparison and number rejection. |
| `order.lines` | integer, `min: 0`, `max: 100` | Tests inclusive declared bounds. |
| `order.units` | integer, unbounded | Tests the range limit of integer values at 2^53. |
| `line.kind` | enum `BULK`, `SINGLE` | Child fact of `line`. Tests `any_in` and missing child facts. |
| `line.amount` | decimal | Child decimal fact. Tests canonical comparison in child maps. |
| `invoice.status` | enum `OPEN`, `SENT` | Declared, not eligible for an order decision. |
| `part.code` | string | Declared, a grandchild fact, not eligible for an order decision. |

## Features

All four features have `decidedPer: order`, `hitPolicy: FIRST`, `bindingSettableAt: [global]` and one
root binding (`bindings.global[""]`), so every decision selects the root binding ([BIND-13], [BIND-19]).

| Feature | `returns` | `ifFactMissing` | Policy and rules (in order) | `requiredFacts` |
| --- | --- | --- | --- | --- |
| `edge.order_gate` | enum `ALLOW`, `DENY` | `DENY` | `gate`, default `DENY`: `exact-total` (`order.total` eq `"1.5"`), `label-cafe` (`order.label` eq `café`), `bulk-line-active` (`customer.active` eq `true` and `line.kind` any_in `[BULK]`), `note-cafe` (`order.note` eq `café`), all then `ALLOW` | `customer.active`, `line.kind`, `order.label`, `order.note`, `order.total` |
| `edge.nullable_effect` | enum `ALLOW`, `DENY`, nullable | `null` | `pick`, default `null`: `tea-clears` (`order.label` eq `tea`, then `null`), `cafe-allows` (`order.label` eq `café`, then `ALLOW`) | `order.label` |
| `edge.object_quote` | object `amount` (decimal), `note` (string, optional), `tier` (enum `GOLD`, `SILVER`) | `{amount: "0", tier: SILVER}` | `quote`, default `{amount: "0", tier: SILVER}`: `bulk-gold` (`line.kind` any_in `[BULK]`, then `{amount: "10.5", note: bulk, tier: GOLD}`), `total-gold` (`order.total` eq `"2"`, then `{amount: "2", tier: GOLD}`) | `line.kind`, `order.total` |
| `edge.decimal_probe` | enum `HIT`, `IGNORED`, `MISS` | `MISS` | `probe`, default `IGNORED`: `total-in-set` (`order.total` in `["0", "0.5", "1.5", "7.1"]`, then `HIT`), `line-amount-probe` (`line.amount` any_in `["2.5"]`, then `HIT`) | `line.amount`, `order.total` |

## Compilation decisions

| Decision | Clauses | Result in the bundle |
| --- | --- | --- |
| A child entity is declared only on its parent, as in `commerce.json`. | BUNDLE-46, ENTITY-11 | `line` and `part` have `belongsTo: []`. Only `order` and `line` have `hasMany`. |
| Enum values and enum `returns` lists are in code point order. | BUNDLE-75, BUNDLE-82 | `café`, `tea`, `water`; `BULK`, `SINGLE`; `HIT`, `IGNORED`, `MISS`. |
| The `in` operand list is in code point order and holds canonical decimals. | BUNDLE-75, NORM-9, NORM-13, RULE-15 | `["0", "0.5", "1.5", "7.1"]`. |
| Literals and effect amounts are canonical decimals. | NORM-9, NORM-10, TYPE-26 | `"1.5"`, `"2"`, `"0"`, `"10.5"`, `"2.5"`, `"7.1"`. |
| The string `café` is stored in NFC, U+00E9. | NORM-1, NORM-6 | Enum values, the `eq` literals of `order.label`, `order.note` and `cafe-allows`. |
| `null` effects appear only where the feature is nullable. | BUNDLE-24, FEAT-9 | `edge.nullable_effect` only: `ifFactMissing`, the policy default and the `tea-clears` `then`. |
| The optional object field is written only where the effect gives it. | BUNDLE-82, TYPE-66 | `note` is in `bulk-gold` only, so `total-gold` has no `note` member. |
| An `integer` with declared bounds stores them, inclusive. | TYPE-13, TYPE-19, FACT-9 | `order.lines`: `min 0`, `max 100`. `order.units` has no bounds. |
| `requiredFacts` is the distinct fact names of every `when`, in code point order. | BUNDLE-91, BUNDLE-92, BUNDLE-136 | As the feature table above. |
| Each `when` is sorted by fact. | BUNDLE-96, RULE-8 | `bulk-line-active` lists `customer.active` before `line.kind`. |
| The root binding is stored under the root level name with node key `""`. | BUNDLE-64, BUNDLE-66, BIND-2 | `bindings.global[""]` for each feature. |
| `region` has no binding. A decision that names a `region` node uses the root binding without error. | BIND-16, BIND-19, EVAL-21 | `check-order/decide-region-no-binding-root`. |
| `keys` and `values` are empty. The vectors need no key lookup through the edge bundle. | BUNDLE-51, BUNDLE-64 | `keys: {}`, `values: {}`. The commerce bundle serves the key lookup vectors. |

## Vector families

The 107 evaluate vectors under `evaluate/facts/` use this bundle, except eight resolve vectors that use
`bundles/commerce.json` for key lookup and context checks. Each family, its directory and its count:

| Directory | Count | What it tests |
| --- | --- | --- |
| `shape/` | 26 | FactSet shape, eligibility and child-map rules: [FACT-22] to [FACT-33], plus two positive child-order cases |
| `values/` | 20 | Value types, ranges (both ends of the integer range) and decimal syntax: [FACT-35] to [FACT-41], [TYPE-12], [TYPE-18], [TYPE-19] |
| `text/` | 5 | Exact code-point comparison of strings and enum values, with a non-NFC input: [FACT-36], [NORM-3] to [NORM-7] |
| `decimal/` | 10 | Runtime canonicalisation and membership of decimals, in facts and child maps: [NORM-9] to [NORM-15], [RULE-37] |
| `missing/` | 11 | Missing facts and their effects: [FACT-16] to [FACT-21], [FACT-24], [RULE-42] to [RULE-44], [FEAT-3] |
| `effects/` | 7 | Effects of the nullable enum and object forms, and effects equal to contribution labels: [FEAT-7], [FEAT-9] to [FEAT-11] |
| `check-order/` | 23 | Operation check order and error precedence: [EVAL-9], [EVAL-12], [EVAL-16] to [EVAL-21], [EVAL-30], [EVAL-32] to [EVAL-36], [FACT-44] |
| `unknown-names/` | 5 | Undeclared, empty and differently cased feature and key names: [EVAL-34], [EVAL-35], [EVAL-37] |

## Checks run

- `pnpm --dir js run spec:check -- --no-write` validates every file of work item E6 (schema, ids, clause
  citations, bundle digest and canonical bytes). `--no-write` leaves `spec/conformance/COVERAGE.md` untouched.
  A run failed only on `compile/keys` and `compile/rules`, which another item owns.
- The drafter's scratch reference model (outside the repository) agreed with the first 100 expectations, and the
  reviewer re-derived the other expectations by hand from the clauses cited. Neither check is a runner and
  neither is reproducible from the repository, so the expectations stay unconfirmed by a runner until the
  evaluators exist.
