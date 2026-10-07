# Strata documentation

**Phase 0 foundations, awaiting review.** There is no accepted specification, compiled bundle, evaluator or CLI. These docs describe project intent and the contribution process; they do not document usable runtime behavior.

The user-authorized prompt review is integrated into the
[amended brief](build-brief.md). Read the [recommendation coverage](recommendations-review.md)
and remaining [questions](open-questions.md) before starting specification work.
The completed [Claude Opus 5.5 xhigh consultation](claude-consultation.md) selects
full skipping for ignored scopes and separate provenance for canonical bundles.
[ADR 0005](adr/0005-node-admission-and-canonical-artifacts.md) records the rationale.

Strata is intended to combine typed values across user-declared levels and evaluate entity-aware decisions. Each key declares how its values combine. Java and JavaScript will share a language-neutral specification and the same evaluator conformance fixtures. Every answer will carry provenance and a bundle version.

The engine must remain generic. Hierarchies, entities, facts, features and effects come from each user's model. Presets offer optional guidance, while engine guarantees remain mandatory.

## Documentation roadmap

| Phase | Documentation |
| --- | --- |
| 0 | Contribution process, governance, open questions and phase boundaries |
| 1 | Normative specification, source and bundle schemas, presets, stable diagnostics and conformance vectors |
| 2–8 | Validated examples and API references alongside the Java toolchain, JavaScript evaluator and integrations |
| 9 | Verified five-minute Java and JavaScript quickstarts, generated Javadoc and TypeDoc, release and operations guides |

The docs site tool is provisionally MkDocs Material; approval of its pinned build dependencies is pending. No site has been generated or published, and API references are unavailable until there is code to document. Package names, public repository ownership, maintainers and private reporting methods still need review.

CI is configured for Java placeholder compilation and JavaScript workspace checks; remote CI has not run. Local empty builds passed through installed compilers: Java's explicit source/target fallback compiled package placeholders, and TypeScript 6.0.3 emitted ESM, CJS and types. The stripped local Java runtime lacks `ct.sym`, so strict release checks and Java 17 API compatibility remain unverified. No functional evaluator behavior has been implemented or tested.

The local offline frozen pnpm install passed with an explicit writable store and no dependencies to install. Maven verification and reproducible clean-machine builds with approved compiler tooling remain open. Read the [Phase 0 review record](phase-0-review.md) for exact validation and the [open questions](open-questions.md) before beginning Phase 1.

Read the [contribution guide](contributing.md) and the [phase-boundary ADR](adr/0001-phase-boundaries.md).

## Browser use

Browser evaluation is for display only. Client-side inputs and outputs can be altered; decisions must be enforced on the server.
