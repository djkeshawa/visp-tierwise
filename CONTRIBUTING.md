# Contributing to Tierwise

Tierwise is pre-release. Start with the [roadmap](docs/roadmap.md) to see the
current phase, and with the [implementation plan](docs/implementation-plan.md) for
its work items.

## Process

1. Open an issue that names the problem and the phase it belongs to.
2. Keep changes focused. Use the repository's scripts and package managers.
3. Write tests alongside the code. Read conformance vectors directly from
   `spec/conformance/`; never copy them.
4. Run the relevant checks and report the actual results in the pull request.
5. Sign off every commit (DCO) and request a maintainer review.

## Checks

Prerequisites: JDK 21 (a full JDK, not a runtime image), Node 22.13+ and pnpm
11.3.0. Maven is downloaded by the wrapper.

```sh
python3 scripts/check-foundation.py          # structure, Maven metadata, approved dependencies, action pins
(cd java && ./mvnw verify)                    # Java build: release 17/21, enforcer rules, reproducible JARs
pnpm --dir js install --frozen-lockfile
pnpm --dir js run check                       # JS workspace metadata and strict config
pnpm --dir js run build                       # ESM, CommonJS and type declarations
pnpm --dir js test                            # packaging tests
```

The toolchain and its pinned versions are recorded in [ADR 0012](docs/adr/0012-parsing-and-dependency-baseline.md).

## Developer Certificate of Origin

Contributions use the [Developer Certificate of Origin 1.1](https://developercertificate.org/).
No CLA is required.

```sh
git commit --signoff
```

This adds a trailer such as `Signed-off-by: Your Name <you@example.invalid>`. Sign
off only your own work. DCO sign-off is not the same as cryptographic commit
signing.

## Design and spec changes

- Non-trivial design decisions need a short ADR in `docs/adr/`. Each new dependency
  needs an ADR before it is added.
- Once Phase 1 is accepted, spec changes need an RFC: a pull request against
  `spec/` using the [RFC template](docs/rfc-template.md), with rationale,
  compatibility impact and conformance changes. Breaking changes need a major spec
  version.
- If a requirement is ambiguous, resolve it explicitly in an ADR. Never pick an
  interpretation silently.

## Genericity and scope

Production code uses only model names declared at runtime. Domain names belong in
samples and fixtures; kind names and naming patterns belong in presets. No
company-specific content belongs in this repository. Out-of-scope items are listed
in [requirements §10](docs/requirements.md#10-out-of-scope).

## Conduct and security

Follow [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Report vulnerabilities as
described in [SECURITY.md](SECURITY.md), never in a public issue.
