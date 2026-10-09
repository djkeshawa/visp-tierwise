# Changelog

This project uses the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) structure. Package releases follow Semantic Versioning. The specification and the bundle format are versioned separately.

Version 0.1.0 has not been released. It targets specification version **0.1** and bundle format version **1**. Every package is private or unpublished, and the release steps are listed in the [release checklist](docs/release-0.1.0-checklist.md).

## [Unreleased]

These changes go into 0.1.0, which has not been released.

### Added

- `tierwise explain naming/<name> [--project DIR]` prints the pattern's declared description, or the family explanation followed by the pattern (FIND-30, ADR 0026). This uses the new `Tierwise.namingPatterns` and `NamingPattern` in tierwise-core.
- `E_UNKNOWN_KIND` suggestions are now in the FIND-27 table (declared kinds).
- Tests for every enforcer entry that used to say "Planned". Each entry in `spec/conformance/enforcers*.yaml` now names the Java and JS tests that check it.
- Compile cases `manifest-specversion-only` and `manifest-specversion-only-preset` (MANIFEST-18).
- S3 integration tests against a real MinIO (`scripts/s3-it.sh`, CI job `s3-integration`, ADR 0027).
- CI jobs that run both quickstarts literally, and the GitHub Action with its docker and jar methods.

### Changed

- A provider timeout must be at most 2^53−1 ms in every evaluator (SVC-41, EVAL-28, ADR 0025). The Java evaluator used to accept larger ones.
- The S3 store writes the `latest` pointer with a trailing newline, like the filesystem store (ADR 0024).

### Fixed

- JS evaluator: a provider that blocked past its timeout and then returned had its result used. Such a result is now a failure (SVC-40, SVC-41).
- Server: publication records added within the same millisecond could be listed out of order.

## [0.1.0] - unreleased

### Added

#### Specification (`spec/`)

- `spec/spec.md` and `spec/sections/01`–`13`: the normative specification, with 1,106 clauses that each have a stable ID.
- `spec/error-codes.md`: 59 stable codes (51 engine, 7 evaluation and 1 tooling; 4 engine codes are also raised by evaluators) and 9 lint rules. Each code has a meaning, triggers, an example and a fix.
- 12 JSON Schemas (2020-12) for the manifest, schema, values and rules files, presets, bundles, provenance sidecars and the three vector formats.
- `spec/data/tz-names.txt`: the pinned IANA time-zone names (release 2026e, 597 names).
- The conformance suite under `spec/conformance/`: 576 evaluate vectors, 32 canonical vectors, 580 compile cases, 15 provenance cases, 28 valid bundles and 13 deliberately invalid bundles, with a generated `COVERAGE.md`.

#### Presets and samples

- Built-in presets `tierwise:recommended`, `tierwise:minimal` and `tierwise:entitlements`.
- `samples/commerce` (domain A): a layered project with two features, service-sourced levels, an axis and a generated catalog.
- `samples/retail-pos` (domain B): a project with custom kinds and no preset, a basket feature and a service-sourced level.
- `samples/saas-entitlements`: a single-file project that uses `tierwise:entitlements`.

#### Java (`java/`, Maven reactor)

- `tierwise-core` (Java 17): YAML 1.2 loading with positions, manifest and preset composition, the model, every engine check, the nine lint rules, compilation to canonical bundles, a strict bundle reader, context validation and admission, the merge strategies and axes, the resolver with providers and timeouts, the decision engine with traces and provenance, and `EvaluationListener`.
- `tierwise-cli` (Java 17): the `tierwise` command with `init`, `validate`, `lint`, `test`, `compile` (with `--provenance`), `explain` and `codegen --lang java|ts`. Output is text or JSON. Exit codes are 0, 1 and 2. The fat JAR is built; native binaries are not yet built.
- `tierwise-spring-boot-starter` (Java 21, Spring Boot 4.1.1): bundle sources (classpath, file, HTTP), ETag refresh with an atomic swap, last-known-good, startup snapshots, `Resolver` and `DecisionEngine` beans, provider wiring with timeouts, and Micrometer meters.
- `tierwise-spring-boot-starter-s3` (Java 21): an optional S3 bundle source.
- `tierwise-server` (Java 21): the control plane. It publishes bundles with digest verification and conflict rejection, serves the latest bundle with an ETag and the history, promotes stored versions, answers `/v1/resolve` and `/v1/decide` with `tierwise-core`, serves the schema and the OpenAPI document, and authenticates with OIDC or JWT.
- `tierwise-server-s3` (Java 21): an optional S3 bundle store.
- `tierwise-openfeature` (Java 17): an OpenFeature provider over the core evaluator.
- `tierwise-e2e` (test only): the cross-language end-to-end test. It compiles the commerce sample and a modified copy, publishes them to a running `tierwise-server`, has a Java starter and a Node service pick up each version, promotes and rolls back, and compares 25 cases with the server at each step.

#### JavaScript (`js/`, pnpm workspace)

- `@tierwise/evaluator` (Node 20 or later, and browsers): `loadBundle` (synchronous, verifying the digest and versions), `createEvaluator`, `resolve` and `decide` with their synchronous variants, providers with required timeouts, listener events, and a built-in SHA-256 and canonical JSON. It has no runtime dependencies. It ships ESM, CommonJS and type declarations.
- `@tierwise/node`: a bundle loader for servers that refreshes with `fetch` and an ETag, swaps atomically, keeps the last good bundle, and reports status and staleness.
- `@tierwise/openfeature`: a server provider and a web provider for OpenFeature. The SDKs are peer dependencies.
- `@tierwise/spec-tools` (private): checks the spec suite, and canonicalises content into a bundle.

#### Build, CI and release

- Maven Wrapper 3.9.16 with checksum verification, pinned plugins, enforcer rules (plugin pinning, dependency convergence, banned core dependencies) and reproducible JARs.
- TypeScript 7.0.2 and pnpm 11.3.0 for the JavaScript packages.
- `ci.yml`: the foundation check; the Java reactor with the conformance suite on JDK 21; core and CLI tests on a JDK 17 runtime; the JavaScript genericity check, workspace check, build and tests on Node 22; and the packaging tests on Node 20.
- `nightly.yml`: a differential run of 10,000 generated cases between Java and JavaScript, and the in-process benchmarks.
- `pnpm --dir js run sbom`: CycloneDX SBOMs for the npm packages.
- `release-dry-run.yml`: builds the JARs and CycloneDX SBOMs, native binaries for Linux, macOS and Windows, and a Docker image, and runs the CLI on the samples. It has no secrets and publishes nothing.
- `action.yml`: a composite GitHub Action that runs the CLI from a Docker image or a JAR.
- `docker/Dockerfile` and `docker/server.Dockerfile`: multi-stage images for the CLI and for `tierwise-server` on a JRE 21 base, pinned by digest, running as a non-root user.
- `release.yml`: a manually started release workflow. It defaults to a dry run that builds and tests every artifact and publishes nothing. Its three publishing jobs (Maven Central, npm, a draft GitHub release) run only when the dry run is turned off, in a protected environment, with secrets that do not exist yet. It has not run.
- Scripts for the release and for checks: `scripts/differential.sh`, `scripts/e2e.sh`, `scripts/set-version.sh`, `scripts/reproducibility-check.sh` and `scripts/quickstart-check.sh`.
- A `release` Maven profile with Javadoc and source JARs, GPG and Sigstore signing, a CycloneDX SBOM and Central publishing with `autoPublish` set to false. It has not been run.
- A genericity check (`js/scripts/check-genericity.mjs`) that fails when a sample domain name appears in production code.

#### Documentation

- Vision, requirements (with the normative draft in §4 and the acceptance criteria in §15), roadmap, implementation plan, open questions, benchmarks and the OpenFeature mapping.
- A [Java quickstart](docs/quickstart-java.md), a [JavaScript quickstart](docs/quickstart-js.md), a [CLI reference](docs/guides/cli-reference.md) and a [modeling guide](docs/guides/modeling.md).
- Decision records ADR 0001 to 0022.
- Governance, contributing guide, code of conduct, issue and pull request templates, and the security policy.
- The [release checklist](docs/release-0.1.0-checklist.md), which records every acceptance criterion.

### Changed

- Renamed the project from Strata to Tierwise (`@tierwise` npm scope, `io.github.tierwise` Maven group) to avoid a collision with OpenGamma Strata (ADR 0006).
- Resolved the open semantic questions in ADRs 0007 to 0011 and the spec open points in ADRs 0013 to 0017.
- Adopted the prompt review recommendations (ADR 0004). The bundle is now a canonical artifact that is hashed on its content, and provenance is kept outside it (ADR 0005).
- Set the dependency baseline (ADR 0012) and the Spring Boot, AWS SDK and OpenFeature versions (ADR 0020).

### Removed

- The Phase 0 installed-compiler build adapters (`scripts/build-java.mjs`, `scripts/build-js.mjs`), replaced by the Maven Wrapper and the pinned TypeScript compiler.

### Security

- Every evaluator verifies a bundle's digest and versions when it loads the bundle. A mismatch is `E_BUNDLE_INTEGRITY`, and an unsupported version is `E_BUNDLE_UNSUPPORTED`.
- Publishing and promotion on the server are limited to configured roles.
- Browser evaluation is for display only. Decisions must be enforced on the server.
- Private vulnerability reporting is not configured yet (see [SECURITY.md](SECURITY.md)).

### Not in this release

- Hosted CI and the nightly differential run have not run, so no hosted result is claimed.
- Native binaries and the Docker image have not been built or run.
- The Java and JavaScript quickstarts have not been verified on a clean machine.
- The release workflow, its dry-run mode and the release scripts have not run on a hosted runner. `docs/release-process.md` is not written yet.
- Publication to Maven Central and npm, and release signing, need owner approval and the owner's open items (organisation, scope, maintainers and security contacts).
