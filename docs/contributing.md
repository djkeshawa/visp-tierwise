# Contributing

Strata is in Phase 0. Foundations and unanswered questions are the current scope. Do not begin specification or production implementation until the phase review authorizes it.

## Workflow

Read the root `CONTRIBUTING.md`, `GOVERNANCE.md`, `CODE_OF_CONDUCT.md` and `SECURITY.md` policies. The public repository and maintainers are unassigned; there is no external contribution endpoint yet.

Keep changes focused, state which requirement they address, run the applicable checks, and report actual results. Add behavior tests alongside implementation, and consume conformance vectors directly from `spec/conformance/` when those fixtures exist. Both evaluators will use the same vectors.

The structural Phase 0 checks are `python3 scripts/check-foundation.py` and `pnpm --dir js run check`. The build adapters are `node scripts/build-java.mjs` and `pnpm --dir js run build`; they require existing compiler tooling, with `STRATA_TYPESCRIPT_PATH` available to select a TypeScript installation.

Local empty builds passed through Java's explicit placeholder-only `--foundation-source-target` fallback and an existing TypeScript 6.0.3 installation, which emitted ESM, CJS and types. The Java runtime lacks `ct.sym`; strict `--release` compilation and Java 17 API compatibility remain unverified. The checks do not exercise evaluator behavior.

The local offline frozen pnpm install passed with an explicit writable store and no dependencies to install. Maven validation/verification, reproducible clean-machine builds and remote CI remain unverified. Pinned compiler tooling and docs generation need approval. Review the [Phase 0 validation record](phase-0-review.md) for the actual results.

Every commit requires DCO sign-off under the [Developer Certificate of Origin 1.1](https://developercertificate.org/):

```sh
git commit --signoff
```

Use your own name and email. The root contribution guide explains the sign-off trailer.

## Reviews and decisions

A non-trivial decision needs an ADR. A dependency also needs an ADR before it is added. Specification changes require an RFC as a pull request against `spec/`, with rationale, compatibility implications and conformance changes. Breaking specification changes require a major specification version.

Unclear requirements require review before an interpretation is chosen. At every phase boundary, summarize completed work, verification and every unspecified decision, then wait for review. See [ADR 0001](adr/0001-phase-boundaries.md).

## Project boundaries

Model names come from runtime declarations. Domain terms belong in samples and test fixtures, with kind names and naming guidance in presets. No company-specific content belongs in the project.

The first release does not include a general-purpose expression language, JavaScript source compiler, editing UI or browser enforcement.

Private security and conduct reporting are not configured in Phase 0. Sensitive reports must wait for a verified private contact; do not put details into public issues.
