# ADR 0027: Verifying the S3 modules against a real S3-compatible server

- Status: accepted (delegated technical decision)
- Date: 2026-10-10

## Context

ADR 0023 requires the S3 modules' conditional requests to be checked against a real S3-compatible endpoint before
release. Until now they had run only against an in-memory fake: `If-None-Match: *` on bundles, `If-Match` on the
`latest` pointer, and the starter's `If-None-Match` GET with its 304 answer.

The obvious server to test against is MinIO. Its upstream project archived the open-source server: the
`minio/minio` repository on Docker Hub is gone, and `dl.min.io` answers 410 Gone. The official binaries are still
attached to the GitHub releases of `minio/minio`, together with their SHA-256 digests.

## Decision

- `S3BundleStoreIntegrationTest` (tierwise-server-s3) and `S3BundleSourceIntegrationTest`
  (tierwise-spring-boot-starter-s3) run only when `TIERWISE_S3_IT_ENDPOINT` is set. They write under a random prefix
  and create their bucket if it is missing.
- `scripts/s3-it.sh` starts a throwaway MinIO, runs both classes, and fails if either class was skipped.
- The CI job `s3-integration` downloads the official linux-amd64 server binary of MinIO
  `RELEASE.2025-09-07T16-13-09Z` from the GitHub release and checks its SHA-256 (`7c5bd851…855f`) before running it.
  This is test infrastructure only. Nothing ships with it, and it adds no project dependency.
- The script's docker mode has no default image. It runs only an image that `MINIO_IMAGE` names, pinned by digest,
  so the project doesn't take on a third-party image.

Against MinIO, the code behaves as the fake modelled. `If-None-Match: *` on an existing key answers 412. A stale
`If-Match` answers 412, and `If-Match` on a deleted pointer answers 404; the store treats both as a lost race and
retries. A GET with a matching `If-None-Match` answers 304.

## Alternatives considered

- **A third-party MinIO image** (`bitnamilegacy/minio`, or the community fork `pgsty/minio`). Either works, but each
  makes the project trust another publisher's build of the server. The official binary with a pinned digest avoids
  that.
- **LocalStack or another S3 emulator.** These are emulators, not storage servers, and their support for conditional
  writes has varied between versions.
- **Real AWS S3 in CI.** This needs an account, credentials as secrets and spending, which are owner decisions. The
  READMEs say that only MinIO has been checked. A run against AWS S3 can be added once the owner provides an account.
