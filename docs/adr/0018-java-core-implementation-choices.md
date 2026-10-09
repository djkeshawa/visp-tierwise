# ADR 0018: Java core implementation choices (J1)

- Status: accepted (delegated technical decision)
- Date: 2026-10-09

| Topic | Decision |
| --- | --- |
| Version check order (EVAL-56) | Check `bundleFormatVersion` completely, then `specVersion`. A future format reports `E_BUNDLE_UNSUPPORTED` even if `specVersion` is missing. The Java reader is aligned to this. |
| Extra loader checks (BUNDLE-118) | Loaders also reject bundles that break any bundle-observable rule (MERGE-7, KEY-7, BIND-13, SCOPE-3, …) with `E_BUNDLE_INTEGRITY`. Source-only rules (RULE-13/14, POL-16) are not rechecked. The JS loader must match, and differential tests check this. |
| Duplicates in `in` / `all_in` / `any_in` lists | Accepted. BUNDLE-133 rejects duplicates only in catalogs and `listOf`. |
| Writer normalisation | `BundleWriter` sorts every order-defined array itself, so compiler model order cannot change the bytes |
| YAML limits | Nesting deeper than 100 is `E_PARSE`. Integer tokens longer than 20 digits are `E_INTEGER_RANGE` without being parsed (avoiding quadratic `BigInteger` cost). Aliases are rejected, so no copy budget is needed. |
| YAML event model | `YamlLoader` uses the parser event stream rather than composed nodes, to keep exact source positions |
| Integral numbers (TYPE-17) | In Java inputs, integral `Double`, `Float` and `BigDecimal` values count as integers and are then range-checked. `1.5` is rejected. |
| Provider execution | Provider calls run on a shared daemon pool (256 threads) with per-call timeouts. A rejected submission counts as `UNAVAILABLE`. Thread-local context does not reach providers; runtime integrations may wrap providers to carry it. |
| Timeouts | A `Duration` must be positive and a whole number of milliseconds. Anything else is `E_EVALUATOR_OPTIONS`. |
| Java type-system cases | EVAL-28 "options not a map" and EVAL-37 "non-string key" can't occur in Java. The JS evaluator tests them, and Java tests the null, empty and unknown-name cases. |
