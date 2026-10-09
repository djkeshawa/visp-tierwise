package io.github.tierwise.server.store;

/** Raised by a {@link BundleStore} when the storage cannot be reached, read or written. */
public class BundleStoreException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what failed
   * @param cause the underlying failure
   */
  public BundleStoreException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates the exception without a cause.
   *
   * @param message what failed
   */
  public BundleStoreException(String message) {
    super(message);
  }
}
