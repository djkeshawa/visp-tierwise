package io.github.visplabs.tierwise.cli;

import io.github.visplabs.tierwise.core.Finding;
import io.github.visplabs.tierwise.core.Severity;
import java.io.PrintWriter;
import java.util.List;

/** Writes findings as text lines or as JSON, and counts them by severity. */
final class FindingReport {

  /**
   * The number of findings of each severity.
   *
   * @param errors findings with severity {@code error}
   * @param warnings findings with severity {@code warn}
   * @param infos findings with severity {@code info}
   */
  record Counts(int errors, int warnings, int infos) {

    /** Returns the one-line summary, such as {@code 1 error, 2 warnings, 0 infos}. */
    String summary() {
      if (errors + warnings + infos == 0) {
        return "no findings";
      }
      return plural(errors, "error", "errors") + ", " + plural(warnings, "warning", "warnings") + ", "
          + plural(infos, "info", "infos");
    }

    private static String plural(int count, String one, String many) {
      return count + " " + (count == 1 ? one : many);
    }
  }

  private FindingReport() {
  }

  /**
   * Tells whether a finding is an engine finding. Engine codes start with {@code E_}; a lint rule
   * identifier such as {@code values/no-op} never does (FIND-12, FIND-13).
   *
   * @param finding a finding
   * @return {@code true} for an engine finding
   */
  static boolean isEngine(Finding finding) {
    return finding.code().startsWith("E_");
  }

  /**
   * Counts findings by severity.
   *
   * @param findings the findings
   * @return the counts
   */
  static Counts count(List<Finding> findings) {
    int errors = 0;
    int warnings = 0;
    int infos = 0;
    for (Finding finding : findings) {
      switch (finding.severity()) {
        case ERROR -> errors++;
        case WARN -> warnings++;
        case INFO -> infos++;
      }
    }
    return new Counts(errors, warnings, infos);
  }

  /**
   * Formats a finding as {@code file:line: severity code message}, followed by {@code (suggestion: X)}
   * when the finding has one.
   *
   * @param finding the finding
   * @return one line
   */
  static String line(Finding finding) {
    StringBuilder text = new StringBuilder()
        .append(finding.file()).append(':').append(finding.line()).append(": ")
        .append(finding.severity().id()).append(' ')
        .append(finding.code()).append(' ')
        .append(finding.message());
    finding.suggestion().ifPresent(name -> text.append(" (suggestion: ").append(name).append(')'));
    return text.toString();
  }

  /**
   * Writes the findings as text lines.
   *
   * @param out the writer
   * @param findings the findings, already in the order of FIND-26
   */
  static void writeText(PrintWriter out, List<Finding> findings) {
    for (Finding finding : findings) {
      out.println(line(finding));
    }
  }

  /**
   * Writes one finding as a JSON object into a document.
   *
   * @param json the document
   * @param finding the finding
   */
  static void writeJson(Json json, Finding finding) {
    json.beginObject()
        .name("code").value(finding.code())
        .name("severity").value(finding.severity().id())
        .name("file").value(finding.file())
        .name("path").path(finding.path())
        .name("line").value(finding.line())
        .name("message").value(finding.message());
    finding.suggestion().ifPresent(name -> json.name("suggestion").value(name));
    json.endObject();
  }

  /**
   * Writes the {@code counts} member of a report.
   *
   * @param json the document
   * @param counts the counts
   */
  static void writeCounts(Json json, Counts counts) {
    json.name("counts").beginObject()
        .name("error").value(counts.errors())
        .name("warn").value(counts.warnings())
        .name("info").value(counts.infos())
        .endObject();
  }

  /**
   * Tells whether any finding has severity {@code error}.
   *
   * @param findings the findings
   * @return {@code true} when the validation fails (FIND-5)
   */
  static boolean failed(List<Finding> findings) {
    return findings.stream().anyMatch(finding -> finding.severity() == Severity.ERROR);
  }
}
