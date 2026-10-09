# tierwise-spring-boot-starter

Spring Boot 4 auto-configuration for Tierwise (Java 21). It loads a compiled bundle, keeps it fresh,
and gives your application a `Resolver` and a `DecisionEngine` that evaluate in-process. The server
(`tierwise-server`) is never on the hot path: if it is down, the application keeps answering from the
last bundle it loaded.

```xml
<dependency>
  <groupId>io.github.tierwise</groupId>
  <artifactId>tierwise-spring-boot-starter</artifactId>
</dependency>
```

Micrometer is optional. Add `micrometer-core` (Spring Boot Actuator brings it) and a `MeterRegistry` bean to
get the meters below.

## Quick start

```properties
# Poll a tierwise-server (reads GET {url}/v1/bundles/latest)
tierwise.server.url=https://tierwise.internal
# A bundle packaged in the application, used only if the server is unreachable at startup
tierwise.snapshot=classpath:tierwise/bundle.json
```

```java
@Service
class Checkout {
  private final Resolver resolver;
  private final DecisionEngine decisions;

  Checkout(Resolver resolver, DecisionEngine decisions) {
    this.resolver = resolver;
    this.decisions = decisions;
  }

  Object limit(String division) {
    Context context = Context.builder().level("division", division).build();
    return resolver.resolve(context, "checkout.order.allowed_max_lines").value();
  }
}
```

Names such as `division` are examples from your own project. The starter never contains any.

## Beans

| Bean | Purpose |
| --- | --- |
| `TierwiseEvaluators` | The current `Evaluator`, `bundleVersion()`, `staleness()`, `isStale()`, `isUsingSnapshot()`, `lastFailure()` and `refresh()`. Loads the first bundle while the context starts. |
| `Resolver` | `resolve(Context, key)` and `resolveLocal(Context, key)` (never calls a provider). |
| `DecisionEngine` | `decide(Context, feature, FactSet)`. Counts and logs every decision. |
| `TierwiseObserver` | Receives the operational events. A `MicrometerTierwiseObserver` is created when Micrometer and exactly one (or one primary) `MeterRegistry` bean exist. Define your own bean to report elsewhere. |

Every bean backs off when you define one of the same type.

`Resolver` and `DecisionEngine` read the current evaluator once per call. Evaluators are immutable, so a call
that is running when the bundle is swapped finishes on the bundle it started with, and the result's
`bundleVersion` says which one that was.

## Configuration reference

All properties are under `tierwise`. Durations use Spring's format (`200ms`, `30s`, `5m`, or ISO-8601).

| Property | Default | Meaning |
| --- | --- | --- |
| `tierwise.enabled` | `true` | `false` turns the auto-configuration off. |
| `tierwise.source` | none | Bundle location: `classpath:path`, `file:/path/bundle.json`, or an `http://` / `https://` URL of the bundle bytes. |
| `tierwise.server.url` | none | Base URL of a `tierwise-server`. The starter reads `GET {url}/v1/bundles/latest`. Set this or `tierwise.source`, not both. |
| `tierwise.snapshot` | none | `classpath:` or `file:` location of a bundle used **only at startup**, when the primary source cannot supply a loadable bundle. Alone, it is the only source. |
| `tierwise.refresh-interval` | `30s` | Delay between checks of the source. `0` turns scheduled refresh off (call `TierwiseEvaluators.refresh()` yourself). Negative is rejected. |
| `tierwise.staleness-threshold` | `5m` | When the bundle in use counts as stale (`TierwiseEvaluators.isStale()`, and the log message turns from `WARN` to `ERROR`). Must be positive. |
| `tierwise.provider-timeout` | `200ms` | Timeout of one provider call, for a provider with no more specific timeout. |
| `tierwise.provider-timeouts.<level>` | none | Timeout for one level's provider. Level names with dots need `tierwise.provider-timeouts[my.level]`. |
| `tierwise.decision-log-level` | `debug` | Level of the one line logged per decision (`trace`, `debug`, `info`, `warn`, `error`, `fatal`, `off`). In YAML write `"off"` with quotes, because bare `off` is a boolean. |
| `tierwise.http.connect-timeout` | `5s` | Connect timeout for HTTP and server sources. |
| `tierwise.http.request-timeout` | `10s` | Request timeout, including reading the body. |
| `tierwise.http.headers.<name>` | none | Extra request headers, for example `Authorization`. Values are never logged. |

A timeout must be a positive whole number of milliseconds (`1500us` is rejected). The check happens at
startup, with the property name in the message.

### Bundle sources

The primary source is, in this order:

1. a `BundleSource` bean, if the application (or another module, such as the S3 source) defines exactly one
   (mark one `@Primary` if there are several). `tierwise.source` and `tierwise.server.url` are then ignored;
2. `tierwise.source`;
3. `tierwise.server.url`;
4. `tierwise.snapshot`, when it is the only property set.

With none of these, the context fails with `No Tierwise bundle source is configured`.

Built-in sources:

| Location | Class | Change detection |
| --- | --- | --- |
| `classpath:` | `ClasspathBundleSource` | A classpath resource does not change; once you hold the bundle it contains, it reports "not modified". |
| `file:` | `FileBundleSource` | Re-reads only when the file's size or modification time changed, or when you do not hold the bundle it last loaded (so a file that never loaded keeps failing the refresh). |
| `http(s)://` and `tierwise.server.url` | `HttpBundleSource` (JDK `HttpClient`) | Sends `If-None-Match: "<bundleVersion>"`; `304` means unchanged. Redirects are followed except from `https` to `http`. Bodies over 64 MiB are rejected. |

The ETag is the bundle's `bundleVersion` (for example `"sha256:ab12..."`), as `tierwise-server` serves it.
The starter does not trust the header: every body is verified with `Tierwise.loadBundle`, which checks the
digest and the versions. A server that ignores `If-None-Match` and returns the same bundle again is handled:
the loaded `bundleVersion` is compared and nothing is swapped.

#### Writing another source

Implement `BundleSource` and publish it as a bean. The S3 module does this.

```java
public interface BundleSource {
  String describe();                           // for logs: no credentials, no query strings
  BundleFetch fetch(String currentVersion) throws IOException;
}
```

`fetch` receives the `bundleVersion` the starter holds (`null` before the first load). Return
`BundleFetch.notModified()` when the source can tell cheaply that it still has that version, otherwise
`BundleFetch.of(bytes, etag)`. Throw `IOException` when the source is unreachable or has no bundle. When
`currentVersion` is `null` the source must return bytes.

## Startup, refresh and last-known-good

* **Startup fails fast.** The first bundle is loaded while the context starts. If the primary source fails (not
  reachable, bad status, a bundle that fails the integrity or version checks, or a bundle that needs a provider
  you did not register) and no snapshot is configured, startup fails with `BundleLoadException`. With
  `tierwise.snapshot` the application starts from the snapshot instead, logs a warning, and reports
  `isUsingSnapshot()`. It leaves the snapshot at the first successful refresh from the primary source.
  If the snapshot fails too, startup fails.
* **Refresh.** A daemon thread checks the source every `tierwise.refresh-interval`. A new bundle is loaded and an
  evaluator is created in full before one atomic write publishes it. A rollback (the server promotes an older
  version) is an ordinary swap.
* **Last-known-good.** Any failure during a refresh (network, status, corrupt or unsupported bundle, a new bundle
  that needs a different set of providers) keeps the current evaluator, adds to
  `tierwise.refresh.failures`, records `lastFailure()` and logs a warning. The next scheduled refresh tries
  again.
* **Staleness** is the time since the bundle in use was last confirmed current: loaded, or confirmed unchanged by a
  refresh (a `304`). A bundle carries no publication time, so this is the only age a client can know. Past
  `tierwise.staleness-threshold`, `isStale()` is `true` and each failed refresh logs at `ERROR`.

## Providers

A service-sourced level takes its values from a provider. Declare each as a bean that names its level:

```java
@Bean
TierwiseProvider accountProvider(AccountClient accounts) {
  return TierwiseProvider.of("account", nodeId -> accounts.limitsFor(nodeId));
}
```

or implement `TierwiseProvider` (`level()`, `valuesFor(nodeId)`, optionally `timeout()`). Timeout precedence:
`tierwise.provider-timeouts.<level>`, then `TierwiseProvider.timeout()`, then `tierwise.provider-timeout`
(200 ms).

The core evaluator requires exactly one provider for each service-sourced level the bundle declares. A missing
provider fails startup with `E_PROVIDER_MISSING`; an extra or duplicate one fails with `E_EVALUATOR_OPTIONS`. A
provider that throws, returns something invalid, or does not answer within its timeout does not fail the call.
The key follows its declared behaviour: under `skip` the level contributes nothing, under `deny` it
contributes the most restrictive value (`false`, `[]` or the declared minimum). The result's provenance lists the
failure as `UNAVAILABLE` or `INVALID`. Decisions never call a provider.

Provider calls run on the core evaluator's shared daemon pool, so thread-locals (security context, MDC) do not
reach a provider. Capture what you need when you create the provider.

Application `EvaluationListener` beans also receive the evaluator's events (each is called, in order, and one that
throws does not stop the others).

## Metrics

Registered when `micrometer-core` is on the classpath and exactly one (or one primary) `MeterRegistry` bean exists.

| Meter | Type | Tags | Meaning |
| --- | --- | --- | --- |
| `tierwise.bundle.version` | gauge, always 1 | `version` | The `bundleVersion` in use. Only the current version is registered; the old one is removed at a swap. |
| `tierwise.bundle.age` | gauge, seconds | none | Staleness: seconds since the bundle in use was last confirmed current. Alert on it, for example above 300. |
| `tierwise.refresh.failures` | counter | none | Refreshes that failed. |
| `tierwise.decisions` | counter | `feature`, `reason` | Decisions. `reason` is `RULE`, `DEFAULT` or `MISSING_FACT`. |
| `tierwise.provider.failures` | counter | `level`, `outcome` | Keys whose provider call failed (`unavailable`: threw or timed out) or returned an invalid value (`invalid`). One count per affected key. |

## Logging

Each decision is logged by the logger `io.github.tierwise.spring.DecisionEngine` at `tierwise.decision-log-level`:

```
tierwise decision feature=checkout.order_submission reason=RULE rule=express-ok effect=ALLOW bundleVersion=sha256:...
```

The line holds the feature, the reason, the rule that won, the effect when it is a plain string, and the
`bundleVersion`. It never holds the context or the facts. Bundle loads, swaps and refresh failures are logged by
`io.github.tierwise.spring.TierwiseEvaluators`.

## Tests

`mvnw -pl tierwise-spring-boot-starter -am verify` runs the unit tests and the integration tests against a stub
HTTP server built on the JDK's `com.sun.net.httpserver` (ETag and `304`, atomic swap under concurrent
evaluation, last-known-good while the server is down, startup failure without a bundle, startup from a
snapshot, provider timeouts under `skip` and `deny` for keys with custom kinds and keys without a kind, and the
meters). The provider tests read the conformance bundles under `spec/conformance/bundles`.
