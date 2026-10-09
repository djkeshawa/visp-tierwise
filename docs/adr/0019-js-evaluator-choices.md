# ADR 0019: JS evaluator implementation choices

- Status: accepted (delegated technical decision)
- Date: 2026-10-09

| Topic | Decision |
| --- | --- |
| Provider registration | `providers: { level: { fn, timeoutMs } }`. A bare function is rejected, because SVC-48 forbids a default timeout. Unknown option names and malformed listeners are `E_EVALUATOR_OPTIONS`, which guards against typos (stricter than the minimum in EVAL-28). |
| Provider results | Must be a plain or null-prototype object. Anything else, or a result whose getters throw, makes the level `UNAVAILABLE` (SVC-25). |
| Timeouts | Capped at 2^31−1 ms, the `setTimeout` limit |
| `null` vs `undefined` | `undefined` means omitted. `null` is present and invalid (CTX-2, FACT-22, FACT-30). |
| JSON | In-house recursive-descent parser over UTF-8 bytes. Nesting deeper than 1000 levels is `E_BUNDLE_INTEGRITY`. |
| Loader parity | BUNDLE-118 now also names SVC (deny on a non-narrowing strategy, or `min` with deny and no lower bound), entity-graph cycles and empty scope names. Both loaders must reject the same bundles. |
| Tests | `.mjs` files that run against the built `dist`, so the tests exercise what ships |
