# Phase 0 review

Status: foundation scaffold ready for review; acceptance still pending.

Update on 7 October 2026: the user authorized applying the DOCX prompt review
recommendations. The [amended brief](build-brief-2026-10-07.md), corrected commerce excerpts,
[coverage record](recommendations-review.md) and ADR 0004 record those changes.
This authorization resolves the listed recommendations, not all remaining
foundation or semantic decisions. The validation table below records the original
foundation checks; the recommendation update has separate source/document checks.

The user subsequently delegated technical clarification to Claude Opus 5.5 at
xhigh effort. The completed [consultation](claude-consultation.md) and
[ADR 0005](../adr/0005-node-admission-and-canonical-artifacts.md) resolve runtime
unknown-node skipping and canonical artifact/provenance separation. They do not
accept Phase 0 or add runtime code; remaining technical questions will use the
same delegated consultation process.

The brief explicitly requires a stop and review after each phase. No Phase 1
specification, runtime engine, validator, compiler, CLI, server or integration
has been implemented. Placeholder source files only establish empty build targets.

## Delivered

- Five-module Maven layout and three-package pnpm workspace with strict ES2020
  TypeScript settings, ESM/CJS/type output paths and private prerelease packages.
- Apache-2.0 LICENSE and NOTICE, project/community/security/governance files,
  changelog, issue/PR templates and a documentation-site configuration.
- Java foundation CI, JS metadata CI, pinned Actions, Dependabot configuration
  and an OpenSSF Scorecard workflow. No remote workflow has run.
- Reserved spec/schema/conformance/preset/sample directories. Commerce now has
  corrected schema excerpts from the reviewed brief; complete schemas, presets
  and conformance content remain Phase 1 work.
- ADRs for phase boundaries, tooling, identity and provisional community policy.
- A complete list of detected semantic questions in `open-questions.md`.

## Validation

Verified on 7 October 2026:

| Check | Actual result |
|---|---|
| `python3 scripts/check-foundation.py` | Passed: required files, XML reactor metadata, action SHA pins |
| `pnpm --dir js install --offline --frozen-lockfile --ignore-scripts` with a writable store under `../work/` | Passed with pnpm 11.3.0; no dependencies installed |
| `pnpm --dir js run check` | Passed: all three private packages and strict compiler configuration |
| `pnpm --dir js run build` with existing `STRATA_TYPESCRIPT_PATH` | Passed using installed TypeScript 6.0.3: ESM, CJS and declarations for all packages |
| `node scripts/build-java.mjs --foundation-source-target` | Passed for all five empty placeholders; class targets 17/21, fixed JAR timestamps and repeatable JAR hashes |
| Normal `node scripts/build-java.mjs` | Failed: this stripped Java runtime lacks ct.sym release signatures; Java 17 API compatibility unverified |
| JSON/YAML parsing with existing tooling | Passed: all 18 source/workflow configuration files parsed |
| `mvn ... validate` / `mvn ... verify` | Not run: Maven executable absent; pinned lifecycle tooling pending approval |
| Hosted Java/JS CI, docs/API generation and release workflows | Not run; no remote or publication exists |

The default pnpm store was not writable in the sandbox; verification used an
explicit writable store under the chat's `work/` folder. Foundation checks and
empty builds establish scaffolding only; they are not evaluator or conformance
tests. No new dependencies were installed or downloaded.

## Every decision not dictated by the brief

| Decision | Status / rationale |
|---|---|
| Local `strata/` folder and `main` Git branch | Provisional; no remote or commit |
| `org.strata` / `@strata` coordinates | Unclaimed placeholders; availability unverified |
| `0.1.0-dev.0` private prerelease packages | No package publication or spec support claimed |
| No supported spec/bundle format versions yet | Phase 1 has not been accepted |
| pnpm 11.3.0 and Node 22 for CI tooling | Existing local package-manager version; runtime Node 20 remains planned |
| Installed-compiler foundation adapters | No new dependencies; normal pinned build toolchain pending |
| Explicit placeholder-only Java source/target check | Local runtime lacks release signatures; no Java 17 API compatibility claim |
| Fixed empty-JAR timestamp | Infrastructure only; canonical bundle design is now recorded in ADR 0005, without runtime implementation |
| SHA-pinned Actions, Ubuntu 24.04, read-only CI token | Reproducible action references and limited CI permissions |
| JS CI initially checks metadata | Full compiler job awaits tooling approval |
| Contributor Covenant 2.1 by reference | Version pin and unassigned enforcement contact; review ADR 0001 |
| Unassigned maintainers and private reporting channels | No invented identities or public vulnerability destination |
| Governance recusal and leaving disputes unresolved | Provisional; appointment/removal criteria and tie resolution still open |
| Product quickstarts marked unavailable | APIs do not exist; runnable instructions follow implementation |
| Source archive for review | Generated output and local Git metadata excluded |

Section 14 defaults are documented as proposed: DCO, maintainers/RFC governance,
MkDocs Material, Java 17 core/Java 21 integrations, S3/filesystem storage,
30-second refresh and 5-minute staleness alert. Semantics outside Section 14 have
no default approval and remain open questions.

## Remaining Phase 0 acceptance items

1. Confirm final identity, GitHub ownership, maintainers and private contacts.
2. Approve and pin TypeScript/Maven build tooling, then run complete Maven/pnpm
   empty builds on a clean toolchain. Documentation dependencies may follow later.
3. Run the workflows in the eventual repository; require green Java/TypeScript
   compiler jobs. Local checks cannot establish hosted CI status.
4. Review the semantic questions and approve Phase 0 before Phase 1 starts.

Release signing, CycloneDX SBOMs, native CLI packaging, Docker/GitHub Action
distribution, generated API references and runnable five-minute quickstarts are
later-phase deliverables. Their implementation and verification are not claimed.
