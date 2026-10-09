# tierwise-cli

The `tierwise` command line (Java 17, [picocli](https://picocli.info)). It is a thin layer over
`tierwise-core`: the commands report the findings of the core and decide none of them.

```
tierwise init [--preset recommended|minimal|entitlements] [DIR]
tierwise validate [DIR] [--format text|json]
tierwise lint     [DIR] [--format text|json]
tierwise test     [DIR] [--format text|json]
tierwise compile  [DIR] [-o bundle.json] [--provenance]
tierwise explain  [CODE|RULE]
tierwise --version
```

`DIR` is the project directory that holds `tierwise.yaml` and defaults to the current directory.

| Command | What it does |
| --- | --- |
| `init` | Writes `tierwise.yaml` for the chosen preset (default `recommended`). The project validates with zero findings. It never overwrites an existing manifest. |
| `validate` | Engine checks, examples and lint, every finding. |
| `lint` | Lint findings only. Lint runs only when the project has no engine finding ([LINT-11]); otherwise the command says so and exits with 1. |
| `test` | Validation plus a `pass` / `FAIL` line for each example. Examples are not run when another engine finding exists ([POL-47]). |
| `compile` | Writes the canonical bundle (default `bundle.json` in the current directory) when the validation does not fail. `--provenance` also writes `<stem>.provenance.json`: `bundleVersion`, `createdAt` (RFC 3339 UTC), and `gitCommit` from `GITHUB_SHA` or `git rev-parse HEAD` when it is a full commit identifier. An existing sidecar is never replaced ([BUNDLE-147]). |
| `explain` | Prints the entry of `spec/error-codes.md` for an error code or a lint rule. Without an argument it lists them all. |

A finding prints as `file:line: severity code message (suggestion: name)`. With `--format json`,
`validate`, `lint` and `test` write one JSON document on standard output (`command`, `ok`, `counts`,
`findings`, and for `test` `examples`), for CI annotations. Summaries go to standard error.

Exit codes: `0` success; `1` a finding has severity `error`, an example failed, or `explain` knows no such
code; `2` a usage error or an I/O failure.

URL presets in a manifest are fetched over `https` by this command line only; the core opens no connection
and checks the digest ([MANIFEST-25] to [MANIFEST-29]).

## Build

`./mvnw verify` builds the plain jar and the runnable fat jar `tierwise-cli-<version>-all.jar`
(`java -jar tierwise-cli-<version>-all.jar validate samples/commerce`). The build copies
`spec/error-codes.md` into the jar for `explain`. The projects that `init` writes are in
[`templates/`](templates), next to the module rather than in its sources, because they are example
projects that name the kinds and keys of the presets.

`tierwise codegen --lang java|ts` generates typed key and feature handles (see the CLI reference in `docs/guides/`).
