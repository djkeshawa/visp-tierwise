package io.github.visplabs.tierwise.server.service;

import java.util.Optional;

/**
 * The result of promoting a stored version.
 *
 * @param latest the {@code bundleVersion} that is the latest now
 * @param previous the {@code bundleVersion} that was the latest before, if there was one
 */
public record PromoteOutcome(String latest, Optional<String> previous) {
}
