# Retail POS bundle: compilation decisions

The bundle `retail-pos.json` in this directory is the compiled content of `samples/retail-pos/`
(domain B, no preset). It was produced by hand, following section 10 and the clauses of sections 1
to 8 that it cites. The canonical bytes come from `js/spec-tools/canonicalize.mjs`, run on a content
object written in a scratch directory outside the repository. Nothing in the bundle was edited after
canonicalization.

- `bundleVersion`: `sha256:5acebbd8528de939c2ca351ef15f2845f9d156eb6c4f275c57501c9edb039df0`
- Check: `pnpm --dir js run spec:check -- --no-write` passes. Stage (c) validates this bundle and reports no
  problem. `--no-write` was used so that `COVERAGE.md` was not rewritten. The coverage gaps are expected.
- Open points: none. Each decision below is decided by a clause.

Abbreviations: "source" is the project's YAML files; "content" is the bundle's `content` member.

## 1. Envelope and content members

| Decision | Clauses |
| --- | --- |
| The envelope is `{bundleVersion, content}`, written as JCS with no whitespace and no trailing newline. | BUNDLE-1, BUNDLE-2, BUNDLE-4, BUNDLE-7 to BUNDLE-9 |
| `bundleVersion` is the SHA-256 of the JCS bytes of `content` alone. | BUNDLE-5, BUNDLE-6 |
| `bundleFormatVersion` is `"1"` and `specVersion` is `"0.1"`, equal to the manifest's `specVersion`. | BUNDLE-16 to BUNDLE-18, MANIFEST-4, MANIFEST-5 |
| `content` has exactly the eight members, all present. The sample has no empty member. | BUNDLE-14, BUNDLE-15, BUNDLE-19 |
| Source keys that differ from content members are renamed per Table 10.1 (for example `settable_at` to `settableAt`, `hit_policy` to `hitPolicy`, `value_of` to `valueOf`, `list_of` to `listOf`, `decision_hierarchy` to `hierarchy`). Other names keep their source spelling. | BUNDLE-25 to BUNDLE-28 |

## 2. Hierarchy

| Decision | Clauses |
| --- | --- |
| `levels` is `["platform","region","store","terminal"]`, root first and in declared order. `axes` is `["brand"]`. | BUNDLE-30, BUNDLE-31, SCOPE-6, SCOPE-7, SCOPE-10 |
| `scopes` has one member each for `region`, `store`, `terminal` and `brand`, and none for the root `platform`. | BUNDLE-32, SCOPE-12, SCOPE-14 |
| `values` is `"files"` for `region`, `store` and `brand`, where the source omits it. It is `"service"` for `terminal`. | BUNDLE-33 to BUNDLE-36, SCOPE-25, SCOPE-26 |
| `nodes` lists are in code point order: `coastal, inland, upland`; `harbor_street, meadow_square`; `harbor_foods, meadow_goods`. | BUNDLE-38, BUNDLE-39, DET-5, DET-6 |
| `terminal` has `nodes: "open"` and no `onUnknownNode`. `onUnknownNode` is written only on closed catalogs, with `error` for `region` and `brand` and `ignore` for `store`. | BUNDLE-37, BUNDLE-42, BUNDLE-43, SCOPE-22, SCOPE-24 |
| The `terminal` level is service-sourced, so the bundle has no `values` entry for it. | BUNDLE-67, SVC-13, SCOPE-47 |

## 3. Entities and facts

| Decision | Clauses |
| --- | --- |
| Each entity has `belongsTo` and `hasMany` arrays of its direct edges. A single name becomes a one-element array, and an absent one becomes `[]`. `basket` gives `belongsTo: ["shopper"]` and `hasMany: ["line"]`. | BUNDLE-44 to BUNDLE-46, ENTITY-4, ENTITY-5 |
| No inverse edges are written, so `line` has `belongsTo: []` and `shopper` has `hasMany: []`. | BUNDLE-47, ENTITY-11, ENTITY-12 |
| Each fact has exactly `entity` and `type`. The `source` member and the `description` member are excluded. | BUNDLE-48, BUNDLE-49, BUNDLE-109, FACT-10 |
| The `values` of each `enum` fact are in code point order: `shopper.tier` gives `GOLD, STAFF, STANDARD`; `basket.tender` gives `CARD, CASH, GIFT_CARD`; `line.category` gives `ALCOHOL, GENERAL, GIFT_CARD_SALE, GROCERY, PHARMACY`. | BUNDLE-75 |

## 4. Keys

| Decision | Clauses |
| --- | --- |
| The seven keys carry exactly `type`, `combine`, `settableAt`, and the optional members that apply. `description` and `owner` are excluded. | BUNDLE-52, BUNDLE-53, BUNDLE-109, KEY-9 |
| `settableAt` lists scopes in scope order (levels in declared order, then axes). The source list of `pos.receipt.tax_label` is `[platform, region, store, brand, terminal]`, and the bundle writes `["platform","region","store","terminal","brand"]`. The other keys were already in that order. | BUNDLE-54 |
| A commutative key (`min`, `max`, `any`, `union`, `intersect`) has no `default`. These are `pos.basket.line_limit`, `pos.tender.allowed`, `pos.fraud.review_reasons`, `pos.offline.allowed` and `pos.pricing.max_discount_percent`. | MERGE-1, MERGE-4, BUNDLE-55 |
| `combineAcrossAxes` is written only for the two order-dependent keys that are settable at the axis `brand`: `pos.receipt.header` and `pos.receipt.tax_label`, both `axis_wins`. No commutative key has it. | AXIS-2, AXIS-3, AXIS-4, BUNDLE-57, BUNDLE-58 |
| `onSourceFailure` is written exactly for the keys settable at the service-sourced `terminal`. `pos.basket.line_limit` and `pos.tender.allowed` take `deny` from their kind `guard`. `pos.receipt.tax_label` takes `skip` from its kind `setting`. `pos.offline.allowed` declares `skip` on the key. | BUNDLE-59 to BUNDLE-61, KIND-9, KEY-8, SVC-1 |
| `pos.fraud.review_reasons`, `pos.pricing.max_discount_percent` and `pos.receipt.header` are not settable at a service-sourced level. The `setting` kind of `pos.receipt.header` declares `skip`, but the bundle omits `onSourceFailure` because no service-sourced level is settable for the key. | SVC-7, BUNDLE-59 |
| A key's `kind` member holds only the kind's name (`guard` or `setting`). The kind definitions, with their strategies, naming and defaults, are not written. | BUNDLE-62, BUNDLE-63, BUNDLE-109 |
| The `deny` values of the guard keys are satisfied: `min` has a `min` bound, and `intersect` uses a universe. | SVC-11, SVC-12, KIND-5, KIND-6 |
| The `default` of `pos.receipt.header` is `{title: "Thank you", show_store_address: true}`, which is a complete object. The `default` of `pos.receipt.tax_label` is `"Tax"`. The `kind` `setting` requires both. | MERGE-7, MERGE-10, TYPE-62, KIND-8, KIND-10 |
| The set `universe` of `pos.tender.allowed` and `pos.fraud.review_reasons` keeps its declared order. | BUNDLE-75 |
| `integer` bounds are written as `min` and `max` in the type declaration. `fields` in the object type of `pos.receipt.header` carry `optional: true` only on `footer_text`. | TYPE-8, TYPE-9, TYPE-10, TYPE-13, TYPE-14, BUNDLE-82 |

## 5. Values

| Decision | Clauses |
| --- | --- |
| The root values of `platform.yaml` are written under the scope `platform` with the node key `""`. | SRC-26, SCOPE-13, BUNDLE-64, BUNDLE-66 |
| The multi-node values file `overrides.yaml` supplies `region`, `store` and `brand` values, each under its node ID. It has no root entries. | SRC-29, SRC-30, BUNDLE-66 |
| A set value is written in universe order: `pos.tender.allowed: [CASH, CARD]` at the root, and `[CASH, CARD]` at `harbor_street`. `pos.fraud.review_reasons: [HIGH_VALUE, NEW_CARD]` at `harbor_street`. | BUNDLE-74, TYPE-50, TYPE-51 |
| `pos.receipt.header` values at the root, `inland` and `meadow_goods` are partial objects. A partial object is allowed for every value that is not the starting value, and the starting value is the complete `default`. The `footer_text` field is a string, so the rule on optional object fields does not apply to it. | MERGE-10, MERGE-11, MERGE-12, MERGE-26 |
| No scope has an empty node entry, and no node has an empty key table, so no entry is omitted. `terminal` has no `values` member. | BUNDLE-22, BUNDLE-67 |
| `pos.offline.allowed: true` at `harbor_street` is a `boolean`. Integer values are JSON integers, and the decimal type is not used. | BUNDLE-68, BUNDLE-69 |

## 6. Features and policies

| Decision | Clauses |
| --- | --- |
| `decidedPer` is `"basket"`, `hitPolicy` is `"FIRST"` and `ifFactMissing` is `"REJECT"`. | BUNDLE-77 to BUNDLE-80, FEAT-4, FEAT-15 |
| `returns` is the enum form `{enum: ["APPROVE","REJECT","REVIEW"]}` in code point order, with no `nullable`. | BUNDLE-82, BUNDLE-75, FEAT-9 |
| `bindingSettableAt` is `["platform","store"]` in level order. The root level is included. | BUNDLE-81, BUNDLE-54, BUNDLE-128, FEAT-18 |
| The feature `description` and the rules file's `examples` are excluded. | BUNDLE-109, FEAT-1, POL-47 |
| Each setting carries `default` and exactly one of `valueOf` or `listOf`. The `listOf` defaults and bindings are in code point order: `cash_categories` gives `["GENERAL","GROCERY"]`, and the strict binding's `restricted_categories` gives `["ALCOHOL","GIFT_CARD_SALE","PHARMACY"]`. | BUNDLE-86, BUNDLE-87, BUNDLE-89, BUNDLE-90, BUNDLE-75 |
| The `default` of each setting, and each `valueOf` default, are valid values of their fact's type. `trusted_tender` has `valueOf: "basket.tender"` and default `"CARD"`. | BUNDLE-88, POL-17 |
| Each policy's `default` is an effect of `returns`: `REVIEW` for `standard` and `strict`. | BUNDLE-85, POL-7 |
| `requiredFacts` is the distinct facts of the policy's conditions, in code point order. `standard`: `["basket.tender","line.category","shopper.fraud_flag","shopper.tier"]`. `strict`: `["basket.tender","line.category","shopper.fraud_flag"]`. | BUNDLE-91, BUNDLE-92, BUNDLE-136 |
| Rules are in source order. Each rule has `id`, `name`, `when` and `then`. Rule `name` is kept. Rule IDs are unique within each policy. | BUNDLE-93 to BUNDLE-95, BUNDLE-132, RULE-46 |
| Conditions in each `when` are sorted by fact, so `trusted-shopper-tender` lists `basket.tender` before `shopper.tier`. | BUNDLE-96, DET-6 |
| The `op` of each condition is the form that Table 8.1 gives: `eq` for `shopper.fraud_flag`, `basket.tender`, and `trusted_tender`; `in` for `shopper.tier`; `any_in` and `all_in` for `line.category`. | BUNDLE-98, BUNDLE-99, RULE-3, BUNDLE-27 |
| Setting references are written as `"$<name>"` as the whole operand. `eq` takes `$trusted_tender` (a `valueOf` setting). `in` takes `$trusted_tiers`, `any_in` takes `$restricted_categories` and `all_in` takes `$cash_categories` (`listOf` settings). | BUNDLE-100, BUNDLE-101, BUNDLE-103, BUNDLE-104, BUNDLE-126, RULE-4, RULE-23 |
| Literals are encoded as their type: `true` (boolean), `"CASH"` (enum). | BUNDLE-68, BUNDLE-70, BUNDLE-102 |
| Each effect in `then` is an enum value of `returns`. | POL-22, FEAT-13, BUNDLE-85 |
| Setting names `restricted_categories`, `cash_categories`, `trusted_tiers` and `trusted_tender` contain no `$`, no `.`, and are not `policy`. | BUNDLE-84, BUNDLE-86, POL-20 |
| Policy `strict` has no `trusted_tiers` setting and no `trusted-shopper-tender` rule, because the source declares neither. | BUNDLE-108, POL-9 |

## 7. Bindings

| Decision | Clauses |
| --- | --- |
| `bindings` is keyed by level. The root binding has the node key `""` under `platform`, and the `store` binding has `harbor_street`. No axis carries a binding. | BUNDLE-105, BUNDLE-106, BUNDLE-66, BIND-2, BIND-3, BIND-15 |
| The root binding is `{policy: "standard", settings: {}}`. Its settings are empty, so the policy defaults are not copied in. | BUNDLE-107, BUNDLE-108, BIND-23, BIND-20 |
| The `store` binding is `{policy: "strict", settings: {restricted_categories: [...]}}`. The setting is `restricted_categories`, sorted as stated above. The binding replaces the root binding entirely, and no value is merged from it. | BUNDLE-107, BUNDLE-108, BUNDLE-124, BIND-18 |
| `bindingSettableAt` is `["platform","store"]`, and the level keys in `bindings` match it. | BUNDLE-81, BUNDLE-127, BUNDLE-128 |

## 8. Excluded from content

| Item | Clauses |
| --- | --- |
| Shared and namespaced schema files, namespace and ownership declarations (`namespace: pos`, the `owner` member and the `pos.*` names). The namespace has no effect on the content. | BUNDLE-109, NS-7, NS-10, KEY-9 |
| Kind definitions (only the kind names remain), naming patterns, lint rules, severities and lint settings in `tierwise.yaml`. | BUNDLE-109, KIND-1, LINT-14 |
| Presets (none are used) and the `extends` key. | BUNDLE-109 |
| Descriptions of keys, facts and features, and the `source` member of each fact. | BUNDLE-109, FACT-10 |
| The ten examples in `rules/pos.basket_check.yaml`. They are compile-time data, and they are run only as validation checks. The check is described below. | BUNDLE-109, POL-41, POL-47 |
| Source file names, paths and positions. | BUNDLE-109 |
| Derived data other than `requiredFacts`, such as ancestor closures, inverse edges and policy defaults copied into bindings. | BUNDLE-47, BUNDLE-110 |

**Examples check.** The ten examples were checked by hand against sections 7 and 8, not by a
script. The expected effect of each example is what the bound policy gives. This includes the
missing-fact precheck ([RULE-40], [RULE-42], [FACT-17]) and the empty collection ([RULE-33]). None
of the examples fails under this bundle.

## 9. Notes for the coordinator

- The sample README (`samples/retail-pos/README.md`) lists two open points that the current
  section text answers, so the README is stale on them. The feature `description` is allowed by
  [FEAT-1]. The `**` glob reads as zero or more directories under [SRC-18] (b). Neither point
  changes the bundle bytes.
- [MERGE-26] says "optional object field", which can be read in two ways. Under either reading,
  the values in this sample are complete, so the bundle does not depend on the reading.
