package io.github.visplabs.tierwise.server.store;

import java.util.List;
import java.util.Optional;

/**
 * Where the server keeps bundles, publication records and the pointer to the latest version.
 *
 * <p>The contract that every implementation (filesystem, S3, ...) keeps:
 *
 * <ul>
 *   <li><b>Bundle bytes are written once.</b> {@link #putBundle} stores the exact bytes it is given and never
 *       modifies them afterwards. A second call for the same version with the same bytes changes nothing; a
 *       second call with different bytes fails with {@link BundleConflictException} and leaves the stored
 *       bytes as they were.
 *   <li><b>A reader sees complete bundles only.</b> A bundle is visible after {@code putBundle} returns, and
 *       never partly written (an atomic write).
 *   <li><b>Publication records are never replaced.</b> {@link #addRecord} adds one record; it removes and
 *       overwrites none (BUNDLE-148). Several records may carry one {@code bundleVersion}.
 *   <li><b>The latest pointer</b> is the only mutable datum. {@link #setLatest} replaces it atomically.
 * </ul>
 *
 * <p>The server never keeps state of its own between requests, so an implementation must be safe for
 * concurrent use by several threads and, for a shared store, by several server instances. Every method throws
 * {@link BundleStoreException} when the storage cannot be reached or written.
 *
 * <p>Versions passed to a store are always of the form {@code sha256:} followed by 64 lower-case hexadecimal
 * digits (BUNDLE-5); the server validates them before it calls the store. An implementation must still
 * reject any other string with {@link IllegalArgumentException}, so a store never maps a version to a path
 * or key outside its own area.
 */
public interface BundleStore {

  /**
   * Stores the bytes of a bundle under its version, if the version is not stored yet.
   *
   * @param version the {@code bundleVersion}
   * @param bytes the exact canonical bytes of the bundle
   * @return {@code true} when this call stored the bytes, {@code false} when the same bytes were already
   *     stored
   * @throws BundleConflictException when different bytes are already stored under the version
   * @throws BundleStoreException when the storage fails
   */
  boolean putBundle(String version, byte[] bytes);

  /**
   * Reads the bytes of a stored bundle.
   *
   * @param version the {@code bundleVersion}
   * @return the exact bytes that {@link #putBundle} stored, or empty when the version is not stored
   * @throws BundleStoreException when the storage fails
   */
  Optional<byte[]> getBundle(String version);

  /**
   * Adds a publication record. Nothing is replaced.
   *
   * @param record the record; its {@code bundleVersion} names the bundle it describes
   * @throws BundleStoreException when the storage fails
   */
  void addRecord(PublicationRecord record);

  /**
   * Lists the records of one version.
   *
   * @param version the {@code bundleVersion}
   * @return the records oldest first: in the order they were added where the store can observe it, otherwise by
   *     {@code createdAt} and then by insertion where known (ADR 0024); empty when there are none
   * @throws BundleStoreException when the storage fails
   */
  List<PublicationRecord> records(String version);

  /**
   * Lists the records of all versions.
   *
   * @return every record; the order is not significant
   * @throws BundleStoreException when the storage fails
   */
  List<PublicationRecord> allRecords();

  /**
   * Reads the pointer to the latest version.
   *
   * @return the version, or empty when no bundle has been published
   * @throws BundleStoreException when the storage fails
   */
  Optional<String> latest();

  /**
   * Moves the pointer to the latest version. The pointer is replaced atomically.
   *
   * @param version the {@code bundleVersion} of a stored bundle
   * @throws BundleStoreException when the storage fails
   */
  void setLatest(String version);
}
