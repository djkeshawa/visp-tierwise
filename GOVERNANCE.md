# Governance

## Status

Tierwise is maintainer-led. The maintainers are listed in `MAINTAINERS.md`; the project lead is the founding maintainer.

## Maintainers

Maintainers steward the specification, review contributions, enforce project scope, and coordinate releases. The maintainer register records names, roles and contact routes. Security and conduct responsibilities require explicit assignment and private reporting channels.

A contributor with a record of sustained, high-quality contributions may be nominated by any maintainer and is appointed by lazy consensus of the current maintainers. A maintainer may step down at any time, and one who is inactive for twelve months or breaches the Code of Conduct may be removed by the other maintainers. Appointment and removal decisions are recorded in the maintainer register. Maintainers disclose relevant conflicts and recuse themselves when appropriate.

## Decisions

Discuss proposed changes in issues and pull requests. Decisions are made by lazy consensus: a proposal stands when no maintainer objects within a reasonable review period, and a maintainer approves a change before merge. Until the project has three or more maintainers, the project lead breaks ties; after that, an unresolved disagreement is settled by a simple majority of maintainers.

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

## Releases

The release maintainer publishes from the `release` workflow, as described in `docs/release-process.md`. Releases are signed and ship CycloneDX SBOMs.
