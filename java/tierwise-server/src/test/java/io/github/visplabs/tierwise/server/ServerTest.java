package io.github.visplabs.tierwise.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Base of the endpoint tests: the whole application on a shared, emptied filesystem store, with the test profile. */
@SpringBootTest(classes = TierwiseServerApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ServerTest {

  /** The mock client. */
  @Autowired
  protected MockMvc mvc;
  /** A JSON mapper. */
  @Autowired
  protected JsonMapper json;

  /**
   * Points the application at the shared store directory.
   *
   * @param registry the property registry
   */
  @DynamicPropertySource
  static void store(DynamicPropertyRegistry registry) {
    registry.add("tierwise.server.filesystem.root", () -> Fixtures.STORE.toString());
  }

  /** Starts every test with an empty store. */
  @BeforeEach
  void emptyStore() {
    Fixtures.resetStore();
  }

  /** Publishes a bundle with the publisher token and asserts that it was accepted. */
  protected void publishOk(byte[] bundle, String record) throws Exception {
    int status = mvc.perform(Fixtures.publish(bundle, record).with(Fixtures.PUBLISHER)).andReturn().getResponse()
        .getStatus();
    assertEquals(true, status == 200 || status == 201, "publish status " + status);
  }

  /** Performs a request and returns the parsed JSON object of the response. */
  protected Map<String, Object> object(MockHttpServletRequestBuilder request, RequestPostProcessor token, int status)
      throws Exception {
    MvcResult result = mvc.perform(request.with(token)).andExpect(status().is(status)).andReturn();
    return parse(result);
  }

  /** Parses the body of a response as a JSON object. */
  protected Map<String, Object> parse(MvcResult result) throws Exception {
    return json.readValue(result.getResponse().getContentAsByteArray(), new TypeReference<Map<String, Object>>() { });
  }
}
