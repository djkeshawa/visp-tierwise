/**
 * The storage interface of the server and its filesystem implementation.
 *
 * <p>A store keeps three kinds of data: bundle bytes keyed by {@code bundleVersion}, publication records, and
 * one pointer to the latest version. Bundle bytes and publication records are written once and never
 * modified; only the pointer moves ({@link io.github.tierwise.server.store.BundleStore}).
 */
package io.github.tierwise.server.store;
