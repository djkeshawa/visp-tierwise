# ADR 0023: S3 module choices

- Status: accepted (delegated technical decision)
- Date: 2026-10-09

| Topic | Decision |
| --- | --- |
| Location property | Both S3 modules use the starter's and server's own properties. The starter reads `tierwise.source=s3://bucket/key`; the starter rejects `s3://` with a hint when the module is absent, and doesn't warn when the module's `BundleSource` bean serves it. The server uses `tierwise.server.store=s3`. |
| Region setting | `…s3.aws-region` rather than `…s3.region`, so the property name doesn't collide with a sample level name in the genericity scan and states its meaning. If unset, the SDK default region chain applies. |
| HTTP client | The AWS SDK `url-connection-client`, a lightweight blocking client with no Netty or Apache HttpClient |
| Conditional requests | The starter's GET uses `If-None-Match` with the last S3 ETag, only when the caller holds that version. The store's writes use `If-None-Match: *` for bundles (never overwritten) and the pointer's ETag for promote. Tests use fakes; the 304 and conditional-write paths are verified against a real S3-compatible endpoint before release (release checklist). |
| Defaults | No bucket default and no credentials in configuration. The AWS default credential chain applies. |
