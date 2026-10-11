package io.github.visplabs.tierwise.openfeature;

import static io.github.visplabs.tierwise.openfeature.ProviderFixture.client;
import static io.github.visplabs.tierwise.openfeature.ProviderFixture.context;
import static io.github.visplabs.tierwise.openfeature.ProviderFixture.facts;
import static io.github.visplabs.tierwise.openfeature.ProviderFixture.levels;
import static io.github.visplabs.tierwise.openfeature.ProviderFixture.of;
import static io.github.visplabs.tierwise.openfeature.ProviderFixture.pairs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import io.github.visplabs.tierwise.core.Evaluator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Errors, the evaluator supplier and the provider's own contract. */
class TierwiseProviderErrorsTest {

  private TierwiseProvider provider;
  private Client client;

  @BeforeEach
  void setUp() {
    provider = TierwiseProvider.of(TestBundles.evaluator());
    client = client(provider);
  }

  private static <T> void assertError(FlagEvaluationDetails<T> details, ErrorCode code, String tierwiseCode, T fallback) {
    assertEquals(code, details.getErrorCode(), details.getErrorMessage());
    assertEquals(Reason.ERROR.toString(), details.getReason());
    assertEquals(fallback, details.getValue(), "the caller's default comes back");
    assertEquals(tierwiseCode, details.getFlagMetadata().getString(TierwiseMetadata.TIERWISE_CODE));
    assertTrue(details.getErrorMessage().contains(tierwiseCode), details.getErrorMessage());
  }

  // ---- flag not found --------------------------------------------------------------------------------

  @Test
  void anUnknownNameIsFlagNotFoundForEveryType() {
    ImmutableContext none = new ImmutableContext();
    assertError(client.getBooleanDetails("nope", true, none), ErrorCode.FLAG_NOT_FOUND, "E_UNKNOWN_FEATURE", true);
    assertError(client.getStringDetails("nope", "d", none), ErrorCode.FLAG_NOT_FOUND, "E_UNKNOWN_FEATURE", "d");
    assertError(client.getIntegerDetails("nope", 4, none), ErrorCode.FLAG_NOT_FOUND, "E_UNKNOWN_FEATURE", 4);
    assertError(client.getDoubleDetails("nope", 4.5, none), ErrorCode.FLAG_NOT_FOUND, "E_UNKNOWN_FEATURE", 4.5);
    assertError(client.getObjectDetails("nope", new Value("d"), none), ErrorCode.FLAG_NOT_FOUND, "E_UNKNOWN_FEATURE", new Value("d"));
  }

  @Test
  void theMessageNamesBothLookups() {
    String message = client.getStringDetails("nope", "d", new ImmutableContext()).getErrorMessage();
    assertTrue(message.contains("no key or feature is named 'nope'"), message);
  }

  // ---- invalid context ---------------------------------------------------------------------------------

  @Test
  void anUnknownLevelNameIsAnInvalidContext() {
    assertError(client.getIntegerDetails("k.over", 0, levels("nowhere", "x")), ErrorCode.INVALID_CONTEXT, "E_UNKNOWN_SCOPE", 0);
  }

  @Test
  void anUnknownNodeIsAnInvalidContext() {
    assertError(client.getIntegerDetails("k.over", 0, levels("mid", "m9")), ErrorCode.INVALID_CONTEXT, "E_UNKNOWN_NODE", 0);
  }

  @Test
  void aDeclaredNameInTheWrongMapIsAnInvalidContext() {
    assertError(client.getIntegerDetails("k.over", 0, of(null, Map.of("mid", "m1"), null)), ErrorCode.INVALID_CONTEXT, "E_UNKNOWN_SCOPE", 0);
  }

  @Test
  void theRootLevelCannotBeNamed() {
    assertError(client.getIntegerDetails("k.over", 0, levels("top", "x")), ErrorCode.INVALID_CONTEXT, "E_CONTEXT_INVALID", 0);
  }

  @Test
  void aMalformedLevelsAttributeIsAnInvalidContext() {
    for (Object malformed :
        new Object[] {"mid=m1", 5, true, List.of("m1"), pairs("mid", 5), pairs("mid", true), pairs("mid", pairs("x", "y")), pairs("mid", null)}) {
      EvaluationContext context = context(pairs(TierwiseProvider.LEVELS_ATTRIBUTE, malformed));
      assertError(client.getIntegerDetails("k.over", 0, context), ErrorCode.INVALID_CONTEXT, "E_CONTEXT_INVALID", 0);
    }
  }

  @Test
  void aMalformedAxesAttributeIsAnInvalidContext() {
    EvaluationContext context = context(pairs(TierwiseProvider.AXES_ATTRIBUTE, "lane"));
    assertError(client.getIntegerDetails("k.over", 0, context), ErrorCode.INVALID_CONTEXT, "E_CONTEXT_INVALID", 0);
  }

  @Test
  void aContextErrorWinsOverAnUnknownFlag() {
    // The evaluator validates the context before it looks a name up, and so does the provider.
    assertError(client.getStringDetails("nope", "d", levels("nowhere", "x")), ErrorCode.INVALID_CONTEXT, "E_UNKNOWN_SCOPE", "d");
  }

  @Test
  void invalidFactsAreAnInvalidContextForAFeature() {
    assertError(
        client.getStringDetails("f.one", "d", facts(pairs("facts", pairs("parent.flag", "true")))),
        ErrorCode.INVALID_CONTEXT,
        "E_FACTS_INVALID",
        "d");
    assertError(
        client.getStringDetails("f.one", "d", facts(pairs("facts", pairs("ghost.fact", 1)))),
        ErrorCode.INVALID_CONTEXT,
        "E_FACTS_INVALID",
        "d");
    assertError(
        client.getStringDetails("f.one", "d", facts(pairs("surprise", pairs()))),
        ErrorCode.INVALID_CONTEXT,
        "E_FACTS_INVALID",
        "d");
  }

  @Test
  void aFactsAttributeThatIsNotAStructureIsAnInvalidContext() {
    EvaluationContext context = context(pairs(TierwiseProvider.FACTS_ATTRIBUTE, "facts"));
    assertError(client.getStringDetails("f.one", "d", context), ErrorCode.INVALID_CONTEXT, "E_FACTS_INVALID", "d");
  }

  @Test
  void aFactThatOpenFeatureCannotExpressAsPlainDataIsAnInvalidContext() {
    EvaluationContext context = facts(pairs("facts", pairs("parent.flag", true, "whole.note", Instant.EPOCH)));
    assertError(client.getStringDetails("f.one", "d", context), ErrorCode.INVALID_CONTEXT, "E_FACTS_INVALID", "d");
  }

  // ---- the evaluator supplier -----------------------------------------------------------------------------

  @Test
  void aSupplierWithoutAnEvaluatorIsProviderNotReady() {
    ProviderEvaluation<String> evaluation =
        new TierwiseProvider(() -> null).getStringEvaluation("k.root", "d", new ImmutableContext());
    assertEquals(ErrorCode.PROVIDER_NOT_READY, evaluation.getErrorCode());
    assertEquals(Reason.ERROR.toString(), evaluation.getReason());
    assertEquals("d", evaluation.getValue());
  }

  @Test
  void theSupplierIsReadOnEveryEvaluationSoABundleSwapIsSeen() {
    AtomicReference<Evaluator> current = new AtomicReference<>(TestBundles.evaluator(node -> Map.of("k.text", "one")));
    TierwiseProvider swapping = new TierwiseProvider(current::get);
    EvaluationContext low = levels("low", "n");
    assertEquals("one", swapping.getStringEvaluation("k.text", "d", low).getValue());
    current.set(TestBundles.evaluator(node -> Map.of("k.text", "two")));
    assertEquals("two", swapping.getStringEvaluation("k.text", "d", low).getValue());
  }

  @Test
  void aSupplierThatThrowsPropagatesToTheSdk() {
    TierwiseProvider failing =
        new TierwiseProvider(
            () -> {
              throw new IllegalStateException("no bundle yet");
            });
    assertThrows(IllegalStateException.class, () -> failing.getStringEvaluation("k.root", "d", new ImmutableContext()));
    Client viaSdk = client(failing);
    FlagEvaluationDetails<String> details = viaSdk.getStringDetails("k.root", "d", new ImmutableContext());
    assertEquals(ErrorCode.GENERAL, details.getErrorCode());
    assertEquals("d", details.getValue());
  }

  @Test
  void aNullEvaluatorOrSupplierIsRejectedAtConstruction() {
    assertThrows(NullPointerException.class, () -> new TierwiseProvider(null));
    assertThrows(NullPointerException.class, () -> TierwiseProvider.of(null));
  }

  // ---- the provider itself ------------------------------------------------------------------------------------

  @Test
  void theProviderHasAStableName() {
    assertEquals("Tierwise", provider.getMetadata().getName());
  }

  @Test
  void aNullEvaluationContextIsTheEmptyContext() {
    ProviderEvaluation<Integer> evaluation = provider.getIntegerEvaluation("k.over", 0, null);
    assertEquals(1, evaluation.getValue());
    assertNull(evaluation.getErrorCode());
  }

  @Test
  void theAttributeNamesAreStable() {
    assertEquals("tierwise.levels", TierwiseProvider.LEVELS_ATTRIBUTE);
    assertEquals("tierwise.axes", TierwiseProvider.AXES_ATTRIBUTE);
    assertEquals("tierwise.facts", TierwiseProvider.FACTS_ATTRIBUTE);
  }

  @Test
  void aFixedEvaluatorAnswersEveryCall() {
    TierwiseProvider fixed = TierwiseProvider.of(TestBundles.evaluator());
    assertEquals(1, fixed.getIntegerEvaluation("k.over", 0, new ImmutableContext()).getValue());
    assertEquals(1, fixed.getIntegerEvaluation("k.over", 0, new ImmutableContext()).getValue());
  }
}
