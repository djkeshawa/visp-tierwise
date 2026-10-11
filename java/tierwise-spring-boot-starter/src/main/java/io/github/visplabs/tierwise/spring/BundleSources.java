package io.github.visplabs.tierwise.spring;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/**
 * Creates the built-in {@link BundleSource} implementations from the location strings of the {@code
 * tierwise.*} properties.
 */
public final class BundleSources {

  private static final String SERVER_PATH = "/v1/bundles/latest";

  private BundleSources() {
  }

  /**
   * Creates a source from a location.
   *
   * @param location {@code classpath:path}, {@code file:path} (or a {@code file:} URI), or an
   *     {@code http://} or {@code https://} URL
   * @param classLoader the class loader for {@code classpath:} locations
   * @param http how HTTP sources are read
   * @return the source
   * @throws IllegalArgumentException when the scheme is not one of these or the location is malformed
   */
  public static BundleSource forLocation(String location, ClassLoader classLoader, TierwiseProperties.Http http) {
    if (!isBuiltIn(location) && location.toLowerCase(Locale.ROOT).startsWith("s3://")) {
      throw new IllegalArgumentException("s3:// bundle locations need the tierwise-spring-boot-starter-s3 module: "
          + redact(location));
    }
    Objects.requireNonNull(location, "location");
    String lower = location.toLowerCase(Locale.ROOT);
    if (lower.startsWith("classpath:")) {
      String resource = location.substring("classpath:".length());
      if (resource.isBlank() || resource.endsWith("/")) {
        throw new IllegalArgumentException("a classpath location must name a resource: " + location);
      }
      return new ClasspathBundleSource(classLoader, resource);
    }
    if (lower.startsWith("file:")) {
      return new FileBundleSource(filePath(location));
    }
    if (lower.startsWith("http://") || lower.startsWith("https://")) {
      return http(URI.create(location), http);
    }
    throw new IllegalArgumentException(
        "unsupported bundle location (use classpath:, file:, http:// or https://): " + redact(location));
  }

  /**
   * Tells whether this starter reads a location itself ({@code classpath:}, {@code file:}, {@code http(s)://}).
   * Other schemes, such as {@code s3://}, are served by an optional module's {@link BundleSource} bean.
   *
   * @param location a value of {@code tierwise.source}
   * @return {@code true} for a built-in scheme
   */
  public static boolean isBuiltIn(String location) {
    String lower = Objects.requireNonNull(location, "location").toLowerCase(Locale.ROOT);
    return lower.startsWith("classpath:") || lower.startsWith("file:")
        || lower.startsWith("http://") || lower.startsWith("https://");
  }

  /**
   * Creates a source that reads {@code GET /v1/bundles/latest} from a {@code tierwise-server}.
   *
   * @param baseUrl the server's base URL; a trailing slash is ignored
   * @param http how HTTP sources are read
   * @return the source
   * @throws IllegalArgumentException when the URL is not an absolute {@code http} or {@code https} URL
   */
  public static BundleSource forServer(String baseUrl, TierwiseProperties.Http http) {
    Objects.requireNonNull(baseUrl, "baseUrl");
    String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    String lower = trimmed.toLowerCase(Locale.ROOT);
    if (!(lower.startsWith("http://") || lower.startsWith("https://"))) {
      throw new IllegalArgumentException("the server URL must be an http:// or https:// URL: " + redact(baseUrl));
    }
    return http(URI.create(trimmed + SERVER_PATH), http);
  }

  private static BundleSource http(URI uri, TierwiseProperties.Http http) {
    if (uri.getHost() == null) {
      throw new IllegalArgumentException("the URL has no host: " + redact(uri.toString()));
    }
    return new HttpBundleSource(uri, http.getConnectTimeout(), http.getRequestTimeout(), http.getHeaders());
  }

  private static Path filePath(String location) {
    String rest = location.substring("file:".length());
    if (rest.startsWith("//")) {
      return Path.of(URI.create(location));
    }
    if (rest.isBlank()) {
      throw new IllegalArgumentException("a file location must name a file: " + location);
    }
    return Path.of(rest);
  }

  /** Keeps only the part of a location that cannot hold credentials: the scheme. */
  private static String redact(String location) {
    int colon = location.indexOf(':');
    return colon < 0 ? "(no scheme)" : location.substring(0, colon + 1) + "...";
  }
}
