# Tierwise

Tierwise is a type checker and evaluator for layered business configuration. You
declare your own levels, keys and decisions. A merge rule on every key, explicit
all/any over collections, and provenance on every answer make sure the result
means one thing, the same thing, in Java and in JavaScript.

> **Status: pre-specification.** The direction, requirements and semantic decisions
> are settled ([vision](docs/vision.md), [requirements](docs/requirements.md),
> [ADRs](docs/adr/)). The normative spec, conformance suite and implementations are
> next ([roadmap](docs/roadmap.md)). Nothing is published yet, and there is no
> usable library.

## The problem

Applications combine values from several levels: platform → tenant → region →
store → user, or plan → org → workspace → user. Without a shared schema and explicit
merge rules, different readers get different answers:

- Misspelled names silently remove restrictions.
- Rules over collections leave "all" versus "any" unclear.
- Nobody can explain where a value came from.

Tierwise gives every key, fact and rule exactly one meaning, defined once and
enforced at build time. Mistakes fail CI, not production, and every answer explains
itself.

## When to use Tierwise

| If you need… | Use |
| --- | --- |
| Release toggles, experiments, kill switches | A feature-flag tool. Tierwise will offer an OpenFeature provider. |
| Authorization: who may do what to which resource | Cedar or OPA |
| A general-purpose configuration language | CUE, Pkl or Jsonnet |
| Typed configuration across levels you define, with a merge rule per key; entity-aware decision tables with explicit all/any; provenance for every answer; Java and JavaScript | **Tierwise** |

## How it will work

```
config repo (git)  ──►  tierwise validate · test · compile  ──►  bundle.json (canonical, content-hashed)
                                                                    │
                                    ┌───────────────────────────────┤
                                    ▼                               ▼
                         Java services (in-process)     Node / browser (in-process; browser = display only)
```

- **Per-key merge strategies:** `override`, `deep_merge`, `all`, `any`,
  `intersect`, `union`, `min`, `max`.
- **Explicit node admission:** typos in node IDs are rejected or explicitly ignored,
  never silently accepted.
- **Entity-aware decisions** with explicit `all_in` / `any_in` quantifiers over
  child collections.
- **Provenance:** every result names each contributing scope, the selected binding,
  the matching rule and the bundle version.
- **One spec, two implementations** sharing one conformance suite.

**Browser evaluation is for display only.** Client-side results can be tampered
with. Enforce decisions on the server.

## Repository

| Path | Contents |
| --- | --- |
| `docs/` | [Vision](docs/vision.md), [requirements](docs/requirements.md), [roadmap](docs/roadmap.md), [implementation plan](docs/implementation-plan.md), [ADRs](docs/adr/), [open questions](docs/open-questions.md) |
| `spec/` | Normative spec, JSON Schemas, error codes, conformance vectors (Phase 1) |
| `java/` | `tierwise-core`, `-cli`, `-spring-boot-starter`, `-server`, `-openfeature` (placeholders) |
| `js/` | `@tierwise/evaluator`, `@tierwise/node`, `@tierwise/openfeature` (placeholders) |
| `presets/`, `samples/` | Built-in presets and sample domains (Phase 1) |

## Quickstarts

The Java and JavaScript five-minute quickstarts are written and verified in the
release phase, when the APIs exist. Until then, read
[the requirements](docs/requirements.md) for the planned API sketches.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) and [GOVERNANCE.md](GOVERNANCE.md).
Contributions use DCO sign-off under Apache-2.0. Structural checks:

```sh
python3 scripts/check-foundation.py
(cd java && ./mvnw verify)
pnpm --dir js install --frozen-lockfile && pnpm --dir js run build && pnpm --dir js test
```

## License

[Apache-2.0](LICENSE)
