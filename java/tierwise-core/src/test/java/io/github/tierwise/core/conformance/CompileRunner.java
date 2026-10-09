package io.github.tierwise.core.conformance;

import io.github.tierwise.core.CompileResult;
import io.github.tierwise.core.Finding;
import io.github.tierwise.core.Tierwise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.opentest4j.AssertionFailedError;

/**
 * Runs one compile case (section 12.5): compiles the case's project, or the sample it names, and compares
 * the outcome with {@code expected.yaml}.
 *
 * <p>A {@code findings} expectation is met when the set of finding keys (code, file, path, line) equals
 * the listed set (VEC-98). A {@code bundle} expectation is met when the compiler reports no finding of any
 * severity and its bytes equal the named bundle file (VEC-94). The optional {@code build} mapping only
 * feeds the provenance writer, which the compiler API does not expose, so it is validated and ignored
 * (VEC-107).
 */
final class CompileRunner {

  private static final Set<String> FIELDS =
      Set.of("id", "specVersion", "clauses", "description", "expect", "project", "build");
  private static final Set<String> BUILD_FIELDS = Set.of("gitCommit", "createdAt", "publisher");

  private CompileRunner() {
  }

  /**
   * Runs the compile case in a directory.
   *
   * @param caseDir a directory directly under {@code compile/}
   * @throws RunnerFailure if the case cannot be executed as written
   * @throws AssertionFailedError if the outcome differs from the expectation
   */
  static void run(Path caseDir) {
    Path expectedFile = caseDir.resolve("expected.yaml");
    if (!Files.isRegularFile(expectedFile)) {
      throw new RunnerFailure("the case has no expected.yaml (VEC-4)");
    }
    Map<String, Object> doc = VectorFiles.loadMapping(expectedFile);
    for (String name : doc.keySet()) {
      if (!FIELDS.contains(name)) {
        throw new RunnerFailure("unknown field " + name + " (VEC-19)");
      }
    }
    if (!"0.1".equals(doc.get("specVersion"))) {
      throw new RunnerFailure("unsupported specVersion (VEC-15)");
    }
    if (doc.get("build") != null) {
      for (String name : VectorFiles.asStringMap(doc.get("build"), "build").keySet()) {
        if (!BUILD_FIELDS.contains(name)) {
          throw new RunnerFailure("unknown field build." + name + " (VEC-107)");
        }
      }
    }
    Path project = projectDir(caseDir, doc);
    Map<String, Object> expect = VectorFiles.asStringMap(doc.get("expect"), "expect");
    if (expect.size() != 1 || !(expect.containsKey("bundle") || expect.containsKey("findings"))) {
      throw new RunnerFailure("expect has exactly one of bundle and findings (VEC-90)");
    }
    CompileResult result = Tierwise.compile(project);
    List<String> problems = expect.containsKey("findings")
        ? compareFindings(expect.get("findings"), result)
        : compareBundle(caseDir, expect.get("bundle"), result);
    if (!problems.isEmpty()) {
      throw new AssertionFailedError(caseDir.getFileName() + ":\n  " + String.join("\n  ", problems));
    }
  }

  private static Path projectDir(Path caseDir, Map<String, Object> doc) {
    Path project;
    if (doc.get("project") instanceof String named) {
      project = caseDir.resolve(named).normalize();
      Path samples = VectorFiles.repositoryRoot().resolve("samples").normalize();
      if (!project.startsWith(samples) || !Files.isDirectory(project)) {
        throw new RunnerFailure("project must name a directory inside samples/ (VEC-91)");
      }
    } else {
      project = caseDir.resolve("project");
      if (!Files.isDirectory(project)) {
        throw new RunnerFailure("the case has no project/ directory (VEC-5)");
      }
    }
    return project;
  }

  private static List<String> compareFindings(Object listed, CompileResult result) {
    if (!(listed instanceof List<?> entries) || entries.isEmpty()) {
      throw new RunnerFailure("expect.findings is a non-empty list (VEC-96)");
    }
    Set<Finding.Key> expected = new LinkedHashSet<>();
    for (Object entry : entries) {
      expected.add(expectedKey(entry));
    }
    Set<Finding.Key> actual = new LinkedHashSet<>();
    result.findings().forEach(finding -> actual.add(finding.key()));
    return diff(expected, actual);
  }

  /**
   * Compares two sets of finding keys (VEC-98).
   *
   * @param expected the keys the case lists
   * @param actual the keys the compiler reported
   * @return the missing and the extra keys; empty when the sets are equal
   */
  static List<String> diff(Set<Finding.Key> expected, Set<Finding.Key> actual) {
    List<String> problems = new ArrayList<>();
    for (Finding.Key key : expected) {
      if (!actual.contains(key)) {
        problems.add("missing finding " + describe(key));
      }
    }
    for (Finding.Key key : actual) {
      if (!expected.contains(key)) {
        problems.add("unexpected finding " + describe(key));
      }
    }
    return problems;
  }

  private static Finding.Key expectedKey(Object entry) {
    Map<String, Object> map = VectorFiles.asStringMap(entry, "a findings entry");
    if (!map.keySet().equals(Set.of("code", "file", "path", "line"))) {
      throw new RunnerFailure("a findings entry has exactly code, file, path and line (VEC-97)");
    }
    if (!(map.get("code") instanceof String code) || !(map.get("file") instanceof String file)
        || !(map.get("path") instanceof List<?> path) || !(map.get("line") instanceof Integer line)) {
      throw new RunnerFailure("a findings entry has a string code and file, a list path and an integer line");
    }
    List<Object> segments = new ArrayList<>();
    for (Object segment : path) {
      if (segment instanceof String) {
        segments.add(segment);
      } else if (segment instanceof Integer || segment instanceof Long) {
        segments.add(((Number) segment).intValue());
      } else {
        throw new RunnerFailure("a path item is a string or a non-negative integer (VEC-104)");
      }
    }
    return new Finding.Key(code, file, segments, line);
  }

  private static List<String> compareBundle(Path caseDir, Object named, CompileResult result) {
    if (!(named instanceof String relative)) {
      throw new RunnerFailure("expect.bundle must be a path (VEC-93)");
    }
    Path bundlesDir = VectorFiles.conformanceDir().resolve("bundles").normalize();
    Path file = caseDir.resolve(relative).normalize();
    if (!file.startsWith(bundlesDir) || file.startsWith(bundlesDir.resolve("invalid"))) {
      throw new RunnerFailure("expect.bundle must name a file inside bundles/ and not inside invalid/ (VEC-93)");
    }
    byte[] expected;
    try {
      expected = Files.readAllBytes(file);
    } catch (IOException e) {
      throw new RunnerFailure("cannot read " + file, e);
    }
    List<String> problems = new ArrayList<>();
    if (!result.findings().isEmpty()) {
      Set<String> codes = new TreeSet<>();
      result.findings().forEach(finding -> codes.add(finding.code()));
      problems.add("a bundle expectation needs no finding of any severity (VEC-94), got " + result.findings().size()
          + " finding(s): " + codes);
    }
    Optional<byte[]> actual = result.bundle();
    if (actual.isEmpty()) {
      problems.add("no bundle was written");
    } else if (!Arrays.equals(expected, actual.get())) {
      problems.add("the bundle differs from " + bundlesDir.relativize(file) + " (" + expected.length
          + " bytes expected, " + actual.get().length + " written)");
    }
    return problems;
  }

  private static String describe(Finding.Key key) {
    return key.code() + " " + key.file() + ":" + key.line() + " " + key.path();
  }
}
