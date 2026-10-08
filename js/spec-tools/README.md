# @tierwise/spec-tools

Private tools that check the Phase 1 spec suite under `spec/`, and that canonicalise content into a
bundle. The package has no dependencies of its own. It uses the `yaml` and `ajv` packages pinned in the
workspace root (`js/package.json`), so run `pnpm --dir js install --frozen-lockfile` first. It targets
Node 22 or later, and uses only Node built-ins plus those two packages.

## Commands

Run from `js/`:

```sh
pnpm spec:check              # check the suite; exit 1 on a problem
pnpm spec:check --strict     # also fail on coverage and suite gaps
pnpm spec:check --no-write   # do not rewrite spec/conformance/COVERAGE.md (the tests use this)
pnpm test                    # packaging tests, then the spec-tools tests
```

`pnpm spec:check` runs `node spec-tools/check.mjs`. It prints one line per stage, the problems (each
prefixed `FAIL`), the suite gaps, and the coverage gaps, and writes `spec/conformance/COVERAGE.md`. Pass `--strict`
to make coverage and suite gaps fatal. The suite gaps are the missing directories of VEC-1 and the empty
families (no bundle, invalid bundle, evaluate vector, canonical vector, compile case or provenance case). At the time of writing
the repository has no bundle files, no evaluate vectors, no compile cases and no provenance cases, so stages (c) and (d) check
only the canonical vectors; the stage lines print the counts. A bare `--` is ignored, so `pnpm spec:check -- --strict` also works.

Exit codes: `0` no problem; `1` a problem, or with `--strict` a coverage gap; `2` bad arguments.

### `canonicalize`

```sh
node spec-tools/canonicalize.mjs <content.json> <out bundle.json>
```

Reads one content object (the `content` member of a bundle, as JSON), checks it against
`spec/schemas/bundle.schema.json`, and writes the canonical envelope `{bundleVersion, content}` to
`<out bundle.json>`. The bytes are RFC 8785 JCS with no insignificant whitespace and no trailing
newline (BUNDLE-1 to BUNDLE-10). The tool prints the `bundleVersion` (the SHA-256 digest of the
canonical bytes of `content` alone, BUNDLE-6). If the content fails the schema, nothing is written
and the tool exits `1`. The input must be strict JSON in bundle form: duplicate member names,
non-integer numbers and numbers outside ±(2^53 − 1) are rejected.

## What `check` does

| Stage | Checks | Failure |
| --- | --- | --- |
| (a) | Every `spec/schemas/*.json` compiles with ajv 2020, so that `$ref` by `$id` resolves. | yes |
| (b) | Each `presets/*.yaml` against `preset.schema.json`. Each `samples/*/tierwise.yaml` against `manifest.schema.json`, and the schema, rules and values files that it maps (glob patterns, path-mapped patterns, root and multi-node entries, namespaces). A selected file must resolve inside the project root (MANIFEST-12), and must have one role (SRC-20, SRC-34). Each catalog that a schema file names with `nodes: { from: ... }` is read (SCOPE-27 to SCOPE-30). | yes |
| (c) | Each `spec/conformance/bundles/**/*.json` outside `invalid/` against `bundle.schema.json`. Its bytes must be the JCS serialisation of its envelope, and its `bundleVersion` must be the digest of `content`. Sidecars beside a bundle (`<stem>.provenance[.<label>].json`) are checked too. | yes |
| (d) | Each evaluate, canonical, compile and provenance vector against its schema, with its ID (the path, or the directory for a compile case), its bundle or project references, and its ID uniqueness. An integer token in an evaluate vector may not exceed 2^53 in magnitude (VEC-34). Each provenance case runs: the sidecar must be accepted or rejected with `E_PROVENANCE_INVALID` as the case expects. | yes |
| (e) | Every clause that a vector or `enforcers.yaml` cites exists in `spec/sections/` and is not `(OPEN)`. | yes |
| (f) | Every normative clause (bold `[AREA-n]` at the start of a paragraph or list item, not OPEN) is cited by a vector or listed in `spec/conformance/enforcers.yaml`. Every engine code of `spec/error-codes.md` has a compile case that expects it. | only with `--strict` |
| (g) | The code enums of `vector-compile.schema.json` (engine codes and lint identifiers) and of `vector-evaluate.schema.json` (evaluation codes) equal the catalogue. | yes |
| (h) | Writes `spec/conformance/COVERAGE.md`: each clause with its vectors or enforcer, the uncovered clauses, and the engine codes without a compile case. | no |

The tool checks structure and consistency. It does not run compile cases, evaluate vectors or canonical
vectors, because no compiler or evaluator runs in this package yet. Those runners are in the
implementations, which read `spec/conformance/` in place (VEC-108).

### Relaxed strict mode

Ajv runs in strict mode, so unknown keywords and unresolved references are fatal. Three lint-style
checks are relaxed, because they flag valid JSON Schema idioms:

- `strictTypes` is `log`. For example, `if`/`then` branches without a `type`, and union types, are logged
  as notes. The check counts the notes (149 at the time of writing) and does not fail on them.
- `strictRequired` is off. A required name that a sibling branch defines is valid.
- Formats are not checked (`validateFormats: false`), because `ajv-formats` is not a dependency. The
  schemas' `pattern` keywords carry the checks that matter.

### Limits of the YAML loader

`lib/load.mjs` returns plain data, and refuses what the source rules forbid: aliases, anchors, explicit
tags, merge keys, more than one document, a byte-order mark, invalid UTF-8, duplicate keys (compared
after NFC), and non-string mapping keys. It also refuses a float token with an integral value (`1.0`,
`1e3`) and a non-finite float (`.inf`, `.nan`), because plain data cannot keep the first apart from an
integer or the second from JSON data. SRC-1 lists these as core-schema floats, and no clause lets a loader
refuse them, so this is a choice of the tool: the example after TYPE-17 and VEC-35 treat an integral float as
the wrong type in a value, and the code is `E_TYPE_MISMATCH`, or `E_MANIFEST` in the manifest (MANIFEST-3, MANIFEST-4).
A non-integral float such as `0.1` is kept. The check passes `maxInteger` for evaluate vectors, which makes
an integer token above 2^53 in magnitude a failure (VEC-34). Elsewhere an integer beyond 2^53 is read as a
JavaScript number and can lose precision.

### Not enforced yet

Three duties that section 12 gives to the check tool are not implemented: the `additionalProperties: false`
rule for the vector schemas (VEC-20), the canonical-decimal rule for `expect` values (VEC-60, which needs the
bundle key types and evaluate vectors), and the schema-violation codes of `spec/schemas/README.md` (a
violation is printed with its path and message but without an error code, except for sidecars). The compile
coverage asks for one compile case per engine code, not the positive and negative pair of VEC-116 and
VEC-120, and evaluation codes (VEC-117) are not checked.

## Files

| File | Role |
| --- | --- |
| `check.mjs` | The stages above, and `COVERAGE.md` |
| `canonicalize.mjs` | Content to canonical bundle |
| `lib/jcs.mjs` | JCS encoder, strict JSON reader (duplicate names, bundle numbers), SHA-256 |
| `lib/load.mjs` | YAML 1.2 core-schema loader under SRC-1 to SRC-11 |
| `lib/schemas.mjs` | Loads `spec/schemas` into one ajv instance |
| `lib/spec-index.mjs` | Reads clause definitions from `spec/sections/` and codes from `spec/error-codes.md` |
| `test/jcs.test.mjs` | Encoder, reader and `canonicalize` tests. The round trip uses the non-normative example of section 10 |
| `test/check.test.mjs` | Runs `check` on the repository (expects exit 0 without `--strict`) |

## Enforcers

`spec/conformance/enforcers.yaml` maps a normative clause that no vector can observe to the enforcer that
tests it (VEC-125). An entry names a test that the implementation has not written yet, so it is a promise
to test that clause. `check` counts it as enforced, and `COVERAGE.md` prints its text, which says "Planned". Each value is one line.

## Open questions

- The `**` glob reads as whole directory segments (SRC-18 (b)), so a trailing `**` selects no file. In a source-list
  glob `?` and `[` are literal characters, because SRC-18 defines only `*` and `**`, and a path-mapped pattern
  rejects them (SRC-23). The sidecar label of BUNDLE-149 may be any dot-free label without upper-case letters.
- BUNDLE-149 names a sidecar beside a bundle `<stem>.provenance.json`. A provenance case keeps its sidecar
  under `spec/conformance/provenance/`, with a name such as `sidecar-bundle-version-mismatch.json`, so the
  check does not apply the BUNDLE-149 name rule to those fixtures. The spec should say so.
