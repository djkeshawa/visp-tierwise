# Contributing to Strata

The project is in Phase 0 and awaits its first review. Review the current phase before proposing work. There is no accepted normative specification or public API yet.

## Contribution process

1. Explain the problem and the current phase it belongs to in an issue or pull request when a public repository is configured.
2. Keep changes focused and preserve unrelated work. Use the repository's existing scripts and package manager.
3. Add behavior tests alongside implementation. Use the shared conformance fixtures directly; never copy them into individual packages.
4. Run the focused checks for the change and the applicable repository checks. Report actual results and any limitations in the pull request.
5. Sign off every commit and request maintainer review.

During Phase 0, contribute to foundations and open questions. Specification semantics, presets and conformance vectors are Phase 1 work. Production implementation starts only after the specification review.

## Foundation checks

From the project root:

```sh
python3 scripts/check-foundation.py
pnpm --dir js run check
```

These inspect repository structure, Maven metadata and JavaScript workspace/strict configuration. The separate build adapters use installed compiler tooling:

```sh
node scripts/build-java.mjs
pnpm --dir js run build
```

The Java adapter requires a complete Java 21+ installation. The JavaScript adapter requires an existing TypeScript compiler, resolved normally or supplied through `STRATA_TYPESCRIPT_PATH`.

Local empty builds passed using the placeholder-only Java `--foundation-source-target` fallback and an existing TypeScript 6.0.3 compiler, which emitted ESM, CJS and types. The fallback does not establish Java 17 API compatibility; strict `--release` remains unverified because the local runtime lacks `ct.sym`. No evaluator or other functional runtime is implemented.

The local offline frozen pnpm install passed with an explicit writable store and no dependencies to install. Maven validation/verification, reproducible clean-machine builds and remote CI remain unverified. Pinned build tooling and docs generation remain pending approval. Review [the Phase 0 validation record](docs/phase-0-review.md) before interpreting check results.

## Developer Certificate of Origin

Contributions use the [Developer Certificate of Origin 1.1](https://developercertificate.org/). By adding a `Signed-off-by` trailer, you certify your contribution under that certificate. A CLA is not required.

Use your own name and email:

```sh
git commit --signoff
```

This adds a trailer such as:

```text
Signed-off-by: Your Name <your-email@example.invalid>
```

Do not add someone else's sign-off. A DCO sign-off is distinct from cryptographic commit signing.

## Design and specification changes

The accepted language-neutral specification will be the source of truth. Any implementation disagreement must be resolved against it.

Record non-trivial design decisions in `docs/adr/`. Explain each new dependency in an ADR before adding it. Specification changes require an RFC as a pull request against `spec/`, with rationale, compatibility impact and conformance changes. Breaking specification changes require a major specification version and maintainer approval.

If a requirement is ambiguous or contradictory, ask for review before choosing an interpretation. At the end of every phase, summarize the deliverables and every unspecified decision, then wait for review.

## Genericity and scope

Production code must use runtime-declared model names. Domain-specific levels, axes, entities, facts, keys, features and effects belong only in samples and fixtures. Kind names and naming recommendations belong in presets. No company-specific content belongs in this repository.

Keep the public API small and documented. No general-purpose expression language, JavaScript compiler, editing UI or browser enforcement is planned in the initial scope.

## Conduct and security

Follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Report vulnerabilities using [SECURITY.md](SECURITY.md), without disclosing exploit details in a public issue.

Maintainer identities and private reporting contacts are not assigned yet. The [maintainer register](MAINTAINERS.md) must be populated before the project starts accepting external contributions.
