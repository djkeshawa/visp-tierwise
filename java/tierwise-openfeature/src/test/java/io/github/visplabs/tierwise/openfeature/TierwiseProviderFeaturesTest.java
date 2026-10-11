package io.github.visplabs.tierwise.openfeature;

import static io.github.visplabs.tierwise.openfeature.ProviderFixture.client;
import static io.github.visplabs.tierwise.openfeature.ProviderFixture.facts;
import static io.github.visplabs.tierwise.openfeature.ProviderFixture.of;
import static io.github.visplabs.tierwise.openfeature.ProviderFixture.pairs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Features read through the OpenFeature client of the SDK: string and object flags over facts. */
class TierwiseProviderFeaturesTest {

  private Client client;

  @BeforeEach
  void setUp() {
    client = client(TierwiseProvider.of(TestBundles.evaluator()));
  }

  private static EvaluationContext wholeFacts(boolean flag, String kind) {
    return facts(pairs("facts", pairs("parent.flag", flag, "whole.kind", kind)));
  }

  @Test
  void aMatchingRuleIsATargetingMatchWithTheRuleAsVariant() {
    FlagEvaluationDetails<String> details = client.getStringDetails("f.one", "none", wholeFacts(true, "a"));
    assertEquals("ALLOW", details.getValue());
    assertEquals(Reason.TARGETING_MATCH.toString(), details.getReason());
    assertEquals("r1", details.getVariant());
    assertNull(details.getErrorCode());
    assertEquals("feature", details.getFlagMetadata().getString(TierwiseMetadata.FLAG_KIND));
    assertEquals("RULE", details.getFlagMetadata().getString(TierwiseMetadata.DECISION_REASON));
    assertEquals("r1", details.getFlagMetadata().getString(TierwiseMetadata.RULE_ID));
    assertEquals("first rule", details.getFlagMetadata().getString(TierwiseMetadata.RULE_NAME));
    assertEquals("p1", details.getFlagMetadata().getString(TierwiseMetadata.POLICY));
    assertEquals("top", details.getFlagMetadata().getString(TierwiseMetadata.BINDING_LEVEL));
    assertNull(details.getFlagMetadata().getString(TierwiseMetadata.BINDING_NODE));
    assertTrue(details.getFlagMetadata().getString(TierwiseMetadata.BUNDLE_VERSION).startsWith("sha256:"));
  }

  @Test
  void thePolicyDefaultIsTheDefaultReason() {
    FlagEvaluationDetails<String> details = client.getStringDetails("f.one", "none", wholeFacts(false, "a"));
    assertEquals("DENY", details.getValue());
    assertEquals(Reason.DEFAULT.toString(), details.getReason());
    assertNull(details.getVariant());
    assertEquals("DEFAULT", details.getFlagMetadata().getString(TierwiseMetadata.DECISION_REASON));
    assertNull(details.getFlagMetadata().getString(TierwiseMetadata.RULE_ID));
  }

  @Test
  void aMissingFactIsTheDefaultReasonWithTheFactsInTheMetadata() {
    FlagEvaluationDetails<String> details = client.getStringDetails("f.one", "none", new ImmutableContext());
    assertEquals("DENY", details.getValue(), "the feature's if_fact_missing effect");
    assertEquals(Reason.DEFAULT.toString(), details.getReason());
    assertEquals("MISSING_FACT", details.getFlagMetadata().getString(TierwiseMetadata.DECISION_REASON));
    assertEquals(
        "[\"parent.flag\",\"whole.kind\"]", details.getFlagMetadata().getString(TierwiseMetadata.MISSING_FACTS));
  }

  @Test
  void aNullFactIsMissing() {
    FlagEvaluationDetails<String> details =
        client.getStringDetails("f.one", "none", facts(pairs("facts", pairs("parent.flag", null, "whole.kind", "a"))));
    assertEquals("MISSING_FACT", details.getFlagMetadata().getString(TierwiseMetadata.DECISION_REASON));
  }

  @Test
  void integerChildAndDecimalFactsAreAcceptedFromTheirOpenFeatureValues() {
    // An Integer value and an integral Double both reach the evaluator as integers; the decimal is a string.
    EvaluationContext context =
        facts(
            pairs(
                "facts",
                pairs("parent.flag", true, "whole.kind", "a", "whole.count", 3, "whole.price", "9.99"),
                "children",
                pairs("part", List.of(pairs("part.tag", "x", "part.qty", 5.0), pairs("part.tag", "y")))));
    FlagEvaluationDetails<String> details = client.getStringDetails("f.one", "none", context);
    assertNull(details.getErrorCode(), details.getErrorMessage());
    assertEquals("ALLOW", details.getValue());
  }

  @Test
  void aBindingAtANodeIsNamedInTheMetadataAndSkippedScopesAreListed() {
    FlagEvaluationDetails<String> details =
        client.getStringDetails(
            "f.one",
            "none",
            of(
                Map.of("mid", "m1"),
                Map.of("lane", "unlisted"),
                pairs("facts", pairs("parent.flag", true, "whole.kind", "a"))));
    assertEquals("ALLOW", details.getValue());
    assertEquals(
        "[{\"scope\":\"axis\",\"name\":\"lane\",\"node\":\"unlisted\",\"reason\":\"UNKNOWN_NODE\"}]",
        details.getFlagMetadata().getString(TierwiseMetadata.SKIPPED_SCOPES));
  }

  @Test
  void anObjectEffectReadsAsAStructureThroughAnObjectFlag() {
    FlagEvaluationDetails<Value> details =
        client.getObjectDetails("f.obj", new Value(), facts(pairs("facts", pairs("parent.flag", true))));
    assertEquals(Reason.TARGETING_MATCH.toString(), details.getReason());
    assertEquals("on", details.getVariant());
    Map<String, Value> fields = details.getValue().asStructure().asUnmodifiableMap();
    assertEquals("n", fields.get("note").asString());
    assertEquals(2, ((Number) fields.get("level").asObject()).intValue());
  }

  @Test
  void aFeatureReadAsTheWrongTypeIsATypeMismatch() {
    EvaluationContext context = facts(pairs("facts", pairs("parent.flag", true, "whole.kind", "a")));
    FlagEvaluationDetails<Value> asObject = client.getObjectDetails("f.one", new Value("dflt"), context);
    assertEquals(ErrorCode.TYPE_MISMATCH, asObject.getErrorCode());
    assertEquals(new Value("dflt"), asObject.getValue());
    assertEquals("feature", asObject.getFlagMetadata().getString(TierwiseMetadata.FLAG_KIND));

    FlagEvaluationDetails<String> asString =
        client.getStringDetails("f.obj", "dflt", facts(pairs("facts", pairs("parent.flag", true))));
    assertEquals(ErrorCode.TYPE_MISMATCH, asString.getErrorCode());
    assertEquals("dflt", asString.getValue());
  }

  @Test
  void aFeatureAskedForAsABooleanIntegerOrDoubleIsATypeMismatchNotAMissingFlag() {
    ImmutableContext none = new ImmutableContext();
    FlagEvaluationDetails<Boolean> asBoolean = client.getBooleanDetails("f.one", true, none);
    assertEquals(ErrorCode.TYPE_MISMATCH, asBoolean.getErrorCode());
    assertEquals(true, asBoolean.getValue());
    assertTrue(asBoolean.getErrorMessage().contains("feature"), asBoolean.getErrorMessage());
    assertEquals(ErrorCode.TYPE_MISMATCH, client.getIntegerDetails("f.one", 1, none).getErrorCode());
    assertEquals(ErrorCode.TYPE_MISMATCH, client.getDoubleDetails("f.one", 1.0, none).getErrorCode());
  }

  @Test
  void aNullEffectCannotBeCarriedSoItResolvesToTheDefault() {
    FlagEvaluationDetails<String> off =
        client.getStringDetails("f.null", "dflt", facts(pairs("facts", pairs("parent.flag", false))));
    assertEquals("dflt", off.getValue());
    assertEquals(Reason.DEFAULT.toString(), off.getReason());
    assertNull(off.getErrorCode());
    assertEquals("true", String.valueOf(off.getFlagMetadata().getBoolean(TierwiseMetadata.EFFECT_NULL)));

    FlagEvaluationDetails<String> on =
        client.getStringDetails("f.null", "dflt", facts(pairs("facts", pairs("parent.flag", true))));
    assertEquals("YES", on.getValue());
    assertNull(on.getFlagMetadata().getBoolean(TierwiseMetadata.EFFECT_NULL));
  }

  @Test
  void aNameThatIsAKeyAndAFeatureReadsTheKey() {
    // The lookup order is documented: keys first. The fixture has no such name, so the order is checked by
    // the stub below, which counts the calls.
    int[] calls = new int[2];
    io.github.visplabs.tierwise.core.Evaluator evaluator =
        new io.github.visplabs.tierwise.core.Evaluator() {
          @Override
          public io.github.visplabs.tierwise.core.Resolved resolve(io.github.visplabs.tierwise.core.Context context, String key) {
            calls[0]++;
            return TestBundles.evaluator().resolve(context, "k.root");
          }

          @Override
          public io.github.visplabs.tierwise.core.Resolved resolveLocal(io.github.visplabs.tierwise.core.Context context, String key) {
            throw new UnsupportedOperationException();
          }

          @Override
          public io.github.visplabs.tierwise.core.Decision decide(
              io.github.visplabs.tierwise.core.Context context, String feature, io.github.visplabs.tierwise.core.FactSet facts) {
            calls[1]++;
            throw new UnsupportedOperationException();
          }
        };
    assertEquals("root", client(TierwiseProvider.of(evaluator)).getStringValue("shared", "x"));
    assertEquals(1, calls[0]);
    assertEquals(0, calls[1], "a key that exists is never looked up as a feature");
  }
}
