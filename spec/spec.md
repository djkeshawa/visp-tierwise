# Tierwise specification

- **Spec version:** 0.1 (draft, not accepted)
- **Bundle format version:** 1 (draft)
- **IANA tz database release:** 2026e ([TYPE-80])

This specification defines the source format, compilation, bundle format and
evaluation semantics of Tierwise in language-neutral terms. Every conforming
compiler and evaluator implements it exactly. When an implementation and this
document disagree, the implementation is wrong.

## Conformance language

The key words **MUST**, **MUST NOT**, **REQUIRED**, **SHALL**, **SHALL NOT**,
**SHOULD**, **SHOULD NOT**, **RECOMMENDED**, **MAY** and **OPTIONAL** are to be
interpreted as described in BCP 14 (RFC 2119, RFC 8174) when, and only when, they
appear in all capitals.

Each normative requirement carries a stable clause ID such as `[MERGE-4]`.
Conformance vectors cite these IDs.

## Conformance classes

| Class | Must implement |
| --- | --- |
| **Compiler** | Sections 1–10 and 13: reads source projects, reports findings, writes canonical bundles. It passes every compile vector. |
| **Evaluator** | Sections 2–12, excluding source-only clauses: loads canonical bundles, performs `resolve` and `decide`. It passes every evaluate and canonical vector. |

## Terminology

| Term | Definition |
| --- | --- |
| **Project** | A directory containing a `tierwise.yaml` manifest and the sources it references |
| **Level** | An entry in the ordered `decision_hierarchy.levels` list |
| **Root level** | The first level. A singleton that is always in effect. |
| **Axis** | An entry in `decision_hierarchy.axes`; a scope outside the level order |
| **Scope** | A level or an axis |
| **Node** | One member of a scope, e.g. one division. A node of a non-root scope is named by its node ID; the root level has one implicit node with no ID ([SCOPE-10]) |
| **Catalog** | The set of admissible nodes of a closed scope (inline or generated) |
| **Context** | The caller-supplied map of non-root levels and axes to node IDs for one evaluation |
| **Key** | A declared configuration item with a type, a merge strategy and settable scopes |
| **Strategy** | The rule (`combine`) that merges a key's values across scopes |
| **Identity** | The result of a commutative strategy when nothing contributes |
| **Kind** | An optional, named set of constraints that keys opt into |
| **Service-sourced level** | A level whose values come from a provider at evaluation time |
| **Provider** | Caller-supplied code that returns a service-sourced level's values for a node |
| **Entity** | A declared thing a decision is about (e.g. an order) |
| **Fact** | A declared attribute `<entity>.<attribute>` with a type |
| **FactSet** | The caller-supplied facts for one decision |
| **Feature** | A declared decision, made per entity, returning an effect |
| **Policy** | A named, ordered rule list with settings and a default effect |
| **Binding** | The selection of a policy (and setting values) at the root level, or at a non-root level and node ([BIND-2], [BIND-3]) |
| **Effect** | The value a decision returns |
| **Bundle** | The canonical compiled artifact `{ bundleVersion, content }` |
| **Finding** | A diagnostic with a stable code, location and message |
| **Provenance case** | A conformance case that checks a provenance sidecar against its bundle ([VEC-126]) |

### Sources and projects (section 1)

| Term | Definition |
| --- | --- |
| **Manifest** | The file `tierwise.yaml` at the project root ([MANIFEST-1]). It is itself a source file ([MANIFEST-9]) |
| **Project root** | The directory that contains the manifest. Relative manifest paths resolve against it ([MANIFEST-1]) |
| **Source file** | A YAML file that a compiler reads: the manifest, a preset, or a schema, values or rules file ([SRC-1]) |
| **Schema file** | A file selected by `sources.schema` or listed under `namespaces` ([SRC-12]) |
| **Values file** | A file selected by a `sources.values` entry ([SRC-12]). It is a path-mapped, root or multi-node values file ([SRC-21]) |
| **Path-mapped values file** | A values file selected by a path-mapped entry `{ pattern }`, whose path supplies its scope and node ([SRC-22]) |
| **Root values file** | A values file selected by a root entry `{ file, scope }`, holding values for the root level ([SRC-26]) |
| **Multi-node values file** | A values file selected by a multi-node entry `{ file }`, holding values for several scopes and nodes ([SRC-29], [SRC-30]) |
| **Rules file** | A file selected by `sources.rules`. It configures exactly one feature ([SRC-12], [POL-2]) |
| **Glob pattern** | A string in `sources.schema` or `sources.rules` that selects files ([MANIFEST-11], [SRC-18]) |
| **Inline content** | Schema, values or rules content written under the manifest keys `schema`, `values` and `rules` ([MANIFEST-13]) |
| **Preset** | A reusable set of kinds, naming patterns and lint settings that a project extends ([MANIFEST-19] to [MANIFEST-36]) |
| **Preset reference** | An `extends` item naming a built-in, path or URL preset ([MANIFEST-20]) |
| **Namespace** | A name under `namespaces` that owns one schema file ([NS-1], [NS-2]) |
| **Namespaced file** | A schema file listed under `namespaces` ([NS-3]) |
| **Shared file** | Every schema file that is not namespaced, including inline `schema` content ([NS-3]) |

### Hierarchy and admission (section 2)

| Term | Definition |
| --- | --- |
| **Node ID** | The string that names a node of a non-root scope ([SCOPE-33] to [SCOPE-35]) |
| **Closed catalog** | A scope whose `nodes` is an inline list or a `from` object ([SCOPE-22]) |
| **Open catalog** | A scope whose `nodes` is `open`. It admits every structurally valid node ID ([SCOPE-45], [CTX-13]) |
| **Evaluation path** | The root level and each supplied non-root level and axis that is admitted ([CTX-19]) |
| **Skipped scope** | A scope whose supplied node ID is unlisted under `on_unknown_node: ignore` ([CTX-14]). An omitted scope is not skipped ([CTX-21]) |
| **Admitted** | A node is admitted at a level when the context supplies it and the level is on the evaluation path ([CTX-19]) |

### Entities and facts (section 3)

| Term | Definition |
| --- | --- |
| **Parent**, **ancestor** | An entity named in E's `belongs_to`, and an entity reachable from E by `belongs_to` edges ([ENTITY-9], [ENTITY-10]) |
| **Child** | An entity named in E's `has_many` ([ENTITY-11]) |
| **Decided entity** | The entity that a feature's `decided_per` names ([FEAT-4]) |
| **Single-value fact** | A fact declared on the decided entity or one of its ancestors ([FACT-13]) |
| **Child fact** | A fact declared on a child of the decided entity ([FACT-14]) |
| **Eligible** | A fact is eligible for a decision when it is a single-value fact or a child fact ([FACT-15]) |
| **Child map** | One element of a `children` list in a FactSet (section 3.4) |
| **Absent**, **missing** | A fact is absent when no entry names it, and missing when it is absent or its entry holds `null` ([FACT-16] to [FACT-19]) |

### Types (section 4)

| Term | Definition |
| --- | --- |
| **Type declaration** | A map with a `type` member and the type members that its type defines ([TYPE-1] to [TYPE-7]) |
| **Canonical decimal string** | The unique string form of a decimal value ([NORM-9], [NORM-10]) |

### Keys and merge (section 5)

| Term | Definition |
| --- | --- |
| **Commutative**, **order-dependent** | The two classes of strategy ([MERGE-1]) |
| **Narrowing** | The strategies `all`, `intersect` and `min` ([MERGE-2]) |
| **Root value** | A value for a key written at the root level ([MERGE-7]) |
| **Complete object** | An object value in which every required field, at every depth, has a value ([MERGE-9]) |
| **Starting value** | The `default` of a `deep_merge` key, or its root value when it has no default ([MERGE-10]) |
| **Contributes** | A scope on the evaluation path contributes a value for a key when the key is settable there and the scope supplies a value ([MERGE-14]) |
| **Hierarchy result**, **axis result** | H and A, the results over the levels and over the axes of an order-dependent key ([MERGE-19], [MERGE-20], [MERGE-23], [MERGE-24]) |
| **Merge** | The field-by-field combination of two objects ([MERGE-22]) |
| **Authored value** | A value for a key written in a source, as opposed to a provider value or a deny value ([KIND-6]) |

### Service-sourced levels (section 6)

| Term | Definition |
| --- | --- |
| **Source-failure behaviour** | The value, `skip` or `deny`, of a key's `on_source_failure`, declared on the key or obtained through its kind ([SVC-1], [SVC-3], [KIND-9]) |
| **Source failure** | The outcome, for one key at one service-sourced level in one evaluation call, that is `UNAVAILABLE` ([SVC-23]) or `INVALID` ([SVC-29], [SVC-34]) |
| **Deny value** | The value that [SVC-9] contributes under `deny` |
| **Provider result** | What one provider call returns |
| **Entry** | One key name and its value in a provider result |
| **Evaluation call** | One invocation of resolve or of its synchronous variant ([EVAL-7], [EVAL-10], [EVAL-11]). Whether a multi-key request is one evaluation call is open in [SVC-20] |
| **Keys being resolved** | The keys that an evaluation call returns results for. Under [EVAL-7] this is one key |
| **Key being resolved at a level** | A key being resolved that is settable at that level ([KEY-6], [KEY-11]) |

### Features, policies and rules (sections 7 and 8)

| Term | Definition |
| --- | --- |
| **Setting** | A named, fact-typed policy parameter whose value comes from the selected binding or the setting's `default` ([POL-9] to [POL-15], [BIND-23]) |
| **Root binding** | The binding under the root level name ([BIND-2]). Every feature has one ([BIND-13]) |
| **Selected binding** | The one binding that a decision uses: the candidate at the latest level ([BIND-14] to [BIND-17]) |
| **Example** | An entry of a rules file's `examples`: a context, a FactSet and an expected effect ([POL-28]) |
| **Rule** | An entry `{ id, name, when, then }` of a policy's `rules` list ([POL-21]) |
| **Condition** | One entry of a rule's `when` mapping: a fact name and a test on its value ([RULE-1], [RULE-3]) |
| **Setting reference** | A condition operand `$name` that stands for a setting's resolved value ([RULE-4]) |
| **Literal** | An operand, or an element of a list operand, that is not a setting reference ([RULE-3]) |
| **Missing-fact precheck** | The check, before any rule, that no fact named in the selected policy's conditions is missing ([RULE-40] to [RULE-45]) |
| **Winner** | The first rule, in rule order, whose conditions all hold ([RULE-47]) |
| **Trace** | The list in a Decision of the rules checked, each with whether it matched and, if not, its first failing condition ([RULE-52] to [RULE-55]) |

### Results, bundles and evaluation (sections 9 to 11)

| Term | Definition |
| --- | --- |
| **Resolved result**, **Decision result** | The results of resolve and decide ([PROV-10], [PROV-62]) |
| **Contribution entry** | One element of a Resolved result's `contributions`. It is a value entry or a failure entry (section 9.3) |
| **Failure entry**, **value entry** | A contribution entry whose label is `UNAVAILABLE` or `INVALID`, and every other contribution entry (section 9.3) |
| **Contribution label** | The `effect` member of a contribution entry. It is not the effect of a decision ([PROV-24], [PROV-38]) |
| **Sidecar** | The provenance file kept beside a bundle and outside its bytes ([BUNDLE-139]) |
| **Loader** | The implementation of load bundle, which applies the checks of section 10.5 ([EVAL-2]) |
| **Synchronous variant** | A variant of resolve or decide that returns its result directly and never calls a provider ([EVAL-9] to [EVAL-12]) |
| **Evaluation error** | An error with a code of requirements §6.2 that an evaluation operation raises ([EVAL-22], [EVAL-38]) |

### Conformance vectors (section 12)

| Term | Definition |
| --- | --- |
| **Vector** | An evaluate vector, a canonical vector or a compile case. Every vector has an `id` |
| **Evaluate vector** | A `.yaml` file under `evaluate/` that runs one `resolve` or `decide` call and checks its outcome |
| **Canonical vector** | A `.yaml` file under `canonical/` that checks one JCS encoding or one SHA-256 digest |
| **Compile case** | A directory directly under `compile/` that compiles one project and checks the outcome |
| **Suite** | Every vector, compile case and bundle file under `spec/conformance/` |
| **Runner** | A program that executes the suite for one implementation |
| **Stub** | The behaviour that an evaluate vector declares for one service-sourced level, mapping node IDs to outcomes |
| **Fail outcome** | A stub outcome, `unavailable` or `timeout`, that makes the provider fail for one node |
| **Provider invocation** | One call that an evaluator makes to a provider for one node at one level, whatever the call returns |
| **Runner failure** | The outcome of a vector that the runner cannot execute as written. The runner reports it as failed and does not compare its outcome with `expect` |
| **Bundle file** | A file that holds one canonical bundle, or, under `bundles/invalid/`, one deliberately defective bundle |
| **Fault** | An input in an evaluate vector that breaks one check of the operation under test |

### Findings and lint (section 13)

| Term | Definition |
| --- | --- |
| **Validation** | One run of a compiler's checks over one project ([FIND-1] to [FIND-3]) |
| **Engine check** | A check that sections 1 to 10 define with an engine error code from requirements §6.1 |
| **Engine error** | The code of an engine check. It always has severity `error`, and no setting disables it ([FIND-12], [FIND-15]) |
| **Lint rule** | A rule listed in [LINT-1]. Its severity is set by presets and the manifest |
| **Severity** | `error`, `warn` or `info` on a finding, or `off` in a lint setting ([LINT-3]) |
| **Engine finding**, **lint finding** | A finding that an engine check, or a lint rule, reports |
| **Finding key** | The tuple (`code`, `file`, `path`, `line`) that identifies a finding ([FIND-24]) |

## Sections

| # | Section | Clause areas |
| --- | --- | --- |
| 1 | [Manifest, sources and namespaces](sections/01-manifest-and-sources.md) | `MANIFEST`, `SRC`, `NS` |
| 2 | [Decision hierarchy and node admission](sections/02-hierarchy-and-admission.md) | `SCOPE`, `CTX` |
| 3 | [Entities, facts and FactSets](sections/03-entities-and-facts.md) | `ENTITY`, `FACT` |
| 4 | [Value types and normalisation](sections/04-types.md) | `TYPE`, `NORM` |
| 5 | [Keys, merge strategies, axes and kinds](sections/05-keys-and-merge.md) | `KEY`, `MERGE`, `AXIS`, `KIND` |
| 6 | [Service-sourced levels and providers](sections/06-service-sources.md) | `SVC` |
| 7 | [Features, policies and bindings](sections/07-features-policies-bindings.md) | `FEAT`, `POL`, `BIND` |
| 8 | [Rule evaluation](sections/08-rule-evaluation.md) | `RULE` |
| 9 | [Provenance](sections/09-provenance.md) | `PROV` |
| 10 | [Bundle format and determinism](sections/10-bundle-format-and-determinism.md) | `BUNDLE`, `DET` |
| 11 | [Evaluation operations and errors](sections/11-evaluation-and-errors.md) | `EVAL` |
| 12 | [Conformance vectors](sections/12-conformance-vectors.md) | `VEC` |
| 13 | [Findings and lint](sections/13-findings-and-lint.md) | `FIND`, `LINT` |

Error codes are catalogued in [error-codes.md](error-codes.md). The OpenFeature mapping appendix is [docs/openfeature-mapping.md](../docs/openfeature-mapping.md).
