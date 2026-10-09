package io.github.tierwise.server.s3;

import io.github.tierwise.server.store.BundleConflictException;
import io.github.tierwise.server.store.BundleStore;
import io.github.tierwise.server.store.BundleStoreException;
import io.github.tierwise.server.store.BundleVersions;
import io.github.tierwise.server.store.PublicationRecord;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * A {@link BundleStore} on Amazon S3 or an S3-compatible service, using the AWS SDK v2 client.
 *
 * <p>Concurrency comes from S3 conditional writes, not from locks:
 *
 * <ul>
 *   <li>A bundle is written with {@code If-None-Match: *}. If the key is taken, the stored bytes are compared with
 *       the new bytes: identical bytes give {@code false} from {@link #putBundle}, and different bytes give
 *       {@link BundleConflictException}. Stored bytes are never overwritten.
 *   <li>A publication record takes the first free slot for its {@code createdAt} (for example {@code -1},
 *       then {@code -2}), so no record is replaced.
 *   <li>{@link #setLatest} rewrites the latest pointer with {@code If-Match} set to the ETag it read, or with
 *       {@code If-None-Match: *} when no pointer exists yet. A lost race re-reads the pointer and tries again.
 * </ul>
 *
 * <p>The pointer is last-writer-wins: when two calls race, the one that is written last names the latest version.
 * The retry loop keeps a stale ETag from overwriting a newer pointer silently, but it does not order the calls.
 * This matches {@link BundleStore#setLatest}, which replaces the pointer atomically and makes no promise about
 * which of two concurrent calls wins.
 *
 * <p>The backend must support conditional PutObject (HTTP 412 on a failed precondition). A backend that rejects
 * the header fails with a {@link BundleStoreException}; the store never falls back to an unconditional write.
 *
 * <p>Versions and {@code createdAt} values are checked before any request is sent. A malformed value is an
 * {@link IllegalArgumentException}, as the SPI requires. Keys are described in {@link S3KeyLayout}. Record bodies
 * are the JSON form of {@link PublicationRecord#toJson()}, read back with {@link PublicationRecord#fromJson}.
 *
 * <p>The store does not own the client. The caller creates it and closes it.
 */
public final class S3BundleStore implements BundleStore {

  /** Attempts at one conditional write (bundle or pointer) before the store reports that writes keep conflicting. */
  static final int MAX_ATTEMPTS = 5;

  /** Slots tried for one {@code createdAt} before the store gives up. Each collision costs one request. */
  static final int MAX_SLOTS = 1000;

  private static final String JSON = "application/json";
  private static final String TEXT = "text/plain; charset=utf-8";
  private static final int NOT_FOUND = 404;
  private static final int CONFLICT = 409;
  private static final int PRECONDITION_FAILED = 412;

  private final S3Client client;
  private final String bucket;
  private final S3KeyLayout layout;

  /** Strict like the server's filesystem store: a duplicate member in a record is damage, not a last-one-wins. */
  private final JsonMapper json =
      JsonMapper.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();

  /**
   * Creates a store over a bucket and prefix.
   *
   * @param client the S3 client, which the caller owns and closes
   * @param bucket the bucket that holds the store
   * @param prefix the key prefix inside the bucket; {@code null} or blank means none
   * @throws IllegalArgumentException when the bucket is blank or the prefix is malformed
   */
  public S3BundleStore(S3Client client, String bucket, String prefix) {
    this.client = Objects.requireNonNull(client, "client");
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalArgumentException("bucket must not be blank");
    }
    this.bucket = bucket;
    this.layout = new S3KeyLayout(prefix);
  }

  /**
   * Stores the bytes of a bundle under its version when no bytes are stored for it.
   *
   * <p>The first call creates the object with {@code If-None-Match: *}. When the key is taken, the stored bytes are
   * compared with the new ones: identical bytes return {@code false} and nothing is written.
   *
   * @param version the {@code bundleVersion}
   * @param bytes the exact bytes of the bundle; the store keeps its own copy
   * @return {@code true} when this call stored the bytes, {@code false} when the same bytes were already stored
   * @throws IllegalArgumentException when the version is not a {@code bundleVersion}; no request is sent
   * @throws BundleConflictException when different bytes are stored under the version; the stored bytes are kept
   * @throws BundleStoreException when the store fails
   */
  @Override
  public boolean putBundle(String version, byte[] bytes) {
    String key = layout.bundleKey(version);
    byte[] content = Objects.requireNonNull(bytes, "bytes").clone();
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      if (createIfAbsent(key, content, JSON)) {
        return true;
      }
      Optional<Stored> existing = read(key);
      if (existing.isPresent()) {
        if (Arrays.equals(existing.get().bytes(), content)) {
          return false;
        }
        throw new BundleConflictException(version);
      }
      // The write was rejected but no object is visible yet, so a racing writer is still in progress. Try again.
    }
    throw new BundleStoreException("bundle " + version + " could not be written: writes kept conflicting after "
        + MAX_ATTEMPTS + " attempts");
  }

  @Override
  public Optional<byte[]> getBundle(String version) {
    return read(layout.bundleKey(version)).map(Stored::bytes);
  }

  /**
   * Adds a publication record in the first free slot for its {@code createdAt}. No existing record is replaced.
   *
   * <p>The store does not check that the bundle is stored. The server stores the bundle first.
   *
   * @param record the record; its JSON form becomes the object body
   * @throws BundleStoreException when the store fails or no slot is free within {@link #MAX_SLOTS}
   */
  @Override
  public void addRecord(PublicationRecord record) {
    Objects.requireNonNull(record, "record");
    byte[] body = json.writeValueAsBytes(record.toJson());
    for (int slot = 1; slot <= MAX_SLOTS; slot++) {
      String key = layout.publicationKey(record.bundleVersion(), record.createdAt(), slot);
      if (createIfAbsent(key, body, JSON)) {
        return;
      }
    }
    throw new BundleStoreException("no free publication slot for " + record.bundleVersion() + " at "
        + record.createdAt() + " within " + MAX_SLOTS + " slots");
  }

  /**
   * Lists the records of one version.
   *
   * <p>S3 cannot order by insertion. The records are ordered by {@code createdAt} and then by slot number. Slots are
   * handed out in the order the calls are made, so this equals the order in which records were added whenever
   * {@code createdAt} never decreases between calls. A record added later with an earlier {@code createdAt} is listed
   * before the earlier-added record. The server only checks emptiness and {@link List#contains} on this list, and
   * sorts its own history.
   *
   * @param version the {@code bundleVersion}
   * @return the records, possibly empty
   * @throws IllegalArgumentException when the version is not a {@code bundleVersion}
   * @throws BundleStoreException when the store fails or holds an object that is not a publication record of the
   *     version, or a record that cannot be read back
   */
  @Override
  public List<PublicationRecord> records(String version) {
    List<S3KeyLayout.PublicationSlot> slots = new ArrayList<>();
    for (String key : list(layout.publicationsPrefix(version))) {
      if (isFolderMarker(key)) {
        continue;
      }
      slots.add(layout.parsePublicationKey(version, key).orElseThrow(() -> unexpected(key)));
    }
    slots.sort(S3KeyLayout.PublicationSlot.ORDER);
    List<PublicationRecord> records = new ArrayList<>(slots.size());
    for (S3KeyLayout.PublicationSlot slot : slots) {
      records.add(readRecord(version, layout.publicationKey(version, slot.createdAt(), slot.number())));
    }
    return List.copyOf(records);
  }

  /**
   * Lists the records of all versions with one flat, paged listing of the publications prefix. Each key is parsed
   * against the version named in its own key, so no separate listing per version is needed.
   *
   * @return every record; the order is not significant
   * @throws BundleStoreException when the store fails or holds an object that is not a publication record
   */
  @Override
  public List<PublicationRecord> allRecords() {
    List<PublicationRecord> all = new ArrayList<>();
    for (String key : list(layout.publicationsRoot())) {
      if (isFolderMarker(key)) {
        continue;
      }
      String version = layout.publicationVersionOf(key)
          .filter(candidate -> layout.parsePublicationKey(candidate, key).isPresent())
          .orElseThrow(() -> unexpected(key));
      all.add(readRecord(version, key));
    }
    return List.copyOf(all);
  }

  /**
   * Reads the latest pointer.
   *
   * @return the version, or empty when nothing has been published
   * @throws BundleStoreException when the store fails or the pointer does not hold a bundle version
   */
  @Override
  public Optional<String> latest() {
    return readPointer().map(Pointer::version);
  }

  /**
   * Makes a stored version the latest. Setting the version that is already latest writes nothing.
   *
   * <p>Before the pointer moves, a HEAD request checks that the bundle is stored, so the pointer never names a
   * missing object. The pointer is then written with {@code If-Match} on the ETag that was read, or with
   * {@code If-None-Match: *} when no pointer exists. A lost race re-reads the pointer and tries again.
   *
   * @param version the {@code bundleVersion} of a stored bundle
   * @throws IllegalArgumentException when the version is not a {@code bundleVersion}; no request is sent
   * @throws BundleStoreException when the bundle is not stored, the store fails, or the attempts run out
   */
  @Override
  public void setLatest(String version) {
    BundleVersions.require(version);
    if (!exists(layout.bundleKey(version))) {
      throw new BundleStoreException("cannot make " + version + " the latest version: no bundle is stored for it");
    }
    for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
      Optional<Pointer> current = readPointer();
      if (current.isPresent() && current.get().version().equals(version)) {
        return;
      }
      if (current.isPresent() && (current.get().etag() == null || current.get().etag().isEmpty())) {
        throw new BundleStoreException(
            "the store returned no ETag for the latest pointer; a conditional update is not possible");
      }
      if (writePointer(version, current)) {
        return;
      }
    }
    throw new BundleStoreException("the latest pointer kept changing; making " + version + " the latest did not complete"
        + " after " + MAX_ATTEMPTS + " attempts");
  }

  /**
   * Writes only when the key is free.
   *
   * @return false when S3 rejected the write because the key is taken or a write to it is in progress
   */
  private boolean createIfAbsent(String key, byte[] content, String contentType) {
    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(bucket)
        .key(key)
        .contentType(contentType)
        .ifNoneMatch("*")
        .build();
    return put(key, request, content);
  }

  /** Writes a pointer that is still at the ETag that was read, or that is still absent. */
  private boolean writePointer(String version, Optional<Pointer> current) {
    String key = layout.latestKey();
    PutObjectRequest.Builder request = PutObjectRequest.builder().bucket(bucket).key(key).contentType(TEXT);
    if (current.isPresent()) {
      request.ifMatch(current.get().etag());
    } else {
      request.ifNoneMatch("*");
    }
    return put(key, request.build(), version.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Sends a conditional put.
   *
   * <p>A rejected write is a lost race, so the caller re-reads and tries again. S3 reports a failed {@code If-None-Match}
   * with 412 and a conflicting concurrent write with 409. A failed {@code If-Match} is 412, or 404 when the object was
   * deleted after it was read. The backend's exact answer for a deleted object is not verified here (see the README),
   * so both are treated the same way.
   *
   * @return true when stored; false when the write was rejected and the caller should re-read and retry
   */
  private boolean put(String key, PutObjectRequest request, byte[] content) {
    try {
      client.putObject(request, RequestBody.fromBytes(content));
      return true;
    } catch (SdkException e) {
      boolean deletedUnderIfMatch = request.ifMatch() != null && isStatus(e, NOT_FOUND);
      if (isStatus(e, CONFLICT) || isStatus(e, PRECONDITION_FAILED) || deletedUnderIfMatch) {
        return false;
      }
      throw failure("write", key, e);
    }
  }

  private Optional<Stored> read(String key) {
    GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
    try (ResponseInputStream<GetObjectResponse> body = client.getObject(request)) {
      return Optional.of(new Stored(body.readAllBytes(), body.response().eTag()));
    } catch (SdkException e) {
      if (isStatus(e, NOT_FOUND)) {
        return Optional.empty();
      }
      throw failure("read", key, e);
    } catch (IOException e) {
      throw new BundleStoreException("S3 read of " + key + " failed while reading the body", e);
    }
  }

  /**
   * Reads the pointer. Surrounding whitespace is ignored, as on the filesystem store, so a pointer written with a
   * trailing newline is read the same way as one without.
   */
  private Optional<Pointer> readPointer() {
    String key = layout.latestKey();
    return read(key).map(stored -> {
      String text = new String(stored.bytes(), StandardCharsets.UTF_8).strip();
      if (!BundleVersions.isValid(text)) {
        throw new BundleStoreException("the latest pointer " + key + " does not hold a bundle version");
      }
      return new Pointer(text, stored.etag());
    });
  }

  /** Reads one record object and parses it against the version of its key. */
  private PublicationRecord readRecord(String version, String key) {
    Stored stored = read(key).orElseThrow(() ->
        new BundleStoreException("publication record " + key + " disappeared while it was being listed"));
    try {
      Map<String, Object> raw =
          json.readValue(stored.bytes(), new TypeReference<Map<String, Object>>() { });
      if (raw == null) {
        throw new IllegalArgumentException("the record is null");
      }
      return PublicationRecord.fromJson(raw, version);
    } catch (JacksonException | IllegalArgumentException e) {
      throw new BundleStoreException("the publication record " + key + " is damaged: " + e.getMessage(), e);
    }
  }

  private boolean exists(String key) {
    try {
      client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
      return true;
    } catch (SdkException e) {
      if (isStatus(e, NOT_FOUND)) {
        return false;
      }
      throw failure("head", key, e);
    }
  }

  /** Lists every key under a prefix in one flat listing, following continuation tokens. */
  private List<String> list(String prefix) {
    List<String> keys = new ArrayList<>();
    String token = null;
    do {
      ListObjectsV2Request.Builder request = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix);
      if (token != null) {
        request.continuationToken(token);
      }
      ListObjectsV2Response page = listPage(prefix, request.build());
      page.contents().forEach(object -> keys.add(object.key()));
      token = nextToken(prefix, page);
    } while (token != null);
    return keys;
  }

  private ListObjectsV2Response listPage(String prefix, ListObjectsV2Request request) {
    try {
      return client.listObjectsV2(request);
    } catch (SdkException e) {
      throw failure("list", prefix, e);
    }
  }

  /** Returns the next token, or null after the last page. A truncated page without a token is an error. */
  private static String nextToken(String prefix, ListObjectsV2Response page) {
    if (!Boolean.TRUE.equals(page.isTruncated())) {
      return null;
    }
    String token = page.nextContinuationToken();
    if (token == null || token.isEmpty()) {
      throw new BundleStoreException("S3 listing of " + prefix + " was truncated without a continuation token");
    }
    return token;
  }

  /** A key ending in a slash is a folder marker that some consoles create. It holds no record and is skipped. */
  private static boolean isFolderMarker(String key) {
    return key.endsWith("/");
  }

  private static BundleStoreException unexpected(String key) {
    return new BundleStoreException("unexpected object among publication records: " + key);
  }

  private static boolean isStatus(SdkException e, int status) {
    return e instanceof SdkServiceException service && service.statusCode() == status;
  }

  private static BundleStoreException failure(String action, String key, SdkException cause) {
    String status = cause instanceof SdkServiceException service ? " with HTTP " + service.statusCode() : "";
    return new BundleStoreException("S3 " + action + " of " + key + " failed" + status, cause);
  }

  /** The bytes of an object and the ETag that the store returned with them. */
  private record Stored(byte[] bytes, String etag) {
  }

  /** The latest pointer: the version it names and the ETag of the pointer object. */
  private record Pointer(String version, String etag) {
  }
}
