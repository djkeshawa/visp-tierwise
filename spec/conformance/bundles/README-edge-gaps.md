# Edge bundle for the G1 gap cases

One canonical bundle file serves the G1 gap cases: `edge-gaps.json`. It is written by hand,
because no reference compiler exists yet. The content object was written as JSON outside the
repository, then turned into canonical bytes by `js/spec-tools/canonicalize.mjs`, which checks
the content against `spec/schemas/bundle.schema.json` first ([BUNDLE-2], [BUNDLE-6]). Nothing
here verifies that a compiler writes these exact bytes. That check waits for a compiler.

| Bundle | `bundleVersion` | Compiled from | Used by |
| --- | --- | --- | --- |
| `edge-gaps.json` | `sha256:3f7303fc53c424104bdb1e36968a3c80210f35b7ea47fc34c11a1aec7588eb92` | the base project below, reproduced in `compile/gap-norm-nfd-universe-member/project/` | every `gap-*-ok` control, `gap-norm-nfd-universe-member` (the positive), and the evaluate vectors under `evaluate/gaps/` |

## Base project

Every compile case in `compile/gap-*` is the base project with one edit, except the bundle
expectation cases, which are the base project itself, and `gap-key-declared-twice-preset` and
its control, which are a copy of the preset case of another item. Each `-ok` control, and the bundle
positive `gap-norm-nfd-universe-member`, compiles to this bundle.

- Manifest: `specVersion: "0.1"`, `schema/core.yaml`, `rules/*.yaml` and `values/root.yaml` at
  the root level.
- Hierarchy: levels `global` (root) and `account`. No axes. `account` is service-sourced and
  open ([SCOPE-24], [SCOPE-26]).
- Entities: `company`; `customer` belongs to `company`; `order` belongs to `customer`. A
  decision about `order` therefore has `company.*` facts as single-value facts through two
  `belongs_to` edges ([ENTITY-10]).
- Facts: `company.active` (boolean), `order.label` (enum, `café` in NFC, `tea`, `water`),
  `order.total` (decimal), `order.units` (integer).
- Keys: `catalog.tags` (set, universe `café` and `tea`, combine `union`, settable at `global`);
  `profile.timezone` (`iana_tz` string, `override`, default `Europe/London`, `skip`, settable at
  `account`); `quota.seats` (integer 1 to 50, `min`, `deny`, settable at `account`).
- Features: `checkout.order_gate` (decided per `order`; policy `gate` with rule
  `company-inactive` on `company.active` false, and rule `total-one-and-a-half` on
  `order.total` equal to `1.5`, default `DENY`) and `checkout.total_probe` (decided per
  `order`; policy `probe` with rule `total-in-probe-set` on `order.total` in
  `["0", "1.5", "7.1"]`, default `DENY`). Both return `ALLOW` or `DENY`, with `if_fact_missing`
  `DENY`.
- Values: the root value `catalog.tags` is written with the decomposed form of `café`, which
  normalises to the universe member ([NORM-1]). The bundle holds the NFC form.

| Decision | Clauses | Result in the bundle |
| --- | --- | --- |
| The decomposed set member is NFC-normalised before membership | [NORM-1], [NORM-2], [TYPE-47] | `values.global[""]["catalog.tags"]` is `["café"]` |
| The `iana_tz` default is a compile-time zone name | [TYPE-34], [TYPE-80] | `profile.timezone.default` is `"Europe/London"`, listed in `spec/data/tz-names.txt` |
| The grandparent fact of `order` is single-value | [ENTITY-10], [FACT-13] | `company.active` is a fact of `order`'s ancestor; it is in the precheck of `checkout.order_gate` |
| Decimal literals are canonical | [NORM-13], [TYPE-26] | `"1.5"` and `["0", "1.5", "7.1"]` |
| Only the root binding exists, at `global` with node key `""` | [BIND-2], [BIND-13] | `bindings.global[""]` for each feature |
| Rule `when` is sorted by fact name and rules keep source order | [RULE-8], [BUNDLE-96] | one condition per rule in these rules |

## Compile cases

Each positive has a negative control (`-ok` for a bundle control; `-control` for the bundle
positive). A positive lists exactly one finding. `gap-key-declared-twice-preset` is the one case that does not use the base project (see its row). Findings are in the form of [VEC-97].

| Positive (findings) | Clauses | Edit to the base project | Control |
| --- | --- | --- | --- |
| `gap-setting-both-forms` | POL-12, POL-11 | `limit` with `value_of` and `list_of` in `checkout.order_gate` | `gap-setting-both-forms-ok` |
| `gap-setting-no-form` | POL-12, POL-11 | `limit: {}` in `checkout.order_gate` | `gap-setting-no-form-ok` |
| `gap-scope-root-in-axes` | SCOPE-11, SCOPE-3, SCOPE-4 | `axes: [global]` in `schema/core.yaml` | `gap-scope-root-in-axes-ok` |
| `gap-returns-enum-repeated` | TYPE-6, FEAT-7, FEAT-8, TYPE-43, TYPE-52 | a second `ALLOW` in `returns.enum` | `gap-returns-enum-repeated-ok` |
| `gap-returns-enum-non-string` | TYPE-6, TYPE-41, FEAT-7, FEAT-8 | the number `7` in `returns.enum` | `gap-returns-enum-non-string-ok` |
| `gap-norm-nfd-duplicate` | NORM-2, NORM-1, TYPE-43, TYPE-52 | a decomposed `café` after `water` in `order.label` `values` | `gap-norm-nfd-duplicate-ok` |
| `gap-norm-nfd-universe-member` (bundle positive) | NORM-2, NORM-1, TYPE-47 | none (the base project) | `gap-norm-nfd-universe-member-control` (findings) |
| `gap-key-declared-twice-preset` | KEY-1, SRC-40, SRC-41, MANIFEST-31 | not an edit of the base project: its own small project, a copy of `src-key-declared-twice-preset`, in which a path preset and the project declare the key `checkout.allowed` | `gap-key-declared-twice-preset-control` (bundle `edge-manifest.json`, not `edge-gaps.json`) |

`gap-norm-nfd-universe-member-control` removes `café` from the universe, so the decomposed
root value is not a member and `E_NOT_IN_UNIVERSE` is reported at `values/root.yaml`, path
`["catalog.tags", 0]`, line 1. This control exercises [TYPE-48]. It shows that the positive
case is a member only through normalisation, and [NORM-2] itself is observed by the positive.

## Evaluate vectors (`evaluate/gaps/`)

| Vector | Clauses | Operation and expectation |
| --- | --- | --- |
| `entity-grandparent-fact-single-value` | ENTITY-10, FACT-13, FACT-15 | `decide`; `company.active` as a single-value fact; `DENY` by `company-inactive` |
| `entity-grandparent-fact-missing` | ENTITY-10, FACT-16, RULE-44 | `decide`; `company.active` missing, so `if_fact_missing` with `MISSING_FACT` |
| `decimal-eq-canonical-equal` | TYPE-27, RULE-29, RULE-37 | `decide`; `"1.50"` equals `"1.5"` under `eq`; `ALLOW` |
| `decimal-eq-canonical-control` | TYPE-27, RULE-29 | `decide`; `"1.51"`; `DENY` by default |
| `decimal-in-canonical-equal` | TYPE-27, RULE-30, RULE-37 | `decide`; `"1.50"` is a member of the `in` list; `ALLOW` |
| `decimal-in-canonical-control` | TYPE-27, RULE-30 | `decide`; `"1.51"`; `DENY` by default |
| `fact-integer-given-string-raises` | TYPE-74, FACT-35 | `decide`; `order.units` given as `"3"`; `E_FACTS_INVALID` |
| `fact-integer-given-integer-control` | FACT-35, TYPE-17 | `decide`; `order.units` given as `3`; `ALLOW` |
| `tz-bad-syntax-space-skip` | TYPE-37, TYPE-75, PROV-30 | `resolve`; `"Europe/ London"` is INVALID under `skip`; the default `Europe/London` stays |
| `tz-bad-syntax-double-slash-skip` | TYPE-37, TYPE-75, PROV-30 | `resolve`; `"Europe//London"` is INVALID under `skip`; the default stays |
| `tz-valid-syntax-unknown-zone-accepted` | TYPE-37, TYPE-38, TYPE-39 | `resolve`; `"Mars/Olympus_Mons"` passes the syntax check and is accepted |
| `quota-seats-invalid-string-deny` | TYPE-75, SVC-9, PROV-41 | `resolve`; `"many"` is INVALID under `deny`; the deny value `1` (the `min` member) is the result |
| `quota-seats-valid-accepted` | TYPE-19, SVC-31 | `resolve`; `10` is accepted; the result is `10` |

## Readings this bundle depends on

These are the author's readings of the clauses. They are not settled by a clause or an ADR, and a
spec owner should confirm them (see the open points below).

1. **SCOPE-11, root listed in `axes`.** The root name is a repeated name ([SCOPE-3], [SCOPE-4]),
   not a second axis, in the same way as in the existing case
   `hierarchy-scope-name-repeated-across-levels-and-axes`. So the root needs no scope entry
   ([SCOPE-12], [SCOPE-14]), and `binding_settable_at: [global]` does not name a declared axis
   ([FEAT-20]). Under the other reading, the case would also report a missing axis entry and
   `E_BINDING_NOT_ALLOWED` for each feature. Only the first reading gives one finding. The entry
   for SCOPE-11 in `enforcers.d/C2.yaml` takes the other view (that [SCOPE-12] and [SCOPE-14] may
   add findings), so the two records disagree until a spec owner chooses.
2. **Location of a repeated list item or repeated scope name.** A repeated item in an enum list,
   a `values` list or the `levels`/`axes` lists is reported at the later entry, as [SRC-9] does
   for a repeated key. No clause states this for list items. The convention is shared with the
   existing cases `type-enum-values-duplicate`, `feat-returns-duplicate` and
   `hierarchy-scope-name-repeated-across-levels-and-axes`.
3. **POL-12 with both forms or none.** The `both` and `neither` settings have no `default`, so
   [POL-11] also fires. Both codes report the setting node, so [FIND-25] gives one finding key.
   No default node is named, which avoids a default-value finding whose form is undefined. For
   `both`, [POL-13] is read as covering only entries other than `default`, `value_of` and
   `list_of` (as in `pol-setting-extra-entry`), because a declaration with two forms has no one
   "its form" from which the other entry would differ. Under the other reading, a second finding
   would be reported at the `list_of` entry. This is why `gap-setting-both-forms` cites POL-12
   and POL-11 and not POL-13.
4. **NORM-2 universe member.** A bundle positive has no trigger to remove, so its control is a
   findings case that removes the universe entry. That control exercises [TYPE-48] and not
   [NORM-2], which only the positive observes. It is a reading of [VEC-120], not an exact match.
5. **`iana_tz` runtime syntax.** The `Mars/Olympus_Mons` vector follows [TYPE-37] and [TYPE-38]:
   the evaluator checks syntax only, so a name missing from the release is accepted.
6. **KEY-1 through a preset.** A preset is an earlier layer than the project ([MANIFEST-31]), so
   the project's declaration is the second one ([SRC-39], [SRC-40]) and carries the finding. This
   does not depend on file order within the project, which [SRC-37] forbids.

## Withheld clauses (open points)

[KEY-12] (two values for one key at one scope and node, from two sources, including two root
values) and [POL-5] (two rules documents for one feature) are not covered by a case in this item.
Two sources of the project's own layer are on an equal footing ([SRC-37]), and no clause says
which of the two carries the finding (`E_DUPLICATE_VALUE` for [KEY-12], `E_DUPLICATE_DEFINITION`
for [POL-5]) or, for a rules document, whether the node is the document or its `feature` entry.
The set comparison of [VEC-98] needs one answer. The clauses are not marked *(OPEN)* in the spec,
so [VEC-114] does not by itself apply to them. The spec owners should mark [KEY-12], [SRC-42],
[POL-5] and [SRC-44] as *(OPEN)* and list them in the Open points of sections 1, 5 and 7, or
settle the location in an ADR. Until then the coverage record should show them as uncovered.

Today the coverage record shows `KEY-12` with a "Planned" enforcer in `enforcers.d/C3.yaml`, and
`POL-5` as cited by `pol-duplicate-rules-file`, which uses the later file in path order and says
in its description that the choice is open. [VEC-125] allows an enforcer only for a clause that no
vector can observe, and these clauses can be observed once the location is fixed, so the "0
uncovered" result of the check tool is structural, not a statement that the cases exist.
([KEY-1] was in the same position. It is now cited by `gap-key-declared-twice-preset`, whose
location rests on the layer order of [MANIFEST-31] and needs no choice between files.)

Candidate resolutions: report the finding at every declaration or value after the first, in
code-point order of the file path (inline content in `tierwise.yaml` ordered as that path); or
report it once at a fixed node such as the first item of a sorted source list. For a rules
document, locate the finding at its `feature` entry, as `pol-duplicate-rules-file` does. The
recommendation is the first candidate, because it uses an order that is neither the order of the
manifest patterns nor the order of discovery, so [SRC-37] holds.

## Checks run

- `pnpm --dir js run spec:check -- --no-write`: passes (0 uncovered clauses, 51 of 51 engine
  codes with a compile case). The 0 counts the Planned enforcer of KEY-12 and the citation of POL-5
  described above.
