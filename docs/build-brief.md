> **Amended build brief — pre-specification review, 7 October 2026.**
> The user authorized applying the recommendations in `strata-prompt-review.docx`.
> The recommendations are integrated below; their unresolved details are identified
> explicitly. This is an amended project brief, not an accepted Phase 1 specification
> or evidence of implemented behavior. Phase review gates remain in force.
> See [recommendation coverage](recommendations-review.md) and [remaining questions](open-questions.md).
> The user subsequently delegated technical clarification to Claude Opus 5.5
> at xhigh effort. The selected node-admission and artifact decisions are applied
> here and recorded in [ADR 0005](adr/0005-node-admission-and-canonical-artifacts.md).

# Build "Strata": an open-source library for layered configuration, capabilities and decisions (Java + JavaScript)

> "Strata" is a working name. Check that it's available on GitHub, Maven Central and npm before publishing. This prompt is written for a coding agent (e.g. Claude Code) starting in an empty repository.

---

## 1. Your role and how to work

You are a senior engineer, fluent in Java and TypeScript, building a small, strict, open-source project. Your priorities, in order:

1. **Spec first.** The language-neutral spec in `spec/` is the source of truth. When code and spec disagree, the code is wrong.
2. **Correct, explicit semantics.** Section 4 is normative: implement it exactly as written.
3. **Simple, obvious code over clever abstractions.** Explicit beats implicit. No reflection magic and no hidden conventions.
4. **Tests that prove behaviour**, written alongside the code, not afterwards.

Work in the phases listed in Section 11. At the end of each phase, **stop** and:

- summarise what you built,
- list every decision you had to make that this prompt didn't cover,
- wait for review.

If anything in this prompt is ambiguous, contradictory or looks wrong, **ask before choosing**. Never silently pick an interpretation; silent interpretation is the exact problem this project exists to remove.

Record each non-trivial design decision as a short ADR in `docs/adr/`. Spec changes go through an RFC (a PR against `spec/` with a written rationale).

Do not build anything listed in Section 10 (out of scope).

### 1.1 Genericity is a hard requirement

The project must work for **any** hierarchy, entities, facts and features. Every domain name in this prompt (`global`, `division`, `north`, `seller`, `order`, `item`, `credit_hold`, `ALLOW`, …) comes from a sample domain. These names are **examples, not scope**.

- **No domain terms in production code.** Nothing in `java/**/src/main` or `js/**/src` may contain a level, axis, node, entity, fact, key, feature, policy or effect name. All of them come from the schema or bundle at runtime, or from code generated from the schema.
- **Structure is positional or declared, never named.** The library has no idea what a "division" or an "order" is:
  - the **root level** is the first entry in `decision_hierarchy.levels`;
  - the **most specific level** is the last entry;
  - **service-sourced levels** are the ones listed in `service_sourced`;
  - **effects** are whatever a feature's `returns` declares.
- **Domain names live only in** `samples/` and test fixtures.
- **Kind names and naming patterns live only in presets.** The engine has no concept of "configuration" or "capability"; those are kinds a preset defines (Section 1.4).
- **Prove it with two very different domains.** Both are used by the conformance suite.
  - **Domain A, `samples/commerce/`**: levels `global → channel → division → site → account → user`; axis `seller`; `account` and `user` service-sourced; entities `customer`, `invoice → order → item`.
  - **Domain B, `samples/retail-pos/`**: levels `platform → region → store → terminal`; axis `brand`; `terminal` service-sourced; entities `shopper`, and `basket` (belongs to shopper, has many `line`); a feature decided per `basket` that returns `{ APPROVE, REVIEW, REJECT }`.
- **Add a docs-only sample, `samples/saas-entitlements/`** (`plan → org → workspace → user`), to show the `strata:entitlements` preset, including `union`-based grants. This is a common use case and helps adoption.
- **Enforce genericity in CI.** Collect every name declared in the sample schemas, and fail the build if any appears as a string literal or identifier in production code. Generic words that collide (e.g. `line`) may be allow-listed explicitly, with a comment.

### 1.2 Open-source requirements

- **License:** Apache-2.0. Include `LICENSE` and `NOTICE`.
- **No organisation-specific content anywhere.** No company names, internal business units, internal system names or internal codes, in code, docs, samples, tests or commit messages.
- **Project files:**
  - `README.md`: the problem, a "when to use / when not to use" table (Section 2.2), and a 5-minute quickstart for **both** Java and JavaScript;
  - `CONTRIBUTING.md`, using DCO sign-off;
  - `CODE_OF_CONDUCT.md` (Contributor Covenant);
  - `SECURITY.md`, with private vulnerability reporting;
  - `GOVERNANCE.md`, covering maintainers, decision-making, and RFCs for spec changes;
  - `CHANGELOG.md` (Keep a Changelog);
  - issue and PR templates.
- **Versioning:**
  - every package follows SemVer;
  - the **spec has its own version** (`specVersion`), and the **bundle format** has its own (`bundleFormatVersion`);
  - every package declares which spec versions it supports;
  - **breaking spec changes require an RFC and a major spec version.**
- **Supply chain:** reproducible builds, signed release artifacts (Sigstore), an SBOM (CycloneDX) per release, automated dependency updates, and an OpenSSF Scorecard workflow.
- **Documentation:** a docs site generated from `docs/`, plus Javadoc and TypeDoc API references.
- **Public APIs are small and documented.** Anything not explicitly public is internal and may change.

### 1.3 Two languages, one spec

| Capability | Java | JavaScript / TypeScript |
|---|---|---|
| Load, validate, compile sources | ✅ (reference toolchain) | ❌ (v1: use the CLI) |
| Evaluate compiled bundles (`resolve`, `decide`) | ✅ | ✅ (Node and browser) |
| CLI (`init`, `validate`, `lint`, `test`, `compile`, `codegen`, `explain`) | ✅, shipped as a native binary, Docker image and GitHub Action | Uses the same CLI |
| Codegen for typed handles | ✅ Java constants | ✅ TypeScript types (generated by the same CLI) |
| Runtime integration | Spring Boot starter | Node loader (fetch + refresh) |
| Server (control plane) | ✅ Spring Boot | — (call it over HTTP using the OpenAPI spec) |
| OpenFeature provider | ✅ | ✅ |

- **Both evaluators must pass the same conformance suite** (`spec/conformance/`). A release is blocked unless both pass the conformance version it claims.
- **Browser evaluation is for display only.** Anything evaluated client-side can be tampered with, so enforcement must always happen server-side. State this in the docs and in the JS package README.

### 1.4 Mechanism, not policy

Strata provides structure, guarantees and guidance. **Each user makes every modeling choice.** Nothing in the engine may force one organisation's way of working on another. Keep three layers strictly apart:

| Layer | What it contains | Who decides | Can a user change it? |
|---|---|---|---|
| **1. Engine guarantees** | Determinism across languages; validation of everything the user declared; unknown levels, axes and keys rejected; node admission follows an explicitly declared source and unknown-node policy (no implicit fail-open); explicit quantifiers over collections; provenance on every result | The spec | **No.** These are the reason to use Strata. |
| **2. The user's model** | Levels and axes; entities and facts; keys and their merge strategies; kinds; naming patterns; file layout; namespaces and ownership; features and policies; failure behaviour; defaults | Each project, in its schema and `strata.yaml` | Yes, completely |
| **3. Guidance** | Presets (ready-made kinds, naming patterns and lint settings); `strata init` templates; lint rules with configurable severity; a modeling guide; `strata explain <code>` | The project ships recommendations; users opt in | Yes: use, extend, override or ignore |

What follows from this:

- The engine knows **merge strategies** (Section 4.2), not "configuration" or "capability." Those two are **kinds defined in the `strata:recommended` preset**. A project may rename them, define different kinds, or use none.
- **Naming conventions, file layout and ownership are declared by the project.** If a project declares none, none are enforced.
- **Every opinion in this prompt about *how to model*** (e.g. "capabilities only narrow," "keys have three segments") belongs in a preset or a lint rule, never in engine code.
- **Presets can tighten checks, but can never switch off an engine guarantee.**

The test, before adding any behaviour: *is this a guarantee every user needs, or a choice one team made?* Choices belong in layer 2 or 3.

---

## 2. Background

### 2.1 The problem

Many systems configure behaviour at several levels: for example platform → tenant → region → store → user, or global → channel → division → site → account. Values end up scattered across code, environment files, flag tools and per-team JSON or YAML files. Teams that try to consolidate them typically hit four problems:

1. **There is no shared meaning per key.** Nothing says whether a key is a value that a lower level replaces (configuration) or a permission that any level can veto (capability). Different readers combine it differently.
2. **Typos fail open.** When permissions default to enabled, a misspelled node (`nroth` instead of `north`) or key silently removes a restriction.
3. **Rules over collections are ambiguous.** A rule like `item.stock_type: [STOCKED]` on an *order* decision doesn't say whether it means "any item" or "all items." The two readings give opposite answers for mixed baskets.
4. **Teams invent their own structures and names.** There's no enforced schema or naming convention.

The goal: **every key, fact and rule has exactly one meaning, defined once and enforced by the build.** Mistakes fail CI, not production, and every answer can explain where it came from.

### 2.2 Positioning (use this in the README)

| If you need… | Use |
|---|---|
| Release toggles, experiments, kill switches | A feature-flag tool. Strata can sit behind OpenFeature as a provider. |
| Authorization (who may do what to which resource) | Cedar or OPA |
| A general-purpose configuration language | CUE, Pkl or Jsonnet |
| Typed configuration across levels *you* define, with a merge rule per key (override, veto, intersect, grant, limits…); entity-aware decision tables with explicit all/any; provenance for every answer; in Java and JavaScript | **Strata** |

---

## 3. What to build

### 3.1 Repository layout (monorepo)

```
strata/
├── spec/
│   ├── spec.md                  normative, language-neutral specification
│   ├── schemas/                 JSON Schemas: source files and compiled bundle
│   ├── error-codes.md           stable validation error codes
│   └── conformance/
│       ├── bundles/             compiled bundles for sample domains A and B
│       ├── evaluate/            resolve/decide vectors (run by Java AND JS)
│       └── compile/             source → bundle or error vectors (run by Java)
├── java/                        Maven multi-module build
│   ├── strata-core/
│   ├── strata-cli/
│   ├── strata-spring-boot-starter/
│   ├── strata-server/
│   └── strata-openfeature/
├── js/                          pnpm workspace, TypeScript
│   ├── evaluator/               @strata/evaluator   (Node + browser, zero runtime deps)
│   ├── node/                    @strata/node        (bundle fetch, ETag refresh, providers)
│   └── openfeature/             @strata/openfeature
├── presets/                     built-in presets: recommended, minimal, entitlements
├── samples/
│   ├── commerce/                domain A
│   ├── retail-pos/              domain B
│   └── saas-entitlements/       docs sample
├── docs/                        docs site source, plus adr/
└── LICENSE, NOTICE, README.md, CONTRIBUTING.md, CODE_OF_CONDUCT.md,
    SECURITY.md, GOVERNANCE.md, CHANGELOG.md
```

`@strata/` is a placeholder npm scope, and the Maven `groupId` is also a placeholder. Confirm both in Phase 0.

### 3.2 Components

| Component | Purpose | Allowed dependencies |
|---|---|---|
| `strata-core` (Java) | Schema model, loading, validation, compilation, resolution, decisions, provenance. No Spring, no network. | Jackson (YAML + JSON), SLF4J |
| `strata-cli` (Java) | `init`, `validate`, `lint`, `test`, `compile`, `codegen --lang java\|ts`, `explain <code>`. Shipped as a fat JAR, GraalVM native binaries (Linux, macOS, Windows), a Docker image and a GitHub Action. | core, picocli |
| `strata-spring-boot-starter` | Loads and refreshes bundles; exposes `Resolver` and `DecisionEngine` beans; Micrometer metrics; providers for service-sourced levels | core, Spring Boot |
| `strata-server` | Control plane: stores and serves versioned bundles; resolve/decide/explain APIs; publishes an OpenAPI spec | core, Spring Boot |
| `strata-openfeature` (Java) | OpenFeature provider backed by the evaluator | core, OpenFeature Java SDK |
| `@strata/evaluator` | Loads compiled bundles; `resolve` and `decide` with provenance. Runs in Node and browsers. | none at runtime |
| `@strata/node` | Fetches bundles from the server or a URL, with ETag polling, last-known-good and atomic swap; provider interface | `@strata/evaluator` |
| `@strata/openfeature` | OpenFeature provider (server and web SDKs) | `@strata/evaluator`, OpenFeature JS SDK |

### 3.3 Architecture

```
config repo (git) ── the ONLY source of truth
  strata.yaml                layout, presets, lint settings
  schema/**                  hierarchies, entities, facts, keys, features (kinds and naming, if used)
  values/**                  configuration and capability values, one file per node
  rules/**                   one file per feature: policies, bindings, examples
        │
        │  PR → CI: strata validate · strata test · strata compile   (CLI or GitHub Action)
        ▼
  bundle.json                canonical JSON, content-hashed, immutable
        │
        │  CI publishes (authenticated)
        ▼
  strata-server (control plane)
    re-validates, stores every version, serves the latest, supports rollback,
    offers /resolve and /decide for any language via OpenAPI
        │
        │  poll with ETag
        ├─────────────────────────────┐
        ▼                             ▼
  Java services                 Node / browser apps
  (Spring Boot starter)         (@strata/node, @strata/evaluator)
  evaluate IN-PROCESS           evaluate IN-PROCESS (browser: display only)
```

Key principles:

- **Git is the source of truth.** The server never edits schema or values; it only stores and serves bundles that already passed validation.
- **Evaluation happens in-process.** The server is never on the hot path. Applications keep evaluating from their last-known-good bundle when the server is down.
- **One bundle version means one answer.** The same bundle, context and facts produce identical results in Java and JavaScript.

---

## 4. Semantics (normative)

`spec/spec.md` must state everything in this section in language-neutral terms. Both implementations follow the spec.

### 4.1 The two hierarchies

**Decision hierarchy (who decides).** An ordered list of levels, from least to most specific, declared in the schema. In sample domain A:

```
global → channel → division → site → account → user
```

It can also declare **axes**, which cut across the hierarchy rather than sitting in it (e.g. `seller`). One seller can appear under many divisions or sites.

- A **context** names at most one node per level and per axis, e.g. `{ levels: { channel: web, division: north, site: site_001, account: A99 }, axes: { seller: acme } }`. Missing levels are skipped.
- Implementations must **not** assume parent relationships between nodes. The context supplies the path.
- The first level is the **root level**, and the last is the **most specific**. Implementations refer to levels only by position, or by what the schema declares about them. They never refer to a level by name.

**Node admission (review amendment).** Each level declares one node source:

- `nodes: [<id>, ...]`: a closed inline list;
- `nodes: { from: "<generated JSON file>" }`: a closed list produced in CI;
- `nodes: open`: any otherwise valid node ID is admitted.

Each level must explicitly declare `on_unknown_node: error | ignore`. There is no hidden default. For a closed inline or generated source, an unlisted ID is an error under `error`; `ignore` is an explicit opt-in and must have separate conformance cases. Under `open`, IDs are admitted rather than treated as unknown; provider failures remain governed by the key's `on_source_failure` independently of node admission. Unknown level and axis names are always rejected.

**Runtime `ignore` (delegated clarification).** An otherwise valid, unlisted ID in a closed catalog skips that supplied scope entirely: no stored values, binding lookup or provider call for that scope. It contributes no value and triggers no `on_source_failure` behavior. Other supplied levels and axes remain eligible; there are no inferred parent relationships or descendant pruning. Only remaining admitted scopes participate in evaluation; record the skipped scopes as ordered `skippedScopes` provenance in Section 4.7. Any later root-inclusion rule must preserve explicit skipping of an unknown supplied root. Validate the entire supplied context, including declared scope names and ID types, before invoking any provider; a later invalid scope cannot permit earlier calls. `ignore` never suppresses structural/type errors. Open catalogs admit valid new IDs normally, including any configured provider calls.

**Compilation boundary.** Closed-catalog IDs referenced by source values or bindings must exist under both runtime policies. Compilation rejects unlisted IDs with `E_UNKNOWN_NODE`; runtime `ignore` is not permission to publish authoring mistakes. No skipped scope is replaced with a fabricated root or another ID. If no eligible feature binding remains, report an explicit evaluation error; a policy's `default` applies only after selecting that policy, never as a substitute for a missing binding. The error code and API representation are defined in Phase 1.

**Phase 1 decisions still required:** the container syntax, matching axis-node declarations, generated JSON format/path/embedding rules, and structural ID/normalization contract. Preserving the ordered `levels` and `axes` lists with separate source/policy maps is the current format proposal, not an accepted parser contract. Root identity and behavior when a context omits the root also remain open; this amendment does not invent an implicit root. See [ADR 0005](adr/0005-node-admission-and-canonical-artifacts.md) for the consultation and assessed alternatives.

**Entity hierarchy (what a decision is about).** Entities and their relationships, declared in the schema. In sample domain A:

```yaml
entity_hierarchy:
  customer: {}
  invoice:  { has_many: order }
  order:    { belongs_to: customer, has_many: item }
  item:     {}
```

- Every **fact** belongs to exactly one entity: `<entity>.<attribute>`.
- Every **feature** is decided at exactly one entity (`decided_per`).

### 4.2 Keys, merge strategies and kinds

**Key value types (review amendment).** Key values have a vocabulary separate from fact types:

| Value type | Declaration and validation intent |
|---|---|
| `boolean` | Strict `true` or `false`; no string or numeric coercion |
| `string` | NFC-normalized string with exact case-sensitive comparisons |
| `integer` | Integer within the cross-language safe range, plus declared minimum/maximum |
| `decimal` | Canonical decimal string as specified in Section 4.10 |
| `enum` | One of the declared `values`, with strict value types |
| Set | Declared by a `universe`; values are subsets and results follow universe order |
| `object` | Object with a declared field schema; field types and nested values must be validated |
| Formatted string | A string with a declared format such as `time_hhmm` or `iana_tz`; validated during compilation, returned as a string |

Set typing is implied by `universe`. The sample now explicitly types boolean/integer keys. Requiring an explicit `type` on every non-set key, the field-schema dialect, array/null/unknown-field behavior, exact format grammar, and pinned timezone validation data are Phase 1 decisions; do not infer them from a kind or invent them in an evaluator. Every strategy must match the declared key type (`E_STRATEGY_TYPE`), and every supplied value must match its type (`E_TYPE_MISMATCH`).

**Every key declares a merge strategy** (`combine`), which says how values from different levels become one result. The engine provides a fixed library of strategies. Each is defined precisely in the spec and implemented identically in Java and JavaScript.

| Strategy | Value type | Result | Unset everywhere | Narrowing |
|---|---|---|---|---|
| `override` | any | Value from the most specific level that sets it | the key's `default` | — |
| `deep_merge` | object | Field by field, the most specific level wins for each field; arrays are replaced, not concatenated | the key's `default` | — |
| `all` | boolean | AND of every value set | `true` | ✅ |
| `any` | boolean | OR of every value set | `false` | — |
| `intersect` | set | `universe` ∩ every value set | the full `universe` | ✅ |
| `union` | set | ∪ of every value set | `[]` | — |
| `min` | integer | minimum of every value set | absent (unlimited) | ✅ |
| `max` | integer | maximum of every value set | absent | — |

- **Narrowing** strategies can only shrink the result when a level adds a value. Kinds can require this property (below).
- `override` and `deep_merge` are **order-dependent**. All the others are **commutative**: the order in which levels are applied doesn't change the result.
- Set values must be subsets of the key's `universe`. `[]` is legal.
- Each key declares `settable_at`: the levels and axes where it may be set. A value anywhere else is an error.
- A key with an order-dependent strategy needs a `default`, or must be set at the root level.
- **Axes.** For commutative strategies, an axis value is simply one more input. A key with an order-dependent strategy that is settable on an axis must declare `combine_across_axes`: `max`, `min`, `axis_wins` or `hierarchy_wins`. The hierarchy value is resolved first, then the axis value, then the two are combined.

**Kinds (optional; declared by the project or a preset).** A kind is a named set of constraints that keys opt into, so a team states its modeling rules once instead of repeating them on every key:

```yaml
kinds:
  capability:
    strategies: [all, intersect, min]   # keys of this kind may only use these
    narrowing_only: true                # values must be able to narrow the result
    on_source_failure: deny
    naming: capability_key              # keys of this kind must match this naming pattern
  configuration:
    strategies: [override, deep_merge]
    require_default: true
    on_source_failure: skip
```

- The engine enforces exactly what a kind declares, and nothing more.
- A project may use the recommended preset's kinds (the two above), rename them, define its own, or use no kinds at all. For example, a `grant` kind could use `union` or `any` for allow-list models, where any level can grant.
- Under `narrowing_only`, a value that can't narrow is rejected. For `all`, that means only `false` is legal, because `true` can never change an AND.

### 4.3 Service-sourced levels

Values for every level listed in `service_sourced` come from a provider (a Java SPI, or a JS provider function), not from files.

- **Every key settable at a service-sourced level declares `on_source_failure`**, either on the key or through its kind. There is no hidden default: a key without one is an error.
  - `skip`: the level contributes nothing, and resolution continues as if it had set no value.
  - `deny`: the level contributes the most restrictive value (`false` for `all`, `[]` for `intersect`, `0` for `min`). This is legal only for narrowing strategies.
- Provider values are validated at runtime against the bundle. An invalid entry is treated as a source failure for that key, and is logged and counted.
- Provenance records the level as `UNAVAILABLE` or `INVALID`.

### 4.4 Facts

Facts are defined in the shared part of the schema (never inside a namespace), so every feature sees one definition. A fact has:

- a name, `<entity>.<attribute>`;
- a type: `boolean`, `string`, `integer`, `decimal`, or `enum` (with `values`);
- a `source`, which is documentation only.

At evaluation time, the caller passes a **FactSet** for the feature's `decided_per` entity:

```yaml
facts:                       # the entity's own facts and its ancestors' facts (one value each)
  customer.active: true
  customer.credit_hold: true
children:                    # the entity's children, one entry per child
  item:
    - { item.stock_type: STOCKED }
    - { item.stock_type: DROP_SHIP }
```

`null` and absent are both **missing**.

### 4.5 Features, policies, bindings

**Feature.** Declared in a component schema, with:

- `name`
- `decided_per`: an entity
- `returns`: an enum, or an object schema; `nullable: true` allows `NONE`
- `if_fact_missing`: the effect returned when a required fact is absent
- `hit_policy`: how rules combine. Spec v0.x defines only `FIRST` (Section 4.6).
- `binding_settable_at`: the levels where a binding may appear (required; there is no default). It must include the root level, defined as the first entry in `decision_hierarchy.levels`. Omitting that level is a distinct feature-definition error, provisionally named `E_ROOT_BINDING_NOT_ALLOWED` for review, rather than the error for an absent actual root binding.

**Policy.** Declared in `rules/<feature>.yaml`, with:

- `name`
- `settings`: name → `{ type, default }`; the type is `enum`, `boolean`, `integer`, `string`, or `list_of: <fact>`, meaning a list of that fact's allowed values
- `default`: the effect when no rule matches
- `rules`: an ordered list, each with `id`, `name`, `when` (conditions), and `then` (effect)
- Rules reference settings as `$<setting_name>`.

**Binding.** At each level in `binding_settable_at`: `{ policy: <name>, <setting>: <value>, ... }`.

- **The most specific binding on the context path replaces all others entirely.** Rules and settings are never merged across levels.
- **Every feature must have a binding at the root level.**

### 4.6 Rule evaluation

1. **Hit policy.** Each feature declares `hit_policy`. Spec v0.x defines only `FIRST`: rules are checked top to bottom, and the first rule whose conditions all hold decides. The field is required now, so that other hit policies (e.g. `UNIQUE`, `COLLECT`) can be added later without breaking existing projects.
2. **Conditions within a rule are ANDed.**
3. **Operators:**

| Operator | Applies to | Meaning | Empty collection |
|---|---|---|---|
| `fact: value` | own or ancestor fact (single value) | equals | n/a |
| `fact: { in: [...] }` | own or ancestor fact | value is one of | n/a |
| `fact: { all_in: [...] }` | child fact (collection) | ∀ child: value ∈ list | **false** |
| `fact: { any_in: [...] }` | child fact (collection) | ∃ child: value ∈ list | false |

4. **Child facts must use `all_in` or `any_in`; single-value facts must use equals or `in`.** The validator rejects anything else, for example `item.stock_type: [STOCKED]` on an order-level feature. This is the most important validation in the project.
5. **Facts from exactly one level down are supported in spec v0.x.** A deeper reference (e.g. an item fact in an invoice-level feature) is rejected with a clear error. Design the condition model so nested quantifiers can be added later (write an ADR).
6. **Missing facts are checked before any rule runs.** If any fact referenced anywhere in the bound policy is missing (for child facts: missing on any child), return `if_fact_missing` with reason `MISSING_FACT(<fact>)`. The result must not depend on rule order. **This is intentionally a strict, policy-wide precheck:** callers supply every fact referenced by the selected policy, including facts used only by a rule that would not win. An earlier rule that could match without that fact does not bypass the precheck. For example, in the sample a customer who passes the first rule still receives the missing-fact effect if a supplied child lacks a fact referenced by a later rule. Test this outcome after reordering rules. Omitted child collections and selection among multiple missing-fact reasons still require precise Phase 1 definitions.
7. **If no rule matches**, return the policy's `default` with reason `DEFAULT`.

### 4.7 Provenance

Every result explains itself.

```
Resolved  { key, strategy, kind (if any), value, bundleVersion,
            skippedScopes: [ { scope: level|axis, name, node, reason: UNKNOWN_NODE } ],
            contributions: [ { scope: level|axis, name, node, value,
                               effect: SET | VETO | NARROWED | WIDENED | IGNORED | UNAVAILABLE | INVALID } ],
            source: DEFAULT | { scope, name, node } }        // for order-dependent strategies

Decision  { feature, effect, bundleVersion,
            skippedScopes: [ { scope: level|axis, name, node, reason: UNKNOWN_NODE } ],
            binding: { level, node }, policy,
            reason: { type: RULE, id, name } | { type: DEFAULT } | { type: MISSING_FACT, fact },
            trace: per rule: matched, or the first failing condition }
```

For commutative strategies, report **every** contributing level, not just the first. In particular, every level that narrowed or vetoed a narrowing strategy must appear.

Both result shapes include `skippedScopes`, empty when no runtime scope was ignored. Order its entries by declared level order, then declared axis order. An entry records admission diagnostics, not a value contribution or a provider failure; do not fabricate a `value`, `UNAVAILABLE` or `INVALID` contribution for it. Only the ignored scope appears; independent supplied scopes remain in the evaluation.

### 4.8 Naming conventions (declared by the project)

A project may declare naming patterns in its schema, or inherit them from a preset. Each declared pattern is checked by a lint rule whose severity the project sets (Section 6.2). A project that declares no patterns gets no naming checks.

The `strata:recommended` preset declares:

| Thing | Pattern | Example |
|---|---|---|
| Key | `^[a-z]+(\.[a-z_]+){2}$`, i.e. `<domain>.<component>.<name>` | `checkout.pickup.lead_hours` |
| Keys of kind `capability` | `\.(allowed\|allow_[a-z_]+\|allowed_[a-z_]+)$` | `checkout.payment.allowed_methods` |
| Enum value | `^[A-Z][A-Z0-9_]*$` | `DROP_SHIP` |
| Node ID | `^[a-z][a-z0-9_]*$` | `north` |
| Feature | `^[a-z]+\.[a-z_]+$`, i.e. `<domain>.<feature>` | `checkout.order_submission` |

**One exception is structural, not a style choice:** fact names are always `<entity>.<attribute>`, where `<entity>` is a declared entity. The engine uses the prefix to know which entity a fact belongs to.

### 4.9 Ownership (optional)

A project may split its schema into a shared part and namespaced parts (for example, one per team) by declaring `namespaces` in `strata.yaml`. When it does:

- shared definitions (hierarchies, entities, facts, kinds) may be **extended** by namespaced files, but never **overridden**;
- a namespaced file may declare keys and features only inside its own namespace.

A project that declares no namespaces is simply one shared schema.

### 4.10 Cross-language determinism

These rules exist so that Java and JavaScript give identical answers.

1. **Canonical hash content and artifact.** A compiled bundle contains a named `content` object. This object contains `bundleFormatVersion`, `specVersion`, the resolved schema, all compiled values and all compiled rules/policies: every input affecting evaluation, including compiled node catalogs and admission policies. `bundleVersion` is `sha256:` followed by the lowercase hexadecimal SHA-256 digest of the UTF-8 [RFC 8785 (JCS)](https://www.rfc-editor.org/rfc/rfc8785) encoding of **that `content` object only**. The bundle artifact is exactly the UTF-8 JCS encoding of `{ bundleVersion, content }`, with no additional envelope fields, byte-order mark or trailing newline. Both evaluators recompute the content digest on load. `gitCommit`, `createdAt` and publisher information belong only in separate provenance/publication records carrying `bundleVersion`; they cannot alter bundle bytes or evaluation. JCS does not perform Unicode normalization; Strata's declared source normalization occurs before canonicalization.
2. **Integers** must lie within ±(2^53 − 1); the validator rejects anything outside, so JavaScript numbers stay exact. `min` works on integers only.
3. **Decimals** are normalised to a canonical string at compile time. Only equality and `in` apply to them, and evaluators compare the strings.
4. **Strings** are NFC-normalised at compile time and compared by exact, case-sensitive code-point equality.
5. **Booleans are strict.** No coercion: the string `"true"` is not `true`, and neither is `1`. Strings, numbers and booleans are never compared across types.
6. **Ordering.**
   - Set results are ordered as in the key's `universe`.
   - Contributions are ordered from the root level to the most specific, then axes in declared order.
   - Trace entries follow rule order.
7. **No locale or time-zone behaviour in evaluators.** String keys with a declared format, including `time_hhmm` and `iana_tz`, are validated at compile time and returned as strings. Their exact validators and any timezone-data version are defined in Phase 1; evaluators do not perform timezone conversions.

---

## 5. File formats (sample domain A)

> The file **formats** are normative. The **names** and the **layout** are samples: each project chooses its own layout in `strata.yaml` (Section 5.0).

### 5.0 Project manifest: `strata.yaml`

Every project has one `strata.yaml` at its root. It says where things are, which presets apply, and how strict lint is. **The layout is the project's choice.** The manifest is the sole source-project authority for `specVersion`; schema fragments, including `schema/core.yaml`, do not repeat it. Compiled bundles and conformance vectors still carry their own version identification.

```yaml
specVersion: "0.1"

extends:                                        # presets, applied in order; later ones win
  - strata:recommended

sources:
  schema: ["schema/**/*.yaml"]
  values:
    - pattern: "values/{scope}/{node}.yaml"     # level or axis, and node, taken from the path
    - file: "values/overrides.yaml"             # or several nodes in one file
  rules: ["rules/**/*.yaml"]

namespaces:                                     # optional (Section 4.9)
  checkout: { schema: "schema/components/checkout.yaml" }

lint:
  rules:
    naming/key: error
    examples/required-per-binding: warn
    rules/dead-rule: warn
```

- **Path-derived values:** the `{scope}` and `{node}` placeholders map a file to a level or axis, and a node.
- **Inline values:** a values `file` can hold several nodes:

  ```yaml
  division:
    north: { checkout.pickup.lead_hours: 2 }
    south: { checkout.pickup.lead_hours: 6 }
  ```

- A whole project may live in **one file**, if its authors prefer.
- The same key set twice for the same node, from any combination of files, is an error.
- **Presets** are YAML files that can contain schema fragments (kinds, naming patterns, facts) and lint settings. Built-in presets are referenced as `strata:<name>`. Other presets are referenced by relative path, or by URL pinned to a SHA-256 hash, so teams can publish and share their own. A preset cannot disable an engine error.

**Built-in presets** (in `presets/`):

| Preset | For | Contains |
|---|---|---|
| `strata:recommended` | Most teams | Kinds `configuration` and `capability`, naming patterns, ownership checks, strict lint |
| `strata:minimal` | Trying it out; small projects | No kinds, no naming patterns, lint only for likely bugs |
| `strata:entitlements` | Product plans and feature entitlements | Kinds `limit` (`min`), `feature` (`all`) and `grant` (`union`), with entitlement-style naming |

### 5.1 Config repo layout (as used by sample domain A)

```
schema/core.yaml
schema/components/checkout.yaml
values/global.yaml
values/channel/web.yaml
values/division/south.yaml
values/site/site_001.yaml
values/seller/acme.yaml
rules/checkout.order_submission.yaml
```

A values file's path determines its level or axis and its node: `values/division/south.yaml` is level `division`, node `south`.

### 5.2 `schema/core.yaml`

```yaml
# Kinds and naming patterns come from the strata:recommended preset (see strata.yaml).

decision_hierarchy:
  levels: [global, channel, division, site, account, user]
  axes: [seller]
  service_sourced: [account, user]
  nodes:                                  # existing closed-list excerpt; full per-scope sources/policies follow Phase 1 review
    channel: [web, partner_portal]
    division: [north, south, west]
    site: [site_001]
    seller: [acme]

entity_hierarchy:
  customer: {}
  invoice:  { has_many: order }
  order:    { belongs_to: customer, has_many: item }
  item:     {}

facts:
  customer.active:      { type: boolean, source: customer-service }
  customer.credit_hold: { type: boolean, source: credit-service }
  item.stock_type:      { type: enum, values: [STOCKED, SPECIAL_ORDER, DROP_SHIP], source: order }
```

**Node-source format proposal, not a complete schema:** retaining the ordered lists above permits the following per-level source and policy maps. A complete sample also needs explicit declarations for its root and service-sourced levels; those identifiers and root omission behavior are not silently inferred here.

```yaml
# Alternative source forms and explicit policies:
nodes:
  channel: [web, partner_portal]
  site: { from: "generated/sites.json" }
  account: open
on_unknown_node:
  channel: error
  site: error
  account: error
```

The map layout is a concrete proposal for review. The original core excerpt above remains incomplete under the new admission requirements until Phase 1 defines declarations for every scope and the root representation. No excerpt is claimed to validate yet.

### 5.3 `schema/components/checkout.yaml`

```yaml
namespace: checkout

keys:
  checkout.shipping.allowed_methods:
    kind: capability
    combine: intersect
    universe: [GROUND, PICKUP, NEXT_DAY]
    settable_at: [global, channel, division, site, account, seller]

  checkout.container_order.allowed:
    kind: capability
    type: boolean
    combine: all
    settable_at: [channel, division, site, account]

  checkout.order.max_lines:
    kind: capability
    type: integer
    combine: min
    settable_at: [global, division, site]

  checkout.pickup.lead_hours:
    kind: configuration
    type: integer
    combine: override
    min: 0
    max: 168
    default: 4
    settable_at: [global, division, site, seller]
    combine_across_axes: max

features:
  checkout.order_submission:
    decided_per: order
    returns: { enum: [ALLOW, DENY] }
    hit_policy: FIRST
    if_fact_missing: DENY
    binding_settable_at: [global, division, site]
```

### 5.4 `values/division/south.yaml`

```yaml
checkout.shipping.allowed_methods: [GROUND, PICKUP]
checkout.pickup.lead_hours: 6
```

### 5.5 `rules/checkout.order_submission.yaml`

```yaml
feature: checkout.order_submission

policies:
  credit_check:
    settings:
      allowed_on_credit_hold: { list_of: item.stock_type, default: [] }
    default: DENY
    rules:
      - id: good-credit
        name: Good credit
        when:
          customer.active: true
          customer.credit_hold: false
        then: ALLOW
      - id: credit-hold-allowed-stock
        name: On credit hold, every item is an allowed stock type
        when:
          customer.active: true
          customer.credit_hold: true
          item.stock_type: { all_in: $allowed_on_credit_hold }
        then: ALLOW

  allow_all:
    default: ALLOW
    rules: []

bindings:
  global: { policy: credit_check }
  division:
    north: { policy: allow_all }
    south: { policy: credit_check, allowed_on_credit_hold: [STOCKED, SPECIAL_ORDER] }

examples:
  - name: Mixed basket on credit hold is denied
    context: { levels: { division: south } }
    facts: { customer.active: true, customer.credit_hold: true, item.stock_type: [STOCKED, DROP_SHIP] }
    expect: DENY
  - name: Credit hold with only allowed stock is allowed
    context: { levels: { division: south } }
    facts: { customer.active: true, customer.credit_hold: true, item.stock_type: [STOCKED, SPECIAL_ORDER] }
    expect: ALLOW
  - name: Empty order on credit hold is denied
    context: { levels: { division: south } }
    facts: { customer.active: true, customer.credit_hold: true, item.stock_type: [] }
    expect: DENY
  - name: Unknown credit status is denied
    context: { levels: { division: south } }
    facts: { customer.active: true, item.stock_type: [STOCKED] }
    expect: DENY
  - name: North allows everything
    context: { levels: { division: north } }
    facts: { customer.active: false, customer.credit_hold: true, item.stock_type: [DROP_SHIP] }
    expect: ALLOW
```

**Example shorthand.** In rule files, a list value for a single child attribute (`item.stock_type: [STOCKED, DROP_SHIP]`) expands to one child per element. Examples needing several child attributes use the explicit FactSet form from Section 4.4. The conformance suite always uses the explicit form.

### 5.6 Compiled bundle

`strata compile` writes a canonical JSON bundle with two named parts:

- `content`: `bundleFormatVersion`, `specVersion`, the resolved `schema`, all compiled `values` and all compiled `rules` (including policies, bindings and evaluation-relevant definitions);
- `bundleVersion`: `sha256:` followed by the lowercase hexadecimal SHA-256 digest of JCS-encoded `content` alone.

The artifact has exactly these two fields and is serialized as UTF-8 JCS bytes without a byte-order mark or trailing newline. Reject unknown envelope fields rather than tolerating variable metadata inside the bundle. Duplicate JSON property names and invalid JCS inputs must be rejected before they can be lost through permissive parsing.

`gitCommit`, `createdAt` and publisher information belong in a separate provenance sidecar or server publication record, each carrying `bundleVersion`. Such records do not enter evaluation or the content hash. The same version may have several provenance/publication records; preserve them rather than overwriting an earlier record. Rebuilding the same content with different external metadata leaves the entire canonical bundle byte-identical. Metadata transport, sidecar filename/schema and timestamp semantics are Phase 1 format details.

Changing any evaluation-relevant field invalidates the stored digest. A matching digest establishes content integrity, not publisher identity or authenticity of provenance claims. Trust in publisher metadata follows the existing authenticated publication boundary or a separately specified attestation mechanism; a digest reference alone is insufficient. For a stored version, reject a conflicting canonical artifact rather than replacing its bytes.

`spec/schemas/bundle.schema.json` will define the accepted format in Phase 1. Both Java and JavaScript must hash the exact same named object.

### 5.7 Conformance vectors

Evaluator vectors take a **compiled bundle** as input, so JavaScript can run them without a compiler:

```yaml
# spec/conformance/evaluate/decide/mixed-basket-on-credit-hold.yaml
id: decide/mixed-basket-on-credit-hold
specVersion: "0.1"
bundle: ../../bundles/commerce.json
call: decide
context: { levels: { division: south } }
feature: checkout.order_submission
input:
  facts: { customer.active: true, customer.credit_hold: true }
  children:
    item:
      - { item.stock_type: STOCKED }
      - { item.stock_type: DROP_SHIP }
expect:
  effect: DENY
  reason: { type: DEFAULT }
  binding: { level: division, node: south }
```

```yaml
# spec/conformance/evaluate/resolve/intersect-narrows.yaml
id: resolve/intersect-narrows
specVersion: "0.1"
bundle: ../../bundles/commerce.json
call: resolve
context: { levels: { channel: web, division: south } }
key: checkout.shipping.allowed_methods
expect:
  value: [GROUND, PICKUP]
  contributions:
    - { scope: level, name: division, node: south, effect: NARROWED }
```

Compile vectors (Java only, in v1) give a source directory, plus either the expected bundle or the expected **error codes**. Error messages may differ between implementations; **codes must not**.

---

## 6. Checks: engine errors and lint rules

There are two kinds of checks, with different rules:

- **Engine errors** protect the guarantees in Section 1.4. They are always on, always fail the build, and no preset or setting can disable them.
- **Lint rules** encode guidance. Each has a severity (`error`, `warn` or `off`), set by presets and by the project's `strata.yaml`, in the same way ESLint works.

Every finding has a stable code (listed in `spec/error-codes.md`), the file, the YAML path, the line, and where possible a suggestion, e.g. `E_UNKNOWN_KEY: checkout.shiping.allowed_methods — did you mean checkout.shipping.allowed_methods?`. `strata explain <code>` prints the rationale and how to fix it.

### 6.1 Engine errors (always on)

| Rule | Code |
|---|---|
| Every key used in a values file is declared | `E_UNKNOWN_KEY` |
| Every source level and axis is declared; source values and bindings use known IDs in closed catalogs under either runtime policy. Runtime contexts follow the explicit admission policy in Section 4.1 | `E_UNKNOWN_SCOPE`, `E_UNKNOWN_NODE` |
| Values appear only at levels and axes in `settable_at` | `E_NOT_SETTABLE_HERE` |
| Values match their type and range | `E_TYPE_MISMATCH`, `E_OUT_OF_RANGE` |
| Set values are subsets of the universe | `E_NOT_IN_UNIVERSE` |
| A key's strategy fits its value type | `E_STRATEGY_TYPE` |
| Order-dependent keys have a default, or a root-level value | `E_MISSING_DEFAULT` |
| Order-dependent keys settable on an axis declare `combine_across_axes` | `E_MISSING_AXIS_COMBINE` |
| Keys at service-sourced levels declare `on_source_failure`; `deny` is used only with narrowing strategies | `E_MISSING_SOURCE_FAILURE`, `E_DENY_NOT_NARROWING` |
| The same key is never set twice for one node | `E_DUPLICATE_VALUE` |
| Keys meet their kind's constraints (when kinds are used) | `E_KIND_VIOLATION` |
| Namespaced files stay inside their namespace (when namespaces are used) | `E_NAMESPACE_VIOLATION` |
| Facts in rules and examples are declared, with legal values | `E_UNKNOWN_FACT`, `E_ILLEGAL_FACT_VALUE` |
| Child facts use `all_in` / `any_in` | `E_QUANTIFIER_REQUIRED` |
| Single-value facts don't use quantifiers | `E_QUANTIFIER_NOT_ALLOWED` |
| Facts deeper than one level are rejected (spec v0.x) | `E_DEPTH_UNSUPPORTED` |
| Every `$setting` is declared, and binding settings are valid | `E_UNKNOWN_SETTING`, `E_INVALID_SETTING` |
| Effects match the feature's `returns` | `E_EFFECT_MISMATCH` |
| Every feature definition allows bindings at the first hierarchy level | `E_ROOT_BINDING_NOT_ALLOWED` (provisional review spelling) |
| Every feature has an actual root-level binding, and bindings appear only at `binding_settable_at` | `E_ROOT_BINDING_MISSING`, `E_BINDING_NOT_ALLOWED` |
| Rule IDs are unique within a policy | `E_DUPLICATE_RULE_ID` |
| Integers are within ±(2^53 − 1) | `E_INTEGER_RANGE` |
| Examples pass | `E_EXAMPLE_FAILED` |
| Presets resolve, and URL presets match their pinned hash | `E_PRESET` |

### 6.2 Lint rules (severity set by presets and by the project)

| Rule | Flags | `recommended` | `minimal` |
|---|---|---|---|
| `naming/*` | Names that don't match the declared patterns (only when patterns are declared) | error | off |
| `values/no-op` | A value that can't change the result, e.g. `true` under `all` | error | warn |
| `rules/dead-rule` | A rule that can never match | warn | warn |
| `examples/required-per-binding` | A binding with no examples | error | off |
| `examples/mixed-collection` | A policy using `all_in` / `any_in` with no example that has a mixed collection | warn | off |
| `schema/unused` | Keys, facts or policies declared but never used | warn | off |
| `schema/description-required` | Keys, features or facts without a description | warn | off |
| `schema/owner-required` | Keys without an owner, when namespaces are used | error | off |

---

## 7. Public APIs (sketches: refine them, but keep them this small)

### 7.1 Java

```java
public interface Key<T> { String name(); ValueType<T> type(); }   // typed by value type, never by kind; codegen creates the constants
public interface Feature<R> { String name(); }

public record Context(Map<String, String> levels, Map<String, String> axes) {
  public static Builder builder() { ... }   // .level("division", "south").axis("seller", "acme"); checked against the schema
}

public interface Resolver       { <T> Resolved<T> resolve(Context ctx, Key<T> key); }
public interface DecisionEngine { <R> Decision<R> decide(Context ctx, Feature<R> feature, FactSet facts); }

public interface LevelValueProvider {            // one per service-sourced level
  String level();
  Map<String, Object> valuesFor(String nodeId) throws SourceUnavailableException;
}

public final class Strata {
  public static ValidationReport validate(Path sourceRoot);
  public static CompiledBundle compile(Path sourceRoot);
  public static CompiledBundle loadBundle(InputStream json);   // verifies hash and spec version
  public static Evaluator evaluator(CompiledBundle b, List<LevelValueProvider> providers);
}
```

### 7.2 TypeScript

```ts
import { loadBundle, createEvaluator } from "@strata/evaluator";

const bundle = loadBundle(json);                 // verifies hash and spec version
const strata = createEvaluator(bundle, {
  providers: { account: async (nodeId) => fetchAccountValues(nodeId) },
});

const ctx = { levels: { division: "south" }, axes: { seller: "acme" } };

const r = await strata.resolve(ctx, "checkout.shipping.allowed_methods");
// r.value, r.contributions, r.bundleVersion

const d = await strata.decide(ctx, "checkout.order_submission", {
  facts: { "customer.active": true, "customer.credit_hold": true },
  children: { item: [{ "item.stock_type": "STOCKED" }] },
});
// d.effect, d.reason, d.trace

// When no service-sourced level is involved:
strata.resolveSync(ctx, "checkout.pickup.lead_hours");
```

`strata codegen --lang ts` generates types, so key names, feature names and result types are checked at compile time.

**Both languages:** callers go only through resolve and decide. No API exposes one level's raw value; reading a single level directly is a misuse the API should make impossible.

---

## 8. Runtime integrations

### Spring Boot starter

- **Bundle sources:** server URL, HTTP URL, S3, or a classpath snapshot.
- **Refresh:** poll with ETag (default every 30 s) and swap atomically. Evaluations in flight finish on the old bundle.
- **Last-known-good:** if a refresh fails, keep serving the current bundle and expose its staleness.
- **Startup:** fail fast if no bundle can be loaded, unless a snapshot is configured.
- **Metrics (Micrometer):** bundle version, bundle age, refresh failures, decisions by feature and reason, provider failures.
- **Logging:** every result carries `bundleVersion`. Log it with every decision (log level configurable).

### `@strata/node`

- The same refresh behaviour as the starter, using built-in `fetch` (Node 20+). No other runtime dependencies.
- Exposes the bundle version and staleness for the application's own metrics.

### OpenFeature providers (Java and JS)

- Keys map to flag keys: booleans, strings, numbers, and objects or sets.
- Features map to string flags named after the feature. Facts come from the evaluation context, under the attribute `strata.facts`.
- `reason` and provenance are reported in flag metadata.
- Document the mapping in a spec appendix.

---

## 9. Server (control plane)

Stateless, horizontally scalable, and **never the source of truth**. Publishes `openapi.yaml`.

| Endpoint | Purpose |
|---|---|
| `POST /v1/bundles` | Publish a compiled bundle. Restricted to the CI identity. Re-validates and re-runs examples before accepting. |
| `GET /v1/bundles/latest` | Latest bundle, with an ETag equal to `bundleVersion` |
| `GET /v1/bundles/{version}` | A specific version |
| `GET /v1/bundles` | History: version, commit, time, publisher |
| `POST /v1/bundles/{version}/promote` | Roll back or forward by making a stored version the latest. Restricted. |
| `POST /v1/resolve` | `{ context, keys[] }` → resolved values with provenance |
| `POST /v1/decide` | `{ context, feature, input }` → decision with trace |
| `GET /v1/schema` | Browse keys, features, facts and policies |

- **Storage:** an interface, with S3 and filesystem implementations. Other stores can be contributed.
- **Auth:** pluggable, with OIDC/JWT as the default. No default credentials. Publish and promote are limited to configured roles.
- `/resolve` and `/decide` use `strata-core`, so for the same bundle they always match in-process results.
- Latest and version reads serve the stored canonical bundle unchanged. Commit, time and publisher in history come from separate publication records bound to `bundleVersion`; multiple publications of identical content may share that version. Publishing metadata does not mutate the stored bundle. Phase 1 defines the record and upload representation without changing the endpoint inventory above.

---

## 10. Out of scope (do not build)

- **A general-purpose rules engine or expression language.** No scripting, and no SpEL (it can invoke arbitrary methods). If richer expressions are needed later, propose CEL in an RFC. Don't extend the YAML grammar ad hoc.
- A validator or compiler in JavaScript (v1). JS users run the CLI.
- A UI for editing values.
- Editing schema or values through the server.
- Feature-flag functionality: release toggles, experiments, kill switches.
- Numeric comparisons such as `gt` and `lt`: deferred to an accepted RFC; do not add operators ad hoc.
- Rules reading resolved keys: deferred to an accepted RFC; rules currently read declared facts/settings only.
- Nested quantifiers beyond one level down (spec v0.x): deferred to an accepted RFC.
- Plugins for custom operators or merge strategies. Every strategy must behave identically in Java and JavaScript, so new ones are added to the spec by RFC, not loaded as plugins.
- Any way to disable engine errors (Section 6.1) through presets or settings.
- Caching resolved values across requests. Measure first.
- Using browser evaluation for enforcement.

---

## 11. Phases (stop for review after each)

| Phase | Deliverable | Done when |
|---|---|---|
| 0. Foundations | Monorepo, license and project files (1.2), CI for Java and JS, your open questions | CI is green on an empty build; questions answered |
| 1. Spec v0.1 | `spec.md` (all of Section 4), the `strata.yaml` manifest format, JSON Schemas, the built-in presets, the catalogue of errors and lint rules, conformance vectors for domains A and B. All **written before any implementation**. | Reviewed and accepted. This is the most important review. |
| 2. Java validation | Model, loader, validator | All compile vectors pass; every rule in Section 6 has a positive and a negative test |
| 3. Java evaluation | Compiler, resolver, decision engine | All evaluator vectors pass for both domains |
| 4. JS evaluator | `@strata/evaluator`, Node and browser builds, ESM + CJS + types | The same evaluator vectors pass; zero runtime dependencies |
| 5. Toolchain | CLI (`init`, `validate`, `lint`, `test`, `compile`, `codegen --lang java\|ts`, `explain`), native binaries, Docker image, GitHub Action | `strata init` with each built-in preset produces a project that validates; a sample repo validates and compiles in a GitHub Actions workflow |
| 6. Runtimes | Spring Boot starter, `@strata/node` | Integration tests in Section 12 pass |
| 7. Server | Section 9, plus the OpenAPI spec | End-to-end test passes |
| 8. OpenFeature | Java and JS providers | Provider tests pass, mapping documented |
| 9. Release 0.1.0 | Docs site with quickstarts for both languages, publish workflows (Maven Central, npm), signed artifacts, SBOM | A clean machine follows the quickstart in under 5 minutes, in each language |

---

## 12. Testing requirements

**Conformance (both languages).** Every evaluator vector passes in Java and in JavaScript. Both test runners load vectors directly from `spec/conformance/`. Never copy the vectors.

**Unit tests**, one named test per semantic rule. Test what the code *does*, not how it's built.

**Property-based tests** (jqwik in Java, fast-check in JS), over generated schemas with arbitrary names, and generated values:

- For every narrowing strategy, adding a value at any level never widens the result.
- For every commutative strategy, the result doesn't depend on the order in which levels and axes are applied.
- A configuration value set at the most specific level in the context always wins.
- `all_in` over an empty collection is always false.
- **Java and JS agree:** a generator produces random bundles, contexts and facts; both evaluators run them, and their outputs must be identical (run nightly).

**Regression tests, required, in both sample domains:**

- A mixed basket under the restrictive policy is denied.
- A child fact without a quantifier is rejected with `E_QUANTIFIER_REQUIRED`.
- A missing fact returns `if_fact_missing` whatever the rule order. Prove it by reordering rules.
- An unlisted runtime node under a closed/generated source with declared `on_unknown_node: error` is rejected with `E_UNKNOWN_NODE`. Under `ignore`, skip only that scope, call no provider for it, preserve other scopes and record ordered `skippedScopes`. Validate the entire context before any provider call. Source values/bindings with unlisted closed-catalog IDs are rejected under both policies. Cover open sources and compiled generated lists in both evaluators.
- The JS evaluator rejects `"true"` as a boolean fact value (strictness).

**Golden validation tests:** each invalid fixture produces exactly its expected codes, files, paths and lines.

**Integration tests:**

- The starter and `@strata/node` against a stub server: refresh, ETag, atomic swap under concurrent evaluation, last-known-good when the server is down, startup without a bundle.
- Provider timeouts produce the key's declared `on_source_failure` behavior. Cover both `skip` and `deny`, using custom kinds and keys without a kind; do not infer failure behavior from preset kind names.

**End-to-end test:** compile a sample repo → publish to the server → a Java service and a Node service both pick up the new version and return identical decisions → promote the previous version → both roll back.

**Performance:** JMH (Java) and a benchmark harness (JS) for `resolve` and `decide`, against a bundle of about 1,000 keys and 50 features. Target in-process p99 under 100 µs, excluding provider calls. Report the numbers; don't optimise before measuring.

---

## 13. Non-functional requirements

- **Java:** core, CLI and OpenFeature provider target Java 17+, for wide adoption; the starter and server target Spring Boot 3.x on Java 21. Build with Maven.
- **JavaScript:** TypeScript in strict mode; ESM and CJS builds with type definitions; Node 20+; browsers supporting ES2020. `@strata/evaluator` has zero runtime dependencies.
- **Immutability:** compiled models are immutable and thread-safe (Java), and frozen (JS).
- **Determinism:** the same sources always produce byte-identical canonical `{ bundleVersion, content }` bundles and the same `bundleVersion`. The named `content` object determines the digest. Variable build/publication metadata is stored separately, bound to that version; changes to it cannot alter bundle bytes. Its own bytes are not part of the canonical bundle reproducibility promise.
- **Dependencies:** minimal in every core package. Justify each new one in an ADR.

---

## 14. Open questions (ask in Phase 0; defaults apply if unanswered)

| Question | Default |
|---|---|
| Final project name, npm scope, Maven `groupId` | Placeholders; check availability first |
| GitHub home: personal account or an organisation | An organisation, for long-term ownership |
| Contribution sign-off: DCO or CLA | DCO |
| Governance model | A maintainers file; spec changes by RFC with maintainer approval |
| Docs site tool | MkDocs Material |
| Java baseline for core: 17 or 21 | 17 |
| Bundle storage defaults for the server | S3 and filesystem |
| Refresh interval and staleness alert | Poll every 30 s; alert at 5 minutes |

---

## 15. Acceptance criteria

- [ ] `spec/spec.md` fully specifies Section 4, and every rule has at least one conformance vector.
- [ ] The Java and JS evaluators pass 100% of the evaluator conformance vectors for both sample domains.
- [ ] The cross-language property test has run for at least one night with zero disagreements.
- [ ] Every engine error and lint rule in Section 6 is implemented, with stable codes and golden tests.
- [ ] No domain term appears in production code, and the genericity check runs in CI.
- [ ] No modeling choice (kind names, naming patterns, layout, ownership) is hardcoded. Each comes from the schema, `strata.yaml` or a preset.
- [ ] All of these validate and evaluate correctly: a project for each built-in preset, a single-file project with no kinds or namespaces, and a project with custom kinds.
- [ ] Every result carries provenance and `bundleVersion`.
- [ ] Applications evaluate in-process, and keep working with the server down.
- [ ] Server `/resolve` and `/decide` match in-process results for the same bundle.
- [ ] The CLI is available as a native binary, a Docker image and a GitHub Action.
- [ ] The README has the positioning table and a 5-minute quickstart for Java and for JavaScript.
- [ ] All project files from Section 1.2 are present; releases are signed and ship an SBOM.
- [ ] ADRs exist for every decision not dictated by this prompt.
