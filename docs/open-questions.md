# Open questions

All 19 technical questions from the Phase 0 review are resolved by ADRs 0005 and
0007–0011.  What remains is for the
**project owner**: ownership, accounts, people and approvals.

| # | Question | Needed by | Proposed answer |
| --- | --- | --- | --- |
| ~~O1~~ | ~~Approve the dependency baseline~~ | — | **Approved 2026-10-08** ([ADR 0012](adr/0012-parsing-and-dependency-baseline.md)) |
| O2 | Claim the publication identity ([ADR 0028](adr/0028-visplabs-publication-identity.md)). **GitHub done 2026-10-11:** the organisation `visplabs` exists and the repository is `visplabs/tierwise`. Still open: the `@visplabs` npm scope and the `io.github.visplabs` Maven Central namespace | Before announcing the name; required by Phase 9 | Create the npm organisation `visplabs` and register the namespace (both free) |
| ~~O3~~ | ~~Who are the initial maintainers?~~ | — | **Answered 2026-10-10:** the owner, [@djkeshawa](https://github.com/djkeshawa), as sole maintainer ([MAINTAINERS.md](https://github.com/visplabs/tierwise/blob/main/MAINTAINERS.md)) |
| ~~O4~~ | ~~Private security and conduct contacts~~ | — | **Answered 2026-10-10:** GitHub private vulnerability reporting for security; conduct reports to the maintainer through GitHub. No email is published |
| ~~O5~~ | ~~Governance tie-break and maintainer appointment rules~~ | — | **Adopted 2026-10-10** as proposed: lazy consensus; the project lead breaks ties until there are three or more maintainers ([GOVERNANCE.md](https://github.com/visplabs/tierwise/blob/main/GOVERNANCE.md)) |
| ~~O6~~ | ~~Should the repository be public from Phase 1 or only from release?~~ | — | **Answered 2026-10-11:** public now, as `djkeshawa/visp-tierwise`. Transferred to `visplabs/tierwise` the same day (O2); GitHub redirects the old URL |

## Technical decision process

Technical clarifications are delegated. Pick the best-supported option, record it
in an ADR with the alternatives considered, and continue. Escalate to the owner
only for ownership, accounts, dependencies, publishing, or acceptance of a phase.
