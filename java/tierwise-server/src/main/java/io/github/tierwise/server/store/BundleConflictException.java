package io.github.tierwise.server.store;

/**
 * Raised by {@link BundleStore#putBundle} when different bytes are already stored under a version. The stored
 * bytes are left unchanged.
 */
public class BundleConflictException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param version the {@code bundleVersion} that already holds different bytes
   */
  public BundleConflictException(String version) {
    super("a different bundle is already stored for " + version);
  }
}
