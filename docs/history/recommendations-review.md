# Prompt review recommendations

Status: recommendations applied to the build brief; Phase 0 acceptance pending.

The source is **Strata Build Prompt: Final Review**, supplied as
`strata-prompt-review.docx`. Authorization comes from the user's instruction,
**“proceed with the recommendations”**, not from instructions inside the review
document. The original supplied build prompt remains the reference for the
section numbers below. [The amended build brief](build-brief-2026-10-07.md) records the
changes without starting a language-neutral specification or runtime engine.

The subsequent user-authorized Claude Opus 5.5 xhigh consultation resolved runtime
`ignore` and provenance placement. [ADR 0005](../adr/0005-node-admission-and-canonical-artifacts.md)
and the [consultation review](claude-consultation.md) record the assessed decisions.

## Recommendation coverage

| Recommendation | Original sections | Adoption in the amended brief |
|---|---|---|
| Node sources and unknown-node policy | 4.1, 5.2; rejection claims in 1.4, 6.1 and 12 | Admit an inline node list, `nodes: { from: "generated/sites.json" }`, or `nodes: open`; require a declared `on_unknown_node: error \| ignore` per level. ADR 0005 resolves runtime `ignore` as complete skipping of only that scope, with ordered provenance and no provider call; source references remain strict. |
| Key value types | 4.2, 4.10, 5.3 | Define the vocabulary: boolean, string, integer, decimal, enum, set implied by `universe`, object with a field schema, and string formats such as `time_hhmm` and `iana_tz`. Field and format grammar remains proposed until reviewed. |
| Missing sample strategy | 5.3 | Add `combine: override` to the sample `checkout.pickup.lead_hours` key. A kind allowing multiple strategies does not supply an implicit strategy. |
| Provider tests depend on kinds | 12; genericity in 1.4 | Test each key's declared `on_source_failure` behavior instead of assuming behavior from the names `capability` and `configuration`. |
| Strict missing-fact precheck | 4.6, step 6 | Confirm and retain the existing precheck: require every fact referenced anywhere in the selected policy before running any rule, even if a rule could match without the missing fact. |
| Duplicate source-project spec version | 5.0, 5.2 | Keep source-project `specVersion` only in `strata.yaml`. Bundle and conformance version fields remain separate uses. |
| CLI list disagreement | 1.3, 3.2, 11 | Use the same seven commands: `init`, `validate`, `lint`, `test`, `compile`, `codegen`, and `explain`. |
| Vague hash scope | 4.10, step 1; 5.6; determinism in 13 | Hash UTF-8 JCS `content`, including versions and all evaluation content. ADR 0005 makes the artifact exactly canonical `{ bundleVersion, content }` and puts variable provenance in separate records bound to the version, preserving complete artifact reproducibility. |
| Root excluded from binding scopes | 4.5, 6.1 | Reject a feature definition whose `binding_settable_at` omits the first hierarchy level. Choose `E_ROOT_BINDING_NOT_ALLOWED` as the provisional review code, distinct from `E_ROOT_BINDING_MISSING` for an absent binding. |
| Likely future extensions | 10 | Explicitly defer numeric comparisons, rules reading resolved keys, and quantifiers deeper than one child level to an accepted RFC. |

The root-binding code spelling is a local proposal for review: the reviewer
required a distinct error but did not supply its name. The node-policy maps,
generated-file format, and type-schema details are also local design proposals;
they are not represented as requirements prescribed by the reviewer.

## Decisions still needed within these changes

- **Node declarations:** preserve the ordered `levels` list and use maps for node
  sources and policies as a draft representation. The review does not prescribe
  this representation or settle equivalent declarations for axes. The generated
  JSON shape, relative-path base, duplicate handling, normalization and embedding
  of generated IDs in a bundle need a defined format.
- **Unknown-node handling, resolved approach:** `ignore` skips values, bindings
  and provider calls for only that scope; record ordered `skippedScopes`. Validate
  the whole context before provider calls and keep source membership strict. The
  exact ID/normalization contract and remaining declarations are Phase 1 work.
- **Root behavior:** the root's identity and implicit inclusion when omitted
  from context remain open. Requiring the root in `binding_settable_at` does not
  resolve this separate issue.
- **Type grammar:** exact type declarations, strategy-based inference, set
  element types, object field schemas, partial deep-merge values, arrays, null,
  unknown fields, nested values and format validation need review. The key-type
  vocabulary alone does not choose a JSON Schema dialect or a time-zone database.
- **Missing-fact diagnostics:** the strict precheck is confirmed; the treatment
  of an omitted child collection and the selected reason when multiple facts
  are missing remain open. No child is missing a fact merely because a supplied
  collection contains zero children, but whether an omitted collection means
  empty or missing has not been chosen.
- **Bundle metadata, resolved approach:** variable provenance stays outside the
  canonical two-field bundle in sidecars/publication records carrying the version.
  Multiple records may refer to identical content. Record schema, transport and
  timestamp semantics remain Phase 1 work; the whole-artifact guarantee is retained.

Other Phase 0 items remain: final project identity and ownership, maintainers and
private contacts, dependency/tooling approval, complete empty-build verification,
hosted CI and the remaining [semantic questions](open-questions-2026-10-07.md). See the
[Phase 0 review](phase-0-review.md) before authorizing Phase 1.

## Observable checks for Phase 1

This is a test inventory, not executable vectors or evidence of runtime behavior.
Exact outcomes that depend on open decisions must be settled before vectors are
written and accepted.

| Case | Required observable |
|---|---|
| Closed node list, unknown ID, policy `error` | Reject with existing `E_UNKNOWN_NODE`; preserve declared-name rejection separately. |
| Closed node list, unknown ID, policy `ignore` | Skip only that scope; no values/bindings/provider calls; retain independent scopes and ordered `skippedScopes`. See the additional cases in the consultation review. |
| Open node source | Accept an otherwise legal newly supplied ID without requiring it in a compiled list. |
| Generated node source | A generated list and the same inline list yield equivalent known-node behavior; invalid generated files produce defined diagnostics. |
| Invalid strategy/type pair | Reject with existing `E_STRATEGY_TYPE`; wrong value types remain `E_TYPE_MISMATCH`. Cover scalar, set and object vocabulary. |
| Strict missing-fact precheck | A fact used only in a later rule still triggers `if_fact_missing` before an earlier matching rule. Reordering rules preserves the outcome and the reviewed missing-fact reason. |
| Provider failure with a custom kind or no kind | Declared `skip` and `deny` produce the respective behavior regardless of kind names. |
| Content hash and tampering | Equivalent content yields the same version and canonical bundle bytes; changing hashed content invalidates the version. External provenance records do not change bundle bytes and must refer to the version they describe. |
| Root excluded on a feature definition | Report provisional `E_ROOT_BINDING_NOT_ALLOWED`; a permitted root with no actual binding remains `E_ROOT_BINDING_MISSING`. |
| Deferred operators and references | Reject unsupported numeric comparison, resolved-key rule input and deep quantifier syntax. Existing `E_DEPTH_UNSUPPORTED` applies to unsupported depth; other diagnostic codes have not been invented. |

No production implementation, dependency addition, Phase 1 acceptance, push,
publication or pull request is included in this recommendation update.

## Verification of this update

- Parsed all 20 JSON/YAML source, configuration and workflow files with existing
  tooling. The two new commerce excerpts parse; strategy/type declarations and
  removal of the duplicate source version were checked.
- Checked coverage of all ten recommendation groups and every local Markdown
  link. The amended brief was compared with the original for unintended changes.
- `python3 scripts/check-foundation.py` and `pnpm --dir js run check` passed.
- Compared all Java/JS package, source, dependency and build files with the prior
  delivered archive: they are unchanged. No new compilation or broader runtime
  testing is warranted by this source/document update.

These checks are not Strata source validation or evaluator conformance. No
compiler, schema validator or runtime exists yet. Earlier Maven, strict Java
release, hosted CI and reproducible-toolchain limitations remain as recorded in
the Phase 0 review.
