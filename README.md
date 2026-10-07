# Strata

Strata is a planned open-source library for typed configuration across declared levels and entity-aware decisions, with Java and JavaScript evaluators following one language-neutral specification.

**Current status: Phase 0 foundations, awaiting review.** This repository contains the project structure, build scaffolding and contribution policies. It has no implemented compiler, evaluator, CLI, runtime integrations or server. The normative specification and conformance fixtures begin in Phase 1, after the Phase 0 review.

`Strata`, the Maven group `org.strata`, and the npm scope `@strata` are provisional, unclaimed identities. They must be checked and approved before publishing. There is no configured public repository or published package.

The supplied prompt review recommendations have been applied to the
[amended build brief](docs/build-brief.md) and commerce schema excerpts. The
[coverage record](docs/recommendations-review.md) separates adopted clarifications
from remaining format and type-schema decisions. A completed
[Claude Opus 5.5 xhigh consultation](docs/claude-consultation.md) resolved runtime
unknown-node skipping and separate bundle provenance. These decisions are in the
brief; the evaluator and Phase 1 specification have not been implemented or accepted.

## The problem

Applications often combine values from several levels. Without a shared schema and explicit merge rules, different readers can produce different answers. Misspelled names can remove restrictions, collection rules can leave “all” versus “any” unclear, and callers cannot explain which sources contributed to a result.

Strata's intended answer is a declared model, validation before deployment, immutable compiled bundles, and provenance with every resolved value and decision. The project must accept user-defined hierarchies, entities, facts, keys and effects. Modeling guidance belongs in optional presets; engine guarantees belong in the specification.

## When to use Strata

| If you need… | Use |
| --- | --- |
| Release toggles, experiments, kill switches | A feature-flag tool. Strata is intended to offer an OpenFeature provider. |
| Authorization: who may do what to which resource | Cedar or OPA |
| A general-purpose configuration language | CUE, Pkl or Jsonnet |
| Typed configuration across levels you define, with a merge rule per key; entity-aware decision tables with explicit all/any; provenance for every answer; Java and JavaScript | **Strata**, once implemented and released |

## Planned architecture

The configuration repository is the source of truth. The Java toolchain will validate sources and compile a canonical, content-hashed bundle. Both evaluators will use that bundle in process and run the same conformance vectors. Runtime loaders will refresh bundles atomically and retain the last-known-good version when refresh fails.

**Browser evaluation is for display only.** Client-side results can be tampered with. Enforce decisions on the server.

| Path | Planned responsibility | Current status |
| --- | --- | --- |
| `spec/` | Normative semantics, JSON Schemas, stable diagnostics and shared conformance | Reserved for Phase 1 |
| `java/` | Reference toolchain, evaluator, CLI and Java integrations | Empty package placeholders compiled locally |
| `js/` | TypeScript evaluator, Node loader and OpenFeature adapter | Empty exports compiled locally to ESM, CJS and types |
| `presets/` | Optional modeling recommendations | Reserved for Phase 1 |
| `samples/` | Two conformance domains and an entitlement documentation example | Reserved for later phases |
| `docs/` | Documentation source and architecture decision records | Phase 0 documentation |

## Java: planned five-minute quickstart

**Unavailable in Phase 0.** No Java library or CLI is published, and the proposed Java API has not been reviewed. Do not add the provisional coordinates to an application.

The release quickstart will show how to validate and compile a sample project, load its bundle, resolve a typed key, and decide a feature with provenance. Core, CLI and OpenFeature packages will target Java 17+; the Spring Boot starter and server will target Java 21. A runnable, verified walkthrough is required before the first release.

## JavaScript: planned five-minute quickstart

**Unavailable in Phase 0.** There is no installable `@strata/evaluator`, compiler or supported JavaScript API.

The release quickstart will show how to compile with the Java CLI, load the resulting bundle in Node or a browser, and call the evaluator with typed handles. The evaluator will have zero runtime dependencies, support ESM and CommonJS with types, target Node 20+, and support browsers implementing ES2020. Browser use will demonstrate display behavior only.

## Development and review

Read [CONTRIBUTING.md](CONTRIBUTING.md) before contributing and [GOVERNANCE.md](GOVERNANCE.md) for the decision process. Changes require DCO sign-off. The [phase-boundary ADR](docs/adr/0001-phase-boundaries.md) records why this foundation stops before specification work.

CI is configured for Java placeholders and JavaScript workspace checks, but remote CI has not run. Run the structural checks from the project root:

```sh
python3 scripts/check-foundation.py
pnpm --dir js run check
```

The foundation build adapters use installed compilers:

```sh
node scripts/build-java.mjs
pnpm --dir js run build
```

The Java command requires a complete Java 21+ installation with release signatures. The JavaScript command requires an existing TypeScript compiler, resolved normally or selected with `STRATA_TYPESCRIPT_PATH`; no TypeScript dependency has been added.

Local placeholder compilation passed using the Java adapter's explicit `--foundation-source-target` fallback and an existing TypeScript 6.0.3 installation. TypeScript emitted ESM, CJS and declaration files. The local Java runtime lacks `ct.sym`, so strict `--release` compilation and Java 17 API compatibility remain unverified. These are empty targets, with no functional runtime behavior to test.

The package-manager pin is recorded in `js/package.json`. The local offline frozen pnpm install passed with an explicit writable store and no dependencies to install. Maven validation/verification, reproducible clean-machine builds with approved compiler tooling, and docs generation remain unverified or pending approval. See the [Phase 0 review record](docs/phase-0-review.md) for the exact evidence and limitations.

Phase 0 remains open until its checks pass and its unanswered questions receive review. Maintainers, a GitHub home, final package identities, and private reporting channels still need assignment. Docs site tooling and release infrastructure are provisional until reviewed. Later phases must each stop for review.

Packages, the specification (`specVersion`) and the bundle format (`bundleFormatVersion`) will be versioned independently. Package releases follow SemVer. A breaking specification change requires an RFC and a major specification version. There is no supported specification or bundle format version yet.

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE](NOTICE).
