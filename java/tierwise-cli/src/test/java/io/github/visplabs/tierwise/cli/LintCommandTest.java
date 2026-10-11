package io.github.visplabs.tierwise.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LintCommandTest {

  @TempDir
  Path temp;

  private Path project(String preset) {
    Path project = temp.resolve(preset);
    CliRun.run("init", "--preset", preset, project.toString());
    return project;
  }

  @Test
  void aCleanProjectHasNoLintFindings() {
    CliRun.Result result = CliRun.run("lint", project("recommended").toString());
    assertEquals(0, result.code());
    assertEquals("", result.out());
    assertEquals("lint: no findings\n", result.err());
  }

  @Test
  void lintReportsLintFindingsOnlyAndWarningsDoNotFail() throws IOException {
    Path project = project("recommended");
    CliRun.replace(project.resolve("tierwise.yaml"),
        "      description: Whether uploads are permitted. One false value on the path vetoes it.\n", "");
    CliRun.Result result = CliRun.run("lint", project.toString());
    assertEquals(0, result.code(), result.out());
    assertTrue(result.out().contains(": warn schema/description-required "), result.out());
    assertFalse(result.out().contains(" E_"), result.out());
  }

  @Test
  void aLintErrorExitsWithOne() throws IOException {
    Path project = project("recommended");
    CliRun.addNoOpValue(project);
    CliRun.Result result = CliRun.run("lint", project.toString());
    assertEquals(1, result.code(), result.out());
    assertTrue(result.out().contains(": error values/no-op "), result.out());
  }

  @Test
  void lintDoesNotRunWhenTheProjectHasAnEngineFinding() throws IOException {
    Path project = project("recommended");
    CliRun.replace(project.resolve("tierwise.yaml"), "service.upload.allowed: false", "service.uplaod.allowed: false");
    CliRun.Result result = CliRun.run("lint", project.toString());
    assertEquals(1, result.code());
    assertEquals("", result.out());
    assertTrue(result.err().contains("lint did not run"), result.err());
  }

  @Test
  void jsonFormatSaysWhetherLintRan() throws IOException {
    Path project = project("recommended");
    assertEquals("{\"command\":\"lint\",\"ok\":true,\"lintRan\":true,\"counts\":{\"error\":0,\"warn\":0,\"info\":0},\"findings\":[]}\n",
        CliRun.run("lint", "--format", "json", project.toString()).out());
    CliRun.replace(project.resolve("tierwise.yaml"), "service.upload.allowed: false", "service.uplaod.allowed: false");
    assertEquals("{\"command\":\"lint\",\"ok\":false,\"lintRan\":false,\"engineFindings\":1,\"findings\":[]}\n",
        CliRun.run("lint", "--format", "json", project.toString()).out());
  }
}
