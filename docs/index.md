# Tierwise documentation

Tierwise is a type checker and evaluator for layered business configuration, with
Java and JavaScript evaluators that follow one language-neutral spec.

**Status:** pre-specification. Direction, requirements and semantic decisions are
settled. Phase 1 (the normative spec and conformance suite) is next.

| Read | For |
| --- | --- |
| [Vision and strategy](vision.md) | Why Tierwise exists, who it's for, principles, risks |
| [Requirements](requirements.md) | What 0.1.0 must do, including the normative draft (§4) |
| [Roadmap](roadmap.md) | Phases, exit criteria, milestones |
| [Implementation plan](implementation-plan.md) | Work items for each phase |
| [Open questions](open-questions.md) | What only the project owner can decide |
| [Decisions (ADRs)](adr/) | Why each non-obvious choice was made |
| [Contributing](contributing.md), [RFC template](rfc-template.md) | How to propose changes |
| [History](history/) | The original brief, prompt review and Phase 0 records (frozen) |

## Decision index

| ADR | Decision |
| --- | --- |
| [0001](adr/0001-phase-boundaries.md) | Review stop at every phase boundary |
| [0002](adr/0002-foundation-tooling.md) | Interim Phase 0 build adapters (superseded by 0012) |
| [0003](adr/0003-provisional-identity.md) | Keep publishing identity unclaimed until the owner confirms |
| [0004](adr/0004-adopt-prompt-review.md) | Adopt the prompt review recommendations |
| [0005](adr/0005-node-admission-and-canonical-artifacts.md) | Ignored scopes are skipped entirely; canonical bundles with separate provenance |
| [0006](adr/0006-rename-to-tierwise.md) | Rename to Tierwise |
| [0007](adr/0007-hierarchy-root-and-admission.md) | Singleton root, per-scope declarations, context validation |
| [0008](adr/0008-merge-semantics.md) | Strategies, axes, defaults, `deny`, contribution effects |
| [0009](adr/0009-providers-and-evaluation-errors.md) | Provider contract and evaluation errors |
| [0010](adr/0010-entities-facts-and-rules.md) | Entity graph, FactSets, settings, effects, traces |
| [0011](adr/0011-source-format-and-types.md) | Type grammar, normalisation, namespaces, presets |
| [0012](adr/0012-parsing-and-dependency-baseline.md) | YAML 1.2, in-house JCS and SHA-256, approved dependency baseline and pinned toolchain |

## Browser use

Browser evaluation is for display only. Client-side inputs and outputs can be
altered, so decisions must be enforced on the server.
