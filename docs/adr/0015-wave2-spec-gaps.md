# ADR 0015: Spec gaps found by the wave 2 artifacts

- Status: accepted (delegated technical decision)
- Date: 2026-10-09

Writing the schemas, presets and samples exposed small gaps in the spec text.

| Clause | Decision |
| --- | --- |
| SRC-18(b) | `**` as a whole segment matches **zero** or more directory segments, so `schema/**/*.yaml` also selects `schema/core.yaml` |
| FEAT-1 | A feature may declare `description` (an informational string), which LINT-44 already requires |
| RULE-34 | Only a literal `[]` is a dead rule (LINT-26); a setting reference is not (LINT-28) |
| RULE-10 | Applies only to names that contain `.`; a name with no `.` follows FACT-3 (`E_UNKNOWN_ENTITY`) |
| EVAL-28 | The stale "no timeout is open" sentence is replaced by a reference to SVC-48 |
| New NS clause | A preset containing `namespace` is `E_PRESET`; `preset.schema.json` forbids it |
| NS-9 | An empty namespace name is `E_MANIFEST` |
| New SRC clause | A top-level schema-file member that is `null`, or of the wrong shape, is `E_INVALID_DECLARATION` unless a more specific code applies |
| SCOPE-9 | An absent `levels`, or an unknown `decision_hierarchy` member, is `E_SCOPE_DECLARATION` |
| KIND-3 | An empty `strategies` list is `E_KIND_DEFINITION` |
| KEY-9, FACT-45 | `description` and `owner` must be strings |
| VEC-19 | `context` and `input` contents are exempt from the closed-schema rule, so CTX and FactSet shape errors stay testable |
| Section 9 example | Rewritten under `deny`, which the sample's `capability` kind actually applies |
| `tierwise:entitlements` | Accepted as written: lint severities copied from `recommended`; naming patterns `key`, `limit_key`, `feature_key` and `grant_key`; `deny` for `limit` (fail closed) |
