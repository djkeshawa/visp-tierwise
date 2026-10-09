package io.github.tierwise.server.s3;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

/**
 * An in-memory {@link S3Client} for unit tests. It models what the store relies on: conditional PutObject
 * ({@code If-None-Match: *} and {@code If-Match} with HTTP 412), ETags, paged listings with delimiters, and
 * injected failures. It makes no network calls.
 *
 * <p>Every operation is synchronized, so concurrent tests see a linearizable store, as S3 provides.
 */
class FakeS3Client implements S3Client {

  private final String bucket;
  private final int pageSize;
  private final TreeMap<String, Stored> objects = new TreeMap<>();
  private final List<PutObjectRequest> putRequests = new ArrayList<>();
  private final Map<String, Deque<Integer>> failures = new HashMap<>();
  private final Deque<Runnable> beforePut = new ArrayDeque<>();
  private boolean omitEtags;
  private long etagCounter;

  /** An object in the fake bucket. */
  record Stored(byte[] bytes, String etag) {
  }

  /**
   * Creates a fake bucket with one listing page for every result.
   *
   * @param bucket the bucket name the store must use
   */
  FakeS3Client(String bucket) {
    this(bucket, Integer.MAX_VALUE);
  }

  /**
   * Creates a fake bucket that lists at most {@code pageSize} entries per page.
   *
   * @param bucket the bucket name the store must use
   * @param pageSize the listing page size
   */
  FakeS3Client(String bucket, int pageSize) {
    this.bucket = bucket;
    this.pageSize = pageSize;
  }

  /** Stores an object directly, bypassing the store under test. Used to seed data and to simulate foreign objects. */
  synchronized void seed(String key, byte[] bytes) {
    objects.put(key, new Stored(bytes.clone(), nextEtag()));
  }

  /** Returns the bytes of an object, or empty when it does not exist. */
  synchronized Optional<byte[]> bytesOf(String key) {
    Stored stored = objects.get(key);
    return stored == null ? Optional.empty() : Optional.of(stored.bytes().clone());
  }

  /** Returns the stored object, or null when it does not exist. */
  synchronized Stored stored(String key) {
    return objects.get(key);
  }

  /** Returns every key in the bucket, in sorted order. */
  synchronized List<String> keys() {
    return new ArrayList<>(objects.keySet());
  }

  /** Returns every PutObject request that reached the fake, including rejected ones, in arrival order. */
  synchronized List<PutObjectRequest> putRequests() {
    return List.copyOf(putRequests);
  }

  /**
   * Makes the next {@code times} calls of an operation fail with the given HTTP status.
   *
   * @param operation the SDK method name, such as {@code putObject}
   * @param status the HTTP status to report
   * @param times how many calls fail
   */
  synchronized void failNext(String operation, int status, int times) {
    Deque<Integer> queue = failures.computeIfAbsent(operation, key -> new ArrayDeque<>());
    for (int i = 0; i < times; i++) {
      queue.add(status);
    }
  }

  /** Runs an action once, just before the next PutObject is evaluated. Used to simulate a racing writer. */
  synchronized void beforeNextPut(Runnable action) {
    beforePut.add(action);
  }

  /** Deletes an object directly, bypassing the store under test. */
  synchronized void remove(String key) {
    objects.remove(key);
  }

  /** Makes every GetObject return no ETag, as a backend that omits it would. */
  synchronized void omitEtags() {
    omitEtags = true;
  }

  @Override
  public String serviceName() {
    return "s3";
  }

  @Override
  public synchronized PutObjectResponse putObject(PutObjectRequest request, RequestBody body) {
    Runnable hook = beforePut.poll();
    if (hook != null) {
      hook.run();
    }
    checkBucket(request.bucket());
    putRequests.add(request);
    maybeFail("putObject", request.key());
    Stored current = objects.get(request.key());
    if ("*".equals(request.ifNoneMatch()) && current != null) {
      throw httpError(412, "precondition failed: object exists");
    }
    if (request.ifMatch() != null && current == null) {
      throw httpError(404, "no such key: " + request.key());
    }
    if (request.ifMatch() != null && !request.ifMatch().equals(current.etag())) {
      throw httpError(412, "precondition failed: If-Match does not hold");
    }
    String etag = nextEtag();
    objects.put(request.key(), new Stored(readAll(body), etag));
    return PutObjectResponse.builder().eTag(etag).build();
  }

  @Override
  public synchronized ResponseInputStream<GetObjectResponse> getObject(GetObjectRequest request) {
    checkBucket(request.bucket());
    maybeFail("getObject", request.key());
    Stored stored = objects.get(request.key());
    if (stored == null) {
      throw httpError(404, "no such key: " + request.key());
    }
    GetObjectResponse response = GetObjectResponse.builder().eTag(omitEtags ? null : stored.etag()).build();
    return new ResponseInputStream<>(response,
        AbortableInputStream.create(new ByteArrayInputStream(stored.bytes().clone())));
  }

  @Override
  public synchronized HeadObjectResponse headObject(HeadObjectRequest request) {
    checkBucket(request.bucket());
    maybeFail("headObject", request.key());
    Stored stored = objects.get(request.key());
    if (stored == null) {
      throw httpError(404, "no such key: " + request.key());
    }
    return HeadObjectResponse.builder().eTag(stored.etag()).build();
  }

  @Override
  public synchronized ListObjectsV2Response listObjectsV2(ListObjectsV2Request request) {
    checkBucket(request.bucket());
    maybeFail("listObjectsV2", request.prefix());
    String prefix = request.prefix() == null ? "" : request.prefix();
    String delimiter = request.delimiter();
    TreeMap<String, Boolean> entries = new TreeMap<>();
    for (String key : objects.keySet()) {
      if (!key.startsWith(prefix)) {
        continue;
      }
      int cut = delimiter == null ? -1 : key.indexOf(delimiter, prefix.length());
      if (cut >= 0) {
        entries.put(key.substring(0, cut + delimiter.length()), true);
      } else {
        entries.put(key, false);
      }
    }
    List<String> names = new ArrayList<>(entries.keySet());
    int start = request.continuationToken() == null ? 0 : Integer.parseInt(request.continuationToken());
    int end = Math.min(names.size(), start + pageSize);
    List<S3Object> contents = new ArrayList<>();
    List<CommonPrefix> commonPrefixes = new ArrayList<>();
    for (String name : names.subList(start, end)) {
      if (entries.get(name)) {
        commonPrefixes.add(CommonPrefix.builder().prefix(name).build());
      } else {
        contents.add(S3Object.builder().key(name).eTag(objects.get(name).etag()).build());
      }
    }
    boolean truncated = end < names.size();
    ListObjectsV2Response.Builder response = ListObjectsV2Response.builder()
        .contents(contents)
        .commonPrefixes(commonPrefixes)
        .isTruncated(truncated);
    if (truncated) {
      response.nextContinuationToken(String.valueOf(end));
    }
    return response.build();
  }

  @Override
  public void close() {
    // Nothing to release.
  }

  private void checkBucket(String requested) {
    if (!bucket.equals(requested)) {
      throw new IllegalStateException("the store used bucket " + requested + " but the fake holds " + bucket);
    }
  }

  private void maybeFail(String operation, String key) {
    Deque<Integer> queue = failures.get(operation);
    if (queue != null && !queue.isEmpty()) {
      throw httpError(queue.poll(), "injected failure for " + operation + " on " + key);
    }
  }

  private String nextEtag() {
    etagCounter++;
    return "\"" + etagCounter + "\"";
  }

  private static byte[] readAll(RequestBody body) {
    try {
      return body.contentStreamProvider().newStream().readAllBytes();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static S3Exception httpError(int status, String message) {
    return (S3Exception) S3Exception.builder().statusCode(status).message(message).build();
  }
}
