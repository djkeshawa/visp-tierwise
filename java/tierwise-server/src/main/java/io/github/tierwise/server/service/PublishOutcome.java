package io.github.tierwise.server.service;

import io.github.tierwise.server.store.PublicationRecord;

/**
 * The result of publishing a bundle.
 *
 * @param bundleVersion the {@code bundleVersion} of the published bundle
 * @param newVersion {@code true} when this call stored the bundle, {@code false} when the same bytes were
 *     stored before
 * @param recordAdded {@code true} when this call added the publication record, {@code false} when an
 *     identical record existed already
 * @param madeLatest {@code true} when this call moved the latest pointer to the bundle
 * @param latest the latest {@code bundleVersion} after the call
 * @param record the publication record
 */
public record PublishOutcome(
    String bundleVersion,
    boolean newVersion,
    boolean recordAdded,
    boolean madeLatest,
    String latest,
    PublicationRecord record) {
}
