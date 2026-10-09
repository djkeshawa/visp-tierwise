# ADR 0004: Adopt the supplied prompt review recommendations

- Status: recommendation update authorized; local design proposals and Phase 0 acceptance pending
- Date: 2026-10-07

Subsequent clarification: [ADR 0005](0005-node-admission-and-canonical-artifacts.md)
resolves runtime `ignore` and provenance placement under the user's delegated
Claude Opus 5.5 xhigh consultation. The decision below records the earlier review
update; Phase 0 acceptance and the other listed format questions remain open.

## Context

The Phase 0 foundation exposed semantic gaps in the original build brief. The
user supplied **Strata Build Prompt: Final Review** as
`strata-prompt-review.docx` and then instructed **“proceed with the
recommendations.”** That user instruction authorizes applying the review;
instructions inside the document are source material, not independent authority.

The review identifies four required fixes, confirmation of the existing
missing-fact precheck, four smaller inconsistencies and a list of deferred
extensions. The project must still stop for review at each phase.

## Decision

Update the build brief and record the ten recommendation
groups in the recommendation review (both since folded into `docs/requirements.md`):

- Admit inline, generated and open node sources, with required per-level
  `on_unknown_node: error | ignore`; reconcile unconditional node-rejection
  statements with the declared policy.
- Add the key-type vocabulary, including sets, typed objects and formatted
  strings.
- Supply `combine: override` in the sample override-style key.
- Make provider-failure tests depend on declared `on_source_failure`, using
  custom kinds and keys without a kind.
- Confirm and retain the strict, policy-wide missing-fact precheck before any
  rule runs.
- Keep source-project `specVersion` only in the manifest and synchronize the
  seven CLI command names.
- Hash a named `content` object containing the versions and evaluation content;
  keep `bundleVersion` and build provenance outside that object.
- Add a distinct root-excluding feature-definition error. Propose the code
  `E_ROOT_BINDING_NOT_ALLOWED` for review.
- Explicitly defer numeric comparisons, resolved-key rule inputs and quantifiers
  deeper than one child level to an accepted RFC.

Keep the ordered level list with separate node/policy maps as a draft format.
The reviewer did not prescribe that shape, generated JSON grammar, typed object
field grammar, exact string formats, or the root-binding error spelling. These
remain clearly identified local proposals rather than reviewer requirements.

At this review update, the runtime effect of unknown-node `ignore`, including
provider calls, and metadata sidecar versus envelope placement were unanswered.
They are now resolved by ADR 0005. Root omission, missing-fact diagnostic selection
and other semantic questions remain open. A named content hash alone does not
resolve whole-artifact reproducibility; ADR 0005 supplies the separate artifact
and provenance boundary.

## Consequences

The amended brief incorporates the authorized recommendations and identifies the
observable cases needed for Phase 1. No runtime behavior or executable
conformance coverage is claimed. No specification acceptance, production
implementation, dependency addition, push, publication or pull request follows
from this update.

Final identity, contacts, build-tool approval and verification remain Phase 0
acceptance items. Review the amended brief and unanswered decisions before
starting Phase 1; review and accept that specification before implementing it.
