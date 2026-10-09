# Modeling guide

This guide explains how to describe your own configuration in Tierwise: the levels your values live at, the keys
and their merge rules, the kinds and presets that constrain them, and the decisions that combine facts into an
effect. It uses two sample projects from the repository:

- `samples/commerce` (order-to-cash): six levels, an axis, service-sourced levels, five keys and two decisions.
- `samples/saas-entitlements` (plans and limits): one file, four levels, five keys and no decisions.

The paths are in the repository, next to this documentation. Every name in this guide (`division`,
`checkout.pickup.lead_hours`, `usage.max_seats`, …) comes from those samples. Tierwise has no built-in level, key or
decision names. Every example is labelled **Example (non-normative)**. The rules themselves are in the
specification (`spec/spec.md`); this guide says what to do, and the spec says what an implementation must do.

To follow along, compile a sample and ask questions of it with the evaluator, as in the
[Java](../quickstart-java.md) or [JavaScript](../quickstart-js.md) quickstart. Every result in this guide was
produced that way, from the bundles of the samples as they are in the repository.

## 1. The shape of a project

A project is a directory with a manifest, `tierwise.yaml`, and the files the manifest selects:

| Part | Holds | Where it is selected |
| --- | --- | --- |
| Manifest | The spec version, the presets to extend, the source lists, namespaces and lint settings | `tierwise.yaml` |
| Schema | The hierarchy, entities, facts, keys, features and, optionally, kinds and naming patterns | `sources.schema` (a list of file patterns) |
| Values | The values of keys, for each level and node | `sources.values` |
| Rules | The policies, bindings and examples of one feature, one file per feature | `sources.rules` |

A small project can put the schema and the values inline in the manifest, as `tierwise init` does and as
`samples/saas-entitlements` does. A larger one splits them by the layout of its choice. The manifest says where
things are; there is no required directory layout.

**Example (non-normative):** the manifest of `samples/commerce`.

```yaml
specVersion: "0.1"
extends:
  - tierwise:recommended
sources:
  schema:
    - "schema/**/*.yaml"
  values:
    - file: "values/global.yaml"
      scope: global
    - pattern: "values/{scope}/{node}.yaml"
  rules:
    - "rules/**/*.yaml"
namespaces:
  checkout: { schema: "schema/components/checkout.yaml" }
```

The `{scope}` and `{node}` in the values pattern mean that the file `values/division/south.yaml` holds the
values of node `south` of the scope `division`. The root level has no node, so its values have a file of their
own (`values/global.yaml`). The other value-file shapes are a multi-node file (`<scope>: { <node>: { key: value } }`,
used by `samples/retail-pos`) and inline `values` in the manifest.

Only the manifest declares `specVersion`. The compiler checks every other file against the schemas under `spec/`,
resolves every name, and writes the bundle. Nothing is guessed: a name that does not resolve is an error with a
code and a line, and the compile stops.

## 2. Levels, nodes and the context

A **level** is one step of your hierarchy, from least to most specific. You declare them in order:

**Example (non-normative):** the levels of the commerce sample.

```yaml
decision_hierarchy:
  levels: [global, channel, division, site, account, user]
  axes: [seller]
  scopes:
    channel:  { nodes: [web, partner_portal], on_unknown_node: error }
    division: { nodes: [north, south, west], on_unknown_node: error }
    site:     { nodes: { from: "catalogs/sites.json" }, on_unknown_node: ignore }
    account:  { nodes: open, values: service }
    user:     { nodes: open, values: service }
    seller:   { nodes: [acme], on_unknown_node: error }
```

- **The root level** is the first entry (`global`). It has one implicit node, no catalog and no node ID. It is
  always in effect, so a context never names it. Values for the root level are written without a node.
- **Every other level and every axis** has an entry in `scopes` that says which nodes exist. The nodes are an
  inline list, a generated list (`{ from: <file> }`, a JSON file with a `nodes` array that the compiler embeds in the
  bundle) or `open` (any structurally valid ID).
- **`on_unknown_node`** decides what happens when a context names a node that a closed catalog does not list.
  `error` raises `E_UNKNOWN_NODE`. `ignore` skips that scope for this evaluation: no value, no binding and no
  provider call from it, and the scope appears in `skippedScopes` of the result. Other scopes still count. It is
  required for closed catalogs and forbidden for `open`.
- **`values: service`** marks a level whose values come from code at evaluation time, not from files. See
  [section 7](#7-levels-whose-values-come-from-a-service).

A **context** names at most one node for each non-root level and each axis. It is the path from the root to the
thing you are asking about:

```json
{ "levels": { "division": "south", "site": "site_001" }, "axes": { "seller": "acme" } }
```

Tierwise never infers a parent from a child. If `site_001` belongs to `south`, the caller says both, or only the
one that matters. A level that the context leaves out is simply not on the path; that is not an error.

**Example (non-normative):** an ignored node is recorded, not hidden. Site `site_099` is not in the site catalog,
and `site` has `on_unknown_node: ignore`.

```json
{
  "key": "checkout.order.allowed_max_lines",
  "strategy": "min",
  "value": 100,
  "skippedScopes": [{ "scope": "level", "name": "site", "node": "site_099", "reason": "UNKNOWN_NODE" }],
  "contributions": [
    { "scope": "level", "name": "global", "effect": "NARROWED", "value": 500 },
    { "scope": "level", "name": "division", "effect": "NARROWED", "node": "south", "value": 100 }
  ]
}
```

(`bundleVersion` and `kind` are left out.) Choose `error` when an unlisted node is a bug in the caller, and
`ignore` when new nodes appear faster than the catalog is updated and falling back to the other scopes is
acceptable. The compiler is strict under both: a value or binding in a *source* file that names an unlisted node is
always `E_UNKNOWN_NODE`.

Node IDs are non-empty, at most 128 code points and contain no control characters. They are compared exactly, by
code point; the compiler normalises sources to NFC, and evaluators do not normalise anything.

## 3. Axes

An **axis** is a scope that cuts across the levels. A seller can appear under many divisions, so a seller is not
"below" or "above" a division. Axes have no order of specificity among themselves; they are listed in the order you
declare them.

A key opts into an axis by listing it in `settable_at`. For a commutative strategy (section 5) the axis values are
just more inputs. For an order-dependent strategy (`override`, `deep_merge`), "the most specific value wins" does
not say whether the seller beats the division, so the key must declare `combine_across_axes`:

| `combine_across_axes` | The result is |
| --- | --- |
| `hierarchy_wins` | the hierarchy result, if there is one, otherwise the axis result |
| `axis_wins` | the axis result, if there is one, otherwise the hierarchy result |
| `max`, `min` | the larger or the smaller of the two (integer keys only) |

If only one of the two results exists, it is used. A key that is not settable on an axis must not declare
`combine_across_axes`.

**Example (non-normative):** `checkout.pickup.lead_hours` is an `override` key settable at `global`, `division`, `site`
and the axis `seller`, with `combine_across_axes: max`. The root says 2, division `south` says 6 and seller `acme`
says 8.

| Context | Result | Why |
| --- | --- | --- |
| `{}` | 2 | The root value, because nothing more specific sets one |
| `levels: division=south` | 6 | The hierarchy result only |
| `levels: division=south`, `axes: seller=acme` | 8 | `max(6, 8)`; the result's `source` names the seller |

## 4. Keys and types

A **key** is a declared setting. It has a name, a type, a merge strategy (`combine`) and the scopes where it may be
set (`settable_at`). A value written at a scope that is not listed is `E_NOT_SETTABLE_HERE`.

**Example (non-normative):** one key of the commerce sample, in full.

```yaml
checkout.order.allowed_max_lines:
  kind: capability
  type: integer
  min: 1
  combine: min
  settable_at: [global, division, site]
  description: "The largest number of lines an order may have. The smallest bound wins."
```

The types, and what each one takes:

| `type` | Extra members |
| --- | --- |
| `boolean` | none |
| `integer` | `min`, `max`, within ±(2^53 − 1) |
| `decimal` | none. Values are canonical decimal strings, and only equality and `in` apply to them |
| `string` | `format: time_hhmm` or `iana_tz` (optional) |
| `enum` | `values` |
| `set` | `universe`: the allowed members, in the order results use |
| `list` | `items` |
| `object` | `fields`; each field has a type and may be `optional: true`. Unknown fields are rejected |

There is no `null` value, no coercion (the string `"true"` is not a boolean) and no float. A number that is not an
integer is not representable; use a `decimal` string such as `"1.50"`.

## 5. Merge strategies

The strategy of a key says how the values from the scopes on the path become one result. There are eight, and they
fall into two classes.

| Strategy | Types | Result | When nothing contributes | Narrowing |
| --- | --- | --- | --- | --- |
| `all` | boolean | AND of the values | `true` | yes |
| `any` | boolean | OR | `false` | no |
| `intersect` | set | the universe intersected with every value | the full universe | yes |
| `union` | set | the union of the values | `[]` | no |
| `min` | integer | the smallest | absent | yes |
| `max` | integer | the largest | absent | no |
| `override` | any | the most specific value | the key's `default` | no |
| `deep_merge` | object | fields merged recursively, the most specific field wins; lists are replaced | the key's `default` | no |

**Commutative** strategies (the first six) do not depend on the order of the scopes. Several levels that all say
something produce one answer however you order them. A commutative key may not declare a `default`
(`E_DEFAULT_NOT_ALLOWED`): the identity in the table is its default. **Order-dependent** strategies (`override`,
`deep_merge`) need a `default` or a value at the root level (`E_MISSING_DEFAULT`). The default is the lowest
fallback: it applies only where no level and no axis sets a value. For `deep_merge` the starting value must be a
complete object, and other scopes may give partial objects.

**Narrowing** strategies are the ones where a lower level can only make the answer more restrictive: `all`
(one `false` vetoes), `intersect` (a set can only shrink) and `min` (the smallest bound wins). Use a narrowing
strategy for anything that must not be re-enabled further down: a capability, a limit, a veto. Use `union` or `any`
when each level adds something. Use `override` for a plain setting where the most specific level should simply win.

**Example (non-normative):** the commerce sample's keys, one per strategy, and what they resolve to. Each row is one
`resolve` call.

| Key and strategy | Context | Result | Why |
| --- | --- | --- | --- |
| `checkout.shipping.allowed_methods`, `intersect` | `{}` | `[GROUND, PICKUP]` | The root value alone |
| `checkout.shipping.allowed_methods` | `channel=web`, `division=south` | `[GROUND]` | `{GROUND, PICKUP}` ∩ `{GROUND, NEXT_DAY}` ∩ `{GROUND, PICKUP}` |
| `checkout.container_order.allowed`, `all` | `division=north` | `false` | Division `north` sets `false`, a veto |
| `checkout.container_order.allowed` | `division=south` | `true` | No scope contributes, so the identity of `all` |
| `checkout.order.allowed_max_lines`, `min` | `division=south`, `site=site_001` | `25` | `min(500, 100, 25)` |
| `checkout.pickup.window`, `deep_merge` | `{}` | `{ open: "09:00", close: "18:00" }` | The default merged with the root's partial object |
| `checkout.pickup.window` | `division=north`, `site=site_001` | `{ open: "09:00", close: "16:00" }` | Root, `north` and `site_001` merged field by field, in path order |

An absent result exists only for `min` and `max` when no scope contributes. The value is then absent (`null` in
a result, `hasValue()` false in Java). It is never silently turned into a number.

### Every result explains itself

The value is one part of the answer. A resolved result lists the contribution of each scope that set a value, in the
order of the path, and, for an order-dependent key, the `source` that won.

**Example (non-normative):** resolving `checkout.pickup.lead_hours` with `division=south` and `seller=acme`.

```json
{
  "key": "checkout.pickup.lead_hours",
  "strategy": "override",
  "kind": "configuration",
  "value": 8,
  "skippedScopes": [],
  "contributions": [
    { "scope": "level", "name": "global", "effect": "SET", "value": 2 },
    { "scope": "level", "name": "division", "effect": "SET", "node": "south", "value": 6 },
    { "scope": "axis", "name": "seller", "effect": "SET", "node": "acme", "value": 8 }
  ],
  "source": { "scope": "axis", "name": "seller", "node": "acme" }
}
```

(`bundleVersion` is left out.) The `effect` of a contribution describes the value alone, against the identity of
its strategy; it never depends on the order of combination:

| Effect | Meaning |
| --- | --- |
| `SET` | An `override` or `deep_merge` key got a value from this scope |
| `VETO` | An `all` key got `false` |
| `NARROWED` | An `intersect` or `min` value |
| `WIDENED` | An `any`, `union` or `max` value |
| `IGNORED` | The value equals the identity of the strategy, so it can change nothing |
| `UNAVAILABLE`, `INVALID` | A provider failed, or gave a value that failed validation. See [section 7](#7-levels-whose-values-come-from-a-service) |

A support team can answer "why is this customer's limit 10?" from this list alone, without reading any YAML.

## 6. Kinds, presets and naming

The engine has no idea what a "limit" or a "capability" is. A **kind** is a named set of constraints that a team
defines once, and that keys opt into with `kind:`. A kind can declare:

| Member | Effect on the keys of the kind |
| --- | --- |
| `strategies` | The strategies a key of this kind may use |
| `narrowing_only` | Strategies must be narrowing, and an authored value may not equal the identity of its strategy (`true` under `all`, the full universe under `intersect`) |
| `require_default` | Every key of this kind needs a `default` |
| `on_source_failure` | The source-failure behaviour that keys settable at a service-sourced level inherit |
| `naming` | The name of a naming pattern that the key names of this kind must match |

The engine enforces exactly what a kind declares and nothing else. A key that breaks its kind is `E_KIND_VIOLATION`.

**Example (non-normative):** the two kinds that `samples/retail-pos` declares in its own schema, without a preset.

```yaml
kinds:
  guard:
    strategies: [all, intersect, min]
    narrowing_only: true
    on_source_failure: deny
    naming: guard_key
  setting:
    strategies: [override, deep_merge]
    require_default: true
    on_source_failure: skip
```

A **preset** is a reusable file of kinds, naming patterns and lint severities. A project names the presets it uses
in `extends`; later presets and then the project's own definitions replace earlier ones. A preset can tighten
checks. It can never turn off an engine guarantee: a lint setting that names an engine error code is `E_PRESET`.

| Preset | Use it when | Kinds |
| --- | --- | --- |
| `tierwise:minimal` | You are trying Tierwise out | none; lint only for likely bugs |
| `tierwise:recommended` | Most teams | `configuration` (`override` or `deep_merge`, with a default) and `capability` (`all`, `intersect` or `min`, narrowing only, fails closed) |
| `tierwise:entitlements` | Plans, limits and grants | `limit` (`min`), `feature` (`all`) and `grant` (`union`) |

Start from `tierwise:minimal` to see how the sources and findings look, and move to `tierwise:recommended` when you
want your names and settings checked. A preset can also be a path or an `https` URL pinned to a SHA-256. The
contents of each built-in preset are listed in `presets/README.md`.

A **naming pattern** is a regular expression that the names of one category (keys, enum values, node IDs, features)
must match, and it is enforced by the lint rule `naming/<pattern>`. A project that declares no patterns gets no
naming checks. The one structural rule that is always on: a fact is named `<entity>.<attribute>` for a declared
entity.

### Lint

Lint rules are guidance whose severity (`error`, `warn`, `info`, `off`) the presets and the manifest set. They run
only when the project has no engine finding.

| Rule | Flags |
| --- | --- |
| `naming/*` | Names that do not match the declared patterns |
| `values/no-op` | A value equal to the identity of its strategy |
| `rules/dead-rule` | A rule that can never match |
| `examples/required-per-binding` | A binding with no example |
| `examples/mixed-collection` | A policy with `all_in` or `any_in` and no example that has a mixed collection |
| `schema/unused` | Keys, facts, settings or policies that nothing uses |
| `schema/description-required` | Keys, features or facts without a description |
| `schema/owner-required` | Keys without an owner, when the project uses namespaces |
| `presets/override` | A preset or the project replaces an earlier kind or naming pattern |

`tierwise explain <rule>` prints the rationale and the fix for each rule and each error code. To change a severity,
set it in the manifest:

```yaml
lint:
  rules:
    schema/description-required: "off"
```

Tierwise reads YAML 1.2 only, with the core schema, and rejects aliases, anchors, tags and duplicate keys. Under
that schema `off` and `no` are plain strings, not booleans, so a node ID such as `no` stays the string `"no"`. The
built-in presets write `"off"` with quotes; both spellings mean the same.

### Namespaces

With `namespaces` in the manifest, each key and feature belongs to a namespace, and a namespaced file may declare
only keys and features whose names start with its own namespace. The hierarchy, entities, facts and kinds stay in
shared files. This is how a platform team owns the shape while component teams own their keys: a team cannot change
the hierarchy by editing its own file.

## 7. Levels whose values come from a service

A level declared `values: service` has no values in files. Its values come from a **provider**: code that you
register for the level, which receives a node ID and returns a map from key name to value. Use it for values that
already live in a service, such as the limits of an account.

The rules that matter for modeling:

- A key settable at a service-sourced level must declare what happens when the provider fails, directly or through
  its kind: `on_source_failure: skip` or `deny`. There is no default (`E_MISSING_SOURCE_FAILURE`).
- `skip`: the level contributes nothing.
- `deny`: the level contributes the most restrictive value: `false` for `all`, `[]` for `intersect`, the declared
  `min` bound for a `min` key (which is therefore required: `E_DENY_BOUND_REQUIRED`). `deny` is allowed only on
  narrowing strategies (`E_DENY_NOT_NARROWING`).
- A provider is called at most once per scope per evaluation call, and only when a key being resolved is settable
  there and the context names a node for the level.
- A provider that throws or times out makes every key being resolved at that level `UNAVAILABLE`. A value that
  fails type or range validation makes only that key `INVALID`. A key the provider leaves out sets nothing; that is
  not a failure. A key the bundle does not know is ignored and reported to the listener.
- A failure is not an error: it is recorded in the contributions, and the result is still returned.
- `decide` never calls a provider, because bindings cannot be placed at a service-sourced level.

**Example (non-normative):** the `saas-entitlements` sample has the levels `plan → org → workspace → user`, and
`user` is service-sourced. The `limit` and `feature` kinds of `tierwise:entitlements` fail closed (`deny`), and
`grant` skips, because `union` is not narrowing. With org `acme`, workspace `ws-2` and user `u-1001`, once with a
healthy provider that answers `usage.max_seats: 12` and `sso.enabled: true`, and once with a provider that throws:

| Key | Healthy provider | Provider down | Why, when it is down |
| --- | --- | --- | --- |
| `usage.max_seats` (`min`) | 10 | 0 | The deny value of `min` is the key's declared bound, 0 |
| `usage.max_projects` (`min`) | 20 | 0 | The same |
| `sso.enabled` (`all`) | `false` | `false` | The plan already vetoes it |
| `audit_log.enabled` (`all`) | `true` | `false` | The deny value of `all` is `false`: audit logging turns off |
| `export.granted_formats` (`union`) | `["csv", "pdf"]` | `["csv", "pdf"]` | `skip`: the user level adds nothing; the grants still apply |

The contribution of the failed level shows why:

```json
{ "scope": "level", "name": "user", "effect": "UNAVAILABLE", "node": "u-1001", "value": 0, "onSourceFailure": "deny" }
```

Fail-closed means a provider outage lowers the seat limit to 0 and turns audit logging off; the `UNAVAILABLE` label
tells a support team that it is the outage and not the configuration. A key cannot choose a different behaviour than
its kind gives it. A project that wants limits to survive an outage defines its own `limit` kind with
`on_source_failure: skip`, which replaces the preset's kind.

## 8. Decisions

Keys answer "what is the value here?". A **decision** answers "what should happen to this thing?", from facts about
the thing, under rules that can differ by level. A decision is made for one entity (an order, an invoice, a basket)
and returns an effect from a set you declare.

### Entities and facts

Entities and their relations are declared once:

**Example (non-normative):**

```yaml
entity_hierarchy:
  customer: {}
  invoice:  { has_many: order }
  order:    { belongs_to: customer, has_many: item }
  item:     {}

facts:
  customer.active:      { type: boolean }
  customer.credit_hold: { type: boolean }
  item.stock_type:      { type: enum, values: [STOCKED, SPECIAL_ORDER, DROP_SHIP] }
```

`belongs_to` names a to-one parent (and, transitively, its ancestors), `has_many` names a child collection. Cycles are
`E_ENTITY_GRAPH`. A **fact** is `<entity>.<attribute>` with a type of `boolean`, `string`, `integer`, `decimal` or
`enum`. When a decision is made for an order:

- A fact of the order or of one of its ancestors (`customer.active`) is a **single-value fact**: one value.
- A fact of a child (`item.stock_type`) is a **child fact**: one value per child, so a collection.

The caller supplies them as a **FactSet**: single-value facts under `facts`, and one map for each child under
`children`.

```json
{
  "facts": { "customer.active": true, "customer.credit_hold": true },
  "children": { "item": [{ "item.stock_type": "STOCKED" }, { "item.stock_type": "DROP_SHIP" }] }
}
```

A fact that is absent, or `null`, is **missing**. An omitted child collection makes its facts missing; an empty
list is present and empty. Facts that are not declared, not in scope or of the wrong type are `E_FACTS_INVALID`.

### Features, policies and bindings

A **feature** is a declared decision:

```yaml
checkout.order_submission:
  decided_per: order
  returns: { enum: [ALLOW, DENY] }
  hit_policy: FIRST
  if_fact_missing: DENY
  binding_settable_at: [global, division, site]
```

`returns` is an enum (optionally nullable) or an object. `hit_policy` is required, and `FIRST` is the only policy
version 0.1 defines. `if_fact_missing` is the effect when facts are missing. `binding_settable_at` lists the levels
where a policy can be chosen; it must include the root level, and no service-sourced level.

The rules live in `rules/<feature>.yaml` as named **policies**, each with an ordered list of rules, a `default`
effect and optional **settings**: named parameters whose values a binding can change.

```yaml
policies:
  credit_check:
    settings:
      allowed_on_credit_hold: { list_of: item.stock_type, default: [] }
    default: DENY
    rules:
      - id: good-credit
        name: Good credit
        when:
          customer.active: true
          customer.credit_hold: false
        then: ALLOW
      - id: credit-hold-allowed-stock
        name: On credit hold, every item is an allowed stock type
        when:
          customer.active: true
          customer.credit_hold: true
          item.stock_type: { all_in: $allowed_on_credit_hold }
        then: ALLOW
```

A **binding** selects a policy, and values for its settings, at the root level or at a node of a level:

```yaml
bindings:
  global: { policy: credit_check }
  division:
    north: { policy: allow_all }
    south: { policy: credit_check, allowed_on_credit_hold: [STOCKED, SPECIAL_ORDER] }
```

The most specific binding on the context path replaces all others entirely. Rules and settings are never merged
across bindings. Every feature has a root binding, so every decision has a binding. A setting that a binding does not
give takes the setting's `default`.

### Quantifiers

When a rule tests a child fact, you must say whether it means *every* child or *any* child:

| Condition | Applies to | Holds when | Empty collection |
| --- | --- | --- | --- |
| `fact: value` or `fact: $setting` | single-value fact | equal | not applicable |
| `fact: { in: [...] }` or `{ in: $setting }` | single-value fact | one of the list | not applicable |
| `fact: { all_in: [...] }` or `{ all_in: $setting }` | child fact | every child's value is in the list | **false** |
| `fact: { any_in: [...] }` or `{ any_in: $setting }` | child fact | some child's value is in the list | false |

A child fact tested without a quantifier is `E_QUANTIFIER_REQUIRED`, and a single-value fact with one is
`E_QUANTIFIER_NOT_ALLOWED`. This is the check that matters most in the project. "Item stock type is STOCKED" on an
order does not say whether it means *any* item or *every* item, and a mixed basket gets opposite answers under the
two readings. Tierwise makes the author choose, at build time.

**Example (non-normative):** testing a child fact with a plain `in`.

```text
rules/checkout.order_submission.yaml:29: error E_QUANTIFIER_REQUIRED a child fact needs all_in or any_in (RULE-13)
validate: 1 error, 0 warnings, 0 infos
```

Only facts one level down are supported in version 0.1; a deeper reference is `E_DEPTH_UNSUPPORTED`. An empty
collection makes both `all_in` and `any_in` false, so an order with no items does not pass an `all_in` test; it falls
through to the policy default.

### How a decision is made

1. The context is validated, then the selected binding is found: the most specific binding on the path.
2. **The missing-fact precheck.** If any fact named anywhere in the selected policy is missing, the decision returns
   `if_fact_missing` with the reason `MISSING_FACT` and the list of the missing facts. No rule runs, so the result
   never depends on the order of the rules.
3. Otherwise the rules are checked from top to bottom, and the first rule whose conditions all hold decides.
   Conditions in one rule are ANDed.
4. If no rule matches, the policy `default` applies with the reason `DEFAULT`.

**Example (non-normative):** decisions of `checkout.order_submission` in division `south`, and the reasons.

| Facts | Effect | Reason |
| --- | --- | --- |
| Active customer on credit hold; items `STOCKED`, `SPECIAL_ORDER` | `ALLOW` | Rule `credit-hold-allowed-stock`: every item is in the division's list |
| The same, with items `STOCKED`, `DROP_SHIP` (a mixed basket) | `DENY` | `DEFAULT`: no rule matched |
| The same, with no items (`item` is `[]`) | `DENY` | `DEFAULT`: `all_in` is false over an empty collection |
| Active customer, `customer.credit_hold` not given | `DENY` | `MISSING_FACT`, facts `["customer.credit_hold"]` |

With `channel=web` and no division, the root binding applies: an active customer with good credit gets `ALLOW`
from rule `good-credit` whatever the items. The decision result also names the binding (`level`, `node`) and the
policy, and its `trace` lists every rule that was checked and, for one that did not match, the first condition that
failed:

```json
{
  "feature": "checkout.order_submission",
  "effect": "DENY",
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

(`bundleVersion` is left out.) The invoice decision shows the other quantifier. `checkout.invoice_release` returns
`HOLD` when `any_in` finds an order in a held state, and `RELEASE` when `all_in` finds every order in a released
state. For orders `PAID` and `PENDING`, the root binding gives `HOLD` (rule `any-held-order`), and division `south`,
whose binding treats `PENDING` as released, gives `RELEASE` (rule `all-orders-released`).

### Write the examples

Each rules file holds `examples`: a context, a FactSet and the expected effect. They run whenever the project is
validated or tested, and a wrong one is `E_EXAMPLE_FAILED`. `examples/required-per-binding` asks for at least one
per binding, and `examples/mixed-collection` asks for one with a mixed collection for each policy that uses a
quantifier. Both are lint rules in `tierwise:recommended`. Examples are how a decision table gets tests: they run in
CI, with `tierwise test`, before a bundle exists.

**Example (non-normative):** an example in a rules file. A list under `facts` for a child attribute expands to one
child per element, and `[]` is an empty collection.

```yaml
examples:
  - name: Mixed basket on credit hold is denied
    context: { levels: { division: south } }
    facts: { customer.active: true, customer.credit_hold: true, item.stock_type: [STOCKED, DROP_SHIP] }
    expect: DENY
```

## 9. A modeling checklist

- **Start with the levels.** Write the hierarchy you already have, from least to most specific. Make the root level
  something that is always true: a platform, a plan, a global scope.
- **Close the catalogs you can.** A closed catalog turns a typo in a node ID into an error. Use `open` only where the
  set is unbounded, and `ignore` only where skipping is acceptable.
- **Choose the strategy by asking what a lower level may do.** Replace the value (`override`)? Veto it (`all`, `min`,
  `intersect`)? Add to it (`union`, `any`, `max`)? If the answer is "nothing it must not", use a narrowing
  strategy and a kind with `narrowing_only`.
- **Declare the source-failure behaviour before you add a provider.** A limit that a service sets should fail closed.
  A grant should not.
- **Put the decision about a *thing* in a feature, and the setting that varies by level in a binding.** Do not copy a
  policy per level; bind the same policy with different setting values.
- **Write one example for every binding and one mixed collection for every quantifier.**
- **Run `tierwise validate` and `tierwise test` in CI** (see the [CLI reference](cli-reference.md)), and publish the
  bundle only from a green build.
