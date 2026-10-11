package io.github.visplabs.tierwise.spring;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Tierwise;
import io.github.visplabs.tierwise.core.TierwiseException;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalBundleSourcesTest {

  @TempDir
  Path dir;

  @Test
  void aFileIsReadWhenNothingIsHeld() throws IOException {
    byte[] bundle = TestBundles.limit(10);
    Path file = Files.write(dir.resolve("bundle.json"), bundle);

    BundleFetch fetched = new FileBundleSource(file).fetch(null);

    assertArrayEquals(bundle, fetched.bytes());
  }

  @Test
  void anUnchangedFileIsNotModifiedOnceABundleIsHeld() throws IOException {
    Path file = Files.write(dir.resolve("bundle.json"), TestBundles.limit(10));
    FileBundleSource source = new FileBundleSource(file);
    String version = source.fetch(null).etag();

    assertTrue(source.fetch(version).isNotModified());
  }

  @Test
  void anUnchangedFileIsReadAgainWhenTheCallerHoldsAnotherBundle() throws IOException {
    byte[] bundle = TestBundles.limit(10);
    Path file = Files.write(dir.resolve("bundle.json"), bundle);
    FileBundleSource source = new FileBundleSource(file);
    source.fetch(null);

    BundleFetch fetched = source.fetch(Tierwise.loadBundle(TestBundles.limit(20)).bundleVersion());

    assertFalse(fetched.isNotModified(), "the caller does not hold this file's bundle, so it must get it");
    assertArrayEquals(bundle, fetched.bytes());
  }

  @Test
  void aFileThatIsNotALoadableBundleIsNeverReportedAsNotModified() throws IOException {
    Path file = Files.write(dir.resolve("bundle.json"), "not a bundle".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    FileBundleSource source = new FileBundleSource(file);
    String held = Tierwise.loadBundle(TestBundles.limit(10)).bundleVersion();

    assertThrows(TierwiseException.class, () -> source.fetch(null));
    assertThrows(TierwiseException.class, () -> source.fetch(held));
    assertThrows(TierwiseException.class, () -> source.fetch(held));
  }

  @Test
  void aReplacedFileIsReadAgain() throws IOException {
    Path file = Files.write(dir.resolve("bundle.json"), TestBundles.limit(10));
    FileBundleSource source = new FileBundleSource(file);
    source.fetch(null);
    byte[] replacement = TestBundles.limit(20);
    Files.write(file, replacement);
    Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5_000));

    BundleFetch fetched = source.fetch("sha256:held");

    assertFalse(fetched.isNotModified());
    assertArrayEquals(replacement, fetched.bytes());
  }

  @Test
  void aMissingFileIsAnIOException() {
    assertThrows(IOException.class, () -> new FileBundleSource(dir.resolve("absent.json")).fetch(null));
  }

  @Test
  void aClasspathResourceIsReadAndThenNotModified() throws IOException {
    byte[] bundle = TestBundles.limit(10);
    Path resources = Files.createDirectories(dir.resolve("resources/snap"));
    Files.write(resources.resolve("bundle.json"), bundle);
    try (URLClassLoader loader = new URLClassLoader(new URL[] {dir.resolve("resources").toUri().toURL()}, null)) {
      ClasspathBundleSource source = new ClasspathBundleSource(loader, "/snap/bundle.json");

      BundleFetch first = source.fetch(null);
      assertArrayEquals(bundle, first.bytes());
      assertTrue(source.fetch(first.etag()).isNotModified());
      assertFalse(source.fetch("sha256:other").isNotModified(), "a caller that holds another bundle gets the resource");
      assertEquals("classpath:snap/bundle.json", source.describe());
    }
  }

  @Test
  void aClasspathResourceThatIsNotALoadableBundleIsNeverReportedAsNotModified() throws IOException {
    Path resources = Files.createDirectories(dir.resolve("resources/snap"));
    Files.write(resources.resolve("bad.json"), "not a bundle".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    try (URLClassLoader loader = new URLClassLoader(new URL[] {dir.resolve("resources").toUri().toURL()}, null)) {
      ClasspathBundleSource source = new ClasspathBundleSource(loader, "snap/bad.json");

      assertThrows(TierwiseException.class, () -> source.fetch(null));
      assertThrows(TierwiseException.class, () -> source.fetch("sha256:held"));
    }
  }

  @Test
  void aMissingClasspathResourceIsAnIOException() {
    assertThrows(IOException.class,
        () -> new ClasspathBundleSource(getClass().getClassLoader(), "no/such/bundle.json").fetch(null));
  }
}
