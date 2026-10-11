package io.github.visplabs.tierwise.spring;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Reads a bundle over HTTP with the JDK {@link HttpClient}.
 *
 * <p>When the caller holds a bundle, the request carries {@code If-None-Match} with the bundle's
 * {@code bundleVersion} in double quotes, which is the ETag that {@code tierwise-server} serves for
 * {@code GET /v1/bundles/latest}. The answer {@code 304 Not Modified} becomes {@link
 * BundleFetch#notModified()}, {@code 200} returns the body, and any other status is an {@link
 * IOException}. Redirects are followed, except from {@code https} to {@code http}.
 */
public final class HttpBundleSource implements BundleSource {

  /** The largest body that is read, in bytes. A larger body is rejected rather than held in memory. */
  static final int MAX_BODY_BYTES = 64 * 1024 * 1024;

  private final URI uri;
  private final Duration requestTimeout;
  private final Map<String, String> headers;
  private final HttpClient client;

  /**
   * Creates a source.
   *
   * @param uri the absolute {@code http} or {@code https} URI to {@code GET}
   * @param connectTimeout the time allowed to open a connection
   * @param requestTimeout the time allowed for one request, from sending it to receiving the full body
   * @param headers extra request headers, such as {@code Authorization}; their values are never logged
   */
  public HttpBundleSource(URI uri, Duration connectTimeout, Duration requestTimeout, Map<String, String> headers) {
    this.uri = Objects.requireNonNull(uri, "uri");
    String scheme = uri.getScheme();
    if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
      throw new IllegalArgumentException("not an http or https URI: " + describe(uri));
    }
    this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
    this.headers = new LinkedHashMap<>(Objects.requireNonNull(headers, "headers"));
    this.client = HttpClient.newBuilder()
        .connectTimeout(Objects.requireNonNull(connectTimeout, "connectTimeout"))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();
  }

  @Override
  public String describe() {
    return describe(uri);
  }

  @Override
  public BundleFetch fetch(String currentVersion) throws IOException {
    HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(requestTimeout).GET();
    request.header("User-Agent", "tierwise-spring-boot-starter");
    headers.forEach(request::header);
    if (currentVersion != null) {
      request.header("If-None-Match", "\"" + currentVersion + "\"");
    }
    HttpResponse<InputStream> response;
    try {
      response = client.send(request.build(), HttpResponse.BodyHandlers.ofInputStream());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while reading " + describe(), e);
    }
    try (InputStream body = response.body()) {
      int status = response.statusCode();
      if (status == 304 && currentVersion != null) {
        return BundleFetch.notModified();
      }
      if (status != 200) {
        throw new IOException("HTTP status " + status + " from " + describe());
      }
      byte[] bytes = body.readNBytes(MAX_BODY_BYTES + 1);
      if (bytes.length > MAX_BODY_BYTES) {
        throw new IOException("the response from " + describe() + " is larger than " + MAX_BODY_BYTES + " bytes");
      }
      return BundleFetch.of(bytes, entityTag(response));
    }
  }

  private static String entityTag(HttpResponse<?> response) {
    String tag = response.headers().firstValue("ETag").orElse(null);
    if (tag == null) {
      return null;
    }
    String value = tag.startsWith("W/") ? tag.substring(2) : tag;
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      value = value.substring(1, value.length() - 1);
    }
    return value;
  }

  private static String describe(URI uri) {
    StringBuilder text = new StringBuilder();
    text.append(uri.getScheme()).append("://").append(uri.getHost());
    if (uri.getPort() >= 0) {
      text.append(':').append(uri.getPort());
    }
    if (uri.getRawPath() != null) {
      text.append(uri.getRawPath());
    }
    return text.toString();
  }
}
