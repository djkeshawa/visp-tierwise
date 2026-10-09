package io.github.tierwise.server.service;

import io.github.tierwise.core.Tierwise;
import io.github.tierwise.core.LoadedBundle;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.server.ApiException;
import io.github.tierwise.server.ErrorCodes;
import io.github.tierwise.server.store.BundleConflictException;
import io.github.tierwise.server.store.BundleStore;
import io.github.tierwise.server.store.BundleVersions;
import io.github.tierwise.server.store.PublicationRecord;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishing, promoting and reading bundles (requirements section 9). It keeps no state beyond an optional,
 * short-lived copy of the latest pointer, so any number of instances can serve one store.
 *
 * <p>Decisions that the sources do not fix, marked (OPEN) in the README:
 *
 * <ul>
 *   <li>The first publication of a version makes it the latest. Publishing a version again adds a record (if
 *       it is a new one) and leaves the latest pointer alone, so a repeated CI run cannot undo a promote.
 *   <li>Two identical records for one version are stored once.
 * </ul>
 */
public final class BundleService {

  private final BundleStore store;
  private final JsonMapper duplicateChecking;
  private final long maxBundleBytes;
  private final long latestTtlNanos;
  private volatile CachedLatest cachedLatest;

  /**
   * Creates the service.
   *
   * @param store the bundle store
   * @param duplicateChecking a mapper that rejects duplicate member names
   * @param maxBundleBytes the largest bundle that {@link #publish} accepts
   * @param latestCacheTtl how long the latest pointer is remembered; zero reads the store every time
   */
  public BundleService(BundleStore store, JsonMapper duplicateChecking, long maxBundleBytes, Duration latestCacheTtl) {
    this.store = Objects.requireNonNull(store, "store");
    this.duplicateChecking = Objects.requireNonNull(duplicateChecking, "duplicateChecking");
    this.maxBundleBytes = maxBundleBytes;
    this.latestTtlNanos = latestCacheTtl.toNanos();
  }

  /**
   * Verifies and stores a bundle and its publication record.
   *
   * <p>The bundle is loaded with {@code tierwise-core}, which checks the digest, the versions and the
   * structure. Nothing is stored when that fails.
   *
   * @param bundleBytes the canonical bytes of the bundle
   * @param recordJson the JSON of the publication record: {@code {gitCommit?, createdAt, publisher?}}
   * @return what the call did
   * @throws ApiException with status 400 for a bundle that the core rejects ({@code E_BUNDLE_INTEGRITY},
   *     {@code E_BUNDLE_UNSUPPORTED}) or a record that breaks BUNDLE-141 to BUNDLE-150
   *     ({@code E_PROVENANCE_INVALID}), 409 when different bytes are stored for the version, 413 for a
   *     bundle over the size limit
   */
  public PublishOutcome publish(byte[] bundleBytes, byte[] recordJson) {
    if (bundleBytes.length > maxBundleBytes) {
      throw new ApiException(413, ErrorCodes.REQUEST_TOO_LARGE,
          "the bundle is larger than " + maxBundleBytes + " bytes");
    }
    String version = load(bundleBytes).bundleVersion();
    PublicationRecord record = parseRecord(recordJson, version);
    boolean firstPublication = store.records(version).isEmpty();
    boolean stored;
    try {
      stored = store.putBundle(version, bundleBytes);
    } catch (BundleConflictException e) {
      throw new ApiException(409, ErrorCodes.BUNDLE_CONFLICT,
          "a different bundle is already stored for " + version + "; stored bundles never change");
    }
    boolean madeLatest = firstPublication || store.latest().isEmpty();
    if (madeLatest) {
      store.setLatest(version);
    }
    boolean recordAdded = !store.records(version).contains(record);
    if (recordAdded) {
      store.addRecord(record);
    }
    forgetLatest();
    String latest = store.latest().orElse(version);
    return new PublishOutcome(version, stored, recordAdded, madeLatest, latest, record);
  }

  /**
   * Returns the latest {@code bundleVersion} without reading the bundle.
   *
   * @return the version, or empty when nothing has been published
   */
  public Optional<String> latestVersion() {
    if (latestTtlNanos <= 0) {
      return store.latest();
    }
    long now = System.nanoTime();
    CachedLatest cached = cachedLatest;
    if (cached != null && now - cached.readAt < latestTtlNanos) {
      return cached.version;
    }
    Optional<String> read = store.latest();
    cachedLatest = new CachedLatest(read, now);
    return read;
  }

  /**
   * Returns the latest {@code bundleVersion}, or fails when nothing has been published.
   *
   * @return the version
   * @throws ApiException with status 404 when nothing has been published
   */
  public String requireLatestVersion() {
    return latestVersion().orElseThrow(BundleService::nothingPublished);
  }

  /**
   * Returns one stored version.
   *
   * @param version the {@code bundleVersion}
   * @return the stored version and its exact bytes
   * @throws ApiException with status 400 when {@code version} is not a {@code bundleVersion}, and 404 when it
   *     is not stored
   */
  public StoredBundle bundle(String version) {
    requireWellFormed(version);
    byte[] bytes = store.getBundle(version).orElseThrow(() -> notStored(version));
    return new StoredBundle(version, bytes);
  }

  /**
   * Returns the history: every publication record, newest first.
   *
   * @return the records ordered by {@code createdAt} descending, then by {@code bundleVersion}
   */
  public List<PublicationRecord> history() {
    List<PublicationRecord> records = new ArrayList<>(store.allRecords());
    records.sort(Comparator.comparing(PublicationRecord::createdAt).reversed()
        .thenComparing(PublicationRecord::bundleVersion));
    return records;
  }

  /**
   * Makes a stored version the latest, to roll back or forward.
   *
   * @param version the {@code bundleVersion}
   * @return the new and the previous latest version
   * @throws ApiException with status 400 when {@code version} is not a {@code bundleVersion}, and 404 when it
   *     is not stored
   */
  public PromoteOutcome promote(String version) {
    requireWellFormed(version);
    if (store.getBundle(version).isEmpty()) {
      throw notStored(version);
    }
    Optional<String> previous = store.latest();
    store.setLatest(version);
    forgetLatest();
    return new PromoteOutcome(version, previous);
  }

  private LoadedBundle load(byte[] bytes) {
    try {
      return Tierwise.loadBundle(bytes);
    } catch (TierwiseException e) {
      throw new ApiException(400, e.code(), withoutCode(e));
    }
  }

  private PublicationRecord parseRecord(byte[] json, String version) {
    try {
      Map<String, Object> raw = duplicateChecking.readValue(json, new TypeReference<Map<String, Object>>() { });
      if (raw == null) {
        throw new IllegalArgumentException("the record is null");
      }
      return PublicationRecord.fromJson(raw, version);
    } catch (JacksonException e) {
      throw new ApiException(400, io.github.tierwise.core.Code.E_PROVENANCE_INVALID,
          "the publication record is not a JSON object without duplicate members");
    } catch (IllegalArgumentException e) {
      throw new ApiException(400, io.github.tierwise.core.Code.E_PROVENANCE_INVALID,
          "invalid publication record: " + e.getMessage());
    }
  }

  private static void requireWellFormed(String version) {
    if (!BundleVersions.isValid(version)) {
      throw new ApiException(400, ErrorCodes.REQUEST_INVALID,
          "a bundle version is sha256: followed by 64 lower-case hexadecimal digits");
    }
  }

  private static ApiException notStored(String version) {
    return new ApiException(404, ErrorCodes.BUNDLE_NOT_FOUND, "no bundle is stored for " + version);
  }

  private static ApiException nothingPublished() {
    return new ApiException(404, ErrorCodes.BUNDLE_NOT_FOUND, "no bundle has been published yet");
  }

  private void forgetLatest() {
    cachedLatest = null;
  }

  /** Returns the message of a core exception without its leading code. */
  static String withoutCode(TierwiseException e) {
    String message = e.getMessage();
    String prefix = e.code() + ": ";
    return message.startsWith(prefix) ? message.substring(prefix.length()) : message;
  }

  private record CachedLatest(Optional<String> version, long readAt) {
  }
}
