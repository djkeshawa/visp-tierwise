package io.github.visplabs.tierwise.server.service;

import static io.github.visplabs.tierwise.server.Fixtures.READER;
import static io.github.visplabs.tierwise.server.Fixtures.bundle;
import static io.github.visplabs.tierwise.server.Fixtures.record;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.EvaluationEvent;
import io.github.visplabs.tierwise.core.EvaluationListener;
import io.github.visplabs.tierwise.core.Evaluator;
import io.github.visplabs.tierwise.core.EvaluatorOptions;
import io.github.visplabs.tierwise.core.LevelValueProvider;
import io.github.visplabs.tierwise.core.Tierwise;
import io.github.visplabs.tierwise.server.Fixtures;
import io.github.visplabs.tierwise.server.ServerTest;
import io.github.visplabs.tierwise.server.TierwiseServerApplication;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import tools.jackson.core.type.TypeReference;

/**
 * The server uses {@code LevelValueProvider} beans for service-sourced levels (the bean name is the level),
 * calls each provider once per node and request, and returns the core's results.
 */
@SpringBootTest(classes = TierwiseServerApplication.class, properties = "tierwise.server.provider-timeout=150ms")
@Import(ProviderTest.Providers.class)
class ProviderTest extends ServerTest {

  static final AtomicInteger ACCOUNT_CALLS = new AtomicInteger();
  static final List<EvaluationEvent> EVENTS = new CopyOnWriteArrayList<>();

  /** The behaviour of the account provider, shared with the in-process reference. */
  static Map<String, Object> account(String node) throws Exception {
    ACCOUNT_CALLS.incrementAndGet();
    return switch (node) {
      case "acct-fail" -> throw new IllegalStateException("account service is down");
      case "acct-slow" -> {
        Thread.sleep(2_000);
        yield Map.of();
      }
      case "acct-stubborn" -> {
        sleepWithoutInterruption(220);
        yield Map.of("checkout.shipping.allowed_methods", List.of("GROUND"), "checkout.container_order.allowed", true);
      }
      case "acct-invalid" -> Map.of("checkout.shipping.allowed_methods", "not a set");
      case "acct-partial" -> Map.of("checkout.container_order.allowed", false);
      default -> Map.of("checkout.shipping.allowed_methods", List.of("GROUND", "NEXT_DAY"),
          "checkout.container_order.allowed", true);
    };
  }

  /** Sleeps for the given time even when the thread is interrupted, like a blocking call that ignores it. */
  private static void sleepWithoutInterruption(long millis) {
    long end = System.nanoTime() + Duration.ofMillis(millis).toNanos();
    boolean interrupted = false;
    long left;
    while ((left = end - System.nanoTime()) > 0) {
      try {
        Thread.sleep(Duration.ofNanos(left).toMillis() + 1);
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }

  /** Test beans: a provider for the service-sourced level {@code account} and one that names no such level. */
  @TestConfiguration
  static class Providers {
    @Bean("account")
    LevelValueProvider accountProvider() {
      return ProviderTest::account;
    }

    /** Not a service-sourced level: ignored with a warning. */
    @Bean("division")
    LevelValueProvider ignoredProvider() {
      return node -> {
        throw new AssertionError("must never be called");
      };
    }

    @Bean
    EvaluationListener listener() {
      return EVENTS::add;
    }
  }

  private final byte[] commerce = bundle("commerce.json");
  private Evaluator reference;

  @BeforeEach
  void publishAndReset() throws Exception {
    publishOk(commerce, record("2026-10-09T08:00:00Z"));
    reference = Tierwise.evaluator(Tierwise.loadBundle(commerce), EvaluatorOptions.builder()
        .provider("account", ProviderTest::account, Duration.ofMillis(150))
        .provider("user", node -> { throw new IllegalStateException("no provider"); }, Duration.ofMillis(150))
        .build());
    ACCOUNT_CALLS.set(0);
    EVENTS.clear();
  }

  private Map<String, Object> resolve(String account, String... keys) throws Exception {
    Map<String, Object> request = Map.of("context", Map.of("levels", Map.of("division", "south", "account", account)),
        "keys", List.of(keys));
    return object(post("/v1/resolve").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)),
        READER, 200);
  }

  private List<Object> expected(String account, String... keys) {
    Context context = Context.of(Map.of("division", "south", "account", account), Map.of());
    List<Object> results = new ArrayList<>();
    for (String key : keys) {
      results.add(json.readValue(json.writeValueAsString(reference.resolve(context, key).toJson()),
          new TypeReference<Map<String, Object>>() { }));
    }
    return results;
  }

  @Test
  void providerValuesAreMergedLikeInProcess() throws Exception {
    String[] keys = {"checkout.shipping.allowed_methods", "checkout.container_order.allowed"};
    Map<String, Object> body = resolve("acct-1", keys);
    assertEquals(expected("acct-1", keys), body.get("results"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");
    assertEquals(List.of("GROUND"), results.get(0).get("value"));
    assertEquals(true, results.get(1).get("value"));
  }

  @Test
  void aProviderIsCalledOnceForTheWholeRequest() throws Exception {
    resolve("acct-1", "checkout.shipping.allowed_methods", "checkout.container_order.allowed",
        "checkout.shipping.allowed_methods");
    assertEquals(1, ACCOUNT_CALLS.get());
  }

  @Test
  void eachRequestCallsTheProviderAgain() throws Exception {
    resolve("acct-1", "checkout.container_order.allowed");
    resolve("acct-1", "checkout.container_order.allowed");
    assertEquals(2, ACCOUNT_CALLS.get());
  }

  @Test
  void aProviderIsNotCalledWhenNoRequestedKeyIsSettableAtItsLevel() throws Exception {
    resolve("acct-1", "checkout.pickup.lead_hours", "checkout.order.allowed_max_lines");
    assertEquals(0, ACCOUNT_CALLS.get());
  }

  @Test
  void aFailingProviderGivesAnUnavailableContribution() throws Exception {
    String[] keys = {"checkout.shipping.allowed_methods", "checkout.container_order.allowed"};
    Map<String, Object> body = resolve("acct-fail", keys);
    assertEquals(1, ACCOUNT_CALLS.get());
    assertEquals(expected("acct-fail", keys), body.get("results"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");
    assertEquals(false, results.get(1).get("value"));
  }

  @Test
  void aSlowProviderTimesOutAfterTheConfiguredTimeout() throws Exception {
    String[] keys = {"checkout.container_order.allowed", "checkout.shipping.allowed_methods"};
    long started = System.nanoTime();
    Map<String, Object> body = resolve("acct-slow", keys);
    long millis = Duration.ofNanos(System.nanoTime() - started).toMillis();
    assertTrue(millis < 1_500, "the request waited " + millis + " ms");
    assertEquals(expected("acct-slow", keys), body.get("results"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");
    assertEquals(false, results.get(0).get("value"));
  }

  @Test
  void aLateAnswerIsNotUsedForAnyKeyOfTheRequest() throws Exception {
    // The provider ignores the interrupt and answers after the timeout, while the second key is being resolved.
    // The request is one evaluation call (SVC-20): both keys see the timeout (SVC-40, SVC-41).
    String[] keys = {"checkout.container_order.allowed", "checkout.shipping.allowed_methods"};
    Map<String, Object> body = resolve("acct-stubborn", keys);
    assertEquals(1, ACCOUNT_CALLS.get());
    assertEquals(expected("acct-stubborn", keys), body.get("results"));
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> results = (List<Map<String, Object>>) body.get("results");
    for (Map<String, Object> result : results) {
      assertTrue(json.writeValueAsString(result.get("contributions")).contains("\"UNAVAILABLE\""),
          "no UNAVAILABLE contribution in " + result);
    }
  }

  @Test
  void aProviderValueThatFailsValidationGivesAnInvalidContribution() throws Exception {
    String[] keys = {"checkout.shipping.allowed_methods"};
    Map<String, Object> body = resolve("acct-invalid", keys);
    assertEquals(expected("acct-invalid", keys), body.get("results"));
    assertTrue(json.writeValueAsString(body).contains("\"INVALID\""));
  }

  @Test
  void aKeyThatTheProviderOmitsContributesNothing() throws Exception {
    String[] keys = {"checkout.shipping.allowed_methods", "checkout.container_order.allowed"};
    Map<String, Object> body = resolve("acct-partial", keys);
    assertEquals(expected("acct-partial", keys), body.get("results"));
  }

  @Test
  void theListenerReceivesTheCoreEventsAndTheLoadEventOnlyOnce() throws Exception {
    resolve("acct-invalid", "checkout.shipping.allowed_methods");
    resolve("acct-fail", "checkout.container_order.allowed");
    resolve("acct-fail", "checkout.container_order.allowed");
    // The shared evaluator reports its creation once per bundle version, possibly in an earlier test.
    long loaded = EVENTS.stream().filter(event -> event.type() == EvaluationEvent.Type.BUNDLE_LOADED).count();
    assertTrue(loaded <= 1, "BUNDLE_LOADED was reported " + loaded + " times");
    assertEquals(1, EVENTS.stream().filter(event -> event.type() == EvaluationEvent.Type.SOURCE_INVALID).count());
    assertEquals(2, EVENTS.stream().filter(event -> event.type() == EvaluationEvent.Type.SOURCE_UNAVAILABLE).count());
    assertTrue(EVENTS.stream().allMatch(event -> event.bundleVersion().equals(Fixtures.version(commerce))));
  }
}
