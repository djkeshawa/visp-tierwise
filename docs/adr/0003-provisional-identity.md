# ADR 0003: Keep publication identity unclaimed

Status: naming superseded by [ADR 0006](0006-rename-to-tierwise.md); the rule against claiming unverified identities still applies

## Context

The brief names Strata as a working name and leaves the npm scope and Maven groupId
as placeholders. No GitHub owner or reverse-domain ownership is provided.

## Decision for review

Create the project in the requested folder as `strata/`. Use `@strata` and
`org.strata` only as provisional local build coordinates. These values are not
claims of ownership or registry availability. Initialize a local Git repository
on `main` with no remote, commit, push or publication. Provide a source archive in
the chat's outputs directory for review; generated artifacts are excluded.

No repository URL, maintainer handle or security contact is fabricated. Confirm
the owner and change all coordinates before preparing public packages. Keep
packages private and omit publishing workflows until the release phase and
explicit approval. License notices use the neutral project contributors name.

## Availability findings (7 October 2026)

The name already appears in public projects, including
[an existing Strata Gradle plugin](https://github.com/jzbrooks/strata) and
[an existing Strata Maven artifact](https://central.sonatype.com/artifact/com.opengamma.strata/strata-pricer).
Those findings do not establish availability or ownership of any chosen GitHub
organisation, npm scope or Maven namespace.

Direct npm and exact-coordinate Maven probes could not complete: the browsing
tool returned inaccessible/forbidden responses, and local network DNS was
unavailable. Registry availability remains unverified and must be checked again
with the final identity before publication.

## Consequences

The foundation can be reviewed without a naming or ownership claim. Final
identity, maintainers and private reporting channels remain open review items.
