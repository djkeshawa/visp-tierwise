package io.github.visplabs.tierwise.server.security;

import static io.github.visplabs.tierwise.server.Fixtures.PUBLISHER;
import static io.github.visplabs.tierwise.server.Fixtures.READER;
import static io.github.visplabs.tierwise.server.Fixtures.bundle;
import static io.github.visplabs.tierwise.server.Fixtures.publish;
import static io.github.visplabs.tierwise.server.Fixtures.record;
import static io.github.visplabs.tierwise.server.Fixtures.token;
import static io.github.visplabs.tierwise.server.Fixtures.version;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.visplabs.tierwise.server.ServerTest;
import io.github.visplabs.tierwise.server.TierwiseServerApplication;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Configured role names, a nested roles claim and a read role, with the real converter in the chain: tokens are
 * decoded by a test decoder whose token text lists the roles, and the server's converter turns them into
 * authorities.
 */
@SpringBootTest(classes = TierwiseServerApplication.class, properties = {
    "tierwise.server.security.publish-role=ci-publish",
    "tierwise.server.security.promote-role=ci-promote",
    "tierwise.server.security.read-role=consumer",
    "tierwise.server.security.roles-claim=realm_access.roles"})
@Import(ConfiguredRolesTest.Decoder.class)
class ConfiguredRolesTest extends ServerTest {

  /** A decoder for tests: the token {@code r.a.b} is a JWT whose {@code realm_access.roles} are a and b. */
  @TestConfiguration
  static class Decoder {
    @Bean
    JwtDecoder testDecoder() {
      return token -> {
        if (!token.equals("r") && !token.startsWith("r.")) {
          throw new BadJwtException("unknown token");
        }
        List<String> roles = token.length() == 1 ? List.of() : List.of(token.substring(2).split("\\."));
        return Jwt.withTokenValue(token).header("alg", "none").subject("tester")
            .issuedAt(Instant.parse("2026-01-01T00:00:00Z")).expiresAt(Instant.parse("2099-01-01T00:00:00Z"))
            .claim("realm_access", Map.of("roles", roles)).build();
      };
    }
  }

  private final byte[] commerce = bundle("commerce.json");
  private final String commerceVersion = version(commerce);

  private static String bearer(String roles) {
    return roles.isEmpty() ? "Bearer r" : "Bearer r." + roles;
  }

  @Test
  void theConfiguredPublishRoleFromTheNestedClaimMayPublish() throws Exception {
    mvc.perform(publish(commerce, record("2026-10-09T08:00:00Z")).header("Authorization", bearer("ci-publish")))
        .andExpect(status().isCreated());
  }

  @Test
  void theDefaultRoleNamesDoNotApplyAnyMore() throws Exception {
    mvc.perform(publish(commerce, record("2026-10-09T08:00:00Z")).header("Authorization", bearer("tierwise-publisher")))
        .andExpect(status().isForbidden());
    mvc.perform(publish(commerce, record("2026-10-09T08:00:00Z")).with(PUBLISHER)).andExpect(status().isForbidden());
  }

  @Test
  void theConfiguredPromoteRoleMayPromote() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    mvc.perform(post("/v1/bundles/" + commerceVersion + "/promote").header("Authorization", bearer("ci-publish")))
        .andExpect(status().isForbidden());
    mvc.perform(post("/v1/bundles/" + commerceVersion + "/promote").header("Authorization", bearer("ci-promote")))
        .andExpect(status().isOk());
  }

  @Test
  void readsNeedTheReadRole() throws Exception {
    mvc.perform(get("/v1/bundles").header("Authorization", bearer("")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
    mvc.perform(get("/v1/bundles").header("Authorization", bearer("ci-publish")))
        .andExpect(status().isForbidden());
    mvc.perform(get("/v1/bundles").header("Authorization", bearer("consumer"))).andExpect(status().isOk());
    mvc.perform(get("/v1/bundles").with(READER)).andExpect(status().isForbidden());
    mvc.perform(get("/v1/bundles").with(token("consumer"))).andExpect(status().isOk());
    mvc.perform(get("/v1/bundles")).andExpect(status().isUnauthorized());
  }

  @Override
  protected void publishOk(byte[] bundle, String record) throws Exception {
    mvc.perform(publish(bundle, record).header("Authorization", bearer("ci-publish"))).andExpect(status().isCreated());
  }
}
