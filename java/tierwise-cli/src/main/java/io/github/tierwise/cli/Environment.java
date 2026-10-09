package io.github.tierwise.cli;

import java.time.Clock;
import java.util.function.Function;

/**
 * The parts of the process that a command reads and that a test replaces: the clock for the
 * {@code createdAt} of a provenance sidecar and the environment variables.
 *
 * @param clock the clock
 * @param getenv reads an environment variable; returns {@code null} when it is not set
 */
record Environment(Clock clock, Function<String, String> getenv) {

  /** Returns the environment of the running process. */
  static Environment system() {
    return new Environment(Clock.systemUTC(), System::getenv);
  }
}
