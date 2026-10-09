# Edge bundle: rules

`edge-rules.json` is the compiled base project of the compile cases under `spec/conformance/compile/rules/` (work item C5: facts and conditions). It is the one bundle that those cases name, so it is both the expected output of the legal control case and the negative control for every error case.

- `bundleVersion`: `sha256:64ae4b2909e8ec7940ba69a9ef3a682b0cca0fdc8ac9e7d2685c398e6d7b5717` ([BUNDLE-5], [BUNDLE-6]). The file has no trailing newline ([BUNDLE-4]).
- Built with `js/spec-tools/canonicalize.mjs` from a content object kept in the scratch directory, outside the repository. The tool checked the content against `bundle.schema.json` and wrote the bytes. The bytes were derived by hand from the base project, so the schema and digest checks confirm the form, not the meaning. The meaning must be confirmed by a compiler ([DET-1]) once one exists.

## The base project

The base project has one root level (`global`), no axes and no scopes. Its entities are `customer`, `warehouse`, `invoice` (has_many `order`), `order` (belongs_to `customer`, has_many `item`), `item`, `shopper`, `basket` (belongs_to `shopper`, has_many `line`) and `line`. Its facts are the 14 facts of `schema/core.yaml` in the case projects, and its features are these three:

| Feature | Decided per | Shape | File in the project |
| --- | --- | --- | --- |
| `checkout.order_submission` | `order` | `credit_check` with three settings, five rules | namespaced `schema/components/checkout.yaml`, rules in `rules/checkout.order_submission.yaml` |
| `checkout.invoice_release` | `invoice` | `release_check`, three rules | namespaced `schema/components/checkout.yaml`, rules in `rules/checkout.invoice_release.yaml` |
| `pos.basket_check` | `basket` | `standard` with two settings, three rules | shared `schema/core.yaml`, rules in `rules/pos.basket_check.yaml` |

The order and invoice features follow sample domain A (`samples/commerce/`), and the basket feature follows sample domain B (`samples/retail-pos/`). The base project extends no preset and sets no lint, so no lint rule runs and every finding in a case is an engine finding.

Each positive case is this base project with one line replaced or inserted. The control case, `legal-forms`, is the base project itself, so its bytes are this bundle.

## Compilation decisions

| Decision | Clauses | Result in the bundle |
| --- | --- | --- |
| Fact `source` and `description` are dropped. A fact holds `entity` and `type` only | [BUNDLE-49], [BUNDLE-109], [FACT-10], [FACT-45] | `customer.active` has no `source` member |
| An integer fact keeps its declared bounds inside `type` | [BUNDLE-50], [FACT-9], [TYPE-7] | `order.lines` is `{type: integer, min: 1, max: 500}` |
| A dotted attribute is kept, split at the first dot | [FACT-3], [BUNDLE-48] | `order.ship.days` has entity `order` |
| Declared but unreferenced facts and settings are written | [FACT-12], [BUNDLE-48], [BUNDLE-84] | `order.ship.days`, `order.cutoff`, `warehouse.open` and the setting `held_flag` are written |
| Direct entity edges only | [BUNDLE-45], [BUNDLE-47] | `invoice.hasMany` is `["order"]`, and `order.hasMany` is `["item"]`, with no closure |
| A `when` is sorted by fact, whatever order the source writes | [BUNDLE-96], [DET-6] | `small-order` names `customer.rate` before `order.lines`, although the source writes them the other way |
| A rule with an empty `when` is `[]` | [BUNDLE-97], [RULE-2] | `any-order` and `any-invoice` have `"when": []` |
| An `in`, `all_in` or `any_in` list keeps code point order for strings and ascending order for integers | [BUNDLE-75], [BUNDLE-152] | `[1, 2, 3]`, `["1.5", "2"]` and enum values in code point order |
| Decimal literals are canonical strings | [NORM-10], [NORM-13], [BUNDLE-71] | `"1.50"` is written `"1.5"`, and `"0.5"` is kept |
| A setting reference stays symbolic | [BUNDLE-103], [BUNDLE-104], [RULE-4] | `"$hold_state"`, `"$allowed_on_credit_hold"`, `"$restricted_categories"`, `"$trusted_tender"` |
| A setting holds `valueOf` or `listOf` with its default | [BUNDLE-86], [BUNDLE-87] | `hold_state` is `{default: true, valueOf: "customer.credit_hold"}` |
| A binding writes only its own settings, and the root binding is keyed by `""` | [BUNDLE-66], [BUNDLE-107], [BUNDLE-108] | `"global": {"": {"policy": ..., "settings": {}}}` |
| `requiredFacts` is the distinct facts of the policy's conditions, in code point order | [BUNDLE-91], [BUNDLE-92] | `credit_check` lists six facts, and `pos.basket_check` lists four |
| The hierarchy has no scope entries, because it has no non-root level or axis | [SCOPE-16], [BUNDLE-29], [BUNDLE-31] | `"scopes": {}` and `"axes": []` |
| `keys` and `values` are present and empty | [BUNDLE-19], [BUNDLE-41] | `"keys": {}` and `"values": {}` |

## Cases that use this bundle

The control case is `legal-forms`. Each code below has its positive cases and the control.

| Code | Positive cases |
| --- | --- |
| `E_UNKNOWN_ENTITY` | `fact-entity-undeclared`, `fact-name-without-dot` |
| `E_UNKNOWN_FACT` | `unknown-fact-undeclared`, `unknown-fact-entity`, `unknown-fact-precedence` |
| `E_INVALID_DECLARATION` | `fact-attribute-empty`, `fact-type-unknown`, `fact-type-set`, `fact-source-not-string`, `fact-member-not-allowed`, `list-operand-on-child`, `list-operand-on-child-domain-b`, `decimal-operator`, `when-not-mapping`, `operator-unknown`, `operator-two`, `operator-none`, `operand-scalar` |
| `E_TYPE_REQUIRED` | `fact-type-missing` |
| `E_DUPLICATE_KEY` | `fact-duplicate-key`, `fact-name-nfc-duplicate`, `when-duplicate-fact` |
| `E_NAMESPACE_VIOLATION` | `fact-namespaced-declaration` |
| `E_FACT_NOT_IN_SCOPE` | `fact-sibling-entity`, `fact-has-many-parent`, `setting-fact-not-in-scope`, `quantifier-not-allowed-precedence` |
| `E_DEPTH_UNSUPPORTED` | `depth-any-in`, `depth-form-precedence` |
| `E_QUANTIFIER_REQUIRED` | `quantifier-required-headline`, `quantifier-required-domain-b`, `quantifier-required-setting-ref`, `quantifier-required-precedence-literal`, `quantifier-required-precedence-setting` |
| `E_QUANTIFIER_NOT_ALLOWED` | `quantifier-not-allowed` |
| `E_ILLEGAL_FACT_VALUE` | `illegal-bool-string`, `illegal-enum-case`, `illegal-decimal-number`, `illegal-null`, `illegal-null-element` |
| `E_INTEGER_RANGE` | `integer-range-precedence` |
| `E_DUPLICATE_VALUE` | `duplicate-literal` |
| `E_UNKNOWN_SETTING` | `unknown-setting`, `unknown-setting-empty-name`, `dollar-literal` |
| `E_INVALID_SETTING` | `setting-in-literal-list`, `setting-precedence-literal`, `setting-kind-mismatch`, `setting-fact-mismatch` |

`E_DUPLICATE_DEFINITION` has no case here, for the reason in the open points.

## Open points

- **Location of condition and setting findings.** No clause says which node a finding about a condition names. The cases use the entry of the fact that the `when` mapping keys, `[..., rules, i, when, <fact>]`, on the line of that key. A non-mapping `when` is located at `[..., when]`. A setting finding is located at its `value_of` or `list_of` value, `[..., settings, <name>, value_of]`. A namespaced top-level key is located at its key. An unknown or non-string member is located at the member, and an unknown or set `type` at the value of `type`. A missing `type` is located at the declaration, by [FIND-23]. Each convention needs a clause before the cases can be trusted.
- **`E_DUPLICATE_DEFINITION` across sources.** No clause says where the second declaration is located, and [SRC-37] makes the union independent of file order. No case is written, so the across-source part of [FACT-5] is not exercised.
- **Dot-less condition name.** `error-codes.md` lists this as OPEN, so no case is written for it ([VEC-114]).
