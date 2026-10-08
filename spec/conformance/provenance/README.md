# Provenance cases

This directory holds the provenance family of the conformance suite (section 12.7, [VEC-124] to [VEC-131]). Each `.yaml` file here is one provenance case: it names a canonical bundle under `bundles/` and a sidecar `.json` under this directory, and expects the sidecar to be accepted, or rejected with `E_PROVENANCE_INVALID` ([VEC-129]). The `.json` files are fixtures, not cases ([VEC-126]). `spec/conformance/provenance/records/` holds two sidecars for one bundle, named in the `<stem>.provenance.<label>.json` form ([BUNDLE-149]).

The spec tool runs every case with `pnpm --dir js run spec:check` ([VEC-130], [BUNDLE-151]). An evaluator never reads a sidecar ([BUNDLE-145]), so no evaluate vector uses this family.

## Reference bundle

All 15 cases in the suite name `bundles/retail-pos.json` ([VEC-128]). Its `bundleVersion` is `sha256:5acebbd8528de939c2ca351ef15f2845f9d156eb6c4f275c57501c9edb039df0`. I read that value from the file and checked it against the JCS serialisation of its `content` with the spec tools ([BUNDLE-5], [BUNDLE-6]). The acceptance sidecars carry that digest. `bundles/commerce.json` is an equally valid reference: its `bundleVersion` is `sha256:ab1cd3a96be4ab7569ccaba61cb65dbd14b5efd8239de35f16643ab791af6859`. `retail-pos.json` was chosen because its digest was the one checked when the fixtures were written. The planned cases below use the same bundle.

## Cases

Each case's `id` is its path under `provenance/` without `.yaml` ([VEC-127]). The sidecar fixture of a case has the same stem unless the table says otherwise.

| Case | Sidecar | Expect | Clauses |
| --- | --- | --- | --- |
| `sidecar-minimal` | `sidecar-minimal.json`: required members only | accept | BUNDLE-141, BUNDLE-142, BUNDLE-151 |
| `sidecar-all-members` | `sidecar-all-members.json`: gitCommit (40 digits) and publisher | accept | BUNDLE-141, BUNDLE-142, BUNDLE-150 |
| `sidecar-git-commit-64` | `sidecar-git-commit-64.json`: gitCommit of 64 digits | accept | BUNDLE-150 |
| `multi-record-ci` | `records/retail-pos.provenance.ci.json` | accept | BUNDLE-144, BUNDLE-148, BUNDLE-149 |
| `multi-record-release` | `records/retail-pos.provenance.release.json` | accept | BUNDLE-144, BUNDLE-148, BUNDLE-149 |
| `sidecar-bundle-version-mismatch` | bundleVersion differs in its last digit only | `E_PROVENANCE_INVALID` | BUNDLE-144, BUNDLE-151 |
| `sidecar-duplicate-member` | createdAt repeated | `E_PROVENANCE_INVALID` | BUNDLE-111, BUNDLE-151 |
| `sidecar-unknown-member` | member `note`, which the record does not define | `E_PROVENANCE_INVALID` | BUNDLE-143, BUNDLE-151 |
| `sidecar-missing-created-at` | createdAt absent | `E_PROVENANCE_INVALID` | BUNDLE-141, BUNDLE-151 |
| `sidecar-gitcommit-short` | gitCommit of 39 digits | `E_PROVENANCE_INVALID` | BUNDLE-150, BUNDLE-151 |
| `sidecar-gitcommit-uppercase` | gitCommit of 40 uppercase digits | `E_PROVENANCE_INVALID` | BUNDLE-150, BUNDLE-151 |
| `sidecar-createdat-space` | space in place of `T` | `E_PROVENANCE_INVALID` | BUNDLE-150, BUNDLE-151 |
| `sidecar-createdat-offset` | `+00:00` in place of `Z` | `E_PROVENANCE_INVALID` | BUNDLE-150, BUNDLE-151 |
| `sidecar-createdat-fraction` | fractional seconds | `E_PROVENANCE_INVALID` | BUNDLE-150, BUNDLE-151 |
| `sidecar-createdat-not-a-date` | `2026-02-30T12:00:00Z` | `E_PROVENANCE_INVALID` | BUNDLE-141, BUNDLE-151 |

VEC-131 needs cases (a) to (d). They are `sidecar-bundle-version-mismatch`, the two `multi-record-*` cases, `sidecar-duplicate-member` and `sidecar-unknown-member`. The other cases extend the family to the forms that BUNDLE-141 and BUNDLE-150 give.

## Fault coverage under BUNDLE-151

[VEC-124] says the family covers the sidecar faults that [BUNDLE-151] reports as `E_PROVENANCE_INVALID`. The table lists each fault class of [BUNDLE-151] and the case that reaches it. Rows marked *planned* have a case specified below but no file yet. Rows marked *none* have neither (see OPEN 5).

| Fault | Clause | Case |
| --- | --- | --- |
| Strict parse fails on a duplicate property name | BUNDLE-111, BUNDLE-151 | `sidecar-duplicate-member` |
| Strict parse fails on input that is not well-formed JSON | BUNDLE-151 | none |
| Sidecar is not a JSON object | BUNDLE-141 | *planned* `sidecar-not-object` |
| Required member `bundleVersion` absent | BUNDLE-141 | *planned* `sidecar-missing-bundle-version` |
| Required member `createdAt` absent | BUNDLE-141 | `sidecar-missing-created-at` |
| Member that BUNDLE-141 and BUNDLE-142 do not define | BUNDLE-143 | `sidecar-unknown-member` |
| `bundleVersion` not `sha256:` plus 64 lowercase hex digits | BUNDLE-141, BUNDLE-5 | none |
| `bundleVersion` differs from the bundle | BUNDLE-144 | `sidecar-bundle-version-mismatch` |
| `gitCommit` of the wrong length | BUNDLE-150 (a) | `sidecar-gitcommit-short` |
| `gitCommit` in uppercase | BUNDLE-150 (a) | `sidecar-gitcommit-uppercase` |
| `gitCommit` with a non-hex character, or not a string | BUNDLE-150 (a) | none |
| `createdAt` with a space in place of `T` | BUNDLE-150 (b) | `sidecar-createdat-space` |
| `createdAt` with lowercase `t` in place of `T` | BUNDLE-150 (b) | *planned* `sidecar-createdat-lowercase-t` |
| `createdAt` with `+00:00` in place of `Z` | BUNDLE-150 (b) | `sidecar-createdat-offset` |
| `createdAt` with lowercase `z`, or with no offset | BUNDLE-150 (b) | none |
| `createdAt` with fractional seconds | BUNDLE-150 (b) | `sidecar-createdat-fraction` |
| `createdAt` with a field out of range (month 13, hour 24) | BUNDLE-150 (b) | none |
| `createdAt` that is not a real calendar date | BUNDLE-141 | `sidecar-createdat-not-a-date` |
| `publisher` that is not a string | BUNDLE-150 (c) | *planned* `sidecar-publisher-not-string` |

## Planned cases

The four cases below close the four faults of [BUNDLE-151] that the table above marks *planned*. They are specified here and are not in the suite. Creating them needs the owner of this family, because `spec/AUTHORING.md` (section "Files") forbids a work item from creating files outside its owned list, and this item owns only this README. Each case is a `.yaml` file with the same shape as `sidecar-missing-created-at.yaml`. Its `bundle` is `../bundles/retail-pos.json`, its `specVersion` is `"0.1"`, and its `expect` is `error: E_PROVENANCE_INVALID`. Each fixture is a `.json` file under this directory, and VEC-126 exempts it from the naming rule of [BUNDLE-149]. The bundle digest below is the reference digest.

| Planned case | Clauses | Fixture body (exact) | Why it is rejected |
| --- | --- | --- | --- |
| `sidecar-missing-bundle-version` | BUNDLE-141, BUNDLE-151 | `{ "createdAt": "2026-10-09T12:00:00Z" }` | The required member `bundleVersion` is absent. |
| `sidecar-not-object` | BUNDLE-141, BUNDLE-151 | `[ { "bundleVersion": "sha256:5acebbd8528de939c2ca351ef15f2845f9d156eb6c4f275c57501c9edb039df0", "createdAt": "2026-10-09T12:00:00Z" } ]` | The sidecar is a JSON array, not an object. |
| `sidecar-createdat-lowercase-t` | BUNDLE-150, BUNDLE-151 | `bundleVersion` as above, `"createdAt": "2026-10-09t12:00:00Z"` | The separator is lowercase `t`, not `T`. |
| `sidecar-publisher-not-string` | BUNDLE-142, BUNDLE-150, BUNDLE-151 | `bundleVersion` and `createdAt` as above, `"publisher": 42` | `publisher` is a number, not a string (BUNDLE-150 (c)). |

Where a row says "as above", the fixture holds `bundleVersion` set to the reference digest and `createdAt` set to `2026-10-09T12:00:00Z`, and differs only in the member that the row names.

## Compilation decisions

- **Near-miss digest.** The mismatch sidecar changes only the last hex digit of the bundle's digest. A comparison by prefix would then accept it, so the case tests the exact comparison of [BUNDLE-144].
- **Fixture names and placement.** [VEC-126] and [VEC-128] require a sidecar that a case names to be a `.json` file under `provenance/`, so the fixtures sit there (the two records in `records/`) and not beside the bundle in `bundles/` where [BUNDLE-139] and [BUNDLE-149] place a real sidecar. [VEC-126] exempts fixtures from the naming rule of [BUNDLE-149]. The two records use the further-sidecar form `<stem>.provenance.<label>.json` of [BUNDLE-149], with the lowercase labels `ci` and `release`. No primary `<stem>.provenance.json` exists for `retail-pos` in the suite; [VEC-126] exempts the fixtures, and [BUNDLE-148] needs only that several records for one `bundleVersion` each have a sidecar of their own.
- **Calendar date.** `sidecar-createdat-not-a-date` expects rejection, because `2026-02-30` is not an RFC 3339 full date. [BUNDLE-141] asks for a time in RFC 3339 form, and RFC 3339 section 5.6 (`date-mday`) allows only days that exist in the month and year. The schema `provenance.schema.json` leaves calendar validity to the checker, and the check tool (`isRealDate` in `js/spec-tools/check.mjs`) already applies this reading under [BUNDLE-141]. See OPEN 3.
- **Expected code.** Every rejecting case expects `E_PROVENANCE_INVALID`, the only code a provenance case may expect ([VEC-129]). Messages are not compared ([VEC-52], [VEC-130]).
- **Not covered here.** [BUNDLE-139] is covered by the `build` mapping of [VEC-107], not by this family. The publisher form of [BUNDLE-150] (c) has no case in the suite yet; the planned `sidecar-publisher-not-string` covers it. [BUNDLE-147] and the no-replacement rule of [BUNDLE-148] concern tool storage, and enforcers in `spec/conformance/enforcers.yaml` list them ([VEC-125]).

## Open points

- **OPEN 1: ownership.** The owned-file list named only this README. The 15 case `.yaml` files and the 15 `.json` fixtures under `provenance/` (including `records/`) were written because the brief asked for the family in this directory. The owner should confirm that they belong to this work item (`spec/AUTHORING.md`, section "Files").
- **OPEN 2: reference bundle.** Every acceptance sidecar and the near-miss carry the digest of `bundles/retail-pos.json`. If that bundle is regenerated, the digests in these fixtures must be recomputed. The owner of `bundles/` may prefer `commerce.json` as the reference name; the file stems then change too.
- **OPEN 3: calendar validity.** Nothing in section 10 states outright that an impossible calendar date fails [BUNDLE-141]; the reading rests on RFC 3339 section 5.6 and on the check tool, which already applies it. Recommended resolution: keep the case and the reading, and add a half-sentence to [BUNDLE-141] or [BUNDLE-150] (b) ("a real calendar date") when the section 10 owner next edits it.
- **OPEN 4: planned cases not yet created.** The four planned cases above are specified but no `.yaml` or `.json` file exists for them. The owner of this family must create them under `provenance/` to close the four BUNDLE-151 faults. This item did not create them, because `spec/AUTHORING.md` (section "Files") limits a work item to its owned files. The four were chosen because each is a fault that BUNDLE-151 names and that no existing case reaches. The reviewer should confirm that this is the intended set.
- **OPEN 5: remaining BUNDLE-151 faults with no case.** The table above marks five fault classes *none*: input that is not well-formed JSON; a `bundleVersion` in the wrong form; a `gitCommit` with a non-hex character or that is not a string; a `createdAt` with lowercase `z` or no offset; and a `createdAt` with a field out of range (month 13, hour 24). Even with the four planned cases, these remain. The reviewer should decide whether each needs a case under [VEC-124], or whether this family should state in its text which faults it leaves out.

## Verification

`pnpm --dir js run spec:check --no-write` passes with the 15 existing provenance cases counted. The planned cases are not counted, because their files do not exist. Their four fixture bodies were run in a scratch script outside the repository, through the checker's own schema compiler (`createSchemas` and `parseJsonStrict` in `js/spec-tools/lib/`) and with the `bundleVersion` and `isRealDate` checks of `js/spec-tools/check.mjs`. All four were rejected for the reason the table gives. The controls behaved as expected: `sidecar-minimal` was accepted, and `sidecar-missing-created-at` was rejected. The `--no-write` flag keeps `spec/conformance/COVERAGE.md` unchanged, because that file is not owned here.
