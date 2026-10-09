package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.CompileOptions;
import io.github.tierwise.core.internal.project.ProjectLoad;
import io.github.tierwise.core.internal.project.ProjectLoader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Writes small projects in a temporary directory and runs the standard stages over them with the project
 * directory known, as the compile path does. A test can stop the run before a named stage, to exercise the
 * stages after it alone.
 */
final class PipelineProject {

  private PipelineProject() {
  }

  /**
   * Writes the files and runs the standard stages in order, each when it is enabled.
   *
   * @param dir the project directory
   * @param files the files by path relative to the directory
   * @param stopBefore the name of the stage at which the run stops, or {@code null} to run every stage
   * @return the context after the run
   */
  static CheckContext run(Path dir, Map<String, String> files, String stopBefore) {
    write(dir, files);
    CompileOptions options = CompileOptions.defaults();
    ProjectLoad load = ProjectLoader.load(dir, options);
    CheckContext context = new CheckContext(load, options, dir);
    for (Stage stage : Compiler.standardStages()) {
      if (stage.name().equals(stopBefore)) {
        break;
      }
      if (stage.enabled(context)) {
        stage.run(context);
      }
    }
    return context;
  }

  /**
   * Writes the files of a project.
   *
   * @param dir the project directory
   * @param files the files by path relative to the directory
   */
  static void write(Path dir, Map<String, String> files) {
    try {
      for (Map.Entry<String, String> file : files.entrySet()) {
        Path target = dir.resolve(file.getKey());
        Files.createDirectories(target.getParent());
        Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Describes the findings of a context as {@code code file line path}, in the order of FIND-26.
   *
   * @param context the context
   * @return the descriptions
   */
  static List<String> describe(CheckContext context) {
    List<String> list = new ArrayList<>();
    context.findings().sorted().forEach(finding -> list.add(finding.code() + " " + finding.file() + " "
        + finding.line() + " " + finding.path()));
    return list;
  }

  /**
   * Returns the 1-based line of the first line that contains a text.
   *
   * @param text the text of a file
   * @param marker the text to find
   * @return the line
   */
  static int lineOf(String text, String marker) {
    String[] lines = text.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
      if (lines[i].contains(marker)) {
        return i + 1;
      }
    }
    throw new AssertionError("no line contains " + marker);
  }
}
