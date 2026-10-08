# samples/saas-entitlements

A docs sample: a single-file Tierwise project for plans and entitlements. It
uses the built-in preset `tierwise:entitlements` and shows the three things a
support team asks about: how a limit narrows, how a feature gets turned off, and
how a grant adds up. There are no features and no rules, so `decide` is not used.

The project is one file, [tierwise.yaml](tierwise.yaml). The schema and the values
are inline in the manifest (see [MANIFEST-13]). Tables in this tutorial summarise
rules that other clauses own. If a table and a clause differ, the clause governs.

## 1. The model

The levels run from least to most specific. The first level is the root ([SCOPE-10]),
and the root is on every evaluation path ([CTX-20]).

| Level | Root? | Nodes | Values come from |
| --- | --- | --- | --- |
| `plan` | yes | none (the root has no node ID) | the manifest |
| `org` | no | closed catalog: `acme`, `globex` (`on_unknown_node: error`) | the manifest |
| `workspace` | no | open (any valid ID) | the manifest |
| `user` | no | open | a provider at evaluation time (`values: service`) |

The `user` level is service-sourced. A source gives it no values, and a provider
returns its values when a result is computed ([SCOPE-47], [SVC-13]).

The sample has five keys. Each one opts into a kind that the preset defines
([KIND-2]), and each key name ends as the kind's naming pattern requires
([LINT-16]).

| Key | Kind | Type | Strategy | On source failure (from the kind) | Meaning |
| --- | --- | --- | --- | --- | --- |
| `usage.max_seats` | `limit` | integer, min 0 | `min` | `deny` | The lowest limit on the path wins. |
| `usage.max_projects` | `limit` | integer, min 0 | `min` | `deny` | The lowest limit on the path wins. |
| `sso.enabled` | `feature` | boolean | `all` | `deny` | Any level that sets `false` turns the feature off. |
| `audit_log.enabled` | `feature` | boolean | `all` | `deny` | On unless a level on the path sets `false`. |
| `export.granted_formats` | `grant` | set of `csv`, `pdf`, `xlsx` | `union` | `skip` | Every level on the path adds its formats. |

Every key is settable at every level. No key declares `on_source_failure`. Each
takes the behaviour of its kind ([KIND-9], [SVC-1]), so a key could not choose
another one ([KIND-11], [SVC-5]). The rules for `skip` and `deny` are in
[SVC-8] and [SVC-9]. The preset gives the narrowing kinds `limit` and `feature`
the behaviour `deny`, so they fail closed when the provider is down. It gives
`grant` the behaviour `skip`, because `deny` is allowed only on narrowing
strategies and `union` is not one ([SVC-11], [MERGE-2]). A `limit` key declares
`min: 0` in its type, because `deny` under `min` contributes that bound
([SVC-9], [SVC-12]).

### The authored values

```yaml
values:
  plan:                          # root: no node ID
    usage.max_seats: 25
    usage.max_projects: 20
    sso.enabled: false
    export.granted_formats: [csv]
  org:
    acme:
      usage.max_seats: 10
  workspace:
    ws-2:
      export.granted_formats: [pdf]
```

The plan sets a seat limit of 25 and a project limit of 20. It turns SSO off. It
grants CSV exports. The `acme` org narrows the seat limit to 10. Workspace `ws-2`
adds PDF exports.

A plan grants a capability in two ways here. A `union` key is granted by writing
the members to grant, as `export.granted_formats` shows. A feature under `all` is
on unless some level turns it off, so a plan grants it by writing no value. This is
why the plan does not mention `audit_log.enabled`: writing `true` would be the
identity of `all`. Such a value changes nothing, it is labelled `IGNORED`
([MERGE-5], [PROV-21]), and the preset's `values/no-op` rule reports it as an
error ([LINT-23]). A plan that offers SSO simply omits `sso.enabled`.

## 2. The example context

We resolve each key in one context. The context names one node for each non-root
level. It never names the root ([CTX-3]), and a node that a level omits is not
an error ([CTX-21]).

```json
{ "levels": { "org": "acme", "workspace": "ws-2", "user": "u-1001" } }
```

The provider for `user` is caller-supplied code. In this example it returns two
entries. A key it does not mention gets no entry, and that is not a failure
([SVC-36]):

```json
{ "usage.max_seats": 12, "sso.enabled": true }
```

Each `resolve(context, key)` call is one evaluation call ([EVAL-7], [SVC-19]).
Its provider call happens once, and the result lists the contributions in
evaluation-path order: `plan`, `org`, `workspace`, `user` ([PROV-34]). A
contribution lists its value and its label. The label, the `effect` member, says
what the scope did to the result. It is not a decision effect ([PROV-24]).

| Label | Meaning for these keys |
| --- | --- |
| `NARROWED` | A `min` value. It can lower the result. |
| `WIDENED` | A non-empty `union` set. It adds to the result. |
| `VETO` | An `all` value of `false`. It turns the result off. |
| `IGNORED` | A value equal to the identity of the strategy (`true` under `all`). It cannot change the result. |
| `UNAVAILABLE` | A failure entry, from a provider that threw or timed out ([PROV-39]). |

This table is a summary of [PROV-38] and [PROV-39]. Those clauses govern.

### 2.1 `usage.max_seats`

The contributions are the plan (25), the org (10) and the user provider (12).
The workspace has no value for this key, so it has no entry ([PROV-42]).

> Example (non-normative): the expected `Resolved` result

```json
{
  "key": "usage.max_seats",
  "strategy": "min",
  "kind": "limit",
  "value": 10,
  "bundleVersion": "sha256:<digest>",
  "skippedScopes": [],
  "contributions": [
    { "scope": "level", "name": "plan", "value": 25, "effect": "NARROWED" },
    { "scope": "level", "name": "org", "node": "acme", "value": 10, "effect": "NARROWED" },
    { "scope": "level", "name": "user", "node": "u-1001", "value": 12, "effect": "NARROWED" }
  ]
}
```

`min(25, 10, 12)` is 10 ([MERGE-17]). The result has no `source`, because `min`
is commutative ([PROV-45]).

### 2.2 `usage.max_projects`

Only the plan sets this key. The provider returns no entry for it, so the user
level contributes nothing ([SVC-36], [PROV-42]).

```json
{
  "key": "usage.max_projects",
  "strategy": "min",
  "kind": "limit",
  "value": 20,
  "bundleVersion": "sha256:<digest>",
  "skippedScopes": [],
  "contributions": [
    { "scope": "level", "name": "plan", "value": 20, "effect": "NARROWED" }
  ]
}
```

### 2.3 `sso.enabled`

The plan sets `false`, which is a `VETO`. The user provider returns `true`, which
is the identity of `all`. It is labelled `IGNORED`, so it cannot widen the
result ([SVC-33], [PROV-21]).

```json
{
  "key": "sso.enabled",
  "strategy": "all",
  "kind": "feature",
  "value": false,
  "bundleVersion": "sha256:<digest>",
  "skippedScopes": [],
  "contributions": [
    { "scope": "level", "name": "plan", "value": false, "effect": "VETO" },
    { "scope": "level", "name": "user", "node": "u-1001", "value": true, "effect": "IGNORED" }
  ]
}
```

A lower level cannot turn a feature back on after a higher level has turned it
off. That is what `all` means ([MERGE-17]).

### 2.4 `audit_log.enabled`

No authored value sets this key, and the provider returns no entry for it. So no
scope contributes a value. The result is the identity of `all`, which is `true`
([MERGE-5]). The contributions list is empty ([PROV-7]).

```json
{
  "key": "audit_log.enabled",
  "strategy": "all",
  "kind": "feature",
  "value": true,
  "bundleVersion": "sha256:<digest>",
  "skippedScopes": [],
  "contributions": []
}
```

### 2.5 `export.granted_formats`

The plan grants `csv` and workspace `ws-2` adds `pdf`. Both contributions are
non-empty sets, so both are `WIDENED`. The union is `csv` and `pdf`, in the
declaration order of the universe (`csv`, `pdf`, `xlsx`) ([MERGE-17], [PROV-86]).

```json
{
  "key": "export.granted_formats",
  "strategy": "union",
  "kind": "grant",
  "value": ["csv", "pdf"],
  "bundleVersion": "sha256:<digest>",
  "skippedScopes": [],
  "contributions": [
    { "scope": "level", "name": "plan", "value": ["csv"], "effect": "WIDENED" },
    { "scope": "level", "name": "workspace", "node": "ws-2", "value": ["pdf"], "effect": "WIDENED" }
  ]
}
```

## 3. When the user provider is down

Suppose the provider for `user` throws or times out. The provider is called for
each key here, because every key is settable at `user` ([SVC-17]). Each key
being resolved gets a failure entry at `user` with the label `UNAVAILABLE`
([SVC-23], [PROV-39]). The failure entry carries the `onSourceFailure` of the key
([PROV-32], [PROV-33]). Under `skip` the entry has no `value` ([PROV-30]). Under
`deny` it has the deny value of the strategy: `false` for `all`, and the `min`
bound of the type for `min` ([SVC-9], [PROV-29]). The label stays `UNAVAILABLE`,
and does not become `VETO` or `NARROWED` ([PROV-41]). A failure entry is a
contribution entry in its own right, listed at its scope's position
([PROV-35]).

> Example (non-normative): the same context, with the user provider failing

| Key | Result | User entry in `contributions` | Why |
| --- | --- | --- | --- |
| `usage.max_seats` | `0` | `{ scope: level, name: user, node: u-1001, value: 0, effect: UNAVAILABLE, onSourceFailure: deny }` | The deny value of `min` is the type's bound, 0. `min(25, 10, 0)` is 0. |
| `usage.max_projects` | `0` | the same entry | `min(20, 0)` is 0. |
| `sso.enabled` | `false` | `{ ..., value: false, effect: UNAVAILABLE, onSourceFailure: deny }` | The deny value of `all` is `false`. The plan `VETO` gives the same answer. |
| `audit_log.enabled` | `false` | `{ ..., value: false, effect: UNAVAILABLE, onSourceFailure: deny }` | Audit logging turns off while the provider is down. |
| `export.granted_formats` | `["csv", "pdf"]` | `{ scope: level, name: user, node: u-1001, effect: UNAVAILABLE, onSourceFailure: skip }` | The entry has no `value` and contributes nothing. The two grants still apply. |

The expected `Resolved` result for `usage.max_seats`:

```json
{
  "key": "usage.max_seats",
  "strategy": "min",
  "kind": "limit",
  "value": 0,
  "bundleVersion": "sha256:<digest>",
  "skippedScopes": [],
  "contributions": [
    { "scope": "level", "name": "plan", "value": 25, "effect": "NARROWED" },
    { "scope": "level", "name": "org", "node": "acme", "value": 10, "effect": "NARROWED" },
    { "scope": "level", "name": "user", "node": "u-1001", "value": 0, "effect": "UNAVAILABLE", "onSourceFailure": "deny" }
  ]
}
```

The `usage.max_seats` and `audit_log.enabled` rows are the cases to explain to a
support team. A provider outage sets the seat limit to 0 and turns audit logging
off, and the `UNAVAILABLE` label in the provenance shows why. That is what
fail-closed means for a narrowing kind. Only the narrowing strategies may use
`deny` ([SVC-11]). A result that failed for this reason does not raise an error:
a source failure is reported in `contributions` ([PROV-8]).

A key cannot choose `skip` for itself, because it cannot override its kind
([SVC-5]). A project that wants limits to survive an outage would have to
define its own `limit` kind with `on_source_failure: skip`, which replaces the
preset's kind ([MANIFEST-32]).

## 4. A typo fails

The org catalog is closed, and `on_unknown_node` is `error`. If the context names
`org: "acem"`, resolution raises `E_UNKNOWN_NODE` and returns no result
([CTX-12], [EVAL-7]). It does not fall back to another org, and it does not skip
the scope ([CTX-23]). The same typo in the `values` of the manifest is reported
as `E_UNKNOWN_NODE` when the project is compiled ([SCOPE-43]). A workspace or
user node is open, so any valid ID is admitted there ([CTX-13]).

## 5. Checking the sample

A validation of the project should report zero findings ([FIND-3], [FIND-5]). The
checks that matter are these.

- The engine checks pass. Each key declares `type`, `combine` and `settable_at`,
  its strategy is one that its kind lists ([KIND-4]), and its type is one that the
  strategy allows ([MERGE-3]). Every key settable at the `user` level has a
  source-failure behaviour through its kind ([SVC-1]), and the `limit` keys have
  the `min` bound that `deny` needs ([SVC-12]).
- The lint rules of the preset run only when there is no engine finding
  ([LINT-11]). The preset sets `naming/*` to `error`, so the naming patterns
  `key`, `limit_key`, `feature_key` and `grant_key` apply to the key names
  ([LINT-12], [LINT-16]). Every key name matches the general `key` pattern and the
  pattern of its kind. The `node_id` pattern applies to the closed `org` catalog
  (`acme`, `globex`) and not to the open `workspace` and `user` scopes
  ([LINT-19]).
- `values/no-op` is `error` in the preset. No authored value equals the identity of
  its strategy ([LINT-23]). `sso.enabled: false` is a veto, the grants are
  non-empty sets, and a `min` key has no identity.
- `schema/unused` is `warn` in the preset. Every key is set by a value or is
  settable at the service-sourced level `user` ([LINT-38]).
- `schema/description-required` is `warn` in the preset. Every key has a
  description ([LINT-44]).
- The other lint rules of the preset have nothing to flag. The project has no
  rules and no features, no `namespaces`, and no kind or naming pattern that
  replaces one of the preset ([LINT-26], [LINT-29], [LINT-46], [LINT-49]).
