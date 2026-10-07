# Open questions

All 19 technical questions from the Phase 0 review are resolved by ADRs 0005 and
0007–0011. The original list is kept in
[history](history/open-questions-2026-10-07.md). What remains is for the
**project owner**: ownership, accounts, people and approvals.

| # | Question | Needed by | Proposed answer |
| --- | --- | --- | --- |
| O1 | Approve the dependency baseline in [ADR 0012](adr/0012-parsing-and-dependency-baseline.md) (Maven Wrapper, build plugins, TypeScript; later snakeyaml-engine, jackson-core, JUnit, jqwik, `yaml`, fast-check) | Phase 0 close-out | Approve the Phase 0 and Phase 2 rows now; approve later rows when each phase starts |
| O2 | Claim the `tierwise` GitHub organisation and the `@tierwise` npm scope | Before announcing the name; required by Phase 9 | Claim both now (free) |
| O3 | Who are the initial maintainers? | Before external contributions | The owner, as sole maintainer to start |
| O4 | Private security and conduct contacts | Before the repository is public | GitHub private vulnerability reporting, plus a dedicated email |
| O5 | Governance tie-break and maintainer appointment rules | Before a second maintainer joins | Lazy consensus; the project lead breaks ties until there are three or more maintainers |
| O6 | Should the repository be public from Phase 1 (developing the spec in the open) or only from release? | Phase 1 start | Public from Phase 1: spec feedback is most valuable before it freezes |

## Technical decision process

Technical clarifications are delegated. Pick the best-supported option, record it
in an ADR with the alternatives considered, and continue. Escalate to the owner
only for ownership, accounts, dependencies, publishing, or acceptance of a phase.
