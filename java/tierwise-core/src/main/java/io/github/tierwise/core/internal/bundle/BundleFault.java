package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.Code;

/**
 * A failed load check: the stable code that EVAL-14 assigns to the check and a message for diagnostics.
 * The bundle reader converts it into a {@link io.github.tierwise.core.TierwiseException}.
 */
final class BundleFault extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String code;

  BundleFault(String code, String message) {
    super(message);
    this.code = code;
  }

  /**
   * Creates an integrity fault.
   *
   * @param message the description of the failed check, with its clause ID
   * @return the fault, with code {@code E_BUNDLE_INTEGRITY}
   */
  static BundleFault integrity(String message) {
    return new BundleFault(Code.E_BUNDLE_INTEGRITY, message);
  }

  /**
   * Returns the code of the failed check.
   *
   * @return {@code E_BUNDLE_INTEGRITY} or {@code E_BUNDLE_UNSUPPORTED}
   */
  String code() {
    return code;
  }
}
