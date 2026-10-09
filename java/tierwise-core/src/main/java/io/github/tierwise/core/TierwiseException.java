package io.github.tierwise.core;

import java.util.Objects;

/**
 * Raised for every evaluation error (EVAL-22). The {@linkplain #code() code} is normative and comes
 * from {@link Code}; the message is not.
 */
public class TierwiseException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String code;

  /**
   * Creates an exception.
   *
   * @param code a code from {@link Code}
   * @param message a human-readable message
   */
  public TierwiseException(String code, String message) {
    super(message);
    this.code = Objects.requireNonNull(code, "code");
  }

  /**
   * Creates an exception with a cause.
   *
   * @param code a code from {@link Code}
   * @param message a human-readable message
   * @param cause the underlying cause
   */
  public TierwiseException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = Objects.requireNonNull(code, "code");
  }

  /**
   * Returns the stable error code.
   *
   * @return a code such as {@code E_UNKNOWN_KEY}
   */
  public String code() {
    return code;
  }

  @Override
  public String getMessage() {
    return code + ": " + super.getMessage();
  }
}
