package io.github.visplabs.tierwise.cli;

import io.github.visplabs.tierwise.core.CompileResult;
import io.github.visplabs.tierwise.core.Finding;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/**
 * {@code tierwise validate [DIR]}: runs the whole validation (FIND-1 to FIND-6) and reports every
 * finding, engine findings and lint findings alike. The exit code is 1 when a finding has severity
 * {@code error}.
 */
@Command(name = "validate", mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
    description = "Validate a project: engine checks, examples and lint. Exit code 1 if any finding is an error.")
final class ValidateCommand extends ProjectCommand {

  @Mixin
  FormatOption formatOption;

  @Override
  public Integer call() {
    CompileResult result = Projects.compile(dir);
    List<Finding> findings = result.findings();
    FindingReport.Counts counts = FindingReport.count(findings);
    boolean failed = result.hasErrors();
    if (formatOption.format == OutputFormat.JSON) {
      Json json = new Json().beginObject()
          .name("command").value("validate")
          .name("ok").value(!failed);
      FindingReport.writeCounts(json, counts);
      json.name("findings").beginArray();
      findings.forEach(finding -> FindingReport.writeJson(json, finding));
      json.endArray().endObject();
      out().println(json);
    } else {
      FindingReport.writeText(out(), findings);
      err().println("validate: " + counts.summary());
    }
    return failed ? ExitCodes.INVALID : ExitCodes.OK;
  }
}
