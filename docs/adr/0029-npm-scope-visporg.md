# ADR 0029: The npm scope is @visporg

- Status: accepted (owner decision)
- Date: 2026-10-11
- Supersedes: the npm scope row of [ADR 0028](0028-visplabs-publication-identity.md). Every other row of that ADR
  stays.

## Context

ADR 0028 chose the npm scope `@visplabs` to match the GitHub organisation. An npm scope belongs to the npm user or
organisation of the same name. The owner created the npm organisation `visporg`, not `visplabs`, so the packages can
only be published as `@visporg/*`. No npm package has been published yet, so the scope can still change.

## Decision

| Identity | Value |
| --- | --- |
| npm scope | `@visporg` (`@visporg/evaluator`, `@visporg/node`, `@visporg/openfeature`; `@visporg/spec-tools` stays private) |

The GitHub repository `visplabs/tierwise`, the Maven `groupId` `io.github.visplabs` and the Java packages
`io.github.visplabs.tierwise.*` do not change.

## Alternatives considered

- **`@visporg` (chosen).** It is the organisation the owner holds on npm.
- **Keep `@visplabs`.** It would need a second npm organisation named `visplabs`. The owner chose `visporg`.

## Consequences

- Every `package.json`, import, README, guide, code generator template and golden file uses `@visporg`. The TypeScript
  code generator emits imports from `@visporg/evaluator`.
- The npm scope and the GitHub owner differ. npm provenance checks `repository.url` against the repository that runs
  the workflow (`visplabs/tierwise`), not against the scope, so this does not affect publishing.
- The `NPM_TOKEN` secret of the `release` environment must have publish rights on `@visporg`.
