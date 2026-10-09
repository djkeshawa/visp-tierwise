# ADR 0017: Where findings are located

- Status: accepted (delegated technical decision)
- Date: 2026-10-09

## Context

Compile cases assert the exact file, YAML path and line of every finding. FIND-19
to FIND-23 cover nodes, missing entries, parse failures and presets. The wave 4
reviewers found eight further situations with no defined location, plus a few
cases where secondary findings were undefined. The suite already followed one
consistent convention throughout.

## Decision

The convention is now normative, in new FIND clauses after FIND-23, a new SCOPE
clause after SCOPE-9, and an extension of BIND-7:

- **Member values** are located at the member entry. **Item shapes** are located at the item.
- **Repeats and conflicts** are located at the later occurrence. Across files, the order is inline manifest content first, then files by code-point path order. Every later occurrence gets one finding.
- **Entity cycles:** one finding per cycle, at the entity that comes first in document order.
- **Examples:** `E_EXAMPLE_FAILED` is located at `expect`. A feature named by two rules documents is reported at each later `feature` entry.
- **Suppression:** a key with a missing or invalid `type` or `combine` gets no further checks. A setting with both or neither form gets no `default` check. A binding with an unknown **or missing** `policy` gets no setting checks.
- **Missing hierarchy:** a project with no `decision_hierarchy` is `E_SCOPE_DECLARATION` at the manifest root, unless a manifest, parse or mapping error is already reported. A root level listed in `axes` gets a single finding at that entry.

## Consequences

The compile cases that carried "OPEN" notes in their descriptions now follow
normative clauses. The Java compiler implements exactly this convention.
