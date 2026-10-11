# Release 0.1.0 checklist

- **Date:** 2026-10-10 (replaces the review of 2026-10-09)
- **Scope:** the acceptance criteria of [requirements §15](requirements.md#15-acceptance-criteria-for-010), with the Phase 9 items of the [roadmap](roadmap.md).
- **Result:** the engineering work is done. Of the 14 criteria, 7 are met, 5 are met with caveats and 2 are not yet met: the nightly differential has not run for a night yet, and nothing is signed or published, because that needs the owner's accounts and keys. [Section 4](#4-what-is-left) lists what remains, and every item there is an owner action or a scheduled run.
- **Method:** the evidence comes from hosted GitHub Actions runs on `djkeshawa/visp-tierwise` at commit `90af984`, and from local runs of the same commands. The run IDs are in [section 3](#3-evidence). A criterion is **met** only when a run or a cited file shows it.

## 1. Summary

| # | Criterion (§15) | Status |
| --- | --- | --- |
| 1 | `spec/spec.md` specifies all of §4, and every rule has at least one conformance vector | met with caveats |
| 2 | Java and JS pass 100% of the evaluator and canonical vectors for both domains | **met** |
| 3 | The cross-language property test has run for at least one night with zero disagreements | **not yet** (scheduled) |
| 4 | Every engine error, evaluation error and lint rule is implemented, with a stable code, a golden test and a `tierwise explain` entry | **met** |
| 5 | No domain term appears in production code; the genericity check runs in CI | met with caveats |
| 6 | No modeling choice is hardcoded | met with caveats |
| 7 | A project for each built-in preset, a single-file project with no kinds or namespaces, and a project with custom kinds all validate and evaluate correctly | met with caveats |
| 8 | Every result carries provenance and `bundleVersion` | **met** |
| 9 | Applications evaluate in-process and keep working with the server down | met with caveats |
| 10 | Server `/resolve` and `/decide` match in-process results | **met** |
| 11 | The CLI ships as a native binary, a Docker image and a GitHub Action | **met** (built and tested; published at release) |
| 12 | The README has the positioning table and a verified five-minute quickstart for each language | **met** |
| 13 | All project files are present; releases are signed and ship an SBOM | **not yet** (signing needs the owner's keys) |
| 14 | ADRs exist for every decision not dictated by the requirements | **met** |

## 2. Criteria in detail

### 1. Spec §4 is specified, and every rule has at least one vector: met with caveats

- **Spec text:** `spec/spec.md` and `spec/sections/01`–`13`, 1,106 normative clauses with stable IDs. The header reads "Spec version: 0.1 (accepted 2026-10-09)".
- **Coverage** (`spec/conformance/COVERAGE.md`, `spec:check --strict`): 888 clauses are cited by a conformance vector, 235 have an enforcer entry, and 0 are uncovered. All 51 engine codes have a compile case.
- **Enforcers:** VEC-125 lets a clause that no vector can observe (durations, storage, thread safety, the explain command and so on) name an enforcer instead. Every enforcer entry in `spec/conformance/enforcers.yaml` and `enforcers.d/*.yaml` now names Java and JS tests that exist, or a check tool. **No entry is "Planned" any more:** 95 promised tests were written on 2026-10-10.
- **Caveat:** 218 clauses are covered by an enforcer and by no vector. This meets the criterion only as VEC-125 reads it. The owner should confirm that reading (O-4).

### 2. Both evaluators pass every evaluator and canonical vector, in both domains: met

- **Java:** `EvaluateVectorsTest` 576 of 576, `CanonicalVectorsTest` 32 of 32, `CompileCasesTest` 582 of 582 (CI run 37989009580).
- **JavaScript:** the conformance suite passes 576 evaluate and 32 canonical vectors; `pnpm --dir js test` gives 1,233 tests, 0 failures, 1 skipped (an opt-in tzdata comparison).
- **Both domains:** commerce (domain A), retail-pos (domain B) and saas-entitlements all have evaluate vectors.

### 3. One night of cross-language differential runs with zero disagreements: not yet (scheduled)

- **Hosted runs:** `nightly.yml` run 37989023510 (dispatched, seed 37989023510) generated 10,000 cases in Java and replayed them in JavaScript with no difference: 7,539 results, 9,683 listener events, and 2,461 error results across 9 codes. An earlier dispatched run, 37981195321, also passed.
- **Not yet:** the scheduled run (cron 03:17 UTC) has not fired yet. The criterion is met after the first scheduled run passes. *(OPEN)* O-10.

### 4. Every code and lint rule is implemented and tested, with an explain entry: met

- **Codes:** `spec/error-codes.md` lists 51 engine codes, 11 evaluation codes (4 of them shared with the engine) and 1 tooling code, plus 9 lint entries.
- **Golden tests:** every engine code has a compile case, every evaluation code is in the evaluate vectors, and `EvaluationErrorCodesTest` (Java) and `evaluation-error-codes.test.mjs` (JS) check that each operation raises only its own codes (EVAL-38).
- **Explain:** `ExplainCatalogueTest` checks that `tierwise explain` prints the exact catalogue text for all 51 engine codes and 8 lint rules (FIND-29). `tierwise explain naming/<name> --project DIR` prints the pattern's declared description, or the family entry followed by the pattern (FIND-30, ADR 0026; `ExplainNamingRuleTest`).

### 5. No domain term in production code, and the genericity check runs in CI: met with caveats

- `pnpm --dir js run genericity`: "146 names from 3 samples, 388 production files scanned, 21 allow-listed". CI job `javascript`, step "Genericity check".
- **Caveat:** the scan is lexical, and its allow-list (`js/scripts/genericity-allowlist.txt`) holds 21 words. A pass is not a semantic audit.

### 6. No modeling choice is hardcoded: met with caveats

- Kinds and naming patterns come from presets and the project schema (ADR 0011, ADR 0013). The engine takes levels, keys, facts and features from the bundle.
- The defaults in code are the runtime-integration defaults that §14 sets: refresh every 30 s, stale after 5 min, a provider timeout of 200 ms in the starter. The S3 modules' choices are in ADRs 0023, 0024 and 0027. A provider timeout is at most 2^53−1 ms in every evaluator (ADR 0025).
- **Caveat:** this rests on the lexical check and on design review.

### 7. A project per preset, a single-file project and a custom-kinds project all validate and evaluate: met with caveats

- `samples/commerce` (`tierwise:recommended`), `samples/saas-entitlements` (single file, `tierwise:entitlements`) and `samples/retail-pos` (custom kinds, no preset) validate, compile byte-identical to their conformance bundles, and evaluate in both evaluators. The release dry run runs all three through the fat JAR, the native binaries and the Docker image.
- `tierwise init --preset minimal|recommended|entitlements` projects pass `validate` and `lint`. The compile case `manifest-single-file` covers a single file with no kinds or namespaces.
- **Caveat:** the `init` projects are only validated, not evaluated.

### 8. Every result carries provenance and `bundleVersion`: met

The evaluate vectors compare result fields in both evaluators. The server returns `bundleVersion` on `/v1/resolve` (`EvaluationEndpointsTest`), and the starter logs it with each decision (`DecisionLoggingTest`). `ResultShapeTest` (Java) and `result-shape.test.mjs` (JS) check that options and listeners don't change the result's members (PROV-2).

### 9. Applications evaluate in-process and keep working with the server down: met with caveats

- **Java starter:** `TierwiseEvaluatorsTest` (last-known-good while the server is down; startup from a snapshot) and `TierwiseAutoConfigurationTest`.
- **Node:** `js/node/test/refresh.test.mjs`, "an unreachable server keeps the last good bundle, and the loader recovers".
- **Caveat:** these tests use stub servers in the test process, not a stopped `tierwise-server`.

### 10. `/resolve` and `/decide` match in-process results: met

- `EvaluationEndpointsTest` (`resolveEqualsTheInProcessResult`, `decideEqualsTheInProcessResult`).
- `tierwise-e2e` `EndToEndTest` runs a server, the Java starter and a Node service against three publish and promote steps, and all three must agree. It passes in CI (run 37989009580) and in the release dry run (`scripts/e2e.sh`).

### 11. Native binary, Docker image and GitHub Action: met (built and tested; published at release)

- **Native binaries:** release dry run 37989018796 built them on `ubuntu-24.04`, `macos-15` and `windows-2022` and ran each against the samples.
- **Docker images:** the same run built `docker/Dockerfile` (CLI) and `docker/server.Dockerfile`, and ran the CLI image against the samples. They are not pushed, because no registry has been chosen.
- **GitHub Action:** the CI job `github-action` runs `action.yml` with the `docker` method (validate and compile) and the `jar` method (compile from a `file:` URL and its SHA-256). It checks that both outputs equal `spec/conformance/bundles/commerce.json` and that a wrong SHA-256 is refused.
- Publishing these (GitHub release assets and an image registry) is part of the release itself; see section 4.

### 12. Positioning table and verified five-minute quickstarts: met

- **Positioning table:** in `README.md`, from `docs/vision.md#positioning`.
- **Quickstarts:** the CI job `quickstarts` runs `scripts/quickstart-check.sh` on a fresh hosted runner. It runs every marked block of `docs/quickstart-java.md` and `docs/quickstart-js.md` literally, in an empty `HOME`, and compares the output. Java took 12.2 s and JS 4.2 s, with 9 of 9 outputs matching for each (run 37985694991, repeated on every push since). A local run gave 13.5 s and 2.9 s.
- **Caveat:** the timing covers the reader's steps. The artifacts are built from the checkout first, because no release exists yet to download.

### 13. Project files present; releases signed and with an SBOM: not yet (signing needs the owner's keys)

- **Project files:** `scripts/check-foundation.py` passes, in CI and locally: foundation files, reactor metadata, release profile, action pins and the release workflow.
- **SBOM:** release dry run 37989018796 writes and validates CycloneDX 1.6 SBOMs for every Java module (`cyclonedx-maven-plugin`) and every npm package (`pnpm --dir js run sbom`), and adds them to `dist/sbom/` in the release assets.
- **Reproducibility:** the dry run's "Reproducibility check" job builds two clean clones in different directories and compares every JAR, SBOM, npm tarball and sample bundle. It passes.
- **Not yet:** nothing is signed. The `release` profile configures `maven-gpg-plugin` and `sigstore-maven-plugin`, and npm publishing uses `--provenance`. The keys and tokens are release secrets that only the owner can create (`docs/release-process.md`, steps 5 and later).

### 14. ADRs for every decision not dictated: met

ADRs 0001–0027 are in `docs/adr/`. ADR 0021 is amended for the Temurin 25 images (Dependabot #3). The decisions made during release hardening are:

- ADR 0024: server API and store choices.
- ADR 0025: the provider timeout bound.
- ADR 0026: naming explanations and kind suggestions.
- ADR 0027: S3 verification against MinIO.

The *(OPEN)* lists in the server and S3 READMEs now point to these ADRs.

## 3. Evidence

All runs are on `djkeshawa/visp-tierwise`, branch `main`, commit `90af984`, unless noted.

| Run | Workflow | Result |
| --- | --- | --- |
| 37989009580 | CI | Success. `foundation`; `java`: `verify -Pconformance` with core 2,621 tests (2 skipped), CLI 139 (3 skipped), starter 102, starter-s3 52 (4 opt-in skipped), server 254, server-s3 91 (12 opt-in skipped), OpenFeature 63, e2e 2. `java-runtime-17`; `javascript` (genericity, workspace check, build, 1,233 tests, Node 20 packaging); `quickstarts`; `github-action`; `s3-integration` (12 + 4 tests against MinIO RELEASE.2025-09-07T16-13-09Z, ADR 0027). |
| 37989009584 | Docs | Success: MkDocs site, Javadoc and TypeDoc. |
| 37989023510 | Nightly differential and benchmarks (dispatched) | Success: 10,000 cases, no difference. |
| 37989018796 | Release, `version=0.1.0`, `dry_run=true` | Success: inputs, build and test everything, native binaries (3 OS), Docker images, reproducibility check, checksums and release assets. The three publishing jobs were skipped, as a dry run requires. |

Local runs on 2026-10-10 (Linux, JDK 21.0.10, Node 26, pnpm 11.3.0):

- `./mvnw verify -Pconformance`: BUILD SUCCESS.
- `pnpm --dir js test`: 1,233 tests, 0 failed.
- `pnpm --dir js run spec:check -- --strict`: check passed, 0 uncovered.
- `scripts/quickstart-check.sh`: passed.
- `scripts/s3-it.sh` with the MinIO binary: 16 of 16.
- The registry checks: the GitHub organisation and user `tierwise`, npm `tierwise` and `@tierwise/evaluator`, the Maven Central group `io.github.tierwise`, PyPI `tierwise` and crates.io `tierwise` were all still free (404 or 0 results).

## 4. What is left

Every remaining item is an owner action or a scheduled run. The steps are in [`docs/release-process.md`](release-process.md).

| ID | Item | Who | Blocks |
| --- | --- | --- | --- |
| O-10 | The first scheduled nightly differential run (cron 03:17 UTC) passing. | CI, then a check | Criterion 3 |
| O2 | Create the `tierwise` GitHub organisation and transfer the repository to it; claim the `@tierwise` npm scope. | Owner | Publishing, criterion 13 |
| ~~O6~~ | **Done 2026-10-11:** the repository is public, as `djkeshawa/visp-tierwise`. | Owner | — |
| O-12 | Register the Maven Central namespace `io.github.tierwise`. Create the release secrets: Central token, GPG key, npm token. Then run `release.yml` with `dry_run=false`. | Owner | Criterion 13, publication |
| ~~O-SEC~~ | **Done 2026-10-11:** private vulnerability reporting is on, and the `release` environment has @djkeshawa as the required reviewer and deploys only from tags matching `v*`. Secret scanning and push protection are on. Check all four again after the transfer (O2). | Owner | — |
| O-4 | Confirm that enforcer-only clauses (VEC-125) meet criterion 1. | Owner | Criterion 1 wording |
| O-3 | Accept phases 0 and 4 to 8, including `tierwise-e2e` as the Phase 7 exit test, and Phase 9. | Owner | Phase acceptance |
| O-15 | Accept the plain benchmark harness in place of JMH for Phase 3 (ADR 0021). | Owner | Phase 3 wording |
| O-S3 | Optional: run `scripts/s3-it.sh` against real AWS S3. That needs an account and credentials. | Owner | Nothing (MinIO is verified) |
