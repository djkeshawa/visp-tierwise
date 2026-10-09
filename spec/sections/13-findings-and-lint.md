# 13. Findings and lint

This section defines what a validation of a project is and what it reports. It covers the fields of a finding, how a finding's location is written, which findings are the same finding, when a validation fails, and when a suggestion or an explanation is provided. For lint, it lists the lint rules, defines how their severities are resolved, how naming patterns apply, and what each lint rule flags. It does not decide which conditions are engine errors: the clause that owns a condition defines the error and its code, and this section fixes only how the error is reported. Evaluation errors are raised by an evaluator and are outside this section ([FIND-9]).

## 13.1 Validation

The terms *validation*, *engine check*, *engine error*, *lint rule*, *severity*, *engine finding*, *lint finding* and *finding key* are defined in `spec.md` §Terminology.

**[FIND-1]** A validation MUST run every engine check on the project's sources and on the presets it extends.

**[FIND-2]** A validation MUST run every example of the project, as [POL-47] requires, and MUST run each lint rule that [LINT-7] requires to run.

**[FIND-3]** A validation MUST report every finding it determines. It MUST NOT stop at the first finding.

**[FIND-4]** For the same manifest, sources and presets, a validation MUST produce the same set of findings, with the same finding keys ([FIND-24]) and severities, on every run and in every implementation.

**[FIND-5]** A validation MUST fail when at least one of its findings has severity `error`.

**[FIND-6]** A validation whose findings all have severity `warn` or `info`, or that has no findings, MUST NOT fail.

**[FIND-7]** A compiler MUST NOT write a canonical bundle for a project whose validation fails.

**[FIND-8]** A compiler MUST write a canonical bundle (section 10) for a project whose validation does not fail. Which findings a conformance case with a `bundle` expectation tolerates is decided by [VEC-95].

**[FIND-9]** An evaluation operation does not perform a validation. An evaluation error is raised, not reported as a finding ([EVAL-22]).

> **Implementation note.** Requirements §3 names `validate`, `lint` and `test` commands. This specification defines no commands. A tool may split a validation across its commands, but the conformance vectors compare the findings of a whole validation ([VEC-98]).

## 13.2 Finding structure

**[FIND-10]** Every finding MUST have the fields `code`, `severity`, `file`, `path`, `line` and `message`. The wording of `message` is not specified, and conformance vectors do not compare it ([VEC-101]).

**[FIND-11]** A finding MAY also have the field `suggestion` ([FIND-27]).

**[FIND-12]** The `code` of an engine finding MUST be an engine error code of requirements §6.1, the code that the clause defining the check names.

**[FIND-13]** The `code` of a lint finding MUST be the identifier of the lint rule that reported it, as listed in [LINT-1].

**[FIND-14]** A code MUST keep its meaning in every later spec version. A check whose meaning changes MUST use a new code.

**[FIND-15]** The `severity` of an engine finding MUST be `error`. No lint setting changes it ([LINT-9]).

**[FIND-16]** The `severity` of a lint finding MUST be the resolved severity of its rule ([LINT-4]). It is therefore `error`, `warn` or `info`, because a rule resolved to `off` does not run ([LINT-6]).

## 13.3 Locations

**[FIND-17]** For a finding located in a project source, `file` MUST be the path of that source relative to the project's directory, with `/` as the separator. A finding located in the manifest, including its inline content ([MANIFEST-13]), has the `file` `tierwise.yaml`.

**[FIND-18]** The `file` of a finding located in a preset MUST name that preset. A built-in preset is named `tierwise:<name>`. A preset loaded from a relative path ([MANIFEST-23]) is named by its path relative to the project's directory, written as [FIND-17] writes a source path. A preset loaded from a URL is named by that URL. The finding names the preset, not the manifest that extends it. Its message names the `extends` item that brought the preset in.

**[FIND-19]** The `path` of a finding MUST identify exactly one node of the parsed source: the sequence of mapping keys and zero-based sequence positions followed from the root of the document to that node. The root has the empty path. This applies to every source a compiler reads, including JSON catalogs. Aliases ([SRC-2]) and duplicate keys ([SRC-3]) are rejected, so each node has exactly one path. A source whose root is not a mapping, or that holds several documents, is handled by [SRC-6].

**[FIND-20]** The `path` of a finding MUST be a list. A string item is a mapping key, and an integer item is a zero-based sequence position. The root is the empty list. A key item holds the key as the compiler reads it: quotes removed, escapes decoded, and normalised as the section that declares that name requires (for example [SCOPE-35] for node IDs). One form serves findings and compile vectors, because [VEC-98] compares `path` exactly. How a tool prints a path for people is not specified. A dotted string is not a valid path, because key and fact names contain dots.

> **Example (non-normative).** The key `checkout.shipping.allowed_methods` declared under `keys` in `schema/components/checkout.yaml` has the path `[keys, "checkout.shipping.allowed_methods"]`. Its `universe` entry `PICKUP` has the path `[keys, "checkout.shipping.allowed_methods", universe, 1]`.

**[FIND-21]** The `line` of a finding MUST be the 1-based line of the source on which the node named by `path` begins, as [FIND-22] defines. Lines are counted at the line breaks that YAML 1.2 defines: line feed, carriage return, and carriage return followed by line feed, which counts as one break.

**[FIND-22]** A node begins on the line that this clause gives. The root begins on line 1. The value of a mapping entry, including an empty mapping or sequence, begins on the line of its key. A sequence item begins on the line of its `-` indicator in block style, and on the line of its first character in flow style. A finding about a duplicate key uses the line of the second occurrence ([SRC-9]).

**[FIND-23]** A finding that no node of a parsed source names MUST be located as follows. A missing required entry is located at the enclosing mapping, with that mapping's path and line. A source that cannot be parsed is located at the empty path and line 1 of that source. An absent manifest is located at `file` `tierwise.yaml`, the empty path and line 1. A preset reference that cannot be resolved ([MANIFEST-22], [MANIFEST-24], [MANIFEST-27], [MANIFEST-28]) is located at its `extends` item in the manifest.

**[FIND-31]** A finding about the value of a member MUST be located at that member's entry. A finding about the shape of a whole list item MUST be located at that item (ADR 0017).

**[FIND-32]** A finding about a repeated entry, a repeated list element or a conflicting second declaration MUST be located at the later occurrence in document order. Across files, document order is the manifest's inline content first, then source files in code-point order of their paths relative to the project root; every occurrence after the first gets one finding (ADR 0017).

**[FIND-33]** `E_ENTITY_GRAPH` MUST be reported once per cycle, at the declaration entry of the cycle's entity that comes first in document order ([FIND-32]) (ADR 0017).

**[FIND-34]** `E_EXAMPLE_FAILED` MUST be located at the failing example's `expect` entry. `E_DUPLICATE_DEFINITION` for a feature named by two rules documents MUST be located at the `feature` entry of every rules document after the first (ADR 0017).

**[FIND-35]** A key whose `type` or `combine` is missing or invalid MUST receive no strategy, default, kind or value checks beyond that finding. A setting with both or neither of `value_of` and `list_of` MUST receive no check of its `default`. A values file that targets a service-sourced level MUST be reported once, at its level entry (ADR 0017).

## 13.4 Finding keys and order

**[FIND-24]** The **finding key** of a finding is the tuple (`code`, `file`, `path`, `line`). The `severity`, `message` and `suggestion` are not part of it. Conformance vectors compare finding keys ([VEC-98]).

**[FIND-25]** When two checks determine findings with the same finding key, a validation MUST report one finding for that key. Which of their messages it carries is not specified, because messages are not compared ([VEC-101]).

**[FIND-26]** A validation MUST list its findings in this order: by `file` in code-point order; then by `line`; then by `path`, segment by segment, where keys are compared by code point on their decoded text, sequence positions are compared as numbers, and a path that is a prefix of another sorts first; then by `code` in code-point order.

## 13.5 Suggestions and explanations

**[FIND-27]** A finding with one of the codes below SHOULD carry a `suggestion` when a name that the project declares in the matching category is close to the unknown name. How closeness is measured is not specified.

| Code | Candidate names |
| --- | --- |
| `E_UNKNOWN_KEY` | declared keys |
| `E_UNKNOWN_SCOPE` | the root level, declared levels and declared axes |
| `E_UNKNOWN_NODE` | the nodes of the same closed catalog |
| `E_UNKNOWN_FACT` | declared facts |
| `E_UNKNOWN_ENTITY` | declared entities |
| `E_UNKNOWN_SETTING` | the settings of the policy that the binding names |

**[FIND-28]** A `suggestion` MUST be the exact text of a name that the project declares in the matching category of [FIND-27] or, for a finding under [LINT-10] (b), a rule identifier of [LINT-1]. Conformance vectors do not compare suggestions ([VEC-101]), so suggestions are tested outside the conformance suite.

> **Implementation note.** The implementation plan (item 2.7) chooses Damerau-Levenshtein distance over the declared names of the matching category.

> **Example (non-normative).** For a value written for `checkout.shiping.allowed_methods`, an `E_UNKNOWN_KEY` finding may suggest `checkout.shipping.allowed_methods`.

**[FIND-29]** Every engine error code of requirements §6.1, and every lint rule identifier in [LINT-1] other than `naming/<name>`, MUST have an explanation that states the rationale and how to fix the finding. The catalogue of explanations is `spec/error-codes.md` (implementation plan item 1.6). A tool that explains a code, such as `tierwise explain` in requirements §6, MUST print that explanation.

**[FIND-30]** The explanation of a naming rule `naming/<name>` is the `description` declared for its pattern ([LINT-14]). When the pattern has no `description`, the explanation is the explanation of the naming family in `spec/error-codes.md`, followed by the pattern.

## 13.6 Lint rules and severities

**[LINT-1]** The lint rules of spec version 0.1 are exactly these:
- `naming/<name>`, one for each naming pattern ([LINT-12]);
- `values/no-op` ([LINT-23]);
- `rules/dead-rule` ([LINT-26] to [LINT-28]);
- `examples/required-per-binding` ([LINT-29] to [LINT-31]);
- `examples/mixed-collection` ([LINT-32] to [LINT-36]);
- `schema/unused` ([LINT-37] to [LINT-43]);
- `schema/description-required` ([LINT-44], [LINT-45]);
- `schema/owner-required` ([LINT-46] to [LINT-48]);
- `presets/override` ([LINT-49], [LINT-50]).

**[LINT-2]** A compiler MUST NOT run any lint rule that [LINT-1] does not list.

**[LINT-3]** The severity in a lint setting MUST be one of `error`, `warn`, `info` or `off`. A value that is not one of these is reported as [LINT-10] (c) gives.

**[LINT-4]** A compiler MUST resolve the severity of each lint rule once per validation. It applies the lint settings of each preset in the layer order of [MANIFEST-31], and then the manifest's `lint.rules` ([MANIFEST-38]). For each rule, the last setting that names it wins (ADR 0011 §7), where, within one preset or the manifest's `lint.rules`, an exact setting for a naming rule takes precedence over the `naming/*` wildcard ([LINT-10] (a)).

**[LINT-5]** A lint rule that no lint setting names, directly or through the `naming/*` wildcard ([LINT-10] (a)), MUST have severity `off`. A project that extends no preset and sets no lint rule therefore runs no lint rule.

**[LINT-6]** A lint rule whose resolved severity is `off` MUST NOT run and MUST NOT report findings.

**[LINT-7]** A lint rule whose resolved severity is not `off` MUST run, subject to [LINT-11]. Each of its findings MUST carry its resolved severity.

**[LINT-8]** A lint setting whose rule name is an engine error code MUST be reported as `E_PRESET` at the location of the setting, whatever its value. This applies to a setting in a preset and to a setting in the manifest: ADR 0011 §7 gives `E_PRESET` for this case in the composition of presets and the project's own definitions, and requirements §5.1 says no preset can configure an engine error.

**[LINT-9]** A lint setting reported under [LINT-8] MUST have no effect. The engine check it names keeps severity `error` ([FIND-15]).

**[LINT-10]** The grammar of lint settings is as follows.
- (a) The name `naming/*` is a wildcard that sets every naming rule ([LINT-12]). Within one preset or within the manifest's `lint.rules`, a setting for an exact naming rule takes precedence over the wildcard. Across sources, [LINT-4] decides. A wildcard in a project with no naming rule is valid and has no effect.
- (b) A setting whose rule name is neither a rule of [LINT-1] in the project, nor the wildcard of (a), nor an engine error code ([LINT-8]) is invalid. It MUST be reported as `E_MANIFEST` when it is in the manifest, and as `E_PRESET` when it is in a preset ([MANIFEST-36]), at the entry that holds the setting. Its finding SHOULD carry a `suggestion` drawn from the rule identifiers of [LINT-1] ([FIND-28]).
- (c) A severity that is not one of the four values of [LINT-3] is invalid, and is reported with the code that (b) gives.

**[LINT-11]** A validation that has at least one engine finding MUST NOT run any lint rule. Lint rules run only when the validation has no engine finding, so a project with an engine error gets only engine findings.

## 13.7 Naming patterns

**[LINT-12]** Each naming pattern that a project declares, or inherits from a preset it extends, MUST give one lint rule `naming/<name>`, where `<name>` is the name of the pattern.

**[LINT-13]** A project that declares no naming pattern and inherits none MUST NOT perform naming checks (requirements §4.9).

**[LINT-14]** A naming pattern MUST be declared as an entry of a top-level `naming` mapping in a shared schema file ([NS-3]) or in a preset. The keys of the mapping are pattern names. Each entry MUST be a mapping with the members `category` ([LINT-15]) and `pattern` ([LINT-17]). An entry MAY have the member `description` ([FIND-30]) and no other member. A `naming` member that is not a mapping, or an entry that breaks this form, is an invalid declaration: it MUST be reported as `E_PRESET` in a preset ([MANIFEST-36]) and as `E_INVALID_DECLARATION` in a shared schema file. A repeated pattern name is handled by [SRC-3] within one file and by [SRC-43] across the project's own sources. A namespaced file MUST NOT contain `naming` ([NS-4]). A pattern name MUST be non-empty and MUST NOT contain `/`, `*` or white space; a name that breaks this is an invalid declaration under the same codes (ADR 0014).

> **Example (non-normative).** The patterns of requirements §4.9 are declared as follows under [LINT-14].
> ```yaml
> naming:
>   key:            { category: key,        pattern: '^[a-z]+(\.[a-z_]+){2}$' }
>   capability_key: { category: key,        pattern: '\.(allowed|allow_[a-z_]+|allowed_[a-z_]+)$' }
>   enum_value:     { category: enum_value, pattern: '^[A-Z][A-Z0-9_]*$' }
>   node_id:        { category: node_id,    pattern: '^[a-z][a-z0-9_]*$' }
>   feature:        { category: feature,    pattern: '^[a-z]+\.[a-z_]+$' }
> ```

**[LINT-15]** Each naming pattern MUST apply to exactly one category of name: `key`, `enum_value`, `node_id` or `feature`. These are the categories of requirements §4.9. A naming entry whose `category` is none of these is an invalid declaration under [LINT-14].

**[LINT-16]** A kind's `naming` member ([KIND-3]) MUST name one naming pattern, by its name. The pattern MUST be declared in the project or inherited from a preset it extends ([LINT-12]), and MUST have category `key`. Matching a key against the pattern is a lint check, not an engine check ([KIND-12]), and is performed by `naming/<name>` ([LINT-19]). A pattern that at least one kind names applies only to the keys whose `kind` member ([KIND-2]) names one of those kinds. A pattern that no kind names applies to every key. A `naming` member that is not a string, that names no declared pattern, or that names a pattern whose category is not `key` MUST be reported as `E_KIND_DEFINITION`, located at that member.

**[LINT-17]** A pattern MUST be a non-empty string in this portable subset of regular expressions:
- literal code points, other than the metacharacters `\ . [ ] ( ) { } | ? * + ^ $`, each of which is literal only when written with a backslash before it;
- `.`;
- bracket classes `[...]` and negated classes `[^...]`, whose contents are literal code points other than `\ [ ] ^ -`, ranges `x-y` between two such literals, and a backslash before one of `\ [ ] ^ -`; nested classes, `&&` intersections and POSIX classes such as `[:alpha:]` are outside the subset;
- groups `(...)`, whose captures have no meaning;
- alternation `|`;
- the quantifiers `?`, `*`, `+`, `{n}`, `{n,}` and `{n,m}`;
- the anchors `^` and `$`;
- a backslash before one of `\ . [ ] ( ) { } | ? * + ^ $ -`.

Everything else is outside the subset, including lookaround, backreferences, inline flags, lazy and possessive quantifiers, shorthand classes such as `\d`, `\w` and `\s`, Unicode property classes, and a backslash before any other character. A pattern compares code points. An empty string, a string outside the subset, and a malformed pattern (for example an unclosed group or class, or a repeat whose lower bound exceeds its upper bound) are invalid patterns. An invalid pattern MUST be reported as `E_PRESET` when it is declared in a preset ([MANIFEST-36]), and as `E_INVALID_DECLARATION` when it is declared in a shared schema file.

**[LINT-18]** A name fails `naming/<name>` when the pattern matches no part of the name (search). `^` matches only at the start of the name and `$` only at its end, and no line terminator is treated specially. Under this reading every pattern of requirements §4.9 works as written.

> **Implementation note.** In Java, `Matcher.find()` with `$` also matches before a final line terminator. A quoted YAML key can end in a line feed after escape decoding, so an implementation must anchor `$` to the true end of the input (see the note under [TYPE-33]).

**[LINT-19]** The rule `naming/<name>` MUST check these names, according to the category of its pattern:
- `key`: the name of each key declaration, subject to [LINT-16];
- `enum_value`: each entry of the `values` list of each `enum` type that a key or a fact declares ([TYPE-40], [TYPE-41]), and each effect string that a feature's `returns` lists in its enum form ([FEAT-7]);
- `node_id`: each node ID written in the inline or generated catalog of a closed scope. An open scope declares no nodes, so none is checked;
- `feature`: the name of each feature declaration.

**[LINT-20]** `enum_value` covers the enum types that are nested in object fields and list items, because their entries are enum values. It does not cover the entries of a `set` type's `universe` ([TYPE-40], [TYPE-41]), because they are members of a set, not enum values.

**[LINT-21]** A naming finding MUST be located at the mapping entry or sequence item that writes the checked name.

**[LINT-22]** The only structural naming rule is [FACT-1]: a fact is named `<entity>.<attribute>`. A node ID must also satisfy [SCOPE-33] and [SCOPE-34], but that is a validity check, not a naming rule ([SCOPE-40]). No other name is constrained unless a naming pattern of its category applies. No category of [LINT-15] covers entity, fact or attribute names.

## 13.8 Lint rule definitions

### `values/no-op`

**[LINT-23]** `values/no-op` MUST flag each authored value that, after normalisation, equals the identity of its key's strategy ([MERGE-5]). A key whose strategy has no identity, or whose identity is absent, never produces this finding.

**[LINT-24]** A `values/no-op` finding MUST be located at the mapping entry that writes the value.

**[LINT-25]** `values/no-op` MUST NOT flag a value that an engine finding already reports, such as an identity value under [KIND-6]. Because no lint rule runs when an engine finding exists ([LINT-11]), this case does not arise in a validation that runs lint rules.

### `rules/dead-rule`

**[LINT-26]** `rules/dead-rule` MUST flag a rule whose `when` contains an `in`, `all_in` or `any_in` condition whose membership list is a literal empty list. Such a condition never holds ([RULE-34]).

**[LINT-27]** `rules/dead-rule` MUST flag a rule that comes after a rule with an empty `when` in the same policy. A rule with an empty `when` always holds ([RULE-2]), and the hit policy stops at the first rule that holds ([RULE-46], [RULE-47]).

**[LINT-28]** `rules/dead-rule` MUST NOT flag any other rule. A membership list given by a setting reference ([RULE-4]) is not literal and is not considered. A rule gets at most one finding, located at its item in the policy's `rules` list.

### `examples/required-per-binding`

**[LINT-29]** `examples/required-per-binding` MUST flag each binding of a feature that no example of that feature covers. Each binding counts separately: the root binding, and each node entry under a non-root level ([BIND-2], [BIND-3]).

**[LINT-30]** An example covers a binding when that binding is the selected binding ([BIND-17]) for the example's context ([POL-31]).

**[LINT-31]** An `examples/required-per-binding` finding MUST be located at the binding: the root binding's entry, or the node's entry under its level.

### `examples/mixed-collection`

**[LINT-32]** `examples/mixed-collection` MUST flag a policy that has at least one rule with an `all_in` or `any_in` condition, when no example whose selected binding ([BIND-17]) names that policy has a mixed child collection for at least one of those conditions. A policy gets at most one finding.

**[LINT-33]** A child collection is mixed for a condition when it has at least one child whose value of the condition's fact is an element of the membership list, and at least one child whose value of that fact is present and is not an element of the list. An omitted or empty child collection is not mixed ([FACT-18]).

**[LINT-34]** When a condition's membership list is a setting reference, the list used for [LINT-33] is the setting's resolved value for the example's selected binding ([BIND-23]).

**[LINT-35]** `examples/mixed-collection` MUST NOT check a policy that no binding names. No example can select such a policy, and it is reported by `schema/unused` ([LINT-37], [LINT-41]).

**[LINT-36]** An `examples/mixed-collection` finding MUST be located at the policy's entry under `policies`.

### `schema/unused`

**[LINT-37]** `schema/unused` MUST flag each declared key, fact, setting and policy that is not used, as [LINT-38] to [LINT-42] define. It MUST NOT flag any other declaration.

**[LINT-38]** A key is used when at least one values source writes a value for it, or when its `settable_at` lists a service-sourced level ([KEY-6]), whose values a provider supplies. Application code reads keys at runtime, which a compiler cannot see, so a key that application code reads only through its `default` is flagged, and the project can set `schema/unused` to `off`.

**[LINT-39]** A fact is used when the `when` mapping of a rule names it ([RULE-1]), or when a setting declares it with `value_of` or `list_of` ([POL-12]). An example's facts and a fact's `source` are not uses.

**[LINT-40]** A setting is used when a condition of a rule in its own policy refers to it ([RULE-4]). A value that a binding gives the setting ([BIND-8]) is not a use.

**[LINT-41]** A policy is used when at least one binding of its feature names it ([BIND-5]).

**[LINT-42]** A reference counts as a use wherever it is written, including a reference from a declaration that is itself unused. A fact named only by a rule of an unused policy, or only by an unused setting, is therefore used. The rule is not transitive: removing one unused declaration can reveal another.

**[LINT-43]** A `schema/unused` finding MUST be located at the entry that declares the unused name.

### `schema/description-required`

**[LINT-44]** `schema/description-required` MUST flag each key, feature and fact declaration that has no `description` member. The finding MUST be located at the declaration's entry.

**[LINT-45]** A `description` that is empty, or that consists only of ASCII space, tab, carriage return and line feed characters, counts as missing under [LINT-44].

### `schema/owner-required`

**[LINT-46]** `schema/owner-required` MUST NOT report findings unless the manifest's `namespaces` mapping ([NS-1]) has at least one entry.

**[LINT-47]** When `namespaces` has at least one entry, `schema/owner-required` MUST flag each key that has no owner under [LINT-48]. The finding MUST be located at the key's declaration entry.

**[LINT-48]** A key has an owner when it is declared in a namespaced file ([NS-3]) or has an `owner` member ([KEY-3]). A key declared in a shared file without an `owner` member has no owner, and is flagged under [LINT-47]. A key's owner is informational ([KEY-9]).

### `presets/override`

**[LINT-49]** `presets/override` MUST report one finding for each definition of a kind or a naming pattern that replaces an earlier definition of the same name under [MANIFEST-32]. A name defined in n layers of [MANIFEST-31] gives n − 1 findings. Whether a second definition within the project's own sources is a replacement or an `E_DUPLICATE_DEFINITION` conflict is decided by [SRC-43]. Only a replacement produces this finding.

**[LINT-50]** A `presets/override` finding MUST be located at the replacing definition's entry. Its message MUST name the source of the definition it replaces.

> **Example (non-normative).** A manifest extends presets `A`, `B` and `C`, in that order, and each defines the kind `capability`. `presets/override` reports two findings: one at the definition in `B`, naming `A`, and one at the definition in `C`, naming `B`. The definition in `C` is the one in effect.

## Open points
- (none)
