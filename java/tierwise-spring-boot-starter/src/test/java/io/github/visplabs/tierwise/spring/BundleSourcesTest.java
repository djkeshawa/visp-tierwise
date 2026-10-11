package io.github.visplabs.tierwise.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BundleSourcesTest {

  private final TierwiseProperties.Http http = new TierwiseProperties().getHttp();
  private final ClassLoader loader = getClass().getClassLoader();

  @Test
  void classpathLocation() {
    BundleSource source = BundleSources.forLocation("classpath:/bundles/b.json", loader, http);

    assertInstanceOf(ClasspathBundleSource.class, source);
    assertEquals("classpath:bundles/b.json", source.describe());
  }

  @Test
  void fileLocationWithAPathAndWithAUri() {
    assertInstanceOf(FileBundleSource.class, BundleSources.forLocation("file:/var/tw/b.json", loader, http));
    assertInstanceOf(FileBundleSource.class, BundleSources.forLocation("file:///var/tw/b.json", loader, http));
    assertInstanceOf(FileBundleSource.class, BundleSources.forLocation("file:relative/b.json", loader, http));
  }

  @Test
  void httpAndHttpsLocationsAreTakenAsTheBundleUrl() {
    BundleSource plain = BundleSources.forLocation("http://host:8080/some/bundle.json", loader, http);
    BundleSource secure = BundleSources.forLocation("HTTPS://host/b", loader, http);

    assertInstanceOf(HttpBundleSource.class, plain);
    assertEquals("http://host:8080/some/bundle.json", plain.describe());
    assertInstanceOf(HttpBundleSource.class, secure);
  }

  @Test
  void aServerBaseUrlGetsTheLatestBundlePath() {
    assertEquals("https://tw.internal/v1/bundles/latest", BundleSources.forServer("https://tw.internal", http).describe());
    assertEquals("https://tw.internal:9/base/v1/bundles/latest",
        BundleSources.forServer("https://tw.internal:9/base/", http).describe());
  }

  @Test
  void unsupportedSchemesAndMalformedLocationsAreRejectedWithoutEchoingTheLocation() {
    IllegalArgumentException ftp = assertThrows(IllegalArgumentException.class,
        () -> BundleSources.forLocation("ftp://user:secret@host/b", loader, http));
    assertTrue(!ftp.getMessage().contains("secret"));
    assertThrows(IllegalArgumentException.class, () -> BundleSources.forLocation("bundle.json", loader, http));
    assertThrows(IllegalArgumentException.class, () -> BundleSources.forLocation("classpath:", loader, http));
    assertThrows(IllegalArgumentException.class, () -> BundleSources.forLocation("file:", loader, http));
    assertThrows(IllegalArgumentException.class, () -> BundleSources.forLocation("http:///nohost", loader, http));
    assertThrows(IllegalArgumentException.class, () -> BundleSources.forServer("tw.internal", http));
  }
}
