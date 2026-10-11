package io.github.visplabs.tierwise.spring;

import java.io.IOException;

/**
 * Where the starter reads bundle bytes from. The built-in implementations read the classpath, a file and
 * an HTTP endpoint. A module such as the S3 source implements this interface and publishes its instance
 * as a bean; the auto-configuration then uses it as the primary source in place of the {@code
 * tierwise.source} property.
 *
 * <p>An implementation must be safe to call from several threads. The starter calls it from one thread
 * at a time per {@link TierwiseEvaluators}, but a bean can be shared.
 */
public interface BundleSource {

  /**
   * Describes the source for logs and exception messages. The description must not contain credentials,
   * header values or query strings.
   *
   * @return a short description
   */
  String describe();

  /**
   * Fetches the bundle, or reports that it is unchanged.
   *
   * <p>The caller passes the {@code bundleVersion} of the bundle it holds. A source that can tell cheaply
   * that its bundle has that version (an HTTP source sends the version as {@code If-None-Match} and
   * understands {@code 304}) returns {@link BundleFetch#notModified()}. "Not modified" must mean that the
   * source's bundle is exactly the one named by {@code currentVersion}: a source that merely remembers
   * that it already handed out some bytes must not report it, because the caller may have rejected those
   * bytes (a failed load) or may hold a bundle from another source (a startup snapshot). A source that
   * cannot tell may always return the bytes. When {@code currentVersion} is {@code null} the caller
   * holds nothing, and the source must return the bytes.
   *
   * @param currentVersion the {@code bundleVersion} the caller holds, or {@code null}
   * @return the bytes or "not modified"
   * @throws IOException when the bundle cannot be read: the source is unreachable, answers with an error
   *     status, or has no bundle
   */
  BundleFetch fetch(String currentVersion) throws IOException;
}
