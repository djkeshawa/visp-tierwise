# Security model

This page says what Tierwise protects, what it leaves to you, and where to enforce a decision. It is short on
purpose. If you remember three things, remember these:

1. **A decision made in a browser is a hint, not a control.** Enforce on a server.
2. **Publishing a bundle is the privileged act.** Whoever can publish a bundle decides every answer. Protect that step.
3. **The digest in a bundle detects damage, not forgery.** It proves the bytes are the ones the digest names. It does
   not prove who wrote them.

## Where decisions are enforced

Tierwise evaluates in-process, in the service that needs the answer. That service is where a decision must be
enforced: it checks the limit before it creates the project, and it denies the order before it submits it.

### Browser evaluation is for display only

`@tierwise/evaluator` and the web OpenFeature provider also run in a browser, and they give the same answers as the
server for the same bundle. That is useful for showing a user what is likely to be allowed: hiding a button, greying out a
plan feature, showing a limit. It is not enforcement, for two reasons.

- **Anyone who controls the browser controls the result.** The evaluator, its inputs (the context and the facts) and its
  output are all in the client. A user can change any of them.
- **Anyone who can run the page can read the bundle.** The bundle contains every value, at every level, and every rule of
  the project it was compiled from. Nothing in it is hidden from the person who loads it.

So, for browser use:

- Treat every browser-side result as advice. Repeat the decision on the server, with the same bundle, before you act on
  it. A `bundleVersion` in a request tells the server which bundle the client used; the server decides with its own.
- **Do not ship a bundle to a browser unless everything in it may be seen by the people who use that browser.** Prices,
  limits and entitlements of other tenants, internal rules and the names of internal services are all in the bundle.
  When the browser needs only a few keys, compile a separate, smaller project that declares only those keys, and serve
  that bundle to browsers.
- A browser cannot call a provider (the web provider and `resolveSync` use the synchronous variants), so it cannot show a
  value that comes from a service-sourced level.

The same warning is in the READMEs of the evaluator, loader and OpenFeature packages, in the JavaScript quickstart, in
the OpenFeature guide and in `SECURITY.md`, because it is the most common misuse.

### Servers

On a server, the evaluator is a library call in your process. A decision is only as sound as its inputs: the context
must name the real tenant and the facts must be the real facts. Take them from your own session and your own data, never
from the request body of an untrusted caller. Tierwise validates their shape and types, and rejects an unknown level, node or
fact. It cannot know that the node you passed is the wrong tenant.

Provenance is detailed. A resolved result lists every contribution, with the value of each level, and a decision names
its binding and its rule. That is valuable to your support team and to your logs. It is also internal configuration.
Do not return a `Resolved` or a `Decision` to an untrusted caller as it is.

The decision log line of the Spring Boot starter holds the feature, the reason, the rule, the effect and the
`bundleVersion`. It never holds the context or the facts.

## Publishing a bundle

A bundle is compiled from a Git repository by CI. What reaches a service is whatever bundle the source serves. That makes
the publish step, and the identity that performs it, the point to protect.

### With `tierwise-server`

- **There are no default credentials.** The server has no user store, no password and no static token. It accepts a JWT
  from the identity provider you configure (`issuer-uri` or `jwk-set-uri`). Without one it rejects every token.
- **Two roles for two acts.** The publish role (`tierwise-publisher` by default) may `POST /v1/bundles`. The promote role
  (`tierwise-promoter`) may make a stored version the latest one, to roll back or forward. Roles come from one configured
  claim of the token; scopes grant nothing. Give CI the publish role only. Give a few people, or a release job, the
  promote role. A compromised CI job can then add a version, and cannot make an old one current again.
- **Reads are authenticated by default**, `/resolve` and `/decide` included, because they return provenance. Open reads
  suit a server on a private network only.
- **Use TLS.** Tokens are bearer tokens. Terminate TLS in front of the server or with `server.ssl.*`.
- **Immutable storage.** A stored bundle never changes, a different body for a stored version is rejected with
  `E_BUNDLE_CONFLICT`, and publication records are never replaced. Rolling back is moving a pointer; nothing is deleted
  or rewritten. The history lists what was published and when.
- **What the record is.** A publication record (`createdAt`, `gitCommit`, `publisher`) is written by the publisher. The
  server stores the `publisher` and `gitCommit` that the request carries; it does not bind them to the identity of the
  token. Treat them as labels that help people find a build, not as proof of origin.

Build the bundle in a CI job that runs only for your protected branch, after `validate` and `test` passed. A bundle
that nobody reviewed should not be publishable: protect the branch the way you protect the code that deploys.

### Without the server

A bundle in an object store or behind an HTTP endpoint is as trustworthy as the permission to write there. Give the
write permission to the CI job and to nothing else, enable versioning if the store offers it, and make the read side
read-only for services.

## What the digest proves, and what it does not

A bundle is `{ bundleVersion, content }`. The `bundleVersion` is `sha256:` and the SHA-256 digest of the canonical bytes
of `content`. Every loader recomputes it.

| The digest | |
| --- | --- |
| Detects a truncated or corrupted download, a proxy that changed the body, a bundle edited by hand, a mismatch between the version a header names and the content | yes |
| Names the content: two bundles with the same `bundleVersion` have the same content, in every implementation | yes |
| Lets a cache, a poller and a log agree on which bundle they mean | yes |
| Proves that the bundle was written by your CI | **no** |
| Detects someone who replaces the bundle on the source and writes a matching digest | **no** |

The last two rows are the point. The digest is stored in the same document as the content it covers, so whoever can
change the content can recompute the digest. `E_BUNDLE_INTEGRITY` tells you the bundle is inconsistent. It cannot tell
you the bundle is yours.

A loader treats everything outside the bytes as a hint. The `ETag` of a response is used to skip a download, and the
version that counts is the one in the bundle. A server that ignores `If-None-Match`, or lies in a header, cannot make a
loader believe a different bundle than the one whose digest it verified.

**Publisher trust is established outside the bundle.** The ways to do it, from least to most work:

1. **Authenticate the source, and restrict who can write to it.** TLS to a server or an object store whose write
   permission is held by your CI job only. This is the default design.
2. **Record the digest where the writer cannot reach.** CI knows the `bundleVersion` it compiled (`tierwise compile`
   prints it). Log it, put it in the deployment record, and compare it with `tierwise.bundle.version` (or
   `status().bundleVersion`) on running instances. A mismatch is an alarm.
3. **Pin the expected digest in the consumer.** Packaging a bundle as a snapshot inside an image that CI built ties that
   service's start-up bundle to that build. Refresh then moves it to what the source serves.
4. **Sign the bundle.** Signed bundle attestations in the server are a candidate for after 0.1.0 and need an RFC. Until
   then a team that needs them can sign the bundle file with its own tooling and verify the signature in its loader
   before it hands the bytes to `loadBundle`.

Tierwise itself uses the same idea. A preset that a project fetches from a URL must be pinned to a SHA-256 in the
manifest, and the CLI checks it before it uses the preset; the GitHub Action's `jar` method refuses a JAR whose SHA-256
differs from the one the workflow names. In both, the digest is held somewhere the downloaded content cannot change.

## Failure behaviour is fail-closed where you say so

The engine never turns an unclear situation into a permissive answer. These are guarantees of the engine, not choices:

- A typo in a node or key name is an error at build time and at run time. It never drops a restriction silently.
- Context validation happens before any lookup. An unknown level, node or fact is `E_UNKNOWN_SCOPE`, `E_UNKNOWN_NODE`
  or `E_FACTS_INVALID`, not a default.
- A child fact must be tested with `all_in` or `any_in`. "Every" and "any" are never guessed.
- Missing facts are checked for the whole policy before any rule runs, and give the feature's `if_fact_missing` effect.
  The answer never depends on the order of rules.
- Strings are compared by exact code point. Booleans and numbers are never coerced.

And these are choices that your schema makes, so make them on purpose:

- For a key at a service-sourced level, `on_source_failure: deny` makes a provider outage contribute the most restrictive
  value. `skip` makes it contribute nothing. There is no default; the compiler requires you to choose.
- `if_fact_missing` is the effect when facts are missing. Make it the safe one.
- A node catalog with `on_unknown_node: error` rejects an unknown node. `ignore` skips that scope, which is a decision to
  fall back to the other scopes.
- On any error, OpenFeature returns the caller's default. Choose defaults that fail safe.

## Threats and where they are handled

| Threat | Handled by |
| --- | --- |
| A user changes a browser-side result | Enforce on the server. Browser evaluation is display only |
| A user reads the bundle in a browser | Serve browsers a bundle that contains only what they may see |
| A bundle is damaged in transit or at rest | The digest, checked by every loader (`E_BUNDLE_INTEGRITY`) |
| A source serves a bundle in a format or spec version a runtime does not support | `E_BUNDLE_UNSUPPORTED`; the runtime keeps its current bundle |
| Someone publishes a bad bundle | Publish role held by CI only; protected branch; `validate` and `test` before `compile`; rollback by `promote` |
| Someone replaces a bundle on the source | Authenticated, write-restricted source; digest recorded elsewhere and compared; optionally your own signature |
| A source is down | Last-known-good bundle; a snapshot for start-up; alert on bundle age |
| A provider is down | The key's `on_source_failure`, recorded as `UNAVAILABLE` in the provenance |
| A caller passes the wrong tenant | Not detectable by Tierwise. Take the context from your own session |
| Provenance leaks internal configuration | Authenticated reads; do not return results to untrusted callers |
| A dependency is compromised | The dependency baseline is small and approved by ADR (`tierwise-core` has two runtime dependencies, `@tierwise/evaluator` has none); SBOMs; actions pinned by commit; base images pinned by digest |

## Reporting a problem

`SECURITY.md` describes how to report a vulnerability privately. *(OPEN)* The private channel is not configured yet:
the repository has no home on a code-hosting site, and the owner has not assigned responders (open question O4 in
[open questions](../open-questions.md)). Until then, do not put the details of a vulnerability in a public issue.

## Open points

- *(OPEN)* The private channel for reporting a vulnerability is not configured (open question O4).
