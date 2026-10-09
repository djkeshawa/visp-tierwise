# spec/conformance/bundles/edge-keys.json

This bundle is the focused bundle of work item C3: types, keys, strategies and axes (sections 4 and 5, and the compile-time parts of MERGE and AXIS). It is built from the base project of the compile cases under `spec/conformance/compile/keys/valid-base/project/`. The file holds every valid form that those clauses admit, so each fault case there differs from the base by one edit.

- `bundleVersion`: `sha256:22db17a46825f5572f5617b693994dd405e253fcdccb361d64868977f2aba505` ([BUNDLE-5], [BUNDLE-6]).
- `specVersion` `"0.1"`, `bundleFormatVersion` `"1"`. The file has no trailing newline ([BUNDLE-4]).
- Content: 21 keys, 3 levels (`global`, `division`, `account`) and 1 axis (`seller`), no entities, no facts, no features.
- Built by `js/spec-tools/canonicalize.mjs` from a content object that was written by hand, outside the repository, from the base project. No reference compiler exists yet, so each entry below was derived by applying the cited clauses. The canonicalizer checks the content against `spec/schemas/bundle.schema.json` before it writes the bytes, and `pnpm --dir js run spec:check` confirms the digest and the canonical form.

## Cases that use this bundle

- `valid-base`: a `bundle` expectation. It is the base project, and it is the negative control ([VEC-120]) for every fault case under `compile/keys/`. Each fault case is this project with one edit, and it expects its findings.
- `valid-nfc-precomposed`: a `bundle` expectation. It differs from the base only in that the greeting is written in NFC, so both forms must give the same bytes ([NORM-1]).

## Compilation decisions

| Decision | Clauses | Result in the bundle |
| --- | --- | --- |
| A decomposed string is NFC-normalised before it is stored | [NORM-1], [NORM-2], [TYPE-29] | `greeting` value `"Café"` (U+00E9) in `values.global[""]` |
| A field name and an object key written decomposed are NFC-normalised | [NORM-1], [TYPE-68] | `contact` fields and value key `"Région"` (U+00E9) |
| Decimal strings are canonicalised | [TYPE-26], [NORM-10], [NORM-13], [BUNDLE-71] | `fee_rate` default `"1.5"` from `"1.50"`; global `"2"` from `"2.00"`; division `north` `"7.1"` from `"+007.100"` |
| Integer forms of the core schema are read as their value | [TYPE-15], [TYPE-16] | `handling_days` global `15` from `0o17`; `retry_count` global `0` from `-0` |
| An integer at the engine limit is valid | [TYPE-18], [DET-4] | `retry_count` at seller `acme`: `9007199254740991` |
| Bounds are inclusive | [TYPE-19] | `max_lines` `500` with `max: 500` is written |
| An empty string is valid when there is no format | [TYPE-30] | `division_hint` `""` at division `north` |
| `time_hhmm` and `iana_tz` values are accepted when they match | [TYPE-33], [TYPE-34] | `opens_at` `"09:15"`; `zone` `"US/Eastern"` (a link name) and default `"Europe/London"` |
| Enum values are stored in code point order | [BUNDLE-75] | `priority.values` `["HIGH", "LOW"]` from `[LOW, HIGH]` |
| A set keeps the universe order, and its values follow it | [TYPE-50], [TYPE-51], [BUNDLE-74] | `allowed_methods` `["GROUND", "NEXT_DAY"]` from `[NEXT_DAY, GROUND]`; universe `["GROUND", "PICKUP", "NEXT_DAY"]` |
| An empty set is a valid value | [TYPE-49] | `allowed_methods` at division `north` is `[]` |
| A list keeps its source order and repeated elements | [TYPE-55], [TYPE-58], [BUNDLE-72] | `tags` `["vip", "eu", "vip"]` |
| A list of lists is valid, and an empty list is valid | [TYPE-54], [TYPE-57], [TYPE-69] | `route_groups` `[["north", "south"], []]`; `blocked_codes` default `[]` |
| A deep_merge starting value is complete, and an absent optional object field has no member | [MERGE-9], [MERGE-10], [TYPE-66], [BUNDLE-82] | `window` default `{"open": "08:00", "close": "18:00"}`; field `slot` is `optional: true` and absent |
| Partial values of a deep_merge key are kept as written | [MERGE-11], [MERGE-12] | `window` root `{"open": "09:00"}`; division `north` `{"close": "15:00"}` |
| An order-dependent key has a default or a root value, or both | [MERGE-7] | `window`, `lead_hours` and the other keys with a default have one; `tags`, `route_groups`, `contact` and `greeting` have a root value and no default |
| Commutative keys have no default | [MERGE-1], [MERGE-4], [BUNDLE-55] | `hold_flag`, `max_lines`, `retry_count`, `spare_slots`, `allowed_methods` have none |
| An order-dependent key settable on an axis has `combineAcrossAxes`, one of the four allowed values | [AXIS-1], [AXIS-3], [AXIS-4], [AXIS-5], [BUNDLE-57], [BUNDLE-58] | `lead_hours` `"max"` and `handling_days` `"min"` (integer keys); `courier` `"axis_wins"` and `label_source` `"hierarchy_wins"` (string keys) |
| A commutative key settable on an axis has no `combineAcrossAxes` | [AXIS-2], [BUNDLE-57] | `retry_count` settable at `seller`, no member |
| `onSourceFailure` is written only on a key settable at a service-sourced level | [KEY-8], [SVC-7], [BUNDLE-59], [BUNDLE-61] | `plan_tier` `"skip"` (settable at `account`). `handling_days` declares `deny` but is not settable at a service level, so the member is accepted with no effect and omitted |
| A service-sourced level is open, has `values: service`, and has no values entry | [SCOPE-24], [SCOPE-26], [BUNDLE-34], [BUNDLE-67] | `scopes.account` is `{"nodes": "open", "values": "service"}`; no `values.account` |
| An omitted `values` member means `files` | [SCOPE-25], [BUNDLE-35] | `scopes.division` and `scopes.seller` have `"values": "files"` |
| Closed catalogs are embedded in code point order, with `onUnknownNode` | [BUNDLE-38], [BUNDLE-39], [BUNDLE-42], [DET-6] | `division` `["north", "south"]`, `seller` `["acme"]`. `south` has no values, so it has no `values` entry |
| The root level's values sit under the level name, with node key `""` | [BUNDLE-64], [BUNDLE-66] | `values.global[""]` |
| `settableAt` lists levels in declared order, then axes | [BUNDLE-54] | `lead_hours` `["global", "division", "seller"]` |
| `description` and `owner` are dropped | [KEY-9], [BUNDLE-109] | `greeting` keeps only `type`, `combine` and `settableAt` |
| A key has its `type` member and its type members inside `type` | [BUNDLE-52], [BUNDLE-53], [TYPE-7] | `max_lines` `{"type": "integer", "min": 1, "max": 500}` |

## Location conventions of the fault cases (non-normative)

The conventions below place each expected finding. FIND-19 and FIND-22 fix the node, and where the clauses name no node, the case uses the node that the clause is about. The reviewer should confirm them.

- L1: a finding about a value is located at the value's path, for example `[hold_flag]` or `[tags, 1]`.
- L2: a finding about a required entry that is absent is located at the enclosing mapping, with its path and line (FIND-23). For example, a missing `phone` gives `[contact]`, and a missing default gives the key declaration.
- L3: a finding about a member that is malformed or not allowed is located at that member, for example `[keys, lead_hours, max]`.
- L4: a finding about a key as a whole, such as E_STRATEGY_TYPE ([MERGE-3]) or a min greater than a max ([TYPE-14]), is located at the key declaration, `[keys, <key>]`. The clause names the key, and no member alone.
- L5: a finding about a list item or a set member is located at that item's zero-based index, `[<key>, <index>]`.
- L6: a repeated string (a universe, an enum's values, a set member or a settable_at name) is E_DUPLICATE_VALUE at the repeated item, which is the later occurrence in document order. This is the convention that the ENTITY-8 case uses. The spec does not state it, so the reviewer should confirm it.

Two duplicate rules have no case, because the spec names no node for them. A value repeated across sources has no defined node ([KEY-12]). A second declaration of a key in another file has no defined node, because [SRC-37] makes the order of files irrelevant ([KEY-1], [SRC-40]). These are open points for the reviewer.

## Open points (for the reviewer)

- OPEN layout: [VEC-3] says every directory directly under `compile/` is one case, and [VEC-4] needs its `expected.yaml`. The cases here sit in `compile/keys/<case>/`, so `spec:check` reports `compile/keys: missing expected.yaml`. Flattening each case to `compile/keys-<case>/`, with the id prefixed and the bundle path `../../bundles/edge-keys.json`, passes the check for all cases. `compile/rules` has the same fault.
- OPEN location of duplicate values: the cases place E_DUPLICATE_VALUE at the later occurrence, as the ENTITY-8 case does. [TYPE-43], [TYPE-46], [TYPE-52] and [KEY-7] name no node. [SRC-9] fixes the second occurrence for keys only.
- OPEN cross-source duplicates: [KEY-12] (a value repeated across sources) and [KEY-1] (a second declaration across files) name no node, because [SRC-37] makes file order irrelevant. No case exists.
- OPEN key without `type`: [TYPE-4] and [KEY-5] give E_TYPE_REQUIRED, but the spec does not say whether the key's default, its values, or its strategy check (MERGE-3) also report. No key-level case exists. The `items` case covers the code.
- OPEN non-map declaration: [TYPE-11] lists "not a map" as E_INVALID_DECLARATION, but its precedence sentence gives E_TYPE_REQUIRED for a declaration without `type`, and a non-map has no `type`. No case exists.
- OPEN location conventions L3 and L4: a finding about a key as a whole (E_STRATEGY_TYPE, a min above a max) is located at the key declaration, and the spec names no node for these. The member-level cases follow L3.
- Outside this item: [TYPE-6] and [TYPE-72] belong to the features area, and [TYPE-27] belongs to the rule operators. [TYPE-37], [TYPE-38], [TYPE-39], [TYPE-73], [TYPE-74] and [TYPE-75] are runtime clauses for the evaluate items. Each is cited or left to its owner.
- Not compared: the did-you-mean suggestion of E_UNKNOWN_KEY is not asserted, because [VEC-101] and [FIND-28] exclude suggestions.
- The bundle content was derived by hand, and `canonicalize.mjs` checks only the bundle schema. A compiler must confirm that `valid-base` produces these bytes, and that each fault case produces exactly its listed findings.
