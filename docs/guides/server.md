# Server guide

`tierwise-server` is the optional control plane of Tierwise. It stores every published bundle version, serves the
latest bundle with an `ETag`, promotes a stored version to latest (to roll back or forward), and answers
`POST /v1/resolve` and `POST /v1/decide` with `tierwise-core`.

- **Stateless and horizontally scalable.** An instance keeps nothing that another instance does not see. All state
  lives in the bundle store.
- **Never the source of truth.** Your configuration repository is. The server holds compiled, content-hashed bundles
  that CI published. It never edits a schema or a value, and there is no API to do so.
- **Never on the hot path.** Applications evaluate in-process (see the
  [runtime and operations guide](runtime-and-operations.md)) and keep serving the bundle they hold when the server is
  down. The server is for storing, distributing and rolling back bundles, and for callers that cannot evaluate in-process.

You do not need the server. A bundle can be served from any HTTP endpoint or an object store. Use the server when you
want an audit trail of publications, an authenticated publish step, one-command rollback, or `/resolve` and
`/decide` for callers that cannot embed an evaluator.

The names in this guide (`checkout.pickup.lead_hours`, `division`, …) come from the `commerce` sample project and are
examples. The commands were run against the executable JAR built from the working tree, with a throw-away identity
provider that served a key set on a local port.

## Run it

The server is a Spring Boot 4.1 application on Java 21. The runnable JAR is `tierwise-server-<version>-exec.jar`;
the plain `tierwise-server-<version>.jar` is the library that embedding applications and `tierwise-server-s3` depend on.

```sh
java -jar tierwise-server-0.1.0-exec.jar \
  --spring.security.oauth2.resourceserver.jwt.issuer-uri=https://idp.example.test/realms/ci \
  --tierwise.server.filesystem.root=/var/lib/tierwise
```

*(OPEN)* The JAR is not published. Build it from a checkout with
`cd java && ./mvnw -B -ntp -DskipTests -pl tierwise-server -am package`; it is
`java/tierwise-server/target/tierwise-server-<version>-exec.jar`. The `issuer-uri` here is a placeholder.

Without a JWT setting the server rejects every token (and logs a warning). Reads then work only if
`tierwise.server.security.reads` is `open`, and publishing and promoting are impossible. There are **no default
credentials**: no user store, no password and no static token.

## Endpoints

The full contract is `openapi.yaml` (OpenAPI 3.1, in the module's `src/main/resources`), which the server also serves
at `GET /openapi.yaml`.

| Endpoint | Purpose | Who |
| --- | --- | --- |
| `POST /v1/bundles` | Publish canonical bundle bytes plus a publication record | publish role |
| `GET /v1/bundles/latest` | The latest bundle, as stored. The `ETag` is the `bundleVersion`; `If-None-Match` gives `304` | reads |
| `GET /v1/bundles/{version}` | One stored version, as stored | reads |
| `GET /v1/bundles` | History from the publication records: version, commit, time, publisher | reads |
| `POST /v1/bundles/{version}/promote` | Make a stored version the latest | promote role |
| `POST /v1/resolve` | `{ context, keys[] }` gives resolved values with provenance | reads |
| `POST /v1/decide` | `{ context, feature, input }` gives a decision with its trace | reads |
| `GET /v1/schema` | The keys, features, facts and policies of the latest bundle | reads |
| `GET /openapi.yaml` | The OpenAPI document | anyone |

`HEAD` is allowed wherever `GET` is. Every other request is denied.

## Authentication and roles

The server is an OAuth2 resource server. Point it at your identity provider and give it the role names:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://idp.example.test/realms/ci
tierwise:
  server:
    security:
      reads: authenticated
      publish-role: tierwise-publisher
      promote-role: tierwise-promoter
```

- A token is a JWT. Roles come from one configured claim (`roles` by default; `tierwise.server.security.roles-claim`
  takes a name or a dotted path such as `realm_access.roles`, a list of strings or one space-separated string). A role
  `r` becomes the authority `ROLE_r`. **Scopes grant nothing.**
- The server keeps no session and uses no cookie, so CSRF protection is off: there is no cookie to forge.
- Give the CI identity the publish role only. Give a smaller group of people, or a release job, the promote role.
  Publishing a bundle does not give the right to make it the latest one again after a rollback.
- Reads are authenticated by default. `POST /v1/resolve` and `/v1/decide` count as reads. With `reads: open`, anyone who
  can reach the server can evaluate and, through providers, cause provider calls; open reads suit a server on a
  private network. `GET /openapi.yaml` is always open.
- To replace the rules entirely, define your own `SecurityFilterChain` bean; the default backs off.
- Terminate TLS in front of the server, or use the standard `server.ssl.*` properties. Tokens are bearer tokens, so use
  TLS outside a private network.

What each call looks like without a token and with a token that lacks the role (the response headers are shortened):

```console
$ curl -sS -i "$TIERWISE_SERVER/v1/bundles/latest"
HTTP/1.1 401
{"code":"E_UNAUTHENTICATED","message":"a valid bearer token is required"}

$ curl -sS -i -X POST "$TIERWISE_SERVER/v1/bundles" -H "Authorization: Bearer $READER_TOKEN" …
HTTP/1.1 403
{"code":"E_FORBIDDEN","message":"the token does not carry the role that this endpoint needs"}
```

## Publish

`POST /v1/bundles` is `multipart/form-data` with two parts:

- `bundle`: the exact canonical bundle bytes, as `tierwise compile` wrote them. The server does not re-encode them: the
  stored bytes are the published bytes.
- `record`: the publication record, JSON `{ "createdAt": "...", "gitCommit": "...", "publisher": "..." }`. `createdAt` is
  required (RFC 3339, UTC, whole seconds) and the other two are optional. The members are those of a provenance
  sidecar, except `bundleVersion`, which the server takes from the bundle. A `bundleVersion` member is accepted only if it
  equals the bundle's.

```sh
curl -sS -X POST "$TIERWISE_SERVER/v1/bundles" \
  -H "Authorization: Bearer $CI_TOKEN" \
  -F "bundle=@bundle.json;type=application/json" \
  -F "record={\"createdAt\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"gitCommit\":\"$(git rev-parse HEAD)\",\"publisher\":\"ci\"}"
```

The answer is `201` when the bundle was stored for the first time and `200` when the same bytes were stored before:

```json
{
  "bundleVersion": "sha256:ab1cd3a96be4ab7569ccaba61cb65dbd14b5efd8239de35f16643ab791af6859",
  "newVersion": true,
  "recordAdded": true,
  "madeLatest": true,
  "latest": "sha256:ab1cd3a96be4ab7569ccaba61cb65dbd14b5efd8239de35f16643ab791af6859",
  "record": {
    "bundleVersion": "sha256:ab1cd3a96be4ab7569ccaba61cb65dbd14b5efd8239de35f16643ab791af6859",
    "createdAt": "2026-10-09T05:32:04Z",
    "gitCommit": "33c9966dd978786ad5f4e3670eabede914d1557c",
    "publisher": "ci"
  }
}
```

(Reformatted; the server writes one line.) What the server does, in this order:

1. **Loads the bundle** with `Tierwise.loadBundle`, which verifies the digest, the bundle format version, the spec
   version and the structure. A failing bundle gets `400` with the core's code (`E_BUNDLE_INTEGRITY` or
   `E_BUNDLE_UNSUPPORTED`) and nothing is stored. A bundle holds no examples, so the server runs none; they run when
   the project is validated, in CI.
2. **Validates the record** (`400 E_PROVENANCE_INVALID`).
3. **Stores the bundle bytes** under their `bundleVersion`, if absent. Different bytes for a version that is stored give
   `409 E_BUNDLE_CONFLICT`, and the stored bytes never change.
4. **Moves the `latest` pointer** to the version if it has no earlier publication record. Publishing a version again
   adds a record and leaves the pointer where it is, so a repeated CI run cannot undo a rollback.
5. **Adds the publication record.** Several records may exist for one version; an identical one is stored once.

Publish from the build of your main branch only, after `tierwise validate`, `tierwise test` and `tierwise compile`
passed. Publishing is idempotent, so a retried job is safe.

## Read

`GET /v1/bundles/latest` returns the stored bytes, with the `ETag` set to the `bundleVersion`:

```console
$ curl -sS -i "$TIERWISE_SERVER/v1/bundles/latest" -H "Authorization: Bearer $TOKEN"   # headers shortened
HTTP/1.1 200
Cache-Control: no-cache
ETag: "sha256:ab1cd3a96be4ab7569ccaba61cb65dbd14b5efd8239de35f16643ab791af6859"
Content-Type: application/json
Content-Length: 5172

{"bundleVersion":"sha256:ab1cd3a96be4ab7569ccaba61cb65dbd14b5efd8239de35f16643ab791af6859","content":{…
```

With `If-None-Match` set to that tag the server answers `304` from the `latest` pointer alone, without reading the
bundle, so polling every 30 seconds from many applications is cheap. `GET /v1/bundles` returns the history from the
publication records, newest first, with the current `latest`:

```json
{
  "latest": "sha256:6aaf2221975c62b5fdc71f4e1823745ed896f94609e3deb82eb81ca53f06d948",
  "bundles": [
    { "bundleVersion": "sha256:6aaf2221975c62b5fdc71f4e1823745ed896f94609e3deb82eb81ca53f06d948", "createdAt": "2026-10-09T06:00:00Z", "publisher": "ci" },
    { "bundleVersion": "sha256:ab1cd3a96be4ab7569ccaba61cb65dbd14b5efd8239de35f16643ab791af6859", "createdAt": "2026-10-09T05:32:04Z", "gitCommit": "33c9966dd978786ad5f4e3670eabede914d1557c", "publisher": "ci" }
  ]
}
```

(Reformatted, and one record of the first version left out.) There is no pagination yet: the history returns every
record.

## Roll back and forward

`POST /v1/bundles/{version}/promote` moves the `latest` pointer to a version that is already stored. Nothing else
changes: bundles and records are never modified. Clients that poll `GET /v1/bundles/latest` with `If-None-Match` see
the new `ETag` at their next poll.

```console
$ curl -sS -X POST "$TIERWISE_SERVER/v1/bundles/$PREVIOUS_VERSION/promote" -H "Authorization: Bearer $ADMIN_TOKEN"
{"latest":"sha256:ab1cd3a96be4ab7569ccaba61cb65dbd14b5efd8239de35f16643ab791af6859","previous":"sha256:6aaf2221975c62b5fdc71f4e1823745ed896f94609e3deb82eb81ca53f06d948"}
```

The token needs the promote role. With only the publish role the same call is `403 E_FORBIDDEN`. Promotions are
recorded in the log and not in the history: the history comes from publication records only.

## Resolve and decide

Both call `tierwise-core` over the latest bundle, so the results equal the in-process results for the same bundle,
context and facts.

```console
$ curl -sS -X POST "$TIERWISE_SERVER/v1/resolve" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d '{"context":{"levels":{"division":"south"},"axes":{"seller":"acme"}},"keys":["checkout.pickup.lead_hours"]}'
```

gives `{ "bundleVersion": "sha256:…", "results": [ <Resolved>, … ] }`, one result per key in request order, each exactly
the JSON form of a resolved result (the same as `Resolved.toJson()` in Java and the object that
`@visplabs/evaluator` returns):

```json
{
  "key": "checkout.pickup.lead_hours",
  "strategy": "override",
  "kind": "configuration",
  "value": 8,
  "skippedScopes": [],
  "contributions": [
    { "scope": "level", "name": "global", "value": 2, "effect": "SET" },
    { "scope": "level", "name": "division", "node": "south", "value": 6, "effect": "SET" },
    { "scope": "axis", "name": "seller", "node": "acme", "value": 8, "effect": "SET" }
  ],
  "source": { "scope": "axis", "name": "seller", "node": "acme" }
}
```

(`bundleVersion` of the result is left out.)

```console
$ curl -sS -X POST "$TIERWISE_SERVER/v1/decide" -H "Authorization: Bearer $TOKEN" \
    -H 'Content-Type: application/json' \
    -d '{"context":{"levels":{"division":"south"}},"feature":"checkout.order_submission",
         "input":{"facts":{"customer.active":true,"customer.credit_hold":true},
                  "children":{"item":[{"item.stock_type":"STOCKED"},{"item.stock_type":"DROP_SHIP"}]}}}'
```

gives the decision: the effect, the binding, the policy, the reason and the trace.

```json
{
  "feature": "checkout.order_submission",
  "effect": "DENY",
  "bundleVersion": "sha256:ab1cd3a96be4ab7569ccaba61cb65dbd14b5efd8239de35f16643ab791af6859",
  "skippedScopes": [],
  "binding": { "level": "division", "node": "south" },
  "policy": "credit_check",
  "reason": { "type": "DEFAULT" },
  "trace": [
    { "id": "good-credit", "matched": false, "failed": { "fact": "customer.credit_hold", "op": "eq" } },
    { "id": "credit-hold-allowed-stock", "matched": false, "failed": { "fact": "item.stock_type", "op": "all_in" } }
  ]
}
```

`input` is the FactSet and may be omitted. An unknown node or key is a `400` or `404` with the evaluator's code:

```console
$ curl -sS -i -X POST "$TIERWISE_SERVER/v1/resolve" … -d '{"context":{"levels":{"division":"nowhere"}},"keys":["checkout.pickup.lead_hours"]}'
HTTP/1.1 400
{"code":"E_UNKNOWN_NODE","message":"the node nowhere is not listed for division"}
```

### Providers for service-sourced levels

`/resolve` runs on the server, so the server needs the values of service-sourced levels itself:

- Define one `io.github.visplabs.tierwise.core.LevelValueProvider` bean for each level you can serve. **The bean name is the level
  name.** `tierwise.server.provider-timeout` (default 200 ms) bounds each call.
- A service-sourced level without a bean gets a provider that always fails. Its keys come back with `UNAVAILABLE`
  contributions and the `onSourceFailure` behaviour of the key (`skip` or `deny`).
- A request is one evaluation call: each provider is called at most once per node for the whole request, however many keys
  it resolves.
- A bean whose name is not a service-sourced level of the bundle is ignored, with a warning in the log.
- `decide` never calls a provider.

A server that embeds these beans is an application of your own that depends on the plain `tierwise-server` JAR. The
beans come from the auto-configurations `TierwiseServerAutoConfiguration` and `TierwiseSecurityAutoConfiguration`.

## Errors

Every error is JSON `{ "code": "...", "message": "..." }` and nothing else.

| Status | When | Code |
| --- | --- | --- |
| 400 | Malformed request, a version that is not a `bundleVersion`, an unknown member, too many keys | `E_REQUEST_INVALID` |
| 400 | The core rejects the context or the facts | `E_CONTEXT_INVALID`, `E_UNKNOWN_SCOPE`, `E_UNKNOWN_NODE`, `E_FACTS_INVALID` |
| 400 | A published bundle fails the core's checks | `E_BUNDLE_INTEGRITY`, `E_BUNDLE_UNSUPPORTED` |
| 400 | A publication record is invalid | `E_PROVENANCE_INVALID` |
| 401 | No valid bearer token | `E_UNAUTHENTICATED` |
| 403 | The token lacks the role | `E_FORBIDDEN` |
| 404 | Unknown key or feature | `E_UNKNOWN_KEY`, `E_UNKNOWN_FEATURE` |
| 404 | Version not stored, or nothing published | `E_BUNDLE_NOT_FOUND` |
| 409 | Different bytes for a stored version | `E_BUNDLE_CONFLICT` |
| 413 | Bundle over `tierwise.server.max-bundle-size` | `E_REQUEST_TOO_LARGE` |
| 415, 405, 406 | Wrong content type, method or accept | `E_UNSUPPORTED_MEDIA_TYPE`, `E_METHOD_NOT_ALLOWED`, `E_NOT_ACCEPTABLE` |
| 503 | The bundle store cannot be reached | `E_STORE_UNAVAILABLE` |
| 500 | Anything else, including a core error that is a fault of the server. No detail in the response | `E_INTERNAL` |

## Storage

`BundleStore` (`io.github.visplabs.tierwise.server.store`) is the storage interface. Its contract: bundle bytes are written once
and never modified; a reader sees complete bundles only; publication records are never replaced; the `latest`
pointer is the only mutable datum.

### Filesystem (the default)

```text
<root>/bundles/<64 hex digits>.json
<root>/records/<64 hex digits>/<millis>-<uuid>.json
<root>/latest
<root>/tmp/
```

Every file is written completely to `tmp/`, flushed, and put in place with an atomic step that refuses to overwrite;
only `latest` is replaced, with an atomic move. Instances can share one directory if the filesystem gives atomic renames
and exclusive creation (a local disk, or a well-behaved network volume). For anything else use S3. Do not edit files in
the store by hand.

### S3

Add the `tierwise-server-s3` module to the application and set `tierwise.server.store=s3`:

```yaml
tierwise:
  server:
    store: s3
    s3:
      bucket: my-tierwise-bucket        # required
      prefix: tierwise/prod             # optional; leading and trailing slashes are ignored
      region: eu-west-1                 # optional; otherwise the SDK's region chain applies
      endpoint: https://s3.example.test # optional; for S3-compatible services
      path-style-access: false          # optional; some S3-compatible services need true
```

Credentials are never configured here: the AWS SDK default provider chain supplies them (environment, profile, web
identity or instance role). A missing bucket fails start-up and names the property.

| Key under the optional `<prefix>/` | Content |
| --- | --- |
| `bundles/<bundleVersion>.json` | The canonical bundle bytes, exactly as given |
| `publications/<bundleVersion>/<createdAt>-<n>.json` | One publication record. `n` is the slot number, from 1 |
| `latest` | The `latest` pointer: the bundle version as UTF-8 text, with no trailing newline |

Every change is a conditional write (`If-None-Match: *`, or `If-Match` for the pointer), so concurrent publishers and
promoters cannot overwrite each other's stored bytes. The backend must honour conditional `PutObject`; AWS S3 does. The
store never falls back to an unconditional write.

Two limits to know. `records(version)` is ordered by `createdAt` and then by slot, which is the order of addition only
when `createdAt` does not decrease between calls; the server sorts its own history, so clients do not see the
difference. And `setLatest` is last-writer-wins: two concurrent promotions are not ordered, and the last successful
write names the latest version.

*(OPEN)* `tierwise-server-s3` has been tested against an in-memory fake of the S3 client (79 tests, no network) and not
against S3. How a given backend answers `If-Match` on a deleted object, and the chosen limits (5 attempts for a bundle
or a pointer, 1,000 slots for a publication), have not been verified. See the open points in
`java/tierwise-server-s3/README.md`.

### Your own

Define a `BundleStore` bean. The filesystem store then backs off.

## Deployment

- **Stateless.** Run as many instances as you need behind a load balancer. They share the store and nothing else: no
  sticky sessions, no cluster, no leader. Roll instances one at a time; a new instance reads the same store.
- **Never the source of truth.** Losing the store loses only distribution, not configuration: re-publish from the
  repository.
- **Consistency.** With the default `latest-cache-ttl=0s`, every request reads the `latest` pointer, so a publish or
  promote on any instance is seen by all at once. A larger TTL trades that for fewer reads of the store (useful for S3);
  an instance still sees its own publish or promote at once. Bundle versions are immutable, so they are cached for good.
- **Memory.** An instance holds up to `runtime-cache-size` loaded bundles. Bundles are small: kilobytes to a few
  megabytes.
- **Providers.** `/resolve` calls providers from the server's threads, with the timeout above. Run the server where it
  can reach the services behind them, or leave the levels without a provider to get `UNAVAILABLE` contributions.
- **TLS.** Terminate it in front of the server or with `server.ssl.*`.

## Configuration

All settings are Spring Boot properties (`application.yaml`, environment variables such as
`TIERWISE_SERVER_SECURITY_READS=open`, or command-line arguments).

| Property | Default | Meaning |
| --- | --- | --- |
| `tierwise.server.store` | `filesystem` | The bundle store. `filesystem` is built in; a store module names its own value (`s3`) |
| `tierwise.server.filesystem.root` | `./tierwise-data` | Directory of the filesystem store. Created if missing |
| `tierwise.server.max-bundle-size` | `16MB` | Largest bundle that `POST /v1/bundles` accepts. It also bounds the multipart request |
| `tierwise.server.provider-timeout` | `200ms` | Timeout of one `LevelValueProvider` call. A positive whole number of milliseconds |
| `tierwise.server.latest-cache-ttl` | `0s` | How long one instance remembers the `latest` pointer. `0s` reads the store on every request |
| `tierwise.server.runtime-cache-size` | `8` | Loaded bundle versions kept in memory per instance |
| `tierwise.server.max-keys-per-request` | `100` | Largest `keys[]` of one `/v1/resolve` request |
| `tierwise.server.security.reads` | `authenticated` | `open` lets reads go without a token; `authenticated` needs a valid token |
| `tierwise.server.security.publish-role` | `tierwise-publisher` | Role that may `POST /v1/bundles` |
| `tierwise.server.security.promote-role` | `tierwise-promoter` | Role that may promote |
| `tierwise.server.security.read-role` | (empty) | If set, reads also need this role. No effect when reads are `open` |
| `tierwise.server.security.roles-claim` | `roles` | JWT claim with the roles |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` or `.jwk-set-uri` | none | **Required** to accept any token |

The role names and the claim are choices of this server; the requirements say only "configured roles".

## Where the server fits

```text
 CI ──publish (publisher role)──► tierwise-server ◄──promote (promoter role)── a person or a release job
                                       │ ETag polling (reads)
                          ┌────────────┴────────────┐
                          ▼                         ▼
                  Spring Boot services        Node services        (in-process evaluation)
```

Applications poll with `If-None-Match`; they do not need the server for any answer. Callers that cannot embed an
evaluator use `/resolve` and `/decide`, which return the same answers for the same bundle, context and facts.
The [security model](security-model.md) says what the publish step must protect.

## Open points

- *(OPEN)* The server JAR is not published until the first release.
- *(OPEN)* The S3 store has been tested only against a fake of the S3 client; its behaviour on a deleted object and its retry limits are unchecked against S3.
