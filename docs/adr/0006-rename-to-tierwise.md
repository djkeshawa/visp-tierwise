# ADR 0006: Rename the project to Tierwise

- Status: accepted (owner decision, 2026-10-08)
- Supersedes: the naming part of [ADR 0003](0003-provisional-identity.md)

## Context

"Strata" was a working name. OpenGamma Strata is an established Java library
published to Maven Central under `com.opengamma.strata`, which is the same ecosystem
and the same audience. Keeping the name would hurt discoverability and invite
confusion over artifact coordinates.

## Decision

Rename the project to **Tierwise**.

| Identity | Value |
| --- | --- |
| Project name | Tierwise |
| npm scope | `@tierwise` (`@tierwise/evaluator`, `@tierwise/node`, `@tierwise/openfeature`) |
| Maven `groupId` | `io.github.tierwise` |
| Java packages | `io.github.tierwise.<module>` |
| Maven artifacts | `tierwise-core`, `tierwise-cli`, `tierwise-spring-boot-starter`, `tierwise-server`, `tierwise-openfeature` |
| CLI command | `tierwise` |
| Project manifest | `tierwise.yaml` |
| Built-in preset references | `tierwise:recommended`, `tierwise:minimal`, `tierwise:entitlements` |
| OpenFeature fact attribute | `tierwise.facts` |
| GitHub owner (proposed) | `tierwise` organisation |

`io.github.tierwise` was chosen over `dev.tierwise` because `tierwise.dev`,
`tierwise.io` and `tierwise.com` are all registered by other parties. Maven Central
verifies an `io.github.<org>` namespace through ownership of the GitHub
organisation, so no domain purchase is needed. If the owner later acquires a
domain, the `groupId` can change before the first publish.

## Availability evidence (probed 2026-10-08)

| Check | Result |
| --- | --- |
| npm package `tierwise` | Not found (404) |
| npm scope `@tierwise` | Not found (404) |
| GitHub user or organisation `tierwise` | Not found (404) |
| GitHub repositories named `tierwise` | 5 unrelated results |
| Maven Central `io/tierwise`, `dev/tierwise` | Not found |

These probes don't reserve anything. Claim the GitHub organisation and npm scope
before announcing the name, and re-check all registries before the first publish.

## Consequences

All build coordinates, module directories, package names and docs use the new
identity. Historical records in `docs/history/` keep the old name as written at the
time. The local checkout directory was renamed from `strata/` to `tierwise/` on 2026-10-09; renaming it is a local
choice with no effect on the project.
