# Phase 1 review: Spec v0.1

- Status: complete; accepted under the owner's "complete the project" instruction (2026-10-09)
- Built by the `tierwise-work-items` workflow: Haiku 5.5 drafted and verified, Sonnet 5.5 (from wave 1b; Opus in wave 1) reviewed each item, and the main model did the final reviews and decisions

## Delivered

| Artifact | Contents |
| --- | --- |
| `spec/spec.md`, `spec/sections/01`–`13` | 1,106 normative clauses with stable IDs; no open points |
| `spec/error-codes.md` | 59 codes (51 engine, 11 evaluation including shared ones, 1 tooling) and 9 lint rules, each with meaning, triggers, example and fix |
| `spec/schemas/` | 13 JSON Schemas (2020-12): manifest, preset, schema/values/rules files, bundle, provenance, results, and the vector formats |
| `spec/data/tz-names.txt` | IANA tz release 2026e (597 names) |
| `presets/` | `recommended`, `minimal`, `entitlements` |
| `samples/` | commerce (domain A), retail-pos (domain B, custom kinds, no preset) and saas-entitlements (single-file docs sample) |
| `spec/conformance/` | Bundles for every sample, edge bundles and invalid bundles; 572 evaluate vectors, 32 canonical vectors, 580 compile cases and 15 provenance cases; `COVERAGE.md` |
| `js/spec-tools/` | `canonicalize` (JCS + SHA-256) and `check` (schema validation, digests, citations, coverage) |
| `js/scripts/check-genericity.mjs` | Domain-name scan of production code, run in CI |

## Evidence

- `pnpm --dir js run spec:check -- --strict` passes:
  - all 1,106 clauses are covered (886 cited by vectors, 236 listed with an enforcer);
  - all 51 engine codes have at least one compile case;
  - every bundle is byte-canonical and its digest verifies.
- `pnpm --dir js test` passes (spec-tool and packaging tests). The genericity check passes.
- **Not yet proven:** no implementation has run the vectors. The expected outcomes were derived by hand and cross-checked by reviewers. Phases 3 and 4 will be the first execution, and any disagreement found there is settled against the spec text.

## Decisions not dictated by the requirements

ADRs 0013 to 0017 record them: the open-point resolutions, the last spec points, the
wave 2 gaps, the conformance scope, and finding locations.
