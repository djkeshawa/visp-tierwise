package io.github.visplabs.tierwise.core.conformance;

/**
 * A vector that the runner cannot execute as written (VEC-111). The test reports it as failed and does
 * not compare the outcome with {@code expect}.
 */
final class RunnerFailure extends RuntimeException {

  private static final long serialVersionUID = 1L;

  RunnerFailure(String message) {
    super("runner failure: " + message);
  }

  RunnerFailure(String message, Throwable cause) {
    super("runner failure: " + message, cause);
  }
}
