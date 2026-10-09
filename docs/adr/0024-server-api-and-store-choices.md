# ADR 0024: Server API and bundle store choices

- Status: accepted (delegated technical decision)
- Date: 2026-10-10

Requirements section 9, the specification and ADRs 0020 and 0023 leave some choices about `tierwise-server` and
its bundle stores open. The open-point lists in `java/tierwise-server/README.md` and
`java/tierwise-server-s3/README.md` recorded them. This ADR settles each one. Most decisions keep the behaviour
that is already implemented and tested. Two of them change code: the order of filesystem records, and the bytes of
the S3 pointer.

| Topic | Decision | Alternatives considered |
| --- | --- | --- |
| Encoding of `POST /v1/bundles` | `multipart/form-data` with the parts `bundle` (the exact canonical bytes) and `record` (the publication record as JSON). | A JSON envelope with the bundle as a string or as base64. A string can change the bytes through escaping and normalisation. Base64 is safe but can't be inspected, and `curl -F` can't send it. Two requests (bundle, then record) would leave a bundle without a record when the second one fails. |
| Does a publish move `latest`? | The first publication of a version makes it the latest. So does any publication when no latest exists. Publishing a version that already has records adds a record and leaves the pointer alone. | Always moving the pointer would let a repeated CI run undo a promotion. Never moving it would make every first deploy need a separate promote. |
| Identical records | A record that equals a stored record of the same version is stored once. BUNDLE-148 forbids replacing a record. Skipping an exact duplicate replaces nothing and keeps a retried request idempotent. | Storing every copy makes a retried request grow the history with records that can't be told apart. |
| `BundleStore.records()` order | The SPI promises the order of addition, oldest first, where the store can observe that order. The filesystem store does: record names carry the time and a sequence number within the millisecond. A store that can't observe it orders by `createdAt`, then by insertion where known (S3). The server uses only emptiness and membership. | Requiring the strict order of addition from every store would rule out object stores. Ordering only by `createdAt` would lose the order of addition that the filesystem store can keep. |
| Response bodies | Publish: `{bundleVersion, newVersion, recordAdded, madeLatest, latest, record}`. History: `{latest, bundles[]}`, one entry per record, newest `createdAt` first. Promote: `{latest, previous}`. Resolve: `{bundleVersion, results[]}`. The schema endpoint returns the declarations without values and bindings. `openapi.yaml` is the reference. | Returning bare `Resolved` or `Decision` values would drop the `bundleVersion` that every result must carry (requirements §15). |
| History pagination | None in 0.1. `GET /v1/bundles` returns every record. A `limit` and a cursor can be added later without breaking callers. | Paging now would add API surface before any deployment needs it. |
| Error codes outside section 6 | The server-only codes `E_REQUEST_INVALID`, `E_REQUEST_TOO_LARGE`, `E_UNSUPPORTED_MEDIA_TYPE`, `E_NOT_ACCEPTABLE`, `E_NOT_FOUND`, `E_METHOD_NOT_ALLOWED`, `E_BUNDLE_NOT_FOUND`, `E_BUNDLE_CONFLICT`, `E_UNAUTHENTICATED`, `E_FORBIDDEN`, `E_STORE_UNAVAILABLE` and `E_INTERNAL` are part of the server's HTTP API, not of the engine catalogue in `error-codes.md`. A bad publication record is `E_PROVENANCE_INVALID`, because the record has the provenance format. | Adding the HTTP codes to the engine catalogue would make them spec-conformance obligations for every evaluator. A new code for a bad record would duplicate `E_PROVENANCE_INVALID`. |
| HTTP status of core errors | `E_UNKNOWN_KEY` and `E_UNKNOWN_FEATURE` map to 404. The caller errors of EVAL-30 to EVAL-36 map to 400. A core error that a caller can't cause (`E_PROVIDER_MISSING`, `E_EVALUATOR_OPTIONS`, a stored bundle that no longer loads) maps to 500. | Answering 400 for everything would hide server faults from monitoring. |
| Multi-key resolve | The server uses the public single-key `Evaluator.resolve` and keeps provider results for the duration of one request. This makes as many provider calls per node as SVC-20's single evaluation call does. A public multi-key `resolve` in the core can replace this later. | Exposing the internal multi-key class now would grow the public core API before 0.1 ships. |
| Provider beans | A `LevelValueProvider` bean is matched to its level by bean name. | Adding `level()` to the public interface would change the core API. |
| Roles | The defaults are `tierwise-publisher` and `tierwise-promoter`, read from the claim `roles`. All three can be configured. | Fixed names would leave no room for an organisation's own role names. |
| Security starter | `spring-boot-starter-security-oauth2-resource-server`, the name that replaces the deprecated one ADR 0020 used. The group and content are the same. | The deprecated name works now but would break on a later Spring Boot. |
| `latest` pointer bytes | Both stores write the version followed by one newline, and both read a pointer with or without it. The S3 store now writes the newline, so a pointer copied between stores is byte-identical. | Writing no newline in either store would change the filesystem format that existing directories use. |
| S3 answer to `If-Match` on a deleted object | 404 and 412 are both treated as a lost race and retried. This is safe whichever one a backend sends. | Trusting one answer would need a check against every S3-compatible backend. |
| S3 limits | 5 attempts per conditional write and 1000 publication slots per version. These are chosen defaults, not measured ones, and are documented. | Unbounded retries would turn an outage into a hung request. |
| Promotion audit trail | Promotions are logged and not stored. The history comes from publication records only (requirements §9). | A stored promotion log would add a record type that the spec doesn't define. |

## Consequences

- The open-point lists in both READMEs point here instead of saying *(OPEN)*.
- `FilesystemBundleStoreTest.recordsAddedWithinOneMillisecondKeepTheirOrder` uses a fixed clock to check the order of records.
- `S3BundleStoreTest` checks that the S3 pointer bytes match the filesystem store's.
