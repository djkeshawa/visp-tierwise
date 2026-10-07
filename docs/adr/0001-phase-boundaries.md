# ADR 0001: Preserve the phase review boundaries

- Status: proposed for Phase 0 review
- Date: 2026-10-07

## Context

The project brief requires specification work before implementation and a review stop at the end of every phase. Phase 0 delivers the monorepo foundations, project policies, empty build CI and unanswered questions. Phase 1 defines the language-neutral specification, schemas, presets, diagnostics and conformance suite.

The same brief contains proposed semantics and API sketches. Copying them into implementation before review would treat unresolved interpretations as accepted behavior.

## Decision

Keep this change within Phase 0. Provide empty Java and JavaScript build scaffolding and the project documentation. Reserve specification and source paths without defining semantics, package APIs or model implementations.

At the review boundary, report foundation checks and every choice that was not dictated. Wait for review before beginning Phase 1. Review and acceptance of Phase 1 must precede production implementation.

Identities, governance appointments, private reporting channels and tooling choices remain provisional where the brief has not established them. No repository URL, maintainer identity, publishing authority or availability claim may be invented.

## Provisional documentation choices

These choices were not specified in detail and require Phase 0 review:

- Use `org.strata` as the Maven placeholder and retain `@strata` as the npm placeholder, without claiming either namespace.
- Adopt Contributor Covenant 2.1 by reference to its authoritative text. Enforcement leads and a private contact remain unassigned; a lead involved in a report recuses themselves.
- Link DCO 1.1 as the certification used by commit sign-off.
- Describe GitHub private vulnerability reporting conditionally, without inventing a repository URL, address, response target or support window.
- Leave maintainer appointment, removal criteria and tie resolution open rather than claiming an authority that has not been assigned.
- Mark both product quickstarts unavailable until their implementation and verification phases.

## Consequences

The foundation can be reviewed and checked without implementing an unreviewed model. Product quickstarts and conformance tests are unavailable at this phase and must be labeled accordingly.

The project is not usable as a library yet. Specification work, runtime behavior, API reference generation and release infrastructure follow their assigned phases. Passing an empty build is evidence for the foundation only.
