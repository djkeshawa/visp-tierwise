package io.github.tierwise.server.service;

/**
 * A stored bundle.
 *
 * @param version the {@code bundleVersion}
 * @param bytes the exact bytes that were published; they are served unchanged
 */
public record StoredBundle(String version, byte[] bytes) {
}
