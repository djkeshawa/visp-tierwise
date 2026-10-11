package io.github.visplabs.tierwise.server.security;

import static io.github.visplabs.tierwise.server.Fixtures.PROMOTER;
import static io.github.visplabs.tierwise.server.Fixtures.PUBLISHER;
import static io.github.visplabs.tierwise.server.Fixtures.READER;
import static io.github.visplabs.tierwise.server.Fixtures.bundle;
import static io.github.visplabs.tierwise.server.Fixtures.publish;
import static io.github.visplabs.tierwise.server.Fixtures.record;
import static io.github.visplabs.tierwise.server.Fixtures.token;
import static io.github.visplabs.tierwise.server.Fixtures.version;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.visplabs.tierwise.server.Fixtures;
import io.github.visplabs.tierwise.server.ServerTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Authentication and authorisation with the default configuration: reads need a token, no default credentials. */
class SecurityTest extends ServerTest {

  @Autowired
  private ApplicationContext context;

  private final byte[] commerce = bundle("commerce.json");
  private final String commerceVersion = version(commerce);

  private static MockHttpServletRequestBuilder resolve() {
    return post("/v1/resolve").contentType(MediaType.APPLICATION_JSON).content("{\"keys\":[]}");
  }

  @Test
  void everyEndpointExceptTheOpenApiDocumentNeedsATokenByDefault() throws Exception {
    mvc.perform(get("/v1/bundles/latest")).andExpect(status().isUnauthorized());
    mvc.perform(get("/v1/bundles")).andExpect(status().isUnauthorized());
    mvc.perform(get("/v1/bundles/" + commerceVersion)).andExpect(status().isUnauthorized());
    mvc.perform(get("/v1/schema")).andExpect(status().isUnauthorized());
    mvc.perform(resolve()).andExpect(status().isUnauthorized());
    mvc.perform(post("/v1/decide").contentType(MediaType.APPLICATION_JSON).content("{\"feature\":\"x\"}"))
        .andExpect(status().isUnauthorized());
    mvc.perform(post("/v1/bundles/" + commerceVersion + "/promote")).andExpect(status().isUnauthorized());
    mvc.perform(publish(commerce, record("2026-10-09T08:00:00Z"))).andExpect(status().isUnauthorized());
  }

  @Test
  void anUnauthenticatedRequestGetsAJsonErrorAndTheBearerChallenge() throws Exception {
    mvc.perform(get("/v1/bundles/latest"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")))
        .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("application/json")))
        .andExpect(jsonPath("$.code").value("E_UNAUTHENTICATED"))
        .andExpect(jsonPath("$.message").isString());
  }

  @Test
  void theOpenApiDocumentIsOpen() throws Exception {
    mvc.perform(get("/openapi.yaml")).andExpect(status().isOk());
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head("/openapi.yaml"))
        .andExpect(status().isOk());
  }

  @Test
  void aTokenWithoutTheRoleCannotPublishOrPromote() throws Exception {
    mvc.perform(publish(commerce, record("2026-10-09T08:00:00Z")).with(READER))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"))
        .andExpect(jsonPath("$.message").isString());
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    mvc.perform(post("/v1/bundles/" + commerceVersion + "/promote").with(READER))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("E_FORBIDDEN"));
  }

  @Test
  void thePublishRoleCannotPromoteAndThePromoteRoleCannotPublish() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    mvc.perform(post("/v1/bundles/" + commerceVersion + "/promote").with(PUBLISHER)).andExpect(status().isForbidden());
    mvc.perform(publish(commerce, record("2026-10-09T09:00:00Z")).with(PROMOTER)).andExpect(status().isForbidden());
  }

  @Test
  void aDeniedPublishStoresNothing() throws Exception {
    mvc.perform(publish(commerce, record("2026-10-09T08:00:00Z")).with(READER)).andExpect(status().isForbidden());
    mvc.perform(get("/v1/bundles/latest").with(READER)).andExpect(status().isNotFound());
  }

  @Test
  void anyValidTokenMayReadByDefault() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    mvc.perform(get("/v1/bundles/latest").with(READER)).andExpect(status().isOk());
    mvc.perform(get("/v1/schema").with(READER)).andExpect(status().isOk());
    mvc.perform(get("/v1/bundles").with(READER)).andExpect(status().isOk());
    mvc.perform(resolve().with(READER)).andExpect(status().isOk());
  }

  @Test
  void aRoleThatIsNotConfiguredGrantsNothing() throws Exception {
    mvc.perform(publish(commerce, record("2026-10-09T08:00:00Z")).with(token("admin", "publisher", "tierwise-reader")))
        .andExpect(status().isForbidden());
  }

  @Test
  void thereIsNoDefaultUserAndTheServerRejectsTokensItCannotVerify() throws Exception {
    assertTrue(context.getBeansOfType(UserDetailsService.class).isEmpty());
    JwtDecoder decoder = context.getBean(JwtDecoder.class);
    org.junit.jupiter.api.Assertions.assertThrows(org.springframework.security.oauth2.jwt.JwtException.class,
        () -> decoder.decode("any.token.value"));
    mvc.perform(get("/v1/bundles/latest").header("Authorization", "Bearer any.token.value"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("E_UNAUTHENTICATED"));
    mvc.perform(get("/v1/bundles/latest").header("Authorization", "Basic dXNlcjpwYXNz"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void theServerKeepsNoSession() throws Exception {
    var result = mvc.perform(get("/v1/bundles").with(READER)).andExpect(status().isOk()).andReturn();
    assertEquals(null, result.getRequest().getSession(false));
    assertEquals(null, result.getResponse().getHeader("Set-Cookie"));
  }

  @Test
  void unknownPathsAreDeniedWithoutRevealingAnything() throws Exception {
    mvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
    mvc.perform(get("/v1/other").with(READER)).andExpect(status().isForbidden());
    mvc.perform(get("/").with(Fixtures.PUBLISHER)).andExpect(status().isForbidden());
  }
}
