# ADR 0028: Publish under the visplabs organisation

- Status: accepted (owner decision on the owner, the npm scope and the repository name; delegated decision on the
  Java packages)
- Date: 2026-10-11
- Supersedes: the npm scope, Maven `groupId`, Java package and GitHub owner rows of
  [ADR 0006](0006-rename-to-tierwise.md). The project name and every other row of that ADR stay.

## Context

ADR 0006 proposed a GitHub organisation named `tierwise`, and from it the Maven `groupId` `io.github.tierwise` and the
npm scope `@tierwise`. That organisation was never claimed. On 2026-10-11 the owner created the GitHub organisation
`visplabs` and chose to publish under it. The repository moved from `djkeshawa/visp-tierwise` to `visplabs/tierwise`
the same day, and GitHub redirects the old URL.

Maven Central verifies an `io.github.<name>` namespace by asking the owner of the GitHub account `<name>` to create a
repository. Under `visplabs` the only namespace that can be verified this way is `io.github.visplabs`. No release has
been published yet, so the coordinates can still change.

## Decision

| Identity | Value |
| --- | --- |
| GitHub repository | `visplabs/tierwise` |
| npm scope | `@visplabs` (`@visplabs/evaluator`, `@visplabs/node`, `@visplabs/openfeature`) |
| Maven `groupId` | `io.github.visplabs` |
| Java packages | `io.github.visplabs.tierwise.<module>` (for example `io.github.visplabs.tierwise.core`) |
| Java module of tierwise-core | `io.github.visplabs.tierwise.core` |
| Container images, once a registry is chosen | `ghcr.io/visplabs/tierwise` |

These stay as they are: the project name Tierwise, the artifact ids (`tierwise-core` and the rest), the CLI command
`tierwise`, `tierwise.yaml`, the `tierwise:` preset references, the `tierwise.` Spring property prefix and the
`tierwise.facts` OpenFeature attribute.

The owner chose the npm scope and the repository name. The Java package is a technical choice made here.

## Alternatives considered for the Java packages

- **`io.github.visplabs.tierwise.<module>` (chosen).** The package starts with the namespace the project can prove it
  owns, and the extra `tierwise` segment leaves room for other visplabs projects.
- **Keep `io.github.tierwise.<module>`.** No code would change, but the prefix would name a GitHub account the
  project does not own. Whoever claims `tierwise` could publish classes in the same packages. A package cannot be
  renamed after 0.1.0 without breaking every user, so the time to change it is now.
- **`io.github.visplabs.<module>`.** Shorter, but `io.github.visplabs.core` reads as belonging to the organisation,
  not to Tierwise.

## Consequences

- Every POM, Java source directory, `module-info.java`, Spring auto-configuration list, native-image configuration,
  `package.json`, import, code generator template and golden file uses the new identity. The TypeScript code generator
  now emits imports from `@visplabs/evaluator`.
- The owner must create the npm organisation `visplabs` and register `io.github.visplabs` at Maven Central before the
  first publish (`docs/release-process.md`, steps 2 and 5).
- ADRs older than this one are historical and keep the names they used at the time.
