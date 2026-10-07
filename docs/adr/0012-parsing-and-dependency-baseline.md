# ADR 0012: Parsing, canonical JSON and the dependency baseline

- Status: **proposed, awaiting owner approval of the dependencies**. The technical
  choices are delegated; adding dependencies needs the owner's approval
  (see `AGENTS.md`).
- Date: 2026-10-08
- Supersedes: the dependency column of the component table in the original brief; the
  interim build adapters of [ADR 0002](0002-foundation-tooling.md) once approved

## Context

The original brief planned Jackson for both YAML and JSON in `tierwise-core`.
Jackson's YAML module is built on SnakeYAML, which implements **YAML 1.1**. Under
YAML 1.1, `no`, `off`, `yes` and `on` parse as booleans. A node ID `no` (Norway)
would become `false` before any validator saw it. That is a silent interpretation
in the parser, which is the class of bug Tierwise exists to prevent.

Phase 0 also used local build adapters because Maven and a pinned TypeScript
compiler weren't approved. The implementation phases need a real, reproducible
toolchain.

## Decisions

### 1. YAML: YAML 1.2 core schema only, with positions

- Java parses sources with **SnakeYAML Engine**, which implements YAML 1.2, using
  its node API so that every finding has a file, YAML path and line.
- Duplicate mapping keys are rejected (`E_DUPLICATE_KEY`).
- Aliases and merge keys (`<<`) are rejected in v0.1 (`E_YAML_FEATURE`). They make
  provenance by line ambiguous, and the merge key is a 1.1 extension.
- Only the core-schema scalar types are recognised. Anything else is a string, and
  type validation then decides whether it is legal.

### 2. JSON and JCS: small in-house code, no float path

- The bundle contains **no non-integer numbers**. Decimals are strings
  (ADR 0011), and integers are within ±(2^53−1). JCS (RFC 8785) number formatting
  therefore reduces to integer formatting. Canonical JSON is a few dozen lines in
  each language, and the shared conformance vectors check it byte for byte.
- JSON parsing: Java uses **jackson-core** (streaming only, with
  `STRICT_DUPLICATE_DETECTION`). JS uses `JSON.parse` with a pre-scan to reject
  duplicate properties, because `JSON.parse` silently keeps the last one.
- JCS sorts properties by UTF-16 code units. That is JS's default string order and
  Java's `String.compareTo`, so both sides sort correctly without extra code.

### 3. SHA-256 in the browser without async

The JS evaluator must have zero runtime dependencies and keep `loadBundle`
synchronous. Web Crypto's digest is async-only, so the evaluator includes a small,
self-contained SHA-256 (about 100 lines). Tests check it against `node:crypto` over
generated inputs. Java uses `java.security.MessageDigest`.

### 4. No logging dependency in core

`tierwise-core` reports runtime events (provider failures, unknown provider keys,
bundle loads) through an `EvaluationListener` interface instead of SLF4J. The Spring
Boot starter turns those events into Micrometer metrics and logs. This drops SLF4J
from the core dependency list.

### 5. Dependency baseline for approval

Pin exact versions at approval time to the latest stable release that is at least
two weeks old. Dependabot proposes updates after that.

| Scope | Package | Purpose | Phase |
| --- | --- | --- | --- |
| Build | Maven 3.9.x via the **Maven Wrapper** (checksum-verified) | Reproducible Java builds; replaces `scripts/build-java.mjs` | 0 |
| Build | maven-compiler, surefire, jar, enforcer plugins | Lifecycle, `--release 17/21`, reproducible JARs | 0 |
| Build (JS) | `typescript` (dev) | Compiler; replaces `TIERWISE_TYPESCRIPT_PATH` | 0 |
| Core runtime | `org.snakeyaml:snakeyaml-engine` | YAML 1.2 sources (compiler/validator only) | 2 |
| Core runtime | `com.fasterxml.jackson.core:jackson-core` | Strict streaming JSON for bundles | 2 |
| Java test | JUnit Jupiter, jqwik | Unit and property tests | 2 |
| Spec tooling / JS test | `yaml` (dev) | Read YAML samples and vectors (YAML 1.2): spec tooling, genericity check, JS conformance runner | 1 |
| Spec tooling | `ajv` (dev) | Validate presets, samples and vectors against the JSON Schemas | 1 |
| JS test | `fast-check` (dev) | Property tests | 4 |
| JS test | Node's built-in `node:test` | Test runner, so no test-framework dependency | 4 |
| CLI | `info.picocli:picocli` | Command-line parsing | 5 |
| Later | Spring Boot 3.x, Micrometer, OpenFeature SDKs, GraalVM native-build-tools, CycloneDX plugins, Sigstore tooling, MkDocs Material, TypeDoc | Their own phases; separate approval when the phase starts | 5–9 |

`@tierwise/evaluator` keeps **zero** runtime dependencies. `tierwise-core` has two
(snakeyaml-engine and jackson-core). Evaluation code doesn't touch YAML, and a
later split into `tierwise-evaluator` and `tierwise-compiler` modules stays
possible if consumers ask for a smaller evaluation-only JAR.

## Consequences

- YAML 1.1 coercions can't reach the validator.
- Canonical JSON and hashing are fully specified by vectors, with no dependency on
  library float formatting.
- Once the Maven Wrapper and TypeScript are approved and in place, the Phase 0
  adapters (`scripts/build-java.mjs`, `scripts/build-js.mjs`) are deleted.
