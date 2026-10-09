package io.github.tierwise.cli;

import io.github.tierwise.core.CompileResult;
import io.github.tierwise.core.Finding;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/**
 * {@code tierwise lint [DIR]}: reports the lint findings only. Lint rules run only when the validation
 * has no engine finding (LINT-11), so a project with an engine finding gets no lint findings: the
 * command says so and exits with 1, because the project does not validate.
 */
@Command(name = "lint", mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
    description = "Report lint findings only. Lint runs only when the project has no engine finding.")
final class LintCommand extends ProjectCommand {

  @Mixin
  FormatOption formatOption;

  @Override
  public Integer call() {
    CompileResult result = Projects.compile(dir);
    long engineFindings = result.findings().stream().filter(FindingReport::isEngine).count();
    if (engineFindings > 0) {
      return skipped(engineFindings);
    }
    List<Finding> findings = result.findings();
    FindingReport.Counts counts = FindingReport.count(findings);
    boolean failed = result.hasErrors();
    if (formatOption.format == OutputFormat.JSON) {
      Json json = new Json().beginObject()
          .name("command").value("lint")
          .name("ok").value(!failed)
          .name("lintRan").value(true);
      FindingReport.writeCounts(json, counts);
      json.name("findings").beginArray();
      findings.forEach(finding -> FindingReport.writeJson(json, finding));
      json.endArray().endObject();
      out().println(json);
    } else {
      FindingReport.writeText(out(), findings);
      err().println("lint: " + counts.summary());
    }
    return failed ? ExitCodes.INVALID : ExitCodes.OK;
  }

  private int skipped(long engineFindings) {
    if (formatOption.format == OutputFormat.JSON) {
      Json json = new Json().beginObject()
          .name("command").value("lint")
          .name("ok").value(false)
          .name("lintRan").value(false)
          .name("engineFindings").value(engineFindings)
          .name("findings").beginArray().endArray()
          .endObject();
      out().println(json);
    } else {
      err().println("lint: lint did not run, because the project has " + engineFindings
          + " engine finding(s). Run `tierwise validate` to see them.");
    }
    return ExitCodes.INVALID;
  }
}
