package io.github.visplabs.tierwise.cli;

import io.github.visplabs.tierwise.core.CompileOptions;
import io.github.visplabs.tierwise.core.CompileResult;
import io.github.visplabs.tierwise.core.Tierwise;
import java.nio.file.Files;
import java.nio.file.Path;

/** Compiles the project that a command names. */
final class Projects {

  private Projects() {
  }

  /**
   * Validates and compiles the project in a directory, with URL presets fetched over {@code https}.
   *
   * @param dir the project directory
   * @return the findings and, when none is an error, the canonical bundle
   * @throws UsageException if {@code dir} is not a directory
   */
  static CompileResult compile(Path dir) {
    if (!Files.isDirectory(dir)) {
      throw new UsageException("not a directory: " + dir);
    }
    CompileOptions options = CompileOptions.builder().presetFetcher(new HttpPresetFetcher()).build();
    return Tierwise.compile(dir, options);
  }
}
