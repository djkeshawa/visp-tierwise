# edge-admission.json: admission and context bundle

`edge-admission.json` is a focused canonical bundle for the admission and context cases of section 2
(`CTX`, the runtime parts of `SCOPE`) and the `skippedScopes` entries of section 9 (`PROV-54` to `PROV-61`).
Its evaluate vectors are in `../evaluate/admission/`. The bundle is small on purpose: each member
exists so that one admission rule can be observed. The sample bundles are not reused, because none has a
closed service-sourced level with `on_unknown_node: ignore`, an empty catalog, or a valued ignored axis.

- Bundle: `edge-admission.json`, `bundleVersion` `sha256:eacee690d00111cc0bc1199fbe3d312b830d5de9d7ef1c24874a366bdd90e8dd`
  ([BUNDLE-5], [BUNDLE-6]). The file has no trailing newline ([BUNDLE-4]).
- Built from a content object written outside the repository, then turned into canonical bytes by
  `js/spec-tools/canonicalize.mjs`, which checks the content against `bundle.schema.json` first. Nothing in
  the bytes was edited afterwards.
- The content was checked by hand against the loader rules of section 10 (`BUNDLE-16` to `BUNDLE-136`):
  the admission-relevant ones are `BUNDLE-22` (no empty node entries), `BUNDLE-39` and `BUNDLE-41`
  (code point order, `[]` for an empty catalog), `BUNDLE-42` (`onUnknownNode` only on a closed catalog),
  `BUNDLE-54` (`settableAt` in scope order), `BUNDLE-59` and `BUNDLE-61` (`onSourceFailure` on keys settable
  at a service-sourced level), `BUNDLE-57` (`combineAcrossAxes` on the one order-dependent key settable at
  an axis), `BUNDLE-67` (no `values` for a service level) and `BUNDLE-81` (`bindingSettableAt` in level order).
  Its values were also checked against `TYPE-19` (inclusive integer bounds) and `TYPE-44` (enum membership).

## Hierarchy

| Scope | Kind | Catalog | `on_unknown_node` | Purpose in the suite |
| --- | --- | --- | --- | --- |
| `global` | root | none | none | Root values and root binding |
| `partner` | level, service-sourced | `["p-1", "p-2"]` | `ignore` | Skipped service-sourced scope with zero provider calls; admitted service node |
| `channel` | level | `[]`, the generated empty catalog | `error` | `E_UNKNOWN_NODE` for an empty closed catalog (`SCOPE-21`) |
| `division` | level | `["café", "north", "south"]`, generated | `error` | Closed error catalog, NFC and NFD forms of `café`, case sensitivity, values and binding |
| `site` | level | `["site_001", "site_002", "sité"]` | `ignore` | Skipped file-sourced level, admitted level with a value and a binding; `sité` (NFC) is a member whose decomposed spelling is skipped (`CTX-7`) |
| `account` | level, service-sourced | `open` | none | Open catalog: new valid IDs, NFD IDs, 128 and 129 code points, providers called once |
| `seller` | axis | `["acme", "globex"]` | `ignore` | Skipped axis, and a valued axis that wins under `axis_wins` |
| `région` | axis | `[]` | `ignore` | Empty ignored catalog (`SCOPE-21`), and the NFC name whose NFD form is an undeclared name |

`division` and `channel` stand for generated catalogs: in their source they are `from` files, and
`SCOPE-31` requires the compiled bundle to embed the node IDs, which is what this file does. The compile
case that produces these bytes from a `from` catalog is not in this item. Whichever item adds it must
either reproduce these bytes or name this file. `région` is written inline. Levels in declared order:
`global`, `partner`, `channel`, `division`, `site`, `account`. Axes in declared order: `seller`, `région`.

## Keys

| Key | Type | Strategy | Settable at | Source failure | Notes |
| --- | --- | --- | --- | --- | --- |
| `edge.enabled` | boolean | `all` | `global`, `partner`, `division`, `account` | `deny` | `false` at `south` is a `VETO`; `deny` value is `false` |
| `edge.max_items` | integer 0 to 500 | `max` | `global`, `partner`, `division`, `account` | `skip` | Root value 50 |
| `edge.min_qty` | integer 0 to 1000 | `min` | `global`, `partner`, `channel`, `division`, `site`, `account`, `seller`, `région` | `deny` | Commutative, so no `source`; deny value is the `min` bound 0 |
| `edge.tier` | enum `BASIC`, `GOLD`, `PLATINUM`, `SILVER` | `override` | `global`, `channel`, `division`, `site`, `seller` | none | `default` `BASIC`; `combineAcrossAxes` `axis_wins`; the only order-dependent key |

Values: root `SILVER`, `max_items` 50, `min_qty` 10. `division south`: `enabled` false, `min_qty` 4, `tier`
`PLATINUM`. `site site_001`: `min_qty` 2, `tier` `BASIC`. `seller acme`: `min_qty` 3, `tier` `GOLD`.
`division north` and `division café` have no values, so they are admitted with nothing to contribute.

## Feature

`edge.route` is decided per `order`. Its single entity fact is `order.paid` (boolean). Returns `HOLD` or
`SHIP`, `hit_policy` `FIRST`, `if_fact_missing` `HOLD`, `binding_settable_at` `global`, `division`, `site`.

| Policy | Default | Rules | Used by |
| --- | --- | --- | --- |
| `paid_only` | `HOLD` | `paid`: `order.paid` eq `true`, then `SHIP` | root binding |
| `manual` | `HOLD` | none | `division south` |
| `ship_all` | `SHIP` | none | `site site_001` |

Bindings: root `paid_only`; `division south` `manual`; `site site_001` `ship_all`. Neither `partner`
nor `account` can carry a binding (`FEAT-19`), and an axis never can (`BIND-15`).

