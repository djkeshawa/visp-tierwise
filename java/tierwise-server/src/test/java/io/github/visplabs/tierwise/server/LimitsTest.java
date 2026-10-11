package io.github.visplabs.tierwise.server;

import static io.github.visplabs.tierwise.server.Fixtures.PUBLISHER;
import static io.github.visplabs.tierwise.server.Fixtures.READER;
import static io.github.visplabs.tierwise.server.Fixtures.bundle;
import static io.github.visplabs.tierwise.server.Fixtures.publish;
import static io.github.visplabs.tierwise.server.Fixtures.record;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

/** The size and key-count limits of the configuration. */
@SpringBootTest(classes = TierwiseServerApplication.class,
    properties = {"tierwise.server.max-bundle-size=1KB", "tierwise.server.max-keys-per-request=2"})
class LimitsTest extends ServerTest {

  @Test
  void aBundleOverTheConfiguredSizeIs413AndNothingIsStored() throws Exception {
    mvc.perform(publish(bundle("commerce.json"), record("2026-10-09T08:00:00Z")).with(PUBLISHER))
        .andExpect(status().is(413))
        .andExpect(jsonPath("$.code").value("E_REQUEST_TOO_LARGE"));
    mvc.perform(get("/v1/bundles/latest").with(READER)).andExpect(status().isNotFound());
  }

  @Test
  void aBundleWithinTheLimitIsAccepted() throws Exception {
    mvc.perform(publish(bundle("edge-hierarchy-root.json"), record("2026-10-09T08:00:00Z")).with(PUBLISHER))
        .andExpect(status().isCreated());
  }

  @Test
  void moreKeysThanTheLimitIs400() throws Exception {
    mvc.perform(post("/v1/resolve").contentType(MediaType.APPLICATION_JSON).content("{\"keys\":[\"a\",\"b\",\"c\"]}")
            .with(READER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_REQUEST_INVALID"));
  }
}
