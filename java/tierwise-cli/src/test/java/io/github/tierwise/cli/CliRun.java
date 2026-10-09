package io.github.tierwise.cli;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

/** Test support: runs the command line in process and gives access to the sample projects. */
final class CliRun {

  /** The fixed time that the tests give to {@code createdAt}. */
  static final Instant NOW = Instant.parse("2026-03-04T05:06:07.891Z");

  /**
   * The outcome of one run.
   *
   * @param code the exit code
   * @param out what was written to standard output
   * @param err what was written to standard error
   */
  record Result(int code, String out, String err) {
  }

  private CliRun() {
  }

  /** Runs the command line with the given environment variables. */
  static Result run(Map<String, String> environment, String... args) {
    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();
    Environment env = new Environment(Clock.fixed(NOW, ZoneOffset.UTC), environment::get);
    int code = TierwiseCli.execute(new PrintWriter(out), new PrintWriter(err), env, args);
    return new Result(code, out.toString().replace("\r\n", "\n"), err.toString().replace("\r\n", "\n"));
  }

  /** Runs the command line with no environment variables. */
  static Result run(String... args) {
    return run(Map.of(), args);
  }

  /** Returns a sample project of the repository, such as {@code commerce}. */
  static Path sample(String name) {
    return Path.of(System.getProperty("tierwise.spec.dir")).resolve("..").resolve("samples").resolve(name).normalize();
  }

  /** Copies a directory tree. */
  static void copyTree(Path from, Path to) throws IOException {
    Files.walkFileTree(from, new SimpleFileVisitor<>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) throws IOException {
        Files.createDirectories(to.resolve(from.relativize(dir).toString()));
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
        Files.copy(file, to.resolve(from.relativize(file).toString()));
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /** Replaces text in a file of a project; the text must be there. */
  static void replace(Path file, String old, String replacement) throws IOException {
    String text = Files.readString(file, StandardCharsets.UTF_8);
    if (!text.contains(old)) {
      throw new AssertionError(old + " is not in " + file);
    }
    Files.writeString(file, text.replace(old, replacement), StandardCharsets.UTF_8);
  }

  /**
   * Adds a boolean key with the strategy {@code any} and the value {@code false}, which is the identity of
   * its strategy, to a project made by {@code init --preset recommended}. The value is a
   * {@code values/no-op} lint finding (LINT-23), which the preset resolves to an error.
   */
  static void addNoOpValue(Path project) throws IOException {
    Path manifest = project.resolve("tierwise.yaml");
    replace(manifest, "  keys:\n", "  keys:\n    service.flag.sticky:\n      type: boolean\n      combine: any\n"
        + "      settable_at: [base]\n      description: A flag that stays on once any level sets it.\n");
    replace(manifest, "  base:\n    service.request.timeout_seconds: 60\n",
        "  base:\n    service.request.timeout_seconds: 60\n    service.flag.sticky: false\n");
  }
}
