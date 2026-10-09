package io.github.tierwise.server.store;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class FilesystemBundleStoreTest {

  private static final String V1 = "sha256:" + "1".repeat(64);
  private static final String V2 = "sha256:" + "2".repeat(64);

  @TempDir
  Path root;
  private FilesystemBundleStore store;

  @BeforeEach
  void open() {
    store = new FilesystemBundleStore(root);
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  private static PublicationRecord record(String version, String createdAt) {
    return new PublicationRecord(version, createdAt, Optional.empty(), Optional.empty());
  }

  @Test
  void aBundleIsStoredOnceAndReadBackByteForByte() {
    byte[] original = bytes("{\"bundleVersion\":\"x\"}");
    assertTrue(store.putBundle(V1, original));
    assertArrayEquals(original, store.getBundle(V1).orElseThrow());
  }

  @Test
  void bytesThatAreNotUtf8SurviveUnchanged() {
    byte[] binary = {0, 1, (byte) 0xff, (byte) 0xfe, 10, 13};
    store.putBundle(V1, binary);
    assertArrayEquals(binary, store.getBundle(V1).orElseThrow());
  }

  @Test
  void anUnknownVersionIsEmpty() {
    assertEquals(Optional.empty(), store.getBundle(V1));
  }

  @Test
  void storingTheSameBytesAgainChangesNothing() {
    store.putBundle(V1, bytes("a"));
    assertFalse(store.putBundle(V1, bytes("a")));
    assertArrayEquals(bytes("a"), store.getBundle(V1).orElseThrow());
  }

  @Test
  void storingDifferentBytesFailsAndTheStoredBytesStay() {
    store.putBundle(V1, bytes("a"));
    assertThrows(BundleConflictException.class, () -> store.putBundle(V1, bytes("b")));
    assertArrayEquals(bytes("a"), store.getBundle(V1).orElseThrow());
  }

  @Test
  void concurrentPutsOfOneVersionStoreItExactlyOnce() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<Boolean>> calls = new ArrayList<>();
      for (int i = 0; i < 16; i++) {
        calls.add(() -> store.putBundle(V1, bytes("same bytes")));
      }
      int stored = 0;
      for (Future<Boolean> result : pool.invokeAll(calls)) {
        if (result.get()) {
          stored++;
        }
      }
      assertEquals(1, stored);
      assertArrayEquals(bytes("same bytes"), store.getBundle(V1).orElseThrow());
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void concurrentPutsOfDifferentBytesLetExactlyOneWin() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Callable<String>> calls = new ArrayList<>();
      for (int i = 0; i < 8; i++) {
        String body = "body-" + i;
        calls.add(() -> {
          try {
            return store.putBundle(V1, bytes(body)) ? "stored" : "same";
          } catch (BundleConflictException e) {
            return "conflict";
          }
        });
      }
      long stored = pool.invokeAll(calls).stream().map(result -> {
        try {
          return result.get();
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      }).filter("stored"::equals).count();
      assertEquals(1, stored);
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  void noTemporaryFileIsLeftBehind() throws IOException {
    store.putBundle(V1, bytes("a"));
    store.putBundle(V1, bytes("a"));
    try {
      store.putBundle(V1, bytes("b"));
    } catch (BundleConflictException expected) {
      // The conflicting write leaves nothing in tmp/.
    }
    store.addRecord(record(V1, "2026-10-09T08:00:00Z"));
    store.setLatest(V1);
    try (Stream<Path> files = Files.list(root.resolve("tmp"))) {
      assertEquals(List.of(), files.toList());
    }
  }

  @Test
  void recordsAreKeptInTheOrderTheyWereAddedAndNeverReplaced() {
    PublicationRecord first = new PublicationRecord(V1, "2026-10-09T08:00:00Z", Optional.of("a".repeat(40)),
        Optional.of("ci-1"));
    PublicationRecord second = record(V1, "2026-10-09T09:00:00Z");
    PublicationRecord same = record(V1, "2026-10-09T09:00:00Z");
    store.addRecord(first);
    store.addRecord(second);
    store.addRecord(same);
    assertEquals(List.of(first, second, same), store.records(V1));
    assertEquals(List.of(), store.records(V2));
  }

  @Test
  void recordsAddedWithinOneMillisecondKeepTheirOrder() {
    FilesystemBundleStore frozen = new FilesystemBundleStore(root.resolve("frozen"),
        Clock.fixed(Instant.parse("2026-10-09T08:00:00Z"), ZoneOffset.UTC));
    List<PublicationRecord> added = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
      PublicationRecord record = new PublicationRecord(V1, "2026-10-09T08:00:00Z", Optional.empty(),
          Optional.of("ci-" + i));
      frozen.addRecord(record);
      added.add(record);
    }
    assertEquals(added, frozen.records(V1));
  }

  @Test
  void allRecordsSpanEveryVersion() {
    store.addRecord(record(V1, "2026-10-09T08:00:00Z"));
    store.addRecord(record(V2, "2026-10-09T09:00:00Z"));
    store.addRecord(record(V1, "2026-10-09T10:00:00Z"));
    assertEquals(3, store.allRecords().size());
  }

  @Test
  void anEmptyStoreHasNoRecords() {
    assertEquals(List.of(), store.allRecords());
  }

  @Test
  void theLatestPointerMovesAtomically() {
    assertEquals(Optional.empty(), store.latest());
    store.setLatest(V1);
    assertEquals(Optional.of(V1), store.latest());
    store.setLatest(V2);
    assertEquals(Optional.of(V2), store.latest());
    store.setLatest(V1);
    assertEquals(Optional.of(V1), store.latest());
  }

  @Test
  void theLatestPointerSurvivesReopeningTheStore() {
    store.putBundle(V1, bytes("a"));
    store.addRecord(record(V1, "2026-10-09T08:00:00Z"));
    store.setLatest(V1);
    FilesystemBundleStore reopened = new FilesystemBundleStore(root);
    assertEquals(Optional.of(V1), reopened.latest());
    assertArrayEquals(bytes("a"), reopened.getBundle(V1).orElseThrow());
    assertEquals(1, reopened.records(V1).size());
  }

  @Test
  void aDamagedPointerFileIsReportedAsAStoreFailure() throws IOException {
    Files.writeString(root.resolve("latest"), "not a version");
    assertThrows(BundleStoreException.class, store::latest);
  }

  @Test
  void aDamagedRecordFileIsReportedAsAStoreFailure() throws IOException {
    store.addRecord(record(V1, "2026-10-09T08:00:00Z"));
    Path directory = root.resolve("records").resolve("1".repeat(64));
    try (Stream<Path> files = Files.list(directory)) {
      Files.writeString(files.findFirst().orElseThrow(), "{broken");
    }
    assertThrows(BundleStoreException.class, () -> store.records(V1));
  }

  static Stream<String> notVersions() {
    return Stream.of("", "latest", "../etc/passwd", "sha256:../../x", "sha256:abc", "SHA256:" + "1".repeat(64),
        "sha256:" + "G".repeat(64), "sha256:" + "1".repeat(65));
  }

  @ParameterizedTest
  @MethodSource("notVersions")
  void aVersionThatIsNotABundleVersionNeverReachesTheFilesystem(String version) {
    assertThrows(IllegalArgumentException.class, () -> store.putBundle(version, bytes("a")));
    assertThrows(IllegalArgumentException.class, () -> store.getBundle(version));
    assertThrows(IllegalArgumentException.class, () -> store.records(version));
    assertThrows(IllegalArgumentException.class, () -> store.setLatest(version));
  }

  @Test
  void theStoreCreatesItsDirectories(@TempDir Path other) {
    Path nested = other.resolve("a").resolve("b");
    new FilesystemBundleStore(nested);
    assertTrue(Files.isDirectory(nested.resolve("bundles")));
    assertTrue(Files.isDirectory(nested.resolve("records")));
  }

  @Test
  void twoStoresOnOneDirectoryShareTheirData() {
    FilesystemBundleStore second = new FilesystemBundleStore(root);
    store.putBundle(V1, bytes("a"));
    store.setLatest(V1);
    assertEquals(Optional.of(V1), second.latest());
    assertTrue(second.getBundle(V1).isPresent());
    second.addRecord(record(V1, "2026-10-09T08:00:00Z"));
    assertEquals(1, store.records(V1).size());
  }
}
