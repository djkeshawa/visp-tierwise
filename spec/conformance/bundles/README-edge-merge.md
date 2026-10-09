# spec/conformance/bundles: edge-merge.json

`edge-merge.json` is a small focused bundle for the merge strategies. It is the bundle that the
evaluate vectors under `spec/conformance/evaluate/merge/` load. It is not a sample domain: its
keys and values exist only to make each merge rule observable, one strategy at a time. The bundle
is canonical bytes ([VEC-9], [BUNDLE-2], [DET-1]). It was produced by `js/spec-tools/canonicalize.mjs`
from a content object written outside the repository, and no byte was edited by hand.

> Example (non-normative). This file records one bundle and the decisions taken while building it.
> It does not define the merge rules, which belong to section 5 and section 9.

## Identity

- `bundleVersion`: `sha256:49c9633623285fdf3ac2385eedda4327e7975811d3e3f0f1ce69625a737b90c8`
  ([BUNDLE-5], [BUNDLE-6]). The file has no trailing newline ([BUNDLE-4]).
- `bundleFormatVersion` `"1"`, `specVersion` `"0.1"` ([BUNDLE-16], [BUNDLE-17]).
- The `entities`, `facts` and `features` members are empty objects. The merge vectors use no fact and
  no feature, and [BUNDLE-19] keeps the empty members present.

## Hierarchy

| Part | Declaration | Notes |
| --- | --- | --- |
| Root level | `global` | Has one implicit node, written under node key `""` ([SCOPE-10], [BUNDLE-66]) |
| Levels, in order | `division`, `site`, `account` | `account` is service-sourced and open, so it has no values in the bundle ([BUNDLE-67], [SVC-13]) |
| Axes, in declared order | `seller`, `promo` | The order is used for the axis fold ([SCOPE-7], [MERGE-24]) |
| `division` | nodes `north`, `south`; `onUnknownNode: error`; `values: files` | |
| `site` | nodes `s1`, `s2`; `onUnknownNode: error`; `values: files` | `s2` has no values, so it has no entry ([BUNDLE-22]) |
| `seller` | node `acme`; `onUnknownNode: error`; `values: files` | |
| `promo` | node `spring`; `onUnknownNode: error`; `values: files` | |

## Keys

Each key has the members that [BUNDLE-52] to [BUNDLE-63] require. `settableAt` lists the scopes in scope
order ([BUNDLE-54]).

| Key | Strategy | Type | Default | Settable at | `onSourceFailure` | `combineAcrossAxes` |
| --- | --- | --- | --- | --- | --- | --- |
| `merge.all_flag` | `all` | boolean | none | global, division, site, account | `deny` | none |
| `merge.any_flag` | `any` | boolean | none | global, division, site, account, seller | `skip` | none |
| `merge.regions` | `intersect` | set of red, green, blue | none | global, division, account | `deny` | none |
| `merge.tags` | `union` | set of red, green, blue | none | global, division, site, account, promo | `skip` | none |
| `merge.floor` | `min` | integer 1 to 500 | none | global, division, site, account | `deny` | none |
| `merge.floor_skip` | `min` | integer 1 to 500 | none | global, division, site, account | `skip` | none |
| `merge.ceiling` | `max` | integer 0 to 500 | none | global, division, site, account, seller | `skip` | none |
| `merge.label` | `override` | string | `"standard"` | global, division, site, account | `skip` | none |
| `merge.contact` | `override` | object: `name` required, `phone` optional | `{name: "desk"}` | global, division, site, account | `skip` | none |
| `merge.window` | `deep_merge` | object: `hours` (open, close), `closed_days` (list), `label`, optional `slot_minutes`, optional `exception` (from, to) | complete object with `closed_days` `["SUN"]`, `hours` 08:00 to 18:00, `label` `"standard"` | global, division, site, account | `skip` | none |
| `merge.window_no_default` | `deep_merge` | the same object type as `merge.window` | none | global, division | none (not settable at a service level) | none |
| `merge.lead_days` | `override` | integer 0 to 60 | `3` | global, division, site, account, seller, promo | `skip` | `axis_wins` |
| `merge.note` | `override` | string | `"none"` | global, division, seller, promo | none | `hierarchy_wins` |
| `merge.caps` | `deep_merge` | object: `cap` (integer 0 to 100), `unit` (string) | `{cap: 10, unit: "items"}` | global, division, seller, promo | none | `axis_wins` |
| `merge.caps_hier` | `deep_merge` | the same object type as `merge.caps` | `{cap: 10, unit: "items"}` | global, division, seller, promo | none | `hierarchy_wins` |

## Compilation decisions

| Decision | Clauses | Result in the bundle |
| --- | --- | --- |
| Commutative keys carry no `default`. Order-dependent keys have a default, except `merge.window_no_default`, which has a root value instead | [MERGE-4], [MERGE-7], [MERGE-10] | `merge.window_no_default` has no `default` and a complete root value at `global` |
| Unset-everywhere vectors need keys with no root value. Only `merge.window_no_default` has a root value | [MERGE-5], [MERGE-6], [MERGE-14] | No commutative key and no override or `merge.window` key has a `global` value |
| `deny` is used only on the narrowing strategies `all`, `intersect` and `min` | [SVC-11], [MERGE-2] | `merge.all_flag`, `merge.regions` and `merge.floor` take `deny`. Every other key settable at `account` takes `skip` |
| The deny value of `min` is the bound `min` of its type, so `merge.floor` has `min: 1` | [SVC-9], [SVC-12] | Deny contributes `1` for `merge.floor` |
| A key settable at `account` has `onSourceFailure`, and no other key has one | [BUNDLE-59], [SVC-1] | `merge.note`, `merge.caps`, `merge.caps_hier` and `merge.window_no_default` omit it |
| `combineAcrossAxes` is present only on order-dependent keys that are settable at an axis. `axis_wins` and `hierarchy_wins` are used, and no commutative key has it | [AXIS-2], [AXIS-3], [AXIS-4], [BUNDLE-57] | `merge.lead_days` and `merge.caps` take `axis_wins`. `merge.note` and `merge.caps_hier` take `hierarchy_wins` |
| Set universes keep declared order, and set values follow it | [TYPE-50], [TYPE-51], [BUNDLE-74], [BUNDLE-75] | `merge.tags` values are stored in universe order, so `site` `s1` holds `["green", "blue"]`. `merge.tags` at `division` `north` holds the empty set, which is a valid set value ([TYPE-49]) |
| A deep_merge value other than the starting value may be partial, at any depth | [MERGE-11], [MERGE-12], [BUNDLE-73] | `division` `north` sets only `hours.close` of `merge.window`. The default fills the other fields |
| An optional object field that the default does not set must be complete in any value that sets it | [MERGE-26], [MERGE-9] | `merge.window.exception` has no default. The `account` provider values in the vectors that set it are complete, except the one vector that gives a partial value to show `INVALID` |
| A list value replaces the earlier list as a whole | [MERGE-22] | `division` `south` sets `closed_days` `["SAT", "SUN"]`, and `site` `s1` sets `["MON"]`, which replaces it |
| An override object replaces the earlier object as a whole | [MERGE-21] | `merge.contact` at `division` `north` has a `phone`, which the `site` `s1` value does not repeat, so the result has no `phone` |
| A key not settable at `account` is not sent to the account provider | [KEY-11], [SVC-17] | `merge.note` has no `account` entry, so its vector shows no provider call |
| `merge.floor_skip` has no values anywhere | [BUNDLE-22], [MERGE-6] | No scope sets it, so the outcome of the `account` provider alone decides the result, and an absent result is `null` |
| Keys have no `kind`, `description` or `owner` | [BUNDLE-62], [KEY-9] | No key member is written for them, so the result has no `kind` member ([PROV-14]) |

## Vectors that use this bundle

Each vector name below is the file name under `spec/conformance/evaluate/merge/`, without `.yaml`.
A vector marked exact compares every member of its `Resolved` result ([VEC-63]).

- Commutative, `all`: `all-unset-identity` (exact), `all-identity-ignored`, `all-one-veto` (exact), `all-repeated-veto`,
  `all-several-exact` (exact), `all-invalid-null-deny`, `all-deny-unavailable`, `all-deny-timeout` and
  `all-ignores-entry-for-unsettable-key` (exact).
- Commutative, `any`: `any-unset-identity` (exact), `any-one-widened`, `any-several-exact` (exact),
  `any-skip-unavailable` (exact) and `any-skip-timeout` (exact).
- Commutative, `intersect`: `intersect-unset-universe` (exact), `intersect-identity-ignored`,
  `intersect-one-narrowed`, `intersect-several-exact` (exact), `intersect-deny-unavailable` and
  `intersect-deny-timeout`.
- Commutative, `union`: `union-unset-identity` (exact), `union-identity-ignored`, `union-one-widened`,
  `union-several-exact` (exact), `union-invalid-skip` (exact), `union-skip-unavailable` (exact) and
  `union-skip-timeout` (exact).
- Commutative, `min`: `min-unset-null` (exact), `min-one-narrowed`, `min-several-exact` (exact),
  `min-deny-unavailable`, `min-deny-timeout`, `min-skip-null-unavailable` (exact) and
  `min-skip-null-timeout` (exact).
- Commutative, `max`: `max-unset-null` (exact), `max-one-widened`, `max-several-exact` (exact),
  `max-skip-null-unavailable` (exact) and `max-skip-null-timeout` (exact).
- Order-dependent, `override` with no axis: `override-unset-default` (exact), `override-one-contributor`,
  `override-equal-default-set` (exact), `override-several-exact` (exact), `override-skip-unavailable` (exact),
  `override-skip-timeout` (exact), `override-skip-only-failure-unavailable` (exact),
  `override-skip-only-failure-timeout` (exact), `override-null-invalid-skip` (exact) and
  `override-object-replaces-whole` (exact).
- Order-dependent, `override` with axes: `lead-axis-only`, `lead-axis-wins-exact` (exact),
  `note-hierarchy-wins-exact` (exact), `note-axis-last-declared` (exact) and `note-not-settable-at-account` (exact).
- Order-dependent, `deep_merge`: `deep-unset-default` (exact), `deep-one-partial-nested` (exact),
  `deep-list-replacement` (exact), `deep-several-exact` (exact), `deep-invalid-optional-object` (exact),
  `deep-skip-unavailable` (exact), `deep-skip-timeout` (exact), `deep-root-starting-value` (exact),
  `deep-root-then-partial` (exact), `deep-axis-only-source` (exact), `deep-axis-wins-exact` (exact) and
  `deep-hierarchy-wins-exact` (exact).
- Kind in the result: `kind-named-in-result`. It loads `commerce.json` instead of this bundle, because no key of
  this bundle opts into a kind ([PROV-14], [PROV-15]).

A vector whose provider stub uses `fail: unavailable` has a twin with `fail: timeout`, and the two differ only
in `id`, `description`, `clauses` and the fail value, as [VEC-44] requires. The twins are the `*-unavailable`
and `*-timeout` pairs in the list above.

The unavailable twin of `override-skip-only-failure-timeout` is `override-skip-only-failure-unavailable`, so each
pair differs only in its final word.

## Checks run

- `pnpm --dir js run spec:check -- --no-write` reports no problem for `edge-merge.json` or for any vector under
  `evaluate/merge/`. The command as a whole still exits with failure, because other items own `compile/keys` and
  `compile/rules`, which lack an `expected.yaml`. It was run with `--no-write`, so `spec/conformance/COVERAGE.md` was not
  rewritten, because another item owns that file. Stage (c) accepts `edge-merge.json` (schema, canonical bytes and
  digest). Stage (d) accepts every vector of this family. Stage (e) finds every cited clause.
- Every fail vector has its paired twin with the same `expect`, `context`, `bundle`, `key` and `exact`, and the same
  provider stubs apart from the fail value.

## Open points

- (none) No clause that these vectors cite is marked (OPEN).
- `MERGE-3`, `MERGE-4`, `MERGE-7` and `MERGE-8` are compile-side clauses. No evaluate vector against a valid bundle
  observes them, and no invalid-bundle file may carry them ([VEC-69], [VEC-71]). The compile cases under
  `compile/keys/` cite them.
- `MERGE-18` and `PROV-49` have listed enforcers in `spec/conformance/enforcers.d/E1.yaml` ([VEC-125]).
