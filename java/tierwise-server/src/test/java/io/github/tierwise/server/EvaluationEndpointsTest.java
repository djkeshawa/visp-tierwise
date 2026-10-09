package io.github.tierwise.server;

import static io.github.tierwise.server.Fixtures.PUBLISHER;
import static io.github.tierwise.server.Fixtures.PROMOTER;
import static io.github.tierwise.server.Fixtures.READER;
import static io.github.tierwise.server.Fixtures.bundle;
import static io.github.tierwise.server.Fixtures.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.tierwise.core.Context;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.Tierwise;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.core.type.TypeReference;

/**
 * POST /v1/resolve and POST /v1/decide: the results equal the results of the core evaluating the same bundle
 * in-process (requirements section 9, section 15). The reference is built here with the core's public API.
 */
class EvaluationEndpointsTest extends ServerTest {

  private final byte[] commerce = bundle("commerce.json");
  private Evaluator reference;

  @BeforeEach
  void publishCommerceAndBuildTheReference() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    // The server has no provider for the service-sourced levels, so every call to them fails in-process too.
    reference = Tierwise.evaluator(Tierwise.loadBundle(commerce), EvaluatorOptions.builder()
        .provider("account", node -> { throw new IllegalStateException("no provider"); }, Duration.ofMillis(200))
        .provider("user", node -> { throw new IllegalStateException("no provider"); }, Duration.ofMillis(200))
        .build());
  }

  static List<Map<String, Object>> resolveCases() {
    List<String> everyKey = List.of("checkout.pickup.lead_hours", "checkout.pickup.window",
        "checkout.shipping.allowed_methods", "checkout.order.allowed_max_lines", "checkout.container_order.allowed");
    return List.of(
        Map.of("context", Map.of(), "keys", everyKey),
        Map.of("context", Map.of("levels", Map.of("division", "south", "channel", "web"),
            "axes", Map.of("seller", "acme")), "keys", everyKey),
        Map.of("context", Map.of("levels", Map.of("division", "north")), "keys", everyKey),
        Map.of("context", Map.of("levels", Map.of("site", "site_001", "division", "south")),
            "keys", List.of("checkout.order.allowed_max_lines", "checkout.pickup.window")),
        // site_999 is not in the closed catalog of a level that ignores unknown nodes: a skipped scope.
        Map.of("context", Map.of("levels", Map.of("site", "site_999", "division", "south")),
            "keys", List.of("checkout.order.allowed_max_lines")),
        // Service-sourced levels without a provider: UNAVAILABLE contributions.
        Map.of("context", Map.of("levels", Map.of("division", "south", "account", "acct-1")),
            "keys", List.of("checkout.shipping.allowed_methods", "checkout.container_order.allowed",
                "checkout.pickup.lead_hours")),
        Map.of("context", Map.of("levels", Map.of("account", "acct-1", "user", "user-7"),
            "axes", Map.of("seller", "acme")),
            "keys", List.of("checkout.shipping.allowed_methods", "checkout.container_order.allowed")),
        Map.of("keys", List.of("checkout.pickup.lead_hours")),
        Map.of("context", Map.of(), "keys", List.of()),
        Map.of("context", Map.of(), "keys", List.of("checkout.pickup.lead_hours", "checkout.pickup.lead_hours")));
  }

  @ParameterizedTest
  @MethodSource("resolveCases")
  void resolveEqualsTheInProcessResult(Map<String, Object> request) throws Exception {
    Map<String, Object> body = object(post("/v1/resolve").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(request)), READER, 200);

    Context context = request.containsKey("context") ? Context.fromJson(request.get("context")) : Context.empty();
    List<Object> expected = new ArrayList<>();
    for (Object key : (List<?>) request.get("keys")) {
      expected.add(normalised(reference.resolve(context, (String) key).toJson()));
    }
    assertEquals(Fixtures.version(commerce), body.get("bundleVersion"));
    assertEquals(expected, body.get("results"));
  }

  @Test
  void resolveReportsUnavailableForAServiceLevelWithoutProvider() throws Exception {
    mvc.perform(resolve("{\"context\":{\"levels\":{\"account\":\"acct-1\"}},\"keys\":[\"checkout.shipping.allowed_methods\"]}")
            .with(READER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results[0].contributions[?(@.name == 'account')].effect").value("UNAVAILABLE"))
        .andExpect(jsonPath("$.results[0].contributions[?(@.name == 'account')].onSourceFailure").value("deny"));
  }

  @Test
  void resolveListsSkippedScopes() throws Exception {
    mvc.perform(resolve("{\"context\":{\"levels\":{\"site\":\"site_999\"}},\"keys\":[\"checkout.order.allowed_max_lines\"]}")
            .with(READER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.results[0].skippedScopes[0].name").value("site"))
        .andExpect(jsonPath("$.results[0].skippedScopes[0].reason").value("UNKNOWN_NODE"));
  }

  static List<Map<String, Object>> decideCases() {
    Map<String, Object> south = Map.of("levels", Map.of("division", "south"));
    Map<String, Object> north = Map.of("levels", Map.of("division", "north"));
    return List.of(
        decide(south, "checkout.order_submission", Map.of(
            "facts", Map.of("customer.active", true, "customer.credit_hold", false),
            "children", Map.of("item", List.of(Map.of("item.stock_type", "STOCKED"))))),
        decide(south, "checkout.order_submission", Map.of(
            "facts", Map.of("customer.active", true, "customer.credit_hold", true),
            "children", Map.of("item", List.of(Map.of("item.stock_type", "STOCKED"),
                Map.of("item.stock_type", "SPECIAL_ORDER"))))),
        decide(south, "checkout.order_submission", Map.of(
            "facts", Map.of("customer.active", true, "customer.credit_hold", true),
            "children", Map.of("item", List.of(Map.of("item.stock_type", "DROP_SHIP"))))),
        decide(north, "checkout.order_submission", Map.of("facts", Map.of())),
        // A missing fact: the feature's if_fact_missing effect with reason MISSING_FACT.
        decide(south, "checkout.order_submission", Map.of("facts", Map.of("customer.active", true))),
        decide(Map.of(), "checkout.order_submission", Map.of(
            "facts", Map.of("customer.active", false, "customer.credit_hold", false),
            "children", Map.of("item", List.of()))),
        decide(south, "checkout.invoice_release", Map.of(
            "children", Map.of("order", List.of(Map.of("order.payment_state", "PAID"),
                Map.of("order.payment_state", "PENDING"))))),
        decide(south, "checkout.invoice_release", Map.of(
            "children", Map.of("order", List.of(Map.of("order.payment_state", "FAILED"))))),
        decide(Map.of("levels", Map.of("division", "west")), "checkout.invoice_release", Map.of(
            "children", Map.of("order", List.of(Map.of("order.payment_state", "PENDING"))))));
  }

  private static Map<String, Object> decide(Map<String, Object> context, String feature, Map<String, Object> input) {
    return Map.of("context", context, "feature", feature, "input", input);
  }

  @ParameterizedTest
  @MethodSource("decideCases")
  void decideEqualsTheInProcessResult(Map<String, Object> request) throws Exception {
    Map<String, Object> body = object(post("/v1/decide").contentType(MediaType.APPLICATION_JSON)
        .content(json.writeValueAsBytes(request)), READER, 200);
    Map<String, Object> expected = normalised(reference.decide(Context.fromJson(request.get("context")),
        (String) request.get("feature"), FactSet.fromJson(request.get("input"))).toJson());
    assertEquals(expected, body);
  }

  @Test
  void decideReturnsTheReasonAndTheTrace() throws Exception {
    mvc.perform(post("/v1/decide").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(decide(
            Map.of("levels", Map.of("division", "south")), "checkout.order_submission", Map.of(
                "facts", Map.of("customer.active", true, "customer.credit_hold", false),
                "children", Map.of("item", List.of(Map.of("item.stock_type", "STOCKED"))))))).with(READER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.effect").value("ALLOW"))
        .andExpect(jsonPath("$.reason.type").value("RULE"))
        .andExpect(jsonPath("$.reason.id").value("good-credit"))
        .andExpect(jsonPath("$.trace[0].id").value("good-credit"))
        .andExpect(jsonPath("$.trace[0].matched").value(true))
        .andExpect(jsonPath("$.bundleVersion").value(Fixtures.version(commerce)));
  }

  @Test
  void decideWithoutInputUsesAnEmptyFactSet() throws Exception {
    Map<String, Object> body = object(post("/v1/decide").contentType(MediaType.APPLICATION_JSON)
        .content("{\"feature\":\"checkout.order_submission\"}"), READER, 200);
    Map<String, Object> expected = normalised(reference.decide(Context.empty(), "checkout.order_submission",
        FactSet.empty()).toJson());
    assertEquals(expected, body);
  }

  @Test
  void aPromoteChangesWhatTheEvaluationEndpointsUse() throws Exception {
    byte[] retail = bundle("retail-pos.json");
    publishOk(retail, record("2026-10-09T09:00:00Z"));
    String retailVersion = Fixtures.version(retail);
    // The latest is retail-pos now; the commerce key is unknown there.
    mvc.perform(resolve("{\"keys\":[\"checkout.pickup.lead_hours\"]}").with(READER))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_UNKNOWN_KEY"));
    mvc.perform(post("/v1/bundles/" + Fixtures.version(commerce) + "/promote").with(PROMOTER)).andExpect(status().isOk());
    mvc.perform(resolve("{\"keys\":[\"checkout.pickup.lead_hours\"]}").with(READER))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bundleVersion").value(Fixtures.version(commerce)));
    mvc.perform(post("/v1/bundles/" + retailVersion + "/promote").with(PROMOTER)).andExpect(status().isOk());
    mvc.perform(resolve("{\"keys\":[\"checkout.pickup.lead_hours\"]}").with(READER))
        .andExpect(status().isNotFound());
  }

  @Test
  void anUnknownKeyIs404() throws Exception {
    mvc.perform(resolve("{\"keys\":[\"checkout.pickup.lead_hours\",\"no.such.key\"]}").with(READER))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_UNKNOWN_KEY"))
        .andExpect(jsonPath("$.message").isString());
  }

  @Test
  void anUnknownFeatureIs404() throws Exception {
    mvc.perform(post("/v1/decide").contentType(MediaType.APPLICATION_JSON).content("{\"feature\":\"no.such\"}")
            .with(READER))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_UNKNOWN_FEATURE"));
  }

  @Test
  void anUnknownLevelInTheContextIs400() throws Exception {
    mvc.perform(resolve("{\"context\":{\"levels\":{\"no_level\":\"x\"}},\"keys\":[\"checkout.pickup.lead_hours\"]}")
            .with(READER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_UNKNOWN_SCOPE"));
  }

  @Test
  void anUnknownNodeInTheContextIs400() throws Exception {
    mvc.perform(resolve("{\"context\":{\"levels\":{\"division\":\"nowhere\"}},\"keys\":[\"checkout.pickup.lead_hours\"]}")
            .with(READER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_UNKNOWN_NODE"));
  }

  @Test
  void aContextThatIsNotAnObjectIs400() throws Exception {
    mvc.perform(resolve("{\"context\":[],\"keys\":[\"checkout.pickup.lead_hours\"]}").with(READER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_CONTEXT_INVALID"));
    mvc.perform(resolve("{\"context\":{\"levels\":{\"division\":5}},\"keys\":[\"checkout.pickup.lead_hours\"]}")
            .with(READER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_CONTEXT_INVALID"));
  }

  @Test
  void aFactOfTheWrongTypeIs400() throws Exception {
    mvc.perform(post("/v1/decide").contentType(MediaType.APPLICATION_JSON).content(
            "{\"context\":{\"levels\":{\"division\":\"south\"}},\"feature\":\"checkout.order_submission\","
                + "\"input\":{\"facts\":{\"customer.active\":\"true\",\"customer.credit_hold\":false}}}").with(READER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_FACTS_INVALID"));
  }

  @Test
  void aFactSetThatIsNotAnObjectIs400() throws Exception {
    mvc.perform(post("/v1/decide").contentType(MediaType.APPLICATION_JSON)
            .content("{\"feature\":\"checkout.order_submission\",\"input\":[]}").with(READER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_FACTS_INVALID"));
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {
      "{}",
      "{\"keys\":\"checkout.pickup.lead_hours\"}",
      "{\"keys\":[1]}",
      "{\"keys\":[null]}",
      "{\"keys\":[],\"extra\":1}",
      "{\"key\":\"checkout.pickup.lead_hours\"}",
      "[]",
      "not json",
      ""})
  void aMalformedResolveRequestIs400(String body) throws Exception {
    mvc.perform(resolve(body).with(READER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_REQUEST_INVALID"));
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {
      "{}",
      "{\"feature\":1}",
      "{\"feature\":null}",
      "{\"feature\":\"checkout.order_submission\",\"extra\":1}",
      "{\"feature\":\"checkout.order_submission\",\"facts\":{}}",
      "[]"})
  void aMalformedDecideRequestIs400(String body) throws Exception {
    mvc.perform(post("/v1/decide").contentType(MediaType.APPLICATION_JSON).content(body).with(READER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_REQUEST_INVALID"));
  }

  @Test
  void aRequestWithTooManyKeysIs400() throws Exception {
    StringBuilder keys = new StringBuilder();
    for (int i = 0; i < 101; i++) {
      keys.append(i == 0 ? "" : ",").append("\"checkout.pickup.lead_hours\"");
    }
    mvc.perform(resolve("{\"keys\":[" + keys + "]}").with(READER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("E_REQUEST_INVALID"));
  }

  @Test
  void aBodyThatIsNotJsonContentIs415() throws Exception {
    mvc.perform(post("/v1/resolve").contentType(MediaType.TEXT_PLAIN).content("{}").with(READER))
        .andExpect(status().isUnsupportedMediaType())
        .andExpect(jsonPath("$.code").value("E_UNSUPPORTED_MEDIA_TYPE"));
  }

  @Test
  void evaluationBeforeAnyPublicationIs404() throws Exception {
    Fixtures.resetStore();
    mvc.perform(resolve("{\"keys\":[]}").with(READER))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_BUNDLE_NOT_FOUND"));
    mvc.perform(post("/v1/decide").contentType(MediaType.APPLICATION_JSON).content("{\"feature\":\"x\"}")
            .with(READER))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("E_BUNDLE_NOT_FOUND"));
  }

  @Test
  void errorsAreJsonWithCodeAndMessageOnly() throws Exception {
    Map<String, Object> error = object(resolve("{\"keys\":[\"no.such.key\"]}"), READER, 404);
    assertEquals(List.of("code", "message"), List.copyOf(error.keySet()));
    assertTrue(((String) error.get("message")).length() > 0);
    assertTrue(!((String) error.get("message")).startsWith("E_UNKNOWN_KEY"));
  }

  @Test
  void aPublisherTokenIsEnoughToEvaluateWhenReadsNeedOnlyAToken() throws Exception {
    mvc.perform(resolve("{\"keys\":[\"checkout.pickup.lead_hours\"]}").with(PUBLISHER)).andExpect(status().isOk());
  }

  private static MockHttpServletRequestBuilder resolve(String body) {
    return post("/v1/resolve").contentType(MediaType.APPLICATION_JSON).content(body.getBytes(StandardCharsets.UTF_8));
  }

  /** Round-trips a core result through the server's JSON mapper, as the HTTP response does. */
  private Map<String, Object> normalised(Map<String, Object> result) {
    return json.readValue(json.writeValueAsString(result), new TypeReference<Map<String, Object>>() { });
  }
}
