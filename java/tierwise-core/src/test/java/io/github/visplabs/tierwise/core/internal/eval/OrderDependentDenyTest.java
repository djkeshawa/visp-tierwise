package io.github.visplabs.tierwise.core.internal.eval;

import static io.github.visplabs.tierwise.core.internal.eval.TestModels.mapOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.EvaluatorOptions;
import io.github.visplabs.tierwise.core.LevelValueProvider;
import io.github.visplabs.tierwise.core.Resolved;
import io.github.visplabs.tierwise.core.Resolved.Contribution;
import io.github.visplabs.tierwise.core.Tierwise;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * PROV-49: an order-dependent key never has a failure entry under {@code deny}.
 *
 * <p>A loader rejects a bundle with {@code deny} on a strategy that is not narrowing (SVC-11, BUNDLE-118;
 * see {@code BundleExtraChecksTest#SVC_11_denyNeedsANarrowingStrategy}), so an order-dependent key that is
 * settable at a service-sourced level carries {@code skip}. This property draws every provider outcome for
 * every order-dependent key of the test model and checks each {@code Resolved} result.
 */
class OrderDependentDenyTest {

  private static final List<String> ORDER_DEPENDENT =
      List.of("k.label", "k.deep", "k.deep_hierarchy", "k.deep_root", "k.object_override", "k.decimal", "k.note");

  /** What the account provider does in one call. */
  enum Outcome {
    VALID, INVALID, NULL_ENTRY, OMITS, THROWS, NOT_A_MAPPING, TIMES_OUT
  }

  private static LevelValueProvider provider(Outcome outcome) {
    return node -> switch (outcome) {
      case VALID -> mapOf("k.label", "x", "k.deep", mapOf("cap", 5L), "k.deep_hierarchy", mapOf("unit", "u"),
          "k.deep_root", mapOf("cap", 5L), "k.object_override", mapOf("from", "09:00"), "k.decimal", "2.5",
          "k.note", "ignored");
      case INVALID -> mapOf("k.label", 5L, "k.deep", mapOf("cap", "high"), "k.deep_hierarchy", List.of(),
          "k.deep_root", "x", "k.object_override", mapOf("to", "17:00"), "k.decimal", 2.5, "k.note", 1L);
      case NULL_ENTRY -> mapOf("k.label", null, "k.deep", null, "k.deep_hierarchy", null, "k.deep_root", null,
          "k.object_override", null, "k.decimal", null, "k.note", null);
      case OMITS -> mapOf();
      case THROWS -> throw new java.io.IOException("unavailable");
      case NOT_A_MAPPING -> null;
      case TIMES_OUT -> {
        Thread.sleep(500);
        yield mapOf();
      }
    };
  }

  @Property(tries = 60)
  void PROV_49_anOrderDependentKeyNeverHasAFailureEntryUnderDeny(
      @ForAll("outcomes") Outcome outcome, @ForAll("contexts") Context context, @ForAll("keys") List<String> keys) {
    TestModels.Builder builder = TestModels.standard();
    builder.value("global", "", "k.deep_root", mapOf("cap", 1L, "unit", "u", "tags", List.of()));
    builder.value("division", "north", "k.label", "north");
    EvaluatorOptions options = EvaluatorOptions.builder()
        .provider("account", provider(outcome), Duration.ofMillis(outcome == Outcome.TIMES_OUT ? 20 : 2_000))
        .provider("seat", node -> Map.of(), Duration.ofSeconds(2))
        .build();
    EvaluatorImpl evaluator = (EvaluatorImpl) Tierwise.evaluator(builder.loaded(), options);
    for (Resolved resolved : evaluator.resolveAll(context, keys)) {
      for (Contribution entry : resolved.contributions()) {
        assertFalse(entry.onSourceFailure().equals(Optional.of("deny")), resolved.key() + " " + entry);
      }
    }
  }

  @Property(tries = 20)
  void PROV_49_aFailureOfAnOrderDependentKeyIsRecordedUnderSkip(@ForAll("failures") Outcome outcome) {
    EvaluatorOptions options = EvaluatorOptions.builder()
        .provider("account", provider(outcome), Duration.ofMillis(outcome == Outcome.TIMES_OUT ? 20 : 2_000))
        .provider("seat", node -> Map.of(), Duration.ofSeconds(2))
        .build();
    EvaluatorImpl evaluator = (EvaluatorImpl) Tierwise.evaluator(TestModels.standard().loaded(), options);
    Context account = Context.builder().level("account", "a1").build();
    for (Resolved resolved : evaluator.resolveAll(account, List.of("k.label", "k.deep", "k.object_override"))) {
      Contribution entry = resolved.contributions().get(0);
      assertTrue(entry.effect().isFailure(), resolved.key() + " " + entry);
      assertTrue(entry.onSourceFailure().equals(Optional.of("skip")), resolved.key() + " " + entry);
      assertTrue(entry.value().isEmpty(), "a skip failure carries no value: " + entry);
    }
  }

  @Provide
  Arbitrary<Outcome> outcomes() {
    return Arbitraries.of(Outcome.class);
  }

  @Provide
  Arbitrary<Outcome> failures() {
    return Arbitraries.of(Outcome.INVALID, Outcome.NULL_ENTRY, Outcome.THROWS, Outcome.NOT_A_MAPPING, Outcome.TIMES_OUT);
  }

  @Provide
  Arbitrary<Context> contexts() {
    Arbitrary<Boolean> flag = Arbitraries.of(true, false);
    return net.jqwik.api.Combinators.combine(flag, flag, flag).as((division, account, seller) -> {
      Context.Builder context = Context.builder();
      if (division) {
        context.level("division", "north");
      }
      if (account) {
        context.level("account", "a1");
      }
      if (seller) {
        context.axis("seller", "acme");
      }
      return context.build();
    });
  }

  @Provide
  Arbitrary<List<String>> keys() {
    return Arbitraries.of(ORDER_DEPENDENT).list().ofMinSize(1).ofMaxSize(4);
  }
}
