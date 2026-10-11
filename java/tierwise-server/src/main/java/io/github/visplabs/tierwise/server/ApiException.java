package io.github.visplabs.tierwise.server;

import java.util.Objects;

/**
 * A failure that the server reports to the caller as a JSON error {@code {code, message}} with an HTTP
 * status.
 */
public class ApiException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final int status;
  private final String code;

  /**
   * Creates an exception.
   *
   * @param status the HTTP status
   * @param code the error code, from {@code docs/requirements.md} section 6 or {@link ErrorCodes}
   * @param message a human-readable message
   */
  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = Objects.requireNonNull(code, "code");
  }

  /**
   * Returns the HTTP status.
   *
   * @return the status code
   */
  public int status() {
    return status;
  }

  /**
   * Returns the error code.
   *
   * @return the code
   */
  public String code() {
    return code;
  }
}
