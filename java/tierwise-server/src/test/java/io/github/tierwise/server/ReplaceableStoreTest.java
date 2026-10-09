package io.github.tierwise.server;

import static io.github.tierwise.server.Fixtures.PROMOTER;
import static io.github.tierwise.server.Fixtures.PUBLISHER;
import static io.github.tierwise.server.Fixtures.READER;
import static io.github.tierwise.server.Fixtures.bundle;
import static io.github.tierwise.server.Fixtures.publish;
import static io.github.tierwise.server.Fixtures.record;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tierwise.server.store.BundleStore;
import io.github.tierwise.server.store.BundleStoreException;
import io.github.tierwise.server.store.PublicationRecord;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

/** An application can replace the {@link BundleStore}; a store that fails gives 503 {@code E_STORE_UNAVAILABLE}. */
@SpringBootTest(classes = TierwiseServerApplication.class)
@Import(ReplaceableStoreTest.Failing.class)
class ReplaceableStoreTest extends ServerTest {

  /** A store whose storage is down. */
  @TestConfiguration
  static class Failing {
    @Bean
    BundleStore failingStore() {
      return new BundleStore() {
        private BundleStoreException down() {
          return new BundleStoreException("the storage is down");
        }

        @Override
        public boolean putBundle(String version, byte[] bytes) {
          throw down();
        }

        @Override
        public Optional<byte[]> getBundle(String version) {
          throw down();
        }

        @Override
        public void addRecord(PublicationRecord record) {
          throw down();
        }

        @Override
        public List<PublicationRecord> records(String version) {
          throw down();
        }

        @Override
        public List<PublicationRecord> allRecords() {
          throw down();
        }

        @Override
        public Optional<String> latest() {
          throw down();
        }

        @Override
        public void setLatest(String version) {
          throw down();
        }
      };
    }
  }

  @Autowired
  private ApplicationContext context;

  private final String version = Fixtures.version(bundle("commerce.json"));

  @Test
  void theApplicationsStoreReplacesTheFilesystemStore() {
    assertEquals(1, context.getBeansOfType(BundleStore.class).size());
    assertInstanceOf(BundleStore.class, context.getBean("failingStore"));
  }

  @Test
  void everyEndpointReports503WhenTheStoreFails() throws Exception {
    mvc.perform(publish(bundle("commerce.json"), record("2026-10-09T08:00:00Z")).with(PUBLISHER))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("E_STORE_UNAVAILABLE"));
    mvc.perform(get("/v1/bundles/latest").with(READER)).andExpect(status().isServiceUnavailable());
    mvc.perform(get("/v1/bundles/" + version).with(READER)).andExpect(status().isServiceUnavailable());
    mvc.perform(get("/v1/bundles").with(READER)).andExpect(status().isServiceUnavailable());
    mvc.perform(post("/v1/bundles/" + version + "/promote").with(PROMOTER)).andExpect(status().isServiceUnavailable());
    mvc.perform(get("/v1/schema").with(READER)).andExpect(status().isServiceUnavailable());
    mvc.perform(post("/v1/resolve").contentType(MediaType.APPLICATION_JSON).content("{\"keys\":[]}").with(READER))
        .andExpect(status().isServiceUnavailable());
    mvc.perform(post("/v1/decide").contentType(MediaType.APPLICATION_JSON).content("{\"feature\":\"x\"}").with(READER))
        .andExpect(status().isServiceUnavailable());
  }

  @Test
  void theErrorDoesNotRevealTheCause() throws Exception {
    mvc.perform(get("/v1/bundles").with(READER))
        .andExpect(jsonPath("$.message").value("the bundle store is unavailable"));
  }
}
