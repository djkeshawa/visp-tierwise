# spec/conformance/bundles: edge-kinds.json

`edge-kinds.json` is the canonical bundle that the compile cases named `kind-*`, `lint-*` and `neg-*`
expect as their output. It is the bundle of one small baseline project, which the cases copy into
their own `project/` directories. The baseline is clean under every engine check and under every
lint rule that the cases switch on, so a compile of it gives no finding of any severity ([VEC-94],
[VEC-95]).

- Bundle file: `edge-kinds.json` (no trailing newline, [BUNDLE-4]).
- Digest (`bundleVersion`): `sha256:0486f8066d3f07e851f53126d50dbde011b8f9619f51588c08a64ee86d0227dd`
  ([BUNDLE-5], [BUNDLE-6]).
- Written by hand as a content object, then turned into bytes by `js/spec-tools/canonicalize.mjs`, which
  checks the content against `spec/schemas/bundle.schema.json` first ([DET-1]). No reference compiler
  exists yet, so the content is the spec's derivation of the baseline and has to be confirmed by the
  first compiler that runs these cases.
- The cases reference it as `../../bundles/edge-kinds.json` ([VEC-93]).

## The baseline project

Each compile case under `spec/conformance/compile/` holds its own `project/` copy of the baseline
(`VEC-5`). A positive case differs from the baseline by one edit, which is the trigger of the finding
it expects. A negative control (`neg-*`) is the baseline itself, so its expected output is this bundle.

The baseline has one shared schema file, one namespaced file (`ns1`), one root values file, one
path-mapped values file (`region/north`) and one rules file. Its manifest sets every lint rule that the
cases switch on to `error`, leaves `presets/override` unset and extends no preset, so the baseline runs
no preset-replacement check. The sections below give the compilation decisions that the bundle
records. Kinds, naming patterns, descriptions, owners, examples and lint settings do not appear in the
bundle ([BUNDLE-109]), so the edits that change only those leave the bytes the same.

## Compilation decisions

| Decision | Clauses | Result in the bundle |
| --- | --- | --- |
| A kind is not written. Each key keeps only its kind name | [BUNDLE-62], [BUNDLE-63], [BUNDLE-109] | `"kind": "flag"`, `"narrow"`, `"defaulted"`, `"sfkind"`, `"kn"` on the keys that name them |
| A key declared with `on_source_failure` and settable at the service level keeps it | [BUNDLE-59], [BUNDLE-60], [SVC-1] | `onSourceFailure: "skip"` on `access.partner_gate`, `deny` on `access.seat_cap` and `access.emergency` |
| A key settable at the service level with no key-level behaviour takes its kind's value | [KIND-9], [BUNDLE-61] | `access.audit_on` writes `onSourceFailure: "skip"` from kind `sfkind` |
| A key not settable at the service level omits `onSourceFailure`, even when it declares one | [BUNDLE-59], [SVC-7] | `access.allowed` declares `on_source_failure: deny` and writes no member |
| `settableAt` lists levels in declared order, then axes (none here) | [BUNDLE-54] | `["global", "region", "account"]` order, so `access.audit_on` writes `["global", "region", "account"]` |
| No axis is declared, so `combineAcrossAxes` is absent and `axes` is `[]` | [BUNDLE-31], [BUNDLE-57] | `"axes": []`, and no key has `combineAcrossAxes` |
| A commutative key has no `default`; an order-dependent key has one | [MERGE-4], [BUNDLE-55] | `quota.max_seats` (`override`) writes `default: 4`. Every other key writes none |
| A min-strategy integer key writes its `min` and `max` in its type object | [BUNDLE-52], [BUNDLE-53], [TYPE-7] | `access.seat_cap` writes `{type: "integer", min: 0, max: 100}` |
| A set key writes its universe in declared order | [BUNDLE-74], [TYPE-50], [TYPE-51] | `shipping.methods` writes `universe: ["ground", "pickup"]` and the value `["ground"]` |
| A scope with no node entries is absent from `values` | [BUNDLE-22], [BUNDLE-67] | `account` has no `values` entry, and it is `values: service` |
| A node entry with no key values is absent | [BUNDLE-22] | `region/south` has no entry, and `region/north` has one |
| The root level's values sit under the level name `global` with node key `""` | [BUNDLE-64], [BUNDLE-66] | `values.global[""]` |
| A closed catalog is written sorted, with `onUnknownNode` | [BUNDLE-38], [BUNDLE-39], [BUNDLE-42], [BUNDLE-43], [DET-6] | `region` nodes `["north", "south"]`, `onUnknownNode: "error"` |
| An open catalog has no `onUnknownNode` | [BUNDLE-37], [BUNDLE-42] | `account`: `nodes: "open"`, `values: "service"` |
| An enum's values, and a feature's `returns`, are in code point order | [BUNDLE-75] | `item.stock` `["DROP_SHIP", "SPECIAL", "STOCKED"]`, `order.stage` `["CLOSED", "OPEN"]`, `returns` `["ALLOW", "DENY"]` |
| `listOf` values are written as arrays, and an empty `listOf` default is `[]` | [BUNDLE-89], [BUNDLE-90] | `no_stages` writes `default: []`, `allowed_stock` writes `default: ["STOCKED"]` |
| A single-name `has_many` is a one-element array; an absent edge is `[]` | [BUNDLE-45], [BUNDLE-46] | `order.hasMany: ["item"]`; `item.belongsTo` and `item.hasMany` are `[]` |
| A fact's entity is the prefix of its name, and its type object carries the enum values | [BUNDLE-49], [FACT-3] | `order.Paid` is `entity: "order"`, boolean. The attribute keeps its upper case, which no fact pattern checks |
| `requiredFacts` is derived, in code point order, over every rule of the policy | [BUNDLE-91], [BUNDLE-92], [RULE-40] | `review`: `["item.stock", "order.Paid", "order.stage"]`. `allow_all`: `[]` |
| Rules keep source order, and each `when` is sorted by fact | [BUNDLE-93], [BUNDLE-96], [RULE-8] | `paid`, `stock_ok`, `stage_closed`, `none_closed`, one condition each |
| Setting references stay symbolic, including in an `in` operator | [BUNDLE-103], [BUNDLE-104], [RULE-4] | `"value": "$allowed_stock"` on `all_in`; `"value": "$no_stages"` on `in` |
| A rule `name` is kept; the feature `description` is dropped | [BUNDLE-95], [BUNDLE-109] | Rule names are written. No description appears |
| A binding is keyed by level, then node key. The root binding has node key `""`, and `settings` holds only the values the source writes | [BUNDLE-105], [BUNDLE-107], [BUNDLE-108], [BIND-23] | `bindings.global[""]` is `review`; `bindings.region.north` is `allow_all`. Both write `settings: {}` |
| Examples, namespace, ownership, presets and lint settings are not written | [BUNDLE-109], [NS-6], [POL-47] | None appear. The examples run but leave no trace |
| No provenance and no sidecar is written | [BUNDLE-139], [DET-3] | The bundle holds no provenance |

## Checks the baseline has to pass

- No engine check fails: the kinds, the source-failure declarations, the min bound of the deny
  integer key, the narrowing strategies, the naming patterns and the namespace rules all hold
  ([KIND-4] to [KIND-12], [SVC-1] to [SVC-12], [NS-4] to [NS-7]).
- Its four examples pass ([POL-41], [POL-42]). The root examples give `ALLOW` for a paid order and
  `DENY` for a mixed stock collection, `ALLOW` when every item is allowed stock, and the `north`
  example gives `ALLOW` through `allow_all`.
- Every lint rule that the cases turn on reports nothing for the baseline: naming patterns, values,
  rules, examples, unused declarations, descriptions and owners ([LINT-13] to [LINT-50]).
