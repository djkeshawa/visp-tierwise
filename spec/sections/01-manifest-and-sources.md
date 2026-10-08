# 1. Manifest, sources and namespaces

This section defines the project manifest `tierwise.yaml`, the YAML rules that apply to every source file, the three source roles (schema, values and rules), the shapes of values files, inline content for single-file projects, how definitions from several sources combine, preset references and their composition order, namespaces, and the shape of the manifest's lint settings. It does not define what a key, scope, node, entity, fact, feature, policy, rule or lint rule means, how lint severities resolve, or how findings are located. Those belong to the sections that own the clause areas, and this section cites them by clause.

The following terms are listed in `spec.md` §Terminology, and the clauses of this section give their precise meaning: *manifest*, *project root*, *source file*, *schema file*, *values file*, *rules file*, *path-mapped entry* and *path-mapped values file*, *root entry* and *root values file*, *multi-node entry* and *multi-node values file*, *glob pattern*, *inline content*, *preset* and *preset reference*, *namespace*, *shared file*, *namespaced file*, *engine error*, *lint rule* and *severity*.

## 1.1 Project and manifest

**[MANIFEST-1]** A project MUST contain a manifest named `tierwise.yaml`. The directory that contains the manifest is the project root. Every relative path that the manifest names is resolved against the project root.

**[MANIFEST-2]** A compiler MUST report `E_MANIFEST` when the project root contains no manifest, or when the root node of the manifest is not a YAML mapping.

**[MANIFEST-3]** The manifest MUST NOT contain any top-level key other than `specVersion`, `extends`, `sources`, `schema`, `values`, `rules`, `namespaces` and `lint`. A compiler MUST report each other top-level key as `E_MANIFEST`.

**[MANIFEST-4]** The manifest MUST contain `specVersion`, and its value MUST be a string. A compiler MUST report `E_MANIFEST` when `specVersion` is absent or is not a string.

> Example (non-normative)
>
> ```yaml
> specVersion: "0.1"    # accepted
> specVersion: 0.1      # rejected: a YAML float under the core schema (see SRC-1)
> ```

**[MANIFEST-5]** A compiler MUST accept a `specVersion` that is equal, code point for code point, to a version the compiler implements, and MUST report `E_MANIFEST` for any other value. This specification defines the version `0.1`. [MANIFEST-8] sets the rule for which versions a compiler accepts.

**[MANIFEST-6]** `specVersion` in the manifest is the only declaration of a project's spec version. A preset and a source file other than the manifest MUST NOT declare a spec version.

**[MANIFEST-7]** A manifest key whose value is null is present, with the value null. A clause of this section that requires a mapping, a list or a string is broken by a null value, and the clause's code applies. An empty list or an empty mapping meets a clause that requires a list or a mapping.

**[MANIFEST-8]** This version defines no version-compatibility rule. A compiler MUST accept only the versions that it implements exactly. A compiler that implements this specification therefore accepts only `0.1`, compared exactly, and rejects `0.1.0` under [MANIFEST-5].

**[MANIFEST-9]** The manifest is a source file. Every rule of section 1.2 applies to it.

## 1.2 YAML source rules

**[SRC-1]** A compiler MUST parse every source file, including the manifest and every preset, as YAML 1.2, and MUST resolve scalars with the core schema (YAML 1.2.2, §10.3). A plain scalar has a non-string type only when its whole text matches a pattern of the core-schema tag resolution table (YAML 1.2.2, §10.3.2). Every other scalar is a string, including every single-quoted, double-quoted and block scalar. In particular, `yes`, `no`, `on` and `off` are strings.

> Example (non-normative): the core-schema plain forms are `null`, `Null`, `NULL`, `~` and the empty scalar (null); `true`, `True`, `TRUE`, `false`, `False`, `FALSE` (boolean); `1`, `-1`, `+1`, `0o17`, `0x1F` (integer); `1.5`, `1e3`, `.5`, `-.inf`, `.nan` (floating point). `"true"`, `'1'`, `0b101`, `1_000` and `y` are strings.

> Implementation note: the Java compiler parses sources with a YAML 1.2 engine's node API (ADR 0012), which supplies the positions that [SRC-8] requires. The JavaScript evaluator compiles no sources in v1.

**[SRC-2]** A compiler MUST report `E_YAML_FEATURE` for an alias node (`*name`) and for a merge key in any source file. A merge key is a mapping key written as the plain scalar `<<`. A quoted `"<<"` is an ordinary string key.

**[SRC-3]** A compiler MUST report `E_DUPLICATE_KEY` when a mapping contains the same key twice. This applies at every depth of every source file, including the manifest.

**[SRC-4]** For [SRC-3], two mapping keys are the same key when both are strings and they are equal after NFC normalisation ([NORM-1], [NORM-6]). [SRC-3] compares string keys only. A key that is not a string in a name position is reported under [SRC-11] instead.

**[SRC-5]** A compiler MUST report a YAML syntax error in the manifest as `E_MANIFEST`, and a YAML syntax error in any other source file or preset as `E_PARSE`.

**[SRC-6]** A compiler MUST report `E_PARSE` for a source file or preset, other than the manifest, that holds more than one YAML document, or whose root node is not a mapping. An empty file has the root node null and is reported in the same way. For the manifest, the same faults are reported as `E_MANIFEST` ([MANIFEST-2]).

**[SRC-7]** A compiler MUST report `E_YAML_FEATURE` for an explicit tag (for example `!!str`, or a local tag) and for an anchor (`&name`), in the manifest, in any source file and in any preset. Such a tag can give a scalar a type outside the core schema.

**[SRC-8]** A finding about a node in a source file MUST be located as section 13 requires ([FIND-17] to [FIND-23]). A finding about a node in a preset is located as [FIND-18] requires.

**[SRC-9]** A compiler MUST report an `E_DUPLICATE_KEY` finding at the second occurrence of the key, in document order. The line of that node is defined by [FIND-21] and [FIND-22].

**[SRC-10]** A compiler MUST read every source file and every preset as UTF-8. A source file or preset that is not valid UTF-8, or that begins with a byte-order mark, MUST be reported as `E_PARSE`. For the manifest, the same faults MUST be reported as `E_MANIFEST`.

**[SRC-11]** Where a name is expected, such as a mapping key, scope name, node ID, key name or feature name, a compiler MUST NOT convert a scalar that the core schema reads as a non-string into its source text. A compiler MUST report such a scalar with the code of the enclosing declaration where one exists, for example `E_SCOPE_DECLARATION` for an entry of a `nodes` list. Otherwise it MUST report `E_TYPE_MISMATCH`. The finding MUST suggest quoting the value.

## 1.3 Source lists and file roles

**[MANIFEST-10]** When present, `sources` MUST be a mapping whose keys are drawn from `schema`, `values` and `rules`, each optional. A compiler MUST report any other value of `sources`, and any other key under it, as `E_MANIFEST`.

**[MANIFEST-11]** When present, `sources.schema` and `sources.rules` MUST each be a list of strings, each string a glob pattern ([SRC-18]), and `sources.values` MUST be a list. A compiler MUST report any other value of these three keys, and any non-string item of `sources.schema` or `sources.rules`, as `E_MANIFEST`. Each item of `sources.values` is checked by [SRC-21].

**[SRC-12]** A file's role is the role of the entry that selects it. A file selected by `sources.schema`, or listed under `namespaces` ([NS-2]), is a schema file. A file selected by `sources.rules` is a rules file. A file selected by a `sources.values` entry is a values file.

**[SRC-13]** A file contributes to the project only when it is selected: by a `sources` entry, or by a `namespaces` entry ([NS-8]). Inline content ([MANIFEST-13]) and presets ([MANIFEST-19]) also contribute. A file that is not selected MUST NOT contribute to the project.

**[SRC-14]** A schema file MUST NOT contain any top-level key other than `decision_hierarchy`, `entity_hierarchy`, `facts`, `kinds`, `naming`, `keys`, `features` and `namespace`. This clause does not define these keys. `decision_hierarchy` belongs to section 2 (SCOPE area), `entity_hierarchy` and `facts` to section 3 (ENTITY and FACT areas), `kinds` and `keys` to section 5 (KIND and KEY areas), `features` to section 7 (FEAT area), `naming` to section 13 (LINT area, [LINT-14]), and `namespace` to [NS-7]. A namespaced file is further restricted by [NS-4].

**[SRC-15]** The top-level keys of a rules file, and the rule that a rules file configures exactly one feature, are defined by section 7 ([POL-1], [POL-2]).

**[SRC-16]** A compiler MUST report each top-level key of a schema file that [SRC-14] does not allow as `E_INVALID_DECLARATION`, except where [NS-4] or [NS-7] requires `E_NAMESPACE_VIOLATION` for that key.

**[SRC-17]** A compiler MUST report the `file` of a values entry, or the `schema` of a `namespaces` entry, as `E_MANIFEST` when that path does not exist or is a directory. A relative preset path that names no file is governed by [MANIFEST-24]. A catalog file is governed by [SCOPE-28].

**[SRC-18]** A glob pattern in `sources.schema` or `sources.rules` is read as follows.

- (a) `/` separates path segments. A pattern is relative to the project root.
- (b) `*` matches any run of characters within one path segment. `**` spans directories.
- (c) Matching is case-sensitive and compares code points.
- (d) Hidden files and hidden directories are included in matches.
- (e) A symbolic link is followed. A match whose resolved location lies outside the project root is reported under [MANIFEST-12].

**[SRC-19]** A glob pattern, and a path pattern of a path-mapped entry ([SRC-22]), that matches no file is not an error and produces no finding. It contributes no file. A file named directly is governed by [SRC-17].

**[SRC-20]** A file may be selected more than once, under these rules.

- (a) A file that entries of two different roles ([SRC-12]) select, for example a schema pattern and a rules pattern, is reported as `E_SOURCE_MAPPING`.
- (b) A file matched by two patterns of the same `sources.schema` or `sources.rules` list is selected once.
- (c) A schema file selected by `sources.schema` and also listed under `namespaces` is selected once, as one namespaced file ([NS-8]).

Values entries are governed by [SRC-34].

**[MANIFEST-12]** A path that the manifest names directly MUST resolve inside the project root. A compiler MUST resolve symbolic links in such a path before it checks containment, and MUST report `E_MANIFEST` for a path that resolves outside the project root. This applies to the `file` of a values entry, the `schema` of a `namespaces` entry, a relative preset path ([MANIFEST-23]), and every file that a glob pattern ([SRC-18]) or a path-mapped pattern ([SRC-22]) selects. A catalog path is governed by [SCOPE-28].

## 1.4 Values files

**[SRC-21]** Each item of `sources.values` MUST be a mapping with exactly one of these shapes, and a compiler MUST report any other item as `E_SOURCE_MAPPING`:

- `{ pattern: <path pattern> }`, a path-mapped entry;
- `{ file: <path>, scope: <root level> }`, a root entry;
- `{ file: <path> }`, a multi-node entry.

**[SRC-22]** A file selected by a path-mapped entry is a path-mapped values file. Each `{scope}` and `{node}` placeholder in the pattern matches a run of characters within one path segment, and the matched text never contains `/`. Pattern text outside the placeholders matches only itself. The text matched by `{scope}` is the scope name, and the text matched by `{node}` is the node ID. Both are strings.

> Example (non-normative): with the pattern `values/{scope}/{node}.yaml`, the file `values/division/south.yaml` is matched with scope `division` and node `south`. `{node}` matches part of the segment `south.yaml`.

**[SRC-23]** A path pattern of a path-mapped entry MUST contain exactly one `{scope}` placeholder and exactly one `{node}` placeholder, in different path segments. It MUST NOT contain any other placeholder, or a glob wildcard (`*`, `?` or `[`). A compiler MUST report a pattern that breaks this as `E_SOURCE_MAPPING`. An empty capture is a match, and its empty name is checked under [SRC-32].

**[SRC-24]** A compiler MUST NFC-normalise the text that a `{scope}` or `{node}` placeholder captures from a file path before it uses that text as a scope name or node ID, so that names from file paths and names from YAML content agree ([NORM-1], [NORM-6]).

**[SRC-25]** The content of a path-mapped values file is a set of `key: value` entries for the scope and node that its path captures.

**[SRC-26]** A file selected by a root entry is a root values file. Its content is a set of `key: value` entries for the root level, which has no node ([SCOPE-13]).

**[SRC-27]** A root entry whose `scope` names a declared level other than the root level ([SCOPE-13]) MUST be reported as `E_SOURCE_MAPPING`. A `scope` that names no declared level is governed by [SRC-32].

**[SRC-28]** A path-mapped pattern whose `{scope}` captures the name of the root level MUST be reported as `E_SOURCE_MAPPING`. Root values are written in a root values file, or in a root entry of a multi-node file.

**[SRC-29]** A file selected by a multi-node entry is a multi-node values file. Its content has the shape `<scope>: { <node>: { key: value } }` for every scope other than the root level.

**[SRC-30]** In a multi-node values file, an entry for the root level has the shape `<root level>: { key: value }`, with no node.

**[SRC-31]** In a multi-node values file, a compiler MUST report as `E_SOURCE_MAPPING` a scope (other than the root level) whose value is not a mapping of nodes, a node whose value is not a mapping of entries, and a root-level entry whose value is not a mapping of entries. A file whose root is not a mapping is governed by [SRC-6].

**[SRC-32]** Every scope name and node ID that a values file supplies, from its path or from its content, MUST be checked by section 2: an undeclared scope by [SCOPE-46], a node ID that breaks the structural contract by [SCOPE-38], and a node ID outside a closed catalog by [SCOPE-43].

**[SRC-33]** A values file that gives values for a level declared `values: service` is reported as `E_SOURCE_MAPPING`, as [SCOPE-47] requires.

**[SRC-34]** A compiler MUST report `E_SOURCE_MAPPING` for a file selected by more than one `sources.values` item.

**[SRC-35]** Every key in a values file is checked against the declared keys by section 5 (KEY area). This section defines only the shape of values files.

## 1.5 Inline content

**[MANIFEST-13]** The manifest MAY hold inline content under the top-level keys `schema`, `values` and `rules`.

**[MANIFEST-14]** Inline `schema` content MUST be a mapping, and a compiler MUST report any other value as `E_MANIFEST`. Its top-level keys are restricted as a schema file's are ([SRC-14], [SRC-16]). It is a shared file ([NS-3]).

**[MANIFEST-15]** Inline `values` content MUST be a mapping with the multi-node shape of [SRC-29] and [SRC-30]. A compiler MUST report a value of inline `values` that is not a mapping as `E_MANIFEST`, and MUST report any other shape violation in it as [SRC-31] requires.

**[MANIFEST-16]** Inline `rules` content MUST be a list of rules documents, with one rules document for each feature. Each rules document has the shape of a rules file ([POL-1], [POL-2]). A compiler MUST report a value of inline `rules` that is not a list as `E_MANIFEST`, and MUST report an item that is not a mapping as `E_MANIFEST`. Two items that configure the same feature are reported as [SRC-44] requires.

**[MANIFEST-17]** A project MAY mix inline content with file sources. Their definitions combine as section 1.6 requires.

**[SRC-36]** A finding about inline content is located in the manifest ([FIND-17]). Because its path starts at the root of the manifest ([FIND-19]), the path begins with the inline top-level key, such as `schema` or `values`.

**[MANIFEST-18]** A manifest with no inline content and no `sources` entries is valid. The sections that own the declarations the project needs check them.

## 1.6 Combining definitions

**[SRC-37]** The project's own definitions are the union of the definitions in every contributing file and every inline content block. That union MUST NOT depend on the order in which the manifest lists patterns or files, or the order in which files are found.

**[SRC-38]** Presets combine with the project's own definitions in the order that [MANIFEST-31] defines. Only kinds, naming patterns ([MANIFEST-32]) and lint severities ([LINT-4]) depend on that order. Every other definition combines as a union under [SRC-39] and [SRC-40].

**[SRC-39]** `decision_hierarchy` and `entity_hierarchy` MUST each be declared at most once across all sources, presets included. A compiler MUST report a second declaration as `E_DUPLICATE_DEFINITION`.

**[SRC-40]** An entity, a fact, a key and a feature MUST each be declared at most once across all sources, presets included. A compiler MUST report a second declaration of the same name as `E_DUPLICATE_DEFINITION`. Sections 3 and 5 state the same rule for their declarations ([ENTITY-3], [FACT-5], [KEY-1]).

**[SRC-41]** A second declaration of a definition that [SRC-39] or [SRC-40] limits to one is a duplicate even when it is identical to the first. A compiler MUST report it as `E_DUPLICATE_DEFINITION`.

**[SRC-42]** Values from every values file and from inline `values` content are checked together. Two values for one key at one scope and node, from any combination of these sources, are reported as [KEY-12] requires (`E_DUPLICATE_VALUE`). This includes a root value given by a root values file and a root value given by a root entry of a multi-node file.

**[SRC-43]** Two kinds, or two naming patterns, with the same name that the project's own sources declare MUST be reported as `E_DUPLICATE_DEFINITION`. [MANIFEST-32] replaces a definition only across layers, and never within the project's own sources.

**[SRC-44]** Two rules documents that name the same feature MUST be reported as `E_DUPLICATE_DEFINITION`, as [POL-5] requires. A rules document is a rules file or an item of inline `rules` ([MANIFEST-16]).

## 1.7 Presets

**[MANIFEST-19]** When present, `extends` MUST be a list. A compiler MUST report any other value as `E_MANIFEST`.

**[MANIFEST-20]** Each item of `extends` MUST be a preset reference in one of the three forms of [MANIFEST-21], [MANIFEST-23] and [MANIFEST-25]. A compiler MUST report any other item as `E_MANIFEST`.

**[MANIFEST-21]** A built-in preset reference is a string `tierwise:<name>`. The built-in names are `recommended`, `minimal` and `entitlements`. This section does not define their contents.

**[MANIFEST-22]** A compiler MUST report `E_PRESET` for a built-in preset reference whose name is not a built-in name.

**[MANIFEST-23]** A path preset reference is a string that does not begin with `tierwise:` and holds a relative path to a preset file. The path is governed by [MANIFEST-12].

**[MANIFEST-24]** A compiler MUST report `E_PRESET` for a path preset reference that names no file.

**[MANIFEST-25]** A URL preset reference is a mapping in the form that [MANIFEST-26] defines. It carries a SHA-256 digest of the preset.

**[MANIFEST-26]** A URL preset reference MUST be a mapping with exactly two keys. `url` holds a string that is an `https` URL. `sha256` holds a string of 64 lowercase hexadecimal digits. The digest is computed over the exact bytes retrieved for the preset. A compiler MUST report a URL preset reference that breaks this shape as `E_MANIFEST` ([MANIFEST-20]).

**[MANIFEST-27]** A compiler MUST report `E_PRESET` for a URL preset reference whose content cannot be retrieved.

> Implementation note: requirements §3.2 forbids networking in `tierwise-core`. Retrieval sits behind an interface that only the CLI connects to the network (implementation plan, item 2.2). A compiler without a retrieval mechanism cannot retrieve the content, so [MANIFEST-27] applies.

**[MANIFEST-28]** A compiler MUST report `E_PRESET` for a URL preset whose retrieved content does not match its digest.

**[MANIFEST-29]** A compiler MUST verify a URL preset's digest before it interprets any of the preset's content. Content whose digest does not match MUST NOT contribute to the project.

**[MANIFEST-30]** A compiler MUST report `E_PRESET` when `extends` names the same preset more than once. A built-in preset is identified by its name, a path preset by its normalised path relative to the project root, and a URL preset by its SHA-256 digest.

**[MANIFEST-31]** A compiler MUST combine presets in the order that `extends` lists them, followed by the project's own definitions ([SRC-37]) as one final layer.

Presets are read only while compiling. Section 10 defines what the bundle contains, and it excludes presets ([BUNDLE-109]).

The table summarises how items combine across presets and the project, following ADR 0011 §7. The cited clauses govern.

| Item | Outcome |
| --- | --- |
| Kinds, naming patterns defined again in a later layer | The later definition replaces the earlier one entirely ([MANIFEST-32], [MANIFEST-33]) |
| Kinds, naming patterns defined twice within the project | `E_DUPLICATE_DEFINITION` ([SRC-43]) |
| Lint severity set more than once | The last setting wins for that rule ([LINT-4]) |
| Facts, entities, hierarchies, keys, features defined more than once | `E_DUPLICATE_DEFINITION` ([SRC-39], [SRC-40]) |
| Lint setting that names an engine error code, in a preset or the manifest | `E_PRESET` ([LINT-8]) |

**[MANIFEST-32]** When a later layer of [MANIFEST-31] defines a kind or a naming pattern with the same name as a definition from an earlier preset, the later definition MUST replace the earlier one entirely.

**[MANIFEST-33]** A compiler MUST NOT merge the fields of a replaced definition into the definition that replaces it.

**[MANIFEST-34]** A replacement under [MANIFEST-32] is not an engine error. It is reported by the lint rule `presets/override` ([LINT-49]) at the severity that [LINT-4] resolves for that rule.

**[MANIFEST-35]** A preset MUST NOT contain an `extends` key. A compiler MUST report a preset that contains one as `E_PRESET`. Composition therefore has one level, and no cycle can occur.

**[MANIFEST-36]** A preset file MUST NOT contain a top-level key other than the keys that [SRC-14] allows in a schema file, and `lint`. A compiler MUST report such a key as `E_PRESET`. Syntax, encoding and root-shape faults of a preset are governed by [SRC-5], [SRC-6] and [SRC-10]. Invalid lint settings in a preset are governed by [LINT-10].

## 1.8 Namespaces

**[NS-1]** When present, `namespaces` MUST be a mapping. Each key is a namespace name. A compiler MUST report any other value as `E_MANIFEST`.

**[NS-2]** Each value of `namespaces` MUST be a mapping whose only key is `schema`, and the value of `schema` MUST be a string holding the path of one schema file. A compiler MUST report any other value as `E_MANIFEST`.

> Example (non-normative)
>
> ```yaml
> namespaces:
>   checkout: { schema: "schema/components/checkout.yaml" }
> ```

**[NS-3]** A schema file listed under `namespaces` is a namespaced file. Every other schema file, including inline `schema` content, is a shared file.

**[NS-4]** A namespaced file MUST NOT contain any top-level key other than `keys`, `features` and `namespace`. A compiler MUST report each other top-level key in a namespaced file, including `decision_hierarchy`, `entity_hierarchy`, `facts`, `kinds` and `naming`, as `E_NAMESPACE_VIOLATION`, and not under [SRC-16]. Shared definitions live only in shared files (ADR 0011 §4).

**[NS-5]** Every key and every feature that a namespaced file declares MUST be in that file's namespace. A compiler MUST report each other key or feature in a namespaced file as `E_NAMESPACE_VIOLATION`. [NS-10] defines what "in the namespace" means.

**[NS-6]** Ownership is not an engine check. A compiler MUST NOT report an engine error because a definition has no owner. What makes a key owned, and the lint rule that checks it, are defined by section 13 ([LINT-46] to [LINT-48]).

**[NS-7]** The name under which `namespaces` lists a schema file is authoritative. A namespaced file MUST contain the `namespace` key, and its value MUST equal that name. A compiler MUST report a namespaced file whose `namespace` key is missing or differs from that name as `E_NAMESPACE_VIOLATION`. A shared file MUST NOT contain the `namespace` key, and a compiler MUST report a shared file that does as `E_NAMESPACE_VIOLATION`.

**[NS-8]** A schema file that a `namespaces` entry lists is selected even when no `sources.schema` pattern selects it. A file that both a pattern and a `namespaces` entry select is one namespaced file ([SRC-20] (c)).

**[NS-9]** Namespace names are unique, as [SRC-3] and [SRC-4] define. A namespace name MUST NOT contain `.`, and a compiler MUST report a namespace name that does as `E_MANIFEST`. Each schema file MUST be listed under one namespace name only, and a compiler MUST report a second listing of the same file as `E_MANIFEST`.

**[NS-10]** A key or feature name in a namespaced file is in that file's namespace when it begins with the namespace name followed by `.`. So `<namespace>.<rest>` is in the namespace `<namespace>`. A shared file MAY declare names that begin with any namespace's prefix.

**[NS-11]** A values file and a rules file do not belong to a namespace. Only a schema file that a `namespaces` entry lists is namespaced ([NS-3]).

## 1.9 Lint configuration

**[MANIFEST-37]** When present, `lint` MUST be a mapping whose only key is `rules`. A compiler MUST report any other value of `lint`, and any other key under it, as `E_MANIFEST`.

**[MANIFEST-38]** When present, `lint.rules` MUST be a mapping from lint rule identifiers to severities. A compiler MUST report any other value of `lint.rules` as `E_MANIFEST`.

**[MANIFEST-39]** The valid severities ([LINT-3]), how severities resolve across presets and the manifest ([LINT-4]), a setting that names an engine error code ([LINT-8]), and the codes for an unknown rule identifier or an invalid severity ([LINT-10]) are defined by section 13.

## Open points

- (none)
