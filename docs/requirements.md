# Tierwise requirements

**Status:** baseline for the Phase 1 specification, 2026-10-08. This document
consolidates the [original brief](history/build-brief-2026-10-07.md), the applied
prompt review and ADRs 0005–0012. Section 4 is the **normative draft**. Phase 1
turns it into `spec/spec.md`, after which the spec is authoritative and this
section is only background.

Read [the vision](vision.md) for direction and [the roadmap](roadmap.md) for
sequencing.

---

## 1. Guarantees and constraints

### 1.1 Genericity is a hard requirement

The project must work for **any** hierarchy, entities, facts and features. Every
domain name in this document (`global`, `division`, `seller`, `order`,
`credit_hold`, `ALLOW`, …) comes from a sample domain.

- **No domain terms in production code.** Nothing in `java/**/src/main` or
  `js/**/src` may contain a level, axis, node, entity, fact, key, feature, policy or
  effect name. All of them come from the bundle at runtime, or from code generated
  from it.
- **Structure is positional or declared, never named.**
  - The root level is the first entry in `decision_hierarchy.levels`.
  - The most specific level is the last entry.
  - Service-sourced levels are the ones declared with `values: service`.
  - Effects are whatever a feature's `returns` declares.
- **Kind names and naming patterns live in presets or the project's schema, never
  in engine code.** The engine has no concept of "configuration" or "capability".
- **Two unrelated domains**, both used by the conformance suite:
  - **A, `samples/commerce/`**: levels `global → channel → division → site →
    account → user`; axis `seller`; `account` and `user` service-sourced; entities
    `customer`, `invoice → order → item`.
  - **B, `samples/retail-pos/`**: levels `platform → region → store → terminal`;
    axis `brand`; `terminal` service-sourced; entities `shopper`, and `basket`
    (belongs to shopper, has many `line`); a basket-level feature returning
    `{ APPROVE, REVIEW, REJECT }`.
  - **Docs-only, `samples/saas-entitlements/`**: `plan → org → workspace → user`,
    showing the `tierwise:entitlements` preset including `union` grants.
- **CI enforces genericity.** It collects every name declared in the sample schemas
  and fails if any appears as a string literal or identifier in production code.
  Generic words that collide (e.g. `line`) are allow-listed explicitly, with a
  comment.

### 1.2 Open-source requirements

- Apache-2.0 with `LICENSE` and `NOTICE`; DCO sign-off; Contributor Covenant;
  `SECURITY.md` with private reporting; `GOVERNANCE.md` (maintainers, decisions,
  RFCs); Keep a Changelog; issue and PR templates.
- No organisation-specific content anywhere.
- **Versioning.** Every package follows SemVer. The **spec** (`specVersion`) and
  the **bundle format** (`bundleFormatVersion`) are versioned independently.
  Packages declare the spec versions they support. Breaking spec changes need an
  RFC and a major spec version.
- **Supply chain.** Reproducible builds, Sigstore-signed release artifacts, a
  CycloneDX SBOM per release, Dependabot, and an OpenSSF Scorecard workflow.
- **Docs.** A site generated from `docs/` (MkDocs Material), plus Javadoc and
  TypeDoc.
- **Small public APIs.** Anything not explicitly public is internal.

### 1.3 Two languages, one spec

| Capability | Java | JavaScript / TypeScript |
| --- | --- | --- |
| Load, validate, compile sources | ✅ reference toolchain | ❌ (v1: use the CLI) |
| Evaluate bundles (`resolve`, `decide`) | ✅ | ✅ Node and browser |
| CLI: `init`, `validate`, `lint`, `test`, `compile`, `codegen`, `explain` | ✅ fat JAR, native binaries, Docker image, GitHub Action | uses the same CLI |
| Codegen for typed handles | ✅ Java constants | ✅ TypeScript types (same CLI) |
| Runtime integration | Spring Boot starter | `@tierwise/node` |
| Control plane | ✅ `tierwise-server` | call over HTTP (OpenAPI) |
| OpenFeature provider | ✅ | ✅ |

- Both evaluators must pass the same conformance suite (`spec/conformance/`). A
  release is blocked unless both pass the conformance version it claims.
- **Browser evaluation is for display only.** Enforcement always happens
  server-side. State this in the docs and the JS README.

### 1.4 Mechanism, not policy

| Layer | Contains | Decided by | User can change? |
| --- | --- | --- | --- |
| 1. Engine guarantees | Cross-language determinism; validation of everything declared; unknown levels, axes and keys rejected; explicit node admission (no implicit fail-open); explicit quantifiers; provenance on every result | The spec | **No** |
| 2. The user's model | Levels, axes, entities, facts, keys and strategies, kinds, naming patterns, layout, namespaces, features, policies, failure behaviour, defaults | Each project | Yes, completely |
| 3. Guidance | Presets, `tierwise init` templates, lint severities, modeling guide, `tierwise explain` | The project ships it; users opt in | Yes |

Test every proposed behaviour with one question: *is this a guarantee every user
needs, or a choice one team made?* Choices belong in layer 2 or 3. Presets can
tighten checks but can never disable an engine guarantee.

---

## 2. Problem and positioning

See [the vision](vision.md#the-problem). The README uses the positioning table
from [the vision](vision.md#positioning).

---

## 3. What to build

### 3.1 Repository layout

```
tierwise/
├── spec/
│   ├── spec.md                 normative, language-neutral specification
│   ├── error-codes.md          stable engine, evaluation and lint codes
│   ├── schemas/                JSON Schemas: manifest, sources, bundle, vectors
│   ├── data/tz-names.txt       pinned IANA zone names (ADR 0011)
│   └── conformance/
│       ├── bundles/            compiled bundles for domains A and B
│       ├── evaluate/           resolve/decide vectors (Java AND JS)
│       ├── canonical/          JCS and SHA-256 byte vectors (Java AND JS)
│       └── compile/            source → bundle or error vectors (Java)
├── java/                       Maven multi-module (Maven Wrapper)
│   ├── tierwise-core/
│   ├── tierwise-cli/
│   ├── tierwise-spring-boot-starter/
│   ├── tierwise-server/
│   └── tierwise-openfeature/
├── js/                         pnpm workspace, TypeScript
│   ├── evaluator/              @tierwise/evaluator  (zero runtime deps)
│   ├── node/                   @tierwise/node
│   └── openfeature/            @tierwise/openfeature
├── presets/                    recommended, minimal, entitlements
├── samples/                    commerce, retail-pos, saas-entitlements
└── docs/                       site source, adr/, history/
```

### 3.2 Components

| Component | Purpose | Allowed dependencies |
| --- | --- | --- |
| `tierwise-core` | Model, loading, validation, compilation, resolution, decisions, provenance. No Spring, no network, no logging framework. | snakeyaml-engine, jackson-core ([ADR 0012](adr/0012-parsing-and-dependency-baseline.md)) |
| `tierwise-cli` | The seven commands; fat JAR, GraalVM native binaries (Linux, macOS, Windows), Docker image, GitHub Action | core, picocli |
| `tierwise-spring-boot-starter` | Bundle loading and refresh; `Resolver` and `DecisionEngine` beans; Micrometer metrics; provider wiring | core, Spring Boot |
| `tierwise-server` | Control plane (Section 9) | core, Spring Boot |
| `tierwise-openfeature` | OpenFeature provider | core, OpenFeature Java SDK |
| `@tierwise/evaluator` | Bundle loading, `resolve`, `decide`, provenance; Node and browser | **none** |
| `@tierwise/node` | Fetch, ETag refresh, last-known-good, atomic swap | `@tierwise/evaluator` |
| `@tierwise/openfeature` | OpenFeature provider (server and web SDKs) | `@tierwise/evaluator`, OpenFeature JS SDK |

### 3.3 Architecture

```
config repo (git) ── the ONLY source of truth
  tierwise.yaml   layout, presets, lint settings
  schema/**       hierarchy, scopes, entities, facts, keys, features
  values/**       values per scope and node
  rules/**        policies, bindings, examples per feature
        │  PR → CI: tierwise validate · test · compile
        ▼
  bundle.json     canonical JCS bytes, content-hashed, immutable
        │  CI publishes (authenticated)
        ▼
  tierwise-server (optional control plane): stores every version, serves latest,
  rollback, /resolve and /decide over OpenAPI
        │  ETag polling (or any HTTP endpoint / object store)
        ├──────────────────────────┐
        ▼                          ▼
  Java services                Node / browser apps
  evaluate IN-PROCESS          evaluate IN-PROCESS (browser: display only)
```

- Git is the source of truth. The server never edits schema or values.
- Evaluation is in-process. The server is never on the hot path.
- One bundle version means one answer, in both languages.

---

## 4. Semantics (normative draft)

### 4.1 The decision hierarchy and scopes

The decision hierarchy is an ordered list of **levels**, least to most specific.
It may also declare **axes**, which cut across levels (one seller can appear under
many divisions). Levels and axes together are **scopes**.

- The **root level** (first entry) is a singleton. It has no catalog and is always
  in effect, and contexts must not name it ([ADR 0007](adr/0007-hierarchy-root-and-admission.md)).
  Its values and bindings are written without a node ID, and its provenance entries
  omit `node`.
- Every other level, and every axis, has one entry in `scopes`:

  ```yaml
  scopes:
    channel:  { nodes: [web, partner_portal], on_unknown_node: error }
    site:     { nodes: { from: catalogs/sites.json }, on_unknown_node: ignore }
    account:  { nodes: open, values: service }
  ```

  - `nodes`: an inline list, `{ from: <path> }` (a JSON `{ "nodes": [...] }` file,
    embedded in the bundle at compile time), or `open`.
  - `on_unknown_node: error | ignore`: required for closed catalogs; forbidden for
    `open`.
  - `values: files | service`: defaults to `files`; `service` is allowed only on
    levels.
- A **context** names at most one node per non-root level and per axis:
  `{ levels: { division: south }, axes: { seller: acme } }`. Missing levels are
  skipped. Implementations never infer parent relationships between nodes; the
  context supplies the path.
- **Node IDs** are non-empty, at most 128 code points, contain no control
  characters, and are NFC in sources.
- **Context validation happens before any lookup or provider call.** It checks the
  structure, the scope names, the ID contract and admission. Under `ignore`, an
  unlisted closed-catalog ID skips that scope entirely: no values, no binding, no
  provider call, no `on_source_failure`. The skipped scope is recorded in
  `skippedScopes`. Other scopes stay eligible ([ADR 0005](adr/0005-node-admission-and-canonical-artifacts.md)).
- **Compilation is strict under both policies.** Source values and bindings that
  use an unlisted closed-catalog ID are `E_UNKNOWN_NODE`.

### 4.2 The entity hierarchy

```yaml
entity_hierarchy:
  customer: {}
  invoice:  { has_many: order }
  order:    { belongs_to: customer, has_many: item }
  item:     {}
```

- `belongs_to` declares to-one ancestors (transitive). `has_many` declares child
  collections. No inverse is inferred. Cycles, or an entity that is both an
  ancestor and a child of the same entity, are `E_ENTITY_GRAPH`
  ([ADR 0010](adr/0010-entities-facts-and-rules.md)).
- Every fact is `<entity>.<attribute>` for a declared entity. Every feature is
  decided at exactly one entity (`decided_per`).

### 4.3 Keys, types and merge strategies

Every key declares `type`, `combine` and `settable_at`
([ADR 0011](adr/0011-source-format-and-types.md)):

| `type` | Extra fields |
| --- | --- |
| `boolean` | — |
| `integer` | `min`, `max` (within ±(2^53−1)) |
| `decimal` | — (canonical decimal strings) |
| `string` | `format: time_hhmm \| iana_tz` (optional) |
| `enum` | `values` |
| `set` | `universe` |
| `list` | `items` (with `override` or as an object field) |
| `object` | `fields` (with `optional: true` per field; unknown fields rejected) |

`null` is never a valid value.

| Strategy | Types | Result | Identity / unset | Narrowing |
| --- | --- | --- | --- | --- |
| `override` | any | Most specific level's value | `default` | — |
| `deep_merge` | object | Recursive field merge, most specific wins; lists replaced | `default` | — |
| `all` | boolean | AND | `true` | ✅ |
| `any` | boolean | OR | `false` | — |
| `intersect` | set | universe ∩ values | full universe | ✅ |
| `union` | set | ∪ values | `[]` | — |
| `min` | integer | minimum | absent (`null`) | ✅ |
| `max` | integer | maximum | absent (`null`) | — |

Rules ([ADR 0008](adr/0008-merge-semantics.md)):

- Values may appear only at the scopes in `settable_at`.
- Commutative keys may not declare `default`.
- Order-dependent keys need a `default` or a root value. For `deep_merge`, the
  starting value must be a complete object.
- The default is the lowest fallback: it applies only where no level and no axis
  sets a value.
- **Axes.**
  - For commutative strategies, axis values are just more inputs.
  - For order-dependent keys settable on an axis, `combine_across_axes:
    axis_wins | hierarchy_wins | max | min` is required (`max`/`min` for integer
    keys only).
  - The hierarchy result and the axis result (folded in declared axis order) are
    computed separately, then combined. If only one of them exists, it is used.
- **Kinds** (optional, declared by the project or a preset) constrain the keys that
  opt in: `strategies`, `narrowing_only`, `require_default`, `on_source_failure`,
  `naming`.
  - The engine enforces exactly what a kind declares.
  - `narrowing_only` rejects values equal to the strategy identity (`true` under
    `all`, the full universe under `intersect`).

### 4.4 Service-sourced levels

Values for `values: service` levels come from a provider
([ADR 0009](adr/0009-providers-and-evaluation-errors.md)).

- Every key settable at a service-sourced level declares `on_source_failure`,
  directly or through its kind. There is no default (`E_MISSING_SOURCE_FAILURE`).
  - `skip`: the level contributes nothing.
  - `deny`: the level contributes the most restrictive value: `false` for `all`,
    `[]` for `intersect`, the declared `min` bound for `min`. `deny` is allowed
    only on narrowing strategies.
- A provider is called at most once per scope per evaluation call, and only when
  needed. A throw or timeout makes every key being resolved at that level
  `UNAVAILABLE`. An invalid entry makes only that key `INVALID`; an entry for a key not settable at that level is ignored and reported to the listener (ADR 0013).
  Omitted keys set nothing. Unknown keys are ignored and reported to the
  `EvaluationListener`.
- A missing provider fails at evaluator creation (`E_PROVIDER_MISSING`).
- Bindings can't be placed at service-sourced levels, so `decide` never calls a
  provider.

### 4.5 Facts and FactSets

Facts are declared in shared schema only: name `<entity>.<attribute>`; type
`boolean`, `string`, `integer`, `decimal` or `enum`; and `source`, which is
documentation only.

```yaml
facts:                       # decided entity and its ancestors
  customer.active: true
  customer.credit_hold: true
children:                    # direct has_many entities
  item:
    - { item.stock_type: STOCKED }
    - { item.stock_type: DROP_SHIP }
```

- An absent fact and a `null` fact are both **missing**.
- An omitted child collection makes that child's facts **missing**. An empty list is
  **present and empty**.
- Undeclared, out-of-scope or wrongly typed facts raise `E_FACTS_INVALID`.

### 4.6 Features, policies and bindings

**Feature** (component schema):

| Field | Meaning |
| --- | --- |
| `decided_per` | The entity the decision is about |
| `returns` | `{ enum: [...] }`, optionally `nullable: true` (JSON `null`), or `{ object: <fields> }` |
| `if_fact_missing` | The effect returned when the precheck fails |
| `hit_policy` | Required; spec v0.x defines only `FIRST` |
| `binding_settable_at` | Required. Must include the root level (`E_ROOT_BINDING_NOT_ALLOWED`) and no service-sourced level (`E_BINDING_NOT_ALLOWED`) |

**Policy** (`rules/<feature>.yaml`):

| Field | Meaning |
| --- | --- |
| `settings` | `name → { value_of: <fact>, default }` or `{ list_of: <fact>, default }`; `default` required |
| `default` | The effect when no rule matches |
| `rules` | An ordered list of `{ id, name, when, then }`. Rules reference settings as `$name`. |

**Binding**: `{ policy: <name>, <setting>: <value>, ... }` at levels in
`binding_settable_at`.

- The most specific binding on the context path replaces all others entirely.
  Rules and settings are never merged.
- Every feature has a root binding (`E_ROOT_BINDING_MISSING`). Because the root is
  always in effect, every decision has a binding.

### 4.7 Rule evaluation

1. **`FIRST` hit policy.** Rules are checked top to bottom, and the first rule whose
   conditions all hold decides.
2. **Conditions in a rule are ANDed.** An empty `when` always matches. Conditions
   are checked in code-point order of fact name.
3. **Operators:**

   | Operator | Applies to | Meaning | Empty collection |
   | --- | --- | --- | --- |
   | `fact: value` or `fact: $s` | single-value fact | equals | n/a |
   | `fact: { in: [...] \| $s }` | single-value fact | one of | n/a |
   | `fact: { all_in: [...] \| $s }` | child fact | ∀ child: value ∈ list | **false** |
   | `fact: { any_in: [...] \| $s }` | child fact | ∃ child: value ∈ list | false |

4. Child facts must use `all_in` or `any_in` (`E_QUANTIFIER_REQUIRED`).
   Single-value facts must not (`E_QUANTIFIER_NOT_ALLOWED`). **This is the most
   important validation in the project.**
5. Only facts one level down are supported in spec v0.x. Deeper references are
   `E_DEPTH_UNSUPPORTED`. The condition model must allow nested quantifiers to be
   added later by RFC.
6. **Strict, policy-wide missing-fact precheck.** Before any rule runs: if any fact
   referenced anywhere in the selected policy is missing, return `if_fact_missing`
   with reason `{ type: MISSING_FACT, facts: [<all missing, code-point order>] }`.
   The result never depends on rule order.
7. If no rule matches, return the policy `default` with reason `DEFAULT`.

### 4.8 Provenance

```
Resolved  { key, strategy, kind?, value, bundleVersion,
            skippedScopes: [ { scope, name, node, reason: UNKNOWN_NODE } ],
            contributions: [ { scope, name, node?, value?, effect, onSourceFailure? } ],
            source?: DEFAULT | { scope, name, node? } }      // order-dependent only

Decision  { feature, effect, bundleVersion,
            skippedScopes: [ ... ],
            binding: { level, node? }, policy,
            reason: { type: RULE, id, name } | { type: DEFAULT }
                  | { type: MISSING_FACT, facts: [...] },
            trace: [ { id, matched, failed?: { fact, op } } ] }
```

- `effect` ∈ `SET | VETO | NARROWED | WIDENED | IGNORED | UNAVAILABLE | INVALID`.
  It is computed from each value alone against the strategy identity, so it never
  depends on combination order. Every veto and every narrowing is listed
  ([ADR 0008](adr/0008-merge-semantics.md#6-contribution-effects-describe-the-value-not-its-marginal-impact)).
- Contributions are ordered root → most specific, then axes in declared order.
  `skippedScopes` use the same order and are never shown as value contributions.
- The trace lists rules up to and including the winner. It is empty for
  `MISSING_FACT`.

### 4.9 Naming conventions (declared by the project)

Patterns come from the schema or a preset, and the lint severities are set by the
project. A project that declares no patterns gets no naming checks. The one
structural rule: fact names are `<declared entity>.<attribute>`.

`tierwise:recommended` declares:

| Thing | Pattern | Example |
| --- | --- | --- |
| Key | `^[a-z]+(\.[a-z_]+){2}$` | `checkout.pickup.lead_hours` |
| `capability`-kind key | `\.(allowed\|allow_[a-z_]+\|allowed_[a-z_]+)$` | `checkout.payment.allowed_methods` |
| Enum value | `^[A-Z][A-Z0-9_]*$` | `DROP_SHIP` |
| Node ID | `^[a-z][a-z0-9_]*$` | `north` |
| Feature | `^[a-z]+\.[a-z_]+$` | `checkout.order_submission` |

### 4.10 Ownership (optional)

With `namespaces` declared in the manifest, the hierarchy, entities, facts and kinds
live only in shared files. A namespaced file declares keys and features in its own
namespace only (`E_NAMESPACE_VIOLATION`).

### 4.11 Cross-language determinism

1. **Canonical bundle.** `content` holds `bundleFormatVersion`, `specVersion`, and
   every evaluation-relevant input, including the embedded catalogs and admission
   policies. `bundleVersion = "sha256:" + hex(SHA-256(JCS(content)))`. The artifact
   is exactly the UTF-8 JCS bytes of `{ bundleVersion, content }`: no BOM, no
   trailing newline, no other fields. Duplicate properties and unknown envelope
   fields are rejected. Provenance metadata (`gitCommit`, `createdAt`, publisher)
   lives in separate records bound to `bundleVersion`.
2. **Numbers.** Integers within ±(2^53 − 1); the bundle contains no other numbers.
3. **Decimals** are canonical strings, and only equality and `in` apply to them.
4. **Strings** are NFC-normalised at compile time and compared by exact code point.
   Evaluators don't normalise.
5. **Booleans are strict.** There is no coercion between types.
6. **Ordering.** Set results follow universe order; contributions follow scope
   order; catalogs are sorted by code point; conditions are sorted by fact name;
   traces follow rule order.
7. **No locale or time-zone behaviour** in evaluators.

---

## 5. File formats (sample domain A)

The **formats** are normative. The **names and layout** are examples; each project
chooses its own in `tierwise.yaml`.

### 5.1 `tierwise.yaml`

```yaml
specVersion: "0.1"

extends:                      # applied in order; later ones win (ADR 0011)
  - tierwise:recommended

sources:
  schema: ["schema/**/*.yaml"]
  values:
    - file: "values/global.yaml"
      scope: global
    - pattern: "values/{scope}/{node}.yaml"
  rules: ["rules/**/*.yaml"]

namespaces:
  checkout: { schema: "schema/components/checkout.yaml" }

lint:
  rules:
    naming/key: error
    examples/required-per-binding: warn
```

- The manifest is the only source of `specVersion`.
- A single-file project puts `schema`, `values` and `rules` inline in the manifest.
- A multi-node values file is shaped `<scope>: { <node>: { key: value } }`. Root
  entries are `<root>: { key: value }`.
- Presets are `tierwise:<name>`, a relative path, or a URL pinned to a SHA-256. Kinds
  and naming patterns are replaced by later definitions. Semantic definitions can't
  be redefined. No preset can configure an engine error.

**Built-in presets:**

| Preset | For | Contains |
| --- | --- | --- |
| `tierwise:recommended` | Most teams | Kinds `configuration` and `capability`, naming patterns, ownership checks, strict lint |
| `tierwise:minimal` | Trying it out | No kinds, no naming, lint only for likely bugs |
| `tierwise:entitlements` | Plans and entitlements | Kinds `limit` (`min`), `feature` (`all`), `grant` (`union`) |

### 5.2 `schema/core.yaml`

```yaml
decision_hierarchy:
  levels: [global, channel, division, site, account, user]
  axes: [seller]
  scopes:
    channel:  { nodes: [web, partner_portal], on_unknown_node: error }
    division: { nodes: [north, south, west], on_unknown_node: error }
    site:     { nodes: { from: catalogs/sites.json }, on_unknown_node: ignore }
    account:  { nodes: open, values: service }
    user:     { nodes: open, values: service }
    seller:   { nodes: [acme], on_unknown_node: error }

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

### 5.3 `schema/components/checkout.yaml`

```yaml
namespace: checkout

keys:
  checkout.shipping.allowed_methods:
    kind: capability
    type: set
    universe: [GROUND, PICKUP, NEXT_DAY]
    combine: intersect
    settable_at: [global, channel, division, site, account, seller]

  checkout.container_order.allowed:
    kind: capability
    type: boolean
    combine: all
    settable_at: [channel, division, site, account]

  checkout.order.allowed_max_lines:
    kind: capability
    type: integer
    min: 1
    combine: min
    settable_at: [global, division, site]

  checkout.pickup.lead_hours:
    kind: configuration
    type: integer
    min: 0
    max: 168
    combine: override
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

**Example shorthand.** In rule files, a list for a single child attribute
(`item.stock_type: [A, B]`) expands to one child per element, and `[]` is an empty
collection. Examples that need several child attributes use the explicit FactSet
form. Conformance vectors always use the explicit form.

### 5.6 Compiled bundle

See §4.11 (1). `spec/schemas/bundle.schema.json` defines `content`. The provenance
sidecar is `<bundle>.provenance.json`:
`{ bundleVersion, gitCommit?, createdAt (RFC 3339 UTC), publisher? }`. A server
keeps its own publication records with the same shape.

### 5.7 Conformance vectors

Evaluator vectors take a **compiled bundle**, so JS needs no compiler:

```yaml
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

- An `expect` may instead be `{ error: <code> }`.
- `expect` fields are a **subset match** unless the vector sets `exact: true`.
- Compile vectors (Java, v1) give a source directory and either the expected bundle
  bytes or the expected **error codes, files, paths and lines**. Messages may
  differ between implementations; codes may not.

---

## 6. Checks

- **Engine errors** protect the guarantees. They are always on, always fail, and
  can't be configured.
- **Evaluation errors** are raised at runtime ([ADR 0009](adr/0009-providers-and-evaluation-errors.md)).
- **Lint rules** encode guidance, with `error | warn | info | off` severity set by
  presets and the manifest.

Every finding has a stable code, the file, the YAML path, the line and, where
possible, a suggestion ("did you mean …?"). `tierwise explain <code>` prints the
rationale and how to fix it.

### 6.1 Engine errors

| Area | Codes |
| --- | --- |
| Parsing and manifest | `E_MANIFEST`, `E_PARSE`, `E_DUPLICATE_KEY`, `E_YAML_FEATURE`, `E_SOURCE_MAPPING`, `E_PRESET`, `E_INVALID_DECLARATION` |
| Hierarchy and scopes | `E_UNKNOWN_SCOPE`, `E_SCOPE_DECLARATION`, `E_UNKNOWN_NODE`, `E_DUPLICATE_NODE`, `E_INVALID_NODE_ID` |
| Keys and values | `E_UNKNOWN_KEY`, `E_TYPE_REQUIRED`, `E_TYPE_MISMATCH`, `E_OUT_OF_RANGE`, `E_INTEGER_RANGE`, `E_NOT_IN_UNIVERSE`, `E_NOT_SETTABLE_HERE`, `E_DUPLICATE_VALUE`, `E_DUPLICATE_DEFINITION` |
| Strategies | `E_STRATEGY_TYPE`, `E_MISSING_DEFAULT`, `E_DEFAULT_NOT_ALLOWED`, `E_MISSING_AXIS_COMBINE`, `E_AXIS_COMBINE_INVALID` |
| Service sources | `E_MISSING_SOURCE_FAILURE`, `E_DENY_NOT_NARROWING`, `E_DENY_BOUND_REQUIRED` |
| Kinds and namespaces | `E_KIND_VIOLATION`, `E_KIND_DEFINITION`, `E_UNKNOWN_KIND`, `E_NAMESPACE_VIOLATION` |
| Entities and facts | `E_UNKNOWN_ENTITY`, `E_ENTITY_GRAPH`, `E_UNKNOWN_FACT`, `E_ILLEGAL_FACT_VALUE` |
| Conditions | `E_QUANTIFIER_REQUIRED`, `E_QUANTIFIER_NOT_ALLOWED`, `E_DEPTH_UNSUPPORTED`, `E_FACT_NOT_IN_SCOPE` |
| Policies and bindings | `E_UNKNOWN_FEATURE`, `E_UNKNOWN_POLICY`, `E_UNKNOWN_SETTING`, `E_INVALID_SETTING`, `E_EFFECT_MISMATCH`, `E_DUPLICATE_RULE_ID`, `E_ROOT_BINDING_NOT_ALLOWED`, `E_ROOT_BINDING_MISSING`, `E_BINDING_NOT_ALLOWED` |
| Examples | `E_EXAMPLE_FAILED` |

### 6.2 Evaluation errors

`E_BUNDLE_INTEGRITY`, `E_BUNDLE_UNSUPPORTED`, `E_PROVIDER_MISSING`,
`E_PROVIDER_REQUIRED`, `E_CONTEXT_INVALID`, `E_UNKNOWN_SCOPE`, `E_UNKNOWN_NODE`,
`E_UNKNOWN_KEY`, `E_UNKNOWN_FEATURE`, `E_FACTS_INVALID`, `E_EVALUATOR_OPTIONS`.

Tooling code (spec tools and CLI only): `E_PROVENANCE_INVALID`. Codes added by [ADR 0013](adr/0013-spec-open-point-resolutions.md).

### 6.3 Lint rules

| Rule | Flags | `recommended` | `minimal` |
| --- | --- | --- | --- |
| `naming/*` | Names not matching declared patterns | error | off |
| `values/no-op` | A value equal to its strategy identity | error | warn |
| `rules/dead-rule` | A rule that can never match (including after a catch-all, or an empty membership list) | warn | warn |
| `examples/required-per-binding` | A binding with no examples | error | off |
| `examples/mixed-collection` | A policy using `all_in`/`any_in` with no example that has a mixed collection | warn | off |
| `schema/unused` | Keys, facts, settings or policies declared but never used | warn | off |
| `schema/description-required` | Keys, features or facts without a description | warn | off |
| `schema/owner-required` | Keys without an owner, when namespaces are used | error | off |
| `presets/override` | A preset or the project replaces an earlier kind or naming pattern | info | info |

---

## 7. Public APIs (sketches)

### 7.1 Java

```java
public interface Key<T> { String name(); ValueType<T> type(); }   // codegen creates constants
public interface Feature<R> { String name(); }

public record Context(Map<String, String> levels, Map<String, String> axes) {
  public static Builder builder() { ... }  // .level("division", "south").axis("seller", "acme")
}

public interface Resolver       { <T> Resolved<T> resolve(Context ctx, Key<T> key); }
public interface DecisionEngine { <R> Decision<R> decide(Context ctx, Feature<R> feature, FactSet facts); }

public interface LevelValueProvider {             // one per service-sourced level
  String level();
  Map<String, Object> valuesFor(String nodeId) throws SourceUnavailableException;
}

public interface EvaluationListener { ... }      // provider failures, unknown keys, loads

public final class Tierwise {
  public static ValidationReport validate(Path projectRoot);
  public static CompiledBundle compile(Path projectRoot);
  public static CompiledBundle loadBundle(InputStream canonicalBytes);  // verifies digest and versions
  public static Evaluator evaluator(CompiledBundle b, EvaluatorOptions options);
}
// Evaluation errors: TierwiseException with code().  Resolved<T>.value() may be null; hasValue().
```

### 7.2 TypeScript

```ts
import { loadBundle, createEvaluator } from "@tierwise/evaluator";

const bundle = loadBundle(bytesOrString);          // sync; verifies digest and versions
const tw = createEvaluator(bundle, {
  providers: { account: async (nodeId) => fetchAccountValues(nodeId) },
  listener: (event) => metrics.record(event),
});

const ctx = { levels: { division: "south" }, axes: { seller: "acme" } };
const r = await tw.resolve(ctx, "checkout.shipping.allowed_methods");
const d = await tw.decide(ctx, "checkout.order_submission", {
  facts: { "customer.active": true, "customer.credit_hold": true },
  children: { item: [{ "item.stock_type": "STOCKED" }] },
});
tw.resolveSync(ctx, "checkout.pickup.lead_hours");    // E_PROVIDER_REQUIRED if a provider is needed
tw.decideSync(ctx, "checkout.order_submission", facts); // decide never needs providers
```

`tierwise codegen --lang ts|java` generates typed key and feature handles, so names
and result types are checked at compile time.

**Both languages:** callers go only through resolve and decide. No API exposes one
level's raw value.

---

## 8. Runtime integrations

### Spring Boot starter

- Bundle sources: server URL, HTTP URL, S3, or a classpath snapshot.
- Refresh: ETag polling (default 30 s) with an atomic swap. Evaluations in flight
  finish on the old bundle.
- Last-known-good: if a refresh fails, keep serving the current bundle and expose
  its staleness (default alert at 5 minutes).
- Startup: fail fast if no bundle loads, unless a snapshot is configured.
- Providers: per-provider timeout (default 200 ms).
- Micrometer: bundle version, bundle age, refresh failures, decisions by feature and
  reason, provider failures. Log `bundleVersion` with every decision (log level
  configurable).

### `@tierwise/node`

- The same refresh behaviour using built-in `fetch` (Node 20+); no other
  dependencies.
- Exposes the bundle version and staleness for the application's own metrics.

### OpenFeature providers

- Keys map to flags by type: boolean, string, number (integer) and object (set,
  list, object).
- Features map to string flags named after the feature. Facts come from the
  evaluation-context attribute `tierwise.facts`.
- Reason and provenance go in flag metadata. The mapping is a spec appendix.

---

## 9. Server (control plane)

Stateless, horizontally scalable, **never the source of truth**. Publishes
`openapi.yaml`.

| Endpoint | Purpose |
| --- | --- |
| `POST /v1/bundles` | Publish canonical bundle bytes plus a publication record. CI identity only. Verifies integrity, versions and structure (examples run at validation, ADR 0013). A conflicting body for an existing version is rejected. |
| `GET /v1/bundles/latest` | Latest bundle bytes; ETag = `bundleVersion` |
| `GET /v1/bundles/{version}` | A specific version |
| `GET /v1/bundles` | History from publication records: version, commit, time, publisher |
| `POST /v1/bundles/{version}/promote` | Make a stored version the latest (roll back or forward). Restricted. |
| `POST /v1/resolve` | `{ context, keys[] }` → resolved values with provenance |
| `POST /v1/decide` | `{ context, feature, input }` → decision with trace |
| `GET /v1/schema` | Browse keys, features, facts and policies |

- Storage is an interface, with S3 and filesystem implementations.
- Auth is pluggable, with OIDC/JWT by default. There are no default credentials.
  Publish and promote are limited to configured roles.
- `/resolve` and `/decide` use `tierwise-core`, so they match in-process results
  for the same bundle.
- Because `/resolve` runs on the server, the server is configured with its own
  providers for service-sourced levels, or it returns `UNAVAILABLE` contributions
  for them.

---

## 10. Out of scope

- A general-purpose rules engine or expression language: no scripting, no SpEL.
  Richer expressions would be proposed as CEL in an RFC.
- A JS validator or compiler (v1).
- A UI or server API for editing values.
- Release toggles, experiments, kill switches.
- Deferred to an accepted RFC:
  - numeric comparisons (`gt`, `lt`);
  - rules that read resolved keys;
  - quantifiers deeper than one level;
  - `null` deletion in `deep_merge`;
  - YAML aliases;
  - multi-root hierarchies;
  - service-sourced axes.
- Plugins for operators or strategies.
- Disabling engine errors.
- Caching resolved values across requests. Measure first.
- Browser enforcement.

---

## 11. Phases

See [the roadmap](roadmap.md).

---

## 12. Testing requirements

- **Conformance (both languages).** Every evaluator and canonical vector passes in
  Java and JS. Both runners read `spec/conformance/` directly and never copy it.
- **Unit tests.** One named test per semantic rule.
- **Property-based tests** (jqwik, fast-check), over generated schemas with arbitrary
  names:
  - Narrowing strategies never widen when a value is added.
  - Commutative results, **and their contribution effects**, don't depend on input
    order.
  - The most specific `override` value always wins.
  - `all_in` over an empty collection is false.
  - **Java and JS agree** on random bundles, contexts and facts (nightly
    differential run).
- **Regression tests in both domains:**
  - A mixed basket under the restrictive policy is denied.
  - A child fact without a quantifier is `E_QUANTIFIER_REQUIRED`.
  - A missing fact returns `if_fact_missing` whatever the rule order (proved by
    reordering).
  - Unknown node: `error` rejects. `ignore` skips only that scope, with zero
    provider calls, other scopes preserved and ordered `skippedScopes`. Context is
    validated before any provider call. Source IDs are strict under both policies.
    Open and generated catalogs are covered in both evaluators.
  - JS rejects `"true"` as a boolean fact (`E_FACTS_INVALID`).
  - The YAML 1.2 trap: node ID `no` remains the string `"no"`.
  - Same content with different provenance gives identical bundle bytes.
- **Golden validation tests.** Each invalid fixture produces exactly its expected
  codes, files, paths and lines.
- **Integration tests.**
  - The starter and `@tierwise/node` against a stub server: refresh, ETag, atomic
    swap under concurrency, last-known-good, startup without a bundle.
  - Provider timeouts follow declared `skip` and `deny`, using custom kinds and
    keys without a kind.
- **End-to-end test.** Compile → publish → Java and Node services pick up the new
  version and return identical decisions → promote the previous version → both roll
  back.
- **Performance.** JMH and a JS harness on about 1,000 keys and 50 features. Target
  p99 < 100 µs in-process, excluding providers. Report the numbers; don't optimise
  before measuring.

---

## 13. Non-functional requirements

- **Java.** Core, CLI and OpenFeature target Java 17+. The starter and server target
  Spring Boot 3.x on Java 21. Maven via the Maven Wrapper.
- **JavaScript.** Strict TypeScript; ESM + CJS + types; Node 20+; ES2020 browsers;
  `@tierwise/evaluator` has zero runtime dependencies.
- **Immutability.** Compiled models are immutable and thread-safe (Java) and frozen
  (JS).
- **Determinism.** The same sources give byte-identical bundles and the same
  `bundleVersion`.
- **Dependencies.** Minimal; each one is justified in an ADR.

---

## 14. Settled defaults

| Question | Decision |
| --- | --- |
| Name and coordinates | Tierwise, `@tierwise`, `io.github.tierwise` ([ADR 0006](adr/0006-rename-to-tierwise.md)) |
| Contribution sign-off | DCO |
| Governance | Maintainers file; spec changes by RFC with maintainer approval |
| Docs site | MkDocs Material |
| Java baseline | 17 for core, CLI and OpenFeature; 21 for the starter and server |
| Server storage | S3 and filesystem |
| Refresh | Poll every 30 s; staleness alert at 5 minutes; provider timeout 200 ms |

Owner-only items (GitHub organisation, maintainers, private contacts, dependency
approval) are tracked in [open questions](open-questions.md).

---

## 15. Acceptance criteria for 0.1.0

- [ ] `spec/spec.md` specifies all of Section 4, and every rule has at least one
      conformance vector.
- [ ] Java and JS pass 100% of the evaluator and canonical vectors for both domains.
- [ ] The cross-language property test has run for at least one night with zero
      disagreements.
- [ ] Every engine error, evaluation error and lint rule is implemented, with a
      stable code, a golden test and a `tierwise explain` entry.
- [ ] No domain term appears in production code; the genericity check runs in CI.
- [ ] No modeling choice is hardcoded.
- [ ] A project for each built-in preset, a single-file project with no kinds or
      namespaces, and a project with custom kinds all validate and evaluate
      correctly.
- [ ] Every result carries provenance and `bundleVersion`.
- [ ] Applications evaluate in-process and keep working with the server down.
- [ ] Server `/resolve` and `/decide` match in-process results.
- [ ] The CLI ships as a native binary, a Docker image and a GitHub Action.
- [ ] The README has the positioning table and a verified five-minute quickstart for
      each language.
- [ ] All project files are present; releases are signed and ship an SBOM.
- [ ] ADRs exist for every decision not dictated by this document.
