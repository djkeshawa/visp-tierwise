package io.github.visplabs.tierwise.server.security;

import static io.github.visplabs.tierwise.server.Fixtures.PROMOTER;
import static io.github.visplabs.tierwise.server.Fixtures.PUBLISHER;
import static io.github.visplabs.tierwise.server.Fixtures.bundle;
import static io.github.visplabs.tierwise.server.Fixtures.publish;
import static io.github.visplabs.tierwise.server.Fixtures.record;
import static io.github.visplabs.tierwise.server.Fixtures.version;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.visplabs.tierwise.server.ServerTest;
import io.github.visplabs.tierwise.server.TierwiseServerApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

/** With {@code tierwise.server.security.reads=open}, reads need no token; publish and promote still do. */
@SpringBootTest(classes = TierwiseServerApplication.class, properties = "tierwise.server.security.reads=open")
class OpenReadsTest extends ServerTest {

  private final byte[] commerce = bundle("commerce.json");
  private final String commerceVersion = version(commerce);

  @Test
  void readsNeedNoToken() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    mvc.perform(get("/v1/bundles/latest")).andExpect(status().isOk());
    mvc.perform(get("/v1/bundles/latest").header("If-None-Match", "\"" + commerceVersion + "\""))
        .andExpect(status().isNotModified());
    mvc.perform(get("/v1/bundles/" + commerceVersion)).andExpect(status().isOk());
    mvc.perform(get("/v1/bundles")).andExpect(status().isOk());
    mvc.perform(get("/v1/schema")).andExpect(status().isOk());
    mvc.perform(post("/v1/resolve").contentType(MediaType.APPLICATION_JSON).content("{\"keys\":[]}"))
        .andExpect(status().isOk());
    mvc.perform(post("/v1/decide").contentType(MediaType.APPLICATION_JSON)
        .content("{\"feature\":\"checkout.invoice_release\"}")).andExpect(status().isOk());
  }

  @Test
  void publishAndPromoteStillNeedTheirRoles() throws Exception {
    mvc.perform(publish(commerce, record("2026-10-09T08:00:00Z"))).andExpect(status().isUnauthorized());
    mvc.perform(post("/v1/bundles/" + commerceVersion + "/promote")).andExpect(status().isUnauthorized());
    mvc.perform(publish(commerce, record("2026-10-09T08:00:00Z")).with(PROMOTER)).andExpect(status().isForbidden());
    mvc.perform(publish(commerce, record("2026-10-09T08:00:00Z")).with(PUBLISHER)).andExpect(status().isCreated());
    mvc.perform(post("/v1/bundles/" + commerceVersion + "/promote").with(PUBLISHER)).andExpect(status().isForbidden());
    mvc.perform(post("/v1/bundles/" + commerceVersion + "/promote").with(PROMOTER)).andExpect(status().isOk());
  }

  @Test
  void anInvalidTokenIsStillRejected() throws Exception {
    mvc.perform(get("/v1/bundles/latest").header("Authorization", "Bearer broken")).andExpect(status().isUnauthorized());
  }
}
