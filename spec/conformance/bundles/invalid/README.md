# Invalid bundles

This directory holds the bundle files that the evaluate vectors of the next wave load to expect a load error (VEC-70). Each file has exactly one fault from the closed fault list of VEC-71 and VEC-80 (VEC-69). No file here is a vector, and no file here is checked by the spec tool beyond being counted (stage (c) of `js/spec-tools/check.mjs` skips this directory).

## Base content

Every file is built from one minimal valid `content`, which the tool `js/spec-tools/canonicalize.mjs` writes as canonical bytes:

- `bundleFormatVersion` `"1"` and `specVersion` `"0.1"` (BUNDLE-16, BUNDLE-17).
- `hierarchy`: `levels` is `["global"]`, `axes` is `[]` and `scopes` is `{}`. The root level alone is a valid hierarchy (SCOPE-1, SCOPE-10, SCOPE-14, SCOPE-16, BUNDLE-29, BUNDLE-30, BUNDLE-32).
- `entities`, `facts`, `values` and `features` are present and empty (BUNDLE-19, BUNDLE-22).
- `keys`: one key `example.lead_hours` of type `{ "type": "integer" }`, with `combine` `"override"`, `default` `4` and `settableAt` `["global"]`. The `override` strategy allows an integer (MERGE-3). The default satisfies MERGE-7 and BUNDLE-55, and it is the only numeric member, so the number faults can be made from it (BUNDLE-11, BUNDLE-12). The key members follow KEY-2, BUNDLE-52, BUNDLE-54 and BUNDLE-56.

The base bundle has `bundleVersion` `sha256:b691992919bf310094101644fcf9c12f58e09a67d465e55c2468387f560ff3a0`. It is not stored as a file here: a valid bundle belongs directly under `bundles/` (VEC-1, VEC-9), and `bundles/invalid/` holds only invalid files (VEC-69). The base `content` object, in the canonical form that the digest covers, is:

```json
{"bundleFormatVersion":"1","entities":{},"facts":{},"features":{},"hierarchy":{"axes":[],"levels":["global"],"scopes":{}},"keys":{"example.lead_hours":{"combine":"override","default":4,"settableAt":["global"],"type":{"type":"integer"}}},"specVersion":"0.1","values":{}}
```

## Files

| File | Single fault | Expected load error (EVAL-14 step) | Clauses |
| --- | --- | --- | --- |
| `unsupported-format-version.json` | `bundleFormatVersion` is `"2"`. The digest matches the content. | `E_BUNDLE_UNSUPPORTED` (step 2) | VEC-71, BUNDLE-115, BUNDLE-16, BUNDLE-137, EVAL-25, EVAL-56 |
| `unsupported-spec-version.json` | `specVersion` is `"0.2"`. The digest matches the content. | `E_BUNDLE_UNSUPPORTED` (step 2) | VEC-71, BUNDLE-115, BUNDLE-17, BUNDLE-137, EVAL-25, EVAL-56 |
| `digest-mismatch.json` | `content` has `default` `5`. `bundleVersion` is the digest of the base content (`default` `4`). | `E_BUNDLE_INTEGRITY` (step 3) | VEC-71, BUNDLE-113, BUNDLE-6, EVAL-24 |
| `duplicate-member.json` | The key object repeats `"default"` with the same value `4`, four levels below the envelope. The digest matches the content. | `E_BUNDLE_INTEGRITY` (step 1) | VEC-69, VEC-71, BUNDLE-111, EVAL-24 |
| `extra-envelope-member.json` | The envelope has a third member `"note"` after `content`. The digest matches the content. | `E_BUNDLE_INTEGRITY` (step 1) | VEC-71, BUNDLE-1, BUNDLE-112, EVAL-24 |
| `non-integer-number.json` | `default` is `4.5`. The digest is the SHA-256 of the JCS of the content with `4.5`. | `E_BUNDLE_INTEGRITY` (step 1) | VEC-80, BUNDLE-11, BUNDLE-116, EVAL-24 |
| `integer-out-of-range.json` | `default` is `9007199254740992` (2^53). The digest is the SHA-256 of the JCS of the content with that value. | `E_BUNDLE_INTEGRITY` (step 1) | VEC-80, BUNDLE-12, DET-4, BUNDLE-116, EVAL-24 |
| `empty-scope-name.json` | The level name `""`: `levels` is `["global", ""]`, and the scope `""` has `values` `files` and `nodes` `open`. The digest is the SHA-256 of the JCS of that content. `canonicalize.mjs` refuses this content (`nonEmptyName` in `bundle.schema.json`), so the bytes come from the JCS helper that it uses, `js/spec-tools/lib/jcs.mjs`. | `E_BUNDLE_INTEGRITY` (step 4) | VEC-71, SCOPE-8, BUNDLE-118, EVAL-24 |
| `entity-graph-cycle.json` | Three entities, `A` with `belongsTo` `C` and `hasMany` `B`, `B` with `hasMany` `C`, and `C` with no edges: the cycle C to A to B to C runs through both kinds of edge. The digest matches the content. | `E_BUNDLE_INTEGRITY` (step 4) | VEC-71, ENTITY-17, BUNDLE-118, EVAL-24 |
| `deny-non-narrowing.json` | `onSourceFailure` is `deny` on the `override` key `example.lead_hours`, which is settable at the service-sourced level `account`. The base content has the level `account` (`values` `service`, `nodes` `open`) and the key has `default` `4`. The digest matches the content. | `E_BUNDLE_INTEGRITY` (step 4) | VEC-71, SVC-11, BUNDLE-118, EVAL-24 |
| `deny-min-without-bound.json` | `onSourceFailure` is `deny` on the `min` key `example.lead_hours`, settable at the service-sourced level `account`, whose integer type has no `min` member. The key has no `default`, which a commutative key may not have (MERGE-4). The digest matches the content. | `E_BUNDLE_INTEGRITY` (step 4) | VEC-71, SVC-12, BUNDLE-118, EVAL-24 |

The expected codes come from EVAL-14 (the order of checks) and EVAL-24 and EVAL-25 (which operation raises each code). EVAL-56 gives two cases: a missing version member raises `E_BUNDLE_INTEGRITY`, and a present version value other than exactly the string `"1"` (format) or `"0.1"` (spec), whatever its JSON type, raises `E_BUNDLE_UNSUPPORTED` (BUNDLE-137). The two version files are present string values (`"2"` and `"0.2"`), so EVAL-56 and EVAL-25 give their code directly. No file here has a missing version member (see the open points).

## Compilation decisions

1. **Tool-built files.** The two version faults change one value, and the tool then writes canonical bytes with a digest that matches the content (BUNDLE-6, BUNDLE-2). The only fault is the version. The tool also validates the content against `bundle.schema.json`, and both files pass.
2. **Digest mismatch.** The tool cannot write a wrong digest. The changed content (`default` `5`) was canonicalized by the tool, and the 64 digits of its `bundleVersion` were replaced by the base digest. The bytes are otherwise canonical (BUNDLE-5, BUNDLE-6, BUNDLE-113, VEC-71). This is the only careful hand edit that the fault requires.
3. **Duplicate member.** The repeated `"default"` carries the same value, so the parsed content is the base content and its digest matches. The fault is then the duplicate name alone (BUNDLE-111, VEC-71 "at any depth"). The loader checks this in strict parsing (EVAL-14 step 1).
4. **Extra envelope member.** The tool writes only the two envelope members `bundleVersion` and `content` (BUNDLE-1), so this file is built by hand: the tool's output for the base content with `,"note":"x"` appended before the closing brace. BUNDLE-112 rejects any member other than the two, so the name `note` is arbitrary. It sorts after `content` in UTF-16 order, so the other bytes stay canonical (BUNDLE-9), and `bundleVersion` still matches `content` (BUNDLE-6). The only fault is the third envelope member (BUNDLE-112).
5. **Numbers outside BUNDLE-11 and BUNDLE-12.** The tool refuses these numbers by design (BUNDLE-11, DET-4, VEC-79), so the bytes were written by hand. Each `bundleVersion` is the SHA-256 of the JCS form of its content, with the number written as RFC 8785 requires (`4.5` and `9007199254740992`). The digest therefore matches, and the number is the only fault that the edit introduces (VEC-80). The value `4.5` also breaks the integer type of the key (BUNDLE-56, BUNDLE-118), but EVAL-14 step 1 (BUNDLE-116) reports the number before the structure check of step 4 (BUNDLE-117), so the expected code is unaffected. The same holds for 2^53 as an integer key default (DET-4). The out-of-range value is 2^53, the smallest integer outside the range of DET-4.
6. **One file per version member.** `bundleFormatVersion` and `specVersion` have separate files, so each file has one fault (VEC-69, VEC-71).
7. **Byte-level faults excluded.** No file has a byte-order mark, a trailing newline or other whitespace fault, because VEC-72 excludes byte-level faults from the suite.

## Verification

- `pnpm --dir js run spec:check -- --no-write` passes with no problem. The `--no-write` flag keeps the generated `spec/conformance/COVERAGE.md` unchanged.
- The loader helpers in `js/spec-tools/lib/jcs.mjs` (`parseBundleJson`, `parseJsonStrict`, `bundleVersionOf`) reject `duplicate-member.json` (BUNDLE-111), `non-integer-number.json` (BUNDLE-11) and `integer-out-of-range.json` (DET-4). They accept `digest-mismatch.json`, as it is well-formed and has no number fault. Comparing `bundleVersionOf(content)` with its `bundleVersion` then gives a mismatch (BUNDLE-113), which is the caller's step. The envelope check (BUNDLE-112) is outside those helpers, so it was checked by reading the envelope members directly.

## Open points

- (OPEN) **Structural faults BUNDLE-118 to BUNDLE-136.** Four of them are now in the VEC-71 list, by the sentence that names them: an empty level or axis name (SCOPE-8), an entity-graph cycle (ENTITY-17), `deny` on a non-narrowing strategy (SVC-11) and `deny` on a `min` key without a lower bound (SVC-12). Their files are the last four rows of the table. The other structural faults are not written, because VEC-72 says the suite tests only the listed faults. Recommended resolution: add each one to VEC-71 with one file each, as those two rows do.
- (OPEN) **Missing `bundleFormatVersion` or `specVersion`.** EVAL-56 says this raises E_BUNDLE_INTEGRITY, and the brief named `missing-spec-version.json`. It is not in the VEC-71 list, so it is not written. Recommended resolution: keep it out for v0.1, and add `missing-spec-version.json` with VEC-71 if a later review wants EVAL-56 tested.
- (OPEN) **Version value of another JSON type.** EVAL-56 says a present non-string version raises E_BUNDLE_UNSUPPORTED. This is also outside the VEC-71 list, so it is not written. Recommended resolution: as for the missing member.
