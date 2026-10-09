package io.github.tierwise.cli;

/**
 * A fault of the command line itself: a missing directory, a file that cannot be written, an unknown
 * preset. It ends the command with exit code {@link ExitCodes#USAGE} and a one-line message.
 */
final class UsageException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  UsageException(String message) {
    super(message);
  }

  UsageException(String message, Throwable cause) {
    super(message, cause);
  }
}
