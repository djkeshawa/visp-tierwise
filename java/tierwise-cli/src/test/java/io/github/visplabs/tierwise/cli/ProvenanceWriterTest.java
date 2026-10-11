package io.github.visplabs.tierwise.cli;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Tierwise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The provenance writer of the CLI: it never replaces or overwrites an existing sidecar (BUNDLE-147), and each
 * record is stored in a sidecar of its own, even when several records carry one {@code bundleVersion}
 * (BUNDLE-148).
 */
class ProvenanceWriterTest {

  private static final String VERSION = "sha256:" + "1".repeat(64);
  private static final String COMMIT = "0123456789abcdef0123456789abcdef01234567";
  private static final Clock FIRST = Clock.fixed(Instant.parse("2026-01-02T03:04:05Z"), ZoneOffset.UTC);
  private static final Clock LATER = Clock.fixed(Instant.parse("2026-06-07T08:09:10Z"), ZoneOffset.UTC);

  @TempDir
  Path temp;

  @Test
  void BUNDLE_147_writingASidecarWhosePathExistsFailsAndLeavesTheExistingFileUnchanged() throws IOException {
    Path bundle = temp.resolve("bundle.json");
    Path sidecar = Provenance.sidecarFor(bundle);
    byte[] existing = {(byte) 0xff, 0x00, 'o', 'l', 'd', '\n'};
    Files.write(sidecar, existing);
    UsageException failure = assertThrows(UsageException.class,
        () -> Provenance.write(bundle, VERSION, LATER, Optional.of(COMMIT)));
    assertTrue(failure.getMessage().contains("never replaced"), failure.getMessage());
    assertArrayEquals(existing, Files.readAllBytes(sidecar));
  }

  @Test
  void BUNDLE_147_aSidecarThatTheWriterWroteIsNotOverwrittenByASecondWrite() throws IOException {
    Path bundle = temp.resolve("b.json");
    Path sidecar = Provenance.write(bundle, VERSION, FIRST, Optional.empty());
    String first = Files.readString(sidecar);
    assertThrows(UsageException.class, () -> Provenance.write(bundle, VERSION, LATER, Optional.of(COMMIT)));
    assertEquals(first, Files.readString(sidecar));
    assertEquals(Provenance.json(VERSION, FIRST.instant(), Optional.empty()), first);
  }

  @Test
  void BUNDLE_148_aSecondRecordWithTheSameBundleVersionIsStoredInASidecarOfItsOwn() throws IOException {
    Path firstBundle = temp.resolve("release.json");
    Path secondBundle = temp.resolve("release.rebuilt.json");
    Path first = Provenance.write(firstBundle, VERSION, FIRST, Optional.empty());
    String firstText = Files.readString(first);
    Path second = Provenance.write(secondBundle, VERSION, LATER, Optional.of(COMMIT));
    assertNotEquals(first, second);
    assertEquals(firstText, Files.readString(first), "the first record is not replaced");
    assertEquals(Provenance.json(VERSION, LATER.instant(), Optional.of(COMMIT)), Files.readString(second));
    assertTrue(Files.readString(first).contains("\"bundleVersion\": \"" + VERSION + "\""));
    assertTrue(Files.readString(second).contains("\"bundleVersion\": \"" + VERSION + "\""));
  }

  @Test
  void BUNDLE_148_compilingOneProjectTwiceKeepsBothRecordsOfTheSameBundleVersion() throws IOException {
    Path project = CliRun.sample("saas-entitlements");
    Path first = temp.resolve("first.json");
    Path second = temp.resolve("second.json");
    assertEquals(0, CliRun.run("compile", project.toString(), "-o", first.toString(), "--provenance").code());
    String firstRecord = Files.readString(temp.resolve("first.provenance.json"));
    CliRun.Result again = CliRun.run(Map.of("GITHUB_SHA", COMMIT), "compile", project.toString(), "-o", second.toString(),
        "--provenance");
    assertEquals(0, again.code(), again.err());
    String version = Tierwise.loadBundle(Files.readAllBytes(first)).bundleVersion();
    assertEquals(version, Tierwise.loadBundle(Files.readAllBytes(second)).bundleVersion());
    assertEquals(firstRecord, Files.readString(temp.resolve("first.provenance.json")), "the first record is not replaced");
    assertTrue(Files.readString(temp.resolve("second.provenance.json")).contains("\"gitCommit\": \"" + COMMIT + "\""));
    try (Stream<Path> files = Files.list(temp)) {
      assertEquals(2, files.filter(path -> path.getFileName().toString().endsWith(".provenance.json")).count());
    }
    // A third compile to the first output stops before it writes anything, so the first record stays.
    CliRun.Result third = CliRun.run("compile", project.toString(), "-o", first.toString(), "--provenance");
    assertEquals(2, third.code());
    assertEquals(firstRecord, Files.readString(temp.resolve("first.provenance.json")));
  }
}
