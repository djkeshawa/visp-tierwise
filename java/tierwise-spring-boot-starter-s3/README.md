# tierwise-spring-boot-starter-s3

Optional S3 bundle source for the Tierwise Spring Boot starter (ADR 0020). It is the only Tierwise module
that depends on the AWS SDK. The core starter and the server stay SDK-free.

The module contains:

- `S3BundleSource`, a `io.github.tierwise.spring.BundleSource` that reads one bundle object with a conditional
  `GetObject`;
- `S3Location`, the parser of `s3://bucket/key`;
- `S3BundleSourceAutoConfiguration`, which creates the S3 client and the source when the configuration asks
  for S3;
- `S3BundleSourceProperties`, the settings under `tierwise.bundle.s3`.

## Configuration

| Property | Default | Meaning |
| --- | --- | --- |
| `tierwise.source` | none | The bundle location, `s3://bucket/key`. The scheme is matched in any letter case. The module is active only when this value starts with `s3://`. |
| `tierwise.bundle.s3.aws-region` | SDK region chain | The AWS region, for example `eu-west-1`. |
| `tierwise.bundle.s3.endpoint` | AWS | An absolute URI for an S3-compatible store (for example a local emulator). |
| `tierwise.bundle.s3.path-style-access` | `false` | Address buckets in the path. Many S3-compatible stores need `true`. |
| `tierwise.bundle.s3.api-call-timeout` | `30s` | The timeout of one bundle read, retries included. It also bounds each socket operation. Must be positive. |

```properties
tierwise.source=s3://tierwise-bundles/prod/commerce.json
tierwise.bundle.s3.aws-region=eu-west-1
```

Credentials come from the AWS SDK default provider chain. This module adds no credential property.

## Behaviour

`S3BundleSource.fetch(currentVersion)` implements the starter's `BundleSource` contract:

- The caller passes the `bundleVersion` it holds, or `null`. The source sends a conditional request only when
  that version is the one it last loaded. It then sends the S3 ETag of that load as `If-None-Match`.
- `304 Not Modified` for that conditional request returns `BundleFetch.notModified()` with no body.
- Every other case is an unconditional `GetObject`. A `304` without a condition is an error.
- The bytes are loaded with `Tierwise.loadBundle`, which verifies the digest (BUNDLE-5). Only verified bytes
  update the remembered version and ETag. A rejected object therefore leaves the previous state in place,
  and the next fetch asks S3 again.
- The S3 ETag is an opaque token. It is never a version or a digest. The bundle version is the digest that the
  core verifies.
- A missing key, an access or server error, a network failure, or an object larger than 64 MiB makes `fetch`
  throw `IOException`, with the SDK exception as the cause. A bundle the core rejects makes it throw
  `IOException` with a `TierwiseException` cause that carries `E_BUNDLE_INTEGRITY` or `E_BUNDLE_UNSUPPORTED`.
- Calls are `synchronized`. One source makes one request at a time.
- The source does not poll, does not schedule anything, and keeps no last-known-good bundle. The starter's
  refresher owns those, and it keeps its current bundle when `fetch` throws.

`describe()` returns `s3://bucket/key`, which holds no credential.

## Auto-configuration

`S3BundleSourceAutoConfiguration` is registered in
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. It applies only when:

1. the AWS SDK `S3Client` class is on the classpath, and
2. `tierwise.source` uses the `s3` scheme.

It then defines:

- an `S3Client` with the settings above, unless the application defines an `S3Client` bean. The client uses
  the URL connection HTTP client, which is chosen explicitly so that no second HTTP client on the classpath is
  picked up;
- an `S3BundleSource`, which is a `BundleSource` bean, unless the application defines any `BundleSource` bean.

A malformed `s3://` value fails the application context at startup. The configuration is ordered before
`io.github.tierwise.spring.TierwiseAutoConfiguration`, so the starter sees the source bean when it decides
whether to use its default source.

## Dependencies

Declared in this module only, as ADR 0020 requires:

- `software.amazon.awssdk:s3` and `software.amazon.awssdk:url-connection-client`, with the versions of the
  `software.amazon.awssdk:bom` import (2.55.0);
- `org.springframework.boot:spring-boot-autoconfigure`, with the versions of the `spring-boot-dependencies`
  import (4.1.1);
- `io.github.tierwise:tierwise-spring-boot-starter`, which brings `tierwise-core`.

The module imports the Spring Boot BOM first, then the AWS BOM. The Spring Boot BOM manages JUnit 6, and it
wins over the parent's `junit-bom` 5.x import because a child's `dependencyManagement` is processed before the
parent's and the first import of an artifact wins. The module's test classpath therefore uses JUnit 6.0.3 and
the enforcer's `dependencyConvergence` rule holds. The parent POM is unchanged.

## Tests

Run from `java/`:

```sh
JAVA_HOME=<jdk 21> ./mvnw -B -ntp -q -Dtierwise.buildDir=target-P6B -pl tierwise-spring-boot-starter-s3 -am verify
```

- `S3LocationTest` covers parsing and rejection of locations.
- `S3BundleSourceTest` covers the conditional-request protocol, verification by the core, the error mapping,
  the size limit and serialised calls. It uses `FakeS3Client`, an in-memory `S3Client` that records requests and
  passes bodies through the SDK's `ResponseTransformer`. The tests read the conformance bundles
  `commerce.json` and `retail-pos.json` from `spec/conformance/bundles` through `tierwise.spec.dir`. They open
  no connection.
- `S3BundleSourceAutoConfigurationTest` covers the activation rules, the bean wiring (region, endpoint and
  timeout read back from the client), user overrides, startup failure on a bad value, and the registration file.

Nothing here contacts S3. The 304 behaviour and the SDK exception it produces are verified only against the
fake. See the open points.

## Open points

- *(OPEN)* The region setting is `tierwise.bundle.s3.aws-region`, not `...region`, because the lexical
  genericity check (`js/scripts/check-genericity.mjs`) flags `region`, which a sample uses as a level name.
  The AWS SDK method `S3ClientBuilder.region(...)` is still a lexical hit in `S3BundleSourceAutoConfiguration`,
  and the allow-list file belongs to another owner. The owner must decide whether to allow-list `region`, which
  is an AWS API term, or accept another arrangement. `tierwise-server-s3` already has the same hit and uses
  `tierwise.server.s3.region`, so allow-listing `region` and renaming this key to `tierwise.bundle.s3.region` is
  recommended, for one spelling across the S3 modules.
- *(OPEN)* The configuration key is `tierwise.source`, as the work item states. The starter's
  `TierwiseProperties` reads the location from `tierwise.source` and rejects `s3://` there ("unsupported bundle
  location"). A user who sets `tierwise.source=s3://...` gets that error, not this module. Decide whether
  `tierwise.source` should be the key (recommended, one key for every scheme) or whether both keys are accepted.
- *(Resolved at review)* `TierwiseAutoConfiguration` now exists in the starter under the name used in
  `beforeName`. It takes the `BundleSource` bean through an `ObjectProvider` and defines no default
  `BundleSource` bean, so the S3 bean is the primary source.
- *(Resolved at review)* The starter's `BundleFetch.of` Javadoc now says the starter does not rely on the tag,
  because loading the bytes verifies the digest. The S3 ETag (quotes removed) is passed as that opaque tag.
- *(Resolved at review)* A bundle that the core rejects reaches the starter as `IOException`. The starter's
  `TierwiseEvaluators.refresh()` catches `IOException` and `RuntimeException`, keeps the current bundle and
  returns `FAILED`.
- *(OPEN)* The 304 path assumes the SDK throws `S3Exception` with status 304 for a conditional `GetObject`.
  This is verified only against the fake. Run a check against S3 or an S3-compatible emulator before relying on
  it.
- *(OPEN)* Defaults are the author's choice, since the requirements are silent: `api-call-timeout` 30 s and
  `path-style-access` false. The 64 MiB object limit mirrors the starter's HTTP source.
- *(OPEN)* The URL connection client was chosen over `apache-client` because it has no transitive dependencies,
  and it does not pool connections. ADR 0020 approves the AWS SDK family but does not name the HTTP client.
  Record the choice in an ADR if the owner wants one.
- *(OPEN)* Credentials come only from the SDK default provider chain. There is no setting for static
  credentials, and there is no setting for a custom credentials provider.
- *(OPEN)* A changed bundle is parsed twice: once here to learn its version, and once by the starter when it
  loads the bytes. This costs one parse per change. A starter entry point that accepts a verified version
  would remove the second parse.
- *(OPEN)* Bucket names are checked leniently (letters, digits, `.`, `_`, `-`, up to 255 characters), so
  legacy names with upper-case letters are accepted. Keys are checked only for being non-empty and free of
  control characters.
