# 2. Decision hierarchy and node admission

This section defines the decision hierarchy: its levels and axes, the singleton root level, the scope declarations, generated node catalogs, the structure of node IDs, and the rules that decide which node IDs a compiler accepts and which an evaluator admits at runtime. It also defines the shape of a context and the order in which context validation runs. It does not define where declarations are written or how files map to scopes (section 1, areas `MANIFEST` and `SRC`), how values and bindings merge (sections 5 and 7), how providers behave (section 6), normalisation of strings (section 4, area `NORM`), or the shape of the `skippedScopes` provenance entry (section 9, area `PROV`).

## 2.1 Levels and axes

**[SCOPE-1]** The `levels` list MUST contain at least one entry. An empty `levels` list is reported as `E_SCOPE_DECLARATION` ([SCOPE-9]).

**[SCOPE-2]** Each entry in `levels` and `axes` MUST be a string. A non-string entry, such as a scalar that the core schema reads as a number or a boolean, is never converted to a string ([SRC-11]). The compiler MUST report it as `E_SCOPE_DECLARATION` ([SCOPE-9]).

**[SCOPE-3]** Each name MUST be unique across the `levels` and `axes` lists taken together. The compiler MUST report a repeated name as `E_SCOPE_DECLARATION` ([SCOPE-4]), including the root level listed in `axes` ([SCOPE-11]).

**[SCOPE-4]** A repeated name under [SCOPE-3] is a malformed hierarchy declaration, and the compiler MUST report it as `E_SCOPE_DECLARATION`. It MUST NOT report it as `E_DUPLICATE_DEFINITION`, which applies to a hierarchy that is defined more than once across presets and the project.

**[SCOPE-5]** The `axes` list is optional. An omitted or empty `axes` list declares no axes.

**[SCOPE-6]** The order of `levels` is the specificity order. The first level is the least specific and the last level is the most specific.

**[SCOPE-7]** Axes have no position in the specificity order. Where the order of axes matters, it is their declared order in `axes`.

**[SCOPE-8]** A level or axis name written in a source MUST be NFC-normalised before the uniqueness check of [SCOPE-3] and the lookups of [SCOPE-46]. A context name is not normalised ([CTX-4]). No character is reserved in a level or axis name. A name that is the empty string, or that contains an unpaired surrogate code point ([NORM-8]), MUST be reported as `E_SCOPE_DECLARATION`.

**[SCOPE-9]** The compiler MUST report `E_SCOPE_DECLARATION` for a malformed hierarchy declaration: a `levels` value that is not a list, an empty `levels` list ([SCOPE-1]), an `axes` value that is not a list, an entry of `levels` or `axes` that is not a string ([SCOPE-2]), an empty name ([SCOPE-8]), an absent `levels` member, or a member of `decision_hierarchy` other than `levels`, `axes` and `scopes` (ADR 0015).

**[SCOPE-48]** A project whose sources declare no `decision_hierarchy` MUST be reported as `E_SCOPE_DECLARATION`, located at the manifest root (path `[]`, line 1), unless the compilation already reports `E_MANIFEST`, `E_PARSE` or `E_SOURCE_MAPPING`. A root level listed in `axes` MUST be reported once, at that `axes` entry ([SCOPE-11]) (ADR 0017).

## 2.2 The root level

**[SCOPE-10]** The first entry of `levels` is the root level. The root level is a singleton. It has no node catalog and exactly one implicit node, and that node has no node ID.

**[SCOPE-11]** The root level MUST NOT be listed in `axes`. If it is, the compiler MUST report `E_SCOPE_DECLARATION`. The root level is then a repeated name under [SCOPE-3], and [SCOPE-4] gives the same code.

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

**[SCOPE-15]** Except as [SCOPE-16] allows, the `scopes` value MUST be a map. If it is not, for example a list or a scalar, the compiler MUST report `E_SCOPE_DECLARATION`.

**[SCOPE-16]** When the hierarchy has no non-root level and no axis, the `scopes` member MAY be absent, `null`, or an empty map. In that case the compiled bundle still records `scopes` as an object ([BUNDLE-29]). When the hierarchy has a non-root level or an axis, an absent or `null` `scopes` member is reported as `E_SCOPE_DECLARATION` under [SCOPE-14] or [SCOPE-15].

**[SCOPE-17]** A scope entry MUST contain `nodes`. If it does not, the compiler MUST report `E_SCOPE_DECLARATION`.

**[SCOPE-18]** Besides `nodes`, a scope entry MAY contain only `on_unknown_node` and `values`. If an entry contains any other property, the compiler MUST report `E_SCOPE_DECLARATION`.

**[SCOPE-19]** The `nodes` value MUST be one of: an inline list of node IDs; an object whose only property is `from`, with a non-empty string path as its value; or the string `open`. Any other value MUST be reported as `E_SCOPE_DECLARATION`.

**[SCOPE-20]** A non-string entry in an inline `nodes` list MUST be reported as `E_SCOPE_DECLARATION`. It is never converted to a string ([SRC-11]). A string entry that breaks [SCOPE-33] or [SCOPE-34] is reported as `E_INVALID_NODE_ID` under [SCOPE-38].

**[SCOPE-21]** A closed catalog with no node IDs is valid. This covers an empty inline list and a generated catalog whose `nodes` array is empty. Every node ID that a source or a context supplies for such a scope is then unlisted, and the rules of [SCOPE-43] and of [CTX-12] to [CTX-14] apply to it.

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

**[SCOPE-28]** A `from` path that is absolute, that resolves outside the project directory, that names a missing file, or that names a directory MUST be reported as `E_SCOPE_DECLARATION`. Symbolic links are resolved before the containment check of [SCOPE-27], so a link whose target lies outside the project directory makes the path outside it. Manifest paths are governed by [MANIFEST-12].

**[SCOPE-29]** A catalog file MUST be a JSON object with exactly one property, `nodes`, whose value is an array of strings.

**[SCOPE-30]** A catalog file that does not match [SCOPE-29] MUST be reported with the code of its defect. A file that is not valid UTF-8, that starts with a byte-order mark, or that is not well-formed JSON is reported as `E_PARSE`. A file that repeats a property name is reported as `E_DUPLICATE_KEY` ([SRC-3]). A file whose top-level value is not an object, that has no `nodes` property, that has a property other than `nodes`, or whose `nodes` value is not an array of strings, is reported as `E_SCOPE_DECLARATION`. Two scopes MAY name the same catalog file.

**[SCOPE-31]** The compiler MUST read each generated catalog and embed its node IDs in the compiled bundle, in the NFC-normalised form of [SCOPE-35]. The bundle MUST NOT reference the catalog file. The bundle layout, including how each scope records its catalog or `open` and its `on_unknown_node` setting, is defined in section 10 ([BUNDLE-29] to [BUNDLE-43]).

**[SCOPE-32]** The order of node IDs in a catalog, whether inline or generated, MUST NOT affect admission, compile checks or evaluation. Section 10 (`DET`) defines the order in which catalogs are stored.

## 2.5 Node IDs

**[SCOPE-33]** A node ID MUST be a non-empty string of at most 128 Unicode code points.

**[SCOPE-34]** A node ID MUST NOT contain a control character in the range U+0000 to U+001F or U+007F to U+009F.

**[SCOPE-35]** The compiler MUST normalise each node ID written in a source to NFC, as the `NORM` area defines. This includes each entry of a generated catalog file and each node ID captured from a file path ([SRC-22], [SRC-24]). A source node ID that is not in NFC is normalised, not rejected.

**[SCOPE-36]** The length check of [SCOPE-33] and the control-character check of [SCOPE-34] are applied to a source node ID after NFC normalisation, as [NORM-2] orders the checks for other strings. Normalisation can change the number of code points in a string.

**[SCOPE-37]** A string that contains an unpaired surrogate is not a valid node ID, as [NORM-8] states. A source node ID of this kind MUST be reported as `E_INVALID_NODE_ID`, and a context node ID of this kind fails [CTX-6].

**[SCOPE-38]** A source node ID that breaks [SCOPE-33] or [SCOPE-34] MUST be reported as `E_INVALID_NODE_ID`. This applies wherever a source writes a node ID: an inline list, a generated catalog, a values entry, a binding, or a node ID captured from a file path ([SRC-22]).

**[SCOPE-39]** A source node ID that is not valid under [SCOPE-33], [SCOPE-34] or [SCOPE-37] MUST be reported as `E_INVALID_NODE_ID`, and the compiler MUST NOT check it for membership under [SCOPE-43]. Such an ID therefore never produces `E_UNKNOWN_NODE`.

**[SCOPE-40]** The engine MUST NOT require any naming pattern for node IDs beyond [SCOPE-33] and [SCOPE-34]. Naming patterns are a lint feature: they are declared in a shared schema file or a preset and are checked, if at all, by lint rules (section 13, area `LINT`; ADR 0013).

**[SCOPE-41]** A node ID that cannot be used as a file name is still a valid node ID. Where its values are written is governed by section 1 (`SRC`).

**[SCOPE-42]** Two node IDs in one catalog MUST NOT be equal after NFC normalisation. If two are, the compiler MUST report `E_DUPLICATE_NODE`.

## 2.6 Compile-time admission

**[SCOPE-43]** For a closed catalog, every node ID that a source value or binding uses MUST be a member of that catalog, compared after NFC normalisation. If an ID is not a member, the compiler MUST report `E_UNKNOWN_NODE`.

**[SCOPE-44]** The check in [SCOPE-43] MUST apply whatever the value of `on_unknown_node`. The setting has no effect at compile time.

**[SCOPE-45]** For an open catalog, the compiler MUST NOT check a source node ID for membership. Any node ID that is valid under [SCOPE-33], [SCOPE-34] and [SCOPE-37] is accepted.

**[SCOPE-46]** A source value or binding that names a level or axis not declared in `levels` or `axes` MUST be reported as `E_UNKNOWN_SCOPE`.

**[SCOPE-47]** A source value MUST NOT target a level declared `values: service`. The compiler MUST report such a value as `E_SOURCE_MAPPING`. Bindings at a service-sourced level are already settled: they are `E_BINDING_NOT_ALLOWED` ([FEAT-19], [BIND-12]).

## 2.7 Context shape

**[CTX-1]** Each of the `levels` and `axes` members that a context contains MUST be a map from strings to strings. If a member is not such a map, or a map entry has a value that is not a string, the evaluator MUST raise `E_CONTEXT_INVALID`. Whether a member may be absent is governed by [CTX-2].

**[CTX-2]** The `levels` and `axes` members of a context are each optional, and an absent member is an empty map. A context MUST NOT have any other member. If it has one, the evaluator MUST raise `E_CONTEXT_INVALID` at step 1 of [CTX-9]. A member that is present but `null` is not absent. It is not a map, so [CTX-1] applies.

**[CTX-3]** A context MUST NOT name the root level, in either map. If it does, the evaluator MUST raise `E_CONTEXT_INVALID`. This clause, not [CTX-4], governs the root level's name.

**[CTX-4]** Each name in `levels`, other than the root level's name, MUST be a declared non-root level. Each name in `axes`, other than the root level's name, MUST be a declared axis. Names are compared under [NORM-6] and are not normalised ([NORM-3]). If a name is not declared in the map that holds it, the evaluator MUST raise `E_UNKNOWN_SCOPE`.

**[CTX-5]** A declared non-root level supplied under `axes`, or a declared axis supplied under `levels`, is not declared in the map that holds it. The evaluator MUST raise `E_UNKNOWN_SCOPE` for it under [CTX-4].

**[CTX-6]** Each node ID value in a context MUST meet [SCOPE-33] and [SCOPE-34], and MUST NOT contain an unpaired surrogate code point ([SCOPE-37]). If it does not, the evaluator MUST raise `E_CONTEXT_INVALID`.

**[CTX-7]** An evaluator MUST NOT check whether a context node ID is in NFC, and MUST NOT raise `E_CONTEXT_INVALID` because it is not. Such an ID is compared as received under [CTX-8]. It is admitted or rejected by the rules of section 2.9 like any other ID.

> **Note (non-normative).** A context node ID that is not in NFC does not equal the NFC catalog member it would normalise to ([NORM-5]). Under `on_unknown_node: error` it raises `E_UNKNOWN_NODE`, under `ignore` its scope is skipped, and in an open catalog it is admitted as received. The NFC rule for node IDs applies to sources only (requirements §4.1, §4.11 item 4; ADR 0011 §3). Checking NFC at runtime needs Unicode data, and runtimes ship different Unicode versions.

**[CTX-8]** Admission MUST compare a context node ID with catalog members by the exact code-point comparison that the `NORM` area defines ([NORM-6]). Admission does not normalise context node IDs.

## 2.8 Context validation order

**[CTX-9]** Context validation MUST run these steps in this order:

1. Shape: [CTX-1] and [CTX-2].
2. Scope names: [CTX-3], [CTX-4] and [CTX-5].
3. Node ID structure: [CTX-6].
4. Admission: [CTX-12], [CTX-13] and [CTX-14].

**[CTX-10]** An evaluator MUST NOT start a value lookup, a binding lookup or a provider call until the context has passed all four steps of [CTX-9].

**[CTX-11]** When step 2 of [CTX-9] finds both a name that [CTX-3] rejects and a name that [CTX-4] or [CTX-5] rejects, the evaluator MUST raise `E_CONTEXT_INVALID`, whatever the order of the map entries. Across steps, the first failing step decides ([EVAL-18]).

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

- (none)
