package io.github.tierwise.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Tierwise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompileCommandTest {

  private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";

  @TempDir
  Path temp;

  @Test
  void compileWritesTheCanonicalBundle() throws IOException {
    Path project = CliRun.sample("commerce");
    Path bundle = temp.resolve("out").resolve("bundle.json");
    CliRun.Result result = CliRun.run("compile", project.toString(), "-o", bundle.toString());
    assertEquals(0, result.code(), result.err());
    byte[] expected = Tierwise.compile(project).bundle().orElseThrow();
    assertArrayEquals(expected, Files.readAllBytes(bundle));
    String version = Tierwise.loadBundle(expected).bundleVersion();
    assertEquals("wrote " + bundle + " (" + expected.length + " bytes, " + version + ")\n", result.out());
    assertFalse(Files.exists(temp.resolve("out").resolve("bundle.provenance.json")));
  }

  @Test
  void compileTwiceGivesTheSameBytes() throws IOException {
    Path first = temp.resolve("first.json");
    Path second = temp.resolve("second.json");
    CliRun.run("compile", CliRun.sample("retail-pos").toString(), "-o", first.toString());
    CliRun.run("compile", CliRun.sample("retail-pos").toString(), "--output", second.toString());
    assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
  }

  @Test
  void aValidationFailureWritesNothing() throws IOException {
    Path project = temp.resolve("project");
    CliRun.copyTree(CliRun.sample("saas-entitlements"), project);
    CliRun.replace(project.resolve("tierwise.yaml"), "usage.max_projects: 20", "usage.max_project: 20");
    Path bundle = temp.resolve("bundle.json");
    CliRun.Result result = CliRun.run("compile", project.toString(), "-o", bundle.toString(), "--provenance");
    assertEquals(1, result.code());
    assertTrue(result.out().contains(": error E_UNKNOWN_KEY "), result.out());
    assertTrue(result.err().contains("no bundle written"), result.err());
    assertFalse(Files.exists(bundle));
    assertFalse(Files.exists(temp.resolve("bundle.provenance.json")));
  }

  @Test
  void provenanceWritesTheSidecarBesideTheBundle() throws IOException {
    Path project = CliRun.sample("saas-entitlements");
    Path bundle = temp.resolve("my-bundle.json");
    CliRun.Result result = CliRun.run(Map.of("GITHUB_SHA", COMMIT), "compile", project.toString(), "-o", bundle.toString(),
        "--provenance");
    assertEquals(0, result.code(), result.err());
    String version = Tierwise.loadBundle(Files.readAllBytes(bundle)).bundleVersion();
    Path sidecar = temp.resolve("my-bundle.provenance.json");
    assertEquals("{\n  \"bundleVersion\": \"" + version + "\",\n  \"createdAt\": \"2026-03-04T05:06:07Z\",\n"
        + "  \"gitCommit\": \"" + COMMIT + "\"\n}\n", Files.readString(sidecar));
    assertTrue(result.out().contains("wrote " + sidecar + "\n"), result.out());
  }

  @Test
  void theSidecarDoesNotChangeTheBundleBytes() throws IOException {
    Path project = CliRun.sample("saas-entitlements");
    Path plain = temp.resolve("plain.json");
    Path withProvenance = temp.resolve("with.json");
    CliRun.run("compile", project.toString(), "-o", plain.toString());
    CliRun.run(Map.of("GITHUB_SHA", COMMIT), "compile", project.toString(), "-o", withProvenance.toString(), "--provenance");
    assertArrayEquals(Files.readAllBytes(plain), Files.readAllBytes(withProvenance));
  }

  @Test
  void theCommitIsLowerCasedFromGithubSha() throws IOException {
    Path bundle = temp.resolve("b.json");
    CliRun.run(Map.of("GITHUB_SHA", COMMIT.toUpperCase(java.util.Locale.ROOT)), "compile",
        CliRun.sample("saas-entitlements").toString(), "-o", bundle.toString(), "--provenance");
    assertTrue(Files.readString(temp.resolve("b.provenance.json")).contains("\"gitCommit\": \"" + COMMIT + "\""));
  }

  @Test
  void aSidecarIsNeverReplaced() throws IOException {
    Path project = CliRun.sample("saas-entitlements");
    Path bundle = temp.resolve("b.json");
    Path sidecar = temp.resolve("b.provenance.json");
    Files.writeString(sidecar, "existing");
    CliRun.Result result = CliRun.run("compile", project.toString(), "-o", bundle.toString(), "--provenance");
    assertEquals(2, result.code());
    assertTrue(result.err().contains("never replaced"), result.err());
    assertEquals("existing", Files.readString(sidecar));
    assertFalse(Files.exists(bundle), "nothing is written when the sidecar cannot be");
  }

  @Test
  void aCommitThatIsNotAFullIdentifierIsLeftOut() {
    assertEquals(java.util.Optional.empty(), Provenance.gitCommit(Map.of("GITHUB_SHA", "abc123")::get, temp));
    assertEquals(java.util.Optional.of(COMMIT), Provenance.gitCommit(Map.of("GITHUB_SHA", COMMIT)::get, temp));
  }

  @Test
  void theSidecarNameDropsTheJsonExtension() {
    assertEquals(Path.of("x", "bundle.provenance.json"), Provenance.sidecarFor(Path.of("x", "bundle.json")));
    assertEquals(Path.of("x", "bundle.v1.provenance.json"), Provenance.sidecarFor(Path.of("x", "bundle.v1.json")));
    assertEquals(Path.of("x", "bundle.provenance.json"), Provenance.sidecarFor(Path.of("x", "bundle")));
  }

  @Test
  void createdAtHasWholeSecondsAndNoFraction() {
    String json = Provenance.json("sha256:" + "0".repeat(64), CliRun.NOW, java.util.Optional.empty());
    assertEquals("{\n  \"bundleVersion\": \"sha256:" + "0".repeat(64) + "\",\n  \"createdAt\": \"2026-03-04T05:06:07Z\"\n}\n", json);
  }
}
