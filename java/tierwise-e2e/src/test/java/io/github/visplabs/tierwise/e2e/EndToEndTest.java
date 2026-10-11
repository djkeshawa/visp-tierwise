package io.github.visplabs.tierwise.e2e;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.visplabs.tierwise.core.LevelValueProvider;
import io.github.visplabs.tierwise.core.Tierwise;
import io.github.visplabs.tierwise.server.TierwiseServerApplication;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The end-to-end test of requirements section 12.
 *
 * <p>Compile the sample project and a modified copy with the core compiler. Publish them, one after the other, to a
 * server running in this process on a random port. A Java service (the Spring Boot starter) and a Node service (a
 * child process running the built {@code @visporg/node}) read the server's latest bundle on a short refresh interval.
 * Both must pick up each new version and answer every case in {@code cases.json} with identical JSON. Then the
 * previous version is promoted, and both must roll back and agree again. The server's own {@code /v1/resolve} and
 * {@code /v1/decide} answer the same cases and must match too (acceptance criterion of requirements section 15).
 *
 * <p>The Node part is skipped, with the reason on the console, when {@code node} is not on the PATH or
 * {@code js/node/dist} is not built.
 */
@SpringBootTest(classes = TierwiseServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        // The starter is on the class path (for the Java service) and would otherwise start inside the server.
        "tierwise.enabled=false",
        "tierwise.server.security.reads=open",
        "spring.main.banner-mode=off",
        "logging.level.root=warn"})
@Import(EndToEndTest.ServerBeans.class)
class EndToEndTest {

  private static final Duration REFRESH_INTERVAL = Duration.ofMillis(200);
  private static final Duration PICKUP_DEADLINE = Duration.ofSeconds(15);
  private static final Path STORE = createStore();

  /** The server's decoder (tokens {@code r.<role>}) and the providers of the service-sourced levels. */
  @TestConfiguration(proxyBeanMethods = false)
  static class ServerBeans {

    @Bean
    JwtDecoder testDecoder() {
      return token -> {
        if (!token.equals("r") && !token.startsWith("r.")) {
          throw new BadJwtException("unknown token");
        }
        List<String> roles = token.length() == 1 ? List.of() : List.of(token.substring(2).split("\\."));
        return Jwt.withTokenValue(token).header("alg", "none").subject("e2e")
            .issuedAt(Instant.parse("2026-01-01T00:00:00Z")).expiresAt(Instant.parse("2099-01-01T00:00:00Z"))
            .claim("roles", roles).build();
      };
    }

    /** The bean name is the level name. */
    @Bean
    LevelValueProvider account() {
      return TestProviders::account;
    }

    @Bean
    LevelValueProvider user() {
      return TestProviders::user;
    }
  }

  @DynamicPropertySource
  static void store(DynamicPropertyRegistry registry) {
    registry.add("tierwise.server.filesystem.root", STORE::toString);
  }

  @AfterAll
  static void deleteStore() throws IOException {
    try (Stream<Path> walk = Files.walk(STORE)) {
      walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
  }

  @Value("${local.server.port}")
  private int port;

  @TempDir
  private Path temp;

  private final List<JsonNode> cases = Cases.load();
  private final Map<String, Object> report = new LinkedHashMap<>();
  private final List<Object> transitions = new ArrayList<>();

  private static Path createStore() {
    try {
      return Files.createTempDirectory("tierwise-e2e-store");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The repository root: the parent of the spec directory that the build passes in. */
  private static Path root() {
    return Path.of(System.getProperty("tierwise.spec.dir", "../../spec")).toAbsolutePath().normalize().getParent();
  }

  @Test
  void compilePublishPickUpAndRollBack() {
    Path root = root();
    Path sample = root.resolve("samples/commerce");

    // 1. Compile the sample and a modified copy with the core compiler.
    byte[] bundle1 = Samples.compile(sample);
    byte[] bundle2 = Samples.compile(Samples.modifiedCopy(sample, temp.resolve("commerce-modified")));
    assertArrayEquals(bundle1, Samples.compile(sample), "the same sources give the same bytes");
    String v1 = Tierwise.loadBundle(bundle1).bundleVersion();
    String v2 = Tierwise.loadBundle(bundle2).bundleVersion();
    assertNotEquals(v1, v2, "the modified project must compile to another version");
    report.put("version1", v1);
    report.put("version2", v2);
    report.put("refreshIntervalMs", REFRESH_INTERVAL.toMillis());
    log("compiled v1 " + v1 + " (" + bundle1.length + " bytes) and v2 " + v2 + " (" + bundle2.length + " bytes)");

    // 2. Publish v1. Only a token with the publish role may.
    ServerClient server = new ServerClient("http://localhost:" + port);
    assertEquals(401, server.publish(bundle1, null).statusCode(), "publishing without a token");
    assertEquals(403, server.publish(bundle1, "Bearer r").statusCode(), "publishing without the publish role");
    HttpResponse<byte[]> published1 = server.publish(bundle1, ServerClient.PUBLISHER);
    assertEquals(201, published1.statusCode(), body(published1));
    assertEquals(v1, server.latestVersion());

    // 3. Start the two services. Both load v1 and answer the cases identically, and as the sample's rules say.
    Optional<String> nodeUnavailable = NodeClient.unavailableReason(root);
    nodeUnavailable.ifPresent(reason -> log("ASSUMPTION: the Node part is skipped: " + reason));
    report.put("nodeSkipped", nodeUnavailable.orElse(null));
    try (JavaClient java = JavaClient.start(server.baseUrl(), REFRESH_INTERVAL);
        NodeClient node = nodeUnavailable.isEmpty() ? NodeClient.start(root, server.baseUrl() + "/v1/bundles/latest", REFRESH_INTERVAL) : null) {
      assertEquals(v1, java.bundleVersion(), "the Java service starts on v1");
      if (node != null) {
        assertEquals(v1, node.bundleVersion(), "the Node service starts on v1");
      }
      Map<String, JsonNode> before = agree("v1", java, node, server);
      expectVersion1(before, v1);

      // 4. Publish v2: it becomes the latest. Both services pick it up on their own timer.
      HttpResponse<byte[]> published2 = server.publish(bundle2, ServerClient.PUBLISHER);
      assertEquals(201, published2.statusCode(), body(published2));
      pickUp("publish v2", v2, java, node, server);
      Map<String, JsonNode> after = agree("v2", java, node, server);
      expectVersion2(before, after, v2);

      // 5. Promote v1 again: only a token with the promote role may. Both services roll back.
      assertEquals(403, server.promote(v1, ServerClient.PUBLISHER).statusCode(), "promoting with the publish role");
      HttpResponse<byte[]> promoted = server.promote(v1, ServerClient.PROMOTER);
      assertEquals(200, promoted.statusCode(), body(promoted));
      pickUp("promote v1", v1, java, node, server);
      Map<String, JsonNode> rolledBack = agree("v1 again", java, node, server);
      assertEquals(before, rolledBack, "after the rollback every result is what it was on v1");
    }

    report.put("transitions", transitions);
    report.put("casesComparedPerStep", cases.size());
    writeReport();
  }

  @Test
  void theNodeServiceCanRun() {
    Optional<String> reason = NodeClient.unavailableReason(root());
    assumeTrue(reason.isEmpty(), () -> "Node part skipped: " + reason.orElse(""));
  }

  // -- steps ----------------------------------------------------------------------------------------------------

  /**
   * Waits until both services serve {@code version}, and records how long each took after the publish or promote
   * returned. The services refresh on their own timers; nothing here asks them to refresh.
   */
  private void pickUp(String step, String version, JavaClient java, NodeClient node, ServerClient server) {
    assertEquals(version, server.latestVersion(), step + ": the server's latest");
    long started = System.nanoTime();
    if (node != null) {
      node.sendAwait(version, PICKUP_DEADLINE);
    }
    long deadline = started + PICKUP_DEADLINE.toNanos();
    while (!version.equals(java.bundleVersion())) {
      if (System.nanoTime() > deadline) {
        fail(step + ": the Java service still serves " + java.bundleVersion() + " after " + PICKUP_DEADLINE);
      }
      sleep(5);
    }
    long javaMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
    Map<String, Object> timing = new LinkedHashMap<>();
    timing.put("step", step);
    timing.put("version", version);
    timing.put("javaPickedUpAfterMs", javaMs);
    if (node != null) {
      JsonNode answer = node.receive(PICKUP_DEADLINE.plusSeconds(5));
      assertTrue(answer.path("ok").asBoolean(), step + ": the Node service did not pick up " + version + ": " + answer);
      assertEquals(version, answer.path("bundleVersion").asString());
      timing.put("nodePickedUpAfterMs", answer.path("waitedMs").asLong());
    }
    transitions.add(timing);
    log(step + ": " + timing);
    assertTrue(javaMs < 5_000, step + ": the Java service took " + javaMs + " ms with a " + REFRESH_INTERVAL.toMillis() + " ms interval");
  }

  /** Runs every case on every service and requires identical JSON. Returns the Java service's outcomes. */
  private Map<String, JsonNode> agree(String label, JavaClient java, NodeClient node, ServerClient server) {
    Map<String, JsonNode> fromJava = java.evaluate(cases);
    assertEquals(cases.size(), fromJava.size());
    assertSame(label + ": Java service against the server's /resolve and /decide", fromJava, server.evaluate(cases));
    if (node != null) {
      assertSame(label + ": Java service against Node service", fromJava, node.evaluate(cases));
    }
    log(label + ": " + cases.size() + " cases, identical" + (node == null ? " (Java service and server only)" : " on Java, Node and the server"));
    return fromJava;
  }

  private static void assertSame(String what, Map<String, JsonNode> expected, Map<String, JsonNode> actual) {
    assertEquals(expected.keySet(), actual.keySet(), what + ": the case ids");
    for (Map.Entry<String, JsonNode> entry : expected.entrySet()) {
      JsonNode other = actual.get(entry.getKey());
      if (!entry.getValue().equals(other)) {
        fail(what + ": case " + entry.getKey() + " differs\n--- first\n" + Json.pretty(entry.getValue())
            + "\n--- second\n" + Json.pretty(other));
      }
    }
  }

  // -- what the sample means ---------------------------------------------------------------------------------------

  /** Results that the sample's README derives by hand, so that "identical" cannot mean "identically wrong". */
  private static void expectVersion1(Map<String, JsonNode> results, String version) {
    for (JsonNode outcome : results.values()) {
      if (outcome.has("result")) {
        assertEquals(version, outcome.get("result").get("bundleVersion").asString(), outcome.get("id").asString());
      }
    }
    assertEquals(Json.parse("[\"GROUND\",\"PICKUP\"]"), value(results, "r01-shipping-root"));
    assertEquals(Json.parse("[\"GROUND\"]"), value(results, "r02-shipping-web-south"));
    assertEquals(Json.parse("false"), value(results, "r03-container-north-veto"));
    assertEquals(Json.parse("true"), value(results, "r04-container-south-identity"));
    assertEquals(Json.parse("25"), value(results, "r05-max-lines-south-site"));
    assertEquals(Json.parse("2"), value(results, "r06-lead-hours-root"));
    assertEquals(Json.parse("6"), value(results, "r07-lead-hours-south"));
    assertEquals(Json.parse("8"), value(results, "r08-lead-hours-south-acme"));
    assertEquals(Json.parse("{\"open\":\"09:00\",\"close\":\"18:00\"}"), value(results, "r09-window-root"));
    assertEquals(Json.parse("{\"open\":\"09:00\",\"close\":\"16:00\"}"), value(results, "r10-window-north-site"));
    assertEquals(1, results.get("r11-lead-hours-skipped-site").get("result").get("skippedScopes").size(), "skipped site_099");
    assertEquals(Json.parse("[\"PICKUP\"]"), value(results, "r12-shipping-account-narrows"));
    assertEquals("UNAVAILABLE", results.get("r13-shipping-account-provider-fails").get("result").get("contributions")
        .get(results.get("r13-shipping-account-provider-fails").get("result").get("contributions").size() - 1)
        .get("effect").asString(), "the failing provider is reported");
    assertEquals(Json.parse("false"), value(results, "r14-container-account-veto"));
    assertEquals("E_UNKNOWN_NODE", code(results, "r15-error-unknown-node"));
    assertEquals("E_UNKNOWN_KEY", code(results, "r16-error-unknown-key"));
    assertEquals("DENY", effect(results, "d01-mixed-basket-on-credit-hold"));
    assertEquals("ALLOW", effect(results, "d02-allowed-stock-on-credit-hold"));
    assertEquals("ALLOW", effect(results, "d03-north-allows-all"));
    assertEquals("ALLOW", effect(results, "d04-web-good-credit"));
    assertEquals("DENY", effect(results, "d05-missing-fact"));
    assertEquals("MISSING_FACT", results.get("d05-missing-fact").get("result").get("reason").get("type").asString());
    assertEquals("HOLD", effect(results, "d06-invoice-pending-holds"));
    assertEquals("RELEASE", effect(results, "d07-invoice-south-releases"));
    assertEquals("E_FACTS_INVALID", code(results, "d08-error-boolean-as-string"));
    assertEquals("E_UNKNOWN_FEATURE", code(results, "d09-error-unknown-feature"));
  }

  /** v2 changes one value and one binding, and exactly the cases that depend on them. */
  private static void expectVersion2(Map<String, JsonNode> v1, Map<String, JsonNode> v2, String version) {
    for (JsonNode outcome : v2.values()) {
      if (outcome.has("result")) {
        assertEquals(version, outcome.get("result").get("bundleVersion").asString(), outcome.get("id").asString());
      }
    }
    Set<String> changed = new TreeSet<>();
    for (String id : v1.keySet()) {
      if (!withoutVersion(v1.get(id)).equals(withoutVersion(v2.get(id)))) {
        changed.add(id);
      }
    }
    assertEquals(Set.of("r07-lead-hours-south", "r08-lead-hours-south-acme", "r11-lead-hours-skipped-site",
        "d01-mixed-basket-on-credit-hold"), changed, "the cases whose results depend on the changed value and binding");
    assertEquals(Json.parse("12"), value(v2, "r07-lead-hours-south"));
    assertEquals(Json.parse("12"), value(v2, "r08-lead-hours-south-acme"));
    assertEquals("ALLOW", effect(v2, "d01-mixed-basket-on-credit-hold"));
    assertEquals("credit-hold-allowed-stock",
        v2.get("d01-mixed-basket-on-credit-hold").get("result").get("reason").get("id").asString());
  }

  private static JsonNode withoutVersion(JsonNode outcome) {
    ObjectNode copy = (ObjectNode) outcome.deepCopy();
    if (copy.has("result")) {
      ((ObjectNode) copy.get("result")).remove("bundleVersion");
    }
    return copy;
  }

  private static JsonNode value(Map<String, JsonNode> results, String id) {
    return results.get(id).get("result").get("value");
  }

  private static String effect(Map<String, JsonNode> results, String id) {
    return results.get(id).get("result").get("effect").asString();
  }

  private static String code(Map<String, JsonNode> results, String id) {
    return results.get(id).get("error").get("code").asString();
  }

  // -- plumbing ----------------------------------------------------------------------------------------------------

  private static String body(HttpResponse<byte[]> response) {
    return response.statusCode() + " " + new String(response.body(), StandardCharsets.UTF_8);
  }

  private static void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new AssertionError("interrupted", e);
    }
  }

  private static void log(String message) {
    System.out.println("[e2e] " + message);
  }

  private void writeReport() {
    String json = Json.pretty(Json.tree(report));
    log("report " + json.replaceAll("\\s+", " "));
    String target = System.getProperty("tierwise.e2e.report");
    if (target != null && !target.isBlank()) {
      try {
        Path file = Path.of(target);
        Files.createDirectories(file.toAbsolutePath().getParent());
        Files.writeString(file, json + "\n", StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
