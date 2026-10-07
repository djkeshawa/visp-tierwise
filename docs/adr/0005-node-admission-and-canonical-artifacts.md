# ADR 0005: Skip ignored scopes and separate bundle provenance

- Status: selected under delegated technical clarification; Phase 0 review remains open
- Date: 2026-10-07
- Consultation: Claude Opus 5.5 (`claude-opus-5-5`), xhigh effort

## Context and authority

The user instructed Codex to ask Claude, take the best approach, and use that
process for future clarification. The user then specified **Claude Opus 5.5
xhigh**. This delegates technical judgment; it does not accept a phase, assign
project owners, authorize dependencies or authorize publication.

A real Claude CLI consultation completed successfully. The request explicitly
selected `claude-opus-5-5` and `--effort xhigh`; response metadata confirmed the
model `claude-opus-5-5`. Tools, MCP servers, browser access and session persistence
were disabled. The successful prompt contained only two abstract questions about
unknown node handling and deterministic artifacts, with no project files,
samples, account details or credentials. An earlier attempt to export the full
brief was rejected by automatic approval review for insufficient authorization
to export potentially sensitive project data. The abstract prompt was separately
approved. Raw consultation streams stay outside deliverables.

Claude recommended complete runtime skipping and an immutable canonical artifact
with separate provenance. Codex assessed those recommendations against Strata's
existing requirements and an independent consistency review. The selected
decisions below are integrated into [the amended brief](../build-brief.md).

## Decision: node admission

For a closed catalog, runtime `on_unknown_node: ignore` removes only the supplied
unknown scope from evaluation. Read no values or bindings and call no provider
for it. It does not trigger `on_source_failure`. Other supplied levels and axes
stay eligible, since the context supplies its own path and the engine infers no
parent relationships.

Validate the whole supplied context before any provider call. Unknown declared
scope names and structural/ID type errors remain errors under `ignore`. Open
catalogs admit otherwise valid IDs, including provider resolution as configured.
Compile-time source values and bindings must use known closed-catalog IDs under
both policies, so authored typos cannot disappear through runtime skipping.

Both proposed result contracts carry ordered `skippedScopes` entries:
`{ scope: level|axis, name, node, reason: UNKNOWN_NODE }`. Use declared level order,
then axis order; use an empty list when nothing was skipped. These are admission
diagnostics, not synthetic value contributions or provider failures.

Do not synthesize a root or substitute another node. If no eligible feature
binding remains, return an explicit evaluation error rather than using an
unselected policy's default. Its diagnostic code/API representation and the
separate question of root identity/omission are Phase 1 work.
Any later implicit-root inclusion rule must not reinsert an explicitly skipped
unknown root. Omission-equivalence tests can use non-root scopes while that
separate inclusion rule is unsettled.

## Decision: canonical artifact

Keep the existing name `bundleVersion`. It is `sha256:` plus the lowercase digest
of UTF-8 JCS-encoded `content`. Include spec/format versions and every
evaluation-relevant input in `content`, including compiled catalogs and admission
policies. The artifact is exactly UTF-8 JCS encoding of
`{ bundleVersion, content }`, with no metadata fields, byte-order mark or trailing
newline. Enforce strict parsing, including rejection of duplicate property names
and unknown envelope fields.

JCS specifies canonical serialization and preserves string data; it does not
normalize Unicode. Strata's declared source normalization therefore precedes
canonicalization. [RFC 8785](https://www.rfc-editor.org/rfc/rfc8785)

Put variable `gitCommit`, `createdAt` and publisher metadata in separate sidecars
or server publication records carrying `bundleVersion`. Permit several records
for one content version and preserve their history. Different external metadata
must leave the canonical bundle byte-identical. Evaluators consume the bundle,
not provenance records.

Keep existing server routes. Latest/version responses serve stored canonical
bytes; history obtains commit/time/publisher from publication records. Reject
conflicting canonical bytes for an already stored version. A matching digest
proves content integrity, not a publisher's identity or the truth of provenance
claims; trusted claims need the existing authenticated publication boundary or
a separately specified attestation design. Sidecar schema/naming, upload transport
and timestamp semantics remain Phase 1 format work.

## Alternatives assessed

| Advice or option | Assessment |
|---|---|
| Ignore only static values, but still call a provider | Reject: an ignored scope would still affect evaluation or cause failures. |
| Prune descendants of an ignored scope | Reject: conflicts with Strata's explicit absence of inferred node parents. |
| Add implicit required scopes or root fallback | Reject: root inclusion has not been defined; full skipping must not invent it. |
| Enforce a universal node-name pattern | Reject: naming conventions are user/preset policy. Structural validity still needs a defined contract. |
| Rename `bundleVersion` to `digest` | Reject: preserve the existing proposed API name. |
| Exclude metadata from the hash but keep it in the bundle envelope | Reject: the same version could have different artifact bytes, breaking whole-bundle reproducibility. |
| Derive every metadata field deterministically | Reject as the default: publisher and publication time describe separate events that can legitimately differ for identical content. |
| Add channel pointers, mandatory signatures or new immutable routes now | Defer: they would expand the existing server/transport contract beyond this clarification. |

## Consequences and verification

These are selected requirements for specification work, not implemented runtime
behavior. Add shared vectors in Phase 1 for skip equivalence, independent scopes,
provider call counts, whole-context rejection before calls, strict source
membership, no effective binding, deterministic artifacts and provenance linkage.
The [consultation review](../claude-consultation.md) records the test inventory.

No production source, build adapter or dependency has changed. Phase 0 acceptance
and approval of a complete Phase 1 specification remain required by the brief.
Future technical clarifications use the requested model and effort with minimal
external context, followed by critical assessment and an ADR.
