package io.github.visplabs.tierwise.spring;

import io.github.visplabs.tierwise.core.Tierwise;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * Reads a bundle from a file.
 *
 * <p>The file is read again only when its size or modification time differs from the last read and the
 * caller holds the bundle that read produced, so polling an unchanged file costs one stat call. Replacing
 * the file by writing a new one and renaming it over the old one is picked up on the next poll.
 *
 * <p>The source loads each file it reads to learn its {@code bundleVersion}. It reports "not modified" only
 * for a file that loaded and that the caller holds. A file that fails to load, or whose bundle the caller
 * could not use (for example because it needs a provider that is not registered), is therefore read and
 * reported again on every poll instead of being mistaken for the bundle in use.
 */
public final class FileBundleSource implements BundleSource {

  private final Path path;
  private String lastStamp;
  private String lastVersion;

  /**
   * Creates a source.
   *
   * @param path the bundle file
   */
  public FileBundleSource(Path path) {
    this.path = Objects.requireNonNull(path, "path");
  }

  @Override
  public String describe() {
    return "file:" + path;
  }

  /**
   * {@inheritDoc}
   *
   * @throws io.github.visplabs.tierwise.core.TierwiseException when the file is not a loadable bundle
   */
  @Override
  public synchronized BundleFetch fetch(String currentVersion) throws IOException {
    BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
    String stamp = attributes.size() + "@" + attributes.lastModifiedTime().toMillis();
    if (currentVersion != null && stamp.equals(lastStamp) && currentVersion.equals(lastVersion)) {
      return BundleFetch.notModified();
    }
    byte[] bytes = Files.readAllBytes(path);
    String version = Tierwise.loadBundle(bytes).bundleVersion();
    lastStamp = stamp;
    lastVersion = version;
    return BundleFetch.of(bytes, version);
  }
}
