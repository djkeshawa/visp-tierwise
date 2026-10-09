# Edge bundles for the manifest, source and namespace compile cases

Three canonical bundles in this directory are the compiled content of the small projects that the
compile cases of work item C1 use (`spec/conformance/compile/manifest-*`, `src-*`, `ns-*`, `find-*`,
`preset-*`, `presets-*`, and the trap case `src-yaml12-no-yes-on-off`). Each bundle was produced by
`js/spec-tools/canonicalize.mjs` from a content object written outside the repository. Nothing was
edited after canonicalization. Each file has no trailing newline ([BUNDLE-4]).

| Bundle | `bundleVersion` | Used by |
| --- | --- | --- |
| `edge-manifest.json` | `sha256:f8e757d72ef7aa3dd4c9ee142e0dd2547f03112568039a27ec1f3a11e91aa83d` | The base project and every control that compiles to it |
| `edge-manifest-namespaced.json` | `sha256:f0d741f7fac28d5f82cfd3258d9adf491e1b7c370c89d3a13b45882cda309d52` | Projects with the `pos` namespace (`ns-*`) |
| `edge-manifest-service.json` | `sha256:1d83632249dc94cf224fbe7e2f5b234ac65b8a3c3fea47fd6523d2f707f951e9` | `src-service-level-values-control` |

## The base project

Every case is a complete project. The base project has these files, and each case copies them and
changes one thing.

- `tierwise.yaml`: `specVersion: "0.1"`, `sources.schema` = `schema/core.yaml`, and two values entries:
  the root entry `{file: values/root.yaml, scope: global}` and the multi-node entry `{file: values/regions.yaml}`.
- `schema/core.yaml`: the hierarchy `levels: [global, region]`, with the closed catalog `region`
  of `nodes: ["1", no, off, on, yes]` and `on_unknown_node: error`. It declares one key,
  `checkout.allowed`, `type: boolean`, `combine: any`, `settable_at: [global, region]`.
- `values/root.yaml`: `checkout.allowed: true`.
- `values/regions.yaml`: `region: { no: { checkout.allowed: true }, yes: { checkout.allowed: true } }`.

The project extends no preset and sets no lint rule, so no lint rule runs ([LINT-5]). The catalog
lists `"1"`, `no`, `off`, `on` and `yes`. The node `"1"` is quoted in the source, so the source writes
a string ([SRC-1], [SRC-11]). The unquoted `no`, `off`, `on` and `yes` are strings under the core
schema, which the trap case `src-yaml12-no-yes-on-off` tests ([VEC-106]).

## Compilation decisions

| Decision | Clauses | Result in `edge-manifest.json` |
| --- | --- | --- |
| The hierarchy has the root `global` and one closed catalog `region`. `axes` is `[]`, and `scopes` has only `region`. | BUNDLE-29 to BUNDLE-32, SCOPE-10, SCOPE-14 | `"axes": []`, `"levels": ["global","region"]`, `"scopes"` with `region` only |
| `region` is file-sourced, so `values` is written as `"files"`. Its nodes are in code point order, and `onUnknownNode` is written because the catalog is closed. | BUNDLE-33, BUNDLE-35, BUNDLE-38, BUNDLE-39, BUNDLE-42, DET-6 | `"nodes": ["1","no","off","on","yes"]`, `"onUnknownNode": "error"`, `"values": "files"` |
| The key is commutative (`any`), so it has no `default`. Its `settableAt` lists levels in declared order. | BUNDLE-52, BUNDLE-54, BUNDLE-55, MERGE-1, MERGE-4 | `"combine": "any"`, `"settableAt": ["global","region"]`, no `default` |
| No axis and no source failure, so `combineAcrossAxes` and `onSourceFailure` are absent. | BUNDLE-57, BUNDLE-59 | Neither member is written |
| The root value is written under the level name `global` with the node key `""`. | BUNDLE-64, BUNDLE-66, BIND-2, SRC-26 | `"global": { "": { "checkout.allowed": true } }` |
| A node with a value is written. A catalog node with no value (`1`, `off`, `on`) is absent from `values`. | BUNDLE-22, BUNDLE-38 | `"no"` and `"yes"` are written. `"1"`, `"off"` and `"on"` are not |
| `entities`, `facts` and `features` are present and empty. | BUNDLE-19, BUNDLE-22 | `{}` each |
| No preset, namespace, lint or provenance data is written. | BUNDLE-109, BUNDLE-139, NS-6 | None of these appears |

`edge-manifest-namespaced.json` is the base bundle with the namespace `pos` and one more key,
`pos.flag` (`type: boolean`, `combine: any`, `settable_at: [global]`), with root value `true` in the root
values file. Its content is the base content plus those two additions, and the namespace is not written
([BUNDLE-109], [NS-6]).

`edge-manifest-service.json` adds the level `terminal` as a service-sourced level (`nodes: open`,
`values: service`). The key is also settable there, so it carries `onSourceFailure: "skip"`
([BUNDLE-59], [BUNDLE-61], [SVC-1]). The bundle has no `values` entry for `terminal` ([BUNDLE-67]).

## Verification

- Each file was written as a content object, canonicalized with `js/spec-tools/canonicalize.mjs`,
  which checks the content against `bundle.schema.json` first. All three passed.
- `pnpm --dir js run spec:check -- --no-write` reports no problem for these bundles or for the compile
  cases that use them. The run used `--no-write`, so `spec/conformance/COVERAGE.md` was not rewritten.
- Each compile case that expects a bundle has no finding of any severity, so VEC-94 and VEC-95 apply.
  The derivation of each expected finding is recorded in the case's `expected.yaml`.

## Open points

- The expected finding of a case that is not a bundle expectation depends on decisions that the spec
  leaves open. They are listed in the work item report. The main ones are which node a finding about a
  member is located at (SRC-17, SRC-21, SRC-23, MANIFEST-26), which declaration of a duplicate across
  two files is the second (SRC-40, SRC-43), and which entry of a file selected twice is the second
  (SRC-34).
