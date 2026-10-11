package io.github.visplabs.tierwise.spring;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Tierwise;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpBundleSourceTest {

  private byte[] bundle;
  private String version;
  private StubBundleServer server;

  @BeforeEach
  void startServer() throws IOException {
    bundle = TestBundles.limit(10);
    version = Tierwise.loadBundle(bundle).bundleVersion();
    server = new StubBundleServer(bundle, version);
  }

  @AfterEach
  void stopServer() {
    server.close();
  }

  private HttpBundleSource source(String url, Map<String, String> headers) {
    return new HttpBundleSource(URI.create(url), Duration.ofSeconds(2), Duration.ofSeconds(5), headers);
  }

  @Test
  void firstFetchReturnsTheBytesAndTheEtagWithoutIfNoneMatch() throws IOException {
    BundleFetch fetched = source(server.latestUrl(), Map.of()).fetch(null);

    assertFalse(fetched.isNotModified());
    assertArrayEquals(bundle, fetched.bytes());
    assertEquals(version, fetched.etag());
    assertNull(server.requests().get(0).ifNoneMatch());
  }

  @Test
  void aKnownVersionIsSentAsAQuotedIfNoneMatchAndA304IsNotModified() throws IOException {
    BundleFetch fetched = source(server.latestUrl(), Map.of()).fetch(version);

    assertTrue(fetched.isNotModified());
    assertEquals("\"" + version + "\"", server.requests().get(0).ifNoneMatch());
    assertEquals(1, server.notModifiedCount());
  }

  @Test
  void aDifferentKnownVersionGetsTheBundle() throws IOException {
    BundleFetch fetched = source(server.latestUrl(), Map.of()).fetch("sha256:" + "0".repeat(64));

    assertFalse(fetched.isNotModified());
    assertArrayEquals(bundle, fetched.bytes());
  }

  @Test
  void aNotModifiedFetchHasNoBytes() {
    assertThrows(IllegalStateException.class, () -> BundleFetch.notModified().bytes());
  }

  @Test
  void anErrorStatusIsAnIOException() {
    server.failWith(503);

    IOException e = assertThrows(IOException.class, () -> source(server.latestUrl(), Map.of()).fetch(null));

    assertTrue(e.getMessage().contains("503"));
  }

  @Test
  void aNotFoundStatusIsAnIOException() {
    assertThrows(IOException.class, () -> source(server.baseUrl() + "/other", Map.of()).fetch(null));
  }

  @Test
  void aStoppedServerIsAnIOException() {
    String url = server.latestUrl();
    server.stop();

    assertThrows(IOException.class, () -> source(url, Map.of()).fetch(null));
  }

  @Test
  void configuredHeadersAreSent() throws IOException {
    source(server.latestUrl(), Map.of("Authorization", "Bearer test-token")).fetch(null);

    assertEquals("Bearer test-token", server.requests().get(0).authorization());
  }

  @Test
  void theDescriptionHasNeitherQueryNorUserInfo() {
    HttpBundleSource source = source("http://user:secret@127.0.0.1:" + server.baseUrl().substring(server.baseUrl().lastIndexOf(':') + 1)
        + "/v1/bundles/latest?token=abc", Map.of("Authorization", "Bearer hidden"));

    String description = source.describe();

    assertTrue(description.startsWith("http://127.0.0.1:"), description);
    assertFalse(description.contains("secret") || description.contains("token") || description.contains("hidden"), description);
  }

  @Test
  void aNonHttpUriIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> new HttpBundleSource(URI.create("ftp://example.com/b"), Duration.ofSeconds(1), Duration.ofSeconds(1), Map.of()));
  }
}
