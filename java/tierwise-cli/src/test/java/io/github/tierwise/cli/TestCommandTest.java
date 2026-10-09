package io.github.tierwise.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TestCommandTest {

  @TempDir
  Path temp;

  private Path commerceCopy() throws IOException {
    Path copy = temp.resolve("commerce");
    CliRun.copyTree(CliRun.sample("commerce"), copy);
    return copy;
  }

  @Test
  void everyExampleOfASampleIsReportedAsPassed() {
    CliRun.Result result = CliRun.run("test", CliRun.sample("commerce").toString());
    assertEquals(0, result.code(), result.out());
    long passed = result.out().lines().filter(line -> line.startsWith("pass rules/")).count();
    assertEquals(12, passed, result.out());
    assertEquals(0, result.out().lines().filter(line -> line.startsWith("FAIL ")).count());
    assertTrue(result.out().contains(
        "pass rules/checkout.invoice_release.yaml:41: A pending order holds the invoice under the root binding\n"), result.out());
    assertTrue(result.err().contains("test: 12 examples, 12 passed, 0 failed\n"), result.err());
  }

  @Test
  void aFailingExampleIsReportedAsFailedAndTheOthersAsPassed() throws IOException {
    Path project = commerceCopy();
    CliRun.replace(project.resolve("rules/checkout.invoice_release.yaml"),
        "    facts: { order.payment_state: [PAID, PENDING] }\n    expect: HOLD\n",
        "    facts: { order.payment_state: [PAID, PENDING] }\n    expect: RELEASE\n");
    CliRun.Result result = CliRun.run("test", project.toString());
    assertEquals(1, result.code(), result.out());
    assertTrue(result.out().contains(
        "FAIL rules/checkout.invoice_release.yaml:41: A pending order holds the invoice under the root binding\n"), result.out());
    assertEquals(1, result.out().lines().filter(line -> line.startsWith("FAIL ")).count(), result.out());
    assertEquals(11, result.out().lines().filter(line -> line.startsWith("pass ")).count(), result.out());
    assertTrue(result.out().contains(": error E_EXAMPLE_FAILED "), result.out());
    assertTrue(result.err().contains("test: 12 examples, 11 passed, 1 failed\n"), result.err());
  }

  @Test
  void examplesAreNotRunWhenAnotherEngineFindingExists() throws IOException {
    Path project = commerceCopy();
    CliRun.replace(project.resolve("values/global.yaml"), "checkout.order.allowed_max_lines", "checkout.order.allowed_max_line");
    CliRun.Result result = CliRun.run("test", project.toString());
    assertEquals(1, result.code(), result.out());
    assertEquals(0, result.out().lines().filter(line -> line.startsWith("pass ") || line.startsWith("FAIL ")).count());
    assertTrue(result.err().contains("examples were not run"), result.err());
  }

  @Test
  void aProjectWithoutExamplesPasses() {
    CliRun.Result result = CliRun.run("test", CliRun.sample("saas-entitlements").toString());
    assertEquals(0, result.code());
    assertEquals("", result.out());
    assertTrue(result.err().contains("test: 0 examples, 0 passed, 0 failed\n"), result.err());
  }

  @Test
  void jsonFormatListsEachExampleWithItsResult() throws IOException {
    Path project = commerceCopy();
    CliRun.replace(project.resolve("rules/checkout.invoice_release.yaml"),
        "    facts: { order.payment_state: [PAID, PENDING] }\n    expect: HOLD\n",
        "    facts: { order.payment_state: [PAID, PENDING] }\n    expect: RELEASE\n");
    CliRun.Result result = CliRun.run("test", "--format", "json", project.toString());
    assertEquals(1, result.code());
    String json = result.out().strip();
    assertTrue(json.startsWith("{\"command\":\"test\",\"ok\":false,\"examplesRan\":true,"), json);
    assertTrue(json.contains("{\"file\":\"rules/checkout.invoice_release.yaml\",\"path\":[\"examples\",0],\"line\":41,"
        + "\"name\":\"A pending order holds the invoice under the root binding\",\"result\":\"fail\"}"), json);
    assertTrue(json.contains("\"result\":\"pass\"}"), json);
    assertEquals(1, result.out().lines().count());
  }

  @Test
  void examplesInTheManifestAreReported() throws IOException {
    Path project = temp.resolve("inline");
    java.nio.file.Files.createDirectories(project);
    java.nio.file.Files.writeString(project.resolve("tierwise.yaml"), """
        specVersion: "0.1"
        schema:
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
        rules:
          - feature: gate.open
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
        """);
    CliRun.Result result = CliRun.run("test", project.toString());
    assertEquals(1, result.code(), result.out());
    assertTrue(result.out().contains("pass tierwise.yaml:"), result.out());
    assertTrue(result.out().contains(": ready thing\n"), result.out());
    assertTrue(result.out().contains("FAIL tierwise.yaml:"), result.out());
    assertTrue(result.out().contains(": not ready thing\n"), result.out());
  }
}
