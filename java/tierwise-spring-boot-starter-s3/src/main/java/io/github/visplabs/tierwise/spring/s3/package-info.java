/**
 * Optional S3 bundle source for the Tierwise Spring Boot starter (ADR 0020).
 *
 * <p>{@link io.github.visplabs.tierwise.spring.s3.S3BundleSource} reads one bundle object with a conditional
 * {@code GetObject}, so an unchanged bundle costs one request that returns no body. The bytes it reads
 * are verified by {@code Tierwise.loadBundle} before the source keeps the new ETag. The
 * {@link io.github.visplabs.tierwise.spring.s3.S3BundleSourceAutoConfiguration} creates the source only when
 * {@code tierwise.source} names an {@code s3://} location and the AWS SDK is on the classpath.
 */
package io.github.visplabs.tierwise.spring.s3;
