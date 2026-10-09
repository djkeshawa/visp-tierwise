package io.github.tierwise.core.internal.bundle;

import static io.github.tierwise.core.internal.bundle.BundleFixtures.bundle;
import static io.github.tierwise.core.internal.bundle.BundleFixtures.richContent;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Context;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.LevelValueProvider;
import io.github.tierwise.core.LoadedBundle;
import io.github.tierwise.core.Tierwise;
import io.github.tierwise.core.TierwiseException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;

/**
 * EVAL-38: the evaluation operations raise only the eleven codes of requirements §6.2, and each operation only the
 * codes whose triggers EVAL-24 to EVAL-37 give it. A vector observes a code only at its trigger, so these tests are the
 * enforcer of EVAL-38 (spec/conformance/enforcers.d/E6.yaml). Every input built here is either valid or a caller
 * error, so an operation must return a result or raise one of its own codes, and never another exception.
 */
class EvaluationErrorCodesTest {

  private static final Set<String> LOAD = Set.of(Code.E_BUNDLE_INTEGRITY, Code.E_BUNDLE_UNSUPPORTED);
  private static final Set<String> CREATE = Set.of(Code.E_EVALUATOR_OPTIONS, Code.E_PROVIDER_MISSING);
  private static final Set<String> RESOLVE = Set.of(Code.E_CONTEXT_INVALID, Code.E_UNKNOWN_SCOPE,
      Code.E_UNKNOWN_NODE, Code.E_UNKNOWN_KEY);
  private static final Set<String> RESOLVE_LOCAL = Set.of(Code.E_CONTEXT_INVALID, Code.E_UNKNOWN_SCOPE,
      Code.E_UNKNOWN_NODE, Code.E_UNKNOWN_KEY, Code.E_PROVIDER_REQUIRED);
  private static final Set<String> DECIDE = Set.of(Code.E_CONTEXT_INVALID, Code.E_UNKNOWN_SCOPE,
      Code.E_UNKNOWN_NODE, Code.E_UNKNOWN_FEATURE, Code.E_FACTS_INVALID);

  private static final LevelValueProvider PROVIDER = node -> Map.of("checkout.allowed", "not a boolean");

  /** Runs a call and returns the code it raises, or null. Any other exception fails the test. */
  private static String codeOf(Callable<?> call) {
    try {
      call.call();
      return null;
    } catch (TierwiseException e) {
      return e.code();
    } catch (Exception | Error e) {
      throw new AssertionError("a non-evaluation exception was raised", e);
    }
  }

  private static void assertAllowed(Set<String> allowed, String operation, Callable<?> call) {
    String code = codeOf(call);
    assertTrue(code == null || allowed.contains(code), operation + " raised " + code);
  }

  @Test
  void EVAL_38_theEvaluationCodesAreExactlyTheEleven() {
    assertEquals(Set.of(Code.E_BUNDLE_INTEGRITY, Code.E_BUNDLE_UNSUPPORTED, Code.E_PROVIDER_MISSING,
        Code.E_PROVIDER_REQUIRED, Code.E_CONTEXT_INVALID, Code.E_UNKNOWN_SCOPE, Code.E_UNKNOWN_NODE,
        Code.E_UNKNOWN_KEY, Code.E_UNKNOWN_FEATURE, Code.E_FACTS_INVALID, Code.E_EVALUATOR_OPTIONS), Code.evaluation());
  }

  @Test
  void EVAL_38_loadBundleRaisesOnlyIntegrityAndUnsupported() {
    byte[] good = bundle(richContent());
    Random random = new Random(38);
    for (int i = 0; i < 400; i++) {
      byte[] flipped = good.clone();
      flipped[random.nextInt(good.length)] = (byte) random.nextInt(256);
      byte[] cut = Arrays.copyOf(good, random.nextInt(good.length));
      assertAllowed(LOAD, "load bundle", () -> Tierwise.loadBundle(flipped));
      assertAllowed(LOAD, "load bundle", () -> Tierwise.loadBundle(cut));
    }
    Map<String, Object> unsupported = richContent();
    unsupported.put("specVersion", "0.2");
    assertEquals(Code.E_BUNDLE_UNSUPPORTED, codeOf(() -> Tierwise.loadBundle(bundle(unsupported))));
  }

  @Test
  void EVAL_38_createEvaluatorRaisesOnlyEvaluatorOptionsAndProviderMissing() {
    LoadedBundle loaded = Tierwise.loadBundle(bundle(richContent()));
    List<String> levels = List.of("account", "division", "global", "seller", "region", "");
    List<Duration> timeouts = Arrays.asList(Duration.ofSeconds(1), Duration.ZERO, Duration.ofMillis(-1),
        Duration.ofNanos(1), Duration.ofNanos(1_500_000), null, Duration.ofSeconds(Long.MAX_VALUE));
    assertAllowed(CREATE, "create evaluator", () -> Tierwise.evaluator(loaded, null));
    for (String first : levels) {
      for (Duration timeout : timeouts) {
        for (String second : Arrays.asList(null, "account", "division")) {
          EvaluatorOptions.Builder options = EvaluatorOptions.builder().provider(first, PROVIDER, timeout);
          if (second != null) {
            options.provider(second, PROVIDER, Duration.ofSeconds(1));
          }
          assertAllowed(CREATE, "create evaluator", () -> Tierwise.evaluator(loaded, options.build()));
        }
      }
    }
    assertEquals(Code.E_PROVIDER_MISSING, codeOf(() -> Tierwise.evaluator(loaded, EvaluatorOptions.none())));
    assertEquals(Code.E_EVALUATOR_OPTIONS, codeOf(() -> Tierwise.evaluator(loaded,
        EvaluatorOptions.builder().provider("account", PROVIDER, Duration.ZERO).build())));
  }

  @Test
  void EVAL_38_resolveResolveLocalAndDecideRaiseOnlyTheCodesOfTheirTriggers() {
    Evaluator evaluator = Tierwise.evaluator(Tierwise.loadBundle(bundle(richContent())),
        EvaluatorOptions.builder().provider("account", PROVIDER, Duration.ofSeconds(1)).build());
    List<Object> nodes = Arrays.asList("north", "west", "a1", "acme", "", "x".repeat(129), "a\u0007", "a\uD800", 5L, null);
    List<String> scopes = List.of("global", "division", "account", "seller", "region", "Division");
    List<Object> contexts = new ArrayList<>(Arrays.asList(null, "x", List.of(), 5L, Map.of("extra", Map.of()),
        Map.of("levels", "x"), Map.of("axes", List.of())));
    for (String scope : scopes) {
      for (Object node : nodes) {
        Map<String, Object> scopeMap = new HashMap<>();
        scopeMap.put(scope, node);
        contexts.add(Map.of("levels", scopeMap));
        contexts.add(Map.of("axes", scopeMap));
      }
    }
    List<String> names = Arrays.asList(null, "", "checkout.allowed", "checkout.window", "checkout.submission", "nope");
    List<Object> factSets = Arrays.asList(null, "x", List.of(), Map.of("extra", 1),
        Map.of("facts", Map.of("customer.active", true)), Map.of("facts", Map.of("customer.active", "yes")),
        Map.of("facts", Map.of("nope.x", true)), Map.of("children", Map.of("order", List.of(Map.of("order.status", 5L)))),
        Map.of("children", Map.of("customer", List.of())));
    for (Object raw : contexts) {
      Context context = Context.fromJson(raw);
      for (String name : names) {
        assertAllowed(RESOLVE, "resolve", () -> evaluator.resolve(context, name));
        assertAllowed(RESOLVE_LOCAL, "resolveLocal", () -> evaluator.resolveLocal(context, name));
        for (Object facts : factSets) {
          assertAllowed(DECIDE, "decide", () -> evaluator.decide(context, name, FactSet.fromJson(facts)));
        }
      }
    }
  }

  @Test
  void EVAL_38_eachOperationReachesEachOfItsCodesAtItsTrigger() {
    Evaluator evaluator = Tierwise.evaluator(Tierwise.loadBundle(bundle(richContent())),
        EvaluatorOptions.builder().provider("account", PROVIDER, Duration.ofSeconds(1)).build());
    Context root = Context.fromJson(Map.of("levels", Map.of("global", "x")));
    Context unknownScope = Context.builder().level("region", "x").build();
    Context unknownNode = Context.builder().level("division", "west").build();
    Context account = Context.builder().level("account", "a1").build();
    assertEquals(Code.E_CONTEXT_INVALID, codeOf(() -> evaluator.resolve(root, "checkout.allowed")));
    assertEquals(Code.E_UNKNOWN_SCOPE, codeOf(() -> evaluator.resolve(unknownScope, "checkout.allowed")));
    assertEquals(Code.E_UNKNOWN_NODE, codeOf(() -> evaluator.resolve(unknownNode, "checkout.allowed")));
    assertEquals(Code.E_UNKNOWN_KEY, codeOf(() -> evaluator.resolve(Context.empty(), "nope")));
    assertEquals(Code.E_PROVIDER_REQUIRED, codeOf(() -> evaluator.resolveLocal(account, "checkout.allowed")));
    assertEquals(Code.E_UNKNOWN_FEATURE, codeOf(() -> evaluator.decide(Context.empty(), "nope", FactSet.empty())));
    assertEquals(Code.E_FACTS_INVALID, codeOf(() -> evaluator.decide(Context.empty(), "checkout.submission",
        FactSet.builder().fact("customer.active", "yes").build())));
  }
}
