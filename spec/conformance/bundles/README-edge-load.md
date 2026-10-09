# Edge bundle: load

`edge-load.json` is a small valid bundle for the evaluate vectors in `evaluate/load/valid/`. It tests the loader at the top of the integer range, which no sample bundle reaches. The bundle has one level (the root `global`), no axes, no entities, no facts and no features. Its only content is two integer keys.

- `bundleVersion`: `sha256:731f3fb3860038a2c343c4d105efe8609ce2ab79e745ffbf41d2870db122b84e` ([BUNDLE-5], [BUNDLE-6]). The file has no trailing newline ([BUNDLE-4]).
- Built with `js/spec-tools/canonicalize.mjs` from a content object in the scratch directory, outside the repository. The tool checked the content against `bundle.schema.json` and wrote the bytes. The digest was then checked with an independent SHA-256 of the JCS text.

## Content

> Example (non-normative). The content object as the canonicalizer read it. The bundle file holds its canonical bytes.

```json
{
  "bundleFormatVersion": "1",
  "entities": {},
  "facts": {},
  "features": {},
  "hierarchy": { "axes": [], "levels": ["global"], "scopes": {} },
  "keys": {
    "example.max_safe": { "combine": "override", "default": 9007199254740991, "settableAt": ["global"], "type": { "type": "integer" } },
    "example.min_safe": { "combine": "override", "default": 0, "settableAt": ["global"], "type": { "type": "integer" } }
  },
  "specVersion": "0.1",
  "values": { "global": { "": { "example.min_safe": -9007199254740991 } } }
}
```

## Decisions

| Decision | Clauses | Result in the bundle |
| --- | --- | --- |
| The largest and smallest safe integers are used as values, so that the boundary is accepted | [BUNDLE-11], [BUNDLE-12], [DET-4] | `example.max_safe` has the default `9007199254740991` (2^53 - 1). `example.min_safe` has the root value `-9007199254740991` |
| The root value replaces the default of `example.min_safe`, so the value shows that the loader read `values` | [MERGE-19], [MERGE-7] | The default is `0`. A resolve returns the root value, not the default |
| A key with only a default has no values, so its resolve reports `DEFAULT` | [PROV-48], [MERGE-7] | `example.max_safe` has no `values` entry |
| The root value is written under the level name `global` with node key `""` | [BUNDLE-64], [BUNDLE-66] | `values.global[""]` |
| The integer keys carry no kind, source failure or axis member, because no service-sourced level is present | [BUNDLE-59], [BUNDLE-57] | Neither key has `onSourceFailure` or `combineAcrossAxes` |

## Vectors that use this bundle

| Vector | Key | Expected |
| --- | --- | --- |
| `load/valid/edge-max-safe-default` | `example.max_safe` | `9007199254740991`, source `DEFAULT` |
| `load/valid/edge-min-safe-root-value` | `example.min_safe` | `-9007199254740991`, source the root level `global` |

## Not in this bundle

- A value of `-0` or of 2^53 is not written here. Both are outside the bundle form ([BUNDLE-11], [DET-4]), and the invalid files cover 2^53 ([VEC-80]).
- No non-NFC string is written. A bundle with one is not compiler output ([NORM-4]), so this bundle does not test that the loader leaves strings as received ([BUNDLE-138]). That clause is already cited by a canonical vector.

## Open points

- None for this bundle.
