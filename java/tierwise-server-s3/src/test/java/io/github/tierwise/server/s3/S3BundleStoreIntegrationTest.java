package io.github.tierwise.server.s3;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.server.store.BundleConflictException;
import io.github.tierwise.server.store.BundleStoreException;
import io.github.tierwise.server.store.PublicationRecord;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * Runs {@link S3BundleStore} against a real S3-compatible endpoint (ADR 0023: the conditional writes must be verified
 * against a real endpoint before release). Opt-in: the class runs only when {@code TIERWISE_S3_IT_ENDPOINT} is set.
 * {@code scripts/s3-it.sh} starts a local MinIO and sets the variables.
 *
 * <p>Environment: {@code TIERWISE_S3_IT_ENDPOINT} (required), {@code TIERWISE_S3_IT_BUCKET} (default
 * {@code tierwise-it}), {@code AWS_ACCESS_KEY_ID}, {@code AWS_SECRET_ACCESS_KEY} (required; read by the SDK's default
 * credential chain, as in production) and {@code AWS_REGION} (default {@code us-east-1}).
 *
 * <p>The client is the one {@link S3BundleStoreAutoConfiguration} builds from {@link S3StoreProperties}, with the
 * endpoint override and path-style addressing. Every run writes under a fresh random prefix, which is deleted at the
 * end.
 */
@EnabledIfEnvironmentVariable(named = "TIERWISE_S3_IT_ENDPOINT", matches = ".+")
class S3BundleStoreIntegrationTest {

  static final String A = "sha256:" + "a".repeat(64);
  static final String B = "sha256:" + "b".repeat(64);
  static final String C = "sha256:" + "c".repeat(64);
  static final String D = "sha256:" + "d".repeat(64);
  static final String T1 = "2026-03-04T05:06:07Z";
  static final String T2 = "2026-03-04T05:06:08Z";
  static final int PRECONDITION_FAILED = 412;

  static S3Client s3;
  static String bucket;
  static String runPrefix;

  String prefix;
  S3BundleStore store;

  @BeforeAll
  static void connect() {
    String endpoint = System.getenv("TIERWISE_S3_IT_ENDPOINT");
    requireEnv("AWS_ACCESS_KEY_ID");
    requireEnv("AWS_SECRET_ACCESS_KEY");
    bucket = envOr("TIERWISE_S3_IT_BUCKET", "tierwise-it");
    S3StoreProperties properties = new S3StoreProperties();
    properties.setBucket(bucket);
    properties.setEndpoint(URI.create(endpoint));
    properties.setRegion(envOr("AWS_REGION", "us-east-1"));
    properties.setPathStyleAccess(true);
    s3 = new S3BundleStoreAutoConfiguration().tierwiseS3Client(properties);
    createBucketIfAbsent(s3, bucket);
    runPrefix = "tierwise-it/" + UUID.randomUUID();
  }

  @AfterAll
  static void cleanUpAndClose() {
    if (s3 == null) {
      return;
    }
    try {
      for (String key : listKeys(runPrefix + "/")) {
        s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
      }
    } finally {
      s3.close();
    }
  }

  @BeforeEach
  void newStore() {
    prefix = runPrefix + "/" + UUID.randomUUID();
    store = new S3BundleStore(s3, bucket, prefix);
  }

  // Bundles: If-None-Match: *

  @Test
  void putBundleStoresANewVersionAcceptsTheSameBytesAndRejectsDifferentBytes() {
    byte[] original = bytes("bundle-a");

    assertTrue(store.putBundle(A, original));
    assertArrayEquals(original, store.getBundle(A).orElseThrow());
    assertFalse(store.putBundle(A, bytes("bundle-a")));
    assertThrows(BundleConflictException.class, () -> store.putBundle(A, bytes("bundle-a-changed")));

    assertArrayEquals(original, store.getBundle(A).orElseThrow(), "stored bytes are never overwritten");
    assertArrayEquals(original, rawGet(bundleKey(A)));
  }

  @Test
  void theEndpointRejectsIfNoneMatchStarOnATakenKeyWith412() {
    store.putBundle(A, bytes("bundle-a"));

    S3Exception rejected = assertThrows(S3Exception.class, () -> s3.putObject(
        PutObjectRequest.builder().bucket(bucket).key(bundleKey(A)).ifNoneMatch("*").build(),
        RequestBody.fromBytes(bytes("other"))));

    assertEquals(PRECONDITION_FAILED, rejected.statusCode());
    assertArrayEquals(bytes("bundle-a"), rawGet(bundleKey(A)));
  }

  @Test
  void getBundleOfAnAbsentVersionIsEmpty() {
    assertEquals(Optional.empty(), store.getBundle(B));
  }

  // The latest pointer: If-None-Match: * when absent, If-Match on the ETag afterwards

  @Test
  void setLatestCreatesThePointerThenMovesItOnItsEtag() {
    store.putBundle(A, bytes("bundle-a"));
    store.putBundle(B, bytes("bundle-b"));
    assertEquals(Optional.empty(), store.latest());

    store.setLatest(A);
    assertEquals(Optional.of(A), store.latest());
    assertArrayEquals(bytes(A + "\n"), rawGet(latestKey()), "ADR 0024: the version and one newline");
    String firstEtag = rawEtag(latestKey());

    store.setLatest(B);
    assertEquals(Optional.of(B), store.latest());
    assertArrayEquals(bytes(B + "\n"), rawGet(latestKey()));
    String secondEtag = rawEtag(latestKey());
    assertNotEquals(firstEtag, secondEtag);

    store.setLatest(B);
    assertEquals(secondEtag, rawEtag(latestKey()), "setting the current version writes nothing");
  }

  @Test
  void setLatestOfAVersionWithNoStoredBundleFailsAndLeavesThePointer() {
    store.putBundle(A, bytes("bundle-a"));
    store.setLatest(A);

    assertThrows(BundleStoreException.class, () -> store.setLatest(C));
    assertEquals(Optional.of(A), store.latest());
  }

  @Test
  void theEndpointRejectsIfMatchOnAStaleEtagWith412() {
    store.putBundle(A, bytes("bundle-a"));
    store.putBundle(B, bytes("bundle-b"));
    store.setLatest(A);
    String stale = rawEtag(latestKey());
    store.setLatest(B);

    S3Exception rejected = assertThrows(S3Exception.class, () -> s3.putObject(
        PutObjectRequest.builder().bucket(bucket).key(latestKey()).ifMatch(stale).build(),
        RequestBody.fromBytes(bytes(C + "\n"))));

    assertEquals(PRECONDITION_FAILED, rejected.statusCode());
    assertEquals(Optional.of(B), store.latest());
  }

  @Test
  void aPointerMovedBetweenTheReadAndTheIfMatchWriteIsReReadAndRetried() {
    store.putBundle(A, bytes("bundle-a"));
    store.putBundle(B, bytes("bundle-b"));
    store.setLatest(A);
    Interfering client = new Interfering(s3, latestKey(), () -> rawPut(latestKey(), bytes(C + "\n")));
    S3BundleStore racing = new S3BundleStore(client, bucket, prefix);

    racing.setLatest(B);

    assertTrue(client.interfered.get(), "the pointer was rewritten behind the store's back");
    assertEquals(List.of(PRECONDITION_FAILED), client.rejections, "the stale If-Match write was rejected once");
    assertEquals(2, client.pointerWrites.size());
    assertTrue(client.pointerWrites.get(0).ifMatch() != null && client.pointerWrites.get(1).ifMatch() != null);
    assertNotEquals(client.pointerWrites.get(0).ifMatch(), client.pointerWrites.get(1).ifMatch(),
        "the retry used the ETag of the pointer it re-read");
    assertEquals(Optional.of(B), store.latest());
    assertArrayEquals(bytes(B + "\n"), rawGet(latestKey()));
  }

  @Test
  void aPointerCreatedBetweenTheReadAndTheIfNoneMatchWriteIsReReadAndRetried() {
    store.putBundle(A, bytes("bundle-a"));
    Interfering client = new Interfering(s3, latestKey(), () -> rawPut(latestKey(), bytes(C + "\n")));
    S3BundleStore racing = new S3BundleStore(client, bucket, prefix);

    racing.setLatest(A);

    assertTrue(client.interfered.get());
    assertEquals(List.of(PRECONDITION_FAILED), client.rejections, "the If-None-Match: * write was rejected once");
    assertEquals("*", client.pointerWrites.get(0).ifNoneMatch());
    assertTrue(client.pointerWrites.get(1).ifMatch() != null, "the retry is conditional on the new pointer's ETag");
    assertEquals(Optional.of(A), store.latest());
  }

  @Test
  void aPointerDeletedBetweenTheReadAndTheIfMatchWriteIsReReadAndRecreated() {
    store.putBundle(A, bytes("bundle-a"));
    store.putBundle(B, bytes("bundle-b"));
    store.setLatest(A);
    Interfering client = new Interfering(s3, latestKey(),
        () -> s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(latestKey()).build()));
    S3BundleStore racing = new S3BundleStore(client, bucket, prefix);

    racing.setLatest(B);

    // S3 answers If-Match on a missing key with 404 NoSuchKey, not 412; the store treats both as a lost race.
    assertEquals(List.of(404), client.rejections, "the If-Match write on the deleted pointer was rejected once");
    assertTrue(client.pointerWrites.get(0).ifMatch() != null);
    assertEquals("*", client.pointerWrites.get(1).ifNoneMatch(), "the retry recreates the absent pointer");
    assertEquals(Optional.of(B), store.latest());
  }

  @Test
  void twoStoresRacingSetLatestFromThreadsBothCompleteAndThePointerNamesOneOfThem() throws Exception {
    store.putBundle(A, bytes("bundle-a"));
    store.putBundle(B, bytes("bundle-b"));
    S3BundleStore first = new S3BundleStore(s3, bucket, prefix);
    S3BundleStore second = new S3BundleStore(s3, bucket, prefix);
    int rounds = 10;
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      for (int round = 0; round < rounds; round++) {
        String firstTarget = round % 2 == 0 ? A : B;
        String secondTarget = round % 2 == 0 ? B : A;
        CyclicBarrier start = new CyclicBarrier(2);
        Future<?> one = pool.submit(() -> {
          start.await();
          first.setLatest(firstTarget);
          return null;
        });
        Future<?> two = pool.submit(() -> {
          start.await();
          second.setLatest(secondTarget);
          return null;
        });
        one.get(60, TimeUnit.SECONDS);
        two.get(60, TimeUnit.SECONDS);
        String latest = store.latest().orElseThrow();
        assertTrue(Set.of(A, B).contains(latest), latest);
        assertArrayEquals(bytes(latest + "\n"), rawGet(latestKey()));
      }
    } finally {
      pool.shutdownNow();
    }
  }

  // Publication records

  @Test
  void recordsRoundTripInSlotOrderAndAllRecordsListsEveryVersion() {
    store.putBundle(A, bytes("bundle-a"));
    store.putBundle(B, bytes("bundle-b"));
    PublicationRecord first = new PublicationRecord(A, T1, Optional.of("0123456789abcdef0123456789abcdef01234567"),
        Optional.of("first"));
    PublicationRecord second = new PublicationRecord(A, T1, Optional.empty(), Optional.of("second"));
    PublicationRecord third = new PublicationRecord(A, T2, Optional.empty(), Optional.empty());
    PublicationRecord other = new PublicationRecord(B, T1, Optional.empty(), Optional.of("other"));

    store.addRecord(third);
    store.addRecord(first);
    store.addRecord(second);
    store.addRecord(other);

    assertEquals(List.of(first, second, third), store.records(A), "createdAt, then slot");
    assertEquals(List.of(other), store.records(B));
    assertEquals(List.of(), store.records(C));
    Set<PublicationRecord> all = new HashSet<>(store.allRecords());
    assertEquals(Set.of(first, second, third, other), all);
    assertEquals(4, store.allRecords().size());
    List<String> keys = listKeys(prefix + "/publications/" + A + "/");
    Collections.sort(keys);
    assertEquals(List.of(
        prefix + "/publications/" + A + "/" + T1 + "-1.json",
        prefix + "/publications/" + A + "/" + T1 + "-2.json",
        prefix + "/publications/" + A + "/" + T2 + "-1.json"), keys);
  }

  @Test
  void allRecordsFollowsContinuationTokensOverMoreThanOnePage() {
    store.putBundle(D, bytes("bundle-d"));
    int count = 1005;
    for (int i = 0; i < count; i++) {
      // S3 returns at most 1000 keys per page; the store must follow the token.
      store.addRecord(new PublicationRecord(D, String.format("2026-01-01T00:%02d:%02dZ", i / 60 % 60, i % 60),
          Optional.empty(), Optional.of("p" + i)));
    }

    assertEquals(count, store.records(D).size());
    assertEquals(count, store.allRecords().size());
  }

  // Helpers

  static byte[] bytes(String text) {
    return text.getBytes(UTF_8);
  }

  String bundleKey(String version) {
    return prefix + "/bundles/" + version + ".json";
  }

  String latestKey() {
    return prefix + "/latest";
  }

  static byte[] rawGet(String key) {
    try (ResponseInputStream<GetObjectResponse> body =
             s3.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
      return body.readAllBytes();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  static void rawPut(String key, byte[] content) {
    s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromBytes(content));
  }

  static String rawEtag(String key) {
    HeadObjectResponse head = s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
    return head.eTag();
  }

  static List<String> listKeys(String keyPrefix) {
    List<String> keys = new ArrayList<>();
    String token = null;
    do {
      ListObjectsV2Request.Builder request = ListObjectsV2Request.builder().bucket(bucket).prefix(keyPrefix);
      if (token != null) {
        request.continuationToken(token);
      }
      ListObjectsV2Response page = s3.listObjectsV2(request.build());
      for (S3Object object : page.contents()) {
        keys.add(object.key());
      }
      token = Boolean.TRUE.equals(page.isTruncated()) ? page.nextContinuationToken() : null;
    } while (token != null);
    return keys;
  }

  static void createBucketIfAbsent(S3Client client, String name) {
    try {
      client.headBucket(HeadBucketRequest.builder().bucket(name).build());
    } catch (S3Exception e) {
      if (e.statusCode() != 404) {
        throw e;
      }
      try {
        client.createBucket(CreateBucketRequest.builder().bucket(name).build());
      } catch (S3Exception raced) {
        // Another run created it in between: BucketAlreadyOwnedByYou (409) is fine.
        if (raced.statusCode() != 409) {
          throw raced;
        }
      }
    }
  }

  static String envOr(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  static void requireEnv(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be set when TIERWISE_S3_IT_ENDPOINT is set");
    }
  }

  /**
   * Passes every call to the real client. Before the first write of one key, it runs an interference (a direct
   * write or delete through the real client), so the store's conditional write runs against a pointer that changed
   * after the store read it. Every write to the key and every status that rejects one are recorded.
   */
  static final class Interfering implements S3Client {

    final S3Client delegate;
    final String key;
    final Runnable interference;
    final AtomicBoolean interfered = new AtomicBoolean();
    final List<PutObjectRequest> pointerWrites = new CopyOnWriteArrayList<>();
    final List<Integer> rejections = new CopyOnWriteArrayList<>();

    Interfering(S3Client delegate, String key, Runnable interference) {
      this.delegate = delegate;
      this.key = key;
      this.interference = interference;
    }

    @Override
    public PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
      if (!request.key().equals(key)) {
        return delegate.putObject(request, body);
      }
      pointerWrites.add(request);
      if (interfered.compareAndSet(false, true)) {
        interference.run();
      }
      try {
        return delegate.putObject(request, body);
      } catch (S3Exception e) {
        rejections.add(e.statusCode());
        throw e;
      }
    }

    @Override
    public ResponseInputStream<GetObjectResponse> getObject(GetObjectRequest request) {
      return delegate.getObject(request);
    }

    @Override
    public HeadObjectResponse headObject(HeadObjectRequest request) {
      return delegate.headObject(request);
    }

    @Override
    public ListObjectsV2Response listObjectsV2(ListObjectsV2Request request) {
      return delegate.listObjectsV2(request);
    }

    @Override
    public String serviceName() {
      return delegate.serviceName();
    }

    @Override
    public void close() {
      // The shared client is closed once, after all tests.
    }
  }
}
