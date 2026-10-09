# edge-providers.json

A focused bundle for the provider and evaluator-creation vectors of work item E3 (section 12, [VEC-123] families 3 and 13, [VEC-38] to [VEC-40], [VEC-41] to [VEC-47]). It is not a sample. It is built by hand to isolate one provider behaviour per key, so that every outcome of [SVC-23] to [SVC-37] under `skip` and under `deny` has a key that shows it. The file is canonical bytes written by `js/spec-tools/canonicalize.mjs` from a content object kept outside the repository (BUNDLE-2, BUNDLE-6). Its `bundleVersion` is `sha256:ed37b3a855432c9d0ff67039a9a80e377962ef85d0a8df0ce5a302be8549523b`.

The vectors that use it are in `spec/conformance/evaluate/providers/`. Three other bundles are used for domain cases: `commerce.json` (domain A), `retail-pos.json` (domain B) and `saas-entitlements.json` (the docs-only sample). Each is documented in its own README.

## Hierarchy

Levels in declared order: `global` (root), `region`, `tenant`, `partner`, `seat`. There are no axes. The evaluation path for a context is the root, then each admitted level in that order.

| Level | `values` | `nodes` | `onUnknownNode` | Purpose |
| --- | --- | --- | --- | --- |
| `global` | root | none | none | Root values, and the identity when nothing else contributes |
| `region` | `files` | `east`, `west` | `error` | A file-sourced narrowing value, and an unlisted node that must raise `E_UNKNOWN_NODE` |
| `tenant` | `service` | open | none | The main provider level |
| `partner` | `service` | `p-1`, `p-2` | `ignore` | A closed service-sourced catalog, so an unlisted node skips the scope ([CTX-14], [CTX-15]) |
| `seat` | `service` | open | none | A service-sourced level at which no key is settable ([SVC-14], [SVC-17]) |

## Keys

| Key | Type | `combine` | `on_source_failure` | Settable at | Values in the bundle | Deny value |
| --- | --- | --- | --- | --- | --- | --- |
| `access.allowed` | boolean | `all` | `deny` | `tenant`, `partner` | none | `false` |
| `quota.max_items` | integer, `min` 2, `max` 100 | `min` | `deny` | `region`, `tenant`, `partner` | `region` `east`: 6 | `2` (the `min` member) |
| `shipping.methods` | set `[GROUND, PICKUP, NEXT_DAY]` | `intersect` | `deny` | `global`, `tenant`, `partner` | root: `[GROUND, PICKUP]` | `[]` |
| `export.formats` | set `[csv, pdf]` | `union` | `skip` | `tenant` | none | no deny value (skip) |
| `display.theme` | string | `override`, default `light` | `skip` | `tenant`, `partner` | none | no deny value (skip) |
| `profile.window` | object `{close, open}`, both `time_hhmm` | `deep_merge`, default `{close: 18:00, open: 08:00}` | `skip` | `tenant` | none | no deny value (skip) |

Each of the three deny keys has a deny value that differs from its identity: `false` against `true` for `all`, `2` against absence for `min`, and `[]` against the full universe for `intersect`. `quota.max_items` has no root value, so a deny value of `2` is visible on its own, and a deny value of `null`, `0` or the region value would be wrong. `shipping.methods` has a root value, so a deny value of `[]` narrows it to empty.

`profile.window` covers [SVC-32] (a partial object from a provider is valid for `deep_merge`) and [MERGE-11], [MERGE-12] and [PROV-53].

## Feature (decide only)

`order.express_allowed`: decided per `order`, returns `ALLOW` or `DENY`, `FIRST` hit policy, `if_fact_missing` `DENY`, bindable at `global` only ([FEAT-19]). Its one policy `express` has the rule `express-ok` (`order.express` equals `true` gives `ALLOW`) and the default `DENY`. Decide vectors use it to show that no provider is called ([SVC-22]).

## Vector families

All vectors are under `spec/conformance/evaluate/providers/`. An `unavailable` vector and its `timeout` twin share one `expect`, as [VEC-44] requires; the twin differs only in `id`, `description`, `clauses` and the fail value, and the timeout twin also cites [SVC-39].

| Family | Vectors | What each checks |
| --- | --- | --- |
| Deny, `all` (`access.allowed`) | `access-values-false`, `access-values-identity-true`, `access-unavailable` and `access-timeout`, `access-omitted`, `access-unknown-key-null`, `access-invalid-null`, `access-invalid-string`, `access-undeclared-not-validated`, `access-partner-ignored` | VETO and IGNORED labels, failure and deny value `false`, omitted and undeclared entries, INVALID for null and wrong type, declared keys that are not resolved are not validated ([SVC-28]), and an ignored partner gives no deny value |
| Deny, `min` (`quota.max_items`) | `quota-values-with-region`, `quota-provider-called-though-region-wins`, `quota-unavailable-with-region` and `quota-timeout-with-region`, `quota-unavailable-bound-only` and `quota-timeout-bound-only`, `quota-omitted-with-region`, `quota-invalid-below-min`, `quota-invalid-above-max`, `quota-absent-null`, `quota-key-name-case` | The deny value is the declared minimum `2`, with and without another scope, and it is not `null` or `0`; INVALID below the minimum and above the maximum; an absent `min` result is `null`; a key name differing only in case is undeclared ([SVC-16]) |
| Deny, `intersect` (`shipping.methods`) | `shipping-values-narrowed`, `shipping-values-full-universe`, `shipping-unavailable` and `shipping-timeout`, `shipping-invalid-outside-universe`, `shipping-omitted` | NARROWED and IGNORED (full universe, [SVC-33]), deny value `[]`, INVALID for a member outside the universe, omitted entry |
| Skip, `override` (`display.theme`) | `theme-values`, `theme-unavailable` and `theme-timeout`, `theme-omitted`, `theme-unknown-key-null`, `theme-invalid-integer`, `theme-partner-ignored` | A valid value sets the result with source `tenant`; a failure contributes no value, so the default applies with source `DEFAULT` and the failure entry is still listed ([PROV-30], [PROV-48]); an ignored partner makes no call |
| Skip, `union` (`export.formats`) | `formats-values`, `formats-unavailable` and `formats-timeout` | A non-empty set is WIDENED; a skipped failure leaves the identity `[]`, which is a value and not `null` ([MERGE-5]) |
| Skip, `deep_merge` (`profile.window`) | `window-partial-valid`, `window-invalid-skip` | A partial object is valid and merges over the default; a time outside `time_hhmm` is INVALID and, under skip, leaves the default |
| Call counts | `calls-once-per-scope`, `calls-not-settable-level`, `calls-omitted-level`, `calls-node-id-as-supplied`, `unknown-key-before-provider` | At most one call per level ([SVC-19]); no call at a level where the key is not settable ([SVC-17]); no call for an omitted level ([CTX-21]); the node is passed exactly as supplied, including a non-NFC node ([SVC-15]); an undeclared key fails before any call |
| Synchronous resolve | `sync-provider-required`, `sync-provider-not-required-control`, `sync-no-provider-needed`, `sync-unknown-key-first`, `sync-context-error-first` | `E_PROVIDER_REQUIRED` with no call, and its negative control, which differs only in a context that names a level at which the key is not settable; the same result as resolve when no call is needed; a lookup or context error comes before the provider need ([EVAL-16], [EVAL-18], [EVAL-11]) |
| Creation | `create-missing-provider-none-registered`, `create-provider-all-present-control`, `create-missing-provider-not-in-context`, `create-provider-on-files-level`, `create-provider-on-root-level`, `create-provider-undeclared-level`, `create-provider-declared-levels-control`, `create-undeclared-before-missing`, `create-files-level-before-missing`, `create-missing-before-key-lookup`, `create-missing-provider-decide` | `E_PROVIDER_MISSING` for each service-sourced level registered as `none`, whether or not the context names it (a level that `providers` does not name is an empty stub ([VEC-39]) and raises nothing); `E_EVALUATOR_OPTIONS` for a registration on a file level, on the root level or on an undeclared level, which takes precedence over a missing provider ([EVAL-15], [EVAL-27], [SVC-14]); creation errors come before key lookup; decide shares the check. `create-provider-all-present-control` and `create-provider-declared-levels-control` are the negative controls of the first and fifth cases: each differs from its positive case by one deleted registration ([VEC-120]) |
| Decide | `decide-express-allowed`, `decide-default-partner-ignored-unavailable` and `-timeout`, `decide-sync-express-allowed` | Decide and its synchronous variant call no provider, even when the tenant stub would fail ([SVC-22]); an ignored partner appears in `skippedScopes` |
| Domain A (`commerce.json`) | `commerce-container-account-unavailable` and `-timeout`, `commerce-container-user-not-settable` | An unavailable account provider under deny makes `checkout.container_order.allowed` `false`; the user level has no settable key and is not called |
| Domain B (`retail-pos.json`) | `retail-line-limit-terminal-unavailable` and `-timeout` | An unavailable terminal provider under deny gives `pos.basket.line_limit` its declared minimum `1`, below the platform value `200` and the store value `80` |
| Docs-only sample (`saas-entitlements.json`) | `saas-max-seats-user-unavailable` and `-timeout`, `saas-granted-formats-user-unavailable` and `-timeout` | A deny value of `0` at the integer minimum gives a `min` result of 0 below the plan and org values; an unavailable user under skip leaves the plan's granted formats |

The matrix of [VEC-123] family 13 is covered as follows. Under `deny`, `access.allowed` covers values, unavailable, timeout, omitted, unknown key and invalid; `quota.max_items` covers values, unavailable, timeout, omitted and invalid; `shipping.methods` covers values, unavailable, timeout, omitted and invalid. Under `skip`, `display.theme` covers values, unavailable, timeout, omitted, unknown key and invalid; `export.formats` covers values, unavailable and timeout. The unknown-key cell under `deny` is `access-unknown-key-null`, and the matching cell under `skip` is `theme-unknown-key-null`. The non-settable-key cell cannot be a single-key vector. A single-key resolve never calls a level at which its key is not settable ([SVC-17]), so the entry it would ignore never arrives. That cell is covered by the no-call vectors `calls-not-settable-level` and `commerce-container-user-not-settable`, and the ignored entry itself is listed as an enforcer ([SVC-29], [SVC-30], [SVC-47]).

## Enforcers

Clauses that no vector can observe are listed in `spec/conformance/enforcers.d/E3.yaml`, with one line each. They are the multi-key and concurrency rules ([SVC-20], [SVC-21], [SVC-37]), the non-mapping provider result ([SVC-25]), wall-clock and timeout rules ([SVC-40], [SVC-41]), evaluator bookkeeping ([SVC-6], [EVAL-5], [EVAL-6]), the `E_EVALUATOR_OPTIONS` case of registering one level twice ([EVAL-27]), and the listener rules ([SVC-43] to [SVC-47], [EVAL-41] to [EVAL-45], [EVAL-53]). `SVC-38`, `SVC-48` and `EVAL-28` are already listed in `spec/conformance/enforcers.yaml`, and are not repeated.

## Decisions taken while deriving the expectations

1. Contributions are listed in evaluation-path order, root first ([PROV-34]). A skipped failure under `skip` appears as a failure entry with no `value` ([PROV-30]), and it contributes nothing to the result ([PROV-48]).
2. A deny value appears as the `value` of a failure entry, and its label stays `UNAVAILABLE` or `INVALID` ([PROV-41]). The deny value of `shipping.methods` is `[]`, which is a value and not `null`.
3. The timeout twins use `fail: timeout`. No vector carries a duration ([VEC-45]).
4. Node IDs are plain strings, except `café`, which is a double-quoted YAML scalar with a combining mark, so that the node is not in NFC ([CTX-7]). The check tool does not normalise values, so the provider stub key and the context value are the same code points.
5. `providerCalls` counts are given only where a call is possible. `decide` and every synchronous variant name `0` ([VEC-65], [SVC-22]).

## Points settled by the specification, and one open point

- A service-sourced level may have a closed catalog with `on_unknown_node: ignore`. This is settled, not open: [KEY-6] lets a service-sourced level appear in `settable_at`, [SCOPE-22] and [CTX-14] apply the closed-catalog skip to any scope, and [CTX-15] and [SVC-17] state that a skipped service-sourced scope makes no provider call and triggers no source failure. [VEC-123] family 3 presumes it. The partner vectors rely on these clauses.
- `providerCalls` counts only invocations during the operation under test ([VEC-65]). [EVAL-6] forbids any provider call during creation, so no creation-time call can arise. This is settled, not open.
- (OPEN) A `providers` entry of `none` for a level that is not service-sourced, or that the bundle does not declare. [VEC-38] allows a `providers` entry for such a level only when the vector expects `E_EVALUATOR_OPTIONS`, and [VEC-40] gives `none` its effect (`E_PROVIDER_MISSING`) only for a service-sourced level, so the outcome of `none` on a file level or an undeclared level is not stated. No vector here uses `none` on those levels. Recommended resolution: [VEC-40] states that `none` for a level that is not a service-sourced level of the bundle is a runner failure.
