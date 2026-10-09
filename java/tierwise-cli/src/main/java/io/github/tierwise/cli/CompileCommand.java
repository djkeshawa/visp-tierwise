package io.github.tierwise.cli;

import io.github.tierwise.core.CompileResult;
import io.github.tierwise.core.Finding;
import io.github.tierwise.core.LoadedBundle;
import io.github.tierwise.core.Tierwise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code tierwise compile [DIR] [-o bundle.json] [--provenance]}: validates the project and, when the
 * validation does not fail, writes the canonical bundle (FIND-7, FIND-8). With {@code --provenance} it
 * also writes the sidecar {@code <stem>.provenance.json} beside the bundle (BUNDLE-149).
 *
 * <p>Nothing is written when a finding has severity {@code error}. An existing bundle file is replaced,
 * because the same sources give the same bytes (DET-3). An existing sidecar is not replaced
 * (BUNDLE-147): the command stops with exit code 2 before it writes anything.
 */
@Command(name = "compile", mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
    description = "Validate a project and write its canonical bundle.")
final class CompileCommand extends ProjectCommand {

  private final Environment environment;

  @Option(names = {"-o", "--output"}, paramLabel = "FILE",
      description = "The bundle file to write (default: bundle.json in the current directory).")
  Path output = Path.of("bundle.json");

  @Option(names = "--provenance",
      description = "Also write <stem>.provenance.json beside the bundle: bundleVersion, createdAt and,"
          + " when known, gitCommit (from GITHUB_SHA or git).")
  boolean provenance;

  CompileCommand(Environment environment) {
    this.environment = environment;
  }

  @Override
  public Integer call() {
    CompileResult result = Projects.compile(dir);
    for (Finding finding : result.findings()) {
      out().println(FindingReport.line(finding));
    }
    FindingReport.Counts counts = FindingReport.count(result.findings());
    Optional<byte[]> bundle = result.bundle();
    if (result.hasErrors() || bundle.isEmpty()) {
      err().println("compile: no bundle written (" + counts.summary() + ")");
      return ExitCodes.INVALID;
    }
    Path sidecar = provenance ? Provenance.sidecarFor(output) : null;
    if (sidecar != null && Files.exists(sidecar)) {
      throw new UsageException("the provenance sidecar exists and is never replaced (BUNDLE-147): " + sidecar);
    }
    LoadedBundle loaded = Tierwise.loadBundle(bundle.get());
    writeBundle(bundle.get());
    out().println("wrote " + output + " (" + bundle.get().length + " bytes, " + loaded.bundleVersion() + ")");
    if (provenance) {
      Path written = Provenance.write(output, loaded.bundleVersion(), environment.clock(),
          Provenance.gitCommit(environment.getenv(), dir));
      out().println("wrote " + written);
    }
    if (counts.errors() + counts.warnings() + counts.infos() > 0) {
      err().println("compile: " + counts.summary());
    }
    return ExitCodes.OK;
  }

  private void writeBundle(byte[] bytes) {
    try {
      Path parent = output.toAbsolutePath().getParent();
      if (parent != null) {
        Files.createDirectories(parent);
      }
      Files.write(output, bytes);
    } catch (IOException e) {
      throw new UsageException("cannot write " + output + ": " + e.getMessage(), e);
    }
  }
}
