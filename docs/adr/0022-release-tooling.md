# ADR 0022: Release and documentation tooling

- Status: accepted. These are the release-phase tools in ADR 0012's "later" row: MkDocs Material, TypeDoc, CycloneDX and Sigstore, plus the Maven plugins publishing needs.
- Date: 2026-10-09

| Purpose | Tool and version | Notes |
| --- | --- | --- |
| Javadoc and sources JARs | maven-javadoc-plugin 3.12.0, maven-source-plugin 3.4.0 | Maven Central requires both |
| Maven Central publishing | central-publishing-maven-plugin 0.11.0 | Used only in a `release` profile, with `autoPublish=false` |
| Signing | maven-gpg-plugin 3.2.8 (Central requires PGP) and sigstore-maven-plugin 2.3.0 (Sigstore bundles); npm `--provenance` | Signing keys come only from release secrets. The dry run signs nothing. |
| Executable server | spring-boot-maven-plugin (Spring Boot BOM version) | Repackages `tierwise-server` and gives it a Docker image |
| JS SBOM | @cyclonedx/cyclonedx-npm 6.0.1 (dev) | Java SBOMs already use cyclonedx-maven-plugin |
| Docs site | mkdocs-material 9.7.7 (pip, pinned in `docs/requirements-docs.txt`) | Built in CI only |
| TypeScript API reference | typedoc 0.28.20 with typescript 6.0.3 in a separate tool project, `docs/tools/typedoc/` | TypeDoc needs the TypeScript compiler API, which the native TypeScript 7 doesn't provide. The packages still build with TypeScript 7. |

Packages stay `private` (npm), and nothing is published until the owner settles open questions O2–O4 and runs the release workflow with dry-run turned off.
