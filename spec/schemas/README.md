# spec/schemas

JSON Schemas (draft 2020-12) for the Tierwise source files, the bundle, the results
and the conformance suite (implementation plan item 1.7). Each schema has the `$id`
`https://tierwise.github.io/schemas/0.1/<file>`, and the schemas reference each other
by that `$id`.

The schemas check structure only. They are derived from the specification and are
not normative: when a schema and a clause differ, the clause governs. A schema
violation carries no error code. The compiler, the loader or the check tool reports
the code that the violated clause names. Semantic checks, such as cross-references,
uniqueness across files and catalog membership, stay in the compiler and the tools.

| Schema | Validates | Main clauses |
| --- | --- | --- |
| `manifest.schema.json` | `tierwise.yaml`, with inline content by `$ref` | MANIFEST, SRC-21, NS |
| `preset.schema.json` | A preset file | MANIFEST-19 to MANIFEST-36 |
| `schema-file.schema.json` | A schema file, and inline `schema` content | SRC-14, sections 2 to 7, LINT-14 |
| `values-file.schema.json` | A values file; `#/$defs/multiNodeFile` for inline `values` | SRC-21 to SRC-31 |
| `rules-file.schema.json` | A rules file, and one item of inline `rules` | POL, BIND, RULE |
| `bundle.schema.json` | A canonical bundle `{ bundleVersion, content }` | BUNDLE-1 to BUNDLE-108 |
| `provenance.schema.json` | A provenance sidecar | BUNDLE-139 to BUNDLE-150 |
| `results.schema.json` | A `Resolved` or `Decision` result | PROV |
| `vector-evaluate.schema.json` | An evaluate vector | VEC-21 to VEC-74 |
| `vector-canonical.schema.json` | A canonical vector | VEC-75 to VEC-89 |
| `vector-compile.schema.json` | The `expected.yaml` of a compile case | VEC-90 to VEC-107 |
| `vector-provenance.schema.json` | A provenance case | VEC-126 to VEC-131 |

A values file has no marker for its shape. The entries shape and the multi-node shape
are chosen by the manifest entry that selects the file ([SRC-21]), so
`values-file.schema.json` accepts either at its root.

The engine error codes listed in `vector-compile.schema.json` copy the engine group of
`spec/error-codes.md`, and the evaluation codes in `vector-evaluate.schema.json` copy
[EVAL-38]. A change to either list must update the schema in the same change.
