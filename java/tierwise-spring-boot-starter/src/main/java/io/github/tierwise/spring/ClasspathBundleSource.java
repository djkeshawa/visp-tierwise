package io.github.tierwise.spring;

import io.github.tierwise.core.Tierwise;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Reads a bundle from a classpath resource, such as a snapshot packaged in the application.
 *
 * <p>A resource on the classpath does not change while the application runs, so once the caller holds the
 * bundle that this resource contains, this source reports "not modified" without reading again. A caller
 * that holds a different bundle (for example a startup snapshot, because this resource failed to load) is
 * given the resource again.
 */
public final class ClasspathBundleSource implements BundleSource {

  private final ClassLoader classLoader;
  private final String resource;
  private volatile String loadedVersion;

  /**
   * Creates a source.
   *
   * @param classLoader the class loader to read from
   * @param resource the resource path, without the {@code classpath:} prefix; a leading slash is ignored
   */
  public ClasspathBundleSource(ClassLoader classLoader, String resource) {
    this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    String path = Objects.requireNonNull(resource, "resource");
    this.resource = path.startsWith("/") ? path.substring(1) : path;
  }

  @Override
  public String describe() {
    return "classpath:" + resource;
  }

  /**
   * {@inheritDoc}
   *
   * @throws io.github.tierwise.core.TierwiseException when the resource is not a loadable bundle
   */
  @Override
  public BundleFetch fetch(String currentVersion) throws IOException {
    if (currentVersion != null && currentVersion.equals(loadedVersion)) {
      return BundleFetch.notModified();
    }
    try (InputStream in = classLoader.getResourceAsStream(resource)) {
      if (in == null) {
        throw new FileNotFoundException("no classpath resource " + resource);
      }
      byte[] bytes = in.readAllBytes();
      String version = Tierwise.loadBundle(bytes).bundleVersion();
      loadedVersion = version;
      return BundleFetch.of(bytes, version);
    }
  }
}
