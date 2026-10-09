package io.github.tierwise.spring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Context;
import io.github.tierwise.core.Resolved;
import io.github.tierwise.core.Resolved.Contribution;
import io.github.tierwise.core.Resolved.Contribution.Label;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Provider timeouts follow the declared {@code skip} and {@code deny} behaviour (requirements section 12),
 * on keys without a kind and on keys with custom kinds. The bundles are the spec's conformance bundles.
 */
class ProviderTimeoutTest {

  private static final long STALL_MILLIS = 5_000;

  private static TierwiseProvider stalling(String level) {
    return TierwiseProvider.of(level, nodeId -> {
      Thread.sleep(STALL_MILLIS);
      return Map.of();
    });
  }

  private static TierwiseProvider answering(String level, Map<String, Object> values) {
    return TierwiseProvider.of(level, nodeId -> values);
  }

  private static ApplicationContextRunner runner(String bundle, String... properties) {
    return new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(TierwiseAutoConfiguration.class))
        .withPropertyValues("tierwise.source=file:" + TestBundles.specBundle(bundle), "tierwise.refresh-interval=0")
        .withPropertyValues(properties);
  }

  private static void resolving(ApplicationContextRunner runner, Consumer<Resolver> check) {
    runner.run(context -> {
      assertTrue(context.getStartupFailure() == null, () -> String.valueOf(context.getStartupFailure()));
      check.accept(context.getBean(Resolver.class));
    });
  }

  private static Contribution failureOf(Resolved resolved, String level) {
    return resolved.contributions().stream()
        .filter(c -> c.name().equals(level) && c.effect().isFailure())
        .findFirst()
        .orElseThrow(() -> new AssertionError("no failure entry for " + level + " in " + resolved.contributions()));
  }

  // -- keys without a kind: edge-providers.json (levels tenant, partner, seat are service-sourced) --------

  private ApplicationContextRunner edgeProviders(TierwiseProvider tenant, String... properties) {
    return runner("edge-providers.json", properties)
        .withBean("tenantProvider", TierwiseProvider.class, () -> tenant)
        .withBean("partnerProvider", TierwiseProvider.class, () -> answering("partner", Map.of()))
        .withBean("seatProvider", TierwiseProvider.class, () -> answering("seat", Map.of()));
  }

  @Test
  void aTimedOutProviderDeniesAKeyWithoutAKindThatDeclaresDeny() {
    resolving(edgeProviders(stalling("tenant"), "tierwise.provider-timeouts.tenant=100ms"), resolver -> {
      Resolved resolved = resolver.resolve(Context.builder().level("tenant", "t-1").build(), "access.allowed");

      assertEquals(false, resolved.value());
      assertTrue(resolved.kind().isEmpty());
      Contribution failure = failureOf(resolved, "tenant");
      assertEquals(Label.UNAVAILABLE, failure.effect());
      assertEquals("deny", failure.onSourceFailure().orElseThrow());
      assertEquals(false, failure.value().orElseThrow());
    });
  }

  @Test
  void aTimedOutProviderGivesAMinKeyItsDeclaredMinimum() {
    resolving(edgeProviders(stalling("tenant"), "tierwise.provider-timeouts.tenant=100ms"), resolver -> {
      Resolved resolved = resolver.resolve(
          Context.builder().level("region", "east").level("tenant", "t-1").build(), "quota.max_items");

      assertEquals(2L, resolved.value(), "the min bound 2 narrows the region value 6");
      assertEquals(Label.UNAVAILABLE, failureOf(resolved, "tenant").effect());
    });
  }

  @Test
  void aTimedOutProviderContributesNothingToAKeyWithoutAKindThatDeclaresSkip() {
    resolving(edgeProviders(stalling("tenant"), "tierwise.provider-timeouts.tenant=100ms"), resolver -> {
      Resolved resolved = resolver.resolve(Context.builder().level("tenant", "t-1").build(), "display.theme");

      assertEquals("light", resolved.value(), "the default applies");
      assertTrue(resolved.source().orElseThrow().isDefault());
      Contribution failure = failureOf(resolved, "tenant");
      assertEquals(Label.UNAVAILABLE, failure.effect());
      assertEquals("skip", failure.onSourceFailure().orElseThrow());
      assertTrue(failure.value().isEmpty());
    });
  }

  @Test
  void aProviderThatThrowsFollowsTheSameBehaviourAsATimeout() {
    TierwiseProvider throwing = TierwiseProvider.of("tenant", nodeId -> {
      throw new IllegalStateException("the account service is down");
    });
    resolving(edgeProviders(throwing), resolver -> {
      Resolved denied = resolver.resolve(Context.builder().level("tenant", "t-1").build(), "access.allowed");
      Resolved skipped = resolver.resolve(Context.builder().level("tenant", "t-1").build(), "display.theme");

      assertEquals(false, denied.value());
      assertEquals(Label.UNAVAILABLE, failureOf(denied, "tenant").effect());
      assertEquals("light", skipped.value());
      assertEquals(Label.UNAVAILABLE, failureOf(skipped, "tenant").effect());
    });
  }

  @Test
  void aProviderThatReturnsAnInvalidValueFailsOnlyThatKey() {
    TierwiseProvider invalid = answering("tenant", Map.of("access.allowed", "yes", "display.theme", "dark"));
    resolving(edgeProviders(invalid), resolver -> {
      Resolved denied = resolver.resolve(Context.builder().level("tenant", "t-1").build(), "access.allowed");
      Resolved fine = resolver.resolve(Context.builder().level("tenant", "t-1").build(), "display.theme");

      assertEquals(false, denied.value());
      assertEquals(Label.INVALID, failureOf(denied, "tenant").effect());
      assertEquals("dark", fine.value());
    });
  }

  // -- keys with custom kinds -----------------------------------------------------------------------------

  @Test
  void aTimedOutProviderDeniesAKeyWhoseCustomKindDeclaresDeny() {
    ApplicationContextRunner runner = runner("commerce.json", "tierwise.provider-timeouts.account=100ms")
        .withBean("accountProvider", TierwiseProvider.class, () -> stalling("account"))
        .withBean("userProvider", TierwiseProvider.class, () -> answering("user", Map.of()));
    resolving(runner, resolver -> {
      Resolved resolved = resolver.resolve(
          Context.builder().level("division", "south").level("account", "acct-17").build(),
          "checkout.container_order.allowed");

      assertEquals("capability", resolved.kind().orElseThrow());
      assertEquals(false, resolved.value());
      Contribution failure = failureOf(resolved, "account");
      assertEquals(Label.UNAVAILABLE, failure.effect());
      assertEquals("deny", failure.onSourceFailure().orElseThrow());
    });
  }

  @Test
  void customKindsOnTheRetailBundleFollowTheirDeclaredBehaviours() {
    ApplicationContextRunner runner = runner("retail-pos.json", "tierwise.provider-timeouts.terminal=100ms")
        .withBean("terminalProvider", TierwiseProvider.class, () -> stalling("terminal"));
    resolving(runner, resolver -> {
      Context context = Context.builder().level("store", "harbor_street").level("terminal", "t-1").build();

      Resolved limit = resolver.resolve(context, "pos.basket.line_limit");
      assertEquals("guard", limit.kind().orElseThrow());
      assertEquals(1L, limit.value(), "deny: the declared minimum 1, below the platform value 200 and the store value 80");
      assertEquals("deny", failureOf(limit, "terminal").onSourceFailure().orElseThrow());

      Resolved label = resolver.resolve(context, "pos.receipt.tax_label");
      assertEquals("setting", label.kind().orElseThrow());
      assertEquals("Sales Tax", label.value(), "skip: the platform value stays");
      Contribution failure = failureOf(label, "terminal");
      assertEquals("skip", failure.onSourceFailure().orElseThrow());
      assertTrue(failure.value().isEmpty());
    });
  }

  // -- which timeout applies -----------------------------------------------------------------------------

  private static TierwiseProvider sleeping(long millis, Duration suggested) {
    return TierwiseProvider.of("tenant", nodeId -> {
      Thread.sleep(millis);
      return Map.of("access.allowed", true);
    }, suggested);
  }

  private static Resolved allowedFor(Resolver resolver) {
    return resolver.resolve(Context.builder().level("tenant", "t-1").build(), "access.allowed");
  }

  @Test
  void theDefaultTimeoutIs200Milliseconds() {
    resolving(edgeProviders(sleeping(1_000, null)), resolver ->
        assertEquals(Label.UNAVAILABLE, failureOf(allowedFor(resolver), "tenant").effect()));
    resolving(edgeProviders(sleeping(0, null)), resolver ->
        assertEquals(List.of(Label.IGNORED), labelsOf(allowedFor(resolver), "tenant")));
  }

  @Test
  void theDefaultCanBeRaisedByProperty() {
    resolving(edgeProviders(sleeping(400, null), "tierwise.provider-timeout=5s"), resolver ->
        assertEquals(List.of(Label.IGNORED), labelsOf(allowedFor(resolver), "tenant")));
  }

  @Test
  void aProviderSuggestionOverridesTheDefault() {
    resolving(edgeProviders(sleeping(400, Duration.ofMillis(50)), "tierwise.provider-timeout=5s"), resolver ->
        assertEquals(Label.UNAVAILABLE, failureOf(allowedFor(resolver), "tenant").effect()));
  }

  @Test
  void aPerLevelPropertyOverridesTheProviderSuggestion() {
    resolving(edgeProviders(sleeping(400, Duration.ofMillis(50)), "tierwise.provider-timeouts.tenant=5s"), resolver ->
        assertEquals(List.of(Label.IGNORED), labelsOf(allowedFor(resolver), "tenant")));
  }

  private static List<Label> labelsOf(Resolved resolved, String level) {
    return resolved.contributions().stream().filter(c -> c.name().equals(level)).map(Contribution::effect).toList();
  }

  // -- configuration errors ----------------------------------------------------------------------------

  private static Throwable startupFailure(ApplicationContextRunner runner) {
    Throwable[] failure = new Throwable[1];
    runner.run((AssertableApplicationContext context) -> failure[0] = context.getStartupFailure());
    return failure[0];
  }

  private static boolean mentions(Throwable failure, String text) {
    for (Throwable t = failure; t != null; t = t.getCause()) {
      if (t.getMessage() != null && t.getMessage().contains(text)) {
        return true;
      }
    }
    return false;
  }

  @Test
  void aTimeoutThatIsNotAPositiveWholeNumberOfMillisecondsFailsStartup() {
    assertTrue(mentions(startupFailure(edgeProviders(answering("tenant", Map.of()), "tierwise.provider-timeout=0s")),
        "tierwise.provider-timeout must be a positive whole number of milliseconds"));
    assertTrue(mentions(startupFailure(edgeProviders(answering("tenant", Map.of()), "tierwise.provider-timeout=1500us")),
        "whole number of milliseconds"));
    assertTrue(mentions(startupFailure(edgeProviders(answering("tenant", Map.of()), "tierwise.provider-timeouts.tenant=-1ms")),
        "tierwise.provider-timeouts.tenant"));
  }

  @Test
  void aMissingProviderFailsStartupWithTheCoreErrorCode() {
    Throwable failure = startupFailure(runner("edge-providers.json")
        .withBean("tenantProvider", TierwiseProvider.class, () -> answering("tenant", Map.of())));

    assertTrue(mentions(failure, "E_PROVIDER_MISSING"), String.valueOf(failure));
  }

  @Test
  void twoProvidersForOneLevelFailStartupWithTheCoreErrorCode() {
    Throwable failure = startupFailure(edgeProviders(answering("tenant", Map.of()))
        .withBean("secondTenantProvider", TierwiseProvider.class, () -> answering("tenant", Map.of())));

    assertTrue(mentions(failure, "E_EVALUATOR_OPTIONS"), String.valueOf(failure));
  }
}
