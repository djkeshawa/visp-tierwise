# ADR 0011: Key types, normalisation, namespaces and source composition

- Status: accepted for the Phase 1 specification draft (delegated technical decision)
- Date: 2026-10-08
- Resolves: open questions 15, 17, 18 and 19 ([history](../history/open-questions-2026-10-07.md))

## Context

The requirements list the key-type vocabulary but not its grammar. They also leave
open how strings and decimals are normalised at runtime, whether namespaced files
may add facts, what a single-file project looks like, and how presets combine.

## Decisions

### 1. Every key declares `type`

`type` is required on every key (`E_TYPE_REQUIRED`). Set-valued keys use
`type: set` with a `universe`. The `universe` no longer implies the type; the
"explicit over implicit" principle applies to types too.

Type declarations follow one grammar for keys, object fields and feature
`returns: { object: … }`:

| `type` | Extra fields | Values |
| --- | --- | --- |
| `boolean` | — | `true` / `false` only |
| `integer` | `min`, `max` (optional, within ±(2^53−1)) | Integers in range |
| `decimal` | — | Decimal **strings** (section 3) |
| `string` | `format` (optional): `time_hhmm` or `iana_tz` | Strings |
| `enum` | `values: [..]` (required, distinct strings) | One of `values` |
| `set` | `universe: [..]` (required, distinct strings) | Distinct subset of `universe`; results follow universe order |
| `list` | `items: <type decl>` (required) | Ordered list; allowed only with `override`, or as an object field |
| `object` | `fields: { name: <type decl> + optional: true? }` | Every non-optional field present; **unknown fields rejected** |

- `null` is never a valid key or field value.
- A strategy must fit its type (`E_STRATEGY_TYPE`):

  | Strategy | Allowed types |
  | --- | --- |
  | `override` | any type |
  | `deep_merge` | `object` |
  | `all`, `any` | `boolean` |
  | `intersect`, `union` | `set` |
  | `min`, `max` | `integer` |

### 2. String formats

- `time_hhmm`: `^([01][0-9]|2[0-3]):[0-5][0-9]$`.
- `iana_tz`: a zone or link name in the IANA tz database release pinned by the
  spec version, shipped as `spec/data/tz-names.txt` and validated at compile time.
  At runtime, evaluators check provider values only against the syntax
  `^[A-Za-z0-9_+-]+(/[A-Za-z0-9_+-]+)*$`. Evaluators carry no tz data and do no
  time-zone conversion.

### 3. Normalisation happens at compile time; evaluators compare exactly

- **Strings** in sources are NFC-normalised by the compiler. Evaluators do **not**
  normalise runtime inputs: they compare code points exactly. Callers should supply
  NFC strings; a non-NFC input simply won't match. Different runtimes ship
  different Unicode versions, so normalising at runtime would break the guarantee
  that Java and JS agree.
- **Decimals** are written as strings in sources (`"1.50"`). A YAML number for a
  decimal key is `E_TYPE_MISMATCH`, with a hint to quote it, because YAML floats
  lose precision. The canonical form is `-?(0|[1-9][0-9]*)(\.[0-9]*[1-9])?`: no
  exponent, no `+`, no leading zeros, no trailing fractional zeros, and no `-0`.
  - The compiler canonicalises source decimals.
  - Evaluators canonicalise runtime decimal **strings** that match
    `[+-]?[0-9]+(\.[0-9]+)?`, which is a pure string operation that is identical in
    both languages. Numbers given for decimal facts are rejected.

### 4. Shared definitions live only in shared files

When `namespaces` are declared, the hierarchy, entities, facts and kinds may appear
**only** in shared (non-namespaced) schema files. A namespaced file declares keys
and features in its own namespace and nothing else (`E_NAMESPACE_VIOLATION`). The
earlier "namespaces may extend shared definitions" clause is removed, because it
conflicted with "facts are never defined inside a namespace". Ownership of shared
definitions is a code-review matter (CODEOWNERS), not an engine feature.

### 5. Manifest and single-file projects

- `tierwise.yaml` is the only source authority for `specVersion`.
- `sources.schema`, `sources.values` and `sources.rules` list glob patterns or
  mappings. They are optional.
- A single-file project puts the same content inline in the manifest under the
  top-level keys `schema`, `values` and `rules`. Inline and file sources may be
  mixed; their definitions are combined, and conflicts are reported in the usual
  way (`E_DUPLICATE_DEFINITION`, `E_DUPLICATE_VALUE`).

### 6. Values files and the root

- **Path-mapped**: `{ pattern: "values/{scope}/{node}.yaml" }`. The file contains
  `key: value` entries for that scope and node.
- **Root file**: `{ file: "values/global.yaml", scope: <root level> }`. The root has
  no node (ADR 0007).
- **Multi-node file**: `{ file: "values/overrides.yaml" }`, structured as
  `<scope>: { <node>: { key: value } }`. Root entries are written
  `<root>: { key: value }`.
- A file matching more than one mapping is `E_SOURCE_MAPPING`.

### 7. Preset composition

`extends` presets are applied in order, then the project's own definitions:

| Item | When defined more than once |
| --- | --- |
| Kinds, naming patterns | **Replace** the earlier definition entirely (later wins; no field-level merging) |
| Lint severities | Later setting wins per rule |
| Facts, entities, hierarchy, keys, features | `E_DUPLICATE_DEFINITION`. These are semantic and may not be redefined. |
| Lint configuration naming an engine error code | `E_PRESET`. Engine errors can't be configured. |

Replacing a kind is reported as an info-level lint (`presets/override`), so the
replacement is visible but not an error.

## Alternatives considered

| Option | Why not |
| --- | --- |
| Universe implies `type: set` | Implicit typing is exactly what the project removes elsewhere |
| Runtime NFC normalisation | Unicode version differences between JVM and JS engines would break cross-language agreement |
| Decimals as YAML numbers | Lose precision before the compiler can see them |
| Field-level merge of preset kinds | Hard to predict; one replaced field can silently change a kind's guarantees |
| Namespaced fact extensions | Contradicts the single shared fact model that makes rules unambiguous |
