# Runtime and operations guide

This guide is for the people who run services that evaluate Tierwise bundles. It covers the Spring Boot starter, the
`@visplabs/node` loader, how a bundle is refreshed, what happens when the source of the bundle is down, the
metrics to watch, and how providers for service-sourced levels behave.

The principle behind all of it: **evaluation is in-process, and the source of bundles is never on the hot path.**
A service holds a compiled bundle in memory and answers `resolve` and `decide` from it. A bundle server, an HTTP
endpoint or an object store only has to be reachable when the service looks for a newer bundle. If it is down, the
service keeps answering from the bundle it has.

Names such as `division` and `checkout.order.allowed_max_lines` in this guide come from the `commerce` sample
project. They are examples.

## How a bundle gets to a service

```text
git ──► CI: validate · test · compile ──► bundle.json ──► a source ──► your service
                                                           │
                                  tierwise-server, any HTTP endpoint, an S3 object,
                                  a file, or the classpath of the application
```

| Source | Starter | `@visplabs/node` | Change detection |
| --- | --- | --- | --- |
| `tierwise-server` (`GET /v1/bundles/latest`) | `tierwise.server.url` | `url` | `ETag` and `If-None-Match`; the server answers `304` from its pointer alone |
| Any HTTP(S) URL of the bundle bytes | `tierwise.source=https://…` | `url` | `ETag` and `If-None-Match`, if the endpoint supports them; otherwise the loaded `bundleVersion` is compared |
| A file | `tierwise.source=file:/path/bundle.json` | `snapshot: { path }` for a start-up fallback | Size and modification time |
| The classpath | `tierwise.source=classpath:tierwise/bundle.json` | not applicable | A classpath resource does not change |
| An S3 object | `tierwise.source=s3://bucket/key` with `tierwise-spring-boot-starter-s3` | not available; fetch with your own `fetch` | The S3 entity tag, sent as `If-None-Match` |

Whatever the source says, a service trusts the bundle only after it has verified it: `Tierwise.loadBundle` in Java
and `loadBundle` in JavaScript recompute the digest, check the bundle format and spec versions, and check the
structure. A header such as `ETag` is a hint to skip work and is never believed. What the digest proves, and what it
does not, is in the [security model](security-model.md).

## The Spring Boot starter

`tierwise-spring-boot-starter` targets Spring Boot 4.1 on Java 21. It loads a bundle while the application context
starts, keeps it fresh, and gives you a `Resolver` and a `DecisionEngine`.

```xml
<dependency>
  <groupId>io.github.visplabs</groupId>
  <artifactId>tierwise-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

*(OPEN)* The artifact is not on Maven Central yet. Until the first release, install it from a checkout with
`cd java && ./mvnw -B -ntp -DskipTests install`.

Point it at a source, and optionally at a snapshot for start-up when the source is down:

```properties
# Poll a tierwise-server. The starter reads GET {url}/v1/bundles/latest
tierwise.server.url=https://tierwise.internal
# A bundle packaged in the application. Used only if no bundle loads at start-up.
tierwise.snapshot=classpath:tierwise/bundle.json
```

Then inject the beans:

```java
@Service
class Checkout {
  private final Resolver resolver;
  private final DecisionEngine decisions;

  Checkout(Resolver resolver, DecisionEngine decisions) {
    this.resolver = resolver;
    this.decisions = decisions;
  }

  long maxLines(String division) {
    Context context = Context.builder().level("division", division).build();
    return ((Number) resolver.resolve(context, "checkout.order.allowed_max_lines").value()).longValue();
  }

  boolean mayEnter(String division, FactSet facts) {
    Context context = Context.builder().level("division", division).build();
    return "ALLOW".equals(decisions.decide(context, "checkout.order_submission", facts).effect());
  }
}
```

### Beans

| Bean | Purpose |
| --- | --- |
| `TierwiseEvaluators` | The current `Evaluator`, `bundleVersion()`, `staleness()`, `isStale()`, `isUsingSnapshot()`, `lastFailure()` and `refresh()`. Loads the first bundle while the context starts |
| `Resolver` | `resolve(Context, key)` and `resolveLocal(Context, key)`, which never calls a provider |
| `DecisionEngine` | `decide(Context, feature, FactSet)`. Counts and logs every decision |
| `TierwiseObserver` | Receives the operational events. A `MicrometerTierwiseObserver` is created when Micrometer and one `MeterRegistry` exist. Define your own bean to report elsewhere |

Every bean backs off when you define one of the same type. `Resolver` and `DecisionEngine` read the current evaluator
once per call. Evaluators are immutable, so a call that is running when the bundle is swapped finishes on the bundle
it started with, and the `bundleVersion` of its result says which one that was.

### Configuration

All properties are under `tierwise`. Durations use Spring's format (`200ms`, `30s`, `5m`, or ISO-8601).

| Property | Default | Meaning |
| --- | --- | --- |
| `tierwise.enabled` | `true` | `false` turns the auto-configuration off |
| `tierwise.source` | none | Bundle location: `classpath:path`, `file:/path/bundle.json`, an `http://` or `https://` URL of the bundle bytes, or `s3://bucket/key` with the S3 module |
| `tierwise.server.url` | none | Base URL of a `tierwise-server`. Set this or `tierwise.source`, not both |
| `tierwise.snapshot` | none | `classpath:` or `file:` location of a bundle used **only at start-up**, when the primary source cannot supply a loadable bundle. Alone, it is the only source |
| `tierwise.refresh-interval` | `30s` | Delay between checks of the source. `0` turns scheduled refresh off (call `TierwiseEvaluators.refresh()` yourself). Negative is rejected |
| `tierwise.staleness-threshold` | `5m` | When the bundle in use counts as stale. Must be positive |
| `tierwise.provider-timeout` | `200ms` | Timeout of one provider call, for a provider with no more specific timeout |
| `tierwise.provider-timeouts.<level>` | none | Timeout for one level's provider. A level name with dots needs `tierwise.provider-timeouts[my.level]` |
| `tierwise.decision-log-level` | `debug` | Level of the one line logged per decision: `trace`, `debug`, `info`, `warn`, `error`, `fatal` or `off`. In Spring's YAML write `"off"` with quotes |
| `tierwise.http.connect-timeout` | `5s` | Connect timeout for HTTP and server sources |
| `tierwise.http.request-timeout` | `10s` | Request timeout, including reading the body |
| `tierwise.http.headers.<name>` | none | Extra request headers, for example `Authorization`. Values are never logged |

A timeout must be a positive whole number of milliseconds; `1500us` is rejected at start-up, with the property name in the
message. The primary source is, in this order: a `BundleSource` bean, `tierwise.source`, `tierwise.server.url`, and
`tierwise.snapshot` when it is the only property set. With none of these, the context fails with
`No Tierwise bundle source is configured`.

A server that needs a token:

```properties
tierwise.server.url=https://tierwise.internal
tierwise.http.headers.Authorization=Bearer ${TIERWISE_TOKEN}
```

### Start-up, refresh and last-known-good

- **Start-up fails fast.** The first bundle loads while the context starts. If the primary source fails (unreachable,
  a bad status, a bundle that fails the integrity or version checks, or a bundle that needs a provider you did not
  register) and no snapshot is configured, start-up fails with `BundleLoadException`. A service that cannot know its
  limits should not start and guess.
- **A snapshot makes start-up survive an outage.** With `tierwise.snapshot`, the application starts from the snapshot,
  logs a warning, reports `isUsingSnapshot()` and leaves the snapshot at the first successful refresh from the primary
  source. If the snapshot fails too, start-up fails.
- **Refresh.** A daemon thread checks the source every `tierwise.refresh-interval`. A new bundle is loaded and an
  evaluator is created in full before one atomic write publishes it. A rollback on the server (it promotes an older
  version) is an ordinary swap.
- **Last-known-good.** Any failure during a refresh (network, status, a corrupt or unsupported bundle, a new bundle
  that needs a different set of providers) keeps the current evaluator, counts in `tierwise.refresh.failures`, is
  recorded in `lastFailure()` and is logged. The next scheduled refresh tries again.
- **Staleness** is the time since the bundle in use was last confirmed current: loaded, or confirmed unchanged by a
  refresh (`304`). A bundle carries no publication time, so this is the only age a client can know. Past
  `tierwise.staleness-threshold`, `isStale()` is `true` and each failed refresh logs at `ERROR` instead of `WARN`.

**Example (non-normative):** the log of an application whose server goes down. The threshold is 3 s, the interval 1 s.
The answers keep coming from the bundle in use.

```text
Tierwise bundle sha256:6aaf2221975c62b5fdc71f4e1823745ed896f94609e3deb82eb81ca53f06d948 loaded from http://127.0.0.1:57779/v1/bundles/latest
WARN  Tierwise bundle refresh from http://127.0.0.1:57779/v1/bundles/latest failed, keeping bundle sha256:6aaf2221975c…
WARN  Tierwise bundle refresh from http://127.0.0.1:57779/v1/bundles/latest failed, keeping bundle sha256:6aaf2221975c…
ERROR Tierwise bundle refresh from http://127.0.0.1:57779/v1/bundles/latest failed, keeping bundle sha256:6aaf2221975c…
```

(The host is a local test server, and the messages are shortened.) And with a snapshot configured while the server is
down at start-up:

```text
WARN  No Tierwise bundle could be loaded from http://127.0.0.1:57779/v1/bundles/latest (ConnectException); starting from the snapshot file:…
```

### Providers

A service-sourced level takes its values from a provider. Declare one bean for each such level, naming the level:

```java
@Bean
TierwiseProvider accountProvider(AccountClient accounts) {
  return TierwiseProvider.of("account", nodeId -> accounts.limitsFor(nodeId));
}
```

or implement `TierwiseProvider` (`level()`, `valuesFor(nodeId)`, and optionally `timeout()`). The timeout is, in this
order: `tierwise.provider-timeouts.<level>`, then `TierwiseProvider.timeout()`, then `tierwise.provider-timeout`.

The evaluator requires exactly one provider for each service-sourced level of the bundle. A missing one fails
start-up, and so does an extra or a duplicate one:

```text
… No Tierwise bundle could be loaded from file:… and no snapshot is configured (tierwise.snapshot): E_PROVIDER_MISSING: no provider is registered for the service-sourced level account
```

This applies to a refresh too: a new bundle that declares another service-sourced level is not swapped in until you
deploy the provider for it, and the old bundle keeps serving.

A provider that throws, returns something invalid or does not answer within its timeout does not fail the call. The
key follows its declared behaviour: under `skip` the level contributes nothing, under `deny` it contributes the most
restrictive value. The provenance lists the failure as `UNAVAILABLE` or `INVALID`. See the
[modeling guide](modeling.md#7-levels-whose-values-come-from-a-service).

**Example (non-normative):** the `account` provider throws. The key `checkout.container_order.allowed` (kind
`capability`, which denies on failure) resolves to `false`, and says why:

```text
container_order.allowed = false [Contribution[scope=LEVEL, name=account, node=Optional[a-1], value=Optional[false], effect=UNAVAILABLE, onSourceFailure=Optional[deny]]]
```

Two things to know about providers. They run on the evaluator's own daemon threads, so thread-locals (a security
context, an MDC) do not reach them: capture what you need when you create the provider. And `decide` never calls one.

### Metrics

Registered when `micrometer-core` is on the class path and exactly one (or one primary) `MeterRegistry` bean exists.

| Meter | Type | Tags | Meaning |
| --- | --- | --- | --- |
| `tierwise.bundle.version` | gauge, always 1 | `version` | The `bundleVersion` in use. Only the current version is registered |
| `tierwise.bundle.age` | gauge, seconds | none | Staleness: seconds since the bundle in use was last confirmed current |
| `tierwise.refresh.failures` | counter | none | Refreshes that failed |
| `tierwise.decisions` | counter | `feature`, `reason` | Decisions. `reason` is `RULE`, `DEFAULT` or `MISSING_FACT` |
| `tierwise.provider.failures` | counter | `level`, `outcome` | Keys whose provider call failed (`unavailable`) or returned an invalid value (`invalid`) |

Alert on `tierwise.bundle.age` rising (for example above 300 seconds), not on one failed refresh: a single failure
costs nothing, and many failures in a row mean the service is running on an old bundle. Watch
`tierwise.bundle.version` across instances to see a rollout, or a rollback, finish. A rising
`tierwise.provider.failures` for a level means that its keys are being answered by their failure behaviour.

### Logging

Each decision is logged by the logger `io.github.visplabs.tierwise.spring.DecisionEngine` at `tierwise.decision-log-level`:

```text
tierwise decision feature=checkout.order_submission reason=RULE rule=express-ok effect=ALLOW bundleVersion=sha256:…
```

The line holds the feature, the reason, the rule that won, the effect when it is a plain string, and the
`bundleVersion`. It never holds the context or the facts. Bundle loads, swaps and refresh failures are logged by
`io.github.visplabs.tierwise.spring.TierwiseEvaluators`.

### Another source

Implement `BundleSource` and publish it as a bean:

```java
public interface BundleSource {
  String describe();                           // for logs: no credentials, no query strings
  BundleFetch fetch(String currentVersion) throws IOException;
}
```

`fetch` receives the `bundleVersion` the starter holds (`null` before the first load). Return
`BundleFetch.notModified()` when the source can tell cheaply that it still has that version, otherwise
`BundleFetch.of(bytes, tag)`. Throw `IOException` when the source is unreachable or has no bundle. The S3 module does
this.

### S3 as a bundle source

Add `tierwise-spring-boot-starter-s3` and set the location:

```properties
tierwise.source=s3://tierwise-bundles/prod/commerce.json
tierwise.bundle.s3.aws-region=eu-west-1
```

| Property | Default | Meaning |
| --- | --- | --- |
| `tierwise.source` | none | `s3://bucket/key`. The module is active only when the value starts with `s3://` |
| `tierwise.bundle.s3.aws-region` | the SDK's region chain | The AWS region |
| `tierwise.bundle.s3.endpoint` | AWS | An absolute URI for an S3-compatible store |
| `tierwise.bundle.s3.path-style-access` | `false` | Address buckets in the path; many S3-compatible stores need `true` |
| `tierwise.bundle.s3.api-call-timeout` | `30s` | The timeout of one bundle read, retries included. Must be positive |

Credentials come from the AWS SDK default provider chain; the module has no credential property. The source does not
poll and keeps no last-known-good bundle: the starter's refresher owns both.

*(OPEN)* This module has been tested only against an in-memory fake of the S3 client, and it contacts no S3 in its
tests. The `304` path of a conditional `GetObject` has not been checked against S3 or an S3-compatible emulator.
Run a check against your store before you rely on it. The module's other open points are in
`java/tierwise-spring-boot-starter-s3/README.md`.

## `@visplabs/node`

`@visplabs/node` is the same behaviour on Node 20 or later, with the built-in `fetch` and no other runtime dependency
than `@visplabs/evaluator`. It is for servers. (A browser runs the evaluator, not the loader, and only to display; see
the [security model](security-model.md).)

```ts
import { createBundleLoader } from '@visplabs/node';

const loader = createBundleLoader({
  url: 'https://tierwise.internal/v1/bundles/latest',
  intervalMs: 30_000,
  staleAfterMs: 300_000,
  // The loader sends only If-None-Match. Add credentials with your own fetch.
  fetch: (url, init) =>
    fetch(url, { ...init, headers: { ...init?.headers, Authorization: 'Bearer ' + token() } }),
  evaluatorOptions: {
    // One provider for each service-sourced level of the bundle, each with its own timeout.
    providers: { account: { fn: async (nodeId) => fetchAccountValues(nodeId), timeoutMs: 200 } },
    listener: (event) => metrics.record(event),
  },
  onError: (error) => log.warn({ err: error }, 'bundle refresh failed'),
});

await loader.start(); // resolves after the first load; rejects if that load fails

const ctx = { levels: { division: 'south' } };
const r = await loader.resolve(ctx, 'checkout.order.allowed_max_lines');
const d = await loader.decide(ctx, 'checkout.order_submission', { facts: { 'customer.active': true } });

const status = loader.status();
status.bundleVersion; // the version being served
status.stalenessMs; // milliseconds since the server last confirmed that version
status.stale; // true before a confirmed check, or when stalenessMs exceeds staleAfterMs

loader.stop(); // at shutdown
```

`resolve`, `resolveSync`, `decide` and `decideSync` take the arguments and return the results of the evaluator's
methods. Each call uses the evaluator of the bundle that is current when the call is made, and a call that has started
finishes on that evaluator. To pin one bundle across several calls, for example across one request, hold
`loader.current()`.

### Options

| Option | Default | Meaning |
| --- | --- | --- |
| `url` | required | An `http:` or `https:` URL. It answers `200` with the bundle bytes, or `304` when the entity tag matches |
| `intervalMs` | `30000` | The wait between two checks, counted from the end of one check to the start of the next, so checks never overlap |
| `staleAfterMs` | `300000` | The age of the last confirmed check after which the bundle counts as stale |
| `requestTimeoutMs` | `10000` | The time allowed for one request, the body included |
| `fetch` | `globalThis.fetch` | The fetch function. A replacement has the same signature |
| `evaluatorOptions` | none | The `providers` and `listener` of every evaluator that the loader makes |
| `snapshot` | none | `{ bytes }` or `{ path }`: a bundle to serve before the first check |
| `onError` | none | Called with each failed refresh, including a failed first load |

A wrong option raises a `TypeError` when the loader is created.

### Start-up, refresh and last-known-good

- **Entity tag.** The held version is sent as `If-None-Match: "<bundleVersion>"`. The version in the bundle is the
  truth; the `ETag` header of a response is not read. A `304` confirms the held version.
- **Atomic swap.** A new bundle is checked by `loadBundle`, and its evaluator is made with `evaluatorOptions`, before it
  replaces the current one. The replacement is one assignment.
- **Last known good.** A refresh that fails keeps the current bundle, reports the error to `onError` and counts it in
  `status().refreshFailures`. A failure is a network error, a timeout, a status other than `200` or `304`, a response
  that is not a valid bundle (`E_BUNDLE_INTEGRITY`, `E_BUNDLE_UNSUPPORTED`), or a bundle that the options cannot serve
  (`E_PROVIDER_MISSING`, `E_EVALUATOR_OPTIONS`). Checks continue at the normal interval, with no back-off.
- **Fail fast.** Without a snapshot, `start()` makes the first check and waits for it. It rejects with the first
  failure and arms no poll. With a snapshot, `start()` resolves once the snapshot is installed and the first check runs
  in the background. A snapshot is not a confirmation: `stale` stays `true` until the server answers.
- **Stopping.** `stop()` ends polling and aborts a request in flight. It is final. Calls keep working on the last
  bundle after a stop.

**Example (non-normative):** a loader polling every 500 ms against a server that promotes another version and is then
stopped. The staleness threshold is 3 s. The values are the answers of `checkout.order.allowed_max_lines`.

```text
start value=100 version=sha256:ab1cd3a stale=false failures=0
tick12 value=100 version=sha256:6aaf222 stale=false failures=0      (the server promoted another version)
refresh failed: E_BUNDLE_FETCH E_BUNDLE_FETCH: the request failed: fetch failed   (the server was stopped)
tick16 value=100 version=sha256:6aaf222 stale=false failures=3
tick20 value=100 version=sha256:6aaf222 stale=true failures=7
```

The swap shows as the new version. The outage shows as `failures` counting up, and later as `stale=true`. The answers
never stopped.

### Errors

The errors of the bundle checks are the evaluator's `TierwiseError`, with the evaluator's codes. The loader's own
errors are `BundleLoaderError`, with a `code` that is local to this package:

| Code | Raised by |
| --- | --- |
| `E_BUNDLE_FETCH` | A request failed, timed out, or answered with a status other than `200` or `304` (`status` is set for a status) |
| `E_BUNDLE_NOT_LOADED` | `resolve`, `resolveSync`, `decide` or `decideSync` before any bundle is loaded |
| `E_LOADER_STOPPED` | `start` or `refresh` after `stop()` |

### Metrics

The loader has no metrics library. Export `status()` yourself: `bundleVersion` as a label, `stalenessMs` or
`Date.now() - loadedAt` as the bundle age, and `refreshFailures` as a counter. Log the `bundleVersion` of each
`decide` result to tie a decision to the bundle that made it.

## Operating it

### What to watch

| Signal | Where | Means |
| --- | --- | --- |
| Bundle age | `tierwise.bundle.age`; `status().stalenessMs` | How long since the source last confirmed the bundle. Alert above your tolerance |
| Refresh failures | `tierwise.refresh.failures`; `status().refreshFailures` | The source is unreachable or serves a bad bundle |
| Bundle version per instance | `tierwise.bundle.version`; `status().bundleVersion` | A rollout is partial, or an instance cannot refresh |
| Provider failures | `tierwise.provider.failures`; the listener events `SOURCE_UNAVAILABLE` and `SOURCE_INVALID` | Keys are being answered by their failure behaviour |
| Decisions by reason | `tierwise.decisions` | A rise in `MISSING_FACT` means callers stopped sending facts; a rise in `DEFAULT` means rules stopped matching |

### Roll out, roll back

A bundle is immutable and named by its digest. To roll forward, publish a new bundle. To roll back, promote an older
one on the server (see the [server guide](server.md#roll-back-and-forward)); clients that poll see the new version at their
next check and swap to it. Without a server, publish the older bundle bytes to the same place. Because each instance
swaps atomically, a fleet moves from one version to another without any request seeing a mixture within one call.
Different instances may be on different versions for up to one refresh interval.

### Package a snapshot

A snapshot lets a service start while the source is down. Build it from the same compiled bundle that CI publishes
(`tierwise compile -o src/main/resources/tierwise/bundle.json`, or copy the artifact of the build into the image),
and keep it recent: it is used only at start-up, and the first successful refresh replaces it. A snapshot is a
bundle like any other; it is verified when it loads.

### Choose the failure behaviour of keys on purpose

Whether a provider outage is safe for your service is decided in the schema, not at run time. A limit that a service
sets should `deny`. A grant should `skip`. The [modeling guide](modeling.md#7-levels-whose-values-come-from-a-service)
says how.

### Troubleshooting

| You see | Cause and fix |
| --- | --- |
| `No Tierwise bundle source is configured` | Set `tierwise.source`, `tierwise.server.url` or `tierwise.snapshot`, or define a `BundleSource` bean |
| `BundleLoadException: No Tierwise bundle could be loaded from … and no snapshot is configured` | The source is unreachable, or its bundle is invalid, at start-up. The cause follows the message. Add a snapshot if the application should start anyway |
| `E_PROVIDER_MISSING` at start-up or in a refresh failure | The bundle declares a service-sourced level that has no `TierwiseProvider` (or `providers` entry) |
| `E_EVALUATOR_OPTIONS` | A provider for a level the bundle does not declare, a duplicate, or a missing or non-positive timeout |
| `E_BUNDLE_INTEGRITY` | The bytes are not a valid canonical bundle, or the digest does not match. Check what the source serves, for example a proxy that rewrites the body |
| `E_BUNDLE_UNSUPPORTED` | The bundle's spec or bundle format version is not supported by this runtime. Upgrade the runtime before the bundle |
| `E_UNKNOWN_NODE` on a resolve | The context names a node that a closed catalog does not list. Fix the caller, or use `on_unknown_node: ignore` in the schema if skipping is right |
| `tierwise.bundle.age` rising with no refresh failures | The source answers `304` forever: is something publishing? Compare the version on the source with `tierwise.bundle.version` |

## Open points

- *(OPEN)* The starter is not on Maven Central until the first release.
- *(OPEN)* The S3 bundle source has been tested only against a fake of the S3 client; its `304` path is unchecked against S3.
