# Release process

This is the owner's checklist from "nothing is claimed yet" to the first publish of Tierwise 0.1.0. The release is built by one workflow, [`.github/workflows/release.yml`](../.github/workflows/release.yml). It runs only when you start it, it is a dry run unless you turn that off, and a dry run needs no account and no secret.

The steps that need your accounts (1 to 7) cannot be done by a script, so they are written out as steps. Steps 8 to 14 use the workflow. [ADR 0022](adr/0022-release-tooling.md) fixes the tools and versions. The open questions O2, O3 and O4 in [open-questions.md](open-questions.md) are the first steps below. [release-0.1.0-checklist.md](release-0.1.0-checklist.md) tracks the acceptance criteria of requirements section 15; this page is only the mechanics of publishing.

**How this page was checked.** Every command that is shown with output was run in this checkout on 2026-10-09 (Linux, JDK 21, Node 22, pnpm 11.3.0). A command without output was not run here, and the text says why: it needs your account, it would publish something, or it needs a hosted runner. The dry run of the workflow has since run on GitHub (run `37995425223`, 2026-10-09, all non-publishing jobs green). Before that, what was run here is the shell of its `build` and `collect` jobs, step by step, in a copy of the tree at version 0.1.0, and the Maven release profile with a throwaway PGP key. The native, docker and publishing jobs were not run: Docker was not available here, and the rest needs a hosted runner or your secrets. Sigstore signing could not be run, because it needs a GitHub OIDC token.

## What the release publishes

| Where | What | Who publishes it |
| --- | --- | --- |
| Maven Central | `io.github.visplabs:tierwise-core`, `-cli`, `-spring-boot-starter`, `-spring-boot-starter-s3`, `-server`, `-server-s3`, `-openfeature` and the parent `tierwise-parent`. Each has a sources JAR, a Javadoc JAR, a CycloneDX SBOM (`-cyclonedx.json`), PGP signatures and Sigstore bundles. The CLI also has `-all` (the runnable fat JAR) and the server has `-exec` (the runnable Spring Boot JAR) | `publish-maven` uploads and Central validates. You press Publish in the portal (step 12) |
| npm | `@visplabs/evaluator`, `@visplabs/node`, `@visplabs/openfeature`, each with npm provenance | `publish-npm`, after you remove `"private": true` (step 9) |
| GitHub release | A draft release `v<version>` with the CLI and server JARs, the native binaries for Linux, macOS and Windows, the npm tarballs, every SBOM, the Maven signatures and `SHA256SUMS` | `github-release` creates the draft; you publish it (step 14) |

`tierwise-e2e` is a test module. It sets `maven.deploy.skip`, so the release profile builds no sources or Javadoc JAR for it and leaves it out of the Central bundle. `scripts/check-foundation.py` fails if a module outside its list of published modules does not set that property.

Not published by the workflow:

- **Docker images.** The CLI image (`docker/Dockerfile`) and the server image (`docker/server.Dockerfile`) are built and smoke-tested on the runner and never pushed, because no registry has been chosen. *(OPEN)* Pick one (for example `ghcr.io/visplabs`) and add a push job; the `action.yml` description of `image` already names `ghcr.io/visplabs/tierwise`.
- **The documentation site** (MkDocs, Javadoc, TypeDoc). ADR 0022 builds it in CI only.

## The release jobs

| Job | Runs in a dry run | What it does |
| --- | --- | --- |
| `prepare` | yes | Checks that `version` is SemVer. A dry run applies it to the checkout with `scripts/set-version.sh`. A real run requires the tag `v<version>` and committed files that already carry the version (`set-version.sh --check`) |
| `build` | yes | Foundation check, JavaScript check, build and tests, then the Java reactor with `verify -Pconformance,release` (all tests, the conformance suite, sources and Javadoc JARs, SBOMs), the Core and CLI tests on a JDK 17 runtime, the end-to-end script `scripts/e2e.sh`, the differential run, the fat JAR and the server JAR against the samples, the npm tarballs and the npm SBOMs |
| `reproducibility` | yes | `scripts/reproducibility-check.sh`: two clean clones, built in two directories, must give identical JARs, SBOMs, npm tarballs and sample bundles |
| `native` | yes | The GraalVM native CLI on Linux, macOS and Windows, run against the samples |
| `docker` | yes | Builds both images (no push), runs the CLI image against the samples, checks that both images run as `10001:10001`, starts the server image and reads `/openapi.yaml` |
| `collect` | yes | Downloads the results, writes `SHA256SUMS`, uploads the `tierwise-release-assets` artifact |
| `publish-maven` | no | Rebuilds unsigned and compares every JAR with the tested build, then runs `deploy -Prelease` with PGP and Sigstore signing on. Central validates the bundle and waits (`autoPublish` is false) |
| `publish-npm` | no | Runs after `publish-maven` succeeds. Rebuilds the tarballs, compares them with the tested build, refuses private packages, then `npm publish --provenance --access public` in dependency order |
| `github-release` | no | Runs after both. Creates a draft release with the notes from the `CHANGELOG.md` section of the version |

The three publishing jobs have `if: needs.prepare.outputs.publish == 'true'` and run in the `release` environment. Each of their steps also checks `env.DRY_RUN == 'false'` and that its secrets exist. A missing secret stops the job with a message before anything is sent. `scripts/check-foundation.py` (the CI "foundation" job) checks that these guards are present, that `dry_run` defaults to true, that only these three jobs use secrets or write permissions, and that every action is pinned by commit SHA.

## Step-by-step checklist

### 1. Claim the GitHub organisation (O2)

**Done on 2026-10-11.** The owner created the organisation `visplabs`, and the repository was transferred from `djkeshawa/visp-tierwise` to `visplabs/tierwise` with `gh api -X POST repos/djkeshawa/visp-tierwise/transfer -f new_owner=visplabs -f new_name=tierwise`. GitHub redirects the old URL. [ADR 0028](adr/0028-visplabs-publication-identity.md) records the identity that follows from it: the Maven group id `io.github.visplabs`, the Java packages `io.github.visplabs.tierwise.*` and the npm scope `@visplabs`.

After the transfer, private vulnerability reporting and the `release` environment (reviewer @djkeshawa, tags `v*`) were still in place. Secret scanning and push protection had been turned off by the transfer and were turned on again. Update an existing clone with `git remote set-url origin https://github.com/visplabs/tierwise.git`.

If the repository moves again, change the URLs before anything else. These files carry the URL:

```sh
grep -rln "github.com/visplabs/tierwise" --exclude-dir=node_modules --exclude-dir='target*' --exclude-dir=dist --exclude-dir=.git .
```

```text
docs/guides/cli-reference.md
docs/open-questions.md
docs/quickstart-java.md
docs/quickstart-js.md
docs/release-process.md
java/pom.xml
js/evaluator/package.json
js/node/package.json
js/openfeature/package.json
```

In the POM change `url`, `issueManagement` and the three `scm` URLs. In each `package.json` change `repository`, `homepage` and `bugs`. npm checks `repository.url` against the repository that runs the workflow when it validates provenance, so a mismatch makes `npm publish --provenance` fail. Moving to another organisation also changes the group id, because Central verifies `io.github.<name>` against the GitHub account `<name>` (step 5). That is an ADR, not a find-and-replace.

### 2. Claim the npm scope (O2)

1. Create an npm account if you have none, and turn on two-factor authentication for it.
2. At npmjs.com create an organisation named `visplabs` (Add Organization). The free plan allows public packages. This reserves the scope `@visplabs`.
3. Check that the packages are not taken. On 2026-10-11 the registry answered 404 for them:

   ```sh
   npm view @visplabs/evaluator
   ```

   ```text
   npm error code E404
   npm error 404 Not Found - GET https://registry.npmjs.org/@visplabs%2fevaluator - Not found
   ```

   A 404 here does not prove that the scope is free: the scope belongs to whoever owns the npm organisation `visplabs`, and npmjs.com blocked the automated check of the organisation page from this machine (HTTP 403). Create the organisation to settle it.
4. Create an access token for the release (Access Tokens; a granular token with write access to the `@visplabs` packages, valid for a limited time; npm changes its token types, so follow its current documentation). It becomes the `NPM_TOKEN` secret in step 6. Do not paste it anywhere else. *(OPEN)* npm "trusted publishing" would replace the token with the workflow's OIDC identity. The workflow does not use it yet, because it was not possible to test it without the packages.

### 3. Name the maintainers (O3)

**Done on 2026-10-10.** The owner, [@djkeshawa](https://github.com/djkeshawa), is the sole maintainer and holds release signing and publishing. `MAINTAINERS.md` lists the roles, and `java/pom.xml` has the `<developers>` entry Maven Central requires (no email address). Add a maintainer to both places, with their agreement, when one joins.

### 4. Set up the security and conduct contacts (O4)

`SECURITY.md` routes reports through GitHub private vulnerability reporting, and `CODE_OF_CONDUCT.md` routes conduct reports to the maintainer through GitHub. No email address is published. What remains:

1. **Done on 2026-10-11.** Private vulnerability reporting is enabled on the public repository (`gh api repos/visplabs/tierwise/private-vulnerability-reporting` answers `{"enabled":true}`, checked again after the transfer). GitHub offers it only on public repositories; on 2026-10-10 the same call answered 404 while the repository was private. Check that the maintainer is notified of a report.

### 5. Register the Maven Central namespace and create a token

1. Sign in at central.sonatype.com with the GitHub account that owns the organisation.
2. Register the namespace `io.github.visplabs` (Namespaces). Central verifies an `io.github.*` namespace by asking you to create a temporary public repository, with a key it shows you, in the matching GitHub account. Remove the repository when Central says the namespace is verified.
3. Generate a user token (your account menu, "View Account", "Generate User Token"). Central shows a username and a password once. They become `CENTRAL_USERNAME` and `CENTRAL_PASSWORD` in step 6. They are not your account password. The labels in the portal change from time to time; the Central documentation is the authority.

The workflow uploads with the server id `central`, which is what `central-publishing-maven-plugin` reads from `settings.xml`. `actions/setup-java` writes that file on the runner from the two secrets.

### 6. Create the release key, the `release` environment and the secrets

1. Create a PGP key that signs the Maven artifacts. Use a key that is only for releases. The command prompts for a passphrase:

   ```sh
   gpg --quick-generate-key "Tierwise Releases <address-you-control>" ed25519 sign 2y
   gpg --list-secret-keys --keyid-format long
   ```

   Both commands were run here with a throwaway key in a temporary `GNUPGHOME` (batch mode, with a passphrase given on the command line only because the key was thrown away). The throwaway key signed `tierwise-core` through the release profile, and `gpg --verify` reported a good signature. Nothing was published and the key and its directory were deleted. You should enter your passphrase interactively.
2. Publish the public key, so that Central and users can verify signatures. Not run here: it uploads a key.

   ```sh
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEYID>
   ```

   Central reads public keys from the key servers it supports. Check its documentation for the current list and use one of them.
3. Export the private key for the secret. Not run here: it prints a private key, which must go straight to the secret and nowhere else.

   ```sh
   gpg --armor --export-secret-keys <KEYID>
   ```

4. **Done on 2026-10-11.** The environment `release` exists with @djkeshawa as the required reviewer, so every publishing job waits for an approval, and its deployments are restricted to tags that match `v*`. (Required reviewers need a public repository or a paid plan: on 2026-10-10 the API refused them for the then-private repository with 422, "billing plan".) A job that uses `environment: release` gets the environment's secrets only after the approval.
5. Add these secrets to the **environment** (not to the repository):

   | Secret | Value |
   | --- | --- |
   | `CENTRAL_USERNAME`, `CENTRAL_PASSWORD` | The user token of step 5 |
   | `GPG_PRIVATE_KEY` | The armoured private key of this step |
   | `GPG_PASSPHRASE` | Its passphrase |
   | `NPM_TOKEN` | The token of step 2 |

   There is no secret for Sigstore. The `publish-maven` job signs with its own GitHub OIDC token (`id-token: write`), and the signature records the repository, the workflow and the tag.

### 7. Re-check the names on the registries

Phase 9 of the [roadmap](roadmap.md) asks for a final registry check before the first publish. On 2026-10-11 the Maven Central search for the group answered with no result:

```sh
curl -s "https://search.maven.org/solrsearch/select?q=g:io.github.visplabs&rows=1&wt=json" | python3 -c "import json,sys; print('numFound', json.load(sys.stdin)['response']['numFound'])"
```

```text
numFound 0
```

Run it, the `npm view` of step 2 and `npm view tierwise name` again on the day. Also search for trademarks of the name (this is a legal check no script can do).

### 8. Dry run

A dry run needs none of the previous steps. It can run at any time and as often as you like.

1. Start the workflow with `dry_run` on (the default). In GitHub: Actions, "Release", Run workflow, `version` = `0.1.0`. From a terminal with the GitHub CLI:

   ```sh
   gh workflow run release.yml --field version=0.1.0 --field dry_run=true
   ```

2. Open the run. Every job in the table above except the three publishing ones runs, and the three publishing ones are skipped. Download the `tierwise-release-assets` artifact: it holds what a real release would attach.
3. Run the same checks on your machine when you want a faster loop.

   Check that every place that carries a version agrees:

   ```sh
   scripts/set-version.sh --check
   ```

   ```text
   All versions agree: 0.1.0-dev.0
   ```

   Build everything the way the workflow does (about a minute), without tests:

   ```sh
   cd java && ./mvnw -B -ntp -DskipTests -Prelease verify
   ```

   The profile skips signing unless you pass `-Dtierwise.gpg.skip=false` and `-Dtierwise.sigstore.skip=false`, so this signs nothing and needs no secret.

   Check reproducibility. `--worktree` copies your working tree, so it also checks uncommitted changes. Without it, the script clones `HEAD`, which is what the workflow does. The clone mode was not run here, because the instructions for this work item forbade running git. The `--worktree` mode was run:

   ```sh
   scripts/reproducibility-check.sh --worktree
   ```

   ```text
   == Comparison
   OK: Java JARs and SBOMs: 31 files are byte-identical
   OK: sample bundles: 3 files are byte-identical
   OK: npm pack tarballs: 3 files are byte-identical

   Reproducible: both builds are byte-identical.
   ```

   The two copies sit in directories with names of different lengths. The full script output also lists the work directory and the build steps. It took about one minute here. On a difference it names each differing file and prints a listing diff of the archives, and it keeps the work directory.

What the check covers: all JARs (library, sources, Javadoc, fat CLI, executable server), every module SBOM, the npm tarballs and the three sample bundles. What it cannot cover: signatures. PGP and Sigstore signatures differ on every signing by design, so the workflow compares the unsigned JARs before it signs.

### 9. Make the npm packages publishable

**Done for 0.1.0.** The three packages were `"private": true` until the release commit. `npm publish` refuses a private package, and the `publish-npm` job stops with a message if it finds one. Do this on the release commit, after steps 2 and 6:

1. Remove the `"private": true` line from `js/evaluator/package.json`, `js/node/package.json` and `js/openfeature/package.json`. Keep it in `js/package.json` (the workspace root) and `js/spec-tools/package.json`, which are never published. Each package already has `publishConfig` (public access, provenance), `repository`, `homepage`, `bugs`, `keywords` and `files`.
2. Update the place that asserts the old state: `scripts/check-workspace.mjs` (`manifest.private`, "Phase 0 packages must not be publishable"). Run `pnpm --dir js run check` and `pnpm --dir js test`.
3. Update the `description` of `@visplabs/node` and `@visplabs/openfeature`, which still say "Phase 0 ... placeholder", because it is the first line users see on npm.

Each tarball also carries `LICENSE` and `NOTICE`. They live once, at the root; the workflow and the reproducibility script copy them into each package directory before `pnpm pack`. Without that step the tarballs would have neither (`files` lists them, and npm ignores a missing entry).

### 10. Bump the version and tag it

1. Set the version everywhere. The script prints each file it writes. This is the output of `scripts/set-version.sh 0.1.0` on a copy of the tree:

   ```text
   java/pom.xml: 0.1.0-dev.0 -> 0.1.0
   java/tierwise-cli/pom.xml: 0.1.0-dev.0 -> 0.1.0
   java/tierwise-core/pom.xml: 0.1.0-dev.0 -> 0.1.0
   java/tierwise-e2e/pom.xml: 0.1.0-dev.0 -> 0.1.0
   java/tierwise-openfeature/pom.xml: 0.1.0-dev.0 -> 0.1.0
   java/tierwise-server/pom.xml: 0.1.0-dev.0 -> 0.1.0
   java/tierwise-server-s3/pom.xml: 0.1.0-dev.0 -> 0.1.0
   java/tierwise-spring-boot-starter/pom.xml: 0.1.0-dev.0 -> 0.1.0
   java/tierwise-spring-boot-starter-s3/pom.xml: 0.1.0-dev.0 -> 0.1.0
   js/package.json: 0.1.0-dev.0 -> 0.1.0
   js/evaluator/package.json: 0.1.0-dev.0 -> 0.1.0
   js/node/package.json: 0.1.0-dev.0 -> 0.1.0
   js/openfeature/package.json: 0.1.0-dev.0 -> 0.1.0
   js/spec-tools/package.json: 0.1.0-dev.0 -> 0.1.0
   java/tierwise-server/src/main/resources/openapi.yaml: 0.1.0-dev.0 -> 0.1.0
   project-metadata.json: 0.1.0-dev.0 -> 0.1.0
   ```

   It rejects anything that is not SemVer (`v1` exits 2 with "not a SemVer version without build metadata: v1"), and `scripts/set-version.sh --check 0.1.0` then reports `All versions agree: 0.1.0`. It also sets the `info.version` of the server's OpenAPI document and `packageVersion` in `project-metadata.json`, which carry the same number. Check the diff.
2. In `CHANGELOG.md` rename "[0.1.0] - unreleased" to the release date. The workflow takes the notes of the draft release from the section whose heading starts with `## [0.1.0]`. Update `project-metadata.json` (`releaseStatus`), `SECURITY.md` ("Supported versions") and the README, where they say that nothing is released.
3. `project.build.outputTimestamp` in `java/pom.xml` is a fixed date (2026-01-01), which keeps every JAR timestamp equal across builds. You may set it to the date of the release commit; do it in this commit, so that all builds of the tag agree.
4. Run the dry run (step 8) once more on this commit, with the version `0.1.0`. A dry run on the tagged commit is the best rehearsal.
5. Commit, then create and push a signed tag. These are your git commands; they were not run here.

   ```sh
   git tag -s v0.1.0 -m "Tierwise 0.1.0"
   git push origin v0.1.0
   ```

### 11. Run the release

1. Start the workflow on the tag with `dry_run` turned **off**: Actions, "Release", Run workflow, choose the tag `v0.1.0` in "Use workflow from", `version` = `0.1.0`, untick the dry run. From a terminal (not run here):

   ```sh
   gh workflow run release.yml --ref v0.1.0 --field version=0.1.0 --field dry_run=false
   ```

   The `prepare` job fails at once if the ref is not the tag `v0.1.0`, or if any file in the tag carries another version.
2. The build, reproducibility, native, docker and collect jobs run as in a dry run.
3. Each publishing job waits for your approval of the `release` environment. Read the run summary first. Approve `publish-maven`.
4. `publish-maven` uploads the bundle to Central and waits until Central reports it as validated. It does not publish it.
5. Approve `publish-npm`, which starts only after step 4 succeeded. It publishes `@visplabs/evaluator`, then `@visplabs/node` and `@visplabs/openfeature`, with provenance. **npm releases cannot be replaced.** npm allows an unpublish for a short time after the publish under conditions; read its current policy before you rely on that.
6. `github-release` creates the draft release.

Central goes first and its upload is not final, so a validation failure stops the release before the npm publish. The two steps that cannot be undone are the npm publish, which the workflow does after your approval, and the Publish button in the Central Portal, which only you press.

### 12. Publish in the Central Portal

1. Open central.sonatype.com, Deployments. The deployment from this run has the status "Validated". If it is "Failed", the portal lists the rule that failed (a missing developer, a missing signature, a missing Javadoc JAR). Drop the deployment, fix the cause on a new commit, and run the release again from a new tag, because a tag is not moved.
2. Review the files. There should be eight coordinates: the parent POM and the seven modules. Each module has its JAR, sources JAR, Javadoc JAR, POM, SBOM, `.asc` signatures and `.sigstore.json` bundles.
3. Press Publish. **A Central release is permanent.** It cannot be deleted or replaced. Central usually syncs to `repo1.maven.org` within a short time; the search index can lag for hours.

### 13. Verify what was published

1. Maven Central. Not run here: nothing is published.

   ```sh
   curl -sI https://repo1.maven.org/maven2/io/github/visplabs/tierwise-core/0.1.0/tierwise-core-0.1.0.jar | head -1
   ```

2. Signatures. Download a JAR and its `.asc` and `.sigstore.json` from the draft release or from Central. PGP:

   ```sh
   gpg --verify tierwise-core-0.1.0.jar.asc tierwise-core-0.1.0.jar
   ```

   Sigstore, with cosign (not installed on the machine that wrote this page, so not run). The identity is the release workflow at the tag. Adjust the repository if it differs:

   ```sh
   cosign verify-blob \
     --bundle tierwise-core-0.1.0.jar.sigstore.json \
     --certificate-identity "https://github.com/visplabs/tierwise/.github/workflows/release.yml@refs/tags/v0.1.0" \
     --certificate-oidc-issuer https://token.actions.githubusercontent.com \
     tierwise-core-0.1.0.jar
   ```

   Check the exact identity string against the certificate in the bundle before you publish this command to users.
3. npm provenance. Install the package in a clean directory and check the registry signatures and provenance:

   ```sh
   npm install @visplabs/evaluator@0.1.0
   npm audit signatures
   ```

   The package page on npmjs.com shows a "Built and signed on GitHub Actions" badge when provenance is attached. Neither command was run here: no package exists yet.
4. `SHA256SUMS` in the release lists every file; compare it with `sha256sum -c SHA256SUMS` in a directory that holds the downloaded files in the same layout. The layout is `maven/`, `npm/`, `sbom/` and `native/`.

### 14. Publish the GitHub release and finish

1. Open the draft release `v0.1.0`. Read the notes (they are the CHANGELOG section), check the assets and press Publish release. The workflow creates a draft on purpose.
2. Announce the name (the reason for O2).
3. Strike O2, O3 and O4 through in `open-questions.md` and update the release checklist.
4. Push the Docker images once you have chosen a registry (see "Not published by the workflow").
5. Set `releaseStatus` in `project-metadata.json`, and start the `## [Unreleased]` section of the changelog again.

## If something goes wrong

| Symptom | Cause and fix |
| --- | --- |
| `prepare` fails: "A real release runs from the tag" | The workflow was started from a branch. Choose the tag in "Use workflow from" |
| `prepare` fails with `MISMATCH` lines | The files in the tag carry different versions. Run `scripts/set-version.sh <version>`, commit, and move to a new tag |
| `reproducibility` fails | A build step puts a time, a path or an unordered listing into an artifact. The job prints which file differs and a listing diff. Fix the cause; do not release |
| `publish-maven` stops with "Missing secrets" | Add the four secrets to the `release` environment (step 6) |
| Central says "Developers information is missing" | Add `<developers>` to `java/pom.xml` (step 3) |
| Central says a signature or public key is missing | The public key is not on a key server Central reads (step 6, part 2) |
| `publish-npm` stops: "is still private" | Step 9 |
| `npm publish` fails on provenance | The `repository.url` in `package.json` does not match the repository that runs the workflow (step 1, part 3), or the job lacks `id-token: write` |
| The Sigstore step fails | It needs the OIDC token of the job, so it cannot run on a fork or from a runner outside GitHub Actions. Check the job permissions |

## Reference

| File | Purpose |
| --- | --- |
| `.github/workflows/release.yml` | The release workflow described above |
| `.github/workflows/release-dry-run.yml` | The older dry run that builds only the JARs, the native binaries and the CLI image. `release.yml` includes all of it |
| `java/pom.xml` | The `release` profile, the plugin versions, the project metadata that Central requires |
| `java/tierwise-server/pom.xml` | The `repackage` execution that makes the `-exec` JAR |
| `docker/Dockerfile`, `docker/server.Dockerfile` | The CLI image and the server image, on the same pinned base images, running as UID 10001 |
| `scripts/set-version.sh` | Sets or checks the version everywhere |
| `scripts/reproducibility-check.sh` | The two-clean-builds comparison |
| `scripts/check-foundation.py` | Checks the release profile, the guards in `release.yml` and the pinned actions in CI |
| `pnpm --dir js run sbom` | Writes the CycloneDX SBOM of each npm package to `js/sbom/` (or to `SBOM_DIR`) |

The release profile of the POM, in one line: `-Prelease` adds sources and Javadoc JARs, SBOMs, PGP and Sigstore signing (each off unless `-Dtierwise.gpg.skip=false` or `-Dtierwise.sigstore.skip=false`) and the Central upload at `deploy`. Only a `deploy` with credentials for the server id `central` uploads anything. Run without credentials, it stops before it sends a byte:

```text
Unable to get publisher server properties for server id: central
```
