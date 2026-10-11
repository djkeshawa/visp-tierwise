# Tierwise

Tierwise is a type checker and evaluator for layered business configuration. You
declare your own levels, keys and decisions. A merge rule on every key, explicit
all/any over collections, and provenance on every answer make sure the result
means one thing, the same thing, in Java and in JavaScript.

> **Status: feature-complete; release 0.1.0 pending owner steps.** The specification
> (version 0.1) and the bundle format (version 1) are implemented in Java and
> JavaScript, and both evaluators pass the shared conformance suite. Every component
> in scope exists, and its tests pass locally, including the cross-language end-to-end
> test. The release is not done. Hosted CI and the nightly
> differential run have not run, the native binaries and the Docker image are not
> built, the quickstarts are not yet verified on a clean machine, and nothing is
> published. The [release checklist](docs/release-0.1.0-checklist.md) lists each
> acceptance criterion and its status.

## The problem

Applications combine values from several levels: platform → tenant → region →
store → user, or plan → org → workspace → user. Without a shared schema and explicit
merge rules, different readers get different answers:

- Misspelled names silently remove restrictions.
- Rules over collections leave "all" versus "any" unclear.
- Nobody can explain where a value came from.

Tierwise gives every key, fact and rule exactly one meaning, defined once and
enforced at build time. Mistakes fail CI, not production, and every answer explains
itself.

## When to use Tierwise

| If you need… | Use |
| --- | --- |
| Release toggles, experiments, kill switches | A feature-flag tool. Tierwise can sit behind OpenFeature as a provider. |
| Authorization: who may do what to which resource | Cedar or OPA |
| A general-purpose configuration language | CUE, Pkl or Jsonnet |
| Typed configuration across levels you define, with a merge rule per key; entity-aware decision tables with explicit all/any; provenance for every answer; Java and JavaScript | **Tierwise** |

## How it works

```
config repo (git)  ──►  tierwise validate · test · compile  ──►  bundle.json (canonical, content-hashed)
                                                                    │
                                    ┌───────────────────────────────┤
                                    ▼                               ▼
                         Java services (in-process)     Node / browser (in-process; browser = display only)
```

- **Per-key merge strategies:** `override`, `deep_merge`, `all`, `any`,
  `intersect`, `union`, `min`, `max`.
- **Explicit node admission:** typos in node IDs are rejected or explicitly ignored,
  never silently accepted.
- **Entity-aware decisions** with explicit `all_in` / `any_in` quantifiers over
  child collections.
- **Provenance:** every result names each contributing scope, the selected binding,
  the matching rule and the bundle version.
- **Canonical bundles:** the same sources give the same bytes, and the same bundle
  gives the same answer in every evaluator.
- **One spec, two implementations** sharing one conformance suite.

**Browser evaluation is for display only.** Client-side results can be tampered
with. Enforce decisions on the server.

## Quickstarts

- [Java quickstart](docs/quickstart-java.md)
- [JavaScript quickstart](docs/quickstart-js.md)

Both are written for the release. They are not yet verified on a clean machine, and
each one is meant to take under five minutes.

## Modules

| Module | Runtime | What it does | Status |
| --- | --- | --- | --- |
| [`java/tierwise-core`](java/tierwise-core) | Java 17 | Sources, checks, lint, compilation to bundles, the bundle reader, `resolve`, `decide` and provenance | Tested; conformance suite passes |
| [`java/tierwise-cli`](java/tierwise-cli) | Java 17 | The `tierwise` command: `init`, `validate`, `lint`, `test`, `compile`, `explain`, `codegen` | Fat JAR built and run; native binaries and Docker image not built |
| [`java/tierwise-spring-boot-starter`](java/tierwise-spring-boot-starter) | Java 21, Spring Boot 4 | Bundle loading and refresh with last-known-good, `Resolver` and `DecisionEngine` beans, Micrometer metrics | Tested |
| [`java/tierwise-spring-boot-starter-s3`](java/tierwise-spring-boot-starter-s3) | Java 21 | Optional S3 bundle source | Tested against a fake S3 client; not yet against S3 |
| [`java/tierwise-server`](java/tierwise-server) | Java 21 | Control plane: publish, history, promote, `/v1/resolve`, `/v1/decide`, OpenAPI | Tested |
| [`java/tierwise-server-s3`](java/tierwise-server-s3) | Java 21 | Optional S3 bundle store | Tested against an in-process fake |
| [`java/tierwise-openfeature`](java/tierwise-openfeature) | Java 17 | OpenFeature provider over the core | Tested |
| [`js/evaluator`](js/evaluator) (`@visporg/evaluator`) | Node 20+, browsers | `loadBundle`, `resolve`, `decide`; no runtime dependencies | Tested; conformance suite passes |
| [`js/node`](js/node) (`@visporg/node`) | Node 20+ | Bundle loader with ETag refresh, atomic swap and last-known-good | Tested |
| [`js/openfeature`](js/openfeature) (`@visporg/openfeature`) | Node 20+, browsers | Server and web OpenFeature providers | Tested |
| [`js/spec-tools`](js/spec-tools) | Node 22+ | Private: checks the spec suite and canonicalises content | Tested |

Version 0.1.0 is the first release. The Maven artifacts use the group
`io.github.visplabs`, and the npm scope is `@visporg`.

## Repository

| Path | Contents |
| --- | --- |
| `spec/` | The normative specification (`spec.md` and its sections), error codes, JSON Schemas and the conformance suite |
| `presets/` | Built-in presets `tierwise:recommended`, `tierwise:minimal` and `tierwise:entitlements` |
| `samples/` | `commerce` and `retail-pos` (two unrelated domains) and `saas-entitlements` (a single-file project) |
| `docker/`, `action.yml` | The Docker images for the CLI and the server, and the GitHub Action (none built or run yet) |
| `java/tierwise-e2e` | Test only: the cross-language end-to-end test (compile, publish, Java and Node services, promote and roll back). It publishes nothing. |
| `docs/` | [Vision](docs/vision.md), [requirements](docs/requirements.md), [roadmap](docs/roadmap.md), [ADRs](docs/adr/), [CLI reference](docs/guides/cli-reference.md), [modeling guide](docs/guides/modeling.md), [benchmarks](docs/benchmarks.md), [open questions](docs/open-questions.md), [release checklist](docs/release-0.1.0-checklist.md) |
| `scripts/`, `.github/workflows/` | Checks, the differential run, the release scripts, and the CI, nightly, release dry-run and release workflows. The release workflow defaults to a dry run and has never run. |

## Versions

| What | Version |
| --- | --- |
| Specification (`specVersion`) | `0.1` |
| Bundle format (`bundleFormatVersion`) | `1` |
| Packages | `0.1.0-dev.0`, private and unpublished; release 0.1.0 pending |

## Performance

On the measuring machine, in-process `resolve` and `decide` take about 4 to 7 µs at
p99 on a bundle of 1,000 keys and 50 features. The target is under 100 µs, excluding
providers. The method and the numbers are in [benchmarks](docs/benchmarks.md).

## Verify this checkout

These are the commands that CI runs for the Java reactor and the JavaScript packages.
The Java build needs a full JDK 21, with `javac` and the `ct.sym` file that
`--release 17` uses.

```sh
cd java && JAVA_HOME=/path/to/jdk-21 ./mvnw -B -ntp verify -Pconformance
```

```sh
pnpm --dir js install --frozen-lockfile && pnpm --dir js run build && pnpm --dir js test
pnpm --dir js run genericity
```

On 2026-10-09 the Java reactor built with no test failure (3,161 tests run, including the two end-to-end tests, and 2
skipped by design). The JavaScript tests passed (1,114 tests). The [release checklist](docs/release-0.1.0-checklist.md)
records the full run.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) and [GOVERNANCE.md](GOVERNANCE.md).
Contributions use DCO sign-off under Apache-2.0. Each non-obvious decision is recorded
as an ADR in [docs/adr/](docs/adr/). Changes to the frozen 0.1 specification go
through an [RFC](docs/rfc-template.md).

## Security

Report vulnerabilities privately through GitHub's private vulnerability reporting,
as described in [SECURITY.md](SECURITY.md). Enforce every decision on the server,
never in the browser.

## License

[Apache-2.0](LICENSE)
