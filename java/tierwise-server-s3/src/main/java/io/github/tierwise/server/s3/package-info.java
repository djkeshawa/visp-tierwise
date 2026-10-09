/**
 * Optional S3 bundle store for the Tierwise server (ADR 0020).
 *
 * <p>{@link io.github.tierwise.server.s3.S3BundleStore} keeps canonical bundles, publication records and the
 * latest pointer in a bucket, using conditional writes for every change. {@link
 * io.github.tierwise.server.s3.S3BundleStoreAutoConfiguration} registers it when {@code tierwise.server.store}
 * is {@code s3}. The AWS SDK is used only in this module and in the optional starter module.
 */
package io.github.tierwise.server.s3;
