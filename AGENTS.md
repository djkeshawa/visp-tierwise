# Strata contributor instructions

The project is at the Phase 0 review gate. Stop after each phase and wait for
explicit review before starting the next, as required by the supplied brief.

- Write and review the language-neutral specification in Phase 1 before any
  production implementation. Empty build placeholders are not implementation.
- For future technical clarifications, consult Claude Opus 5.5 at xhigh effort,
  assess the advice against the specification, and choose the best supported
  approach. The user explicitly delegated these choices. Record the consultation
  and decisions in `docs/adr/`; do not repeatedly ask the user routine design
  questions. Report a consultation failure honestly and do not silently substitute
  a different model. Human ownership, account access, dependency approval,
  publishing and phase acceptance remain governed by the user's instructions.
  Start with abstract questions and the minimum necessary context. Sending full
  project documents to an external service requires separately authorized export;
  do not work around an automatic approval rejection.
- Keep domain names in samples and test fixtures. Engine guarantees are fixed;
  kinds, naming, ownership and file layout are declared by users or presets.
- Preserve unrelated changes. Do not add dependencies, publish, push or open a
  pull request without user approval.
- Keep the core free of Spring and networking. Keep the JS evaluator free of
  runtime dependencies. JavaScript does not compile source projects in v1.
- Java and JS must eventually read the same conformance vectors directly.
- Report checks accurately. Foundation checks do not prove runtime semantics,
  Maven verification, conformance, hosted CI or release readiness.

The project identity and supported spec/bundle versions are provisional.
Review `docs/phase-0-review.md` before continuing.

The user has authorized applying the prompt review recommendations. Read
`docs/build-brief.md` and `docs/recommendations-review.md` for the amended brief
and remaining decisions. This update has not accepted Phase 1 or authorized
dependencies or publication. The user subsequently delegated technical
clarification to Claude Opus 5.5 at xhigh effort and Codex's assessment of the
best approach.
