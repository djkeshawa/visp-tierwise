package io.github.visplabs.tierwise.cli.codegen;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * One generated file.
 *
 * @param path the path relative to the output directory, with {@code /} separators
 * @param content the text of the file, with {@code \n} line endings and a final line feed
 */
public record GeneratedFile(String path, String content) {

  /** Validates the components. */
  public GeneratedFile {
    Objects.requireNonNull(path, "path");
    Objects.requireNonNull(content, "content");
  }

  /**
   * Writes the file below an output directory, creating its parent directories and replacing an
   * existing file of the same name. No other file is touched.
   *
   * @param outputDirectory the directory that the path is relative to
   * @return the path that was written
   * @throws UncheckedIOException if the file cannot be written
   */
  public Path writeTo(Path outputDirectory) {
    Path target = outputDirectory;
    for (String segment : path.split("/")) {
      target = target.resolve(segment);
    }
    try {
      Path parent = target.getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.writeString(target, content, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException("cannot write " + target, e);
    }
    return target;
  }
}
