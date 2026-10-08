# spec/conformance/canonical

Canonical vectors (section 12.4) for the two primitives that the bundle clauses rely on:
the JSON Canonicalization Scheme ([BUNDLE-7]) and the SHA-256 digest ([BUNDLE-6]). A
canonical vector has no bundle and no call, and it is checked against its expected bytes.

## Layout

The canonical format holds one case per file ([VEC-2]), so each group below is a
directory with one file per case, not one file per theme. Each `id` is the file's path
relative to `canonical/` without `.yaml` ([VEC-12]). Every `.yaml` file here is a vector.

| Group | Files | Clauses cited | What the group checks |
| --- | --- | --- | --- |
| `jcs/ordering/` | 6 | BUNDLE-9, BUNDLE-8, DET-5 | Member order by UTF-16 code units, the cases where that differs from code point order, mixed case, digits and punctuation, prefixes and the empty name, nested objects |
| `jcs/strings/` | 7 | BUNDLE-10, BUNDLE-9, BUNDLE-138 | Named escapes, other control characters as lowercase `\u00xx`, DEL and non-ASCII kept literal, non-BMP characters, non-ASCII and escaped member names, no normalisation |
| `jcs/numbers/` | 4 | BUNDLE-11, BUNDLE-12, DET-4 | `0`, positive and negative integers, and the two safe-integer limits plus and minus 2^53 - 1 |
| `jcs/structure/` | 5 | BUNDLE-7, BUNDLE-8, BUNDLE-9 | Empty object and array, nested arrays, `true`, `false`, `null`, no whitespace, array order kept |
| `sha256/` | 7 | BUNDLE-6 | Empty input, `abc`, inputs of 55, 56, 64 and 1000 bytes, and a non-UTF-8 input in the hex form |
| `bundle-digest/` | 3 | BUNDLE-1, BUNDLE-2, BUNDLE-4 to BUNDLE-9 | The section 10 example content: its canonical bytes, its digest, and the canonical envelope |

The `bundle-digest/` vectors form a chain. `content-jcs` gives the canonical bytes of the
example content, `content-sha256` gives their digest, and `envelope-jcs` embeds that digest
as `bundleVersion` and gives the full bundle bytes. The digest is
`sha256:1e9c6ff42b19ba87006b172f1cc5211cc04bbea48b4d297afe441aa1e6a13403`. The content is
the non-normative example in section 10, and it also validates against
`spec/schemas/bundle.schema.json`.

### Vector ids

Each id is the path of a file in this directory without `.yaml` ([VEC-12]).

- `jcs/ordering/`: `jcs/ordering/digits-letters-punctuation`, `jcs/ordering/mixed-case`, `jcs/ordering/nested-objects`, `jcs/ordering/prefix-and-empty-name`, `jcs/ordering/utf16-bmp-above-e000-vs-non-bmp`, `jcs/ordering/utf16-fffd-vs-u10000`
- `jcs/strings/`: `jcs/strings/control-characters`, `jcs/strings/del-and-non-ascii-literal`, `jcs/strings/escapes-named`, `jcs/strings/member-names-escaped`, `jcs/strings/member-names-non-ascii`, `jcs/strings/no-normalisation`, `jcs/strings/non-bmp-literal`
- `jcs/numbers/`: `jcs/numbers/negative`, `jcs/numbers/safe-integer-max`, `jcs/numbers/safe-integer-min`, `jcs/numbers/zero`
- `jcs/structure/`: `jcs/structure/array-order-kept`, `jcs/structure/booleans-and-null`, `jcs/structure/empty-array`, `jcs/structure/empty-object`, `jcs/structure/nested-arrays`
- `sha256/`: `sha256/abc`, `sha256/empty-input`, `sha256/hex-non-utf8-bytes`, `sha256/input-1000-bytes`, `sha256/input-55-bytes`, `sha256/input-56-bytes`, `sha256/input-64-bytes`
- `bundle-digest/`: `bundle-digest/content-jcs`, `bundle-digest/content-sha256`, `bundle-digest/envelope-jcs`

## Coverage of [VEC-85] to [VEC-89]

- [VEC-85], ordering that differs by UTF-16 code units and by code points:
  `jcs/ordering/utf16-bmp-above-e000-vs-non-bmp` and `jcs/ordering/utf16-fffd-vs-u10000`.
- [VEC-86], non-ASCII member names: `jcs/strings/member-names-non-ascii`.
- [VEC-87], string escapes: `jcs/strings/escapes-named`, `jcs/strings/control-characters`
  and `jcs/strings/member-names-escaped`.
- [VEC-88], integers at plus and minus 2^53 - 1: `jcs/numbers/safe-integer-max` and
  `jcs/numbers/safe-integer-min`.
- [VEC-89], SHA-256 inputs on the padding boundaries: `sha256/empty-input`,
  `sha256/input-55-bytes`, `sha256/input-56-bytes` and `sha256/input-64-bytes`.

## Not in this directory

- **Rejection cases.** A non-integer number and an integer outside plus or minus 2^53 - 1 are
  runner failures for a `jcs` vector ([VEC-79], [VEC-80]), and a duplicate member is a runner
  failure for a vector file under [VEC-7]. A canonical vector cannot name an expected error.
  These faults are to be tested as bundle files under `bundles/invalid/` ([VEC-71], [VEC-80],
  [BUNDLE-111], [BUNDLE-116]). Those files belong to another work item and are not yet present.
- **Negative zero.** The number syntax of [BUNDLE-11] excludes `-0`, so the subset needs no
  vector for it. YAML reads the token `-0` as the integer `0` ([TYPE-16]), so a vector could not
  show a difference from `0`. [VEC-71] and [VEC-80] list no `-0` fault, so the rejection of `-0`
  is not tested by this suite.

## Derivation

The expected bytes were derived by hand for the ordering and escape cases, and every expected
output was checked against an independent JCS serialiser and against `sha256sum`. The two
ordering cases that differ by UTF-16 code units were also checked to fail under code point
order. The scripts used for the check are not kept in the repository.
