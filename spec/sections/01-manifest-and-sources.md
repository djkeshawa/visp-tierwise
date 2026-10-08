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

**[MANIFEST-5]** A compiler MUST accept a `specVersion` that is equal, code point for code point, to a version the compiler implements, and MUST report `E_MANIFEST` for any other value. This specification defines the version `0.1`. Whether a compiler accepts other versions as well is the open point [MANIFEST-8].

**[MANIFEST-6]** `specVersion` in the manifest is the only declaration of a project's spec version. A preset and a source file other than the manifest MUST NOT declare a spec version.

**[MANIFEST-7]** A manifest key whose value is null is present, with the value null. A clause of this section that requires a mapping, a list or a string is broken by a null value, and the clause's code applies. An empty list or an empty mapping meets a clause that requires a list or a mapping.

**[MANIFEST-8]** *(OPEN)* Which spec versions does a compiler accept besides the versions it implements exactly? Proposed: exact match only, until a version-compatibility rule is accepted. Under this proposal `0.1.0` is rejected.

**[MANIFEST-9]** The manifest is a source file. Every rule of section 1.2 applies to it.

## 1.2 YAML source rules

**[SRC-1]** A compiler MUST parse every source file, including the manifest and every preset, as YAML 1.2, and MUST resolve scalars with the core schema (YAML 1.2.2, §10.3). A plain scalar has a non-string type only when its whole text matches a pattern of the core-schema tag resolution table (YAML 1.2.2, §10.3.2). Every other scalar is a string, including every single-quoted, double-quoted and block scalar. In particular, `yes`, `no`, `on` and `off` are strings.

> Example (non-normative): the core-schema plain forms are `null`, `Null`, `NULL`, `~` and the empty scalar (null); `true`, `True`, `TRUE`, `false`, `False`, `FALSE` (boolean); `1`, `-1`, `+1`, `0o17`, `0x1F` (integer); `1.5`, `1e3`, `.5`, `-.inf`, `.nan` (floating point). `"true"`, `'1'`, `0b101`, `1_000` and `y` are strings.

> Implementation note: the Java compiler parses sources with a YAML 1.2 engine's node API (ADR 0012), which supplies the positions that [SRC-8] requires. The JavaScript evaluator compiles no sources in v1.

**[SRC-2]** A compiler MUST report `E_YAML_FEATURE` for an alias node (`*name`) and for a merge key in any source file. A merge key is a mapping key written as the plain scalar `<<`. A quoted `"<<"` is an ordinary string key.

**[SRC-3]** A compiler MUST report `E_DUPLICATE_KEY` when a mapping contains the same key twice. This applies at every depth of every source file, including the manifest.

**[SRC-4]** *(OPEN)* When are two mapping keys the same key for [SRC-3]? (a) Are string keys compared before or after NFC normalisation (section 4, NORM area)? Proposed: after, so that two spellings of one key count as duplicates. (b) How are non-string keys compared, for example `1` and `01`, or `1` and `"1"`? Proposed: a non-string key in a name position is handled by [SRC-11] first, and [SRC-3] compares only string keys.

**[SRC-5]** *(OPEN)* Which code reports a YAML syntax error? Requirements §6.1 names no code for it. Proposed: `E_MANIFEST` for the manifest, and a new code for other source files.

**[SRC-6]** *(OPEN)* Which code reports a non-manifest source file whose root is not a mapping (an empty file reads as null), or a file that holds more than one YAML document? Requirements §6.1 names no fitting code. Proposed: a new code.

**[SRC-7]** *(OPEN)* Are explicit YAML tags (for example `!!str`, or a local tag) and anchors without aliases allowed? Proposed: reject both with `E_YAML_FEATURE`, because a tag can give a scalar a type outside the core schema.

**[SRC-8]** A finding about a node in a source file MUST be located as section 13 requires ([FIND-17] to [FIND-23]). A finding about a node in a preset is located as [FIND-18] requires.

**[SRC-9]** *(OPEN)* Which occurrence does an `E_DUPLICATE_KEY` finding name? Proposed: the second occurrence in document order. The line of a node in general is defined by [FIND-21] and [FIND-22].

**[SRC-10]** *(OPEN)* Which character encoding do source files use, and is a byte-order mark allowed? YAML 1.2 itself allows UTF-16 and UTF-32 and a byte-order mark. Proposed: UTF-8 only, with a byte-order mark rejected.

**[SRC-11]** *(OPEN)* How is a scalar that the core schema reads as a non-string (for example `true`, `null`, `1` or `1.0`) handled where a name is expected, such as a mapping key, scope name, node ID, key name or feature name? Proposed: report `E_TYPE_MISMATCH` with a hint to quote the value. The compiler must not silently convert it to its source text. ADR 0011 §3 takes the same approach for YAML numbers used as decimals.

## 1.3 Source lists and file roles

**[MANIFEST-10]** When present, `sources` MUST be a mapping whose keys are drawn from `schema`, `values` and `rules`, each optional. A compiler MUST report any other value of `sources`, and any other key under it, as `E_MANIFEST`.

**[MANIFEST-11]** When present, `sources.schema` and `sources.rules` MUST each be a list of strings, each string a glob pattern ([SRC-18]), and `sources.values` MUST be a list. A compiler MUST report any other value of these three keys, and any non-string item of `sources.schema` or `sources.rules`, as `E_MANIFEST`. Each item of `sources.values` is checked by [SRC-21].

**[SRC-12]** A file's role is the role of the entry that selects it. A file selected by `sources.schema`, or listed under `namespaces` ([NS-2]), is a schema file. A file selected by `sources.rules` is a rules file. A file selected by a `sources.values` entry is a values file.

**[SRC-13]** A file contributes to the project only when it is selected: by a `sources` entry, or by a `namespaces` entry when [NS-8] counts that listing as a selection. Inline content ([MANIFEST-13]) and presets ([MANIFEST-19]) also contribute. A file that is not selected MUST NOT contribute to the project.

**[SRC-14]** A schema file MUST NOT contain any top-level key other than `decision_hierarchy`, `entity_hierarchy`, `facts`, `kinds`, `naming`, `keys`, `features` and `namespace`. This clause does not define these keys. `decision_hierarchy` belongs to section 2 (SCOPE area), `entity_hierarchy` and `facts` to section 3 (ENTITY and FACT areas), `kinds` and `keys` to section 5 (KIND and KEY areas), `features` to section 7 (FEAT area), `naming` to section 13 (LINT area, [LINT-14]), and `namespace` to [NS-7]. A namespaced file is further restricted by [NS-4].

**[SRC-15]** The top-level keys of a rules file, and the rule that a rules file configures exactly one feature, are defined by section 7 ([POL-1], [POL-2]).

**[SRC-16]** *(OPEN)* Which code reports a schema file that breaks [SRC-14]? Requirements §6.1 names none. The same gap exists for declaration members ([KEY-5]) and rules files ([FEAT-2]). Proposed: one new code for an unknown field, used by all three.

**[SRC-17]** *(OPEN)* Which code reports a file that the manifest names directly and that does not exist or is a directory: the `file` of a values entry, or the `schema` path of a `namespaces` entry? Proposed: `E_SOURCE_MAPPING` for a values entry and `E_MANIFEST` for a `namespaces` entry. A missing relative preset is `E_PRESET` ([MANIFEST-24]). A missing catalog file is the open point [SCOPE-28].

**[SRC-18]** *(OPEN)* How are glob patterns read: path separator, the meaning of `*` and `**`, hidden files, case sensitivity and symbolic links? Proposed: `/` separates segments, matching is case-sensitive and compares code points, `*` matches within one segment, `**` spans directories, hidden files are included, and a symbolic link that resolves outside the project root is rejected ([MANIFEST-12]).

**[SRC-19]** *(OPEN)* Is a glob pattern that matches no file an error, a finding, or silently empty? Proposed: silently empty, because a file-sourced scope with no files contributes nothing (ADR 0007 §2). A file named directly is governed by [SRC-17].

**[SRC-20]** *(OPEN)* May one file be selected more than once? Proposed: (a) a file selected by entries of two roles, for example by a schema pattern and a rules pattern, is `E_SOURCE_MAPPING`; (b) a file matched by two patterns of the same `sources.schema` or `sources.rules` list is selected once; (c) a schema file selected by `sources.schema` and also listed under `namespaces` is selected once, as one namespaced file ([NS-8]). The requirements §5.1 sample relies on (c): `schema/**/*.yaml` and the `checkout` namespace both select `schema/components/checkout.yaml`. Values entries are governed by [SRC-34].

**[MANIFEST-12]** *(OPEN)* May a path named in the manifest, including a relative preset path and a catalog path (section 2), resolve outside the project root, for example `../shared/x.yaml`? ADR 0007 §3 forbids this for catalogs. Proposed: forbid it for every path, and report `E_MANIFEST`.

## 1.4 Values files

**[SRC-21]** Each item of `sources.values` MUST be a mapping with exactly one of these shapes, and a compiler MUST report any other item as `E_SOURCE_MAPPING`:

- `{ pattern: <path pattern> }`, a path-mapped entry;
- `{ file: <path>, scope: <root level> }`, a root entry;
- `{ file: <path> }`, a multi-node entry.

**[SRC-22]** A file selected by a path-mapped entry is a path-mapped values file. Each `{scope}` and `{node}` placeholder in the pattern matches a run of characters within one path segment, and the matched text never contains `/`. Pattern text outside the placeholders matches only itself. The text matched by `{scope}` is the scope name, and the text matched by `{node}` is the node ID. Both are strings.

> Example (non-normative): with the pattern `values/{scope}/{node}.yaml`, the file `values/division/south.yaml` is matched with scope `division` and node `south`. `{node}` matches part of the segment `south.yaml`.

**[SRC-23]** *(OPEN)* What may a path pattern contain? Proposed: exactly one `{scope}` and one `{node}`, each in its own path segment, no other placeholder, and no glob wildcard. A pattern that breaks this is `E_SOURCE_MAPPING`. An empty capture is a match, and the empty name is then checked by [SRC-32].

**[SRC-24]** *(OPEN)* Should the matched text be NFC-normalised before it is used as a scope name or node ID, because file systems can store names in another normalisation form? Proposed: yes, using the normalisation of section 4 (NORM area), so that names from file paths and from YAML content agree.

**[SRC-25]** The content of a path-mapped values file is a set of `key: value` entries for the scope and node that its path captures.

**[SRC-26]** A file selected by a root entry is a root values file. Its content is a set of `key: value` entries for the root level, which has no node ([SCOPE-13]).

**[SRC-27]** *(OPEN)* What does a root entry mean when its `scope` names a scope other than the root level? Proposed: report `E_SOURCE_MAPPING`.

**[SRC-28]** *(OPEN)* What happens when the `{scope}` of a path-mapped file captures the root level, which has no node? Proposed: report `E_SOURCE_MAPPING`, because root values are written in a root values file or a root entry of a multi-node file.

**[SRC-29]** A file selected by a multi-node entry is a multi-node values file. Its content has the shape `<scope>: { <node>: { key: value } }` for every scope other than the root level.

**[SRC-30]** In a multi-node values file, an entry for the root level has the shape `<root level>: { key: value }`, with no node.

**[SRC-31]** *(OPEN)* Which code reports a values file whose content does not have its shape: in a multi-node file, a scope whose value is not a mapping of nodes, or a node whose value is not a mapping of entries? Proposed: `E_SOURCE_MAPPING`. A file whose root is not a mapping is governed by [SRC-6].

**[SRC-32]** Every scope name and node ID that a values file supplies, from its path or from its content, MUST be checked by section 2: an undeclared scope by [SCOPE-46], a node ID that breaks the structural contract by [SCOPE-38], and a node ID outside a closed catalog by [SCOPE-43].

**[SRC-33]** Whether a values file may give values for a level declared `values: service` is the open point [SCOPE-47] of section 2.

**[SRC-34]** A compiler MUST report `E_SOURCE_MAPPING` for a file selected by more than one `sources.values` item.

**[SRC-35]** Every key in a values file is checked against the declared keys by section 5 (KEY area). This section defines only the shape of values files.

## 1.5 Inline content

**[MANIFEST-13]** The manifest MAY hold inline content under the top-level keys `schema`, `values` and `rules`.

**[MANIFEST-14]** Inline `schema` content MUST be a mapping, and a compiler MUST report any other value as `E_MANIFEST`. Its top-level keys are restricted as a schema file's are ([SRC-14], [SRC-16]). It is a shared file ([NS-3]).

**[MANIFEST-15]** *(OPEN)* What shape does inline `values` content take? Proposed: the multi-node shape of [SRC-29] and [SRC-30], because a single-file project usually has several scopes and nodes.

**[MANIFEST-16]** *(OPEN)* What shape does inline `rules` content take? A rules file configures exactly one feature ([POL-2]), so the choices are one rules document or a list of rules documents. Proposed: a list with one rules document per feature.

**[MANIFEST-17]** A project MAY mix inline content with file sources. Their definitions combine as section 1.6 requires.

**[SRC-36]** A finding about inline content is located in the manifest ([FIND-17]). Because its path starts at the root of the manifest ([FIND-19]), the path begins with the inline top-level key, such as `schema` or `values`.

**[MANIFEST-18]** *(OPEN)* Is a project with no inline content and no `sources` entries valid? Proposed: yes. Any declaration the project needs is then checked by the section that owns it.

## 1.6 Combining definitions

**[SRC-37]** The project's own definitions are the union of the definitions in every contributing file and every inline content block. That union MUST NOT depend on the order in which the manifest lists patterns or files, or the order in which files are found.

**[SRC-38]** Presets combine with the project's own definitions in the order that [MANIFEST-31] defines. Only kinds, naming patterns ([MANIFEST-32]) and lint severities ([LINT-4]) depend on that order. Every other definition combines as a union under [SRC-39] and [SRC-40].

**[SRC-39]** `decision_hierarchy` and `entity_hierarchy` MUST each be declared at most once across all sources, presets included. A compiler MUST report a second declaration as `E_DUPLICATE_DEFINITION`.

**[SRC-40]** An entity, a fact, a key and a feature MUST each be declared at most once across all sources, presets included. A compiler MUST report a second declaration of the same name as `E_DUPLICATE_DEFINITION`. Sections 3 and 5 state the same rule for their declarations ([ENTITY-3], [FACT-5], [KEY-1]).

**[SRC-41]** *(OPEN)* Does a second declaration that is identical to the first also count as a duplicate under [SRC-39] and [SRC-40]? Proposed: yes, so that a copied definition is never silently ignored.

**[SRC-42]** Values from every values file and from inline `values` content are checked together. Two values for one key at one scope and node, from any combination of these sources, are reported as [KEY-12] requires (`E_DUPLICATE_VALUE`). This includes a root value given by a root values file and a root value given by a root entry of a multi-node file.

**[SRC-43]** *(OPEN)* How are a kind or a naming pattern handled when two of the project's own sources declare the same name? [MANIFEST-32] gives replacement only across layers, and [SRC-37] gives no order between project sources. Proposed: `E_DUPLICATE_DEFINITION`. [LINT-49] already assumes this.

**[SRC-44]** Whether two rules files may configure the same feature is the open point [POL-5] of section 7.

## 1.7 Presets

**[MANIFEST-19]** When present, `extends` MUST be a list. A compiler MUST report any other value as `E_MANIFEST`.

**[MANIFEST-20]** Each item of `extends` MUST be a preset reference in one of the three forms of [MANIFEST-21], [MANIFEST-23] and [MANIFEST-25]. A compiler MUST report any other item as `E_MANIFEST`.

**[MANIFEST-21]** A built-in preset reference is a string `tierwise:<name>`. The built-in names are `recommended`, `minimal` and `entitlements`. This section does not define their contents.

**[MANIFEST-22]** A compiler MUST report `E_PRESET` for a built-in preset reference whose name is not a built-in name.

**[MANIFEST-23]** A path preset reference is a string that does not begin with `tierwise:` and holds a relative path to a preset file. Whether the path may resolve outside the project root is the open point [MANIFEST-12].

**[MANIFEST-24]** A compiler MUST report `E_PRESET` for a path preset reference that names no file.

**[MANIFEST-25]** A URL preset reference names a URL and MUST carry a SHA-256 digest of the preset. Its syntax is the open point [MANIFEST-26].

**[MANIFEST-26]** *(OPEN)* What is the syntax of a URL preset reference and its digest, and over which bytes is the digest computed? Proposed: a mapping `{ url: <https URL>, sha256: <64 lowercase hexadecimal digits> }`, with the digest computed over the exact bytes retrieved.

**[MANIFEST-27]** A compiler MUST report `E_PRESET` for a URL preset reference whose content cannot be retrieved.

> Implementation note: requirements §3.2 forbids networking in `tierwise-core`. Retrieval sits behind an interface that only the CLI connects to the network (implementation plan, item 2.2). A compiler without a retrieval mechanism cannot retrieve the content, so [MANIFEST-27] applies.

**[MANIFEST-28]** A compiler MUST report `E_PRESET` for a URL preset whose retrieved content does not match its digest.

**[MANIFEST-29]** A compiler MUST verify a URL preset's digest before it interprets any of the preset's content. Content whose digest does not match MUST NOT contribute to the project.

**[MANIFEST-30]** *(OPEN)* What happens when `extends` names one preset twice? Proposed: `E_PRESET`, where a built-in preset is identified by its name, a path preset by its normalised path relative to the project root, and a URL preset by its digest. Applying a preset twice would otherwise make each of its semantic definitions a duplicate under [SRC-39] and [SRC-40].

**[MANIFEST-31]** A compiler MUST combine presets in the order that `extends` lists them, followed by the project's own definitions ([SRC-37]) as one final layer.

Presets are read only while compiling. Section 10 defines what the bundle contains, and it excludes presets ([BUNDLE-109]).

The table summarises how items combine across presets and the project, following ADR 0011 §7. The cited clauses govern.

| Item | Outcome |
| --- | --- |
| Kinds, naming patterns defined again in a later layer | The later definition replaces the earlier one entirely ([MANIFEST-32], [MANIFEST-33]) |
| Kinds, naming patterns defined twice within the project | Open point [SRC-43] |
| Lint severity set more than once | The last setting wins for that rule ([LINT-4]) |
| Facts, entities, hierarchies, keys, features defined more than once | `E_DUPLICATE_DEFINITION` ([SRC-39], [SRC-40]) |
| Lint setting that names an engine error code, in a preset or the manifest | `E_PRESET` ([LINT-8]) |

**[MANIFEST-32]** When a later layer of [MANIFEST-31] defines a kind or a naming pattern with the same name as a definition from an earlier preset, the later definition MUST replace the earlier one entirely.

**[MANIFEST-33]** A compiler MUST NOT merge the fields of a replaced definition into the definition that replaces it.

**[MANIFEST-34]** A replacement under [MANIFEST-32] is not an engine error. It is reported by the lint rule `presets/override` ([LINT-49]) at the severity that [LINT-4] resolves for that rule.

**[MANIFEST-35]** *(OPEN)* May a preset use `extends` itself? Proposed: no, so that composition has one level and no cycles can occur.

**[MANIFEST-36]** *(OPEN)* Which top-level keys may a preset file contain, and which code applies when a preset file is structurally invalid? Proposed: the keys listed in [SRC-14] plus `lint`, and `E_PRESET`. Invalid lint settings in a preset are governed by [LINT-10].

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

**[NS-4]** A namespaced file MUST NOT contain any top-level key other than `keys`, `features` and `namespace`. A compiler MUST report each other top-level key in a namespaced file, including `decision_hierarchy`, `entity_hierarchy`, `facts`, `kinds` and `naming`, as `E_NAMESPACE_VIOLATION`. Shared definitions live only in shared files (ADR 0011 §4).

**[NS-5]** Every key and every feature that a namespaced file declares MUST be in that file's namespace. A compiler MUST report each other key or feature in a namespaced file as `E_NAMESPACE_VIOLATION`. What "in the namespace" means is the open point [NS-10].

**[NS-6]** Ownership is not an engine check. A compiler MUST NOT report an engine error because a definition has no owner. What makes a key owned, and the lint rule that checks it, are defined by section 13 ([LINT-46] to [LINT-48]).

**[NS-7]** *(OPEN)* Must a namespaced file contain the `namespace` key, and must its value equal the name under which `namespaces` lists the file? May a shared file contain `namespace`? Proposed: the name in `namespaces` is authoritative. A missing or different `namespace` value in a namespaced file is `E_NAMESPACE_VIOLATION`. A shared file that contains `namespace` is `E_NAMESPACE_VIOLATION`, which narrows [SRC-14] for shared files.

**[NS-8]** *(OPEN)* Does a file listed under `namespaces` count as selected when no `sources.schema` pattern selects it? Proposed: yes, and a file selected both ways is one namespaced file, as [SRC-20] (c) proposes.

**[NS-9]** *(OPEN)* Which names are valid namespace names, and may one schema file be listed under two names? Proposed: names are unique, contain no `.`, and each file is listed under one name only.

**[NS-10]** *(OPEN)* What does "in that file's namespace" mean in [NS-5]? Proposed: the key or feature name begins with the namespace name followed by a dot, so that `<namespace>.<rest>` is in the namespace `<namespace>`. May a shared file declare names that begin with a namespace's prefix? Proposed: yes.

**[NS-11]** *(OPEN)* May a values file or a rules file belong to a namespace? The manifest maps only schema files to namespaces. Proposed: no.

## 1.9 Lint configuration

**[MANIFEST-37]** When present, `lint` MUST be a mapping whose only key is `rules`. A compiler MUST report any other value of `lint`, and any other key under it, as `E_MANIFEST`.

**[MANIFEST-38]** When present, `lint.rules` MUST be a mapping from lint rule identifiers to severities. A compiler MUST report any other value of `lint.rules` as `E_MANIFEST`.

**[MANIFEST-39]** The valid severities ([LINT-3]), how severities resolve across presets and the manifest ([LINT-4]), a setting that names an engine error code ([LINT-8]), and the codes for an unknown rule identifier or an invalid severity ([LINT-10]) are defined by section 13.

## Open points

- [MANIFEST-8]: which spec versions are accepted beyond the exact match.
- [MANIFEST-12]: whether paths in the manifest may leave the project root.
- [MANIFEST-15]: shape of inline `values` content.
- [MANIFEST-16]: shape of inline `rules` content.
- [MANIFEST-18]: whether a project with no sources and no inline content is valid.
- [MANIFEST-26]: syntax of a URL preset reference and the bytes the digest covers.
- [MANIFEST-30]: a preset named twice in `extends`, and preset identity.
- [MANIFEST-35]: whether a preset may use `extends`.
- [MANIFEST-36]: top-level keys of a preset file, and the code for an invalid preset file.
- [SRC-4]: key identity for duplicate-key detection (NFC, non-string keys).
- [SRC-5]: code for a YAML syntax error in a source file.
- [SRC-6]: code for a non-manifest source file with a non-mapping root or several documents.
- [SRC-7]: explicit tags and anchors without aliases.
- [SRC-9]: which occurrence an `E_DUPLICATE_KEY` finding names.
- [SRC-10]: encoding and byte-order mark of source files.
- [SRC-11]: non-string scalars in name positions, and the code for them.
- [SRC-16]: code for an unknown top-level key in a schema file.
- [SRC-17]: code for a directly named file that does not exist.
- [SRC-18]: glob dialect.
- [SRC-19]: effect of a glob pattern that matches no file.
- [SRC-20]: one file selected more than once.
- [SRC-23]: what a values path pattern may contain, and empty captures.
- [SRC-24]: NFC normalisation of text captured from file paths.
- [SRC-27]: root entry whose `scope` names a scope other than the root level.
- [SRC-28]: path-mapped pattern whose `{scope}` captures the root level.
- [SRC-31]: code for a values file whose content does not have its shape.
- [SRC-41]: identical duplicate declarations.
- [SRC-43]: kinds and naming patterns declared twice within the project.
- [NS-7]: the `namespace` key in namespaced and shared files.
- [NS-8]: selection of a file listed under `namespaces` that no `sources.schema` pattern selects.
- [NS-9]: valid namespace names, and one file under two names.
- [NS-10]: meaning of "in that file's namespace".
- [NS-11]: whether values and rules files may belong to a namespace.
