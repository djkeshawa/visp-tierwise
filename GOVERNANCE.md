# Governance

## Foundation status

This document proposes the maintainer-led governance default for Phase 0 review. There is no appointed maintainer group, public GitHub home or accepted specification yet. The project owner must confirm the governance process and populate `MAINTAINERS.md` before it takes effect for external contributions.

## Maintainers

Maintainers steward the specification, review contributions, enforce project scope, and coordinate releases. The maintainer register records names, roles and contact routes. Security and conduct responsibilities require explicit assignment and private reporting channels.

Appointment and removal decisions must be recorded transparently in the maintainer register after review by the current maintainers. Maintainers must disclose relevant conflicts and recuse themselves when appropriate. Appointment criteria, removal criteria and how to resolve a tie are open decisions for the Phase 0 review.

## Decisions

Discuss proposed changes in issues and pull requests once the repository is configured. Seek consensus and record rationale. A maintainer approves a change before merge. If maintainers cannot agree, leave the proposal open and document the disagreement; do not invent a tie-breaking authority.

Small corrections need a focused explanation and applicable checks. Non-trivial design decisions also need a short ADR in `docs/adr/`. Every new dependency requires an ADR explaining its purpose and cost.

Contributions use DCO sign-off under Apache-2.0. Conduct follows the Contributor Covenant 2.1; security reports follow `SECURITY.md`.

## Specification RFCs

The accepted language-neutral specification is authoritative for both implementations. A specification change requires an RFC presented as a pull request against `spec/` with:

- the problem and proposed language-neutral behavior;
- rationale and alternatives considered;
- affected implementations, diagnostics and conformance vectors;
- compatibility and migration impact;
- the proposed specification and bundle-format version impact.

An RFC requires maintainer approval before implementation. Breaking specification changes require a major specification version. Packages use SemVer and declare supported specification versions; the specification and bundle format have independent version identifiers.

## Phase reviews

Each phase ends with a review stop. Its summary must state what was built, the evidence for its completion criteria, and every choice the requirements did not dictate. Review acceptance authorizes the next phase. Phase 1 specification review must precede production implementation.

Publishing, repository ownership, final package identifiers and release permissions remain unassigned. Release artifacts, Sigstore signing and CycloneDX SBOMs are release requirements, not claims about this foundation.
