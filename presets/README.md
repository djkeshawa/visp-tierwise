# presets

Built-in presets are reusable sets of kinds, naming patterns and lint settings. A
project names one in `extends` as `tierwise:<name>` ([MANIFEST-21]). Each name is
the file of the same name in this directory. Any other `tierwise:` name is
`E_PRESET` ([MANIFEST-22]).

| Reference | File | Use it when |
| --- | --- | --- |
| `tierwise:recommended` | `recommended.yaml` | Most teams. Kinds for settings and capabilities, naming checks, strict lint. |
| `tierwise:minimal` | `minimal.yaml` | Trying Tierwise out. No kinds and no naming checks, and lint only for likely bugs. |
| `tierwise:entitlements` | `entitlements.yaml` | Plan and entitlement models. Kinds for numeric limits, on/off features and granted sets. |

Clause IDs in this file refer to [section 1](../spec/sections/01-manifest-and-sources.md),
[section 3](../spec/sections/03-entities-and-facts.md), [section 5](../spec/sections/05-keys-and-merge.md),
[section 6](../spec/sections/06-service-sources.md), [section 10](../spec/sections/10-bundle-format-and-determinism.md)
and [section 13](../spec/sections/13-findings-and-lint.md) of the specification. The
clauses define the behaviour. This file lists what the built-in presets contain.

## What a preset may contain

The top-level keys of a preset file are limited by [MANIFEST-36]. The built-in presets
use only `kinds`, `naming` and `lint`, which is what
[spec.md](../spec/spec.md#terminology) defines a preset to be. They declare no
hierarchy, entity, fact, key or feature. A preset has no `extends` ([MANIFEST-35], code
`E_PRESET`) and no `specVersion` ([MANIFEST-6]). Presets are not part of the bundle
([BUNDLE-109]).

A preset cannot switch off an engine check. A lint setting that names an engine error
code is `E_PRESET` and has no effect ([LINT-8], [LINT-9], [FIND-15]).

## How presets combine

- Layer order is the order of `extends`, then the project's own definitions
  ([MANIFEST-31]).
- A kind or naming pattern defined again in a later layer replaces the earlier one
  entirely ([MANIFEST-32], [MANIFEST-33]). Each replacement is reported by
  `presets/override` at the severity that resolves for that rule ([MANIFEST-34],
  [LINT-49], [LINT-50]). Every built-in preset sets it to `info`.
- Lint severities resolve per rule, and the last setting in layer order wins
  ([LINT-4], [LINT-5]). The wildcard `naming/*` is defined by [LINT-10] (a).
- A kind's `naming` member and the scope of a naming pattern are defined by [KIND-12]
  and [LINT-16]. A kind that names no declared pattern is `E_KIND_DEFINITION`.

## Kinds

Keys opt into a kind with `kind` ([KIND-2]). The members a kind may declare are listed
in [KIND-3].

| Preset | Kind | Strategies | Other members | `on_source_failure` | Naming |
| --- | --- | --- | --- | --- | --- |
| `recommended` | `configuration` | `override`, `deep_merge` | `require_default: true` | `skip` | none |
| `recommended` | `capability` | `all`, `intersect`, `min` | `narrowing_only: true` | `deny` | `capability_key` |
| `entitlements` | `limit` | `min` | none | `deny` | `limit_key` |
| `entitlements` | `feature` | `all` | none | `deny` | `feature_key` |
| `entitlements` | `grant` | `union` | none | `skip` | `grant_key` |

Notes on the choices, which follow from the clauses cited:

- `configuration` allows only order-dependent strategies because it sets
  `require_default` ([KIND-8], [KIND-10]; `E_KIND_DEFINITION` otherwise).
- `capability` allows only narrowing strategies ([KIND-5], [MERGE-2]), and its authored
  values may not equal the strategy identity ([KIND-6], `E_KIND_VIOLATION`). Its
  `deny` behaviour applies at service-sourced levels, where a `min` key must declare the
  `min` bound of its integer type ([SVC-12], `E_DENY_BOUND_REQUIRED`).
- `limit` and `feature` use `deny`, which fits their narrowing strategies. `limit` keys
  settable at a service-sourced level must declare the `min` bound ([SVC-9], [SVC-12],
  `E_DENY_BOUND_REQUIRED`).
- `grant` uses `skip`, because `union` is not narrowing ([SVC-11],
  `E_DENY_NOT_NARROWING`).
- A key takes its kind's `on_source_failure` ([KIND-9]). A key that declares a different
  value is `E_KIND_VIOLATION` ([KIND-11]).

## Naming patterns

Each pattern is declared with a `category`, a `pattern` and an optional `description`
([LINT-14], [LINT-15]). The pattern language is [LINT-17], and an invalid pattern or
declaration in a preset is `E_PRESET`. Each pattern gives one lint rule `naming/<name>`
([LINT-12]), and names are checked by search ([LINT-18]).

| Preset | Pattern | Category | Applies to |
| --- | --- | --- | --- |
| `recommended` | `key` | key | every key |
| `recommended` | `capability_key` | key | keys of kind `capability` |
| `recommended` | `enum_value` | enum_value | enum values ([LINT-19], [LINT-20]) |
| `recommended` | `node_id` | node_id | node IDs of closed catalogs ([LINT-19]) |
| `recommended` | `feature` | feature | feature names |
| `entitlements` | `key` | key | every key |
| `entitlements` | `limit_key` | key | keys of kind `limit` |
| `entitlements` | `feature_key` | key | keys of kind `feature` |
| `entitlements` | `grant_key` | key | keys of kind `grant` |
| `entitlements` | `enum_value`, `node_id`, `feature` | as above | as above |

No pattern covers fact names ([LINT-22], [FACT-1]).

## Lint severities

Severities are `error`, `warn`, `info` or `off` ([LINT-3], [LINT-6]).

| Rule ([LINT-1]) | `recommended` | `minimal` | `entitlements` |
| --- | --- | --- | --- |
| `naming/*` | error | off | error |
| `values/no-op` | error | warn | error |
| `rules/dead-rule` | warn | warn | warn |
| `examples/required-per-binding` | error | off | error |
| `examples/mixed-collection` | warn | off | warn |
| `schema/unused` | warn | off | warn |
| `schema/description-required` | warn | off | warn |
| `schema/owner-required` | error | off | error |
| `presets/override` | info | info | info |

The `recommended` and `minimal` columns are from requirements section 6.3. The
requirements give no column for `entitlements`, so that column repeats the `recommended`
severities. The `minimal` preset lists every rule, so that a rule set to `off` also
overrides an earlier preset ([LINT-4]).

`schema/owner-required` reports nothing unless the manifest declares at least one
namespace ([LINT-46], [LINT-47]). Lint runs only when a validation has no engine
finding ([LINT-11]).

## Choosing a preset

- Start with `tierwise:minimal` to see how the sources and findings look. It has no
  kinds and no naming checks, and its lint covers `values/no-op` and `rules/dead-rule`
  (both `warn`) and `presets/override` (`info`).
- Use `tierwise:recommended` for a project that wants its names and settings checked.
  Extend it, then add lint overrides in the manifest's `lint.rules` if a rule is too
  strict ([LINT-4]).
- Use `tierwise:entitlements` for numeric limits, on/off features and granted sets. Its
  kinds `limit` and `feature` deny on a failed source, and `grant` skips.
- A project can also extend a preset by path or URL ([MANIFEST-23], [MANIFEST-25]). The
  same rules apply to those presets.

> Example (non-normative)
>
> ```yaml
> extends:
>   - tierwise:recommended
> lint:
>   rules:
>     schema/description-required: "off"
> ```
