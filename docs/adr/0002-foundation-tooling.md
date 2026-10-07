# ADR 0002: Verify empty builds without installing dependencies

Status: provisional; tooling approval pending

## Context

The personal instructions require approval before adding dependencies. Phase 0
needs Java and JavaScript foundations, but no approval has arrived. The local
environment has Node, pnpm, Python, a stripped Java 21 runtime with compiler/JAR
modules, and an existing bundled TypeScript compiler. It has no Maven executable.

## Decision for review

Keep all five Maven modules and three pnpm packages free of declared dependencies
and keep JS packages private. Package versions start at `0.1.0-dev.0`; no
supported spec or bundle format is claimed. Pinned Maven plugins, a local
TypeScript compiler, MkDocs Material and API-reference tools remain pending.

Use small foundation adapters with installed compiler components. The Java
adapter normally uses `--release` for 17/21. An explicitly requested
`--foundation-source-target` mode is limited to package-info.java placeholders:
this stripped runtime has no ct.sym signatures, so source/target checks do not
establish Java 17 API compatibility. The JS adapter accepts an installed compiler
via `STRATA_TYPESCRIPT_PATH` or normal package resolution and emits ESM, CJS and
declarations; absent tooling fails loudly. These adapters do not replace the
reviewed Maven/pnpm release toolchain.

The empty JAR adapter uses the fixed `project.build.outputTimestamp` of
`2026-01-01T00:00:00Z`. This is artifact infrastructure, not a decision about
bundle metadata or hashing. Empty JS targets are ES2020 with strict checking.

Pin pnpm to the locally available 11.3.0. CI uses Node 22 for tooling; the planned
runtime minimum remains Node 20. Pin external Actions by verified commit SHA and
let Dependabot propose updates. Java CI validates the Maven descriptors and
compiles placeholders on a complete JDK 21. JS CI verifies workspace metadata;
full TypeScript CI awaits a pinned compiler. Hosted CI has not run.

## Consequences

Local empty compilation is possible without adding dependencies. Full reproducible
Maven builds and clean-machine TypeScript builds are still open Phase 0 items.
No runtime semantics, conformance, performance, docs generation or release
readiness follows from these checks. Foundation adapters should be retired or
integrated after normal build tooling is approved.
