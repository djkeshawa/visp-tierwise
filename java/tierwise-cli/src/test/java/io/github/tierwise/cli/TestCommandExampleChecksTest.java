package io.github.tierwise.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.CompileOptions;
import io.github.tierwise.core.Finding;
import io.github.tierwise.core.Tierwise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * POL-49: the {@code test} command bases its example results on the same example checks as validation (POL-47),
 * identifies each example by its name (POL-29), and reports a failing example with its code (POL-42).
 */
class TestCommandExampleChecksTest {

  private static final String SCHEMA = """
      decision_hierarchy:
        levels: [base]
      entity_hierarchy:
        thing: {}
      facts:
        thing.ready:
          type: boolean
      features:
        gate.open:
          decided_per: thing
          returns: { enum: [YES, NO] }
          hit_policy: FIRST
          if_fact_missing: NO
          binding_settable_at: [base]
      """;

  private static final String RULES = """
      feature: gate.open
      policies:
        plain:
          default: NO
          rules:
            - { id: ready, name: ready, when: { thing.ready: true }, then: YES }
      bindings:
        base: { policy: plain }
      examples:
        - { name: ready thing, context: {}, facts: { thing.ready: true }, expect: YES }
        - { name: not ready thing, context: {}, facts: { thing.ready: false }, expect: YES }
        - { name: missing fact, context: {}, facts: {}, expect: NO }
        - { name: also wrong, context: {}, facts: { thing.ready: true }, expect: NO }
      """;

  @TempDir
  Path project;

  private void write(String rules) throws IOException {
    Files.writeString(project.resolve("tierwise.yaml"),
        "specVersion: \"0.1\"\nsources:\n  schema: [schema.yaml]\n  rules: ['rules/*.yaml']\n");
    Files.writeString(project.resolve("schema.yaml"), SCHEMA);
    Files.createDirectories(project.resolve("rules"));
    Files.writeString(project.resolve("rules/gate.open.yaml"), rules);
  }

  private static String findingsMember(String json) {
    int start = json.indexOf("\"findings\":");
    assertTrue(start >= 0, json);
    return json.substring(start).strip();
  }

  @Test
  void POL_49_theTestCommandReportsTheSameFindingsAsValidation() throws IOException {
    write(RULES);
    CliRun.Result test = CliRun.run("test", "--format", "json", project.toString());
    CliRun.Result validate = CliRun.run("validate", "--format", "json", project.toString());
    assertEquals(validate.code(), test.code());
    assertEquals(1, test.code(), test.out());
    assertEquals(findingsMember(validate.out()), findingsMember(test.out()));
  }

  @Test
  void POL_49_theFailedExamplesAreExactlyThoseThatValidationReportsAsEExampleFailed() throws IOException {
    write(RULES);
    Set<String> failedByValidation = new TreeSet<>();
    for (Finding finding : Tierwise.compile(project, CompileOptions.defaults()).findings()) {
      assertEquals(Code.E_EXAMPLE_FAILED, finding.code(), finding.toString());
      assertEquals("rules/gate.open.yaml", finding.file());
      failedByValidation.add(String.valueOf(finding.path().subList(0, 2)));
    }
    assertEquals(Set.of("[examples, 1]", "[examples, 3]"), failedByValidation);
    String json = CliRun.run("test", "--format", "json", project.toString()).out();
    Set<String> failedByTest = new TreeSet<>();
    for (int i = 0; i < 4; i++) {
      String path = "\"path\":[\"examples\"," + i + "]";
      assertTrue(json.contains(path), json);
      String rest = json.substring(json.indexOf(path));
      String result = rest.substring(rest.indexOf("\"result\":\"") + 10, rest.indexOf("\"}"));
      if (result.equals("fail")) {
        failedByTest.add("[examples, " + i + "]");
      } else {
        assertEquals("pass", result);
      }
    }
    assertEquals(failedByValidation, failedByTest);
  }

  @Test
  void POL_49_eachExampleIsIdentifiedByItsNameAndAFailureCarriesItsCode() throws IOException {
    write(RULES);
    CliRun.Result result = CliRun.run("test", project.toString());
    List<String> lines = result.out().lines().toList();
    assertTrue(lines.contains("pass rules/gate.open.yaml:10: ready thing"), result.out());
    assertTrue(lines.contains("FAIL rules/gate.open.yaml:11: not ready thing"), result.out());
    assertTrue(lines.contains("pass rules/gate.open.yaml:12: missing fact"), result.out());
    assertTrue(lines.contains("FAIL rules/gate.open.yaml:13: also wrong"), result.out());
    assertEquals(2, lines.stream().filter(line -> line.contains(" E_EXAMPLE_FAILED ")).count(), result.out());
    assertTrue(lines.stream().anyMatch(line -> line.startsWith("rules/gate.open.yaml:11:")
        && line.contains("error E_EXAMPLE_FAILED")), result.out());
    assertTrue(lines.stream().anyMatch(line -> line.startsWith("rules/gate.open.yaml:13:")
        && line.contains("error E_EXAMPLE_FAILED")), result.out());
    assertTrue(result.err().contains("test: 4 examples, 2 passed, 2 failed\n"), result.err());
  }

  @Test
  void POL_49_whenValidationPassesEveryExampleIsReportedAsPassed() throws IOException {
    write(RULES.replace("facts: { thing.ready: false }, expect: YES", "facts: { thing.ready: false }, expect: NO")
        .replace("facts: { thing.ready: true }, expect: NO", "facts: { thing.ready: true }, expect: YES"));
    assertEquals(List.of(), Tierwise.compile(project, CompileOptions.defaults()).findings());
    CliRun.Result result = CliRun.run("test", project.toString());
    assertEquals(0, result.code(), result.out() + result.err());
    assertEquals(4, result.out().lines().filter(line -> line.startsWith("pass ")).count(), result.out());
    assertEquals(0, CliRun.run("validate", project.toString()).code());
  }
}
