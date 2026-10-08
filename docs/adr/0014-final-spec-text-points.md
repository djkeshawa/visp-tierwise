# ADR 0014: Final open points in the spec text

- Status: accepted for the Phase 1 specification (delegated technical decision)
- Date: 2026-10-09

After [ADR 0013](0013-spec-open-point-resolutions.md) was applied (wave 1b), five
clauses were still *(OPEN)* and the item reviewers raised a few small gaps. This
ADR decides all of them.

| Clause | Decision |
| --- | --- |
| TYPE-80 | Spec 0.1 pins IANA tz database release **2026e**, the latest on 2026-10-09. `spec/data/tz-names.txt` lists its 597 Zone and Link names from the main data files and `backward`, excluding `Factory` and `backzone`. A new release needs a spec patch version. |
| SVC-48 | A provider registered with **no timeout** is `E_EVALUATOR_OPTIONS`. The core evaluator hides no default. Runtime integrations supply their defaults (e.g. the Spring starter's 200 ms) before creating the evaluator. |
| EVAL-56 | A **missing** `bundleFormatVersion` or `specVersion` is `E_BUNDLE_INTEGRITY`. A present value other than exactly `"1"` or `"0.1"`, whatever its type, is `E_BUNDLE_UNSUPPORTED`. |
| EVAL-53 | Evaluators **don't serialise** listener calls, so listeners must tolerate concurrent calls. Each event carries `type`, `bundleVersion`, and where applicable `level`, `node` and `key`. Implementations may add members. Conformance vectors don't assert listener events. |
| BUNDLE-152 | Arrays of integer literals are written in ascending numeric order; arrays of booleans with `false` before `true`. |
| BUNDLE-150(a) | `gitCommit` is 40 (SHA-1) or 64 (SHA-256) lowercase hexadecimal digits. |
| Value equality (POL-48) | New clause NORM-19: two values are equal iff they have the same type and: scalars as NORM defines; sets have the same elements; lists have the same length and equal elements in order; objects have the same field names with equal values. |
| Examples with other errors (POL-47) | Examples run only when no other engine finding exists. Otherwise they are not run and nothing is reported for them. |
| LINT-17 bracket classes | Inside a class: an optional leading `^`; then literal code points other than `\ [ ] ^ -`, ranges `a-z` between two such literals, and backslash escapes of `\ [ ] ^ -`. Nested classes, `&&` and POSIX classes are outside the subset. |
| LINT-14 pattern names | A pattern name is non-empty, NFC, and contains no `/`, `*` or whitespace. Otherwise the declaration is invalid. |
| ENTITY-18, ENTITY-19, FACT-3, FACT-45 | The item reviewer's choices are accepted: an empty entity name and an empty attribute part are `E_INVALID_DECLARATION`; a non-string entity name, including `null`, is `E_TYPE_MISMATCH`; facts don't carry `owner`. |
| Requirements §4.4 | Updated to the ADR 0013 rule: a non-settable provider entry is ignored and reported to the listener. |
