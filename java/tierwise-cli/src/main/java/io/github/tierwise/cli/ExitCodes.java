package io.github.tierwise.cli;

/** The process exit codes of the command line. */
final class ExitCodes {

  /** The command succeeded. */
  static final int OK = 0;

  /** The validation failed (FIND-5), or the thing asked for was not found. */
  static final int INVALID = 1;

  /** The command line was wrong, or a file could not be read or written. */
  static final int USAGE = 2;

  private ExitCodes() {
  }
}
