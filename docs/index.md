# Tierwise documentation

Tierwise is a type checker and evaluator for layered business configuration, with
Java and JavaScript evaluators that follow one language-neutral spec.

**Status:** feature-complete; release 0.1.0 pending owner steps (Phase 9 in progress).
The spec is version 0.1 and the bundle format is version 1. Nothing is published.
The [release checklist](release-0.1.0-checklist.md) records every acceptance
criterion and its status. See the [roadmap](roadmap.md) for each phase.

| Read | For |
| --- | --- |
| [Vision and strategy](vision.md) | Why Tierwise exists, who it's for, principles, risks |
| [Requirements](requirements.md) | What 0.1.0 must do, including the normative draft (§4) and the acceptance criteria (§15) |
| [Release 0.1.0 checklist](release-0.1.0-checklist.md) | Each acceptance criterion, its status and the evidence behind it |
| [Roadmap](roadmap.md) | Phases, exit criteria, status, milestones |
| [Changelog](../CHANGELOG.md) | What 0.1.0 contains, by package |
| [Quickstart: Java](quickstart-java.md), [Quickstart: JavaScript](quickstart-js.md) | A first project in each language (not yet verified on a clean machine) |
| [CLI reference](guides/cli-reference.md), [Modeling guide](guides/modeling.md) | The `tierwise` commands, and how to describe levels, keys and decisions |
| [Benchmarks](benchmarks.md) | In-process `resolve` and `decide` cost in both evaluators |
| [Open questions](open-questions.md) | What only the project owner can decide |
| [Decisions (ADRs)](adr/) | Why each non-obvious choice was made |
| [Contributing](contributing.md), [RFC template](rfc-template.md) | How to propose changes |

## Decision index

| ADR | Decision |
| --- | --- |
| [0001](adr/0001-phase-boundaries.md) | Review stop at every phase boundary |
| [0002](adr/0002-foundation-tooling.md) | Interim Phase 0 build adapters (superseded by 0012) |
| [0003](adr/0003-provisional-identity.md) | Keep publishing identity unclaimed until the owner confirms (naming superseded by 0006) |
| [0004](adr/0004-adopt-prompt-review.md) | Adopt the prompt review recommendations |
| [0005](adr/0005-node-admission-and-canonical-artifacts.md) | Ignored scopes are skipped entirely; canonical bundles with separate provenance |
| [0006](adr/0006-rename-to-tierwise.md) | Rename to Tierwise |
| [0007](adr/0007-hierarchy-root-and-admission.md) | Singleton root, per-scope declarations, context validation |
| [0008](adr/0008-merge-semantics.md) | Strategies, axes, defaults, `deny`, contribution effects |
| [0009](adr/0009-providers-and-evaluation-errors.md) | Provider contract and evaluation errors |
| [0010](adr/0010-entities-facts-and-rules.md) | Entity graph, FactSets, settings, effects, traces |
| [0011](adr/0011-source-format-and-types.md) | Type grammar, normalisation, namespaces, presets |
| [0012](adr/0012-parsing-and-dependency-baseline.md) | YAML 1.2, in-house JCS and SHA-256, approved dependency baseline and pinned toolchain |
| [0013](adr/0013-spec-open-point-resolutions.md) | Resolutions for the wave 1 spec open points |
| [0014](adr/0014-final-spec-text-points.md) | Final open points in the spec text (IANA tz release 2026e, value equality, listener calls) |
| [0015](adr/0015-wave2-spec-gaps.md) | Spec gaps found by the schemas, presets and samples |
| [0016](adr/0016-conformance-suite-scope.md) | Conformance suite scope for v0.1 and enforcer files |
| [0017](adr/0017-finding-locations.md) | Where findings are located |
| [0018](adr/0018-java-core-implementation-choices.md) | Java core implementation choices (YAML limits, writer order, provider pool) |
| [0019](adr/0019-js-evaluator-choices.md) | JS evaluator implementation choices (provider registration, loader parity) |
| [0020](adr/0020-runtime-integration-dependencies.md) | Runtime integration, server and OpenFeature dependencies (Spring Boot 4.1.1, AWS SDK for the S3 modules) |
| [0021](adr/0021-cli-codegen-and-distribution.md) | CLI output, codegen, benchmarks, native image and Docker choices |
| [0022](adr/0022-release-tooling.md) | Release and documentation tooling (Javadoc, signing, SBOM, MkDocs, TypeDoc) |
| [0023](adr/0023-s3-module-choices.md) | S3 module choices: location property, region key, HTTP client, conditional requests |
| [0024](adr/0024-server-api-and-store-choices.md) | Server API and bundle store choices: publish encoding, `latest`, responses, error codes, record order, pointer bytes |
| [0025](adr/0025-provider-timeout-upper-bound.md) | A provider timeout is at most 2^53−1 ms in every evaluator |
| [0026](adr/0026-naming-explanations-and-kind-suggestions.md) | `explain naming/<name>` reads the project's pattern (`Tierwise.namingPatterns`); `E_UNKNOWN_KIND` suggests declared kinds |

## Browser use

Browser evaluation is for display only. Client-side inputs and outputs can be
altered, so decisions must be enforced on the server.
