# ADR 0026: Naming rule explanations and kind suggestions

- Status: accepted (delegated technical decision)
- Date: 2026-10-10
- Amends: the `explain naming/<name>` row of [ADR 0021](0021-cli-codegen-and-distribution.md)

## Context

Two gaps turned up while the planned enforcer tests of `spec/conformance/enforcers.d/C1.yaml` were being written.

1. **FIND-30.** The explanation of a naming rule `naming/<name>` is the `description` declared for its pattern. When
   the pattern has none, it is the naming family explanation of `spec/error-codes.md` followed by the pattern. ADR 0021
   deferred this: `tierwise explain naming/<name>` printed only the `naming/*` family entry, because the declared
   pattern lives in a project and the command took none. `spec/spec.md` is authoritative, so the deferral can't stand.
   The CLI only uses the exported package `io.github.tierwise.core`, which offered no way to read naming patterns.
2. **FIND-27 and FIND-28.** The compiler suggests a declared kind name for an `E_UNKNOWN_KIND` finding. FIND-28
   allows a suggestion only from a category of the FIND-27 table or, under LINT-10 (b), a lint rule identifier. The
   table had no row for `E_UNKNOWN_KIND`, so the existing suggestion didn't conform.

## Decision

### `tierwise explain naming/<name>`

- The command takes `--project DIR`. Without it, the project is the current directory when that directory holds
  `tierwise.yaml`. A `--project` that is not a directory, or that holds no `tierwise.yaml`, is a usage error (exit 2).
- With a project, a pattern that has a `description` prints the rule identifier and then the description. A pattern
  without one prints the rule identifier, the `naming/*` family entry, then its category and pattern. The pattern
  comes last, as FIND-30 requires.
- A naming rule that the project doesn't declare exits with 1, and the message suggests the closest declared pattern
  name when one is close.
- A project with error findings still explains every pattern it could read. Explaining isn't validating, and a
  project being fixed is exactly when the explanation is needed.
- Without a project, the command prints the family entry as before, plus a note that the pattern needs a project and
  how to give one.
- `naming/*` and every other code ignore the project.

### Core API

`Tierwise.namingPatterns(Path projectRoot, CompileOptions options)` returns an unmodifiable map from pattern name to a
public record `NamingPattern(name, category, pattern, Optional<String> description)`, in code-point order of the names.

- It loads the project and its presets as `compile` does, and combines patterns as the kind stage does: a later layer
  replaces an earlier pattern entirely (MANIFEST-32, MANIFEST-33).
- It reports no finding. It leaves out a pattern whose `category` or `pattern` isn't a string. The texts are returned
  as written and aren't checked, because `compile` reports faulty declarations.
- `CompileOptions` supplies the URL preset fetcher, so the CLI resolves URL presets as `validate` does.

This is the smallest addition that lets a tool outside the core implement FIND-30: one static method and one record of
four components. Neither exposes internal types.

### Kind suggestions

The FIND-27 table gets the row `E_UNKNOWN_KIND` → "declared kinds". The existing suggestion, the closest declared kind
name, now conforms to FIND-28. `FindingContractTest#FIND_28_aSuggestionForAnUnknownKindIsADeclaredKindName` checks
that the suggestion is exactly a declared kind, of the project or of a preset.

## Alternatives considered

- **Keep the ADR 0021 deferral.** This contradicts FIND-30, and `spec/spec.md` wins over an implementation choice.
- **Let the CLI read naming patterns itself with its YAML library.** It would have to repeat how presets are found,
  fetched, digest-checked and layered (MANIFEST-19 to MANIFEST-33). A second implementation of those rules can
  drift from the core.
- **Return the patterns as part of `CompileResult`.** This would tie explanation to a full validation and widen a
  result type that conformance code compares. It would also make the patterns of a faulty project depend on how far
  validation got.
- **Export the internal `NamingTable`.** It carries compiled regular expressions, parse nodes and layer numbers,
  none of which a caller needs, and it would make them public API.
- **Include faulty patterns with empty fields.** There is nothing useful to print for a pattern without a pattern
  text, so leaving it out keeps the record's fields required.
- **Drop the kind suggestion instead of adding a table row.** The suggestion helps users and is cheap. FIND-27 already
  lists every other "unknown name" code that has a matching category of declared names.
