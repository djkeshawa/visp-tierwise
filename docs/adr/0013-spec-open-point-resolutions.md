# ADR 0013: Resolutions for the wave 1 spec open points

- Status: accepted for the Phase 1 specification (delegated technical decision)
- Date: 2026-10-09
- Amends: [ADR 0009](0009-providers-and-evaluation-errors.md) §2 (non-settable provider entries), requirements §1.1, §6.1, §9 and implementation plan item 3.7

## Context

Wave 1 drafted spec sections 1–13. Reviewers marked about 180 clauses *(OPEN)*,
where the sources didn't decide a detail. The final review grouped them and
recommended a resolution for each. This ADR decides all of them, so that the
sections can drop their *(OPEN)* markers before error codes, schemas and vectors
are written.

## Cross-cutting decisions

| # | Topic | Decision |
| --- | --- | --- |
| X1 | Malformed declarations | New engine code **`E_INVALID_DECLARATION`**: a missing required member, an unknown member, a member of the wrong shape, or an empty list where a non-empty one is required, in a schema, rules or values declaration. A more specific code takes precedence where one applies: `E_MANIFEST`, `E_PRESET`, `E_SCOPE_DECLARATION`, `E_KIND_DEFINITION`, `E_TYPE_REQUIRED`, `E_SOURCE_MAPPING`. Clauses: SRC-16, KEY-5, KEY-7 (empty), FEAT-2, FEAT-16, ENTITY-7, FACT-8, TYPE-11, TYPE-22(b), TYPE-52(a), RULE-6, POL-25, POL-45, SVC-4 (on a key), LINT-17 (in a schema file). |
| X2 | Repeated entries | **`E_DUPLICATE_VALUE`** reports any repeated entry: in a declaration list (`settable_at`, `values`, `universe`, `belongs_to`, `has_many`), among set elements, setting names, rule IDs (still `E_DUPLICATE_RULE_ID`), example names, and in values. This widens the source-conflict meaning in ADR 0011 §5. An identical second declaration still counts as a duplicate. Clauses: ENTITY-8, TYPE-52(b)(c), KEY-7, FEAT-8, FEAT-21, POL-19, POL-30, RULE-21, SRC-41. |
| X3 | Parse and encoding | New engine code **`E_PARSE`**: a source, preset or catalog file that isn't valid UTF-8, starts with a byte-order mark, or isn't well-formed YAML 1.2 (or JSON, for catalogs). For the manifest itself, the code is `E_MANIFEST`. Clauses: SRC-5, SRC-6, SRC-10, SCOPE-30 (parse part), VEC-6. |
| X4 | Paths outside the project | Forbidden. Resolve symbolic links first, then check containment. A manifest path outside the project is `E_MANIFEST`; a catalog `from` path outside it is `E_SCOPE_DECLARATION`. A named file that doesn't exist uses the same codes. Clauses: MANIFEST-12, SCOPE-28, SRC-17, SRC-18. |
| X5 | Non-string scalars in name positions | Never converted silently. Use the enclosing declaration's code where one exists (e.g. `E_SCOPE_DECLARATION` for a `nodes` entry); otherwise `E_TYPE_MISMATCH`, with a hint to quote the value. Clauses: SRC-11, SCOPE-20, ENTITY-7. |
| X6 | Names | Names of scopes, entities, attributes, settings, kinds, namespaces and node IDs are compared after NFC normalisation. Entity names and namespace names contain no `.`; setting names contain no `$` or `.`. Clauses: SRC-4, SRC-24, SCOPE-8, SCOPE-36, FACT-3, NS-9, POL-20. |

## New and extended codes

Added to requirements §6.1 (engine) or §6.2 (evaluation):

| Code | Class | Meaning | Clauses |
| --- | --- | --- | --- |
| `E_INVALID_DECLARATION` | engine | X1 | many |
| `E_PARSE` | engine | X3 | many |
| `E_UNKNOWN_KIND` | engine | A key names a kind that isn't declared | KIND-13 |
| `E_UNKNOWN_FEATURE` | engine **and** evaluation | A rules file names a feature that isn't declared (it is also the existing evaluation code) | POL-3 |
| `E_UNKNOWN_POLICY` | engine | A binding names a policy that isn't declared in the same rules file. That binding's other entries are not checked against settings (BIND-7). | BIND-6, BIND-7 |
| `E_FACT_NOT_IN_SCOPE` | engine | A condition or setting names a declared fact that is not a single-value or child fact of the decided entity, and is not covered by `E_DEPTH_UNSUPPORTED` | RULE-12, POL-16 |
| `E_PROVENANCE_INVALID` | tooling | A provenance sidecar fails strict parsing, its member rules, or its `bundleVersion` linkage. Reported by the spec tools and the CLI, never by an evaluator. | BUNDLE-151 |
| `E_EVALUATOR_OPTIONS` | evaluation | Raised when creating an evaluator if: a provider is registered for an undeclared level, for a level that isn't service-sourced, or twice for one level; or the options or a timeout are invalid. A timeout must be a positive integer number of milliseconds. | EVAL-27, EVAL-28, SVC-14 |

## Semantic decisions

| Topic | Decision | Clauses |
| --- | --- | --- |
| Who runs examples | **Validation and compilation run every example.** `E_EXAMPLE_FAILED` is an engine error and blocks the bundle. `tierwise test` runs the same validation and reports results per example. The server does **not** re-run examples, because bundles exclude them. It verifies integrity, versions and structure. Implementation plan item 3.7 moves into validation. | POL-47, VEC-121 |
| Load-check order | Strict parse and envelope → versions (`E_BUNDLE_UNSUPPORTED`) → digest → structure (`E_BUNDLE_INTEGRITY`). A bundle in a future format reports "unsupported" rather than a digest mismatch. Supported in v0.1: format `"1"`, spec `"0.1"`, compared exactly. | EVAL-14, BUNDLE-117, BUNDLE-137 |
| Create-evaluator order | Options shape → provider registrations (`E_EVALUATOR_OPTIONS`) → `E_PROVIDER_MISSING` | EVAL-15 |
| Synchronous variants | Every implementation offers both a resolve that never calls providers (raising `E_PROVIDER_REQUIRED`) and a provider-capable resolve. `decide` never calls providers, so it is always usable synchronously. Error codes are the same in every variant. | EVAL-9, VEC-27 |
| Non-settable provider entries | An entry for a key that is not settable at that level is **ignored and reported to the listener**. It contributes no value and gets no contributions entry. This amends ADR 0009 §2, which classified it as `INVALID`. That key's resolution never consults that level, so the entry is noise, like an unknown key. | SVC-28, SVC-30, PROV-44 |
| `deep_merge` source | Candidate (a): the last present term of the AXIS-7 fold. `H` names the most specific contributing level, `A` names the last declared contributing axis, and `D` alone names `DEFAULT`. | PROV-53 |
| Optional object fields | A value that sets an optional object field the starting value doesn't set must give a complete object for that field. This is checked statically for authored values (`E_TYPE_MISMATCH`) and on validation for provider values (`INVALID`). | MERGE-26 |
| Entity cycles | Orient every edge parent → child (`E: {belongs_to: P}` gives P→E; `P: {has_many: C}` gives P→C). Any cycle in the oriented graph is `E_ENTITY_GRAPH`. | ENTITY-17 |
| Decimals in quantifiers | `all_in` and `any_in` are allowed on `decimal` child facts. Membership is equality on canonical strings, and requirements §4.11(3) only excludes ordering. RULE-15 is relaxed accordingly. | RULE-16 |
| Rule and condition codes | When several checks fail on one condition, report one code, in this order: fact checks, then form (quantifier), then settings, then literals | RULE-28 |
| Naming patterns | Declared as a top-level `naming` map in a **shared schema file or a preset**. Each entry is keyed by pattern name and has `category`, `pattern` and an optional `description`. A kind's `naming` member references a pattern by name. The check is a **lint rule** (naming/*), not an engine check. A bad reference is `E_KIND_DEFINITION`. Patterns use the portable regex subset in LINT-17 with **search** semantics. An invalid pattern is `E_PRESET` in a preset, or `E_INVALID_DECLARATION` in a schema file. Requirements §1.1 is updated: "naming patterns live in presets or the project's schema, never in engine code". | KIND-12, LINT-14, LINT-16, LINT-17, LINT-18 |
| `on_source_failure` on key and kind | If both declare it, the key's value must equal the kind's (`E_KIND_VIOLATION` otherwise). Declaring it on a key that isn't settable at any service-sourced level is accepted with no effect, and the `deny` checks apply only where it takes effect. | KIND-11, SVC-5, SVC-7 |
| Provider time | Timeouts are positive integers of milliseconds, measured from the call until the result is available | SVC-41 |
| Listener events | Delivered before the operation returns, one per affected key per call. A listener that throws is ignored. Creating an evaluator emits one load event. | EVAL-43, EVAL-44, EVAL-45 |
| Lint gating | Lint rules run only when there are no engine findings | LINT-11 |
| Capability sample key | Rename `checkout.order.max_lines` to `checkout.order.allowed_max_lines`, so the sample satisfies the recommended capability naming pattern | requirements §5.3 |
| Root-binding code spelling | `E_ROOT_BINDING_NOT_ALLOWED` is final | ADR 0004 |

## Adopted as recommended by the final review

For every other *(OPEN)* clause, the final review's recommendation is adopted
unchanged. Where a recommendation names a "new code by ADR", it maps to the codes
above. In summary:

- **Section 1:**
  - **MANIFEST:** -8 exact match with `"0.1"`; -15 multi-node shape; -16 a list of rules documents, one per feature; -18 valid; -26 `{url, sha256}` over the exact bytes; -30 `E_PRESET`; -35 presets may not use `extends`; -36 the SRC-14 keys plus `lint`, `E_PRESET` when invalid.
  - **SRC:** -7 `E_YAML_FEATURE`; -9 the second occurrence; -19 a glob that matches nothing is silently empty; -20 (a)(b)(c) as recommended; -23 exactly one `{scope}` and one `{node}`, each in its own segment; -27, -28, -31 `E_SOURCE_MAPPING`; -43 `E_DUPLICATE_DEFINITION`.
  - **NS:** -7 the manifest name is authoritative; -8 yes; -9 unique names without `.`; -10 the `<namespace>.` prefix; -11 no.
- **Section 2:** SCOPE-4 and -9 `E_SCOPE_DECLARATION`; -16 absent, `null` and `{}` are all valid when no non-root scope exists; -21 valid; -37 invalid (with NORM-8); -39 `E_INVALID_NODE_ID` only; -47 `E_SOURCE_MAPPING`; CTX-2 both maps optional, other members are `E_CONTEXT_INVALID`; CTX-11 `E_CONTEXT_INVALID` wins.
- **Section 3:** FACT-9 yes; -11 optional string; -24 either may be omitted; -30 `E_FACTS_INVALID`; -34 the vector runner rejects repeats.
- **Section 4:** TYPE-17 an integer when the value is mathematically integral; -22(a) `E_INTEGER_RANGE`; -23 one finding per violation; -36 a spec.md constant names the tz release, and the file is UTF-8, one name per line, LF, sorted by code point; -63 `E_TYPE_MISMATCH`; -68 a non-empty NFC name with at least one field; -69 unlimited depth; NORM-8 invalid everywhere.
- **Section 6:** SVC-20 one evaluation call; -25 a provider failure; -33 no.
- **Section 7:** FEAT-3 required; -12 not allowed; -20 `E_BINDING_NOT_ALLOWED`; POL-4 no; -5 `E_DUPLICATE_DEFINITION`; -27 `E_INVALID_SETTING`; -32 children beside facts, with no shorthand under facts; -36 `E_INVALID_DECLARATION`; -43 source codes; -44 `E_UNKNOWN_NODE` under both policies; -46 `E_EXAMPLE_FAILED` with the evaluation code in the message; -48 field-by-field equality.
- **Section 8:** RULE-5 no escape in v0.1; -10 `E_UNKNOWN_FACT` only; -20 `E_INTEGER_RANGE`.
- **Section 9:** PROV-85 an unordered JSON object, where only array order is significant.
- **Section 10:** BUNDLE-22 omit empty entries; -28 camelCase; -54 scope order, and level order for `bindingSettableAt`; -75 code-point order; -80 required; -82 the drafter's shape; -104 the source string `$name`; -116 `E_BUNDLE_INTEGRITY`; -118 to -136 all adopted with `E_BUNDLE_INTEGRITY`; -138 no NFC check; -149 `<stem>.provenance.json`, with `<stem>.provenance.<label>.json` for more; -150 the drafter's formats.
- **Section 11:** EVAL-19 `E_CONTEXT_INVALID` wins; -37 the proposal in the clause.
- **Section 12:**
  - VEC-8 a runner failure; -45 an immediate timeout hook, with no durations in vectors; -61 one-to-one, in order; -80 `bundles/invalid/` cases.
  - VEC-84 `utf8` or `hex`, quoted; -95 compare all severities; -104 a list (with FIND-20); -107 an optional `build` mapping; -120 the minimal change.
  - VEC-124 a `provenance/` family; -125 an enforcer list in the coverage record.
- **Section 13:** FIND-18, -20, -22, -23, -25, -26 and -30 as proposed; LINT-5 off; -10 wildcard, with `E_MANIFEST` or `E_PRESET`; -20 include nested enums and exclude universe; -35 not checked; -38, -42, -45 and -48 as proposed.

## Consequences

The sections drop every *(OPEN)* marker and state these rules normatively. The new
codes appear in `spec/error-codes.md`, and requirements §6, §9 and implementation
plan item 3.7 are updated to match.
