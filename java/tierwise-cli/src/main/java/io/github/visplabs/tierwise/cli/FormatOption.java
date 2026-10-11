package io.github.visplabs.tierwise.cli;

import picocli.CommandLine.Option;

/** The {@code --format} option shared by {@code validate}, {@code lint} and {@code test}. */
final class FormatOption {

  @Option(names = "--format", paramLabel = "FORMAT",
      description = "Report format: ${COMPLETION-CANDIDATES}. Use json for CI tooling (default: text).")
  OutputFormat format = OutputFormat.TEXT;
}
