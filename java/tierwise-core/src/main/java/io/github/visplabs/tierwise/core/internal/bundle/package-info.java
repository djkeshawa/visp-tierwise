/**
 * Bundle reading and writing (specification section 10).
 *
 * <p>{@link io.github.visplabs.tierwise.core.internal.bundle.BundleReader} applies the checks of EVAL-14 in order: the strict
 * parse and the envelope, the versions, the digest, and then the structure (BUNDLE-118 to BUNDLE-136). It builds the
 * immutable {@link io.github.visplabs.tierwise.core.internal.model.Model}. {@link io.github.visplabs.tierwise.core.internal.bundle.BundleWriter}
 * writes the same model back to canonical bytes and the {@code bundleVersion} that names them.
 */
package io.github.visplabs.tierwise.core.internal.bundle;
