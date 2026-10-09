# tierwise-server-s3

Optional S3 bundle store for the Tierwise server (ADR 0020, requirements section 9). It implements the server's
`io.github.tierwise.server.store.BundleStore` SPI and keeps canonical bundles, publication records and the latest
pointer in a bucket. Every change is a conditional S3 write, so concurrent publishers and promoters cannot overwrite
each other's stored bytes.

The module depends on the AWS SDK for Java v2 (`software.amazon.awssdk:s3`, version from the BOM in ADR 0020). Only
this module and `tierwise-spring-boot-starter-s3` use the SDK. The core modules and `tierwise-server` do not.

## Enable it

```yaml
tierwise:
  server:
    store: s3
    s3:
      bucket: my-tierwise-bucket        # required
      prefix: tierwise/prod              # optional; leading and trailing slashes are ignored
      region: eu-west-1                  # optional; otherwise the SDK's region chain applies
      endpoint: https://s3.example.test  # optional; for S3-compatible services
      path-style-access: false           # optional; some S3-compatible services need true
```

Credentials are never configured here. The SDK's default provider chain supplies them (environment variables,
profile, web identity or instance role). The store starts only when `tierwise.server.store` is `s3`. A missing bucket
fails startup and names the property.

The server binds `tierwise.server.store` as a scalar (`filesystem` by default) and registers its filesystem store only
for `filesystem`. With `store: s3` the server's filesystem store is not created and this module supplies the store.

Auto-configuration (`S3BundleStoreAutoConfiguration`) registers two beans, each skipped when the application defines
its own:

| Bean | Type | Notes |
| --- | --- | --- |
| `tierwiseS3Client` | `S3Client` | Built from the settings above. Closed by the container at shutdown. |
| `tierwiseS3BundleStore` | `BundleStore` | `S3BundleStore` over the bucket and prefix. |

The client is created even when the application supplies its own `BundleStore`. Supply your own `S3Client` as well if
that matters to you.

### Ordering with the server's default store

`S3BundleStoreAutoConfiguration` is declared with `@AutoConfiguration(before = TierwiseServerAutoConfiguration.class)`.
The server's filesystem store is `@ConditionalOnMissingBean(BundleStore.class)`, so the S3 bean must be registered
first, or the filesystem default would be created and kept. The ordering test checks the sorted order of the two
classes.

## Layout

Keys sit under the optional `<prefix>/`:

| Key | Content |
| --- | --- |
| `bundles/<bundleVersion>.json` | The canonical bundle bytes, exactly as given. The server validates them first. |
| `publications/<bundleVersion>/<createdAt>-<n>.json` | One publication record, as the JSON form of `PublicationRecord.toJson()`. `createdAt` is RFC 3339 UTC with whole seconds. `n` is the slot number, from 1, with no leading zeros. |
| `latest` | The latest pointer: the bundle version as UTF-8 text, written with no trailing newline. |

Versions are checked with `BundleVersions` (`sha256:` plus 64 lower-case hex digits, BUNDLE-5), and `createdAt` with the
rule of `PublicationRecord` (BUNDLE-150 (b)). Values that break the rule are rejected with `IllegalArgumentException`
before any request is sent. The store never maps a version to a key outside its own prefix.

A record body is parsed with the same strict mapper as the filesystem store (duplicate members are rejected). The
version comes from the key prefix, and `PublicationRecord.fromJson` checks the body against it. A record whose
`bundleVersion` disagrees with its prefix is damaged.

## Operations (the server SPI)

| Method | S3 behaviour |
| --- | --- |
| `putBundle(version, bytes)` | `PutObject` with `If-None-Match: *`. Returns `true` when this call stored the bytes. If the key is taken, the stored bytes are compared: identical bytes return `false` and nothing is written. Different bytes throw `BundleConflictException` (server type), and the stored bytes are kept. A conflicting write that leaves no object is retried. |
| `getBundle(version)` | `GetObject`. Empty when the key is absent. |
| `addRecord(record)` | `PutObject` with `If-None-Match: *` on the first free slot for `record.createdAt()` (`-1`, then `-2`, and so on). Records are never replaced. Each collision costs one request. The store does not check that the bundle exists; the server stores the bundle first. |
| `records(version)` | Lists the version's prefix and reads each record. Folder markers (keys ending in `/`) are skipped. Any other key that is not a record of the version fails the call. |
| `allRecords()` | One flat, paged listing of `publications/`. Each key is parsed against its own version segment, and each record is read. Costs one `GET` per record, which is the price of the history endpoint. A foreign key fails the call. |
| `latest()` | `GetObject` of `latest`. Empty when nothing has been set. The text is stripped of surrounding whitespace, so a pointer with a trailing newline is read the same way. |
| `setLatest(version)` | Checks the bundle is stored with a HEAD request, and throws `BundleStoreException` if it is not, so the pointer never names a missing key. Then it reads the pointer and its ETag, and writes `latest` with `If-Match: <etag>`, or with `If-None-Match: *` when no pointer exists. Setting the current version writes nothing. A lost race re-reads and tries again. |

A rejected conditional write (HTTP 412, or 409 for a concurrent write) is handled inside the store. The store re-reads
and tries again, so callers get the final result, or a `BundleStoreException` once the attempts run out. The limits
are 5 attempts for a bundle or a pointer, and 1000 slots for a publication.

### Limits the SPI does not show

- **`records(version)` is not in insertion order.** S3 can only order by `createdAt` and then by slot number. Slots
  are handed out in call order, so the result equals the order of addition whenever `createdAt` never decreases
  between calls. A record added later with an earlier `createdAt` is listed first. The SPI's Javadoc promises
  "the order they were added", which this store cannot guarantee. The server uses `records()` only for emptiness and
  `contains`, and sorts its own history, so the difference does not reach a client.
- **`setLatest` is last-writer-wins.** The `If-Match` retry loop stops a stale call from overwriting a newer pointer
  without re-reading it. It does not order two concurrent calls. The last successful write names the latest version.
  This matches the SPI's "replaced atomically", which promises that a reader sees a whole pointer, not which of two
  racing calls wins.

## Backend requirements

The backend must honour conditional PutObject: `If-None-Match: *` and `If-Match` with HTTP 412 on a failed
precondition. AWS S3 supports both. A backend that rejects the headers fails with a `BundleStoreException`. The store
never falls back to an unconditional write. Reads after writes are strongly consistent on AWS S3.

## Reconciliation with the server SPI

This module uses the server's SPI directly. The provisional copy has been removed:

1. The module's own `BundleStore`, `BundleConflictException`, `BundleStoreException` and `BundleNotFoundException` are
   deleted. The store implements `io.github.tierwise.server.store.BundleStore` and throws the server's exceptions.
2. The SPI signatures are used: `boolean putBundle`, `Optional<byte[]> getBundle`, `void addRecord`,
   `List<PublicationRecord> records`, `List<PublicationRecord> allRecords`, `Optional<String> latest` and
   `void setLatest`. `PutOutcome` and `publishedVersions` are gone.
3. Records are structured. The body is `record.toJson()`, and the read path calls `PublicationRecord.fromJson`.
4. `setLatest` has no `BundleNotFoundException`. The cheap HEAD check throws `BundleStoreException` instead.
5. The auto-configuration is ordered before the server's configuration (see above).
6. The server's `BundleService.publish` and `promote` call the store as the SPI describes. Its `history()` uses
   `allRecords()`.

No change to the server module is needed. The server's properties now take `tierwise.server.store` as a scalar, so
`store: s3` binds, and a test starts the real server configuration with it.

## Tests

`cd java && ./mvnw -B -ntp -pl tierwise-server-s3 -am verify` runs 79 tests in this module. The tests use an in-memory `S3Client` fake that
models conditional PutObject, ETags, paged listings and injected failures. No network is used. The concurrency tests
race 8 threads against the fake and check the outcomes that conditional writes guarantee. The auto-configuration tests
run `ApplicationContextRunner`, and web contexts with the real `TierwiseServerAutoConfiguration` check that `store=s3` replaces
the filesystem default and that the filesystem default is kept when `store` is unset. The JSON round trip, damaged records (including a record whose
`bundleVersion` disagrees with its key prefix), and the ordering of `records()` are covered in `S3BundleStoreTest`.

## Open points

- **(OPEN, SPI owner) `BundleStore.records()` wording.** The SPI promises "the order they were added, oldest first",
  which S3 cannot guarantee (see Limits). Recommended: weaken the SPI wording to "ordered by `createdAt`, then by
  insertion where known", since the server only uses emptiness and `contains`.
- **(OPEN) Backend answer to `If-Match` on a deleted object.** The code treats both 404 and 412 as a lost race. Which
  one a given backend returns has not been verified against a real service.
- **(OPEN) Pointer format.** The filesystem store writes the version plus a newline, while this store writes no
  newline. Both are read the same way, but it is not decided whether the writer should match the filesystem store. Until
  it is, the S3 pointer and the filesystem pointer differ in their last byte.
- **(OPEN) Limits.** The 5 attempts and 1000 slots are chosen values, not measured ones.

Answered by the server, and no longer open:

- **Does publishing move `latest`?** Answered by `BundleService.publish`. It calls `setLatest` when the version has no
  records yet or when no latest exists. The store needs no decision of its own.
- **Must a publication name an existing bundle?** Answered by `BundleService.publish`. It calls `putBundle` before
  `addRecord`. The store does not check.
- **Fractional seconds in `createdAt`.** Answered by `PublicationRecord`, which allows whole seconds only (BUNDLE-150 (b)).
  Keys therefore sort in time order, and the restriction stays.
