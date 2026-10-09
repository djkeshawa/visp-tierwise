# spec/conformance/bundles: edge-axes

Focused bundle for the axis rules of section 5 ([AXIS-1] to [AXIS-8]) and the axis parts of section 9 ([PROV-34], [PROV-45] to [PROV-48] and [PROV-50] to [PROV-53]). It is a hand-built content object, canonicalised by `js/spec-tools/canonicalize.mjs`, so that the evaluate vectors under `spec/conformance/evaluate/axes/` can each check one axis rule against one bundle. It names no sample domain. It is a canonical bundle under `bundles/`, not an invalid bundle, so it is valid ([VEC-9]).

- File: `spec/conformance/bundles/edge-axes.json`
- `bundleVersion`: `sha256:05179da9ede881099ef2c24d893073bb5a23f3ce934d26e6d0b76def8dab2f7f` ([BUNDLE-5], [BUNDLE-6]). The file has no trailing newline ([BUNDLE-4]).
- Content members: `bundleFormatVersion` `"1"`, `specVersion` `"0.1"`, `hierarchy`, `entities`, `facts`, `keys`, `values` and `features`. `entities`, `facts` and `features` are empty, because no entity or feature is needed ([BUNDLE-19]).
- Built by writing the content as JSON in a scratch file outside the repository, then running `node js/spec-tools/canonicalize.mjs <content.json> spec/conformance/bundles/edge-axes.json`. The tool checks the content against `bundle.schema.json` before it writes.

## Hierarchy

The root level is `global`. The non-root levels in declared order are `region` and `store`. The axes in declared order are `seller` and `channel`. Every closed catalog has `onUnknownNode: "error"` and `values: "files"`. No scope is service-sourced, so no key needs `onSourceFailure` ([SVC-1]).

| Scope | Kind | Nodes (code point order) |
| --- | --- | --- |
| `global` | root level, node `""` | none |
| `region` | level | `north`, `south` |
| `store` | level | `s1`, `s2` |
| `seller` | axis, declared first | `acme`, `globex` |
| `channel` | axis, declared second | `shop`, `web` |

The evaluation path is `global`, `region`, `store`, `seller`, `channel` ([MERGE-13], [PROV-34]). A vector may write its context keys in any order, and the axes always contribute in declared order.

## Keys

The keys cover each commutative strategy with an axis input, each order-dependent strategy under each `combine_across_axes` rule that its type allows, a key settable on no axis, and a `deep_merge` key without a default.

| Key | Type | `combine` | `default` | `combineAcrossAxes` | `settableAt` |
| --- | --- | --- | --- | --- | --- |
| `axes.commutative.any_flag` | boolean | `any` | none | none | global, region, seller, channel |
| `axes.commutative.all_flag` | boolean | `all` | none | none | global, store, seller, channel |
| `axes.commutative.methods` | set `GROUND`, `PICKUP`, `NEXT_DAY` | `intersect` | none | none | global, region, seller, channel |
| `axes.commutative.tags` | set `A`, `B`, `C` | `union` | none | none | global, seller, channel |
| `axes.commutative.cap` | integer 0 to 100 | `min` | none | none | global, region, seller, channel |
| `axes.commutative.peak` | integer 0 to 100 | `max` | none | none | global, store, seller, channel |
| `axes.override.max_hours` | integer 0 to 168 | `override` | 4 | `max` | global, region, store, seller, channel |
| `axes.override.max_floor` | integer 0 to 168 | `override` | 6 | `max` | region, seller, channel |
| `axes.override.min_cap` | integer 0 to 100 | `override` | 10 | `min` | region, seller, channel |
| `axes.override.axis_label` | string | `override` | `"standard"` | `axis_wins` | region, seller, channel |
| `axes.override.hier_label` | string | `override` | `"standard"` | `hierarchy_wins` | region, store, seller, channel |
| `axes.override.level_only` | string | `override` | `"base"` | none | global, region, store |
| `axes.deep.axis_wins` | object: `open`, `close` (integers), optional `slot` (integer) | `deep_merge` | `{open: 8, close: 18}` | `axis_wins` | global, region, seller, channel |
| `axes.deep.hierarchy_wins` | object, as above | `deep_merge` | `{open: 8, close: 18}` | `hierarchy_wins` | region, seller |
| `axes.deep.root_start` | object, as above | `deep_merge` | none | `axis_wins` | global, seller |

Root values exist for `any_flag`, `all_flag`, `methods`, `tags`, `cap`, `peak`, `max_hours`, `deep.axis_wins` and `deep.root_start`. The root value of `deep.axis_wins` and of `max_hours` is always a contribution, so their hierarchy result is always present. `max_floor`, `min_cap`, `axis_label`, `hier_label`, `level_only` and `deep.hierarchy_wins` have no root value, so their only-axis and neither cases are reachable. `deep.root_start` has no default, so its starting value is the root value ([MERGE-10]). `level_only` has no `combineAcrossAxes`, as [AXIS-6] and [BUNDLE-57] require for a key settable on no axis. The `max_floor` default of 6 is above every axis value of that key (5 and 2), so a result that wrongly folds the default into an axis result under `max` differs from the correct one.

## Values

Each value is written at a scope on which its key is settable ([BUNDLE-130]). The root level is `global`, with node key `""` ([BUNDLE-66]). A set value lists its members in universe order ([BUNDLE-74]). A deep value may be partial, as [MERGE-11] allows.

| Scope and node | Values |
| --- | --- |
| `global` (root) | any_flag `false`; all_flag `true`; methods: full universe; tags `[]`; cap `60`; peak `10`; max_hours `2`; deep.axis_wins `{open: 9}`; deep.root_start `{open: 9, close: 17}` |
| `region` `north` | any_flag `true`; methods `[GROUND, PICKUP]`; cap `40`; max_hours `6`; max_floor `3`; min_cap `40`; axis_label `"north"`; hier_label `"north"`; level_only `"north-level"`; deep.axis_wins `{close: 16, slot: 30}`; deep.hierarchy_wins `{close: 16, slot: 30}` |
| `region` `south` | min_cap `60` |
| `store` `s1` | all_flag `false`; max_hours `10`; hier_label `"s1"`; level_only `"s1-level"` |
| `store` `s2` | peak `25` |
| `seller` `acme` | any_flag `true`; all_flag `true`; methods `[PICKUP, NEXT_DAY]`; tags `[B, C]`; peak `20`; max_hours `8`; max_floor `5`; min_cap `30`; axis_label `"acme"`; hier_label `"acme"`; deep.axis_wins `{open: 7}`; deep.hierarchy_wins `{open: 7, close: 12}`; deep.root_start `{open: 7}` |
| `seller` `globex` | cap `30`; max_hours `6`; min_cap `40`; deep.hierarchy_wins `{open: 11}` |
| `channel` `shop` | all_flag `false`; methods `[PICKUP]`; tags `[A]`; peak `15` |
| `channel` `web` | any_flag `false`; cap `45`; max_hours `5`; max_floor `2`; min_cap `35`; axis_label `"web"`; hier_label `"web"`; deep.axis_wins `{slot: 15, close: 20}` |

Two ties are built on purpose. `max_hours` at `region north` and at `seller globex` are both 6, so a `max` comparison ties and the source names the hierarchy ([AXIS-8], [PROV-51]). `min_cap` at `region north` and at `seller globex` are both 40, which is a `min` tie. The axis result is the last declared contributing axis, not the largest or smallest axis value ([MERGE-20]). So with `seller acme` (8) and `channel web` (5) both supplied, `max_hours` compares the hierarchy with 5, and with `seller acme` (30) and `channel web` (35) both supplied, `min_cap` compares the hierarchy with 35.

## Vectors

The suite has 45 vectors, three of them exact. The vectors are under `spec/conformance/evaluate/axes/`, in three directories: `commutative/`, `override/` (the `override` strategy, with each of its `combine_across_axes` rules and the key settable on no axis) and `deep-merge/`. Each is a `resolve` call against this bundle. Each expected value was derived by hand from the clauses. A separate scratch check, outside the repository, then applied the same rules to the bundle, and it agreed with every expectation. The table gives each vector's context, expected value and source. A `(none)` source means a commutative key, which has no source ([PROV-45]). `Exact` marks a vector with `exact: true` ([VEC-63]).

| Vector | Key | Context | Value | Source | Exact |
| --- | --- | --- | --- | --- | --- |
| `commutative/all-axis-veto` | `axes.commutative.all_flag` | axes seller=acme, channel=shop | false | (none) |  |
| `commutative/all-identity-from-axis` | `axes.commutative.all_flag` | axes seller=acme | true | (none) |  |
| `commutative/any-axis-alone-sets-result` | `axes.commutative.any_flag` | axes seller=acme | true | (none) |  |
| `commutative/any-region-and-channel-exact` | `axes.commutative.any_flag` | levels region=north; axes channel=web | true | (none) | yes |
| `commutative/intersect-axes-narrow-in-universe-order` | `axes.commutative.methods` | levels region=north; axes seller=acme, channel=shop | [PICKUP] | (none) |  |
| `commutative/max-store-and-axes-widen` | `axes.commutative.peak` | levels store=s2; axes seller=acme, channel=shop | 25 | (none) |  |
| `commutative/min-axis-narrows` | `axes.commutative.cap` | levels region=north; axes seller=globex, channel=web | 30 | (none) |  |
| `commutative/union-axes-widen-in-universe-order` | `axes.commutative.tags` | axes channel=shop, seller=acme | [A, B, C] | (none) |  |
| `deep-merge/axis-wins-fold-of-all-scopes` | `axes.deep.axis_wins` | levels region=north; axes seller=acme, channel=web | {open: 7, close: 20, slot: 15} | axis channel web |  |
| `deep-merge/axis-wins-hierarchy-only-default-fills` | `axes.deep.axis_wins` | empty | {open: 9, close: 18} | level global |  |
| `deep-merge/axis-wins-only-axis-default-fills-exact` | `axes.deep.axis_wins` | axes seller=acme | {open: 7, close: 18} | axis seller acme | yes |
| `deep-merge/axis-wins-seller-over-level-keeps-other-fields` | `axes.deep.axis_wins` | levels region=north; axes seller=acme | {open: 7, close: 16, slot: 30} | axis seller acme |  |
| `deep-merge/hierarchy-wins-fields-of-level-over-axis` | `axes.deep.hierarchy_wins` | levels region=north; axes seller=acme | {open: 7, close: 16, slot: 30} | level region north |  |
| `deep-merge/hierarchy-wins-neither-default` | `axes.deep.hierarchy_wins` | levels region=south | {open: 8, close: 18} | DEFAULT |  |
| `deep-merge/hierarchy-wins-only-axis-over-default` | `axes.deep.hierarchy_wins` | axes seller=globex | {open: 11, close: 18} | axis seller globex |  |
| `deep-merge/hierarchy-wins-only-level-over-default` | `axes.deep.hierarchy_wins` | levels region=north | {open: 8, close: 16, slot: 30} | level region north |  |
| `deep-merge/root-start-axis-wins-no-default` | `axes.deep.root_start` | axes seller=acme | {open: 7, close: 17} | axis seller acme |  |
| `deep-merge/root-start-root-only-source` | `axes.deep.root_start` | empty | {open: 9, close: 17} | level global |  |
| `override/axis-wins-channel-last-over-seller` | `axes.override.axis_label` | levels region=north; axes seller=acme, channel=web | web | axis channel web |  |
| `override/axis-wins-neither-default` | `axes.override.axis_label` | levels region=south; axes seller=globex | standard | DEFAULT |  |
| `override/axis-wins-only-axis-over-default` | `axes.override.axis_label` | axes seller=acme | acme | axis seller acme |  |
| `override/axis-wins-only-hierarchy-over-default` | `axes.override.axis_label` | levels region=north | north | level region north |  |
| `override/axis-wins-seller-over-hierarchy` | `axes.override.axis_label` | levels region=north; axes seller=acme | acme | axis seller acme |  |
| `override/hierarchy-wins-most-specific-level` | `axes.override.hier_label` | levels region=north, store=s1; axes seller=acme, channel=web | s1 | level store s1 |  |
| `override/hierarchy-wins-neither-default` | `axes.override.hier_label` | empty | standard | DEFAULT |  |
| `override/hierarchy-wins-only-axis-when-level-unvalued` | `axes.override.hier_label` | levels store=s2; axes seller=acme | acme | axis seller acme |  |
| `override/hierarchy-wins-only-hierarchy-over-default` | `axes.override.hier_label` | levels region=north | north | level region north |  |
| `override/hierarchy-wins-over-axis` | `axes.override.hier_label` | levels region=north; axes seller=acme | north | level region north |  |
| `override/level-only-default-when-only-axis-supplied` | `axes.override.level_only` | axes seller=acme | base | DEFAULT |  |
| `override/level-only-ignores-unsettable-axis` | `axes.override.level_only` | levels region=north, store=s1; axes seller=acme | s1-level | level store s1 |  |
| `override/max-axis-larger-wins` | `axes.override.max_hours` | levels region=north; axes seller=acme | 8 | axis seller acme |  |
| `override/max-floor-default-when-neither` | `axes.override.max_floor` | empty | 6 | DEFAULT |  |
| `override/max-floor-last-axis-below-hierarchy` | `axes.override.max_floor` | levels region=north; axes seller=acme, channel=web | 3 | level region north |  |
| `override/max-floor-only-axis` | `axes.override.max_floor` | axes seller=acme | 5 | axis seller acme |  |
| `override/max-last-axis-not-largest` | `axes.override.max_hours` | levels region=north; axes seller=acme, channel=web | 6 | level region north |  |
| `override/max-most-specific-level-is-hierarchy` | `axes.override.max_hours` | levels region=north, store=s1; axes seller=acme | 10 | level store s1 |  |
| `override/max-only-hierarchy-root-source` | `axes.override.max_hours` | levels region=south | 2 | level global |  |
| `override/max-ordering-levels-then-declared-axes` | `axes.override.max_hours` | levels region=north, store=s1; axes channel=web, seller=acme | 10 | level store s1 |  |
| `override/max-tie-names-hierarchy` | `axes.override.max_hours` | levels region=north; axes seller=globex | 6 | level region north | yes |
| `override/min-default-when-neither-unvalued-axis` | `axes.override.min_cap` | axes channel=shop | 10 | DEFAULT |  |
| `override/min-hierarchy-larger-axis-smaller` | `axes.override.min_cap` | levels region=south; axes seller=globex | 40 | axis seller globex |  |
| `override/min-last-axis-not-smallest` | `axes.override.min_cap` | levels region=north; axes seller=acme, channel=web | 35 | axis channel web |  |
| `override/min-only-axis-without-hierarchy` | `axes.override.min_cap` | axes seller=acme | 30 | axis seller acme |  |
| `override/min-only-hierarchy-default-not-combined` | `axes.override.min_cap` | levels region=south | 60 | level region south |  |
| `override/min-tie-names-hierarchy` | `axes.override.min_cap` | levels region=north; axes seller=globex | 40 | level region north |  |

## Clauses covered

Every vector cites the clauses whose behaviour it checks. Of the AXIS clauses, the vectors cite [AXIS-1], [AXIS-2] (its positive half, that axis contributions are ordinary inputs, [MERGE-17]), [AXIS-7] and [AXIS-8]. They cite the PROV clauses of the contributions, labels and sources ([PROV-19] to [PROV-26], [PROV-34], [PROV-37], [PROV-38], [PROV-45] to [PROV-48], [PROV-50] to [PROV-53]) and the MERGE clauses of the rules and the `deep_merge` fold. [PROV-49] belongs to the merge-strategy item, because no key here can fail under `deny`.

[AXIS-3] to [AXIS-6] and the negative half of [AXIS-2] reject a declaration, so only compile cases can observe them (they are not valid in a canonical bundle, [BUNDLE-57], [BUNDLE-118]). They belong to the keys compile item and are not listed in `enforcers.d/E2.yaml` ([VEC-125]).

## Checks run

- `pnpm --dir js run spec:check -- --no-write` passed for the files of this item. `--no-write` keeps `spec/conformance/COVERAGE.md` unchanged, because that file belongs to the coverage item.
- The bundle canonicalises to the bytes that its digest covers ([BUNDLE-2], [BUNDLE-6]), and it passes the schema check that the tool runs first.

## Open points

- No clause of AXIS or of the axis parts of PROV is marked (OPEN).
- Handoff, not an OPEN clause: the compile cases for [AXIS-3] to [AXIS-6] belong to the keys compile item.
