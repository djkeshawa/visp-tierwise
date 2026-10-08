# ADR 0016: Conformance suite scope for v0.1

- Status: accepted (delegated technical decision)
- Date: 2026-10-09

| Topic | Decision |
| --- | --- |
| Missing and wrong-type versions | VEC-71 also covers a `content` without `specVersion` (`E_BUNDLE_INTEGRITY`) and a non-string `specVersion` (`E_BUNDLE_UNSUPPORTED`), because EVAL-56 is easy to get wrong |
| Structural loader faults (BUNDLE-118 to 136) | No invalid-bundle files in v0.1. Evaluator unit tests cover them, listed as enforcers (VEC-125). A later spec revision may extend VEC-71. |
| `createdAt` | Must denote a real calendar date and time (BUNDLE-150(b)) |
| Provenance fixtures | Pinned to the digest of `bundles/retail-pos.json`; regenerate them if that bundle changes |
| Enforcer files | `spec/conformance/enforcers.yaml` plus one file per area under `spec/conformance/enforcers.d/`. A clause may be listed in only one file. |
| Edge bundles | Small focused bundles `bundles/edge-<area>.json`, built with the canonicalizer, each documented in `bundles/README-edge-<area>.md` |
