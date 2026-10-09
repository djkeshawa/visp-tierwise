# ADR 0025: Upper bound of a provider timeout

- Status: accepted (delegated technical decision)
- Date: 2026-10-10

## Context

EVAL-28, SVC-38 and SVC-41 required a timeout to be "a positive integer number of milliseconds" and set no upper
bound. The two evaluators disagreed:

- The JS evaluator accepted only safe integers (`Number.isSafeInteger`), so it rejected `2 ** 53` and anything larger
  with `E_EVALUATOR_OPTIONS`. Its test suite expected this.
- The Java evaluator accepted any positive `Duration` that is a whole number of milliseconds, up to
  `Duration.ofSeconds(Long.MAX_VALUE)`. Its test suite expected `Duration.ofDays(1_000_000_000)`, about 8.6 × 10^16 ms,
  to be accepted.

The same options would therefore be valid for one evaluator and invalid for the other. No vector catches this,
because VEC-45 keeps durations out of vectors. The gap turned up while the planned enforcer tests were being written.

## Decision

A timeout is valid only when it is a positive integer number of milliseconds **no greater than 2^53−1**
(9007199254740991). SVC-41 and EVAL-28 say so. Anything larger raises `E_EVALUATOR_OPTIONS` in both evaluators.

- 2^53−1 is the integer range that TYPE-18 already uses for typed values. Every evaluator can represent it exactly,
  including one whose only number type is a binary64 double.
- No real timeout comes near the bound, which is about 285,000 years, so no reasonable configuration becomes invalid.
- The Java evaluator now rejects a `Duration` above 2^53−1 ms. `EvaluatorCreationTest` accepts 2^53−1 ms and
  rejects 2^53 ms, 10^9 days and `Long.MAX_VALUE` seconds. The JS test `evaluator-creation.test.mjs` "SVC-41: …"
  checks the same bounds.

## Alternatives considered

- **No upper bound (the Java behaviour).** A JS evaluator can't represent every such integer exactly. It would have
  to accept a number it can't tell apart from its neighbours, or use BigInt in the public options.
- **The 32-bit timer limit of JS (2^31−1 ms).** This bound comes from one platform's `setTimeout`. The JS evaluator
  already works around it by capping its timer and checking the elapsed time. It would also be a stricter rule than
  either evaluator applies today.
- **Leave it to each implementation.** That breaks the rule that the same bundle, options and inputs behave the same
  in every evaluator.
