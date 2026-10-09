package io.github.tierwise.spring;

/**
 * Raised when the application starts and no bundle can be loaded: the primary source failed and no
 * snapshot is configured, or the snapshot failed too.
 */
public class BundleLoadException extends IllegalStateException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what failed
   * @param cause the failure of the primary source
   */
  public BundleLoadException(String message, Throwable cause) {
    super(message, cause);
  }
}
