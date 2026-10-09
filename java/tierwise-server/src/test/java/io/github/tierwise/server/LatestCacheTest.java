package io.github.tierwise.server;

import static io.github.tierwise.server.Fixtures.PROMOTER;
import static io.github.tierwise.server.Fixtures.READER;
import static io.github.tierwise.server.Fixtures.bundle;
import static io.github.tierwise.server.Fixtures.record;
import static io.github.tierwise.server.Fixtures.version;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tierwise.server.store.FilesystemBundleStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * With a latest-pointer cache, an instance may lag behind another instance by the configured time, but a
 * publish or promote through the instance itself takes effect at once.
 */
@SpringBootTest(classes = TierwiseServerApplication.class, properties = "tierwise.server.latest-cache-ttl=1h")
class LatestCacheTest extends ServerTest {

  private final byte[] commerce = bundle("commerce.json");
  private final byte[] retail = bundle("retail-pos.json");

  @Test
  void aChangeMadeByAnotherInstanceIsSeenAfterTheTtlButOwnChangesAtOnce() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    mvc.perform(get("/v1/bundles/latest").with(READER)).andExpect(header().string("ETag", "\"" + version(commerce) + "\""));

    // Another instance publishes and promotes through the shared store.
    FilesystemBundleStore other = new FilesystemBundleStore(Fixtures.STORE);
    other.putBundle(version(retail), retail);
    other.setLatest(version(retail));
    mvc.perform(get("/v1/bundles/latest").with(READER)).andExpect(header().string("ETag", "\"" + version(commerce) + "\""));

    // A promote through this instance is visible at once.
    mvc.perform(post("/v1/bundles/" + version(retail) + "/promote").with(PROMOTER)).andExpect(status().isOk());
    mvc.perform(get("/v1/bundles/latest").with(READER)).andExpect(header().string("ETag", "\"" + version(retail) + "\""));
  }
}
