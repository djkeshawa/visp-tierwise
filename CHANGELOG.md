# Changelog

This project uses the [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) structure. Package releases will follow Semantic Versioning. The specification and bundle format will be versioned separately.

No package has been released. No specification or bundle format version is supported yet.

## [Unreleased]

### Added

- Monorepo foundation for the planned Java reference toolchain and TypeScript workspace.
- Apache-2.0 project licensing and contributor policies, with DCO sign-off.
- Phase 0 documentation, governance proposal and review boundary ADR.
- Installed-compiler adapters and locally compiled empty Java/TypeScript targets; no runtime implementation or conformance claim.

### Changed

- Renamed the project from Strata to Tierwise (`@tierwise`, `io.github.tierwise`) to avoid a collision with OpenGamma Strata (ADR 0006).
- Restructured the documentation into a vision, consolidated requirements, a roadmap, an implementation plan and owner-only open questions; moved process records to `docs/history/`.
- Resolved all 19 open semantic questions (ADRs 0007–0011): singleton root and per-scope declarations, merge and axis semantics, the provider contract and evaluation errors, the entity graph and FactSets, the type grammar and source composition.
- Proposed YAML 1.2 parsing, in-house JCS and SHA-256, and a dependency baseline for approval (ADR 0012).

- Applied the supplied prompt review to an amended build brief, with node-source
  policies, a key-type vocabulary, exact content hashing, root-binding validation
  and explicit deferred extensions.
- Corrected the commerce schema excerpts: explicit strategy/types and manifest-only
  source version authority.
- Reconciled CLI inventories and provider-failure test requirements, retained the
  strict missing-fact precheck, and recorded recommendation coverage and remaining
  decisions in ADR 0004.
- Applied assessed Claude Opus 5.5 xhigh advice in ADR 0005: fully skip ignored
  runtime scopes, keep authored catalog references strict, and store variable
  provenance outside canonical bundles. Recorded the requested consultation
  process for future technical clarification.

### Pending review

- Claiming the GitHub organisation and npm scope; maintainer assignments.
- Approval of the dependency baseline (ADR 0012).
- Private security and conduct reporting channels.
- Clean-toolchain foundation verification, hosted CI and authorization to begin Phase 1.
