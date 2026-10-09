# Edge bundles for the features, policies and bindings cases

Two canonical bundle files serve the compile cases of clause areas FEAT, POL and the compile parts of BIND. They are written by hand, because no reference compiler exists yet. The content object was written as JSON outside the repository, then turned into canonical bytes by `js/spec-tools/canonicalize.mjs`, which checks the content against `spec/schemas/bundle.schema.json` first ([BUNDLE-2], [BUNDLE-6]). The bytes are those that section 10 defines, and `pnpm spec:check` verifies their form and digest. Nothing here verifies that a compiler writes these exact bytes. That check waits for a compiler.

| Bundle | `bundleVersion` | Compiled from | Used by |
| --- | --- | --- | --- |
| `edge-features.json` | `sha256:c370dcaf812468afbbe4b90acc3270e3f5b2ebb6bc06a6a0bad1de56e7d8e15e` | the main base project, reproduced in `compile/bnd-reference-clean/project/` | every compile case whose positive is a finding on that base, through its `-ok` control, and `bnd-reference-clean`, `pol-file-name-differs`, `ex-quote-decided-expect` |
| `edge-features-small.json` | `sha256:22f95c6f86d7fbf484765417a4aa3646c9269e374c9c1e6ae08dc5f0b04a504d` | the small base project, reproduced in `compile/feat-string-null-effect/project/` | the feature-declaration cases `feat-decided-per-*`, `feat-unknown-feature`, and `feat-string-null-effect` |

The digests in this table are the ones at the time of writing. Each bundle file has its own digest in its envelope.

## Main base project (edge-features.json)

- Manifest: `specVersion: "0.1"`, one schema file, and every file under `rules/`.
- Hierarchy: levels `global`, `division`, `account`; axis `seller`. `division` is a closed catalog (`north`, `south`, `on_unknown_node: error`). `account` is service-sourced and open. `seller` is a closed catalog (`acme`). Every non-service scope gets `values: "files"` ([BUNDLE-35]).
- Entities: `customer`, `order` (belongs to `customer`, has many `item`), `item`.
- Facts: `customer.active` and `customer.credit_hold` (boolean), `item.stock_type` (enum), `item.weight` (integer).
- Features: `checkout.order_submission` (enum `ALLOW`, `DENY`; bindings at `global` and `division`) and `checkout.quote` (object form; bindings at `global` only).

| Decision | Clauses | Result in the bundle |
| --- | --- | --- |
| A feature's effects are sorted in code point order | [BUNDLE-75], [BUNDLE-82] | `returns.enum` for `checkout.order_submission` is `["ALLOW", "DENY"]` |
| The object form has no `nullable` member, and field declarations are flat type declarations with `optional` only when optional | [BUNDLE-82], [FEAT-12] | `checkout.quote` returns `{ "object": { "days": {...}, "method": {...}, "note": {"optional": true, ...} } }` |
| An enum field's values are sorted | [BUNDLE-75] | `method` values are `["GROUND", "NEXT_DAY"]` |
| A `requiredFacts` array holds the distinct facts of all conditions, in code point order | [BUNDLE-91], [BUNDLE-92] | `credit_check` lists `customer.active`, `customer.credit_hold`, `item.stock_type`; `allow_all` lists none |
| A rule with an empty `when` has `when: []`, and its name is kept | [BUNDLE-95], [BUNDLE-97] | `allow_all` has rule `good-credit`, named `Always allow`, with `when: []` |
| A rule id may repeat across policies | [POL-24] | `good-credit` appears in both `credit_check` and `allow_all` |
| `when` is sorted by fact name, and a setting reference stays symbolic | [BUNDLE-96], [BUNDLE-103], [BUNDLE-104] | `item.stock_type` has `"op": "all_in"` and `"value": "$allowed_on_credit_hold"` |
| A binding's list setting value is sorted | [BUNDLE-90], [BUNDLE-108] | `south` writes `["SPECIAL_ORDER", "STOCKED"]`, from the source order `[STOCKED, SPECIAL_ORDER]` |
| The root level's binding sits under level `global` with node key `""` | [BUNDLE-66], [BUNDLE-105], [BIND-2] | `bindings.global[""]` for each feature |
| A scope with no node entries is absent from `values`, and `values` and `keys` are present and empty | [BUNDLE-19], [BUNDLE-22] | `"keys": {}` and `"values": {}` |
| The `account` service level has `nodes: "open"`, `values: "service"` and no `onUnknownNode` | [BUNDLE-34], [BUNDLE-36], [BUNDLE-42] | `hierarchy.scopes.account` |
| Examples, the source of `fact` and descriptions are not written | [BUNDLE-109] | No example, `name` of an example, or `description` appears |

## Small base project (edge-features-small.json)

- Hierarchy: level `global` only, so `scopes` is `{}` ([SCOPE-16], [BUNDLE-29]).
- Entities `customer` and `order` (belongs to `customer`); no facts; no keys; no values.
- Feature `checkout.order_submission`: enum `ALLOW`, `DENY`, `"null"` (the string, which FEAT-10 says has no special meaning); policy `allow_all` with `default: "null"` and no rules; binding `global` only.

The small base has no fact conditions, so a finding about its decided entity or its feature name has no cascading finding from rule checks. The clause that governs what happens to the rules of a feature whose decided entity is undeclared is not stated by the spec, so those cases use the small base.

## Assumptions in the case expectations

The cases in `compile/` derive their findings by applying the clauses named in each case. The following choices are the case author's reading. A reviewer should check them against the clauses cited.

- The location of `E_ROOT_BINDING_MISSING` is the `bindings` mapping of the rules file ([FIND-23]), because the missing root binding sits in that mapping.
- A binding at a non-listed level is reported once per node binding, so `feat-binding-not-listed-level` expects one finding each for `north` and `south` ([BIND-12], [FIND-3]).
- A repeated shorthand key in an example is reported at its second occurrence ([POL-36]).
- A duplicate key of a policy is reported at its second occurrence ([SRC-3], [SRC-9]).
- The example `ex-run-only-without-findings` relies on [POL-47]: an engine finding in the same validation means no example is run, so its wrong expect is not reported.
