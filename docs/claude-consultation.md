# Claude consultation and selected decisions

Claude Opus 5.5 was consulted at xhigh effort on 7 October 2026. The CLI selected
`claude-opus-5-5`; the successful response confirmed that model. The user delegated
technical clarification and selection of the best approach. Codex applied the
assessed decisions to the build brief; [ADR 0005](adr/0005-node-admission-and-canonical-artifacts.md)
records the rationale, alternatives and consultation boundary.

| Question | Selected approach |
|---|---|
| Runtime closed-catalog unknown ID with `ignore` | Skip only that supplied scope: no values, binding lookup or provider call; retain independent scopes and ordered `skippedScopes`. |
| Compile-time closed-catalog references | Reject unknown source value/binding IDs under either runtime policy. |
| Context validation | Validate the entire context before any provider call; structural/type errors cannot be ignored. |
| Canonical bundle bytes | Exactly UTF-8 JCS of `{ bundleVersion, content }`; hash JCS `content` alone. |
| Variable provenance | Separate sidecar/publication records carrying `bundleVersion`; several records may describe the same content version. |

Claude's descendant-pruning suggestion was rejected because Strata infers no
parent relationships. Its alternate digest name and extra channel/signature
contract were not adopted. Existing proposed API names and endpoints are retained.
Root omission, source container grammar and provenance transport remain explicit
specification questions; no implicit root or publisher trust mechanism was added.

## Checks required in Phase 1

These are required observable cases, not executable tests or evidence that an
evaluator exists.

| Case | Required observable |
|---|---|
| Ignored non-root scope versus omitted scope | Same resolved value/decision, selected binding and actual contributions, apart from `skippedScopes`. |
| Explicitly ignored unknown root | No later implicit-root rule reinserts that scope; remaining eligible bindings alone determine whether selection succeeds. |
| Ignored service-sourced scope | Zero calls for that scope; neither `skip` nor `deny` source-failure handling runs. |
| Independent later level or axis | Remains eligible after another scope is ignored. |
| Invalid scope later in context | Reject before any provider calls, including earlier eligible scopes. |
| Wrong ID type or unknown scope name with `ignore` | Reject; only otherwise valid unlisted IDs qualify for skipping. |
| Unknown source value/binding ID | `E_UNKNOWN_NODE` for closed catalogs under both runtime policies. |
| Open catalog with a new valid ID | Admit it and invoke configured providers normally. |
| Multiple ignored scopes | Deterministic level-then-axis `skippedScopes`; no fabricated value contributions. |
| No eligible feature binding | Explicit evaluation error; no unrelated policy default or synthetic root. |
| Same content, different build metadata | Identical digest and complete canonical bundle bytes; separate provenance records may differ. |
| Duplicate JSON properties or extra envelope metadata | Reject rather than silently accepting noncanonical input. |
| Changed content with an old digest | Reject integrity verification. |
| Separate record with a different bundle reference | Do not attach it to the selected bundle; linkage validation is distinct from publisher authentication. |
| Multiple publications of one version | Preserve separate records and the same stored bundle bytes. |

## Current status

The brief, ADRs, question statuses and future consultation instructions are
updated. Source/configuration parsing, local documentation links and foundation
checks are the verification scope for this update. There is no new runtime code,
dependency, phase acceptance or publication. See the [Phase 0 review](phase-0-review.md)
for existing build and infrastructure limitations.

Verified locally on 7 October 2026:

- All 20 JSON/YAML files parse; all 47 local Markdown links resolve.
- Foundation structure/action-pin and pnpm workspace checks pass.
- Compared with the previous delivered archive: 11 review/instruction/metadata
  files changed and two review documents were added. All 52 build, package,
  source, preset, sample and spec files are byte-identical.
- The requested model/effort and unchanged phase/support-version status are
  recorded consistently. Two independent reviews found no remaining
  contradiction after the explicit skipped-root constraint was added.

No broader compilation was repeated for this document-only update. These checks
do not establish compiler behavior, evaluator conformance or hosted CI success.

Automatic approval review rejected exporting the full brief because authorization
to export potentially sensitive project data was insufficient. A separately
approved prompt containing only abstract design questions completed successfully;
no project files or samples were sent in that successful consultation.
