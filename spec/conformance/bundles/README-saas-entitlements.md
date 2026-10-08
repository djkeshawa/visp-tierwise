# SaaS entitlements bundle: compilation decisions

The bundle `saas-entitlements.json` in this directory is the compiled content of
`samples/saas-entitlements/` (single-file project, `tierwise:entitlements` preset, no features, no
rules). It was produced by hand, following section 10 and the clauses of sections 1, 2, 4, 5 and 6 that
it cites. The canonical bytes come from `js/spec-tools/canonicalize.mjs`, run on a content object
written in the session scratch directory, outside the repository. Nothing in the bundle was edited
after canonicalization. The sample was not edited.

- `bundleVersion`: `sha256:836c45a782d4c0beb15052f381ec1a7eb4a507b20ce3885d049d3ecb3f5a3c51`
- Check: the literal command of the brief, `pnpm --dir js run spec:check`, was not run, because it
  rewrites `COVERAGE.md`, which this work item does not own. `pnpm --dir js run spec:check -- --no-write`
  was run instead and exits 0 with "check passed". Stage (b) reads the sample and stage (c) reads this
  bundle and reports no problem (the suite gaps and coverage gaps it prints are expected).
- Hand check of the README's resolved values against section 5: all match (section 4 below).
- Open points: none. Each decision below is decided by a clause.

Abbreviations: "source" is the project's YAML files; "content" is the bundle's `content` member.

## 0. Single-file source

| Decision | Clauses |
| --- | --- |
| The manifest `tierwise.yaml` holds the schema and the values inline, with no `sources` entry. This is a valid single-file project. | MANIFEST-13, MANIFEST-17, MANIFEST-18, SRC-13 |
| The inline `schema` is a mapping whose only top-level keys are `decision_hierarchy` and `keys`, both allowed in a schema file. | MANIFEST-14, SRC-14, SRC-16 |
| The inline `values` is a mapping of the multi-node shape: `<scope>: { <node>: { key: value } }` for `org` and `workspace`, and `plan: { key: value }` with no node for the root. | MANIFEST-15, SRC-29, SRC-30 |
| `extends: [tierwise:entitlements]` is a list holding one built-in preset reference. The preset layer comes first, then the project's own definitions as the final layer. The preset supplies the kinds `limit`, `feature` and `grant` that the keys opt into. | MANIFEST-19, MANIFEST-20, MANIFEST-21, MANIFEST-31, SRC-38 |

## 1. Envelope and content members

| Decision | Clauses |
| --- | --- |
| The envelope is `{bundleVersion, content}`, written as JCS with no whitespace and no trailing newline. | BUNDLE-1, BUNDLE-2, BUNDLE-4, BUNDLE-7 to BUNDLE-9 |
| `bundleVersion` is the SHA-256 of the JCS bytes of `content` alone. | BUNDLE-5, BUNDLE-6 |
| `bundleFormatVersion` is `"1"`, and `specVersion` is `"0.1"`, equal to the manifest's `specVersion`. | BUNDLE-16 to BUNDLE-18, MANIFEST-4, MANIFEST-5 |
| `content` has exactly the eight members, all present. `entities`, `facts` and `features` are `{}`. | BUNDLE-14, BUNDLE-15, BUNDLE-19 |
| Source names are renamed per Table 10.1: `decision_hierarchy` to `hierarchy`, and the key members `settable_at` and `on_source_failure` to `settableAt` and `onSourceFailure`. Other names keep their source spelling. | BUNDLE-25, BUNDLE-26, BUNDLE-52, BUNDLE-53, Table 10.1 |
| The `description` of each key and the `extends` key are excluded. The preset is not in the bundle. | BUNDLE-109 |

## 2. Hierarchy

| Decision | Clauses |
| --- | --- |
| `levels` is `["plan","org","workspace","user"]`, root first and in declared order. `axes` is `[]`, because the sample declares none. | BUNDLE-30, BUNDLE-31, SCOPE-6, SCOPE-10 |
| `scopes` has one member each for `org`, `workspace` and `user`, and none for the root `plan`. | BUNDLE-32, SCOPE-12, SCOPE-14 |
| `values` is `"files"` for `org` and `workspace`, where the source omits it. It is `"service"` for `user`. | BUNDLE-33 to BUNDLE-36, BUNDLE-35, SCOPE-25, SCOPE-26 |
| `org.nodes` is `["acme","globex"]`, in code point order, with `onUnknownNode: "error"` as the source states. | BUNDLE-38, BUNDLE-39, BUNDLE-42, BUNDLE-43, SCOPE-22, DET-6 |
| `workspace` and `user` have `nodes: "open"` and no `onUnknownNode`. | BUNDLE-37, BUNDLE-42, SCOPE-24 |
| The `user` level is service-sourced, so `values` has no `user` entry. | BUNDLE-64, BUNDLE-67, SVC-13, SCOPE-47 |

## 3. Keys and values

| Decision | Clauses |
| --- | --- |
| Every key has `settableAt: ["plan","org","workspace","user"]`, the levels in declared order. There are no axes. | BUNDLE-54, KEY-6, KEY-7 |
| Each `limit` key (`usage.max_seats`, `usage.max_projects`) has `type: {type: "integer", min: 0}`, `combine: "min"`, `kind: "limit"` and `onSourceFailure: "deny"`. The `min` member stays in the type, because it is a type member of `integer`. | BUNDLE-52, TYPE-7, TYPE-13, MERGE-1, KIND-2, SVC-12 |
| Each `feature` key (`sso.enabled`, `audit_log.enabled`) has `type: {type: "boolean"}`, `combine: "all"`, `kind: "feature"` and `onSourceFailure: "deny"`. | BUNDLE-52, TYPE-12, MERGE-1, KIND-2 |
| `export.granted_formats` has `type: {type: "set", universe: ["csv","pdf","xlsx"]}`, `combine: "union"`, `kind: "grant"` and `onSourceFailure: "skip"`. The `universe` keeps its declared order. | BUNDLE-52, BUNDLE-75, TYPE-40, MERGE-1 |
| `onSourceFailure` is written on every key, because every key is settable at the service-sourced `user`. Each value is the one in effect, obtained through the kind that the preset defines (`on_source_failure: deny` for `limit` and `feature`, `skip` for `grant`). The sample declares none on a key. | BUNDLE-59, BUNDLE-60, BUNDLE-61, KIND-9, SVC-1, SVC-9, SVC-11, MANIFEST-21, MANIFEST-31 |
| No key is commutative with a `default`, so no key has `default`. The sample declares none. | MERGE-4, BUNDLE-55 |
| No key is order-dependent, and no key is settable at an axis, so no key has `combineAcrossAxes`. | BUNDLE-57, AXIS-2, AXIS-6 |
| `kind` is the kind's name only. The kinds' strategies, `on_source_failure` as a kind member, `naming` and `description` are not written. | BUNDLE-62, BUNDLE-63, BUNDLE-109, KIND-1 |
| `values` has three scopes, `plan`, `org` and `workspace`, and no empty scope or node. | BUNDLE-22, BUNDLE-64, BUNDLE-66 |
| The root values of the sample are written under `values.plan` with the node key `""`. | MANIFEST-15, SRC-30, SCOPE-13, BUNDLE-66 |
| `org.acme` has `{"usage.max_seats": 10}`. `workspace.ws-2` has `{"export.granted_formats": ["pdf"]}`. | MANIFEST-15, SRC-29, BUNDLE-65, BUNDLE-74 |
| Set values are written in universe order: `["csv"]` at the root and `["pdf"]` at `ws-2`. | BUNDLE-74, TYPE-50 |
| Integer values are JSON integers within the declared bound: 25, 20, 10. The boolean `false` at the root is written as a JSON boolean. | BUNDLE-68, BUNDLE-69, TYPE-19, TYPE-12 |

## 4. Checking the README's resolved values

The sample README (sections 2 and 3 of its tutorial) states resolved values for an example context
`{org: acme, workspace: ws-2, user: u-1001}` and a user provider that returns
`{usage.max_seats: 12, sso.enabled: true}`, and then a provider failure. Each value is computed here by
section 5 from this bundle and the provider inputs, which the bundle does not contain
([SVC-13]). No mismatch was found.

| Key | README result | Computed from the bundle by section 5 | Clauses |
| --- | --- | --- | --- |
| `usage.max_seats`, provider up | `10` | `min` of the root value 25 (`values.plan[""]`), the `acme` value 10 (`values.org.acme`) and the provider's 12. No `ws-2` value. `min(25, 10, 12) = 10`. | MERGE-17, MERGE-5, PROV-42 |
| `usage.max_projects`, provider up | `20` | Only the root value 20. The provider returns no entry, so `user` contributes nothing. `min(20) = 20`. | SVC-36, PROV-42 |
| `sso.enabled`, provider up | `false` | `all` of the root value `false` and the provider's `true`. The `true` is the identity of `all`, so it is `IGNORED` and cannot widen the result. `all(false, true) = false`. | MERGE-17, MERGE-5, SVC-33, PROV-21 |
| `audit_log.enabled`, provider up | `true` | No value in the bundle, and the provider returns no entry. Nothing contributes, so the result is the identity of `all`, `true`. | MERGE-5, MERGE-6, PROV-7 |
| `export.granted_formats`, provider up | `["csv","pdf"]` | `union` of `["csv"]` (root) and `["pdf"]` (`ws-2`), in universe order. The provider returns no entry for this key. | MERGE-17, TYPE-51, PROV-86 |
| `usage.max_seats`, provider down | `0` | Each key gets a `user` failure entry (SVC-17, PROV-39). Under `deny` the entry takes the `min` bound of the type, 0. `min(25, 10, 0) = 0`. | SVC-9, SVC-23, PROV-29, TYPE-13 |
| `usage.max_projects`, provider down | `0` | The same failure entry. `min(20, 0) = 0`. | SVC-9, PROV-29 |
| `sso.enabled`, provider down | `false` | The `user` entry is `false` (deny value of `all`). `all(false, false) = false`. | SVC-9, PROV-41 |
| `audit_log.enabled`, provider down | `false` | The `user` entry is `false` (deny value of `all`). With no other contribution, `all(false) = false`. | SVC-9, PROV-41 |
| `export.granted_formats`, provider down | `["csv","pdf"]` | The `user` entry is `skip`: no value, no contribution. The root and `ws-2` grants still apply. | SVC-8, PROV-30, PROV-32 |
| Typo `org: "acem"` (section 4 of the README) | `E_UNKNOWN_NODE` | The bundle's `org.nodes` is `["acme","globex"]`, which does not admit `acem`. The org catalog is closed with `onUnknownNode: "error"`. | SCOPE-43, CTX-12 |

The `contributions`, `skippedScopes` and `source` members in the README's expected `Resolved` results
also match the clauses it cites: a commutative key has no `source` ([PROV-45]), and an empty list is
written as `[]` ([PROV-7]). The contribution labels follow [PROV-21], [PROV-38], [PROV-39] and [PROV-41].

Two notes on the README. They are not mismatches of value.

- The README writes `bundleVersion` as `sha256:<digest>`, a placeholder. The digest of this bundle is
  `sha256:836c45a782d4c0beb15052f381ec1a7eb4a507b20ce3885d049d3ecb3f5a3c51`.
- Section 5 of the README (validation and lint) concerns compile-time checks. Lint rules and
  severities are not part of the bundle ([BUNDLE-109]), so those claims were not reproduced here.

## 5. Excluded from content

| Item | Clauses |
| --- | --- |
| Kind definitions (only the kind names remain), naming patterns, lint rules, severities, and the `lint` section of the manifest. | BUNDLE-109, KIND-1, LINT-14 |
| The preset `tierwise:entitlements` and the `extends` key. | BUNDLE-109 |
| Descriptions of keys and the `owner` of a key (none is declared). | BUNDLE-109, KEY-9 |
| Source file names, paths and positions. The sample is one file, `tierwise.yaml`. | BUNDLE-109 |
| Derived data other than `requiredFacts`, such as strategy identities and ancestor closures. The sample has no policies, so `requiredFacts` does not occur. | BUNDLE-110, BUNDLE-91 |

## 6. Notes for the coordinator

- The bundle has no features, entities or facts, so the sections of the bundle layout that concern
  them are empty objects, which BUNDLE-19 requires to be present.
- None of the five keys declares `on_source_failure`. Each one is settable at the service-sourced
  `user`, so each gets `onSourceFailure` from its kind ([KIND-9], [BUNDLE-59], [BUNDLE-61]). The
  provider-down rows of section 4 depend on those values.
- The check tool's stage (c) also counts the sibling bundle `retail-pos.json` in this directory. That
  file belongs to another work item and was not read or changed.
