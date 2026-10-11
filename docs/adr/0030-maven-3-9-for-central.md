# ADR 0030: Maven stays on 3.9.x for the Central upload

- Status: accepted (delegated technical decision)
- Date: 2026-10-12
- Amends: the Maven row of the pinned versions in [ADR 0012](0012-parsing-and-dependency-baseline.md).

## Context

Dependabot moved the Maven Wrapper from Maven 3.9.16 to 3.10.0 on 2026-10-10 (#6). Release run 38169177674 then
uploaded the 0.1.0 bundle to the Central Portal, and Central failed the deployment with:

```text
Bundle has content that does NOT have a .pom file: io/github/visplabs/tierwise-core, ... (every module)
```

central-publishing-maven-plugin 0.11.0, the latest release, stages each module by installing it into
`target/central-staging` with Maven's legacy `ArtifactInstaller`. Before it bundles, it deletes the
`maven-metadata-central-staging.xml` files that this install writes under Maven 3.9. Under Maven 3.10 the same install
writes `maven-metadata-local.xml` beside the version directories and `_remote.repositories` in them instead. The plugin
does not know those names, so they go into the bundle, and Central rejects a directory with no POM.

The same deploy, run locally against an unreachable upload address, gives a bundle with those files under 3.10.0
and a bundle without them under 3.9.16.

## Decision

The Maven Wrapper uses Maven 3.9.16 again, with its published SHA-256. Dependabot ignores `apache-maven` 3.10.0 and
later, so the bump does not come back by itself.

## Alternatives considered

- **Maven 3.9.16 (chosen).** It is the version the release pipeline was built and rehearsed with, and it builds a
  bundle that Central accepts.
- **Keep 3.10.0 and remove the files before the upload.** The plugin bundles and uploads in one goal, so there is no
  step in between. Building the bundle by hand and uploading it with the Portal API would replace the plugin with
  workflow code.
- **Upgrade the plugin.** 0.11.0 is the latest version on Maven Central on 2026-10-12.

## Consequences

- Lift the Dependabot ignore when a central-publishing-maven-plugin release handles Maven 3.10, and check the bundle
  contents with a local deploy against an unreachable `-DcentralBaseUrl` before a release.
- The `[3.9,)` enforcer range does not change: builds with 3.10 still work. Only the Central bundle is affected.
