# Implementation plan

This plan turns [the roadmap](roadmap.md) into work items. Phases 0–4 are planned in
detail, because they establish the spec and the vertical slice. Phases 5–9 are
planned at work-package level and will be refined at the Phase 4 review.

Sizes are relative: **S** ≈ a day, **M** ≈ a few days, **L** ≈ a week or more, for
one focused contributor.

---

## Phase 0 close-out: toolchain and CI

**Status:** items 0.1–0.5 are done locally (2026-10-08). Item 0.6 needs the owner (O2).

| # | Work item | Size | Output |
| --- | --- | --- | --- |
| 0.1 | Add the Maven Wrapper (pinned Maven 3.9.x, `distributionSha256Sum`) | S | `mvnw`, `.mvn/wrapper/` |
| 0.2 | Parent POM `pluginManagement`: compiler (`release` 17/21 per module), surefire, jar, enforcer (Java ≥17, Maven ≥3.9, dependency convergence, banned dependencies in core) | S | `java/pom.xml` |
| 0.3 | Pin `typescript` as a root dev dependency; build with two `tsconfig` targets (ESM, CJS) plus a 10-line post-step that writes `dist/cjs/package.json` | S | `js/package.json`, `js/*/tsconfig.*.json` |
| 0.4 | Delete `scripts/build-java.mjs` and `scripts/build-js.mjs`; update `check-foundation.py` | S | |
| 0.5 | CI: Java on JDK 21 running `./mvnw -B verify` (`--release 17` enforces the Java 17 API, so a JDK 17 runtime test job is added in Phase 2, once there are tests); JS installs, checks and builds with pnpm on Node 22 and runs the packaging tests on Node 22 and 20; foundation check | S | `.github/workflows/ci.yml` |
| 0.6 | Push to the `tierwise` GitHub organisation (after O2), get hosted CI green, enable private vulnerability reporting | S | Owner action plus verification |

**Exit:** a clean clone builds with `./mvnw verify` and `pnpm -r build`, and hosted CI
is green.

---

## Phase 1: Spec v0.1

The spec is written before any implementation. Bundles and vectors are written by
hand and checked by small, dependency-light **spec tools**, so the Java compiler in
Phase 3 has an independent oracle to match.

### 1A. Normative text

| # | Work item | Size |
| --- | --- | --- |
| 1.1 | `spec/spec.md` skeleton with stable **clause IDs** (e.g. `[MERGE-AXIS-3]`). Every MUST gets an ID, which vectors cite. | S |
| 1.2 | Write the clauses from requirements §4, folding in ADRs 0005 and 0007–0011: hierarchy and admission; entities and facts; types; strategies and axes; providers; features, policies and bindings; rule evaluation; provenance; determinism; evaluation errors | L |
| 1.3 | Bundle-format section: the exact `content` layout (below), the envelope, integrity checks, provenance sidecar | M |
| 1.4 | Conformance-vector format section: evaluate, canonical and compile vectors; provider stubs; subset versus exact matching | M |
| 1.5 | OpenFeature mapping appendix (draft; finalised in Phase 8) | S |
| 1.6 | `spec/error-codes.md`: per code, the meaning, trigger, minimal example, fix, and the clause ID. This is the source for `tierwise explain`. | M |

**Bundle `content` layout (proposal to finalise in 1.3).** Everything is keyed by
name, so JCS ordering is the canonical order. Ordered semantics, such as levels,
axes, rules and universes, stay as arrays.

```json
{
  "bundleFormatVersion": "1",
  "specVersion": "0.1",
  "hierarchy": {
    "levels": ["global", "channel", "..."],
    "axes": ["seller"],
    "scopes": { "channel": { "nodes": ["partner_portal", "web"], "onUnknownNode": "error", "values": "files" },
                "account": { "nodes": "open", "values": "service" } }
  },
  "entities": { "order": { "belongsTo": ["customer"], "hasMany": ["item"] } },
  "facts": { "item.stock_type": { "entity": "item", "type": { "type": "enum", "values": ["..."] } } },
  "keys": { "checkout.pickup.lead_hours": { "type": {"type": "integer", "min": 0, "max": 168},
            "combine": "override", "default": 4, "settableAt": ["..."],
            "combineAcrossAxes": "max", "kind": "configuration" } },
  "values": { "global": { "": { "key": "value" } }, "division": { "south": { "key": "value" } } },
  "features": { "checkout.order_submission": { "decidedPer": "order", "returns": {"enum": ["ALLOW", "DENY"]},
                "hitPolicy": "FIRST", "ifFactMissing": "DENY", "bindingSettableAt": ["..."],
                "policies": { "credit_check": { "settings": {}, "default": "DENY",
                              "requiredFacts": ["customer.active", "..."],
                              "rules": [ { "id": "good-credit", "name": "…",
                                           "when": [ { "fact": "…", "op": "eq", "value": true } ], "then": "ALLOW" } ] } },
                "bindings": { "global": { "": { "policy": "credit_check", "settings": {} } } } } }
}
```

- The root's single node uses the key `""`. This is internal to the bundle and never
  appears in the API.
- `requiredFacts` (sorted) is precomputed, so both evaluators run an identical
  precheck.
- Condition lists are pre-sorted by fact, and `$setting` references stay symbolic,
  to be resolved against the selected binding at evaluation time.
- Kinds, naming patterns, lint settings, descriptions and examples are
  **compile-time only** and not in `content`. The kind *name* stays on each key for
  provenance.

### 1B. Schemas, presets and samples

| # | Work item | Size |
| --- | --- | --- |
| 1.7 | JSON Schemas (2020-12) for: manifest, schema file, values file, rules file, preset, bundle, provenance sidecar, evaluate/canonical/compile vectors | M |
| 1.8 | `presets/recommended.yaml`, `minimal.yaml`, `entitlements.yaml` | S |
| 1.9 | `samples/commerce/`: a complete project (manifest, schema, catalogs, values for every scope type, rules for two features, examples) | M |
| 1.10 | `samples/retail-pos/`: a complete project with a basket feature returning `APPROVE/REVIEW/REJECT`, a service-sourced terminal, and an axis `brand` with `combine_across_axes` | M |
| 1.11 | `samples/saas-entitlements/`: a docs sample using `limit`/`feature`/`grant` kinds and `union` grants | S |

### 1C. Conformance

| # | Work item | Size |
| --- | --- | --- |
| 1.12 | Spec tools in `js/spec-tools/` (Node with the dev dependencies `yaml` and `ajv`): `canonicalize` (content → canonical bundle and digest), `check` (schema-validate everything, verify bundle digests, check that every clause ID is cited by ≥1 vector, check that every error code has positive and negative compile vectors) | M |
| 1.13 | Bundles: `commerce.json`, `retail-pos.json`, plus small focused bundles (`edge-axes`, `edge-deep-merge`, `edge-admission`, `edge-providers`) written as content JSON and canonicalised by the tool | M |
| 1.14 | **Canonical vectors**: JCS byte cases (key ordering with non-ASCII keys, escapes, integers at ±(2^53−1)) and SHA-256 cases | S |
| 1.15 | **Evaluate vectors**: every strategy × set/unset/identity, axes × each combiner, defaults, `deep_merge` recursion, provider outcomes (stubbed in the vector: values, omit, unknown key, invalid entry, throw) × `skip`/`deny`, provider call counts, admission (`error`/`ignore`/open/generated, multiple skipped scopes, whole-context validation before calls), every operator, empty and omitted collections, missing-fact precheck with reordered rules, bindings and settings, nullable and object effects, traces, and each evaluation error code | L |
| 1.16 | **Compile vectors**: one directory per case, each with `expected.yaml` listing the codes, files, YAML paths and lines; samples compile to the exact bytes in `bundles/`; the YAML 1.2 `no` trap; provenance independence | L |
| 1.17 | Genericity check (`scripts/check-genericity.mjs`): collect names from the sample schemas and scan `java/**/src/main` and `js/**/src`, with an explicit allow-list | S |
| 1.18 | `spec/conformance/COVERAGE.md`, generated by `check`: clause → vectors | S |

**Exit:** the Phase 1 review accepts the spec. `pnpm --dir js run spec:check` passes. Every clause
and every code is covered.

---

## Phase 2: Java validation

`tierwise-core` package layout. Only `io.github.tierwise.core` is exported
(JPMS `module-info`); everything else is internal.

```
io.github.tierwise.core              public API: Tierwise, Context, Key, Feature, FactSet,
                                     Resolved, Decision, Evaluator, EvaluatorOptions,
                                     LevelValueProvider, EvaluationListener,
                                     ValidationReport, Finding, TierwiseException
io.github.tierwise.core.internal
  ├── source     YAML 1.2 loading with positions; manifest; source mapping; presets
  ├── model      immutable records: Hierarchy, Scope, Entity, Fact, TypeDecl, KeyDef,
  │              Kind, FeatureDef, Policy, Rule, Condition, Binding, Example
  ├── check      engine checks, one class per area, mirroring requirements §6.1
  ├── lint       lint rules and severity resolution
  ├── compile    model → content tree; canonical JSON; SHA-256          (Phase 3)
  ├── bundle     strict reader; integrity; version support             (Phase 3)
  └── eval       context validation; strategies; resolver; decisions   (Phase 3)
```

| # | Work item | Size |
| --- | --- | --- |
| 2.1 | Source layer: snakeyaml-engine node API → a positioned tree (file, YAML path, line); reject duplicate keys, aliases and merge keys; manifest parsing and source mapping | M |
| 2.2 | Preset resolution: built-in (from the classpath, copied from `presets/` at build time), relative paths, and URLs pinned by SHA-256 (fetching sits behind an interface, and only the CLI wires up the network) | M |
| 2.3 | Model building and the type grammar | M |
| 2.4 | Engine checks, area by area, each with positive and negative tests and its compile vectors | L |
| 2.5 | Lint framework and the §6.3 rules | M |
| 2.6 | Conformance runner (JUnit dynamic tests) reading `spec/conformance/compile` directly, plus golden output | S |
| 2.7 | "Did you mean" suggestions (Damerau–Levenshtein over declared names) | S |

---

## Phase 3: Java evaluation

| # | Work item | Size |
| --- | --- | --- |
| 3.1 | Compiler: model → `content`; canonical JSON writer; digest; byte-identical to the Phase 1 bundles | M |
| 3.2 | Strict bundle reader and integrity check, using jackson-core streaming with duplicate detection | S |
| 3.3 | Context validation and admission (ADR 0007) | S |
| 3.4 | Strategies as one small, pure function each, plus axis combination (ADR 0008) | M |
| 3.5 | Resolver with providers, timeouts and listener events (ADR 0009) | M |
| 3.6 | Decision engine: binding selection, settings, precheck, `FIRST`, trace | M |
| 3.7 | Example runner inside validation (`E_EXAMPLE_FAILED`, ADR 0013); `tierwise test` reports per-example results | S |
| 3.8 | Evaluate and canonical conformance runners; jqwik properties (§12) | M |
| 3.9 | JMH benchmark on a generated bundle of about 1,000 keys and 50 features; report the numbers | S |

---

## Phase 4: JS evaluator

```
js/evaluator/src/
  index.ts        public exports only
  errors.ts       TierwiseError + codes
  json.ts         strict parse (duplicate scan) + JCS
  sha256.ts       self-contained SHA-256
  bundle.ts       loadBundle: integrity, versions, deep-freeze
  context.ts      validation and admission
  strategies.ts   one pure function per strategy + axis combination
  resolve.ts      resolve / resolveSync, providers, listener
  decide.ts       decide / decideSync
  types.ts        public result types
js/evaluator/test/
  conformance.test.ts   reads ../../../spec/conformance (node:test + yaml)
  sha256.test.ts        differential against node:crypto
  properties.test.ts    fast-check (§12)
```

| # | Work item | Size |
| --- | --- | --- |
| 4.1 | JSON, JCS and SHA-256 primitives, passing the canonical vectors | S |
| 4.2 | Loader, context, strategies, resolve, decide, passing the evaluate vectors | L |
| 4.3 | ESM, CJS and types packaging; a browser smoke test (a bundled page that runs a decision) | S |
| 4.4 | **Differential harness**: a jqwik generator in Java writes random (bundle, call, input, Java result) cases as JSONL; the JS runner replays them and compares results; a nightly CI job | M |
| 4.5 | Benchmark harness and the numbers | S |

**Exit (the vertical-slice milestone):** sources → Java compile → identical answers in
Java and JS for every vector and for generated cases.

---

## Phases 5–9: work packages

| Phase | Work packages |
| --- | --- |
| 5 Toolchain | picocli commands; `init` templates per preset; human and JSON output (`--format json` for CI annotations); `explain` generated from `error-codes.md`; Java and TS codegen with golden tests; GraalVM native builds (reflection-free core makes this easier); Docker image; GitHub Action wrapping the native binary |
| 6 Runtimes | Starter: bundle sources (classpath, HTTP, server, S3 via an optional module), refresher with ETag and atomic swap, last-known-good, staleness, provider timeouts, Micrometer binder. `@tierwise/node`: the same, on `fetch`. Both tested against a shared stub server. |
| 7 Server | Storage SPI with filesystem and S3; publish with digest verification, example re-run and conflict rejection; publication records; promote; resolve/decide/schema; OIDC/JWT; OpenAPI; E2E test across Java and Node |
| 8 OpenFeature | Java and JS providers; type mapping; `tierwise.facts`; metadata; finalised spec appendix |
| 9 Release | MkDocs site; quickstarts verified on clean machines; Javadoc and TypeDoc; reproducible-build check; Sigstore signing; CycloneDX; Maven Central and npm publish workflows (dry run first); registry re-check for the name |

---

## Working agreements

- **One phase per branch.** Commits are small, signed off (DCO), and reference
  work-item numbers.
- **Test with the code.** A work item isn't done until its vectors or tests pass.
- **Spec first.** If implementing reveals a spec gap, write an ADR or RFC and update
  the spec and vectors before the code.
- **Accurate reporting.** Each review summary states what actually ran and what
  didn't.
