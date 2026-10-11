package io.github.visplabs.tierwise.cli;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.CompileResult;
import io.github.visplabs.tierwise.core.Finding;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/**
 * {@code tierwise test [DIR]}: validates the project and reports each example as passed or failed.
 *
 * <p>Examples run only when the validation has no engine finding other than a failing example
 * (POL-47), so a project with another engine finding gets no example report, and the command says so.
 * The exit code is 1 when a finding has severity {@code error}, which includes every failing example.
 */
@Command(name = "test", mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
    description = "Validate a project and report each example as passed or failed.")
final class TestCommand extends ProjectCommand {

  @Mixin
  FormatOption formatOption;

  /** One example and whether it passed. */
  private record Outcome(ExampleCatalog.Entry example, boolean passed) {
  }

  @Override
  public Integer call() {
    CompileResult result = Projects.compile(dir);
    List<Finding> findings = result.findings();
    boolean examplesRan = findings.stream()
        .noneMatch(finding -> FindingReport.isEngine(finding) && !finding.code().equals(Code.E_EXAMPLE_FAILED));
    List<Outcome> outcomes = examplesRan ? outcomes(findings) : List.of();
    FindingReport.Counts counts = FindingReport.count(findings);
    boolean failed = result.hasErrors();
    if (formatOption.format == OutputFormat.JSON) {
      writeJson(findings, outcomes, examplesRan, counts, failed);
    } else {
      writeText(findings, outcomes, examplesRan, counts);
    }
    return failed ? ExitCodes.INVALID : ExitCodes.OK;
  }

  private List<Outcome> outcomes(List<Finding> findings) {
    List<Outcome> outcomes = new ArrayList<>();
    for (ExampleCatalog.Entry example : ExampleCatalog.list(dir)) {
      boolean failedExample = findings.stream().anyMatch(finding ->
          finding.code().equals(Code.E_EXAMPLE_FAILED)
              && finding.file().equals(example.file())
              && example.contains(finding.path()));
      outcomes.add(new Outcome(example, !failedExample));
    }
    return outcomes;
  }

  private void writeText(List<Finding> findings, List<Outcome> outcomes, boolean examplesRan,
      FindingReport.Counts counts) {
    for (Outcome outcome : outcomes) {
      ExampleCatalog.Entry example = outcome.example();
      out().println((outcome.passed() ? "pass " : "FAIL ") + example.file() + ":" + example.line() + ": "
          + example.name());
    }
    FindingReport.writeText(out(), findings);
    if (!examplesRan) {
      err().println("test: examples were not run, because the project has engine findings (POL-47).");
    } else {
      long passed = outcomes.stream().filter(Outcome::passed).count();
      err().println("test: " + outcomes.size() + " examples, " + passed + " passed, "
          + (outcomes.size() - passed) + " failed");
    }
    err().println("test: " + counts.summary());
  }

  private void writeJson(List<Finding> findings, List<Outcome> outcomes, boolean examplesRan,
      FindingReport.Counts counts, boolean failed) {
    Json json = new Json().beginObject()
        .name("command").value("test")
        .name("ok").value(!failed)
        .name("examplesRan").value(examplesRan);
    FindingReport.writeCounts(json, counts);
    json.name("examples").beginArray();
    for (Outcome outcome : outcomes) {
      ExampleCatalog.Entry example = outcome.example();
      json.beginObject()
          .name("file").value(example.file())
          .name("path").path(example.path())
          .name("line").value(example.line())
          .name("name").value(example.name())
          .name("result").value(outcome.passed() ? "pass" : "fail")
          .endObject();
    }
    json.endArray().name("findings").beginArray();
    findings.forEach(finding -> FindingReport.writeJson(json, finding));
    json.endArray().endObject();
    out().println(json);
  }
}
