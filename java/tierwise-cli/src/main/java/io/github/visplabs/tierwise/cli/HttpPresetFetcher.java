package io.github.visplabs.tierwise.cli;

import io.github.visplabs.tierwise.core.PresetFetcher;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Fetches the presets that a manifest names by URL (MANIFEST-25 to MANIFEST-29). Only the command line
 * opens a network connection; the core verifies the digest of what this class returns before it reads
 * a byte of it.
 *
 * <p>Only {@code https} URLs are fetched, redirects stay on {@code https}, and a response larger than
 * {@link #MAX_BYTES} is refused.
 */
final class HttpPresetFetcher implements PresetFetcher {

  /** The largest preset that is fetched, in bytes. */
  static final int MAX_BYTES = 1 << 20;

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

  private HttpClient client;

  @Override
  public byte[] fetch(String url) throws IOException {
    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new IOException("not a valid URL: " + url, e);
    }
    if (!"https".equalsIgnoreCase(uri.getScheme())) {
      throw new IOException("only https URLs are fetched: " + url);
    }
    HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).GET().build();
    try {
      HttpResponse<InputStream> response = client().send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (InputStream body = response.body()) {
        if (response.statusCode() != 200) {
          throw new IOException("HTTP " + response.statusCode() + " for " + url);
        }
        byte[] bytes = body.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) {
          throw new IOException("the preset is larger than " + MAX_BYTES + " bytes: " + url);
        }
        return bytes;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted while fetching " + url, e);
    }
  }

  private HttpClient client() {
    if (client == null) {
      client = HttpClient.newBuilder()
          .connectTimeout(CONNECT_TIMEOUT)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();
    }
    return client;
  }
}
