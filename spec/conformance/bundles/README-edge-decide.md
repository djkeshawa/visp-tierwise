# Edge bundle for decide: compilation decisions and coverage

`edge-decide.json` in this directory is the focused bundle for the decide vectors under
`spec/conformance/evaluate/decide/` (work item E5). It is not a sample. It holds 27 features that
exercise each operator, each operand form, the missing-fact precheck, the hit policy, binding
selection and the setting values. The bytes come from `js/spec-tools/canonicalize.mjs`, run on a
content object written in the session scratch directory, outside the repository. Nothing in the
bundle was edited after canonicalization.

- `bundleVersion`: `sha256:27903b3197b5a3efc2948383605beafac375ed4f434e54ca8fd35c0ae867e1a1`
  ([BUNDLE-5], [BUNDLE-6]). The file has no trailing newline ([BUNDLE-4]).
- Check: `pnpm --dir js run spec:check -- --no-write` passes for this file (stage (c)).
- Open points: none. Each decision below is decided by a clause.

## 1. Hierarchy, entities and facts

| Item | Content | Clauses |
| --- | --- | --- |
| Levels | `global` (root), `division`, `site`. Axes: `seller`. | BUNDLE-30, BUNDLE-31, SCOPE-6, SCOPE-7, SCOPE-10 |
| `division` | Closed catalog `north`, `south`, `west`, `onUnknownNode: error`, `values: files`. | BUNDLE-38, BUNDLE-42, SCOPE-22 |
| `site` | Closed catalog `site_001`, `site_002`, `onUnknownNode: ignore`, so an unknown site is skipped. | BUNDLE-42, CTX-14, SCOPE-23 |
| `seller` (axis) | Closed catalog `acme`, `onUnknownNode: ignore`, so an unknown seller is skipped. An axis carries no binding. | BUNDLE-42, BIND-15, CTX-14 |
| Entities | `account` (ancestor of `order`), `order` (`belongsTo: [account]`, `hasMany: [line]`), `line` (child of `order`). | BUNDLE-44, BUNDLE-45, ENTITY-4, FACT-13, FACT-14 |
| Facts | `account.active` boolean; `order.code` string; `order.priority` integer; `order.status` enum `CLOSED, HELD, OPEN`; `order.total` decimal; `line.kind` enum `BULK, GIFT, STANDARD` (child); `line.rate` decimal (child). | BUNDLE-48, BUNDLE-49, BUNDLE-75, FACT-7 |
| Empty members | `keys` and `values` are present and empty, because the decide vectors use no keys. | BUNDLE-19, BUNDLE-22 |

All feature decisions are about `order` (`decidedPer: order`). The single-value facts are
`account.active`, `order.*`. The child facts are `line.*`, so a FactSet gives them under `children.line`.

## 2. Features

Every feature uses `hitPolicy: FIRST`, `bindingSettableAt: [global, division, site]` and the
policy name `main`, except where the table says otherwise. A root binding `global: { "": { policy, settings } }`
is present in every feature ([BIND-13]). `requiredFacts` ([BUNDLE-91], [BUNDLE-92]) is stored in the
bundle, so it is listed here in code point order. [RULE-40] defines the precheck set from the `when`
mappings of the selected policy, independently of that member, and the two agree in this bundle.

| Feature | Exercises | Default | `ifFactMissing` | `requiredFacts` | Bindings besides the root |
| --- | --- | --- | --- | --- | --- |
| `edge.eq_bool` | `eq` literal, boolean, ancestor fact | DENY | DENY | `account.active` | none |
| `edge.eq_enum` | `eq` literal, enum | RELEASE | HOLD | `order.status` | none |
| `edge.eq_integer` | `eq` literal, integer | RELEASE | HOLD | `order.priority` | none |
| `edge.eq_decimal` | `eq` literal, decimal (canonical `2.5`) | RELEASE | HOLD | `order.total` | none |
| `edge.eq_string` | `eq` literal, string, case-sensitive | DENY | DENY | `order.code` | none |
| `edge.in_enum` | `in` literal list, enum | RELEASE | HOLD | `order.status` | none |
| `edge.in_integer` | `in` literal list, integer | RELEASE | HOLD | `order.priority` | none |
| `edge.in_decimal` | `in` literal list, decimal | RELEASE | HOLD | `order.total` | none |
| `edge.setting_eq_enum` | `eq` with a `valueOf` setting `expected_status` (default `OPEN`) | DENY | DENY | `order.status` | division `south` binds `expected_status: HELD` |
| `edge.in_setting_enum` | `in` with a `listOf` setting `allowed_statuses` (default `[CLOSED]`) | DENY | DENY | `order.status` | division `north` binds `[HELD, OPEN]`; `west` binds `[]` |
| `edge.decimal_setting` | `eq` with `valueOf` `target` (default `2.5`), `in` with `listOf` `totals` (default `[1.5, 2.5]`) | RELEASE | HOLD | `order.total` | none |
| `edge.setting_not_in_precheck` | A setting `threshold` on `order.total`, used by no condition ([RULE-41]) | RELEASE | HOLD | `order.status` | none |
| `edge.all_in_literal` | `all_in` literal list over children | DENY | HOLD | `line.kind` | none |
| `edge.any_in_literal` | `any_in` literal list over children | ALLOW | HOLD | `line.kind` | none |
| `edge.all_in_setting` | `all_in` with a `listOf` setting `allowed_kinds` (default `[GIFT, STANDARD]`) | DENY | DENY | `line.kind` | root binds `[GIFT]`; division `south` binds `[BULK, STANDARD]` |
| `edge.any_in_setting` | `any_in` with a `listOf` setting `watch_kinds` (default `[BULK]`) | ALLOW | HOLD | `line.kind` | none |
| `edge.decimal_child` | `any_in` with `listOf` decimal setting `watch_rates` (default `[0.5]`), `all_in` literal `[1.5, 2.5]` | DENY | HOLD | `line.rate` | none |
| `edge.empty_operands` | Empty literal `in`, `any_in`, `all_in`, and empty setting `none` ([RULE-34]) | DENY | DENY | `line.kind`, `order.status` | none |
| `edge.first_hit` | FIRST: a rule that wins, a later rule not checked, an empty `when` ([RULE-2]) | DENY | DENY | `order.status` | none |
| `edge.precheck_forward` | Three rules over `account.active`, `order.priority`, `line.kind`, in source order | DENY | HOLD | `account.active`, `line.kind`, `order.priority` | none |
| `edge.precheck_reverse` | The same three rules in reverse order, the only difference from `edge.precheck_forward` | DENY | HOLD | `account.active`, `line.kind`, `order.priority` | none |
| `edge.first_failure` | The first failing condition in code point order ([RULE-8], [RULE-54]) | DENY | DENY | `account.active`, `line.kind`, `order.status` | none |
| `edge.nullable_effect` | Nullable enum with `null` as the policy default and `ifFactMissing` ([FEAT-9], [PROV-66]) | `null` | `null` | `account.active` | none |
| `edge.label_effect` | Effects `IGNORED` and `SET`, which are contribution labels but have no meaning in a Decision ([PROV-67]) | IGNORED | IGNORED | `order.status` | none |
| `edge.object_effect` | Object form `{code, days}` as the effect, the default and `ifFactMissing` ([FEAT-11], [PROV-66]) | `{STANDARD, 5}` | `{HOLD, 0}` | `order.status` | none |
| `edge.binding_selection` | Three rule-less policies `root_policy` (ROOT), `division_policy` (DIVISION), `site_policy` (SITE) | ROOT | ROOT | none | division `north` and `south` bind `division_policy`; site `site_001` binds `site_policy` |
| `edge.setting_values` | Value setting `expected_status` (default `OPEN`) and list setting `allowed_kinds` (default `[GIFT, STANDARD]`) | DENY | DENY | `line.kind`, `order.status` | root binds `allowed_kinds: [GIFT]`; division `north` binds `[BULK, GIFT, STANDARD]`; `south` binds `expected_status: HELD` and omits `allowed_kinds` |

Notes on the derived content:

- `edge.precheck_forward` and `edge.precheck_reverse` differ only in the order of their rules.
  Their precheck sets are equal, so the same missing-fact result must follow from each. This is the
  "two vectors over bundles that differ only in rule order" pair. The chosen reading places the pair in
  one edge bundle as two features, not as two bundle files; the rules are the only difference, so the
  proof by reordering holds ([VEC-123] item 12).
- `edge.first_hit` and `edge.first_failure` have an empty `when` or several conditions, so the trace
  ordering and the first failing condition are both observable.
- Every `in`, `all_in` and `any_in` list is in code point order ([BUNDLE-75]). Integer lists are
  ascending ([BUNDLE-152]). `listOf` defaults and bindings are in code point order ([BUNDLE-90]).
- Decimal literals and setting values are written in canonical form ([BUNDLE-71], [NORM-13]).
  Runtime decimal fact values in the vectors are written as received (for example `"1.50"`), so
  the canonicalisation of [NORM-15] is what the vectors test.
- A level that has no bindings in a feature is absent from that feature's `bindings`, so there is no
  `division` or `site` member where nothing is bound ([BUNDLE-22]).

## 3. Compilation decisions

| Decision | Clauses | Result in the bundle |
| --- | --- | --- |
| Feature `hitPolicy` is `FIRST`, and `ifFactMissing` is written as declared | BUNDLE-78, BUNDLE-79, BUNDLE-80, FEAT-3, FEAT-15 | Every feature has both members. |
| A nullable enum writes `nullable: true`, and its `null` default and `ifFactMissing` are written as `null` | BUNDLE-24, BUNDLE-82, FEAT-9 | `edge.nullable_effect` only. |
| An object-form `returns` writes field declarations, with `min` and `max` in the `days` integer type | BUNDLE-82, TYPE-7, FEAT-11 | `edge.object_effect` only. |
| `requiredFacts` is the distinct facts of every rule of the policy, in code point order, whether or not a rule can run | BUNDLE-91, BUNDLE-92, RULE-40 | As in section 2. |
| `when` is sorted by fact, and each rule keeps its source order | BUNDLE-93, BUNDLE-96, RULE-8, RULE-46 | Conditions sorted in every rule. Rules in source order. |
| Setting references stay symbolic, as the whole operand | BUNDLE-103, BUNDLE-104, RULE-4 | `"$name"` for each setting reference. |
| A setting is `{default, valueOf}` or `{default, listOf}` | BUNDLE-86, BUNDLE-87, POL-12 | Every setting in section 2. |
| Binding settings are exactly those the source binding writes. Policy defaults are not copied | BUNDLE-107, BUNDLE-108, BIND-23, BIND-24 | `edge.setting_values` `south` writes only `expected_status`, so `allowed_kinds` takes its default. |
| The root binding has node key `""` under `global`, and a non-root binding has its node ID | BUNDLE-66, BUNDLE-105, BIND-2, BIND-3, SCOPE-13 | `bindings.global[""]` in every feature. |
| Closed catalogs are sorted, and carry `onUnknownNode` | BUNDLE-38, BUNDLE-39, BUNDLE-42, BUNDLE-43, SCOPE-22, SCOPE-23, DET-6 | `north, south, west`; `site_001, site_002`; `acme`. |
| `settableAt` and `bindingSettableAt` list levels in level order, with the root first | BUNDLE-54, BUNDLE-81, FEAT-18 | `[global, division, site]` on every feature. |
| Descriptions, examples, sources, presets and lint are excluded | BUNDLE-109, FACT-10, FEAT-1 | No such member is written. |
| The empty `keys` and `values` containers are written | BUNDLE-19 | `"keys": {}`, `"values": {}`. |
| `requiredFacts` for a policy with no rules is `[]` | BUNDLE-91 | `edge.binding_selection`. |

## 4. Coverage of the clause areas RULE, BIND and PROV (Decision)

The 93 evaluate vectors under `spec/conformance/evaluate/decide/` cite the clauses of [RULE-2] to
[RULE-55] that the evaluator observes, and the Decision and binding-selection clauses of
[BIND-14] to [BIND-24] and [PROV-62] to [PROV-84]. The clauses that only an enforcer can observe
are listed in `spec/conformance/enforcers.d/E5.yaml`.

Clauses that only the compiler observes, and that no evaluate vector can cite, are these. They are
covered by compile cases that other work items own, under `spec/conformance/compile/`: the `bind-*`
cases and `bnd-reference-clean` for BIND-1 to BIND-13, and the cases of the `rules` family (with
`pol-rule-reference-*`) for the RULE clauses. They are not listed as enforcers, because a compile case
observes them ([VEC-125]).

- `BIND-1` to `BIND-13`: the shape of `bindings`, the candidate node checks, the root binding and the
  binding-settable levels.
- `RULE-1`, `RULE-5`, `RULE-6`: the shape of `when`, the literal `$` rule and the malformed condition codes.
- `RULE-9` to `RULE-15`, `RULE-17` to `RULE-26` and `RULE-28`: the fact checks, the form checks,
  the setting-reference checks, the literal checks and the order of checks within one condition.

## 5. Vectors

| Directory | Count | Covers |
| --- | --- | --- |
| `decide/samples/` | 15 | Sample domain A (commerce): the restrictive credit-hold policy (mixed basket denied, allowed stock, empty order, missing facts, web channel, north allows everything, invoice release). Sample domain B (retail-pos): the strict and standard policies (mixed basket reviewed, the same basket approved under the platform binding, a mixed basket of a flagged shopper rejected, cash basket approved, flagged shopper rejected, missing facts). |
| `decide/operators/` | 24 | Every operator with literal and `$setting` operands: `eq`, `in`, `all_in` and `any_in`, over boolean, enum, integer, decimal and string facts, with canonical decimals. |
| `decide/children/` | 19 | Child collections: empty, mixed and uniform collections, an omitted collection, a child map without a fact, decimal children, and empty operand lists. |
| `decide/hit-policy/` | 5 | FIRST: the first match wins, the empty `when` matches, the first failing condition in code point order, and a later rule checked after a failure. |
| `decide/precheck/` | 8 | The missing-fact precheck: rule order does not change it, several missing facts are sorted by code point, and a setting-only fact is not in the precheck. |
| `decide/effects/` | 8 | Nullable effects, the contribution labels `SET` and `IGNORED` returned unchanged, and object-form effects. |
| `decide/bindings/` | 10 | Binding selection: the root binding, an unbound node, the most specific binding, an admitted node with no binding, a skipped site that is not eligible, an omitted division, an axis that carries no binding, and two skipped scopes in level-then-axis order. |
| `decide/settings/` | 4 | Setting values: the binding value replaces the default, an omitted setting takes its default and not the root value, and the root binding values. |

Interpretation of "a mixed basket under the restrictive policy is denied" (requirements section 12,
[VEC-123] item 10). Domain A (commerce) has the effect `DENY`, and `domain-a-mixed-basket-denied`
expects it with reason `DEFAULT`. Domain B (retail-pos) has no `DENY` effect: its returns are
`APPROVE`, `REJECT` and `REVIEW`. For domain B the suite takes "denied" in two ways, both literal
to that bundle. The restrictive `strict` policy takes its default `REVIEW` for the mixed basket
(`domain-b-mixed-basket-strict-review`, reason `DEFAULT`, the effect that is not an approval), and the
mixed basket of a flagged shopper at the same store is denied by `REJECT`, the only denying effect
(`domain-b-mixed-basket-flagged-shopper-rejected`, reason `RULE`). No domain B vector expects `DENY`.

## Open points

- (none). Each decision in this file is decided by a clause.
