# Edge bundles for the hierarchy, scope and entity compile cases

This file documents the nine bundles that the compile cases `hierarchy-*` under
`spec/conformance/compile/` expect. Each bundle is a canonical file that
`js/spec-tools/canonicalize.mjs` wrote from a content object kept outside the repository. No
bundle byte was edited by hand ([BUNDLE-2], [BUNDLE-6], [DET-1]). The file names follow
`edge-hierarchy*.json`, because one suffix per content shape keeps each bundle small enough to read.

## Bundles

| Bundle | `bundleVersion` | Content (hierarchy, entities, values) | Valid case that expects it |
| --- | --- | --- | --- |
| `edge-hierarchy.json` | `sha256:d351e1ce4eabd8558fc3149bc37c9b5984de0989b66ccb235388247cc26f6146` | levels `global`, `division`; `division` closed at `north`, `south` with `on_unknown_node: error`. No entities, keys or values | `hierarchy-valid-closed-catalog`, `hierarchy-valid-generated-catalog` |
| `edge-hierarchy-root.json` | `sha256:7f2441e289e0151397b9c6077e28f7d2c8e6467b04fe24bdd5b3849337914cb8` | levels `global` only; `scopes: {}` | `hierarchy-valid-root-only-absent`, `hierarchy-valid-root-only-null` |
| `edge-hierarchy-open.json` | `sha256:628b441b1dba2b8a2955d54e7d40517743c8b6e7da500b08e61555a21910925b` | `division` open; key `checkout.open` settable at `global`, `division`; value `brand-new` at `division` | `hierarchy-valid-open-catalog` |
| `edge-hierarchy-axes.json` | `sha256:1c17e833103606849c82718d56a7886064da23acacbe1a4f3c00e3bae3177afb` | axes `seller`, `channel` in that order; `channel` is closed with no node IDs | `hierarchy-valid-axes-and-empty-catalog` |
| `edge-hierarchy-service.json` | `sha256:fd343a13a81ccb759baf7dcb01bb8172df93b6fa30fb7e1b9babb09d1860c98a` | level `account` open and `values: service`; key `checkout.open` with `onSourceFailure: skip`; no values | `hierarchy-valid-service-level` |
| `edge-hierarchy-nodes.json` | `sha256:31081759cbaad581a0aca2e6ddc0ccb8317ebd167201af19a45a05425b3e2c6f` | `division` closed with node IDs `North Shore`, `a/b`, `no`, `off`, `on`, `yes` and a 65-code-point NFC node, in code point order | `hierarchy-valid-node-id-forms` |
| `edge-hierarchy-nfc.json` | `sha256:20478a3e83a88ed8b29bb541b8a86725016e2876f48fd4d72ca814d47d0e78f2` | `division` closed at `e`, `é` (NFC) | `hierarchy-valid-node-id-nfc` |
| `edge-hierarchy-entity-graph.json` | `sha256:94a4803e6039261d01ca40c8246708dfd1936d105112736631ae8040d9138a31` | root-only hierarchy; entities `alpha` (belongs to `gamma`, has many `beta`), `beta`, `gamma` | `hierarchy-valid-entity-graph-acyclic` |
| `edge-hierarchy-model.json` | `sha256:729c36bc2d84a342b0e14eed366e53df8017bc7a4a6b80717eca9cc73a4aafb8` | levels `global`, `division`, `site` (`site` from a catalog, `ignore`); entities `customer`, `invoice`, `order`, `item`, `carrier`; fact `order.total`; key `checkout.open`; values at root, `division`, `site`; feature `checkout.gate` with one policy and three bindings | `hierarchy-valid-model`, `hierarchy-valid-namespace-file` |

The `edge-hierarchy-model.json` bundle is the base of 26 positive compile cases. Each of them
changes one file of `hierarchy-valid-model`, so that case is their negative control ([VEC-120]).
The exception is `hierarchy-entity-hierarchy-in-namespace`, whose control is
`hierarchy-valid-namespace-file`, because its base already has the namespace file.

## Layout of the compile cases

Every directory directly under `compile/` is one compile case ([VEC-3], [VEC-13]). An area
directory such as `compile/hierarchy/` would therefore need an `expected.yaml` of its own, and the
checker reports it as missing ([VEC-4]). So each case is `compile/hierarchy-<name>/`, with the
`hierarchy-` prefix standing for the work item.

## Negative controls

Each positive case differs from the valid case of its family by one change, which introduces the
trigger ([VEC-120]). A change is an edit to one file, or the addition of one file that the positive
selects or that a catalog path names. The valid case is the negative control, so one control serves every positive
in its family. The families are: hierarchy declarations (base `hierarchy-valid-closed-catalog`);
generated catalogs (`hierarchy-valid-generated-catalog`); the root-only hierarchy
(`hierarchy-valid-root-only-absent`); the open catalog (`hierarchy-valid-open-catalog`); the axes
(`hierarchy-valid-axes-and-empty-catalog`); the service level (`hierarchy-valid-service-level`);
node IDs (`hierarchy-valid-node-id-nfc`); and the model (`hierarchy-valid-model`).

## Location conventions

The spec defines a location for most findings, through [FIND-17] to [FIND-23]. Where it does not,
these cases use the following conventions. Each one is a point for the reviewer to confirm.

- A repeated name, a repeated node ID, and a repeated list item ([SCOPE-3], [SCOPE-42],
  [ENTITY-8]) is reported at the later occurrence in document order, as [SRC-9] does for keys.
- A member that is not allowed ([SCOPE-18], [ENTITY-7]) is reported at that member's key, by
  [FIND-19] and [FIND-22].
- A missing required member ([SCOPE-17], [SCOPE-22], [SCOPE-14], [SCOPE-9] for an absent levels)
  is reported at the enclosing mapping, as [FIND-23] gives.
- A `from` path that breaks [SCOPE-28] is reported at the `from` value, and a defect in a catalog
  file at the catalog file, with the path of the defective node inside it.
- A source value that targets a service-sourced level ([SCOPE-47]) is reported at the value.

## Withheld cases

These expected outcomes depend on a location that the spec does not define, so the cases are
withheld ([VEC-114]). Their negative controls are present (`hierarchy-valid-entity-graph-acyclic`
and `hierarchy-valid-model`).

- `E_ENTITY_GRAPH` for a `belongs_to` cycle, a `has_many` cycle, an entity that is both an
  ancestor and a child ([ENTITY-14] to [ENTITY-16]), and the mixed cycle of [ENTITY-17]. No clause
  says which node a cycle is reported at.
- `E_DUPLICATE_DEFINITION` for an entity declared in two schema files ([ENTITY-3], [SRC-39],
  [SRC-40]). No clause says which of the two declarations is the second.

## Open points

- [SCOPE-11] (open): a root level listed in `axes` also needs a `scopes` entry under [SCOPE-14],
  but [SCOPE-12] forbids one. The spec does not say which applies, so no case is written. The
  clause has no citing case and is not on the enforcer list, because a vector can observe it.
- [SCOPE-16] (open): an absent or null `scopes` with a non-root level is reported under [SCOPE-14]
  or [SCOPE-15], but the spec does not say where. No case is written.
- [SCOPE-38] (open): a node ID captured from a file path ([SRC-22]) has no node in the parsed
  source, so no location is defined. No case is written.
- [ENTITY-10]: no compile case can observe the ancestor closure. It is observable through the
  ancestor check of [ENTITY-16] (withheld) and through evaluation ([FACT-13]), so it is left to
  the evaluate vectors.
- [SRC-33] and [SCOPE-47] both describe a service-sourced value. The positive case expects one
  finding at the value. If an implementation reports the values file as well, the key differs.
