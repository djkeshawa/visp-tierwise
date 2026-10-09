# ADR 0021: CLI, codegen and distribution choices

- Status: accepted (delegated technical decision)
- Date: 2026-10-09

| Topic | Decision |
| --- | --- |
| CLI output | Findings print as `file:line: severity code message`, plus the suggestion when there is one. `--format json` emits `{command, ok, counts, findings, …}`; `test` adds `examples` and `lint` adds `lintRan`. Exit codes: 0 ok, 1 validation failed, 2 usage error. |
| `lint` with engine findings | Prints a notice and exits 1 without running lint (LINT-11, FIND-5) |
| `compile` default output | Without `-o`, writes `bundle.json` in the current directory. `--provenance` writes `<stem>.provenance.json`, with `gitCommit` from `GITHUB_SHA` or git, and `createdAt` in UTC. |
| `explain naming/<name>` | Prints the `naming/*` family entry. Declared pattern descriptions need a project and are deferred. |
| Init templates | Kept in `java/tierwise-cli/templates/`, outside `src/main`, because they are example projects that name preset kinds |
| Codegen names | Java constants in `UPPER_SNAKE`, splitting at non-alphanumerics. TypeScript keeps names as string-literal unions. Collisions and names with no ASCII letter or digit fail with exit 1. The default Java package is `tierwise.generated`. `--package` is rejected with `--lang ts`. Codegen never deletes files. |
| Codegen types | Typed keys and features. FactSets stay untyped, and object values are `Map<String, Object>` or `Record<string, unknown>`. Typed facts are post-0.1. |
| Benchmarks | A hand-written nanoTime harness, because JMH is not an approved dependency. Results are in `docs/benchmarks.md`: p99 ≈ 5–7 µs in both languages. |
| Native image | Hand-maintained reachability metadata, built only in CI (`release-dry-run.yml`) for linux-x64, macOS-arm64 and windows-x64. It has not been verified locally because there is no GraalVM here. |
| Docker | Multi-stage build on Temurin 21 JDK and JRE images pinned by digest, running as a non-root user. The registry name waits for open question O2. |
| GitHub Action | A composite `action.yml` with `docker` and `jar` methods. A native method is added at release. |
