# Tierwise contributor and agent instructions

## Where things are

- Direction: `docs/vision.md`. Requirements (with the normative draft in §4):
  `docs/requirements.md`. Sequencing: `docs/roadmap.md`.
- Decisions: `docs/adr/`. Owner-only questions: `docs/open-questions.md`.
- `spec/spec.md` is authoritative. When code and spec
  disagree, the code is wrong.

## How to work

- Work in the roadmap's phases. Stop at the end of each phase with a summary, the
  evidence for its exit criteria, and every decision the requirements didn't
  dictate. Wait for review before starting the next phase.
- Write the spec before the code. Implement only accepted spec, and write tests
  alongside the code.
- **Technical clarifications are delegated.** Choose the best-supported option,
  record it in an ADR with the alternatives considered, and continue. Escalate only
  ownership, accounts, dependency additions, publishing and phase acceptance to the
  owner.
- Never silently pick an interpretation. If the spec is ambiguous, resolve it with
  an ADR or RFC and update the spec and vectors first.

## Hard rules

- No domain names (levels, axes, nodes, entities, facts, keys, features, effects)
  in `java/**/src/main` or `js/**/src`. They belong in `samples/` and test fixtures.
  Kind names and naming patterns belong in presets.
- Engine guarantees are fixed. No preset or setting may disable an engine error.
- `tierwise-core`: no Spring, no networking, no logging framework.
  `@visplabs/evaluator`: zero runtime dependencies. JS never compiles sources in v1.
- Java and JS read the same `spec/conformance/` files directly. Never copy vectors.
- Don't add dependencies, publish, push or open pull requests without the owner's
  approval. Preserve unrelated changes.
- Report checks accurately. Structural checks don't prove semantics, conformance or
  hosted CI.
