package io.github.visplabs.tierwise.cli;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * {@code tierwise init [--preset recommended|minimal|entitlements] [DIR]}: writes a minimal project
 * that extends the chosen preset and validates with zero findings. The project is one file,
 * {@code tierwise.yaml}, with its schema and values inline (MANIFEST-13). An existing manifest is never
 * overwritten.
 */
@Command(name = "init", mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
    description = "Write a minimal, valid project that extends a built-in preset.")
final class InitCommand implements Callable<Integer> {

  /** The file name of the manifest (MANIFEST-1). */
  static final String MANIFEST = "tierwise.yaml";

  @Spec
  CommandSpec spec;

  @Option(names = "--preset", paramLabel = "PRESET",
      description = "The built-in preset to extend: ${COMPLETION-CANDIDATES} (default: recommended).")
  Preset preset = Preset.RECOMMENDED;

  @Parameters(index = "0", arity = "0..1", paramLabel = "DIR",
      description = "The directory to write the project into; created if missing (default: the current directory).")
  Path dir = Path.of(".");

  @Override
  public Integer call() {
    Path manifest = dir.resolve(MANIFEST);
    try {
      Files.createDirectories(dir);
      Files.writeString(manifest, template(preset), StandardCharsets.UTF_8,
          StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    } catch (FileAlreadyExistsException e) {
      throw new UsageException(manifest + " already exists; init never overwrites a project");
    } catch (IOException e) {
      throw new UsageException("cannot write " + manifest + ": " + e.getMessage(), e);
    }
    spec.commandLine().getOut().println("created " + manifest + " (preset: " + preset.id() + ")");
    spec.commandLine().getOut().println("next: tierwise validate " + dir);
    return ExitCodes.OK;
  }

  /**
   * Reads the template of a preset.
   *
   * @param preset the preset
   * @return the text of the manifest
   */
  static String template(Preset preset) {
    String resource = "templates/" + preset.id() + ".yaml";
    try (InputStream in = InitCommand.class.getResourceAsStream(resource)) {
      if (in == null) {
        throw new UsageException("the template for preset " + preset.id() + " is not part of this build");
      }
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UsageException("cannot read the template for preset " + preset.id(), e);
    }
  }
}
