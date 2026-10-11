package io.github.visplabs.tierwise.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidateCommandTest {

  @TempDir
  Path temp;

  private Path brokenProject() throws IOException {
    Path project = temp.resolve("broken");
    CliRun.run("init", "--preset", "recommended", project.toString());
    CliRun.replace(project.resolve("tierwise.yaml"), "service.upload.allowed: false", "service.uplaod.allowed: false");
    return project;
  }

  @ParameterizedTest
  @ValueSource(strings = {"commerce", "retail-pos", "saas-entitlements"})
  void theSamplesValidateWithZeroFindings(String sample) {
    CliRun.Result result = CliRun.run("validate", CliRun.sample(sample).toString());
    assertEquals(0, result.code(), result.out());
    assertEquals("", result.out());
    assertEquals("validate: no findings\n", result.err());
  }

  @Test
  void anEngineErrorPrintsFileLineSeverityCodeMessageAndSuggestionAndExitsWithOne() throws IOException {
    CliRun.Result result = CliRun.run("validate", brokenProject().toString());
    assertEquals(1, result.code());
    String line = result.out().strip();
    assertTrue(line.startsWith("tierwise.yaml:"), line);
    assertTrue(line.matches("tierwise\\.yaml:\\d+: error E_UNKNOWN_KEY .+ \\(suggestion: service\\.upload\\.allowed\\)"), line);
    assertEquals("validate: 1 error, 0 warnings, 0 infos\n", result.err());
  }

  @Test
  void aLintWarningDoesNotFailTheValidation() throws IOException {
    Path project = temp.resolve("warn");
    CliRun.run("init", "--preset", "recommended", project.toString());
    CliRun.replace(project.resolve("tierwise.yaml"),
        "      description: Whether uploads are permitted. One false value on the path vetoes it.\n", "");
    CliRun.Result result = CliRun.run("validate", project.toString());
    assertEquals(0, result.code(), result.out());
    assertTrue(result.out().contains(": warn schema/description-required "), result.out());
  }

  @Test
  void aLintErrorFailsTheValidation() throws IOException {
    Path project = temp.resolve("lint-error");
    CliRun.run("init", "--preset", "recommended", project.toString());
    CliRun.addNoOpValue(project);
    CliRun.Result result = CliRun.run("validate", project.toString());
    assertEquals(1, result.code(), result.out());
    assertTrue(result.out().contains(": error "), result.out());
  }

  @Test
  void jsonFormatWritesOneDocumentWithEveryFindingField() throws IOException {
    CliRun.Result result = CliRun.run("validate", "--format", "json", brokenProject().toString());
    assertEquals(1, result.code());
    assertEquals("", result.err());
    String json = result.out().strip();
    assertTrue(json.startsWith("{\"command\":\"validate\",\"ok\":false,\"counts\":{\"error\":1,\"warn\":0,\"info\":0},"), json);
    assertTrue(json.contains("\"code\":\"E_UNKNOWN_KEY\",\"severity\":\"error\",\"file\":\"tierwise.yaml\",\"path\":[\"values\","), json);
    assertTrue(json.contains("\"suggestion\":\"service.upload.allowed\"}]}"), json);
    assertEquals(1, result.out().lines().count());
  }

  @Test
  void jsonFormatOfAValidProjectIsOk() {
    CliRun.Result result = CliRun.run("validate", "--format", "JSON", CliRun.sample("saas-entitlements").toString());
    assertEquals(0, result.code());
    assertEquals("{\"command\":\"validate\",\"ok\":true,\"counts\":{\"error\":0,\"warn\":0,\"info\":0},\"findings\":[]}\n",
        result.out());
  }

  @Test
  void aDirectoryWithoutAManifestReportsEManifest() {
    CliRun.Result result = CliRun.run("validate", temp.toString());
    assertEquals(1, result.code());
    assertTrue(result.out().startsWith("tierwise.yaml:1: error E_MANIFEST "), result.out());
  }

  @Test
  void aMissingDirectoryIsAUsageError() {
    CliRun.Result result = CliRun.run("validate", temp.resolve("absent").toString());
    assertEquals(2, result.code());
    assertTrue(result.err().startsWith("tierwise: not a directory: "), result.err());
  }

  @Test
  void anUnknownFormatIsAUsageError() {
    CliRun.Result result = CliRun.run("validate", "--format", "xml", temp.toString());
    assertEquals(2, result.code());
  }
}
