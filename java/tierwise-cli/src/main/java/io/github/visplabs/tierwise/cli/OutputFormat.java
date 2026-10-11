package io.github.visplabs.tierwise.cli;

/** The report format of {@code validate}, {@code lint} and {@code test}. */
enum OutputFormat {
  /** One finding per line: {@code file:line: severity code message}. */
  TEXT,
  /** One JSON document on standard output, for CI tooling. */
  JSON
}
