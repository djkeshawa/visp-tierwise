# Vision and strategy

## One sentence

Tierwise is a type checker and evaluator for layered business configuration. You
declare your own levels, keys and decisions. A merge rule on every key, explicit
all/any over collections, and provenance on every answer make sure the result
means one thing, the same thing, in Java and in JavaScript.

## The problem

Organisations configure behaviour at many levels, for example platform → tenant →
region → store → user, or plan → org → workspace → user. Over time the values end
up spread across code, environment files, flag tools and per-team YAML. When teams
try to consolidate them, the same four failures keep appearing:

1. **No shared meaning per key.** Nothing says whether a lower level replaces a
   value or vetoes it. Two readers combine the same files differently.
2. **Typos fail open.** A misspelled node or key silently drops a restriction.
3. **Ambiguous rules over collections.** "Item stock type is STOCKED" on an order
   doesn't say whether it means *any* item or *every* item, and mixed baskets get
   opposite answers.
4. **No enforced structure.** Every team invents its own layout and naming.

Each of these is a *silent interpretation*. Tierwise exists to remove them: every
key, fact and rule has exactly one meaning, defined once and checked at build time.
Mistakes fail CI, not production.

## Who it is for

| Primary user | Typical model | What they need most |
| --- | --- | --- |
| Platform teams in multi-region or multi-brand commerce | global → channel → division → site → account | Capability vetoes that can't fail open; entity-aware order decisions |
| SaaS teams managing plans and entitlements | plan → org → workspace → user | Limits (`min`), grants (`union`), clear provenance for support teams |
| Retail and POS platforms | platform → region → store → terminal | The same answer in back-office Java and in-store or browser JavaScript |

The **entitlements** use case is the adoption wedge: it is common, easy to explain
and needs no decision tables. Commerce-style **capability vetoes with entity-aware
decisions** are the differentiator. The docs and quickstarts lead with entitlements
and graduate to decisions.

## Positioning

| If you need… | Use |
| --- | --- |
| Release toggles, experiments, kill switches | A feature-flag tool. Tierwise can sit behind OpenFeature as a provider. |
| Authorization: who may do what to which resource | Cedar or OPA |
| A general-purpose configuration language | CUE, Pkl or Jsonnet |
| Typed configuration across levels *you* define, with a merge rule per key; entity-aware decision tables with explicit all/any; provenance for every answer; Java and JavaScript | **Tierwise** |

Tierwise does not compete with these tools. It sits between "a folder of YAML" and
"a policy engine": stricter than the first, much narrower than the second.

## Product principles

These are ordered. When two conflict, the earlier one wins.

1. **No silent interpretation.** Anything ambiguous is a build error or a required
   declaration. There are no hidden defaults: `combine`, `on_unknown_node`,
   `on_source_failure`, `hit_policy` and `type` are always explicit.
2. **The spec and conformance suite are the product.** The two implementations are
   reference implementations of `spec/`. The shared conformance suite is what makes
   "one bundle, one answer" true, and it is what lets third parties write evaluators
   in other languages later.
3. **Mechanism, not policy.** The engine supplies guarantees: determinism,
   validation, admission, quantifiers and provenance. Users supply every modeling
   choice. Opinions live in presets and lint rules, which users can adopt, override
   or ignore. Presets can tighten checks but never switch off a guarantee.
4. **Generic by construction.** Production code never contains a domain name. CI
   proves it, and two unrelated sample domains exercise the whole suite.
5. **Git is the source of truth; evaluation is in-process.** The control plane
   stores and serves immutable bundles. It is never on the hot path, and
   applications keep working from their last-known-good bundle when it is down.
6. **Small surface, explicit growth.** There is no expression language, plugin
   operators or plugin strategies. Every extension goes through an RFC so both
   languages stay identical.

## Strategy

### Layered value: each stage is useful on its own

| Stage | What a team gets | Components |
| --- | --- | --- |
| 1. Validate | `tierwise validate` / `lint` / `test` in CI on layered YAML: typo-proof keys and nodes, typed values, examples as tests | Spec, Java core, CLI, GitHub Action |
| 2. Evaluate | The same answers in-process in Java services, Node services and browsers (browser for display only) | Java evaluator, `@tierwise/evaluator`, codegen |
| 3. Operate | Hot refresh, last-known-good, metrics, OpenFeature integration | Spring Boot starter, `@tierwise/node`, OpenFeature providers |
| 4. Govern | Versioned bundles, rollback, cross-language `/resolve` and `/decide` | `tierwise-server` |

A team can stop at any stage and still get value. Stage 1 alone (CLI-only adoption)
is the lowest-friction entry point. Bundles can be served from any HTTP endpoint or
object store, so the server is optional.

### Release strategy

Release 0.1.0 ships the **full scope** in [the roadmap](roadmap.md). Internally,
work proceeds in phases with review stops. Those phases are deliberately ordered so
that stage 1 and stage 2 work end to end before any runtime, server or packaging
work starts. Spec and bundle-format versions are independent of package versions;
0.x spec versions may still change by RFC.

### What success looks like

- Both evaluators pass 100% of the conformance suite, with zero disagreements in
  nightly differential testing.
- A new user can follow either quickstart on a clean machine in under five minutes.
- In-process `resolve`/`decide` p99 stays under 100 µs on a bundle of about 1,000
  keys and 50 features.
- Every engine error has a stable code, a golden test and a `tierwise explain` page.
- No domain term appears in production code, enforced in CI.

## Risks

| Risk | Mitigation |
| --- | --- |
| **Scope versus capacity.** The full 0.1 scope is large and no maintainers are assigned. | Phase order front-loads the vertical slice (spec → Java → JS). Runtime, server and packaging phases are independent and can be parallelised or cut later without affecting the spec. |
| **Java/JS drift** | Spec-first; both runners read the same vectors; nightly differential property tests; a release is blocked unless both pass. |
| **YAML type coercion** (YAML 1.1 reads `no`, `on`, `yes` as booleans) | Parse YAML 1.2 core schema only, with duplicate-key rejection and source positions ([ADR 0012](adr/0012-parsing-and-dependency-baseline.md)). |
| **Unicode and time-zone non-determinism** | Normalise at compile time only; evaluators compare code points; IANA zone names are validated against pinned data at compile time ([ADR 0011](adr/0011-source-format-and-types.md)). |
| **Spec over-complexity** | Every feature must justify itself against principle 1; extensions are deferred to RFCs ([requirements §10](requirements.md#10-out-of-scope)). |
| **Native binary and supply-chain tooling cost** | Isolated to the toolchain and release phases; a fat JAR and the Docker image ship regardless. |
| **Identity** | Renamed from Strata to avoid the OpenGamma Strata collision ([ADR 0006](adr/0006-rename-to-tierwise.md)). Registries are re-checked before the first publish. |

## Non-goals

- A general-purpose rules engine or expression language
- Release toggles, experiments or percentage rollouts
- Authorization of principals against resources
- A UI or server API for editing values; git remains the only write path
- Enforcing anything in the browser
