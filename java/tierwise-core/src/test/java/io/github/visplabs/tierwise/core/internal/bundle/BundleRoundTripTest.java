package io.github.visplabs.tierwise.core.internal.bundle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.internal.model.LoadedModel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every valid bundle of the conformance suite loads, and writing the loaded model gives the same bytes (DET-1,
 * BUNDLE-2). The files under {@code bundles/invalid/} are excluded, because they are not valid bundles.
 */
class BundleRoundTripTest {

  @Test
  void everyValidBundleReadsAndWritesBackToTheSameBytes() throws IOException {
    Path dir = BundleFixtures.specBundles();
    List<Path> files;
    try (Stream<Path> listing = Files.list(dir)) {
      files = listing.filter(path -> path.getFileName().toString().endsWith(".json")).sorted().toList();
    }
    assertTrue(files.stream().anyMatch(path -> path.getFileName().toString().equals("commerce.json")), dir.toString());
    List<String> failures = new ArrayList<>();
    for (Path file : files) {
      byte[] bytes = Files.readAllBytes(file);
      LoadedModel loaded = BundleReader.read(bytes);
      BundleWriter.Written written = BundleWriter.write(loaded.model());
      if (!Arrays.equals(bytes, written.bytes()) || !written.bundleVersion().equals(loaded.bundleVersion())) {
        failures.add(file.getFileName().toString());
      }
    }
    assertEquals(List.of(), failures);
    assertFalse(files.isEmpty());
  }
}
