/**
 * Canonical JSON for bundles.
 *
 * <p>{@link io.github.visplabs.tierwise.core.internal.json.CanonicalJson} writes the JSON Canonicalization Scheme for the
 * integer-only subset that a bundle uses (BUNDLE-7 to BUNDLE-13). {@link io.github.visplabs.tierwise.core.internal.json.StrictJson}
 * reads a document strictly: it rejects duplicate member names at any depth, non-integer and out-of-range numbers,
 * unpaired surrogates and trailing data (BUNDLE-111, BUNDLE-116). {@link io.github.visplabs.tierwise.core.internal.json.Sha256}
 * computes the digest that names a bundle (BUNDLE-6).
 */
package io.github.visplabs.tierwise.core.internal.json;
