# Benchmarks

This page records the in-process cost of `resolve` and `decide` in the two evaluators, measured on one machine. It
answers requirements section 12 (performance): on a bundle of about 1,000 keys and 50 features, the p99 target is
under 100 µs in process, excluding providers. The numbers below are measurements, not estimates. Nothing has been tuned
for them, as the plan asks ("don't optimise before measuring").

## What is measured

| | Java (`tierwise-core`) | JavaScript (`@tierwise/evaluator`) |
| --- | --- | --- |
| Source | `ResolveDecideBenchmarkTest` and `BenchModel` in `java/tierwise-core/src/test/java/io/github/tierwise/core/bench/` | `js/evaluator/bench/bench.mjs` |
| Bundle | 1,000 keys, 50 features, 3 levels, 1 axis, 40 zones, 20 units, 4 lanes; no service-sourced level | The same shape (built in the script; the bytes differ from Java's) |
| Operations | `resolve(context, key)` and `decide(context, feature, facts)` | `resolveSync`, `resolve` (async API) and `decideSync` |
| Warm-up | 50,000 operations of each kind, untimed | 50,000 operations of each kind, untimed |
| Samples | 200,000 operations of each kind, each timed on its own | 200,000 operations of each kind, each timed on its own |
| Clock | `System.nanoTime()` | `process.hrtime.bigint()` |
| Statistic | Nearest-rank percentiles of all 200,000 samples | Nearest-rank percentiles of all 200,000 samples |

Both sides use the same shape of work: each call takes a context that names a zone, a unit and a lane, and a key or
feature that the bundle declares. Every key cycles through five shapes: an `all` boolean, an `override` integer with
an axis (`combineAcrossAxes: max`), a `min` integer, an `override` enum, and a `union` set. Every feature decides an
order from its three child lines, with one policy of six rules, two settings and bindings at the root and at ten zones.
Contexts and fact sets cycle through 64 variants, so that the lookups do not repeat one path.

`resolve` never calls a provider, because the model has no service-sourced level, so the numbers exclude provider cost
as section 12 requires. The timed work is the evaluation itself, including the construction of the result object that
the call returns. The percentiles include the cost of the two clock reads around each call, which was not separated out.

## Results

Each evaluator was run three times on the same machine. The table gives the median of the three runs, and the range
(lowest to highest) in brackets. All values are in microseconds.

| Evaluator | Operation | p50 | p99 | p99.9 | mean |
| --- | --- | --- | --- | --- | --- |
| Java 21 | `resolve` | 1.12 [1.08–1.15] | 5.49 [5.46–6.08] | 8.89 [8.89–10.34] | 1.59 [1.54–1.65] |
| Java 21 | `decide` | 1.74 [1.68–1.77] | 4.90 [4.83–5.60] | 10.25 [9.54–11.29] | 2.03 [1.93–2.04] |
| Node (v26.7.0) | `resolve` (sync) | 1.98 [1.97–2.07] | 4.41 [4.24–5.38] | 8.00 [7.89–9.31] | 2.32 [2.26–2.36] |
| Node (v26.7.0) | `resolve` (async API) | 2.33 [2.27–2.33] | 4.91 [4.80–5.74] | 8.67 [8.57–10.15] | 2.64 [2.59–2.70] |
| Node (v26.7.0) | `decide` (sync) | 3.84 [3.77–3.85] | 7.01 [7.01–7.27] | 11.01 [10.67–11.30] | 3.76 [3.74–3.85] |

The maximum sample was about 3.5 ms in Java and about 0.3 to 0.7 ms in JavaScript. A few single samples this large
appear in every run, and they are most likely garbage collection or compilation pauses. They are outside the p99 and
p99.9 values, and they are not a steady-state cost.

**Against the target.** The median p99 of every operation is below 100 µs. The slowest median p99 is 7.0 µs, so the
margin is 14 times, and the fastest is 4.4 µs, a margin of 23 times. The target is met in process for both evaluators,
excluding providers.

## Machine and toolchain

- CPU: AMD Ryzen 9 5900HS, 8 cores with 2 threads each (16 logical processors), up to 4.68 GHz.
- Memory: 30 GB. OS: Pop!_OS 22.04 (Ubuntu base), Linux 7.1.1.
- Java: OpenJDK 21.0.12.1 (build 21.0.12.1+1). The Java numbers come from a forked surefire JVM with default options.
- JavaScript: Node v26.7.0. The nightly job runs Node 22, which is the version `ci.yml` uses for the JavaScript
  packages; those numbers have not been measured here.

## Caveats

- **One machine, one run per configuration per session.** The three repeats show the run-to-run spread on this
  machine only. Shared CI runners have more noise, and the nightly job logs its numbers for comparison, but it does
  not assert on them.
- **Not JMH.** The plan asks for no new dependency, so the Java benchmark is a plain test with `System.nanoTime()`,
  a warm-up, and a sink that keeps the results alive. JMH would control the JIT and the dead-code elimination more
  tightly. The results should be read as order-of-magnitude figures for this model, not as a tuned upper bound.
- **Warm-up is short and not checked.** Each operation is warmed up for 50,000 calls. Longer warm-ups were not tried, so
  the numbers may still move with more warm-up. A cold start, with no warm-up, is not measured.
- **The async API costs about 0.3 µs more** than the sync call in Node, which is the cost of the promise and the
  microtask turn. It is listed so that a reader does not compare the sync number with a cold, async path.
- **Shape matters.** Lookups that reach many levels and many axes, or decisions with many rules and child facts, cost
  more than these. The model is representative of a bundle of this size, not of every bundle.
- **No provider cost.** A service-sourced level adds the provider call, its timeout handling and its outcome to every
  resolve that needs it. Those are outside this page on purpose (requirements section 12).

## Reproduce

Java, from `java/`:

```sh
./mvnw -B -ntp -pl tierwise-core -Dtest=ResolveDecideBenchmarkTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtierwise.excludedGroups= -Dtierwise.bench.out=/path/to/bench-java.txt test
```

JavaScript, from the repository root, after `pnpm --dir js run build`:

```sh
node js/evaluator/bench/bench.mjs /path/to/bench-js.txt
```

The nightly workflow (`.github/workflows/nightly.yml`) runs both and prints the reports in its log. The Java benchmark
is skipped in a normal build, because the test only runs when `tierwise.bench.out` is set.
