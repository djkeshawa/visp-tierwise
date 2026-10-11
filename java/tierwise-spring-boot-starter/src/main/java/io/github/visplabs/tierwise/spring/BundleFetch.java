package io.github.visplabs.tierwise.spring;

import java.util.Objects;

/**
 * The outcome of one {@link BundleSource#fetch(String)} call: either the source's bundle bytes or the
 * statement that the bundle the caller already has is still current.
 */
public final class BundleFetch {

  private static final BundleFetch NOT_MODIFIED = new BundleFetch(null, null);

  private final byte[] bytes;
  private final String etag;

  private BundleFetch(byte[] bytes, String etag) {
    this.bytes = bytes;
    this.etag = etag;
  }

  /**
   * Returns the outcome for a source whose bundle is the one the caller named.
   *
   * @return the shared "not modified" outcome
   */
  public static BundleFetch notModified() {
    return NOT_MODIFIED;
  }

  /**
   * Creates the outcome for a source that returned bundle bytes.
   *
   * @param bytes the exact bytes of one bundle; they are copied
   * @param etag the entity tag the source reported, with quotes removed, or {@code null} when it has
   *     none. By the server contract it is the {@code bundleVersion}. The starter does not rely on it:
   *     loading the bytes verifies the digest.
   * @return the outcome
   */
  public static BundleFetch of(byte[] bytes, String etag) {
    return new BundleFetch(Objects.requireNonNull(bytes, "bytes").clone(), etag);
  }

  /**
   * Tells whether the source's bundle is the one the caller already has.
   *
   * @return {@code true} for {@link #notModified()}
   */
  public boolean isNotModified() {
    return bytes == null;
  }

  /**
   * Returns the bundle bytes.
   *
   * @return a copy of the bytes
   * @throws IllegalStateException when the outcome is "not modified"
   */
  public byte[] bytes() {
    if (bytes == null) {
      throw new IllegalStateException("the bundle is not modified; there are no bytes");
    }
    return bytes.clone();
  }

  /**
   * Returns the entity tag the source reported.
   *
   * @return the tag without quotes, or {@code null} when none was reported or the bundle is not modified
   */
  public String etag() {
    return etag;
  }
}
