package io.github.visplabs.tierwise.spring.s3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Tierwise;
import io.github.visplabs.tierwise.spring.BundleFetch;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Runs {@link S3BundleSource} against a real S3-compatible endpoint (ADR 0023: the 304 path must be verified against
 * a real endpoint before release). Opt-in: the class runs only when {@code TIERWISE_S3_IT_ENDPOINT} is set.
 * {@code scripts/s3-it.sh} starts a local MinIO and sets the variables.
 *
 * <p>Environment: {@code TIERWISE_S3_IT_ENDPOINT} (required), {@code TIERWISE_S3_IT_BUCKET} (default
 * {@code tierwise-it}), {@code AWS_ACCESS_KEY_ID}, {@code AWS_SECRET_ACCESS_KEY} (required; read by the SDK's default
 * credential chain, as in production) and {@code AWS_REGION} (default {@code us-east-1}).
 *
 * <p>The client is the one {@link S3BundleSourceAutoConfiguration} builds from {@link S3BundleSourceProperties}, with
 * the endpoint override and path-style addressing. Each test uses its own random key, which is deleted afterwards.
 */
@EnabledIfEnvironmentVariable(named = "TIERWISE_S3_IT_ENDPOINT", matches = ".+")
class S3BundleSourceIntegrationTest {

  private static final int NOT_MODIFIED = 304;

  private static S3Client s3;
  private static String bucket;
  private static String runPrefix;

  private byte[] commerce;
  private byte[] retailPos;
  private String commerceVersion;
  private String retailPosVersion;
  private String key;
  private S3BundleSource source;

  @BeforeAll
  static void connect() {
    requireEnv("AWS_ACCESS_KEY_ID");
    requireEnv("AWS_SECRET_ACCESS_KEY");
    bucket = envOr("TIERWISE_S3_IT_BUCKET", "tierwise-it");
    S3BundleSourceProperties properties = new S3BundleSourceProperties(envOr("AWS_REGION", "us-east-1"),
        URI.create(System.getenv("TIERWISE_S3_IT_ENDPOINT")), true, Duration.ofSeconds(30));
    s3 = new S3BundleSourceAutoConfiguration().tierwiseBundleS3Client(properties);
    createBucketIfAbsent(s3, bucket);
    runPrefix = "tierwise-it/" + UUID.randomUUID();
  }

  @AfterAll
  static void close() {
    if (s3 != null) {
      s3.close();
    }
  }

  @BeforeEach
  void setUp() throws IOException {
    Path bundles = Path.of(System.getProperty("tierwise.spec.dir")).resolve("conformance").resolve("bundles");
    commerce = Files.readAllBytes(bundles.resolve("commerce.json"));
    retailPos = Files.readAllBytes(bundles.resolve("retail-pos.json"));
    commerceVersion = Tierwise.loadBundle(commerce).bundleVersion();
    retailPosVersion = Tierwise.loadBundle(retailPos).bundleVersion();
    key = runPrefix + "/" + UUID.randomUUID() + "/bundle.json";
    source = new S3BundleSource(s3, new S3Location(bucket, key));
  }

  @AfterEach
  void deleteObject() {
    s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
  }

  @Test
  void fetchesThenGetsNotModifiedThenSeesTheOverwrittenObject() throws IOException {
    put(commerce);
    String firstEtag = rawEtag();

    BundleFetch first = source.fetch(null);
    assertFalse(first.isNotModified());
    assertArrayEquals(commerce, first.bytes());
    assertEquals(unquote(firstEtag), first.etag());

    BundleFetch unchanged = source.fetch(commerceVersion);
    assertTrue(unchanged.isNotModified(), "If-None-Match with the ETag must give 304");

    put(retailPos);
    String secondEtag = rawEtag();
    assertNotEquals(firstEtag, secondEtag);

    BundleFetch changed = source.fetch(commerceVersion);
    assertFalse(changed.isNotModified(), "the conditional request must return the new object");
    assertArrayEquals(retailPos, changed.bytes());
    assertEquals(unquote(secondEtag), changed.etag());

    assertTrue(source.fetch(retailPosVersion).isNotModified());
  }

  @Test
  void theEndpointAnswersIfNoneMatchOnTheCurrentEtagWith304() {
    put(commerce);
    String etag = rawEtag();

    S3Exception notModified = assertThrows(S3Exception.class, () -> s3.getObject(
        GetObjectRequest.builder().bucket(bucket).key(key).ifNoneMatch(etag).build(),
        ResponseTransformer.toBytes()));

    assertEquals(NOT_MODIFIED, notModified.statusCode());
  }

  @Test
  void aFetchForAVersionTheSourceDidNotLoadIsUnconditional() throws IOException {
    put(commerce);
    source.fetch(null);

    BundleFetch fetch = source.fetch(retailPosVersion);

    assertFalse(fetch.isNotModified());
    assertArrayEquals(commerce, fetch.bytes());
  }

  @Test
  void aMissingObjectIsAnIoException() {
    IOException error = assertThrows(IOException.class, () -> source.fetch(null));
    assertNotNull(error.getCause());
  }

  private void put(byte[] bytes) {
    s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/json").build(),
        RequestBody.fromBytes(bytes));
  }

  private String rawEtag() {
    return s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build()).eTag();
  }

  private static String unquote(String etag) {
    return etag.startsWith("\"") && etag.endsWith("\"") ? etag.substring(1, etag.length() - 1) : etag;
  }

  private static void createBucketIfAbsent(S3Client client, String name) {
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

  private static String envOr(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  private static void requireEnv(String name) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(name + " must be set when TIERWISE_S3_IT_ENDPOINT is set");
    }
  }
}
