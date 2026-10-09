package io.github.tierwise.server.s3;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.server.TierwiseServerAutoConfiguration;
import io.github.tierwise.server.store.BundleStore;
import io.github.tierwise.server.store.FilesystemBundleStore;
import io.github.tierwise.server.store.PublicationRecord;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.Configurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import tools.jackson.databind.json.JsonMapper;

class S3BundleStoreAutoConfigurationTest {

  static final String VERSION = S3KeyLayoutTest.VERSION;

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(S3BundleStoreAutoConfiguration.class))
      .withPropertyValues(
          "tierwise.server.store=s3",
          "tierwise.server.s3.bucket=tierwise-test",
          "tierwise.server.s3.region=eu-west-1",
          "tierwise.server.s3.endpoint=http://127.0.0.1:9");

  @TempDir
  Path root;

  @Test
  void registersTheStoreAndItsClientWhenTheStoreIsS3() {
    runner.run(context -> {
      assertNull(context.getStartupFailure());
      assertInstanceOf(S3BundleStore.class, context.getBean(BundleStore.class));
      assertNotNull(context.getBean(S3Client.class));
    });
  }

  @Test
  void bindsThePrefixAndAddressingStyle() {
    runner.withPropertyValues("tierwise.server.s3.prefix=/tierwise/prod/", "tierwise.server.s3.path-style-access=true")
        .run(context -> {
          S3StoreProperties properties = context.getBean(S3StoreProperties.class);
          assertEquals("/tierwise/prod/", properties.getPrefix());
          assertTrue(properties.isPathStyleAccess());
          assertEquals("tierwise-test", properties.getBucket());
        });
  }

  @Test
  void writesThroughTheRegisteredStoreReachTheConfiguredClient() {
    FakeS3Client fake = new FakeS3Client("tierwise-test");
    runner.withBean(S3Client.class, () -> fake)
        .withPropertyValues("tierwise.server.s3.prefix=tw")
        .run(context -> {
          BundleStore store = context.getBean(BundleStore.class);
          assertTrue(store.putBundle(VERSION, "body".getBytes(UTF_8)));

          assertArrayEquals("body".getBytes(UTF_8), fake.bytesOf("tw/bundles/" + VERSION + ".json").orElseThrow());
        });
  }

  @Test
  void aRecordWrittenThroughTheRegisteredStoreIsReadBackAsTheSameRecord() {
    FakeS3Client fake = new FakeS3Client("tierwise-test");
    runner.withBean(S3Client.class, () -> fake)
        .withPropertyValues("tierwise.server.s3.prefix=tw")
        .run(context -> {
          BundleStore store = context.getBean(BundleStore.class);
          PublicationRecord record = new PublicationRecord(VERSION, "2026-03-04T05:06:07Z",
              Optional.empty(), Optional.of("ci"));
          store.addRecord(record);

          assertEquals(List.of(record), store.records(VERSION));
        });
  }

  @Test
  void doesNotRegisterAnythingWhenTheStoreIsNotS3() {
    runner.withPropertyValues("tierwise.server.store=filesystem").run(context -> {
      assertFalse(context.containsBean("tierwiseS3BundleStore"));
      assertTrue(context.getBeansOfType(BundleStore.class).isEmpty());
      assertTrue(context.getBeansOfType(S3Client.class).isEmpty());
    });
  }

  @Test
  void doesNotRegisterAnythingWhenTheStoreIsUnset() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(S3BundleStoreAutoConfiguration.class))
        .withPropertyValues("tierwise.server.s3.bucket=tierwise-test")
        .run(context -> assertTrue(context.getBeansOfType(BundleStore.class).isEmpty()));
  }

  @Test
  void aMissingBucketFailsStartupWithANamedProperty() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(S3BundleStoreAutoConfiguration.class))
        .withPropertyValues(
            "tierwise.server.store=s3",
            "tierwise.server.s3.region=eu-west-1",
            "tierwise.server.s3.endpoint=http://127.0.0.1:9")
        .run(context -> {
          assertNotNull(context.getStartupFailure());
          Throwable cause = context.getStartupFailure();
          StringBuilder messages = new StringBuilder();
          while (cause != null) {
            messages.append(cause.getMessage()).append('\n');
            cause = cause.getCause();
          }
          assertTrue(messages.toString().contains("tierwise.server.s3.bucket"), messages.toString());
        });
  }

  @Test
  void anApplicationBundleStoreReplacesTheDefault() {
    BundleStore custom = new BundleStore() {
      @Override
      public boolean putBundle(String version, byte[] bytes) {
        return true;
      }

      @Override
      public Optional<byte[]> getBundle(String version) {
        return Optional.empty();
      }

      @Override
      public void addRecord(PublicationRecord record) {
      }

      @Override
      public List<PublicationRecord> records(String version) {
        return List.of();
      }

      @Override
      public List<PublicationRecord> allRecords() {
        return List.of();
      }

      @Override
      public Optional<String> latest() {
        return Optional.empty();
      }

      @Override
      public void setLatest(String version) {
      }
    };
    runner.withBean(BundleStore.class, () -> custom).run(context -> {
      assertSame(custom, context.getBean(BundleStore.class));
      assertEquals(1, context.getBeansOfType(BundleStore.class).size());
    });
  }

  @Test
  void anApplicationS3ClientReplacesTheDefaultClient() {
    FakeS3Client fake = new FakeS3Client("tierwise-test");
    runner.withBean(S3Client.class, () -> fake).run(context -> {
      assertSame(fake, context.getBean(S3Client.class));
      assertEquals(1, context.getBeansOfType(S3Client.class).size());
    });
  }

  /**
   * The S3 configuration must run before {@link TierwiseServerAutoConfiguration}, whose filesystem bean is conditional
   * on the absence of any BundleStore. Boot sorts the classes of {@code AutoConfigurations} with its own sorter, so
   * this asserts the order that the {@code before} attribute produces. Alphabetically the server class sorts first,
   * so without the attribute the test fails.
   */
  @Test
  void theS3ConfigurationIsOrderedBeforeTheServerConfiguration() {
    Class<?>[] ordered = Configurations.getClasses(
        AutoConfigurations.of(TierwiseServerAutoConfiguration.class, S3BundleStoreAutoConfiguration.class));

    assertArrayEquals(new Class<?>[] {S3BundleStoreAutoConfiguration.class, TierwiseServerAutoConfiguration.class},
        ordered);
  }

  /**
   * The S3 store replaces the server's filesystem default when the full server context starts. The server binds
   * {@code tierwise.server.store} as a scalar and registers its filesystem store only for {@code filesystem}, so
   * the two stores cannot both exist.
   */
  @Test
  void theS3StoreReplacesTheServerFilesystemDefault() {
    webRunner(true).run(context -> {
      assertNull(context.getStartupFailure());
      assertEquals(1, context.getBeansOfType(BundleStore.class).size());
      assertInstanceOf(S3BundleStore.class, context.getBean(BundleStore.class));
      assertFalse(context.containsBean("tierwiseBundleStore"));
    });
  }

  @Test
  void theServerFilesystemDefaultIsUsedWhenTheStoreIsNotS3() {
    webRunner(false)
        .withPropertyValues("tierwise.server.filesystem.root=" + root.resolve("data"))
        .run(context -> {
          assertNull(context.getStartupFailure());
          assertInstanceOf(FilesystemBundleStore.class, context.getBean(BundleStore.class));
          assertFalse(context.containsBean("tierwiseS3BundleStore"));
        });
  }

  /**
   * A web context with the real server configuration. It supplies the JSON mapper that the server's services need,
   * and no servlet container.
   */
  private WebApplicationContextRunner webRunner(boolean s3) {
    WebApplicationContextRunner webRunner = new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TierwiseServerAutoConfiguration.class,
            S3BundleStoreAutoConfiguration.class))
        .withBean(JsonMapper.class, () -> JsonMapper.builder().build());
    if (s3) {
      webRunner = webRunner.withPropertyValues(
          "tierwise.server.store=s3",
          "tierwise.server.s3.bucket=tierwise-test",
          "tierwise.server.s3.region=eu-west-1",
          "tierwise.server.s3.endpoint=http://127.0.0.1:9");
    }
    return webRunner;
  }

  @Test
  void theAutoConfigurationIsListedInTheImportsFile() throws IOException {
    String resource = "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";
    try (InputStream in = getClass().getClassLoader().getResourceAsStream(resource)) {
      assertNotNull(in, resource);
      String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(text.lines().anyMatch(line -> line.trim().equals(S3BundleStoreAutoConfiguration.class.getName())), text);
    }
  }
}
