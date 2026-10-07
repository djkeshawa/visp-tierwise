# ADR 0007: Root level, scope declarations and context validation

- Status: accepted for the Phase 1 specification draft (delegated technical decision)
- Date: 2026-10-08
- Resolves: open questions 1 and 13 ([history](../history/open-questions-2026-10-07.md))
- Builds on: [ADR 0005](0005-node-admission-and-canonical-artifacts.md)

## Context

ADR 0005 settled what runtime `ignore` does but left four things open: what the
root level is, the declaration syntax for node sources, the generated-catalog
format, and the structural contract for node IDs. The samples already treat the
root as special: `values/global.yaml` and `bindings: { global: { policy: … } }` give
no node ID, and example contexts never mention the root.

## Decisions

### 1. The root level is a singleton and is always in effect

- The first entry in `decision_hierarchy.levels` is the **root level**. It has no
  node catalog and exactly one implicit node.
- Values and bindings for the root are written directly under the level name, with
  no node ID.
- Every evaluation includes the root. A context **must not** name the root level;
  doing so is the evaluation error `E_CONTEXT_INVALID`.
- In provenance, root entries carry `scope: level` and `name: <root>` and **omit**
  `node`.
- The root level cannot be service-sourced, and it can't be listed as an axis.
  Either is `E_SCOPE_DECLARATION`.

Consequences for ADR 0005:

- An unknown root cannot be supplied, so the "skipped root reinsertion" case cannot
  happen.
- Every feature must have a root binding (`E_ROOT_BINDING_MISSING`), and the root is
  always eligible. A decision therefore **always** has an eligible binding, and the
  "no eligible binding" evaluation error is unreachable. It is dropped from the spec.

### 2. One declaration per scope

Replace the parallel `service_sourced`, `nodes` and `on_unknown_node` maps with a
single `scopes` map. It has one entry for every non-root level and every axis:

```yaml
decision_hierarchy:
  levels: [global, channel, division, site, account, user]
  axes: [seller]
  scopes:
    channel:  { nodes: [web, partner_portal], on_unknown_node: error }
    division: { nodes: [north, south, west], on_unknown_node: error }
    site:     { nodes: { from: catalogs/sites.json }, on_unknown_node: ignore }
    account:  { nodes: open, values: service }
    user:     { nodes: open, values: service }
    seller:   { nodes: [acme], on_unknown_node: error }
```

| Field | Values | Rule |
| --- | --- | --- |
| `nodes` | inline list, `{ from: <path> }`, or `open` | Required |
| `on_unknown_node` | `error` or `ignore` | Required for a closed catalog (inline or `from`); forbidden for `open` |
| `values` | `files` or `service` | Optional, defaults to `files`. `service` is allowed only on levels, not axes |

The declaration for each scope sits in one place, and `levels` and `axes` stay
ordered lists. A missing, extra or malformed entry is `E_SCOPE_DECLARATION`.

`values: files` is the one default in this map. That is deliberate: it is not a
safety-relevant choice, because a file-sourced scope with no files contributes
nothing.

### 3. Generated catalogs

- Format: a JSON object with exactly one property, `"nodes"`, an array of node-ID
  strings, e.g. `{ "nodes": ["site_001", "site_002"] }`.
- The path is relative to the directory containing `tierwise.yaml` and must resolve
  inside the project root.
- The compiler reads the file and **embeds** the catalog in the bundle. The bundle
  never references external files.
- Duplicate IDs, whether inline or generated, are `E_DUPLICATE_NODE`.
- In the bundle, each catalog is stored sorted by code point. Catalog order carries
  no meaning.

### 4. Node ID structural contract

Naming style belongs to presets (ADR 0005), but every implementation needs the same
structural rule. A node ID is a string that:

- is non-empty and at most 128 Unicode code points long;
- contains no C0 or C1 control characters (U+0000–U+001F, U+007F–U+009F);
- is NFC-normalised. Source IDs are normalised at compile time. Runtime IDs are
  compared by exact code-point equality and are not normalised (ADR 0011).

An ID that breaks these rules is `E_INVALID_NODE_ID` in sources and
`E_CONTEXT_INVALID` at runtime. An ID that can't be used as a file name can still be
declared through an inline values file.

### 5. Context validation happens before anything else

`resolve` and `decide` validate the whole context before any lookup or provider
call:

1. `levels` and `axes` are maps from strings to strings.
2. Every name is a declared non-root level (under `levels`) or a declared axis
   (under `axes`).
3. Every ID meets the structural contract.
4. Admission: for closed catalogs, an unlisted ID under `error` raises
   `E_UNKNOWN_NODE`, and under `ignore` it is skipped and recorded in `skippedScopes`
   (ADR 0005). Open catalogs admit every structurally valid ID.

Any failure in steps 1–3, or an `error` in step 4, raises an evaluation error and
makes no provider calls.

### 6. No bindings at service-sourced levels

`binding_settable_at` may not include a service-sourced level
(`E_BINDING_NOT_ALLOWED`). Policies and bindings come only from git. As a result,
`decide` never calls a provider, and a decision is a pure function of
(bundle, context, facts).

## Alternatives considered

| Option | Why not |
| --- | --- |
| The root is a normal level that contexts must supply | Every call site repeats a constant, and leaving it out silently drops root values and bindings |
| A root with a catalog (multi-root) | Adds a "which root" question with no use case in any sample. Model it as a second level instead. |
| Keep the parallel maps (`service_sourced`, `nodes`, `on_unknown_node`) | Each scope's facts are split across three places, so it is easy to leave one out |
| Provider-sourced bindings | Puts policy selection on a network path, makes `decide` impure, and needs a second provider contract |
| A universal node-ID regex | Naming is a modeling choice; it belongs in presets |
