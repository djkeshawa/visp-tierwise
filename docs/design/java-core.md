# Design: `tierwise-core` (Java)

Status: working design for Phases 2–3, 2026-10-09. The spec (`spec/`) is
authoritative. This document only fixes the package boundaries and contracts, so
that several contributors can implement the core in parallel.

## Principles

- **One model, two directions.** The compiler builds an immutable `Model` from
  sources and writes it as a bundle. The bundle reader builds the **same** `Model`
  from bundle bytes. The evaluator only ever sees a `Model` loaded from a bundle,
  so compiled and loaded bundles cannot disagree.
- **Plain values.** Runtime values use `Boolean`, `Long` (every integer), `String`
  (strings, enums and canonical decimals), `List<Object>` (sets and lists) and
  `Map<String, Object>` (objects; insertion order is not significant). They are
  always unmodifiable, and `null` is never a value.
- **No reflection, no logging framework, no network.** Events go through
  `EvaluationListener`. Preset URLs are fetched through a `PresetFetcher`
  interface; only the CLI provides a network implementation.
- **Stable codes everywhere.** Every finding and every raised error carries a code
  from `spec/error-codes.md`, defined once in `Code`.

## Module

```
module io.github.tierwise.core {
  requires org.snakeyaml.engine;
  requires com.fasterxml.jackson.core;
  exports io.github.tierwise.core;
}
```

Runtime dependencies are snakeyaml-engine and jackson-core (ADR 0012). Tests use
JUnit Jupiter and jqwik.

## Public API (`io.github.tierwise.core`)

| Type | Contract |
| --- | --- |
| `Tierwise` | Static entry points: `CompileResult compile(Path projectRoot)`, `CompileResult compile(Path projectRoot, CompileOptions)`, `LoadedBundle loadBundle(byte[] bytes)`, `Evaluator evaluator(LoadedBundle, EvaluatorOptions)` |
| `CompileResult` | `List<Finding> findings()`, `Optional<byte[]> bundle()`. The bundle is present only when no error-severity finding exists (FIND-7). |
| `CompileOptions` | `PresetFetcher presetFetcher` (default: none, so URL presets report `E_PRESET`) |
| `Finding` | record `(String code, Severity severity, String file, List<Object> path, int line, String message, Optional<String> suggestion)`; `path` holds `String` and `Integer` segments (FIND) |
| `Severity` | `ERROR`, `WARN`, `INFO` |
| `Code` | Constants for every code in `spec/error-codes.md`, plus a `Code.of(String)` lookup |
| `TierwiseException` | Unchecked; `String code()`. Raised for every evaluation error (EVAL). |
| `LoadedBundle` | Sealed interface (sole implementation `internal.model.LoadedModel`); `String bundleVersion()`. Created only by `loadBundle`. |
| `EvaluatorOptions` | Builder: `provider(String level, LevelValueProvider, Duration timeout)`, `listener(EvaluationListener)` |
| `LevelValueProvider` | `Map<String, Object> valuesFor(String nodeId) throws Exception` |
| `EvaluationListener` | `void onEvent(EvaluationEvent event)`; an event has `type`, `bundleVersion`, optional `level`, `node`, `key` (EVAL-53) |
| `Context` | Final class holding `levels` and `axes` maps, plus a builder. A `Context.fromJson(Object)` factory accepts raw maps for conformance input; malformed shapes raise `E_CONTEXT_INVALID` at evaluation time. |
| `FactSet` | Final class holding `facts` and `children`; `FactSet.fromJson(Object)` mirrors `Context` |
| `Evaluator` | `Resolved resolve(Context, String key)` (may call providers); `Resolved resolveLocal(Context, String key)` (never calls providers, raises `E_PROVIDER_REQUIRED`); `Decision decide(Context, String feature, FactSet)` |
| `Resolved`, `Decision` | Immutable result types mirroring spec section 9, with `Map<String, Object> toJson()` giving exactly the JSON shape of section 9 (used by the conformance runner) |

Typed `Key<T>` and `Feature<R>` handles (requirements §7.1) are added by codegen in
Phase 5, on top of this string API.

## Internal packages (`io.github.tierwise.core.internal.*`)

| Package | Responsibility | Spec |
| --- | --- | --- |
| `json` | `CanonicalJson` (JCS writer for the bundle subset), `StrictJson` (jackson-core streaming → plain values, rejecting duplicates and non-integer numbers), `Sha256` | BUNDLE-1..13, canonical vectors |
| `yaml` | `YamlLoader`: snakeyaml-engine node API → `PNode` tree (`PMap`, `PSeq`, `PScalar`) with file, path and line. It rejects aliases, anchors, tags and merge keys, multiple documents, duplicate keys (after NFC), BOMs and invalid UTF-8. | SRC-1..11 |
| `project` | Manifest parsing, preset resolution and composition, source mapping (globs, path patterns, multi-node files, inline single-file content) → `SourceSet` | MANIFEST, SRC, NS |
| `model` | Immutable records: `Model`, `Hierarchy`, `ScopeDef`, `EntityDef`, `FactDef`, `TypeDecl`, `KeyDef`, `FeatureDef`, `PolicyDef`, `SettingDef`, `RuleDef`, `ConditionDef`, `BindingDef`, `Values` | all |
| `check` | `ModelBuilder` (`SourceSet` → `Model` plus findings), then one checker class per area: `HierarchyChecks`, `EntityFactChecks`, `TypeChecks`, `KeyChecks`, `KindChecks`, `ServiceSourceChecks`, `FeatureChecks`, `RuleChecks`, `ExampleRunner` | §1–§8 engine codes |
| `lint` | `LintRunner` plus one class per lint rule; severity resolution | LINT |
| `bundle` | `BundleWriter` (`Model` → content tree → canonical bytes) and `BundleReader` (bytes → `Model`, with the EVAL-14 check order) | §10 |
| `eval` | `ContextValidator`, `Strategies` (one pure function per strategy, plus axis combination), `ProviderCalls`, `Resolver`, `DecisionEngine`, `ResultFactory` | §2, §5, §6, §8, §9, §11 |

Findings are collected in one `Findings` accumulator. Its order is file, then
line, then code, so output is deterministic.

## Tests

- **Conformance runner** (`src/test/java/.../conformance`): JUnit `@TestFactory`
  dynamic tests that read `spec/conformance/` directly, located from the
  `tierwise.spec.dir` system property (set in the POM to `${project.basedir}/../../spec`).
  - `CanonicalVectorsTest`, `EvaluateVectorsTest` (load, resolve, decide, with
    stub providers from the vector) and `CompileCasesTest` (findings set equality,
    or bundle bytes equality).
  - Subset matching follows VEC rules; `exact: true` compares the whole `toJson()`.
- **Unit tests** per package, plus jqwik properties from requirements §12.
- **Enforcer tests:** every enforcer entry that says "Planned … unit test" gets a
  test named after its clause ID (e.g. `EVAL_46_operationDoesNotModifyBundle`).

## Parallel build safety

Contributors working in separate git worktrees each build in their own tree. Shared
files (the POM, `module-info.java`, `Code`, the public API types, the `model`
records, `PNode`) belong to the skeleton step and change only through it.

## Notes from the skeleton (J0)

- The snakeyaml-engine 3.1.1 module is `org.snakeyaml.engine`. Its `api.lowlevel`
  package isn't exported, so `YamlLoader` builds nodes with
  `new Composer(settings, new ParserImpl(settings, new StreamReader(settings, text)))`.
- `Context.problem()` and `FactSet.problem()` carry malformed `fromJson` input.
  The evaluator raises `E_CONTEXT_INVALID` or `E_FACTS_INVALID` at the step EVAL-17
  gives. A Java `null` context or FactSet counts as empty (EVAL-54).
- Numbers: every integral `Number` is an integer and is checked against
  ±(2^53−1) by the evaluator (FACT-41, TYPE-18). A `Double` is a non-integer
  number (FACT-38).
- `Code.of(String)` returns `Optional<String>`. `SkippedScope` and `ScopeKind` are
  top-level public types.
- `TierwiseTest` asserts the "not implemented" messages. Each implementation item
  updates its own assertion.
