# spec/conformance/evaluate/load

Evaluate vectors for bundle loading: the load step that [VEC-24] runs first, under [EVAL-2], [EVAL-14], [EVAL-24], [EVAL-25] and [EVAL-56]. A load vector is an ordinary evaluate vector. Its one `resolve` or `decide` call is the operation that the load step must not get past, or it resolves a key whose value shows that the load worked.

## Layout

- `invalid/`: one vector per file in `bundles/invalid/` ([VEC-70], [VEC-71]). Each expects the code that its fault raises. One extra vector, `digest-mismatch-decide-load-first`, shows that load bundle runs before the feature lookup of a `decide` call ([VEC-74], [VEC-24]).
- `valid/`: successful loads. Each of the three sample bundles has one vector, and the focused `edge-load.json` has two ([BUNDLE-137], [DET-4]).

## Invalid bundles

| Vector | File | Expected code | Fault |
| --- | --- | --- | --- |
| `load/invalid/duplicate-member` | `duplicate-member.json` | `E_BUNDLE_INTEGRITY` | A repeated property name inside a key ([BUNDLE-111]) |
| `load/invalid/extra-envelope-member` | `extra-envelope-member.json` | `E_BUNDLE_INTEGRITY` | A third envelope member ([BUNDLE-112]) |
| `load/invalid/digest-mismatch` | `digest-mismatch.json` | `E_BUNDLE_INTEGRITY` | Changed content with the old digest ([BUNDLE-113]) |
| `load/invalid/non-integer-number` | `non-integer-number.json` | `E_BUNDLE_INTEGRITY` | The number 4.5 ([BUNDLE-116], [VEC-80]) |
| `load/invalid/integer-out-of-range` | `integer-out-of-range.json` | `E_BUNDLE_INTEGRITY` | The integer 2^53 ([DET-4], [VEC-80]) |
| `load/invalid/unsupported-format-version` | `unsupported-format-version.json` | `E_BUNDLE_UNSUPPORTED` | `bundleFormatVersion` is `"2"` ([BUNDLE-115]) |
| `load/invalid/unsupported-spec-version` | `unsupported-spec-version.json` | `E_BUNDLE_UNSUPPORTED` | `specVersion` is `"0.2"` ([BUNDLE-115]) |
| `load/invalid/missing-spec-version` | `missing-spec-version.json` | `E_BUNDLE_INTEGRITY` | Content with no `specVersion` ([EVAL-56]) |
| `load/invalid/non-string-spec-version` | `non-string-spec-version.json` | `E_BUNDLE_UNSUPPORTED` | `specVersion` is the integer `1` ([EVAL-56]) |
| `load/invalid/empty-scope-name` | `empty-scope-name.json` | `E_BUNDLE_INTEGRITY` | A level named by the empty string ([SCOPE-8], [BUNDLE-118]) |
| `load/invalid/entity-graph-cycle` | `entity-graph-cycle.json` | `E_BUNDLE_INTEGRITY` | A cycle through `belongsTo` and `hasMany` edges ([ENTITY-17], [BUNDLE-118]) |
| `load/invalid/deny-non-narrowing` | `deny-non-narrowing.json` | `E_BUNDLE_INTEGRITY` | `onSourceFailure` `deny` on an `override` key settable at a service-sourced level ([SVC-11], [BUNDLE-118]) |
| `load/invalid/deny-min-without-bound` | `deny-min-without-bound.json` | `E_BUNDLE_INTEGRITY` | `onSourceFailure` `deny` on a `min` key whose integer type has no `min` member ([SVC-12], [BUNDLE-118]) |

The two version files that this family adds, `missing-spec-version.json` and `non-string-spec-version.json`, were written with the JCS helper that `js/spec-tools/canonicalize.mjs` uses. The canonicalizer refuses content that fails `bundle.schema.json`, which is the point of these faults. Both digests were checked against an independent SHA-256 of the JCS text. The integer `1` was chosen rather than the number `0.1`, because `0.1` breaks [BUNDLE-11] and would raise `E_BUNDLE_INTEGRITY` at the first step, so it would test the question below and not the type.

The `bundleFormatVersion` branch of [EVAL-56] has no vector: a missing `bundleFormatVersion` (`E_BUNDLE_INTEGRITY`) and a non-string `bundleFormatVersion` (`E_BUNDLE_UNSUPPORTED`) are not on the closed list of [VEC-71] (ADR 0016 adds only the `specVersion` cases), and [VEC-72] limits the invalid files to that list. The gap is deliberate. The same branch is checked for a present string value by `unsupported-format-version`.

## Valid bundles

| Vector | Bundle | Key | Expected |
| --- | --- | --- | --- |
| `load/valid/commerce-lead-hours-division` | `commerce.json` | `checkout.pickup.lead_hours` with `division: south` | `6`, from the division value, not the root value `2` or the default `4` |
| `load/valid/retail-pos-max-discount-region` | `retail-pos.json` | `pos.pricing.max_discount_percent` with `region: inland` | `15`, the maximum of the platform value `10` and the region value `15` |
| `load/valid/saas-max-seats-org` | `saas-entitlements.json` | `usage.max_seats` with `org: acme` | `10`, the minimum of the plan value `25` and the org value `10` |
| `load/valid/edge-max-safe-default` | `edge-load.json` | `example.max_safe` | `9007199254740991`, the default, source `DEFAULT` |
| `load/valid/edge-min-safe-root-value` | `edge-load.json` | `example.min_safe` | `-9007199254740991`, the root value |

The other valid bundles under `bundles/` (the `edge-*` bundles of other areas) are loaded by the vectors of their own areas, so a load vector for each would repeat them. Each derived value follows the MERGE and PROV clauses that the vector cites. Each valid vector names the `bundleVersion` of its bundle, so that a loader which read another file fails the vector ([PROV-18]).

## Clauses this family does not cite

The structural loader faults [BUNDLE-117] and [BUNDLE-119] to [BUNDLE-136] and the determinism rules [DET-7] and [DET-8] are listed in `spec/conformance/enforcers.d/E7.yaml`. [BUNDLE-118] is listed there too, for the structural faults that no vector observes. Four of its faults are observed by vectors: the deny faults of [SVC-11] and [SVC-12] by `deny-non-narrowing` and `deny-min-without-bound`, and the empty level name of [SCOPE-8] and the entity-graph cycle of [ENTITY-17] by `empty-scope-name` and `entity-graph-cycle`. A vector cannot observe the other faults under the closed fault list ([VEC-71], [VEC-80]) or in a different environment. [EVAL-3] and [EVAL-55] are listed there too. The byte-order mark of [BUNDLE-3] and the unpaired surrogate of [BUNDLE-13] are listed there as well, with the check tool as enforcer, because the check tool rejects them in `bundles/`. [BUNDLE-116] is cited by the number vectors and is also listed there, for its cases that no vector observes (input that is not UTF-8 or not well-formed JSON, an envelope without `bundleVersion` or `content`, `-0` and other number forms, and bytes that are not the JCS serialisation). [BUNDLE-117] is listed because [VEC-69], [VEC-22] and [VEC-23] together allow one fault per invalid bundle file, so no vector can hold the several faults that an order test needs.

## Open points

- (OPEN) [EVAL-56] says a present `specVersion` that is not the string `"0.1"` raises `E_BUNDLE_UNSUPPORTED` "whatever its JSON type". [EVAL-14] runs the strict-parse checks first, and [BUNDLE-116] rejects a non-integer number at that step. So a `specVersion` written as the number `0.1` raises `E_BUNDLE_INTEGRITY` under the order of [EVAL-14], and `E_BUNDLE_UNSUPPORTED` under the wording of [EVAL-56]. Recommended resolution (ADR needed, then amend [EVAL-56] and the descriptions in `bundle.schema.json`): "whatever its JSON type" means any type that passes step 1, so a non-integer number raises `E_BUNDLE_INTEGRITY`. None of the vectors in this family depends on the answer ([VEC-114]): the non-string case uses the integer `1`.
- (OPEN) [VEC-69] allows one fault per invalid bundle file, and [VEC-74] allows several faults in a vector that tests the order of checks. A bundle path may lie only in `bundles/` or `bundles/invalid/` ([VEC-23]), so the two clauses pull apart: an order test for [BUNDLE-117] would need an invalid file with two faults. Recommended resolution: keep [BUNDLE-117] as an evaluator unit test for v0.1 (it is listed in `enforcers.d/E7.yaml`), and let a later revision admit order-test files under `bundles/invalid/` by extending [VEC-69] and [VEC-71].
- (OPEN) [BUNDLE-11] excludes `-0`, and no vector tests it, because [VEC-71] and [VEC-80] do not list it. Recommended resolution: keep the closed list for v0.1 (ADR 0016) and treat `-0` like the other byte-level and number-form faults. It is recorded in the [BUNDLE-116] entry of `enforcers.d/E7.yaml` as an evaluator unit test. A later revision may add `-0` to [VEC-80] with one invalid file.
