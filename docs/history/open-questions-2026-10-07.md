# Questions before specification v0.1

These questions were found while inspecting the supplied brief. The user has
authorized applying the DOCX review recommendations; see the
[amended build brief](build-brief-2026-10-07.md) and [coverage record](recommendations-review.md).
The adopted clarifications below are separated from decisions still needed
before Phase 1. Changes to an accepted spec subsequently need an RFC. The
numbers below refer to sections of the brief; original question IDs are retained.

The user subsequently delegated technical clarification to Claude Opus 5.5 at
xhigh effort, followed by Codex's assessment. The completed consultation resolved
runtime `ignore` and provenance placement; see [ADR 0005](../adr/0005-node-admission-and-canonical-artifacts.md).
Future technical questions use that process. Ownership, dependencies and phase
acceptance still require the user's authorization.

## Adopted review clarifications

- Node sources may be inline, generated, or open. Per-level
  `on_unknown_node: error | ignore` is required; no hidden default is allowed.
- Runtime `ignore` skips only the unknown supplied scope, including its values,
  bindings and provider calls. Validate the full context before provider calls;
  preserve other supplied scopes and ordered `skippedScopes`. Compile-time
  closed-catalog references remain strict under both runtime policies.
- The key-type vocabulary includes scalars, universe-derived sets, objects with
  a field schema, and formatted strings. The sample now declares `combine:
  override` explicitly and types its boolean/integer keys.
- Provider tests follow the declared `on_source_failure`; they do not depend
  on preset kind names. The strict policy-wide missing-fact precheck is retained
  and explained, including facts used only by a later nonwinning rule.
- Source-project `specVersion` belongs only in the manifest. The CLI inventory
  consistently names all seven commands.
- `bundleVersion` hashes JCS bytes of the named `content` object, including
  format/spec versions and all evaluation-relevant schema/values/rules. The
  version itself and provenance metadata are outside the hashed object.
- The canonical bundle artifact has exactly `bundleVersion` and `content`.
  Variable provenance belongs in separate sidecars/publication records carrying
  the version, preserving complete bundle-byte reproducibility.
- Feature definitions must permit root-level bindings; the proposed distinct
  code is `E_ROOT_BINDING_NOT_ALLOWED`. Numeric comparisons, resolved-key rule
  inputs and deeper quantifiers are explicitly deferred to RFCs.

## Project identity and operations

- What final project name, npm scope, Maven groupId and GitHub organisation will
  own the project? `Strata`, `@strata` and `org.strata` are unclaimed placeholders.
- Who are the initial maintainers and the private security/conduct contacts?
- Is a pinned TypeScript development compiler and pinned Maven lifecycle tooling
  approved? No application or development dependency has been added.
- Confirm or override the Section 14 defaults: DCO, maintainer/RFC governance,
  MkDocs Material, Java 17 core/Java 21 integrations, S3/filesystem storage,
  30-second refresh and 5-minute staleness alert.

## Semantics requiring decisions

1. **Root and node-source details (4.1, 5.2, 6.1), partly resolved.** Inline,
   generated and open membership are adopted. Is the root an implicit singleton
   always included, or a normal context node skipped when omitted? Which sources
   should the sample use for root/service scopes? Confirm the ordered lists with
   separate node/policy maps, matching axis declarations, generated JSON shape,
   path base, duplicate handling, normalization and bundle embedding. Runtime
   `ignore` is resolved by ADR 0005: skip only that scope, with no value/binding
   lookup or provider call, and record ordered admission provenance. No eligible
   feature binding is an evaluation error; its diagnostic/API shape remains
   specification work. Root identity/omission has not been chosen; any inclusion
   rule must preserve explicit skipping of an unknown supplied root.
2. **Required strategies (4.2, 5.3), resolved.** The sample key now declares
   `combine: override`. A kind allowing multiple strategies does not provide an
   implicit strategy. This is corrected in both the amended brief and source
   excerpt; it is not an engine implementation claim.
3. **Multiple axes (4.2).** Are axes folded in declared order or resolved together
   before the hierarchy/axis combination? Which wins under multiple `axis_wins`
   contributions? Are axis `min`/`max` combiners limited to integer keys?
4. **Deep merge (4.2).** Are nested objects merged recursively? Does the default
   seed a partial merge, or apply only when all scopes are unset? Is `null` a
   replacement, a deletion, or invalid?
5. **Defaults and absence (4.2, 4.7).** Are defaults on commutative strategies
   allowed, rejected or ignored? How is unset `min`/`max` represented in both
   APIs and JSON: an omitted field, null, or a presence indicator?
6. **Denial for integer minima (4.3, 4.10).** Is `min` nonnegative? Synthetic
   denial `0` is not the most restrictive value when negatives are allowed,
   and it can violate a positive declared minimum.
7. **Narrowing and no-ops (4.2, 6.2).** Does `narrowing_only` mean a value can
   narrow the identity, or must it narrow the actual inherited result? How are
   the full universe and redundant restrictive subsets treated?
8. **Provider failure (4.3, 7, 12).** What happens for an absent provider,
   omitted key, timeout, or unknown returned key? Does a bad entry fail only
   that key or the whole provider response? Where is timeout configured?
9. **Entity graph (4.1, 4.4, 4.6).** Does `has_many` create an inverse ancestor
   relationship? Can entities have multiple ancestors or children? Are cycles
   and incompatible relationships rejected, and with which stable codes?
10. **Missing child inputs (4.4, 4.6).** Is an omitted collection empty or
    missing? When multiple facts are missing, which fact names the reason?
    Selecting the first rule reference can make the reason depend on rule order.
11. **Condition settings (4.5, 4.6).** Can `$setting` appear in equality and
    `in`, as well as quantifiers? Are setting defaults mandatory? Are an empty
    condition map and an empty membership list legal?
12. **Effects (4.5).** Does `NONE` mean JSON null or a special literal? Which
    object-schema dialect applies to returns, including unknown fields and
    nested values? Can a returned enum also contain a value spelled `NONE`?
13. **Service-level bindings (4.3, 4.5).** Can service-sourced levels supply
    bindings? If yes, where do they come from? The provider contract currently
    returns only key values.
14. **Hashing and reproducibility (4.10, 5.6, 13), resolved approach.** Hash JCS
    `content` and serialize exactly `{ bundleVersion, content }` as the canonical
    bundle. Keep `gitCommit`/`createdAt`/publisher in separate records bound to
    `bundleVersion`; preserve multiple records for the same version. ADR 0005
    retains the whole-artifact guarantee. Sidecar naming/schema, timestamp
    semantics and server upload/record representation remain format work.
15. **Runtime normalization (4.4, 4.10).** Must caller strings already be NFC,
    or does evaluation normalize them? What exact canonical decimal grammar and
    normalization applies? Are numeric decimal inputs accepted?
16. **Contribution effects (4.7).** Are effects relative to a running result or
    assessed independently? Do repeated vetoes remain `VETO`? How does a failure
    show both `INVALID`/`UNAVAILABLE` and its synthetic denial contribution?
17. **Shared facts and namespace files (4.4, 4.9).** May namespaced files add
    facts to the shared model? Facts never live in namespaces in 4.4, while 4.9
    permits namespaced files to extend shared facts.
18. **Source/preset composition (5.0).** What is the complete single-file
    format? How do root files map to scope/node identities? Do later presets
    replace definitions or merge their fields? Which conflicts are errors?

19. **Key-type grammar (4.2, 4.10), newly exposed by the review.** Should every
    non-set key require an explicit `type`? Define enum/set element typing,
    typed object fields, partial deep-merge objects, arrays/null/unknown fields,
    exact `time_hhmm`/`iana_tz` validation and any pinned timezone data. The
    adopted vocabulary does not silently select a schema dialect or validator.

Answers need accompanying rationale and shared conformance cases in Phase 1.
The definite sample corrections have been applied. Unanswered format/runtime
choices remain proposals; no answer is inferred from elapsed time or a document
instruction. Selected decisions derive from the user's delegated consultation.
No specification acceptance or runtime implementation is claimed.
