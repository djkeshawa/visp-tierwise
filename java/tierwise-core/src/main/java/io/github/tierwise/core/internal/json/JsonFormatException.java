package io.github.tierwise.core.internal.json;

/**
 * Raised by {@link StrictJson} for input that the bundle format does not accept: bytes that are not UTF-8,
 * a byte-order mark, malformed JSON, a repeated member name at any depth (BUNDLE-111), a number that is not
 * an integer within +/-(2^53 - 1) (BUNDLE-11, BUNDLE-12), an unpaired surrogate (BUNDLE-13) or data after
 * the document. The bundle reader turns it into {@code E_BUNDLE_INTEGRITY} (BUNDLE-116).
 */
public final class JsonFormatException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message a description of the defect, for diagnostics only
   */
  public JsonFormatException(String message) {
    super(message);
  }
}
