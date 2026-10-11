package io.github.visplabs.tierwise.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.server.store.BundleStore;
import io.github.visplabs.tierwise.server.store.FilesystemBundleStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import io.github.visplabs.tierwise.server.store.PublicationRecord;

/** {@code tierwise.server.store} chooses the store; the filesystem store is the default and backs off. */
class StoreSelectionTest {

  @TempDir
  Path root;

  private WebApplicationContextRunner runner() {
    return new WebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class, TierwiseServerAutoConfiguration.class))
        .withPropertyValues("tierwise.server.filesystem.root=" + root.resolve("data"));
  }

  @Test
  void theFilesystemStoreIsTheDefault() {
    runner().run(context -> {
      assertNull(context.getStartupFailure());
      assertInstanceOf(FilesystemBundleStore.class, context.getBean(BundleStore.class));
      assertTrue(Files.isDirectory(root.resolve("data").resolve("bundles")));
    });
  }

  @Test
  void filesystemCanBeNamedExplicitly() {
    runner().withPropertyValues("tierwise.server.store=filesystem").run(context ->
        assertInstanceOf(FilesystemBundleStore.class, context.getBean(BundleStore.class)));
  }

  @Test
  void anotherStoreNameRegistersNoFilesystemStore() {
    // Nothing can serve without a store, so the application does not start, and no filesystem store was created.
    runner().withPropertyValues("tierwise.server.store=s3").run(context -> {
      assertNotNull(context.getStartupFailure());
      assertTrue(Files.notExists(root.resolve("data")));
    });
  }

  @Test
  void anotherStoreNameWorksWithTheStoreThatTheApplicationProvides() {
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
    runner().withPropertyValues("tierwise.server.store=s3").withBean(BundleStore.class, () -> custom).run(context -> {
      assertNull(context.getStartupFailure());
      assertSame(custom, context.getBean(BundleStore.class));
    });
  }

  @Test
  void aBeanReplacesTheFilesystemStoreWhateverTheStoreProperty() {
    BundleStore custom = new FilesystemBundleStore(root.resolve("other"));
    runner().withBean(BundleStore.class, () -> custom).run(context -> {
      assertEquals(1, context.getBeansOfType(BundleStore.class).size());
      assertSame(custom, context.getBean(BundleStore.class));
      assertTrue(Files.notExists(root.resolve("data")));
    });
  }

  @Test
  void theDefaultsAreTheDocumentedOnes() {
    runner().run(context -> {
      TierwiseServerProperties properties = context.getBean(TierwiseServerProperties.class);
      assertEquals("filesystem", properties.store());
      assertEquals(16L * 1024 * 1024, properties.maxBundleSize().toBytes());
      assertEquals(200, properties.providerTimeout().toMillis());
      assertEquals(0, properties.latestCacheTtl().toMillis());
      assertEquals(TierwiseServerProperties.Reads.AUTHENTICATED, properties.security().reads());
      assertEquals("tierwise-publisher", properties.security().publishRole());
      assertEquals("tierwise-promoter", properties.security().promoteRole());
      assertEquals("", properties.security().readRole());
      assertEquals("roles", properties.security().rolesClaim());
    });
  }

  @Test
  void invalidSettingsFailStartupWithTheNameOfTheProperty() {
    for (String bad : List.of("tierwise.server.provider-timeout=0ms", "tierwise.server.provider-timeout=1500us",
        "tierwise.server.max-bundle-size=0B", "tierwise.server.runtime-cache-size=0",
        "tierwise.server.max-keys-per-request=0", "tierwise.server.latest-cache-ttl=-1s",
        "tierwise.server.security.publish-role= ", "tierwise.server.security.roles-claim= ")) {
      runner().withPropertyValues(bad).run(context -> {
        assertNotNull(context.getStartupFailure(), bad);
        Throwable cause = context.getStartupFailure();
        StringBuilder messages = new StringBuilder();
        while (cause != null) {
          messages.append(cause.getMessage()).append('\n');
          cause = cause.getCause();
        }
        assertTrue(messages.toString().contains("tierwise.server"), bad + ": " + messages);
      });
    }
  }
}
