package io.github.visplabs.tierwise.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** GET /openapi.yaml: the hand-written OpenAPI 3.1 document describes every endpoint. */
class OpenApiTest extends ServerTest {

  @Autowired
  private RequestMappingHandlerMapping mappings;

  @SuppressWarnings("unchecked")
  private Map<String, Object> document() throws Exception {
    String text = mvc.perform(get("/openapi.yaml")).andReturn().getResponse().getContentAsString();
    return (Map<String, Object>) new Load(LoadSettings.builder().build()).loadFromString(text);
  }

  @Test
  void theDocumentIsServedAsYaml() throws Exception {
    mvc.perform(get("/openapi.yaml"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", "application/yaml"));
  }

  @Test
  void theDocumentIsServedExactlyAsPackaged() throws Exception {
    byte[] served = mvc.perform(get("/openapi.yaml")).andReturn().getResponse().getContentAsByteArray();
    try (InputStream stream = OpenApiTest.class.getResourceAsStream("/openapi.yaml")) {
      assertNotNull(stream);
      assertEquals(new String(stream.readAllBytes(), StandardCharsets.UTF_8), new String(served, StandardCharsets.UTF_8));
    }
  }

  @Test
  void theDocumentIsOpenApi31() throws Exception {
    Map<String, Object> document = document();
    assertEquals("3.1.0", document.get("openapi"));
    assertNotNull(document.get("info"));
    assertNotNull(document.get("components"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void theDocumentDescribesExactlyTheEndpointsOfTheServer() throws Exception {
    Map<String, Object> paths = (Map<String, Object>) document().get("paths");
    Set<String> documented = new TreeSet<>();
    for (Map.Entry<String, Object> path : paths.entrySet()) {
      for (String method : ((Map<String, Object>) path.getValue()).keySet()) {
        documented.add(method.toUpperCase() + " " + path.getKey());
      }
    }
    Set<String> implemented = new TreeSet<>();
    for (Map.Entry<RequestMappingInfo, HandlerMethod> mapping : mappings.getHandlerMethods().entrySet()) {
      if (!mapping.getValue().getBeanType().getName().startsWith("io.github.visplabs.tierwise.server.")) {
        continue;
      }
      Set<RequestMethod> methods = mapping.getKey().getMethodsCondition().getMethods();
      Collection<String> patterns = mapping.getKey().getPathPatternsCondition().getPatternValues();
      for (String pattern : patterns) {
        for (RequestMethod method : methods) {
          implemented.add(method.name() + " " + pattern);
        }
      }
    }
    assertEquals(implemented, documented);
    assertEquals(Set.of("POST /v1/bundles", "GET /v1/bundles", "GET /v1/bundles/latest", "GET /v1/bundles/{version}",
        "POST /v1/bundles/{version}/promote", "POST /v1/resolve", "POST /v1/decide", "GET /v1/schema",
        "GET /openapi.yaml"), implemented);
  }

  @Test
  @SuppressWarnings("unchecked")
  void everyOperationHasAnIdSecurityAndTheErrorResponses() throws Exception {
    Map<String, Object> document = document();
    Map<String, Object> paths = (Map<String, Object>) document.get("paths");
    List<String> ids = new ArrayList<>();
    for (Object path : paths.values()) {
      for (Object operation : ((Map<String, Object>) path).values()) {
        Map<String, Object> op = (Map<String, Object>) operation;
        ids.add((String) op.get("operationId"));
        assertTrue(op.containsKey("responses"));
        assertTrue(op.containsKey("security"), op.get("operationId") + " has no security");
        Map<String, Object> responses = (Map<String, Object>) op.get("responses");
        if (!"getOpenApi".equals(op.get("operationId"))) {
          assertTrue(responses.containsKey("401"), op.get("operationId") + " lacks a 401 response");
        }
      }
    }
    assertEquals(ids.size(), Set.copyOf(ids).size(), "operationIds are unique");
  }

  @Test
  @SuppressWarnings("unchecked")
  void everyReferenceResolves() throws Exception {
    String text = new String(OpenApiTest.class.getResourceAsStream("/openapi.yaml").readAllBytes(), StandardCharsets.UTF_8);
    Map<String, Object> document = document();
    Map<String, Object> components = (Map<String, Object>) document.get("components");
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\$ref: '#/components/(\\w+)/([\\w-]+)'")
        .matcher(text);
    int count = 0;
    while (matcher.find()) {
      Map<String, Object> section = (Map<String, Object>) components.get(matcher.group(1));
      assertNotNull(section, matcher.group());
      assertTrue(section.containsKey(matcher.group(2)), "unresolved " + matcher.group());
      count++;
    }
    assertTrue(count > 20);
  }

  @Test
  void theErrorSchemaIsCodeAndMessage() throws Exception {
    @SuppressWarnings("unchecked")
    Map<String, Object> schemas = (Map<String, Object>) ((Map<String, Object>) document().get("components")).get("schemas");
    @SuppressWarnings("unchecked")
    Map<String, Object> error = (Map<String, Object>) schemas.get("Error");
    assertEquals(List.of("code", "message"), error.get("required"));
  }

  @Test
  void theReadEndpointsAreDocumentedAsOpenOrAuthenticated() throws Exception {
    @SuppressWarnings("unchecked")
    Map<String, Object> paths = (Map<String, Object>) document().get("paths");
    @SuppressWarnings("unchecked")
    Map<String, Object> latest = (Map<String, Object>) ((Map<String, Object>) paths.get("/v1/bundles/latest")).get("get");
    @SuppressWarnings("unchecked")
    List<Object> security = (List<Object>) latest.get("security");
    assertEquals(2, security.size());
    assertTrue(security.contains(Map.of()));
  }
}
