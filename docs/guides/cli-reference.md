# CLI reference

The `tierwise` command line validates, tests and compiles a Tierwise project, explains its findings and generates
typed handles for your code. It is a thin layer over `tierwise-core`: the commands report the findings of the core
and decide none of them. Every command takes a project directory (the directory that holds `tierwise.yaml`), which
defaults to the current directory.

The help text in this page is the output of `--help`, copied from the build of the working tree. The commands were run
on that build. The projects in the examples are the samples of the repository (`samples/commerce`,
`samples/saas-entitlements`) and the template of `tierwise init`; their names are examples.

## Running it

| How | Command | Needs |
| --- | --- | --- |
| Fat JAR | `java -jar tierwise.jar <command>` | Java 17 or later. One file with its dependencies inside, `tierwise-cli-<version>-all.jar` |
| Docker image | `docker run --rm --user "$(id -u):$(id -g)" --volume "$PWD:/work" tierwise:local <command>` | Docker. The image has a JRE 21 and runs as a non-root user; the project path is relative to `/work` |
| GitHub Action | `uses: <owner>/tierwise@<sha>` with `command: validate` or `compile` | A GitHub Actions runner. Runs the Docker image, or a released JAR checked against a SHA-256 you give it |
| Native binary | `tierwise <command>` | Nothing. Built in the release dry run for Linux, macOS and Windows |

*(OPEN)* Nothing is published, so there is no download address and no image name yet. They depend on the GitHub
organisation that the project owner has not claimed (open question O2 in [open questions](../open-questions.md)). Build
the JAR from a checkout (`cd java && ./mvnw -B -ntp -DskipTests package`) and find it at
`java/tierwise-cli/target/tierwise-cli-<version>-all.jar`. The page uses `java -jar tierwise.jar` for the command
line, as the quickstarts do.

The Docker image, the GitHub Action and the native binary are described in `docker/README.md`, `action.yml` and the
`native` profile of `java/tierwise-cli/pom.xml`. They were not run for this page, because no Docker daemon or GraalVM
was available; the JAR was.

## Exit codes

| Code | Meaning |
| --- | --- |
| `0` | Success. No finding has the severity `error`, and every example passed |
| `1` | A finding has the severity `error`, an example failed, `lint` was asked to run on a project with an engine finding, or `explain` knows no such code |
| `2` | A usage error (an unknown command or option, a bad option value) or an I/O failure (no such directory, a file that exists) |

## Output

A finding prints on standard output as `file:line: severity code message`, followed by `(suggestion: name)` when the
core has one. A summary line goes to standard error. A finding names the clauses of the specification that define the
check, such as `(KEY-10, SRC-35)`.

```text
tierwise.yaml:57: error E_UNKNOWN_KEY the key quota.max_itmes is not declared (KEY-10, SRC-35) (suggestion: quota.max_items)
validate: 1 error, 0 warnings, 0 infos
```

With `--format json`, `validate`, `lint` and `test` write one JSON document on standard output and keep the summary on
standard error. It is meant for CI annotations:

| Member | In | Meaning |
| --- | --- | --- |
| `command` | all | `validate`, `lint` or `test` |
| `ok` | all | `true` when no finding has the severity `error` |
| `counts` | all | `{ "error": n, "warn": n, "info": n }` |
| `findings` | all | The findings: `code`, `severity`, `file`, `path` (the YAML path), `line`, `message` and, when there is one, `suggestion` |
| `examplesRan` | `test` | Whether the examples were run. They are not run when another engine finding exists |
| `examples` | `test` | One entry per example: `file`, `path`, `line`, `name` and `result` (`pass` or `fail`) |
| `lintRan` | `lint` | Whether lint ran. It runs only when the project has no engine finding |

**Example (non-normative):** a project with one engine error.

```json
{"command":"validate","ok":false,"counts":{"error":1,"warn":0,"info":0},"findings":[{"code":"E_UNKNOWN_KEY","severity":"error","file":"values/global.yaml","path":["checkout.pickup.leed_hours"],"line":9,"message":"the key checkout.pickup.leed_hours is not declared (KEY-10, SRC-35)","suggestion":"checkout.pickup.lead_hours"}]}
```

Findings are listed in a fixed order, so two runs on the same project print the same text.

## tierwise

```text
Usage: tierwise [-hV] COMMAND
Tierwise: validate, test and compile a project of layered settings.
  -h, --help      Show this help message and exit.
  -V, --version   Print version information and exit.
Commands:
  init      Write a minimal, valid project that extends a built-in preset.
  validate  Validate a project: engine checks, examples and lint. Exit code 1
              if any finding is an error.
  lint      Report lint findings only. Lint runs only when the project has no
              engine finding.
  test      Validate a project and report each example as passed or failed.
  compile   Validate a project and write its canonical bundle.
  explain   Explain an error code or a lint rule, for example E_UNKNOWN_KEY or
              values/no-op. Without an argument, list them all.
  codegen   Generate typed key and feature handles for Java or TypeScript.
```

`tierwise --version` prints the version, for example `tierwise 0.1.0`.

## tierwise init

```text
Usage: tierwise init [-hV] [--preset=PRESET] [DIR]
Write a minimal, valid project that extends a built-in preset.
      [DIR]             The directory to write the project into; created if
                          missing (default: the current directory).
  -h, --help            Show this help message and exit.
      --preset=PRESET   The built-in preset to extend: RECOMMENDED, MINIMAL,
                          ENTITLEMENTS (default: recommended).
  -V, --version         Print version information and exit.
```

Writes `tierwise.yaml` for the chosen preset, in the directory `DIR`, which is created if it is missing. The project is
valid: it validates with no finding. The default preset is `recommended`; the names are `recommended`, `minimal` and
`entitlements`, in either letter case. `init` never overwrites an existing manifest; it exits with 2 instead.

```console
$ java -jar tierwise.jar init --preset entitlements my-config
created my-config/tierwise.yaml (preset: entitlements)
next: tierwise validate my-config
$ java -jar tierwise.jar init --preset entitlements my-config
tierwise: my-config/tierwise.yaml already exists; init never overwrites a project
```

## tierwise validate

```text
Usage: tierwise validate [-hV] [--format=FORMAT] [DIR]
Validate a project: engine checks, examples and lint. Exit code 1 if any
finding is an error.
      [DIR]             The project directory, which holds tierwise.yaml
                          (default: the current directory).
      --format=FORMAT   Report format: TEXT, JSON. Use json for CI tooling
                          (default: text).
  -h, --help            Show this help message and exit.
  -V, --version         Print version information and exit.
```

Runs every check: the engine checks, the examples of the rules files and the lint rules. It prints every finding, of
every severity. It exits with 1 when a finding is an error.

```console
$ java -jar tierwise.jar validate samples/commerce
validate: no findings
```

## tierwise lint

```text
Usage: tierwise lint [-hV] [--format=FORMAT] [DIR]
Report lint findings only. Lint runs only when the project has no engine
finding.
      [DIR]             The project directory, which holds tierwise.yaml
                          (default: the current directory).
      --format=FORMAT   Report format: TEXT, JSON. Use json for CI tooling
                          (default: text).
  -h, --help            Show this help message and exit.
  -V, --version         Print version information and exit.
```

Reports only the lint findings. Lint runs only when the project has no engine finding, because a lint rule judges a
well-formed project. If the project has one, `lint` says so and exits with 1:

```console
$ java -jar tierwise.jar lint broken
lint: lint did not run, because the project has 1 engine finding(s). Run `tierwise validate` to see them.
```

A lint finding is reported at the severity the presets and the manifest set for its rule. A project whose lint
findings are all warnings exits with 0:

```console
$ java -jar tierwise.jar lint audit-sample
tierwise.yaml:64: warn schema/description-required the declaration of audit_log.enabled has no description
lint: 0 errors, 1 warning, 0 infos
```

## tierwise test

```text
Usage: tierwise test [-hV] [--format=FORMAT] [DIR]
Validate a project and report each example as passed or failed.
      [DIR]             The project directory, which holds tierwise.yaml
                          (default: the current directory).
      --format=FORMAT   Report format: TEXT, JSON. Use json for CI tooling
                          (default: text).
  -h, --help            Show this help message and exit.
  -V, --version         Print version information and exit.
```

Validates the project and prints a `pass` or `FAIL` line for each example, then the findings. The examples are the
`examples` of the rules files: a context, a FactSet and the expected effect. They do not run when another engine
finding exists. A failed example is the engine error `E_EXAMPLE_FAILED`, so `validate` reports it too.

```console
$ java -jar tierwise.jar test samples/commerce
pass rules/checkout.invoice_release.yaml:41: A pending order holds the invoice under the root binding
pass rules/checkout.invoice_release.yaml:45: Paid orders release the invoice under the root binding
pass rules/checkout.invoice_release.yaml:49: Pending and paid orders release the invoice at division south
pass rules/checkout.invoice_release.yaml:53: A failed payment holds the invoice at division south
pass rules/checkout.invoice_release.yaml:57: An invoice with an empty order list is held
pass rules/checkout.invoice_release.yaml:61: An invoice with no order data is held
pass rules/checkout.order_submission.yaml:52: Mixed basket on credit hold is denied
pass rules/checkout.order_submission.yaml:56: Credit hold with only allowed stock is allowed
pass rules/checkout.order_submission.yaml:60: Empty order on credit hold is denied
pass rules/checkout.order_submission.yaml:64: Unknown credit status is denied
pass rules/checkout.order_submission.yaml:68: North allows everything
pass rules/checkout.order_submission.yaml:72: Good credit on the web channel is allowed under the root binding
test: 12 examples, 12 passed, 0 failed
test: no findings
```

When an example fails, the `FAIL` line and the error say why, and the exit code is 1:

```text
FAIL rules/checkout.order_submission.yaml:52: Mixed basket on credit hold is denied
rules/checkout.order_submission.yaml:55: error E_EXAMPLE_FAILED the example "Mixed basket on credit hold is denied" expects ALLOW but the decision is DENY (POL-41)
test: 12 examples, 11 passed, 1 failed
test: 1 error, 0 warnings, 0 infos
```

## tierwise compile

```text
Usage: tierwise compile [-hV] [--provenance] [-o=FILE] [DIR]
Validate a project and write its canonical bundle.
      [DIR]           The project directory, which holds tierwise.yaml
                        (default: the current directory).
  -h, --help          Show this help message and exit.
  -o, --output=FILE   The bundle file to write (default: bundle.json in the
                        current directory).
      --provenance    Also write <stem>.provenance.json beside the bundle:
                        bundleVersion, createdAt and, when known, gitCommit
                        (from GITHUB_SHA or git).
  -V, --version       Print version information and exit.
```

Validates the project and, when no finding is an error, writes the canonical bundle. Without `-o`, the file is
`bundle.json` in the current directory. If a finding is an error, no bundle is written and the command exits with 1.

```console
$ java -jar tierwise.jar compile samples/commerce -o commerce.json
wrote commerce.json (5172 bytes, sha256:ab1cd3a96be4ab7569ccaba61cb65dbd14b5efd8239de35f16643ab791af6859)
```

The same sources always give the same bytes and the same `bundleVersion`, which is the SHA-256 digest of the content.
Provenance never changes them.

`--provenance` also writes `<stem>.provenance.json` beside the bundle, where `<stem>` is the bundle file name without
`.json`. It holds `bundleVersion`, `createdAt` (an RFC 3339 time in UTC) and `gitCommit` when it is known: from
`GITHUB_SHA`, or from `git rev-parse HEAD` when that is a full commit identifier. An existing sidecar is never
replaced; the command exits with 2 instead.

```console
$ java -jar tierwise.jar compile samples/saas-entitlements --provenance -o saas.json
wrote saas.json (1459 bytes, sha256:836c45a782d4c0beb15052f381ec1a7eb4a507b20ce3885d049d3ecb3f5a3c51)
wrote saas.provenance.json
```

URL presets in a manifest (`extends` with an `https` address and a SHA-256) are fetched by this command line only. The
core opens no connection, and the digest is checked before the preset is used.

## tierwise explain

```text
Usage: tierwise explain [-hV] [CODE|RULE]
Explain an error code or a lint rule, for example E_UNKNOWN_KEY or
values/no-op. Without an argument, list them all.
      [CODE|RULE]   An engine or evaluation error code, or a lint rule
                      identifier.
  -h, --help        Show this help message and exit.
  -V, --version     Print version information and exit.
```

Prints the catalogue entry of an error code or a lint rule: what it means, when it is raised, a minimal example and how
to fix it. Without an argument it lists every code and rule, one per line. The text is the specification's
`error-codes.md`, built into the JAR. A name that is not in the catalogue exits with 1.

````console
$ java -jar tierwise.jar explain E_QUANTIFIER_REQUIRED
E_QUANTIFIER_REQUIRED
**Class:** engine
**Meaning:** A child fact is tested without a quantifier.
**Raised when:**
- an `eq` or `in` condition is used on a child fact ([RULE-13], [FACT-14]).

**Minimal example (non-normative):**
```yaml
when:
  entity_b.fact_y: { in: [1, 2] }        # fact_y is a child fact
```
**Fix:** Use `all_in` or `any_in` for a child fact.
````

## tierwise codegen

```text
Usage: tierwise codegen [-hV] --lang=java|ts [-o=DIR] [--package=PKG] [DIR]
Generate typed key and feature handles for Java or TypeScript.
      [DIR]            The project directory, which holds tierwise.yaml
                         (default: the current directory).
  -h, --help           Show this help message and exit.
      --lang=java|ts   The language to write: java (a package of handles) or ts
                         (one module).
  -o, --output=DIR     The directory to write into (default: the current
                         directory).
      --package=PKG    The Java package to write (default: tierwise.generated).
                         Java only.
  -V, --version        Print version information and exit.
```

Compiles the project and writes typed handles for its keys and features, so that a misspelled name or a wrong result
type is a compile error in your code instead of a runtime error. It writes files and never deletes any.

```console
$ java -jar tierwise.jar codegen samples/commerce --lang java -o generated
wrote generated/tierwise/generated/Key.java
wrote generated/tierwise/generated/Feature.java
wrote generated/tierwise/generated/Keys.java
wrote generated/tierwise/generated/Features.java
wrote generated/tierwise/generated/TypedEvaluator.java
wrote generated/tierwise/generated/TypedResolved.java
wrote generated/tierwise/generated/TypedDecision.java
$ java -jar tierwise.jar codegen samples/commerce --lang ts -o generated-ts
wrote generated-ts/tierwise.ts
```

| Language | Output |
| --- | --- |
| `java` | A package (default `tierwise.generated`, changed with `--package`). `Keys` and `Features` hold one constant for each declared key and feature, such as `Keys.CHECKOUT_PICKUP_LEAD_HOURS`, typed by the key's type. `TypedEvaluator` wraps an `Evaluator` and takes those constants. Names are `UPPER_SNAKE`, split at characters that are not letters or digits |
| `ts` | One module, `tierwise.ts`, with the types `KeyName`, `KeyValues`, `FeatureName` and `FeatureEffects`, so that names are string-literal unions and results are typed by name. `--package` is rejected with `--lang ts` |

A name that collides with another after the conversion, or has no ASCII letter or digit, fails with exit 1. A
FactSet stays untyped; object values are `Map<String, Object>` or `Record<string, unknown>`. The first line of a
generated file names the bundle digest it came from; regenerate the files when the project changes.

## In CI

A pull request check that blocks a bad change and keeps the bundle of a good one:

```sh
java -jar tierwise.jar validate --format json > validate.json   # annotations
java -jar tierwise.jar test
java -jar tierwise.jar compile --provenance -o bundle.json
```

`validate` already runs lint and the examples, so `lint` and `test` are for when you want their output alone. Publish
the bundle only from the build of the main branch (see the [server guide](server.md#publish)).

## Open points

- *(OPEN)* The download address of the JAR and the name of the Docker image (open question O2).
