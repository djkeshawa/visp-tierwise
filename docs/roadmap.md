# Roadmap

Release **0.1.0** ships the full scope in [the requirements](requirements.md). Work
proceeds in ten phases, and each one ends with a **review stop**: a summary, the
evidence for its exit criteria, and every decision the requirements didn't dictate.
Accepting that review authorises the next phase.

```
 0 Foundations ─► 1 Spec v0.1 ─► 2 Java validation ─► 3 Java evaluation ─┬─► 4 JS evaluator ─┐
                                                                          └─► 5 Toolchain ────┤
                                                       ┌──────────────────────────────────────┘
                                                       ├─► 6 Runtimes ─┐
                                                       ├─► 7 Server ───┼─► 9 Release 0.1.0
                                                       └─► 8 OpenFeature┘
```

Phases 4 and 5 depend only on Phase 3, and 6–8 only on 4 and 5. With more than one
contributor they can run in parallel, each with its own review stop.

| # | Phase | Deliverables | Exit criteria | Status |
| --- | --- | --- | --- | --- |
| 0 | **Foundations** (close-out) | Owner items; approved dependency baseline ([ADR 0012](adr/0012-parsing-and-dependency-baseline.md)); Maven Wrapper and pinned TypeScript; Phase 0 build adapters removed | Owner questions answered; `./mvnw verify` and `pnpm -r build` pass on a clean machine; hosted CI green | **Complete.** Hosted CI is green on `djkeshawa/visp-tierwise` (run 37989009580). Item 0.6 (the `tierwise` organisation) still needs the owner (O2). |
| 1 | **Spec v0.1** | `spec/spec.md`; `error-codes.md`; JSON Schemas (manifest, sources, bundle, vectors); presets; samples A, B and entitlements; hand-built canonical bundles; evaluate, canonical and compile vectors; stdlib-only spec tooling | Spec reviewed and accepted. Every rule in requirements §4 maps to ≥1 vector. Every code in §6 has a positive and a negative compile vector. **The most important review.** | **Complete** (review accepted, per [phase-1-review](phase-1-review.md)). 888 of 1,106 clauses are cited by a vector. The other 218 are not observable by a vector, and each names Java and JS tests or a check tool under VEC-125; none is still "Planned". The owner should confirm that reading (O-4 in the [release checklist](release-0.1.0-checklist.md)). |
| 2 | **Java validation** | Source loader (YAML 1.2, positions), manifest and preset composition, model, every engine check, lint framework | All compile *error* vectors pass; every §6 rule has positive and negative tests; golden file/path/line output | **Complete.** 582 of 582 compile cases pass. |
| 3 | **Java evaluation** | Compiler (canonical bundles), bundle loader, resolver, decision engine, provenance, `EvaluationListener` | Compiled samples are byte-identical to the Phase 1 bundles; all evaluate and canonical vectors pass; JMH baseline reported | **Complete.** Compiled samples are byte-identical to the Phase 1 bundles, and 576 of 576 evaluate and 32 of 32 canonical vectors pass. The benchmark is a plain harness, not JMH ([ADR 0021](adr/0021-cli-codegen-and-distribution.md)). |
| 4 | **JS evaluator** | `@tierwise/evaluator`: JCS, SHA-256, loader, resolve/decide, sync variants; ESM + CJS + types | Same vectors pass; zero runtime dependencies; browser smoke test; nightly Java↔JS differential job running | **Complete.** 608 vector subtests pass. The nightly differential job runs on hosted runners: 10,000 cases with no difference (run 37989023510). |
| 5 | **Toolchain** | CLI (`init`, `validate`, `lint`, `test`, `compile`, `codegen --lang java\|ts`, `explain`); native binaries; Docker image; GitHub Action | `tierwise init` with each preset validates; a sample repo validates and compiles in a GitHub Actions workflow; codegen output compiles in both languages | **Complete.** The release dry run (run 37989018796) built native binaries on Linux, macOS and Windows and the Docker images, and ran them against the samples. The CI job `github-action` runs the action with both methods. |
| 6 | **Runtimes** | Spring Boot starter; `@tierwise/node` | Section 12 integration tests pass (refresh, ETag, atomic swap, last-known-good, provider `skip`/`deny`) | **Complete.** The starter (102 tests), the S3 source (52, including 4 against MinIO) and `@tierwise/node` pass. The S3 304 and conditional-write paths run against MinIO in CI (ADR 0027). |
| 7 | **Server** | Section 9 endpoints, storage SPI (filesystem, S3), auth, OpenAPI | End-to-end test passes, including promote and rollback; `/resolve` and `/decide` match in-process results | **Complete.** The cross-language end-to-end test, `java/tierwise-e2e`, passes in CI and in the release dry run. It compiles the commerce sample and a modified copy, publishes both, has a Java starter and a Node service pick up each version, promotes and rolls back, and compares 25 cases against the server, Java and Node at each step. Server tests: 254. The owner has not yet accepted it as the exit test. *(OPEN)* |
| 8 | **OpenFeature** | Java and JS providers; mapping appendix in the spec | Provider tests pass; mapping documented | **Complete.** The Java provider (63 tests) and `@tierwise/openfeature` (59 tests) pass. |
| 9 | **Release 0.1.0** | Docs site, quickstarts, Javadoc and TypeDoc, publish workflows (Maven Central, npm), Sigstore signatures, CycloneDX SBOMs | Clean-machine quickstart under 5 minutes in each language; requirements §15 checklist complete; registries re-checked for the name | **Engineering complete; waiting on the owner.** The docs site, Javadoc and TypeDoc build (Docs workflow). The quickstarts pass literally on a fresh hosted runner, in 12.2 s (Java) and 4.2 s (JS). The release dry run passes: native binaries, Docker images, the reproducibility check and the SBOMs. The registries were re-checked on 2026-10-10. Remaining: one scheduled nightly run, then the owner's items (organisation and npm scope, Central namespace, signing keys and tokens, the real release run). Tracked in the [release checklist](release-0.1.0-checklist.md). |

## Milestone markers

| Marker | After phase | Meaning |
| --- | --- | --- |
| **Spec frozen for 0.1** | 1 | Further spec changes go through an RFC |
| **Vertical slice** | 4 | Sources → bundle → identical answers in Java and JS. This is the first point where the core promise can be demonstrated. |
| **Feature complete** | 8 | Everything in scope exists; Phase 9 is hardening and release only |

## After 0.1.0 (candidates, each needs an RFC)

- Numeric comparisons; rules reading resolved keys; nested quantifiers
- Hit policies beyond `FIRST`
- Evaluators in other languages, built against the conformance suite
- A JS validator for editor tooling (a language server)
- Channel pointers and signed bundle attestations in the server
