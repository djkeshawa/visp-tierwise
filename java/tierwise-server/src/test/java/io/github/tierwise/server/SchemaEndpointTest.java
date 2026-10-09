package io.github.tierwise.server;

import static io.github.tierwise.server.Fixtures.READER;
import static io.github.tierwise.server.Fixtures.bundle;
import static io.github.tierwise.server.Fixtures.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

/** GET /v1/schema: keys, features, facts and policies of the latest bundle. */
class SchemaEndpointTest extends ServerTest {

  private final byte[] commerce = bundle("commerce.json");

  @SuppressWarnings("unchecked")
  private Map<String, Object> content(byte[] bundle) {
    Map<String, Object> envelope = json.readValue(bundle, new TypeReference<Map<String, Object>>() { });
    return (Map<String, Object>) envelope.get("content");
  }

  @Test
  void theSchemaListsKeysFeaturesFactsAndPolicies() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    Map<String, Object> schema = object(get("/v1/schema"), READER, 200);
    Map<String, Object> content = content(commerce);

    assertEquals(Fixtures.version(commerce), schema.get("bundleVersion"));
    assertEquals("1", schema.get("bundleFormatVersion"));
    assertEquals("0.1", schema.get("specVersion"));
    assertEquals(content.get("keys"), schema.get("keys"));
    assertEquals(content.get("facts"), schema.get("facts"));
    assertEquals(content.get("entities"), schema.get("entities"));
    assertEquals(content.get("hierarchy"), schema.get("hierarchy"));
  }

  @Test
  @SuppressWarnings("unchecked")
  void featuresListTheirPolicyNamesAndPoliciesListTheirRules() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    Map<String, Object> schema = object(get("/v1/schema"), READER, 200);

    Map<String, Object> features = (Map<String, Object>) schema.get("features");
    assertEquals(List.of("checkout.invoice_release", "checkout.order_submission"), List.copyOf(features.keySet()));
    Map<String, Object> submission = (Map<String, Object>) features.get("checkout.order_submission");
    assertEquals("order", submission.get("decidedPer"));
    assertEquals(Map.of("enum", List.of("ALLOW", "DENY")), submission.get("returns"));
    assertEquals("DENY", submission.get("ifFactMissing"));
    assertEquals(List.of("allow_all", "credit_check"), submission.get("policies"));
    assertFalse(submission.containsKey("bindings"));

    List<Map<String, Object>> policies = (List<Map<String, Object>>) schema.get("policies");
    assertEquals(3, policies.size());
    Map<String, Object> creditCheck = policies.stream()
        .filter(policy -> policy.get("policy").equals("credit_check")).findFirst().orElseThrow();
    assertEquals("checkout.order_submission", creditCheck.get("feature"));
    assertEquals(List.of("customer.active", "customer.credit_hold", "item.stock_type"), creditCheck.get("requiredFacts"));
    assertEquals(2, ((List<?>) creditCheck.get("rules")).size());
    assertEquals("DENY", creditCheck.get("default"));
  }

  @Test
  void theSchemaHasNoValuesAndNoBindings() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    String body = mvc.perform(get("/v1/schema").with(READER)).andReturn().getResponse().getContentAsString();
    Map<String, Object> schema = json.readValue(body, new TypeReference<Map<String, Object>>() { });
    assertFalse(schema.containsKey("values"));
    assertTrue(!body.contains("\"bindings\""));
    // A value of the bundle ("web" is a node of the channel catalog; 100 is a value) is not exposed as a value.
    assertFalse(body.contains("\"settings\":{\"allowed_on_credit_hold\":[\"SPECIAL_ORDER\""));
  }

  @Test
  void theSchemaFollowsThePromotedVersion() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    byte[] retail = bundle("retail-pos.json");
    publishOk(retail, record("2026-10-09T09:00:00Z"));
    mvc.perform(get("/v1/schema").with(READER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bundleVersion").value(Fixtures.version(retail)));
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .post("/v1/bundles/" + Fixtures.version(commerce) + "/promote").with(Fixtures.PROMOTER)).andExpect(status().isOk());
    mvc.perform(get("/v1/schema").with(READER))
        .andExpect(jsonPath("$.bundleVersion").value(Fixtures.version(commerce)));
  }

  @Test
  void theSchemaOfEveryConformanceBundleIsServed() throws Exception {
    Path directory = Path.of(System.getProperty("tierwise.spec.dir", "../../spec"), "conformance", "bundles");
    List<String> names;
    try (var files = Files.list(directory)) {
      names = files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".json")).sorted().toList();
    }
    assertTrue(names.size() >= 20);
    for (String name : names) {
      Fixtures.resetStore();
      byte[] bytes = bundle(name);
      publishOk(bytes, record("2026-10-09T08:00:00Z"));
      Map<String, Object> schema = object(get("/v1/schema"), READER, 200);
      assertEquals(Fixtures.version(bytes), schema.get("bundleVersion"), name);
      assertEquals(content(bytes).get("keys"), schema.get("keys"), name);
    }
  }

  @Test
  void noBundleMeans404() throws Exception {
    mvc.perform(get("/v1/schema").with(READER))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_BUNDLE_NOT_FOUND"));
  }
}
