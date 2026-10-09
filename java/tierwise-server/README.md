# tierwise-server

The optional **control plane** of Tierwise (requirements section 9). It stores every published bundle version, serves
the latest bundle with an ETag, promotes a stored version to latest (roll back or forward), and answers
`POST /v1/resolve` and `POST /v1/decide` with `tierwise-core`.

- **Stateless and horizontally scalable.** An instance keeps nothing that another instance does not see. All state
  lives in the bundle store.
- **Never the source of truth.** The configuration repository is. The server holds compiled, content-hashed bundles
  that CI published. It never edits schema or values.
- **Never on the hot path.** Applications evaluate in-process (`tierwise-spring-boot-starter`, `@tierwise/node`) and
  keep serving the bundle they hold when the server is down. The server is for storing, distributing, rolling back,
  and for callers that cannot evaluate in-process.

A Spring Boot 4.1.1 application on Java 21. The runnable jar is `tierwise-server-<version>-exec.jar`. The plain
`tierwise-server-<version>.jar` is the library, which `tierwise-server-s3` and embedding applications depend on.

## Endpoints

The full contract is [`src/main/resources/openapi.yaml`](src/main/resources/openapi.yaml) (OpenAPI 3.1, hand-written),
which the server also serves at `GET /openapi.yaml`.

| Endpoint | Purpose | Who |
| --- | --- | --- |
| `POST /v1/bundles` | Publish canonical bundle bytes plus a publication record | publish role |
| `GET /v1/bundles/latest` | The latest bundle, as stored. `ETag` is the `bundleVersion`; `If-None-Match` gives 304 | reads |
| `GET /v1/bundles/{version}` | One stored version, as stored | reads |
| `GET /v1/bundles` | History from the publication records: version, commit, time, publisher | reads |
| `POST /v1/bundles/{version}/promote` | Make a stored version the latest | promote role |
| `POST /v1/resolve` | `{ context, keys[] }` gives resolved values with provenance | reads |
| `POST /v1/decide` | `{ context, feature, input }` gives a decision with its trace | reads |
| `GET /v1/schema` | Keys, features, facts and policies of the latest bundle | reads |
| `GET /openapi.yaml` | The OpenAPI document | anyone |

`HEAD` is allowed wherever `GET` is. Every other request is denied.

### Publish

`POST /v1/bundles` is `multipart/form-data` with two parts:

- `bundle`: the exact canonical bundle bytes (`compile` output, specification section 10). The server does not
  re-encode them; the stored bytes are the published bytes.
- `record`: the publication record, JSON `{ "createdAt": "...", "gitCommit": "...", "publisher": "..." }`. `createdAt` is
  required and the other two are optional. The members and forms are those of a provenance sidecar (BUNDLE-141 to
  BUNDLE-150, `spec/schemas/provenance.schema.json`), except `bundleVersion`, which the server takes from the bundle.
  A `bundleVersion` member is accepted only if it equals the bundle's.

```sh
curl -sS -X POST "$TIERWISE_SERVER/v1/bundles" \
  -H "Authorization: Bearer $CI_TOKEN" \
  -F "bundle=@bundle.json;type=application/json" \
  -F "record={\"createdAt\":\"$(date -u +%Y-%m-%dT%H:%M:%SZ)\",\"gitCommit\":\"$(git rev-parse HEAD)\",\"publisher\":\"ci\"}"
```

What the server does, in this order:

1. Loads the bundle with `Tierwise.loadBundle`. That verifies the digest, `bundleFormatVersion`, `specVersion` and the
   structure (BUNDLE-111 to BUNDLE-136). A failing bundle gets 400 with the core's code (`E_BUNDLE_INTEGRITY` or
   `E_BUNDLE_UNSUPPORTED`) and nothing is stored. Examples run when the project is validated; a bundle holds none, so
   the server does not run any.
2. Validates the record (400 `E_PROVENANCE_INVALID`).
3. Stores the bundle bytes under their `bundleVersion`, if absent. **Different bytes for a version that is stored give 409
   `E_BUNDLE_CONFLICT`, and the stored bytes never change.**
4. Moves the `latest` pointer to the version if it has no earlier publication record.
5. Adds the publication record (several records may exist for one version, BUNDLE-148; an identical one is stored once).

The answer is 201 when the bundle was stored for the first time and 200 when the same bytes were stored before.

### Roll back and forward

`POST /v1/bundles/{version}/promote` moves the `latest` pointer to a version that is already stored. Nothing else changes:
bundles and records are never modified. Clients that poll `GET /v1/bundles/latest` with `If-None-Match` see the new `ETag`
on their next poll.

### Resolve and decide

Both call `tierwise-core` over the latest bundle, so the results equal the in-process results for the same bundle,
context and facts. The tests prove it against `spec/conformance/bundles/commerce.json`.

```json
POST /v1/resolve
{ "context": { "levels": { "division": "south" }, "axes": { "seller": "acme" } },
  "keys": ["checkout.pickup.lead_hours"] }
```

gives `{ "bundleVersion": "sha256:...", "results": [ <Resolved>, ... ] }`, one result per key in request order, each
exactly the JSON form of the core's `Resolved` (specification section 9.2).

```json
POST /v1/decide
{ "context": { "levels": { "division": "south" } },
  "feature": "checkout.order_submission",
  "input": { "facts": { "customer.active": true }, "children": { "item": [ { "item.stock_type": "STOCKED" } ] } } }
```

gives the JSON form of the core's `Decision` (section 9.6): effect, binding, policy, reason and trace. `input` is the
FactSet; it may be omitted.

**Providers for service-sourced levels.** `/resolve` runs on the server, so the server needs the values of
service-sourced levels itself:

- Define one `io.github.tierwise.core.LevelValueProvider` bean for each level you can serve. **The bean name is the level
  name.** `tierwise.server.provider-timeout` (default 200 ms) bounds each call (SVC-48).
- A service-sourced level without a bean gets a provider that always fails. Its keys come back with `UNAVAILABLE`
  contributions and the `onSourceFailure` behaviour of the key (`skip` or `deny`), as requirements section 9 states.
- A request is one evaluation call: each provider is called at most once per node for the whole request, however many
  keys it resolves (SVC-19, SVC-20).
- A bean whose name is not a service-sourced level of the bundle is ignored, with a warning in the log.
- `decide` never calls a provider.

An `EvaluationListener` bean, if present, receives the core's events (provider failures, unknown keys, loads).

### Errors

Every error is JSON `{ "code": "...", "message": "..." }` and nothing else.

| Status | When | Code |
| --- | --- | --- |
| 400 | Malformed request, a version that is not a `bundleVersion`, an unknown member, too many keys | `E_REQUEST_INVALID` |
| 400 | The core rejects the context or the facts | `E_CONTEXT_INVALID`, `E_UNKNOWN_SCOPE`, `E_UNKNOWN_NODE`, `E_FACTS_INVALID` |
| 400 | A published bundle fails the core's checks | `E_BUNDLE_INTEGRITY`, `E_BUNDLE_UNSUPPORTED` |
| 400 | A publication record breaks BUNDLE-141 to BUNDLE-150 | `E_PROVENANCE_INVALID` |
| 401 | No valid bearer token | `E_UNAUTHENTICATED` |
| 403 | The token lacks the role | `E_FORBIDDEN` |
| 404 | Unknown key or feature | `E_UNKNOWN_KEY`, `E_UNKNOWN_FEATURE` |
| 404 | Version not stored, or nothing published | `E_BUNDLE_NOT_FOUND` |
| 409 | Different bytes for a stored version | `E_BUNDLE_CONFLICT` |
| 413 | Bundle over `tierwise.server.max-bundle-size` | `E_REQUEST_TOO_LARGE` |
| 415 / 405 / 406 | Wrong content type, method or accept | `E_UNSUPPORTED_MEDIA_TYPE`, `E_METHOD_NOT_ALLOWED`, `E_NOT_ACCEPTABLE` |
| 503 | The bundle store cannot be reached | `E_STORE_UNAVAILABLE` |
| 500 | Anything else, including a core error that is a fault of the server. No detail in the response | `E_INTERNAL` |

## Configuration

All settings are Spring Boot properties (`application.yaml`, environment variables such as
`TIERWISE_SERVER_SECURITY_READS=open`, or command-line arguments).

| Property | Default | Meaning |
| --- | --- | --- |
| `tierwise.server.store` | `filesystem` | The bundle store. `filesystem` is built in; a store module names its own value (`s3` for `tierwise-server-s3`) |
| `tierwise.server.filesystem.root` | `./tierwise-data` | Directory of the filesystem store. Created if missing |
| `tierwise.server.max-bundle-size` | `16MB` | Largest bundle that `POST /v1/bundles` accepts. It also bounds the multipart request, so it is the one setting to raise |
| `tierwise.server.provider-timeout` | `200ms` | Timeout of one `LevelValueProvider` call. A positive whole number of milliseconds |
| `tierwise.server.latest-cache-ttl` | `0s` | How long one instance remembers the `latest` pointer. `0s` reads the store on every request, so all instances see a publish or promote at once |
| `tierwise.server.runtime-cache-size` | `8` | Loaded bundle versions kept in memory per instance |
| `tierwise.server.max-keys-per-request` | `100` | Largest `keys[]` of one `/v1/resolve` request |
| `tierwise.server.security.reads` | `authenticated` | `open` lets reads (see the endpoint table) go without a token; `authenticated` needs a valid token |
| `tierwise.server.security.publish-role` | `tierwise-publisher` | Role that may `POST /v1/bundles` |
| `tierwise.server.security.promote-role` | `tierwise-promoter` | Role that may promote |
| `tierwise.server.security.read-role` | (empty) | If set, reads also need this role. No effect when reads are `open` |
| `tierwise.server.security.roles-claim` | `roles` | JWT claim with the roles: a name or a dotted path such as `realm_access.roles`; a list of strings or one space-separated string |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` or `.jwk-set-uri` | (none) | **Required** to accept any token. See below |

### Authentication

OIDC/JWT by default: the server is an OAuth2 resource server. Point it at your identity provider:

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

- **There are no default credentials.** There is no user store, no password, no static token. Without a JWT setting the
  server rejects every token (and logs a warning), so reads work only if `reads: open`, and publishing and promoting are
  impossible.
- Roles come from the one configured claim. Scopes grant nothing. A role `r` becomes the authority `ROLE_r`.
- The server keeps no session and uses no cookie. CSRF protection is off because there is no cookie to forge.
- Give the CI identity the publish role only, and a smaller group of people (or a release job) the promote role.
- Reads are authenticated by default. `POST /v1/resolve` and `/v1/decide` count as reads, because they only read. With
  `reads: open` anyone who can reach the server can evaluate and, through providers, cause provider calls. Open reads
  suit a server on a private network.
- `GET /openapi.yaml` is always open.

To replace the rules entirely, define your own `SecurityFilterChain` bean; the default backs off.

### Storage

`BundleStore` (`io.github.tierwise.server.store`) is the storage SPI. The contract is in its Javadoc: bundle bytes are written
once and never modified; a reader sees complete bundles only; publication records are never replaced; the `latest`
pointer is the only mutable datum.

- **Filesystem** (`FilesystemBundleStore`, the default). Layout under the root: `bundles/<64 hex digits>.json`,
  `records/<64 hex digits>/<millis>-<uuid>.json`, `latest`, `tmp/`. Every file is written completely to `tmp/`, flushed,
  and put in place with an atomic step that refuses to overwrite (a hard link, or a move without replace); only `latest` is
  replaced, with an atomic move. Instances can share one directory if the filesystem gives atomic renames and exclusive
  creation (a local disk, or a well-behaved network volume). For anything else use S3.
- **S3**: add the `tierwise-server-s3` module and set `tierwise.server.store=s3`.
- **Your own**: define a `BundleStore` bean. The filesystem store then backs off.

## Deployment

- **Stateless.** Run as many instances as you need behind a load balancer. They share the store and nothing else. No
  sticky sessions, no cluster, no leader. Roll instances one at a time: a new instance reads the same store.
- **Never the source of truth.** Losing the store loses only distribution, not configuration: re-publish from the
  repository (publishing is idempotent). Do not edit files in the store by hand.
- **Consistency.** With the default `latest-cache-ttl=0s`, every request reads the `latest` pointer, so a publish or promote
  on any instance is seen by all at once. A larger TTL trades that for fewer reads of the store (useful for S3); an
  instance still sees its own publish or promote at once. Bundle versions are immutable, so they are cached for good.
- **Polling clients.** `GET /v1/bundles/latest` with `If-None-Match` answers 304 from the pointer alone, without reading
  the bundle, so polling every 30 seconds from many applications is cheap.
- **TLS.** Terminate TLS in front of the server (load balancer, ingress) or with the standard `server.ssl.*` properties.
  Tokens are bearer tokens, so always use TLS outside a private network.
- **Memory.** An instance holds up to `runtime-cache-size` loaded bundles. Bundles are small (kilobytes to a few
  megabytes).
- **Providers.** `/resolve` calls providers from the server's threads, with the timeout above. Run the server where it can
  reach the services behind them, or leave the levels without a provider to get `UNAVAILABLE` contributions.
- **Run it:**

  ```sh
  java -jar tierwise-server-0.1.0-dev.0-exec.jar \
    --spring.security.oauth2.resourceserver.jwt.issuer-uri=https://idp.example.test/realms/ci \
    --tierwise.server.filesystem.root=/var/lib/tierwise
  ```

- **Embed it.** Depend on the plain `tierwise-server` jar from a Spring Boot application. The beans come from the
  auto-configurations `TierwiseServerAutoConfiguration` and `TierwiseSecurityAutoConfiguration`.

## Build and test

```sh
cd java && ./mvnw -B -ntp -pl tierwise-server -am verify
```

The tests cover every endpoint (including 304, 409, authentication denials, promote and rollback), the equality of
`/resolve` and `/decide` with in-process core results on `commerce.json`, provider wiring, the filesystem store
(atomicity, concurrency, never overwriting), the configuration, the OpenAPI document (it describes exactly the endpoints
that exist), and the server on a real Tomcat with real multipart parsing. Tests authenticate with
`spring-security-test` JWT mocks under the `test` profile (`src/test/resources/application-test.yaml`).

### Build notes

- The module imports `junit-bom` 6.0.3 before the Spring Boot BOM, so tests use the JUnit line that Spring Boot 4.1 builds
  on; the parent still imports JUnit 5.14 for the other modules. Keep `version.junit.server` equal to the BOM's
  `junit-jupiter.version` when Spring Boot is upgraded.
- The module pins `jackson-core` to the parent's `version.jackson`, because the Spring Boot BOM manages an older 2.x line
  for the Jackson 2 compatibility modules and the core is built against the newer one.
- The server uses Jackson 3 (`tools.jackson`), which Spring Boot 4 uses. `tierwise-core` keeps Jackson 2's `jackson-core`.

## Decisions

[ADR 0024](../../docs/adr/0024-server-api-and-store-choices.md) settles the choices that requirements section 9 and the
specification leave open: the multipart publish encoding, when a publish moves `latest`, identical records, the response
bodies, no history pagination in 0.1, the server-only error codes and their HTTP statuses, multi-key resolve through the
public API, provider beans matched by bean name, the role defaults, the security starter name, the pointer bytes and the
promotion log. The genericity allow-list (`js/scripts/genericity-allowlist.txt`) has entries for `store`, `web`,
`configuration` and `header`, which the server's package names and SPI use.

