# 9. Provenance

This section defines the exact JSON shape of the two results that carry provenance: the `Resolved` result returned for a key, and the `Decision` result returned for a feature. For each field it states when the field is present, what it holds, and the order of every list. It defines the contribution entries, failure entries, contribution labels, the `source` attribution of order-dependent keys, the `skippedScopes` entries, and the shape of decision reasons and traces. It does not define the semantics that produce these values. Those are the merge results ([MERGE-17], [MERGE-19], [MERGE-20], [AXIS-7]), provider outcomes ([SVC-17], [SVC-23], [SVC-31], [SVC-34]), admission ([CTX-14] to [CTX-16]), binding selection ([BIND-17]), and rule evaluation and trace content ([RULE-42] to [RULE-55]). Value encoding is defined by [BUNDLE-68] to [BUNDLE-74], and raised errors by [EVAL-7] and [EVAL-8].

## 9.1 Common rules

**[PROV-1]** Every `Resolved` result and every `Decision` result MUST have the shape that this section defines.

**[PROV-2]** An evaluator MUST NOT let a preset, a project setting or an evaluator option change that shape, because provenance on every result is an engine guarantee (requirements §1).

**[PROV-3]** A conditional field whose condition is false MUST be absent from the result, not present with JSON `null`.

**[PROV-4]** `skippedScopes` MUST be present on every result.

**[PROV-5]** `contributions` MUST be present on every `Resolved` result.

**[PROV-6]** `trace` MUST be present on every `Decision` result.

**[PROV-7]** Each of `skippedScopes`, `contributions` and `trace` MUST be an empty array when it has no entries.

**[PROV-8]** A source failure MUST be reported as a failure entry in `contributions`, and MUST NOT raise an evaluation error ([EVAL-39]).

**[PROV-9]** Every other failure that prevents a valid result MUST be raised as an evaluation error with its code, and MUST NOT be returned as a result ([EVAL-7], [EVAL-8]).

## 9.2 Resolved result

**[PROV-10]** A `Resolved` result MUST contain the fields `key`, `strategy`, `value`, `bundleVersion`, `skippedScopes` and `contributions`.

**[PROV-11]** A `Resolved` result MUST NOT contain any field other than `key`, `strategy`, `kind`, `value`, `bundleVersion`, `skippedScopes`, `contributions` and `source`.

**[PROV-12]** `key` MUST be the declared name of the key.

**[PROV-13]** `strategy` MUST be the key's merge strategy, the value of its `combine` member ([MERGE-1]).

**[PROV-14]** `kind` MUST be present if and only if the key opts into a kind ([KIND-2]).

**[PROV-15]** When `kind` is present, its value MUST be the name of that kind.

**[PROV-16]** `value` MUST hold the key's result in the JSON encoding of its type that [BUNDLE-68] to [BUNDLE-74] define.

**[PROV-17]** `value` MUST be JSON `null` if and only if the result is absent ([MERGE-6]). A failure entry under `skip` contributes no value ([MERGE-15], [SVC-8]), so a `min` or `max` key whose only entries are such failures has `value` `null`.

**[PROV-18]** `bundleVersion` MUST be the `bundleVersion` of the bundle that the evaluator loaded ([BUNDLE-5], [BUNDLE-6]).

> Note: requirements §4.8 and ADR 0008 §6 require every contribution to carry its value. This does not conflict with [EVAL-13], which lets one scope's own value appear only inside a combined `Resolved` result.

## 9.3 Contributions

A **contribution entry** is one element of `contributions`. A scope *contributes a value* in the sense of [MERGE-14] and [MERGE-15]. A **failure entry** is a contribution entry whose contribution label is `UNAVAILABLE` or `INVALID`. Every other contribution entry is a **value entry**.

**[PROV-19]** `contributions` MUST contain at most one entry for each scope.

**[PROV-20]** `contributions` MUST contain a value entry for each scope that contributes a value for the key ([MERGE-14]), including a scope whose value is the identity of the key's strategy ([MERGE-5]). A scope that contributes the deny value through a source failure under `deny` ([MERGE-15], [SVC-9]) has a failure entry instead ([PROV-39], [PROV-40]), not a value entry.

**[PROV-21]** A value entry whose value is the identity of the key's strategy MUST have the contribution label `IGNORED` ([PROV-38]).

**[PROV-22]** Every contribution entry MUST contain the members `scope`, `name` and `effect`.

**[PROV-23]** In every contribution entry, `scope` MUST be `level` or `axis`, and `name` MUST be the declared name of that scope.

**[PROV-24]** The `effect` member of every contribution entry MUST hold that entry's contribution label, as section 9.3.1 and section 9.3.2 define.

**[PROV-25]** A contribution entry MUST contain `node` if and only if its scope is not the root level ([SCOPE-13]).

**[PROV-26]** When a contribution entry contains `node`, its value MUST be the node ID that the context supplies for that scope ([CTX-1]).

**[PROV-27]** A contribution entry MUST contain `value` if and only if it is a value entry, or a failure entry under `deny`.

**[PROV-28]** The `value` of a value entry MUST be the value that the scope contributes, as validated ([SVC-31] for a provider value, and the bundle rules for a bundle value), and MUST NOT be a running or merged result. For `deep_merge` the value can therefore be a partial object ([MERGE-11]), subject to [MERGE-26] for an optional object field.

**[PROV-29]** The `value` of a failure entry under `deny` MUST be the deny value of the key's strategy ([SVC-9]).

**[PROV-30]** A failure entry under `skip` MUST NOT contain `value` ([SVC-8]).

**[PROV-31]** A contribution entry MUST NOT have `value` set to JSON `null`.

**[PROV-32]** A contribution entry MUST contain `onSourceFailure` if and only if it is a failure entry.

**[PROV-33]** The `onSourceFailure` of a failure entry MUST be the source-failure behaviour in effect for the key under [SVC-1], whether declared on the key or supplied by its kind ([KIND-9]). That behaviour is `skip` or `deny` ([SVC-3]).

> Note: when the key and its kind both declare `on_source_failure`, the two values are equal ([SVC-5]), so one behaviour is in effect.

**[PROV-34]** Contribution entries MUST be in evaluation-path order ([MERGE-13]): levels from the root level to the most specific level in declared order ([SCOPE-6]), then axes in declared order ([SCOPE-7]).

**[PROV-35]** A failure entry MUST take the position of its scope in that order.

**[PROV-36]** A failure entry MUST occur only at a non-root service-sourced level, because the root level is never service-sourced ([SCOPE-12]) and an axis is never service-sourced ([SCOPE-26]).

### 9.3.1 Contribution labels

A **contribution label** is the value of a contribution entry's `effect` member. It is distinct from the effect of a decision ([PROV-65], [PROV-67]). The labels of failure entries are given in section 9.3.2.

**[PROV-37]** The contribution label of a value entry MUST be determined by that entry's value alone and the key's strategy. For a commutative strategy, the value is compared with the identity of that strategy ([MERGE-5]). It MUST NOT depend on the other values, on the order in which values are combined, or on the running result.

**[PROV-38]** The contribution labels of value entries MUST follow this table:

| Strategy | Value that the scope contributes | Contribution label |
| --- | --- | --- |
| `all` | `false` | `VETO` |
| `all` | `true` | `IGNORED` |
| `any` | `true` | `WIDENED` |
| `any` | `false` | `IGNORED` |
| `intersect` | a strict subset of the universe | `NARROWED` |
| `intersect` | the full universe | `IGNORED` |
| `union` | a non-empty set | `WIDENED` |
| `union` | the empty set | `IGNORED` |
| `min` | any integer | `NARROWED` |
| `max` | any integer | `WIDENED` |
| `override`, `deep_merge` | any value | `SET` |

> Note: an entry with the label `SET` need not be the winning value. The `source` field identifies the winner ([PROV-45] to [PROV-52]), and every `SET` entry is still listed ([PROV-20]).

### 9.3.2 Failure entries

**[PROV-39]** If a provider throws, rejects or times out, each key being resolved that is settable at that level MUST have a failure entry at that level with the contribution label `UNAVAILABLE` ([SVC-17], [SVC-23]).

**[PROV-40]** If a provider returns a value that fails validation for a key, including `null`, that key MUST have a failure entry at that level with the contribution label `INVALID` ([SVC-31], [SVC-34]).

**[PROV-41]** A failure entry under `deny` MUST keep its failure label, `UNAVAILABLE` or `INVALID`, and MUST NOT take a value label. For example, a failure under `deny` for an `all` key carries `value` `false` and the label `UNAVAILABLE`, not `VETO`.

**[PROV-42]** `contributions` MUST NOT contain an entry for any scope other than those that [PROV-20], [PROV-39] and [PROV-40] require. This covers in particular a scope that the context omits ([CTX-21]), a skipped scope ([PROV-61]), a scope whose node has no value for the key, a key that a provider omits ([SVC-36]), a key that a provider returns but the bundle does not declare ([SVC-27]), and a provider entry for a key that is not settable at that level ([PROV-44]).

**[PROV-43]** A scope at which the key is not settable MUST NOT appear in `contributions`.

> Note: for a single-key `resolve` ([EVAL-7]), no `Resolved` result contains such an entry. The provider is called only when the key is settable at the level ([SVC-17]), and the result covers no other key. In a multi-key request ([SVC-20]), a provider can return such an entry for another requested key, and [PROV-44] then applies.

**[PROV-44]** A provider entry for a key that is not settable at the level that returns it MUST be ignored, as [SVC-29] and [SVC-30] define. It MUST NOT produce a contribution entry of any kind, and in particular MUST NOT produce a failure entry. This applies in a multi-key request as well.

## 9.4 Source of an order-dependent value

**[PROV-45]** `source` MUST be present if and only if the key's strategy is `override` or `deep_merge` ([MERGE-1]).

**[PROV-46]** When `source` is present, it MUST be either the JSON string `DEFAULT`, or an object `{ scope, name, node? }` that names the winning scope, whose `scope` is `level` or `axis`.

**[PROV-47]** The object form of `source` MUST contain `node` if and only if the winning scope is not the root level.

**[PROV-48]** `source` MUST be `DEFAULT` if and only if no level and no axis contributes a value for the key ([MERGE-14]). This includes the case where every entry in `contributions` is a failure entry under `skip`.

**[PROV-49]** An order-dependent key MUST NOT have a failure entry under `deny`, because `deny` applies only to narrowing strategies ([SVC-11], [MERGE-2]).

**[PROV-50]** For `override`, `source` MUST be determined from the hierarchy result H ([MERGE-19]), the axis result A ([MERGE-20]) and the default D, as the following table gives. The result of each row is defined by [AXIS-7]. This table applies to `override` only; `deep_merge` is handled by [PROV-53].

| H present | A present | `combine_across_axes` | Source |
| --- | --- | --- | --- |
| no | no | any | `DEFAULT` |
| yes | no | any | the most specific level that contributes a value |
| no | yes | any | the last declared axis that contributes a value |
| yes | yes | `axis_wins` | the last declared axis that contributes a value |
| yes | yes | `hierarchy_wins` | the most specific level that contributes a value |
| yes | yes | `max` or `min` | the scope whose value is the result ([AXIS-8]); on a tie, see [PROV-51] |

> Note: a key that is settable on no axis has no axis result ([AXIS-1], [AXIS-6], [AXIS-7]), so for such a key only the first two rows apply.

**[PROV-51]** On a tie between H and A under `max` or `min`, `source` MUST name the scope of H, which is the most specific level that contributes a value ([AXIS-8], [MERGE-19]).

**[PROV-52]** When `source` names a scope, that scope MUST have a value entry ([PROV-20]).

**[PROV-53]** For `deep_merge`, the fields of the result can come from several scopes, but `source` names one scope or `DEFAULT`. `source` MUST name the last present term of the fold of [AXIS-7], which uses the merge operation of [MERGE-22] and omits absent terms. For `axis_wins` the fold is `D ⊕ H ⊕ A`, and for `hierarchy_wins` it is `D ⊕ A ⊕ H`. `H` names the most specific level that contributes a value, `A` names the last declared axis that contributes a value, and `D` alone names `DEFAULT`. The role of `default` in the fold is settled by [MERGE-25].

## 9.5 Skipped scopes

**[PROV-54]** Each `skippedScopes` entry MUST be an object `{ scope, name, node, reason }`, where `scope` is `level` or `axis`, `name` is the declared scope name, and `node` is the unknown node ID that the context supplied.

**[PROV-55]** The `reason` of a `skippedScopes` entry MUST be `UNKNOWN_NODE`, the only reason in this version.

**[PROV-56]** `skippedScopes` MUST list exactly the scopes that admission skipped under `on_unknown_node: ignore` ([CTX-14], [CTX-16]). A scope that the context omits is not skipped and MUST NOT appear ([CTX-21]).

**[PROV-57]** The root level MUST NOT appear in `skippedScopes`, because a context cannot name it ([CTX-3]).

**[PROV-58]** `skippedScopes` MUST be ordered by declared level order, then by declared axis order ([SCOPE-6], [SCOPE-7]).

**[PROV-59]** `skippedScopes` MUST contain at most one entry for each scope.

**[PROV-60]** The `skippedScopes` of every result computed from one context MUST be the same. The list is a property of the context, not of the key or the feature, because admission validates the whole context before any lookup or provider call ([CTX-9], [CTX-10]).

**[PROV-61]** A skipped scope MUST NOT produce a contribution entry, a value, a contribution label, a failure entry or a `source` ([CTX-15], [CTX-16]).

## 9.6 Decision result

**[PROV-62]** A `Decision` result MUST contain the fields `feature`, `effect`, `bundleVersion`, `skippedScopes`, `binding`, `policy`, `reason` and `trace`.

**[PROV-63]** A `Decision` result MUST NOT contain any field other than those in [PROV-62], and in particular MUST NOT contain `contributions` or `source`. `decide` calls no provider ([SVC-22]), so no failure entry appears in a `Decision` result.

**[PROV-64]** `feature` MUST be the declared feature name.

**[PROV-65]** `effect` MUST be the effect that the decision returns: the winner's `then` ([RULE-48]), the policy `default` ([RULE-50]), or the feature's `if_fact_missing` ([RULE-43]).

**[PROV-66]** `effect` MUST be a value of the feature's `returns` form: a string from the `enum` list ([FEAT-7]), JSON `null` when the `enum` form is `nullable` ([FEAT-9]), or an object of the object form ([FEAT-11]).

**[PROV-67]** A `Decision` result's `effect` MUST be returned unchanged, even when it is a string equal to a contribution label such as `IGNORED` or `SET` ([FEAT-10]). Contribution labels have no meaning in a `Decision` result.

**[PROV-68]** `binding` MUST be an object whose `level` is the level of the selected binding ([BIND-17]).

**[PROV-69]** `binding` MUST contain `node` if and only if its `level` is not the root level ([BIND-2], [BIND-3]).

**[PROV-70]** When `binding` contains `node`, its value MUST be the node ID that the context supplies for that level ([CTX-1]).

**[PROV-71]** `policy` MUST be the name of the policy of the selected binding ([BIND-4], [BIND-21]).

**[PROV-72]** `binding` and `policy` MUST be present on every `Decision` result, including one with reason `MISSING_FACT`, because a binding is always selected ([BIND-13]) and the precheck runs on the selected policy ([RULE-40]).

**[PROV-73]** `reason` MUST be exactly one of the following objects, distinguished by its `type` member: `RULE`, `DEFAULT` or `MISSING_FACT`.

**[PROV-74]** A `reason` whose `type` is `RULE` MUST have the members `type`, `id` and `name` and no other member. Its `id` and `name` MUST be the `id` and `name` of the winning rule ([RULE-49], [POL-21]).

**[PROV-75]** A `reason` whose `type` is `DEFAULT` MUST be the object `{ type: DEFAULT }` with no other member, when no rule matches ([RULE-50], [RULE-51]).

**[PROV-76]** A `reason` whose `type` is `MISSING_FACT` MUST have the members `type` and `facts` and no other member, when the missing-fact precheck fails ([RULE-42], [RULE-44]).

**[PROV-77]** `facts` MUST be an array of fact names. Its contents and order are defined by [RULE-44].

**[PROV-78]** Each `trace` entry MUST be an object with the members `id` and `matched`, and MUST NOT have any member other than `id`, `matched` and `failed`.

**[PROV-79]** The `id` of each `trace` entry MUST be the `id` of the rule that it describes, a non-empty string ([POL-25]), and its `matched` MUST be a boolean ([RULE-53]).

**[PROV-80]** A `trace` entry MUST contain `failed` if and only if its `matched` is `false` ([RULE-53], [RULE-54]).

**[PROV-81]** When present, `failed` MUST be an object with exactly the members `fact`, a fact name, and `op`, a string ([RULE-54], [RULE-55]).

**[PROV-82]** The entries of `trace`, and their order, MUST be those that [RULE-52] defines under the `FIRST` hit policy ([RULE-46], [RULE-47]).

**[PROV-83]** For a `reason` whose `type` is `RULE`, the `id` of the last `trace` entry MUST equal `reason.id` ([RULE-47], [RULE-52]).

**[PROV-84]** For a `reason` whose `type` is `MISSING_FACT`, `trace` MUST be empty ([RULE-52]).

## 9.7 Serialization

Conformance vectors compare result fields as a subset match unless the vector sets `exact: true` (requirements §5.7), so vectors do not depend on member order.

**[PROV-85]** A serialized result, such as an HTTP response body, is an unordered JSON object: its member order MUST NOT be significant. Only the order of array elements is significant, and the clause that defines each array states that order ([PROV-34], [PROV-58], [PROV-77], [PROV-82] and [PROV-86]).

**[PROV-86]** The `value` of a set-valued result and of a set-valued contribution entry MUST list its members in the declaration order of `universe` ([TYPE-50], [TYPE-51]).

## Examples (non-normative)

**Example (non-normative)**: a `Resolved` result for `checkout.pickup.lead_hours` (kind `configuration`, strategy `override`, `combine_across_axes: max`). The division sets 6, and the seller axis sets 8. The axis value is larger, so the axis wins the `max` comparison. The `bundleVersion` is abbreviated.

```json
{
  "key": "checkout.pickup.lead_hours",
  "strategy": "override",
  "kind": "configuration",
  "value": 8,
  "bundleVersion": "sha256:<digest>",
  "skippedScopes": [],
  "contributions": [
    { "scope": "level", "name": "division", "node": "south", "value": 6, "effect": "SET" },
    { "scope": "axis", "name": "seller", "node": "acme", "value": 8, "effect": "SET" }
  ],
  "source": { "scope": "axis", "name": "seller", "node": "acme" }
}
```

**Example (non-normative)**: a `Resolved` result for `checkout.container_order.allowed` (strategy `all`) when the `account` provider times out. The key takes `deny` from its `capability` kind, and no other scope contributes a value. The failure entry carries the deny value `false` ([SVC-9]), so the result is `false`. No `source` appears, because `all` is commutative ([PROV-45]).

```json
{
  "key": "checkout.container_order.allowed",
  "strategy": "all",
  "kind": "capability",
  "value": false,
  "bundleVersion": "sha256:<digest>",
  "skippedScopes": [],
  "contributions": [
    { "scope": "level", "name": "account", "node": "A-1001", "value": false, "effect": "UNAVAILABLE", "onSourceFailure": "deny" }
  ]
}
```

**Example (non-normative)**: a `Decision` result for a mixed basket on credit hold in the `south` division. Neither rule matches, so the policy default applies. The trace lists both rules, and each entry for a rule that did not match names its first failing condition ([RULE-54]).

```json
{
  "feature": "checkout.order_submission",
  "effect": "DENY",
  "bundleVersion": "sha256:<digest>",
  "skippedScopes": [],
  "binding": { "level": "division", "node": "south" },
  "policy": "credit_check",
  "reason": { "type": "DEFAULT" },
  "trace": [
    { "id": "good-credit", "matched": false, "failed": { "fact": "customer.credit_hold", "op": "eq" } },
    { "id": "credit-hold-allowed-stock", "matched": false, "failed": { "fact": "item.stock_type", "op": "all_in" } }
  ]
}
```

## Open points
- (none)
