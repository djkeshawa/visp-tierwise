# 2. Decision hierarchy and node admission

This section defines the decision hierarchy: its levels and axes, the singleton root level, the scope declarations, generated node catalogs, the structure of node IDs, and the rules that decide which node IDs a compiler accepts and which an evaluator admits at runtime. It also defines the shape of a context and the order in which context validation runs. It does not define where declarations are written or how files map to scopes (section 1, areas `MANIFEST` and `SRC`), how values and bindings merge (sections 5 and 7), how providers behave (section 6), normalisation of strings (section 4, area `NORM`), or the shape of the `skippedScopes` provenance entry (section 9, area `PROV`).

## 2.1 Levels and axes

**[SCOPE-1]** The `levels` list MUST contain at least one entry. The error code is the open point [SCOPE-9].

**[SCOPE-2]** Each entry in `levels` and `axes` MUST be a string. The error code is the open point [SCOPE-9]. How a scalar that the core schema reads as a non-string is treated in a name position is the open point [SRC-11].

**[SCOPE-3]** Each name MUST be unique across the `levels` and `axes` lists taken together. The error code is the open point [SCOPE-4], except that the root level listed in `axes` is governed by [SCOPE-11].

**[SCOPE-4]** *(OPEN)* Which error code reports a duplicate name under [SCOPE-3]: `E_SCOPE_DECLARATION` (a malformed declaration) or `E_DUPLICATE_DEFINITION` (the code ADR 0011 §7 uses when a hierarchy is defined more than once across presets and the project)?

**[SCOPE-5]** The `axes` list is optional. An omitted or empty `axes` list declares no axes.

**[SCOPE-6]** The order of `levels` is the specificity order. The first level is the least specific and the last level is the most specific.

**[SCOPE-7]** Axes have no position in the specificity order. Where the order of axes matters, it is their declared order in `axes`.

**[SCOPE-8]** *(OPEN)* Beyond being unique strings, the engine states no syntax for level and axis names. Is the empty string a valid name? Are any characters reserved, given that names are used as map keys in sources and contexts? Are level and axis names written in sources NFC-normalised before the uniqueness check of [SCOPE-3] and the lookups of [SCOPE-46], as [NORM-1] does for values and [SRC-24] proposes for names captured from file paths?

**[SCOPE-9]** *(OPEN)* Which code reports a malformed hierarchy declaration: a `levels` value that is not a list, an empty `levels` list ([SCOPE-1]), an `axes` value that is not a list, or an entry of `levels` or `axes` that is not a string ([SCOPE-2])? The candidates are `E_MANIFEST` and `E_SCOPE_DECLARATION`.

## 2.2 The root level

**[SCOPE-10]** The first entry of `levels` is the root level. The root level is a singleton. It has no node catalog and exactly one implicit node, and that node has no node ID.

**[SCOPE-11]** The root level MUST NOT be listed in `axes`. If it is, the compiler MUST report `E_SCOPE_DECLARATION`. This code applies whatever [SCOPE-4] decides for other duplicate names.

**[SCOPE-12]** The root level MUST NOT have an entry in the `scopes` map, including an entry that sets `values: service`. If it has one, the compiler MUST report `E_SCOPE_DECLARATION`.

**[SCOPE-13]** Values and bindings for the root level are identified by the root level name alone and carry no node ID. How they are written in source files is governed by section 1 (`SRC`).

> **Note (non-normative).** The root level's consequences in other areas are defined there and are only listed here:
> - Every feature has a root binding ([BIND-13], `E_ROOT_BINDING_MISSING`), and the root binding is always a candidate ([BIND-19]).
> - `binding_settable_at` must include the root level ([FEAT-18], `E_ROOT_BINDING_NOT_ALLOWED`).
> - A key has at most one root value ([KEY-12]), and an order-dependent key needs a `default` or a root value ([MERGE-7]).
> - Root provenance entries and a root binding carry no `node` ([PROV-25], [PROV-69]).
> - The root level never appears in `skippedScopes` ([PROV-57]).
> - A context cannot name the root level ([CTX-3]), and the root level is on every evaluation path ([CTX-20]).

## 2.3 Scope declarations

**[SCOPE-14]** Every non-root level and every axis MUST have exactly one entry in the `scopes` map. The `scopes` map MUST NOT contain any other entry. If an entry is missing or extra, the compiler MUST report `E_SCOPE_DECLARATION`.

**[SCOPE-15]** The `scopes` value MUST be a map. If it is not, for example a list or a scalar, the compiler MUST report `E_SCOPE_DECLARATION`.

**[SCOPE-16]** *(OPEN)* May `scopes` be absent, or must it be an empty map, when the hierarchy has only the root level and no axes (for example `levels: [global]`)? Is an explicit `null` treated as absent, or as a non-map value under [SCOPE-15]?

**[SCOPE-17]** A scope entry MUST contain `nodes`. If it does not, the compiler MUST report `E_SCOPE_DECLARATION`.

**[SCOPE-18]** Besides `nodes`, a scope entry MAY contain only `on_unknown_node` and `values`. If an entry contains any other property, the compiler MUST report `E_SCOPE_DECLARATION`.

**[SCOPE-19]** The `nodes` value MUST be one of: an inline list of node IDs; an object whose only property is `from`, with a non-empty string path as its value; or the string `open`. Any other value MUST be reported as `E_SCOPE_DECLARATION`.

**[SCOPE-20]** *(OPEN)* Which code reports a non-string entry in an inline `nodes` list: `E_SCOPE_DECLARATION` or `E_INVALID_NODE_ID`? Whether a scalar that the core schema reads as a non-string is ever converted to a string in a name position is the open point [SRC-11]. This clause asks only which code applies.

**[SCOPE-21]** *(OPEN)* Is a closed catalog with no IDs valid, that is, an empty inline list or a generated catalog whose `nodes` array is empty? If it is valid, every supplied ID for that scope is unlisted.

**[SCOPE-22]** A closed catalog is a scope whose `nodes` is an inline list or a `from` object. Its entry MUST contain `on_unknown_node`. If it does not, the compiler MUST report `E_SCOPE_DECLARATION`.

**[SCOPE-23]** The value of `on_unknown_node` MUST be `error` or `ignore`. Any other value MUST be reported as `E_SCOPE_DECLARATION`.

**[SCOPE-24]** An open catalog entry, one whose `nodes` is `open`, MUST NOT contain `on_unknown_node`. If it does, the compiler MUST report `E_SCOPE_DECLARATION`.

**[SCOPE-25]** When `values` is absent, the scope is file-sourced. When it is present, it MUST be `files` or `service`. Any other value MUST be reported as `E_SCOPE_DECLARATION`.

**[SCOPE-26]** A scope declared `values: service` MUST be a level. If an axis declares `values: service`, the compiler MUST report `E_SCOPE_DECLARATION`.

**Example (non-normative)**

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

## 2.4 Generated catalogs

**[SCOPE-27]** A `from` path MUST be relative to the project directory, the directory that contains `tierwise.yaml` (spec.md §Terminology, **Project**), and MUST resolve to a location inside that directory.

**[SCOPE-28]** *(OPEN)* Which code reports a `from` path that is absolute, that resolves outside the project directory, that names a missing file, or that names a directory? Are symbolic links followed when checking that a path stays inside the project directory? [MANIFEST-12] asks the same question for every manifest path and proposes `E_MANIFEST`; one resolution answers both.

**[SCOPE-29]** A catalog file MUST be a JSON object with exactly one property, `nodes`, whose value is an array of strings.

**[SCOPE-30]** *(OPEN)* Which code reports a catalog file that does not match [SCOPE-29]: one that is not well-formed JSON, that is not UTF-8 or starts with a byte-order mark, that repeats a property name, that has a property other than `nodes`, or whose `nodes` value is not an array of strings? Section 1 owns the parse codes (`E_MANIFEST`, `E_DUPLICATE_KEY`, see [SRC-3]); the question is which of them, or `E_SCOPE_DECLARATION`, applies to a catalog file. May two scopes name the same catalog file?

**[SCOPE-31]** The compiler MUST read each generated catalog and embed its node IDs in the compiled bundle, in the NFC-normalised form of [SCOPE-35]. The bundle MUST NOT reference the catalog file. The bundle layout, including how each scope records its catalog or `open` and its `on_unknown_node` setting, is defined in section 10 ([BUNDLE-29] to [BUNDLE-43]).

**[SCOPE-32]** The order of node IDs in a catalog, whether inline or generated, MUST NOT affect admission, compile checks or evaluation. Section 10 (`DET`) defines the order in which catalogs are stored.

## 2.5 Node IDs

**[SCOPE-33]** A node ID MUST be a non-empty string of at most 128 Unicode code points.

**[SCOPE-34]** A node ID MUST NOT contain a control character in the range U+0000 to U+001F or U+007F to U+009F.

**[SCOPE-35]** The compiler MUST normalise each node ID written in a source to NFC, as the `NORM` area defines. This includes each entry of a generated catalog file. A source node ID that is not in NFC is normalised, not rejected. Whether a node ID captured from a file path is normalised is the open point [SRC-24].

**[SCOPE-36]** *(OPEN)* Are the length check of [SCOPE-33] and the control-character check of [SCOPE-34] applied to a source node ID before or after NFC normalisation? Normalisation can change the number of code points in a string. Proposed: after, as [NORM-1] orders its checks for values.

**[SCOPE-37]** *(OPEN)* Is a string that contains an unpaired surrogate a valid node ID? Such a string does not denote a sequence of Unicode code points, and JSON and YAML sources can contain one. This is the node ID case of [NORM-8]. The answer also governs context node IDs under [CTX-6].

**[SCOPE-38]** A source node ID that breaks [SCOPE-33] or [SCOPE-34] MUST be reported as `E_INVALID_NODE_ID`. This applies wherever a source writes a node ID: an inline list, a generated catalog, a values entry, a binding, or a node ID captured from a file path ([SRC-22]).

**[SCOPE-39]** *(OPEN)* When a source node ID both breaks the contract and is absent from its closed catalog, which code is reported: `E_INVALID_NODE_ID`, `E_UNKNOWN_NODE`, or both? Every finding that is determined is reported ([FIND-3]); the question is whether an ID that fails the contract is also checked for membership.

**[SCOPE-40]** The engine MUST NOT require any naming pattern for node IDs beyond [SCOPE-33] and [SCOPE-34]. Naming patterns are project or preset policy and are checked, if at all, by lint rules (section 13, area `LINT`).

**[SCOPE-41]** A node ID that cannot be used as a file name is still a valid node ID. Where its values are written is governed by section 1 (`SRC`).

**[SCOPE-42]** Two node IDs in one catalog MUST NOT be equal after NFC normalisation. If two are, the compiler MUST report `E_DUPLICATE_NODE`.

## 2.6 Compile-time admission

**[SCOPE-43]** For a closed catalog, every node ID that a source value or binding uses MUST be a member of that catalog, compared after NFC normalisation. If an ID is not a member, the compiler MUST report `E_UNKNOWN_NODE`.

**[SCOPE-44]** The check in [SCOPE-43] MUST apply whatever the value of `on_unknown_node`. The setting has no effect at compile time.

**[SCOPE-45]** For an open catalog, the compiler MUST NOT check a source node ID for membership. Any node ID that passes [SCOPE-33] and [SCOPE-34] is accepted.

**[SCOPE-46]** A source value or binding that names a level or axis not declared in `levels` or `axes` MUST be reported as `E_UNKNOWN_SCOPE`.

**[SCOPE-47]** *(OPEN)* May a source value target a level declared `values: service`? If not, which code reports it? This clause owns the question; [SRC-33] and [SVC-13] refer to it. Bindings at a service-sourced level are already settled: they are `E_BINDING_NOT_ALLOWED` ([FEAT-19], [BIND-12]).

## 2.7 Context shape

**[CTX-1]** Each of the `levels` and `axes` members that a context contains MUST be a map from strings to strings. If a member is not such a map, or a map entry has a value that is not a string, the evaluator MUST raise `E_CONTEXT_INVALID`. Whether a member may be absent is the open point [CTX-2].

**[CTX-2]** *(OPEN)* Are the `levels` and `axes` members of a context required? Are members other than these two rejected? The sources differ: requirements §5.5 and §5.7 write contexts with only `levels`, while ADR 0007 §5 step 1 and the API sketches in requirements §7 carry both maps.

**[CTX-3]** A context MUST NOT name the root level, in either map. If it does, the evaluator MUST raise `E_CONTEXT_INVALID`. This clause, not [CTX-4], governs the root level's name.

**[CTX-4]** Each name in `levels`, other than the root level's name, MUST be a declared non-root level. Each name in `axes`, other than the root level's name, MUST be a declared axis. Names are compared under [NORM-6] and are not normalised ([NORM-3]). If a name is not declared in the map that holds it, the evaluator MUST raise `E_UNKNOWN_SCOPE`.

**[CTX-5]** A declared non-root level supplied under `axes`, or a declared axis supplied under `levels`, is not declared in the map that holds it. The evaluator MUST raise `E_UNKNOWN_SCOPE` for it under [CTX-4].

**[CTX-6]** Each node ID value in a context MUST meet [SCOPE-33] and [SCOPE-34]. If it does not, the evaluator MUST raise `E_CONTEXT_INVALID`. Whether a string with an unpaired surrogate meets them is the open point [SCOPE-37].

**[CTX-7]** An evaluator MUST NOT check whether a context node ID is in NFC, and MUST NOT raise `E_CONTEXT_INVALID` because it is not. Such an ID is compared as received under [CTX-8]. It is admitted or rejected by the rules of section 2.9 like any other ID.

> **Note (non-normative).** A context node ID that is not in NFC does not equal the NFC catalog member it would normalise to ([NORM-5]). Under `on_unknown_node: error` it raises `E_UNKNOWN_NODE`, under `ignore` its scope is skipped, and in an open catalog it is admitted as received. The NFC rule for node IDs applies to sources only (requirements §4.1, §4.11 item 4; ADR 0011 §3). Checking NFC at runtime needs Unicode data, and runtimes ship different Unicode versions.

**[CTX-8]** Admission MUST compare a context node ID with catalog members by the exact code-point comparison that the `NORM` area defines ([NORM-6]). Admission does not normalise context node IDs.

## 2.8 Context validation order

**[CTX-9]** Context validation MUST run these steps in this order:

1. Shape: [CTX-1].
2. Scope names: [CTX-3], [CTX-4] and [CTX-5].
3. Node ID structure: [CTX-6].
4. Admission: [CTX-12], [CTX-13] and [CTX-14].

**[CTX-10]** An evaluator MUST NOT start a value lookup, a binding lookup or a provider call until the context has passed all four steps of [CTX-9].

**[CTX-11]** *(OPEN)* When a context fails two checks of the same step of [CTX-9] that have different codes, which code is raised? Only step 2 has two codes: one context may name the root level ([CTX-3], `E_CONTEXT_INVALID`) and an undeclared scope ([CTX-4], `E_UNKNOWN_SCOPE`). The answer must not depend on the order of map entries. Across steps, the first failing step decides ([EVAL-18]). [EVAL-19] refers to this question and proposes `E_CONTEXT_INVALID`.

## 2.9 Runtime admission

**[CTX-12]** For a closed catalog whose `on_unknown_node` is `error`, a supplied node ID that is not a catalog member MUST raise `E_UNKNOWN_NODE`. The evaluator MUST raise it during step 4 of [CTX-9].

**[CTX-13]** For an open catalog, a supplied node ID that passes [CTX-6] MUST be admitted. Admission does not check membership. Provider resolution for an admitted node is governed by section 6.

**[CTX-14]** For a closed catalog whose `on_unknown_node` is `ignore`, a supplied node ID that is not a catalog member MUST skip the whole scope.

**[CTX-15]** A skipped scope MUST have no values read and no binding applied. It makes no provider call and triggers no source failure, so no `on_source_failure` applies ([SVC-17]).

**[CTX-16]** The result MUST record each skipped scope in `skippedScopes`, in the shape and order that the `PROV` area defines ([PROV-54], [PROV-58]). What a skipped scope does not produce is defined by [PROV-61], and the empty list by [PROV-7].

**[CTX-17]** Skipping one scope MUST NOT change whether any other supplied scope is admitted, or whether it is on the evaluation path ([CTX-19]).

**[CTX-18]** The `on_unknown_node` setting MUST change only the outcome of step 4 of [CTX-9] for an unlisted node ID in a closed catalog. Errors from steps 1 to 3, and `E_UNKNOWN_SCOPE`, MUST be raised under both policies.

## 2.10 Evaluation path

**[CTX-19]** The evaluation path MUST consist of the root level and each supplied non-root level and axis that is admitted, that is, not skipped.

**[CTX-20]** The root level MUST be on every evaluation path, including when the context is empty. No context entry can remove it.

**[CTX-21]** A non-root scope that the context omits MUST contribute nothing, and it MUST NOT be an error. It is not a skipped scope: only [CTX-14] skips a scope, and an omitted scope is not recorded in `skippedScopes` ([PROV-56]).

> **Note (non-normative).** Requirements §4.1 says "Missing levels are skipped" in the informal sense of [CTX-21]. In this specification, *skipped* means only an admission skip under [CTX-14].

**[CTX-22]** An evaluator MUST NOT infer a parent or any other relation between supplied nodes. It MUST NOT infer a node for an omitted scope.

**[CTX-23]** An evaluator MUST NOT substitute another node, or a default node, for an unlisted or skipped node.

## Open points

- [SCOPE-4]: error code for a duplicate name across `levels` and `axes`.
- [SCOPE-8]: syntax of level and axis names (empty string, reserved characters) and whether source names are NFC-normalised.
- [SCOPE-9]: error code for a malformed hierarchy declaration, including an empty `levels` list and non-string entries.
- [SCOPE-16]: whether `scopes` may be absent, or must be an empty map, when only the root level exists; whether an explicit `null` counts as absent. Proposal: absent and `{}` are both valid when no non-root scope exists.
- [SCOPE-20]: error code for a non-string entry in an inline `nodes` list.
- [SCOPE-21]: whether a closed catalog with no IDs is valid.
- [SCOPE-28]: error code for a `from` path that is absolute, leaves the project directory, or names a missing file or a directory; whether symbolic links are followed (shared with [MANIFEST-12]).
- [SCOPE-30]: error code for a catalog file that is malformed or does not match [SCOPE-29]; whether two scopes may share a catalog file.
- [SCOPE-36]: whether the length and control-character checks apply before or after NFC normalisation.
- [SCOPE-37]: whether a string with an unpaired surrogate is a valid node ID (node ID case of [NORM-4]).
- [SCOPE-39]: error code when a source node ID both breaks the contract and is unlisted.
- [SCOPE-47]: whether a source value may target a `values: service` level, and the code if not (referenced by [SRC-33] and [SVC-13]).
- [CTX-2]: whether the `levels` and `axes` members of a context are required, and whether other members are rejected.
- [CTX-11]: error code when a context fails two checks of one validation step with different codes (referenced by [EVAL-19]; step order settled by [EVAL-18]).
