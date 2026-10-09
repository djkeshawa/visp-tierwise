# ADR 0020: Runtime integration, server and OpenFeature dependencies

- Status: accepted. The dependency families were approved by the owner under ADR 0012's "later" row. The optional S3 modules were approved on 2026-10-09.
- Date: 2026-10-09
- Amends: requirements §3.2 and §13 (Spring Boot major version)

## Decisions

| Area | Choice | Notes |
| --- | --- | --- |
| Spring Boot | **4.1.1** (BOM `spring-boot-dependencies`) | The requirements said 3.x. Spring Boot 3.5's open-source support ended in mid-2026, and starting a new project on an end-of-life line would contradict the supply-chain goals. Boot 4 requires Java 17+, and the starter and server target Java 21 as planned. |
| Micrometer | Managed by the Spring Boot BOM | Used only in the starter |
| Spring Security (OIDC/JWT) | `spring-boot-starter-security-oauth2-resource-server` (the Boot 4 name), managed by the BOM | Server authentication |
| OpenFeature Java | `dev.openfeature:sdk` **1.22.1** | 1.23.0 is less than two weeks old |
| OpenFeature JS | `@openfeature/server-sdk` 1.23.0, `@openfeature/web-sdk` 1.10.0 and `@openfeature/core` 1.12.0, as **peer** dependencies of `@tierwise/openfeature` | Applications bring their own SDK. `@tierwise/evaluator` stays dependency-free. |
| AWS SDK v2 | `software.amazon.awssdk:s3` **2.55.0** (BOM), only in the optional modules `tierwise-spring-boot-starter-s3` and `tierwise-server-s3` | Owner decision 2026-10-09. The core starter and server stay SDK-free. |
| Testing | `spring-boot-starter-test` (BOM). The stub HTTP server uses the JDK's `com.sun.net.httpserver` | No Testcontainers. The S3 modules are tested against an in-process fake implementing the storage SPI, plus unit tests of request building. |

Versions follow ADR 0012's rule: the latest stable release that is at least two weeks old.
