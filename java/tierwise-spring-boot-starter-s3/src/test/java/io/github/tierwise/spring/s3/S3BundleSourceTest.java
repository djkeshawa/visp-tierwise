package io.github.tierwise.spring.s3;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Tierwise;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.spring.BundleFetch;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

class S3BundleSourceTest {

  private static final S3Location LOCATION = new S3Location("bundles", "prod/commerce.json");
  private static final String ETAG_1 = "\"etag-1\"";
  private static final String ETAG_2 = "\"etag-2\"";

  private byte[] commerce;
  private byte[] retailPos;
  private String commerceVersion;
  private String retailPosVersion;
  private FakeS3Client client;
  private S3BundleSource source;

  @BeforeEach
  void setUp() throws IOException {
    Path bundles = Path.of(System.getProperty("tierwise.spec.dir")).resolve("conformance").resolve("bundles");
    commerce = Files.readAllBytes(bundles.resolve("commerce.json"));
    retailPos = Files.readAllBytes(bundles.resolve("retail-pos.json"));
    commerceVersion = Tierwise.loadBundle(commerce).bundleVersion();
    retailPosVersion = Tierwise.loadBundle(retailPos).bundleVersion();
    client = new FakeS3Client();
    source = new S3BundleSource(client, LOCATION);
  }

  @Test
  void theFirstFetchIsUnconditionalAndReturnsTheVerifiedBytes() throws IOException {
    client.reply(ETAG_1, commerce);

    BundleFetch fetch = source.fetch(null);

    assertFalse(fetch.isNotModified());
    assertArrayEquals(commerce, fetch.bytes());
    assertEquals("etag-1", fetch.etag());
    GetObjectRequest request = client.requests().get(0);
    assertEquals("bundles", request.bucket());
    assertEquals("prod/commerce.json", request.key());
    assertNull(request.ifNoneMatch());
  }

  @Test
  void aFetchForTheLoadedVersionSendsTheEtagAndReturnsNotModifiedOn304() throws IOException {
    client.reply(ETAG_1, commerce).notModified();
    source.fetch(null);

    BundleFetch fetch = source.fetch(commerceVersion);

    assertTrue(fetch.isNotModified());
    assertEquals(ETAG_1, client.requests().get(1).ifNoneMatch());
  }

  @Test
  void aFetchForAnotherVersionIsUnconditional() throws IOException {
    client.reply(ETAG_1, commerce).reply(ETAG_1, commerce);
    source.fetch(null);

    BundleFetch fetch = source.fetch(retailPosVersion);

    assertFalse(fetch.isNotModified());
    assertNull(client.requests().get(1).ifNoneMatch());
  }

  @Test
  void aFetchForNoVersionIsAlwaysUnconditional() throws IOException {
    client.reply(ETAG_1, commerce).reply(ETAG_1, commerce);
    source.fetch(null);

    assertFalse(source.fetch(null).isNotModified());
    assertNull(client.requests().get(1).ifNoneMatch());
  }

  @Test
  void aChangedObjectIsReturnedAndItsNewEtagIsSentForTheNewVersion() throws IOException {
    client.reply(ETAG_1, commerce).reply(ETAG_2, retailPos).notModified();
    source.fetch(null);

    BundleFetch changed = source.fetch(commerceVersion);
    BundleFetch unchanged = source.fetch(retailPosVersion);

    assertFalse(changed.isNotModified());
    assertArrayEquals(retailPos, changed.bytes());
    assertEquals("etag-2", changed.etag());
    assertTrue(unchanged.isNotModified());
    assertEquals(ETAG_2, client.requests().get(2).ifNoneMatch());
  }

  @Test
  void aNotModifiedAnswerWithoutAConditionIsAFailure() {
    client.notModified();

    assertThrows(IOException.class, () -> source.fetch(null));
  }

  @Test
  void aBundleTheCoreRejectsFailsTheFetchAndKeepsThePreviousVersionAndEtag() throws IOException {
    client.reply(ETAG_1, commerce)
        .reply(ETAG_2, "not a bundle".getBytes(StandardCharsets.UTF_8))
        .notModified();
    source.fetch(null);

    IOException e = assertThrows(IOException.class, () -> source.fetch(commerceVersion + "x"));
    assertCode(e, Code.E_BUNDLE_INTEGRITY);
    assertTrue(source.fetch(commerceVersion).isNotModified());
    assertEquals(ETAG_1, client.requests().get(2).ifNoneMatch());
  }

  @Test
  void aBundleWhoseDigestDoesNotMatchItsContentIsRejected() {
    String text = new String(commerce, StandardCharsets.UTF_8);
    String tampered = text.replaceFirst("\"bundleVersion\":\"sha256:ab1c", "\"bundleVersion\":\"sha256:ab1d");
    assertFalse(tampered.equals(text));
    client.reply(ETAG_1, tampered.getBytes(StandardCharsets.UTF_8));

    IOException e = assertThrows(IOException.class, () -> source.fetch(null));

    assertCode(e, Code.E_BUNDLE_INTEGRITY);
  }

  @Test
  void aRejectedFirstBundleLeavesNoEtagSoTheNextFetchIsUnconditional() throws IOException {
    client.reply(ETAG_1, "not a bundle".getBytes(StandardCharsets.UTF_8)).reply(ETAG_1, commerce);
    assertThrows(IOException.class, () -> source.fetch(null));

    BundleFetch fetch = source.fetch(commerceVersion);

    assertFalse(fetch.isNotModified());
    assertNull(client.requests().get(1).ifNoneMatch());
  }

  @Test
  void aMissingObjectIsAReadFailureAndKeepsThePreviousVersionAndEtag() throws IOException {
    client.reply(ETAG_1, commerce)
        .fail(NoSuchKeyException.builder().statusCode(404).message("The key does not exist").build());
    source.fetch(null);

    IOException e = assertThrows(IOException.class, () -> source.fetch(commerceVersion));

    assertInstanceOf(NoSuchKeyException.class, e.getCause());
    assertTrue(e.getMessage().contains("s3://bundles/prod/commerce.json"));
    assertEquals(ETAG_1, client.requests().get(1).ifNoneMatch());
  }

  @Test
  void anAccessErrorIsAReadFailure() {
    client.fail(S3Exception.builder().statusCode(403).message("Forbidden").build());

    IOException e = assertThrows(IOException.class, () -> source.fetch(null));

    assertInstanceOf(S3Exception.class, e.getCause());
  }

  @Test
  void aServerErrorOnARefreshIsAReadFailureAndKeepsTheEtag() throws IOException {
    client.reply(ETAG_1, commerce).fail(S3Exception.builder().statusCode(500).message("Internal").build()).notModified();
    source.fetch(null);

    assertThrows(IOException.class, () -> source.fetch(commerceVersion));
    assertTrue(source.fetch(commerceVersion).isNotModified());
    assertEquals(ETAG_1, client.requests().get(2).ifNoneMatch());
  }

  @Test
  void aNetworkFailureIsAReadFailure() {
    client.fail(SdkClientException.create("connection reset"));

    IOException e = assertThrows(IOException.class, () -> source.fetch(null));

    assertInstanceOf(SdkClientException.class, e.getCause());
  }

  @Test
  void aResponseWithoutAnEtagIsNeverSentAsAConditionalRequest() throws IOException {
    client.reply(null, commerce).reply(ETAG_1, commerce);

    BundleFetch first = source.fetch(null);
    BundleFetch second = source.fetch(commerceVersion);

    assertNull(first.etag());
    assertFalse(second.isNotModified());
    assertNull(client.requests().get(1).ifNoneMatch());
  }

  @Test
  void anObjectLargerThanTheStarterLimitIsRefused() {
    client.reply(ETAG_1, new byte[S3BundleSource.MAX_BODY_BYTES + 1]);

    IOException e = assertThrows(IOException.class, () -> source.fetch(null));

    assertTrue(e.getMessage().contains("larger than"));
  }

  @Test
  void concurrentFetchesRunOneAtATime() throws Exception {
    client.delay(40).reply(ETAG_1, commerce).reply(ETAG_1, commerce).reply(ETAG_1, commerce).reply(ETAG_1, commerce);
    ExecutorService pool = Executors.newFixedThreadPool(4);
    List<Future<BundleFetch>> results = new ArrayList<>();
    try {
      for (int i = 0; i < 4; i++) {
        results.add(pool.submit(() -> source.fetch(null)));
      }
      for (Future<BundleFetch> result : results) {
        assertArrayEquals(commerce, result.get().bytes());
      }
    } finally {
      pool.shutdownNow();
    }
    assertEquals(1, client.maxInFlight());
    assertEquals(4, client.requests().size());
  }

  @Test
  void describesTheLocationWithoutCredentials() {
    assertEquals("s3://bundles/prod/commerce.json", source.describe());
    assertEquals(LOCATION, source.location());
  }

  @Test
  void constructorRejectsNulls() {
    assertThrows(NullPointerException.class, () -> new S3BundleSource(null, LOCATION));
    assertThrows(NullPointerException.class, () -> new S3BundleSource(client, null));
  }

  private static void assertCode(IOException e, String code) {
    assertInstanceOf(TierwiseException.class, e.getCause());
    assertEquals(code, ((TierwiseException) e.getCause()).code());
  }
}
