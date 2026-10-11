package io.github.visplabs.tierwise.openfeature;

import static io.github.visplabs.tierwise.openfeature.ProviderFixture.client;
import static io.github.visplabs.tierwise.openfeature.ProviderFixture.context;
import static io.github.visplabs.tierwise.openfeature.ProviderFixture.levels;
import static io.github.visplabs.tierwise.openfeature.ProviderFixture.of;
import static io.github.visplabs.tierwise.openfeature.ProviderFixture.pairs;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.FlagEvaluationDetails;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.ImmutableMetadata;
import dev.openfeature.sdk.Reason;
import dev.openfeature.sdk.Value;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Keys read through the OpenFeature client of the SDK: types, reasons and metadata. */
class TierwiseProviderKeysTest {

  private Client client;

  @BeforeEach
  void setUp() {
    client = client(TierwiseProvider.of(TestBundles.evaluator()));
  }

  private static String string(FlagEvaluationDetails<?> details, String name) {
    ImmutableMetadata metadata = details.getFlagMetadata();
    return metadata.getString(name);
  }

  // ---- boolean -----------------------------------------------------------------------------------

  @Test
  void aBooleanKeyThatOnlyTheRootLevelSetsIsStatic() {
    FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("k.all", false, new ImmutableContext());
    assertEquals(true, details.getValue());
    assertEquals(Reason.STATIC.toString(), details.getReason());
    assertNull(details.getErrorCode());
    assertEquals("key", string(details, TierwiseMetadata.FLAG_KIND));
    assertEquals("all", string(details, TierwiseMetadata.STRATEGY));
    assertEquals(
        "[{\"scope\":\"level\",\"name\":\"top\",\"effect\":\"IGNORED\"}]",
        string(details, TierwiseMetadata.CONTRIBUTIONS));
  }

  @Test
  void aBooleanKeyThatANamedNodeContributesToIsATargetingMatch() {
    FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("k.all", false, levels("mid", "m1"));
    assertEquals(true, details.getValue());
    assertEquals(Reason.TARGETING_MATCH.toString(), details.getReason());
    assertEquals(
        "[{\"scope\":\"level\",\"name\":\"top\",\"effect\":\"IGNORED\"},"
            + "{\"scope\":\"level\",\"name\":\"mid\",\"node\":\"m1\",\"effect\":\"IGNORED\"}]",
        string(details, TierwiseMetadata.CONTRIBUTIONS));
  }

  @Test
  void aVetoShowsInTheContributionSummary() {
    FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("k.all", true, levels("mid", "m2"));
    assertEquals(false, details.getValue());
    assertTrue(string(details, TierwiseMetadata.CONTRIBUTIONS).contains("\"node\":\"m2\",\"effect\":\"VETO\""));
  }

  @Test
  void aCommutativeKeyNobodySetsIsTheDefaultReason() {
    FlagEvaluationDetails<Boolean> details = client.getBooleanDetails("k.any", true, new ImmutableContext());
    assertEquals(false, details.getValue(), "the identity of any is false, not the caller's default");
    assertEquals(Reason.DEFAULT.toString(), details.getReason());
    assertEquals("[]", string(details, TierwiseMetadata.CONTRIBUTIONS));
  }

  // ---- integer and double --------------------------------------------------------------------------

  @Test
  void anIntegerKeyReadsAsAnIntegerWithItsProvenance() {
    FlagEvaluationDetails<Integer> details =
        client.getIntegerDetails("k.over", 0, of(Map.of("mid", "m1"), Map.of("lane", "l1"), null));
    assertEquals(8, details.getValue());
    assertEquals(Reason.TARGETING_MATCH.toString(), details.getReason());
    assertEquals("conf", string(details, TierwiseMetadata.KIND));
    assertEquals("override", string(details, TierwiseMetadata.STRATEGY));
    assertEquals("axis:lane", string(details, TierwiseMetadata.SOURCE));
    assertEquals("l1", string(details, TierwiseMetadata.SOURCE_NODE));
    assertEquals(
        "[{\"scope\":\"level\",\"name\":\"top\",\"effect\":\"SET\"},"
            + "{\"scope\":\"level\",\"name\":\"mid\",\"node\":\"m1\",\"effect\":\"SET\"},"
            + "{\"scope\":\"axis\",\"name\":\"lane\",\"node\":\"l1\",\"effect\":\"SET\"}]",
        string(details, TierwiseMetadata.CONTRIBUTIONS));
    assertTrue(string(details, TierwiseMetadata.BUNDLE_VERSION).startsWith("sha256:"));
  }

  @Test
  void aValueFromTheRootLevelAloneIsStaticAndNamesItsSource() {
    FlagEvaluationDetails<Integer> details = client.getIntegerDetails("k.over", 0, new ImmutableContext());
    assertEquals(1, details.getValue());
    assertEquals(Reason.STATIC.toString(), details.getReason());
    assertEquals("level:top", string(details, TierwiseMetadata.SOURCE));
    assertNull(details.getFlagMetadata().getString(TierwiseMetadata.SOURCE_NODE));
  }

  @Test
  void aDeclaredDefaultIsTheDefaultReason() {
    FlagEvaluationDetails<Double> details = client.getDoubleDetails("k.big", 0.0, new ImmutableContext());
    assertEquals(3_000_000_000.0, details.getValue());
    assertEquals(Reason.DEFAULT.toString(), details.getReason());
    assertEquals("DEFAULT", string(details, TierwiseMetadata.SOURCE));
  }

  @Test
  void anIntegerOutsideTheIntRangeIsATypeMismatchForAnIntegerFlag() {
    FlagEvaluationDetails<Integer> details = client.getIntegerDetails("k.big", 7, new ImmutableContext());
    assertEquals(7, details.getValue(), "the caller's default comes back");
    assertEquals(ErrorCode.TYPE_MISMATCH, details.getErrorCode());
    assertEquals(Reason.ERROR.toString(), details.getReason());
    assertTrue(details.getErrorMessage().contains("3000000000"), details.getErrorMessage());
    assertEquals("key", string(details, TierwiseMetadata.FLAG_KIND), "the metadata of the result is kept");
  }

  @Test
  void theSmallestIntIsInRange() {
    assertEquals(Integer.MIN_VALUE, client.getIntegerValue("k.neg", 0, new ImmutableContext()));
  }

  @Test
  void aLongFlagReadsAnIntegerKey() {
    assertEquals(3_000_000_000L, client.getLongValue("k.big", 0L, new ImmutableContext()));
  }

  @Test
  void aKeyWithoutAValueResolvesToTheCallersDefault() {
    FlagEvaluationDetails<Integer> none = client.getIntegerDetails("k.max", 42, new ImmutableContext());
    assertEquals(42, none.getValue());
    assertEquals(Reason.DEFAULT.toString(), none.getReason());
    assertNull(none.getErrorCode());
    assertEquals("true", String.valueOf(none.getFlagMetadata().getBoolean(TierwiseMetadata.VALUE_ABSENT)));

    FlagEvaluationDetails<Integer> some = client.getIntegerDetails("k.max", 42, of(null, Map.of("lane", "l1"), null));
    assertEquals(3, some.getValue());
    assertEquals(Reason.TARGETING_MATCH.toString(), some.getReason());
    assertNull(some.getFlagMetadata().getBoolean(TierwiseMetadata.VALUE_ABSENT));
  }

  // ---- string --------------------------------------------------------------------------------------

  @Test
  void aStringKeyAndADecimalKeyReadAsStrings() {
    assertEquals("root", client.getStringValue("k.root", "x", new ImmutableContext()));
    assertEquals("mid", client.getStringValue("k.root", "x", levels("mid", "m2")));
    assertEquals("1.5", client.getStringValue("k.dec", "x", new ImmutableContext()), "a decimal is its canonical string");
  }

  @Test
  void aDecimalIsNotADouble() {
    FlagEvaluationDetails<Double> details = client.getDoubleDetails("k.dec", 0.5, new ImmutableContext());
    assertEquals(0.5, details.getValue());
    assertEquals(ErrorCode.TYPE_MISMATCH, details.getErrorCode());
  }

  @Test
  void aServiceSourcedValueComesFromTheProviderAndIsATargetingMatch() {
    Client served =
        client(TierwiseProvider.of(TestBundles.evaluator(node -> Map.of("k.text", "from-" + node))));
    FlagEvaluationDetails<String> details = served.getStringDetails("k.text", "x", levels("low", "n7"));
    assertEquals("from-n7", details.getValue());
    assertEquals(Reason.TARGETING_MATCH.toString(), details.getReason());
    assertEquals("level:low", string(details, TierwiseMetadata.SOURCE));
    assertEquals("n7", string(details, TierwiseMetadata.SOURCE_NODE));
  }

  @Test
  void aSourceFailureIsNotAnErrorAndShowsInTheSummary() {
    Client failing =
        client(
            TierwiseProvider.of(
                TestBundles.evaluator(
                    node -> {
                      throw new IllegalStateException("down");
                    })));
    FlagEvaluationDetails<String> details = failing.getStringDetails("k.text", "x", levels("low", "n7"));
    assertEquals("d", details.getValue(), "skip leaves the declared default");
    assertNull(details.getErrorCode());
    assertEquals(Reason.DEFAULT.toString(), details.getReason());
    assertTrue(
        string(details, TierwiseMetadata.CONTRIBUTIONS)
            .contains("\"effect\":\"UNAVAILABLE\",\"onSourceFailure\":\"skip\""));
  }

  // ---- object --------------------------------------------------------------------------------------

  @Test
  void aSetReadsAsAListInUniverseOrder() {
    FlagEvaluationDetails<Value> details = client.getObjectDetails("k.inter", new Value(), levels("mid", "m2"));
    assertTrue(details.getValue().isList());
    assertEquals(List.of("u3", "u1"), strings(details.getValue()));
    assertEquals(Reason.TARGETING_MATCH.toString(), details.getReason());
  }

  @Test
  void aListReadsAsAListInSourceOrder() {
    Value value = client.getObjectValue("k.list", new Value(), new ImmutableContext());
    assertEquals(List.of("a", "b"), strings(value));
  }

  @Test
  void anObjectReadsAsAStructureWithTheMergedFields() {
    Value value = client.getObjectValue("k.deep", new Value(), levels("mid", "m1"));
    assertTrue(value.isStructure());
    Map<String, Value> fields = value.asStructure().asUnmodifiableMap();
    assertEquals(10L, fields.get("a").asObject() instanceof Number n ? n.longValue() : -1L);
    Map<String, Value> nested = fields.get("b").asStructure().asUnmodifiableMap();
    assertEquals("top", nested.get("c").asString());
    assertEquals("mid", nested.get("d").asString());
  }

  // ---- type mismatches -----------------------------------------------------------------------------

  @Test
  void aKeyReadAsTheWrongTypeIsATypeMismatchWithTheCallersDefault() {
    ImmutableContext none = new ImmutableContext();
    assertMismatch(client.getBooleanDetails("k.over", true, none), true);
    assertMismatch(client.getStringDetails("k.all", "dflt", none), "dflt");
    assertMismatch(client.getIntegerDetails("k.root", 9, none), 9);
    assertMismatch(client.getDoubleDetails("k.root", 9.5, none), 9.5);
    assertMismatch(client.getObjectDetails("k.root", new Value("dflt"), none), new Value("dflt"));
    assertMismatch(client.getObjectDetails("k.over", new Value(), none), new Value());
    assertMismatch(client.getBooleanDetails("k.list", false, none), false);
  }

  private static <T> void assertMismatch(FlagEvaluationDetails<T> details, T expectedDefault) {
    assertEquals(ErrorCode.TYPE_MISMATCH, details.getErrorCode(), details.getFlagKey());
    assertEquals(Reason.ERROR.toString(), details.getReason());
    assertEquals(expectedDefault, details.getValue());
    assertFalse(details.getErrorMessage().isEmpty());
  }

  // ---- context ---------------------------------------------------------------------------------------

  @Test
  void otherAttributesAndTheTargetingKeyAreIgnored() {
    FlagEvaluationDetails<Integer> details =
        client.getIntegerDetails(
            "k.over", 0, context(pairs("targetingKey", "user-1", "mid", "m1", "country", "x")));
    assertEquals(1, details.getValue(), "a bare attribute named like a level is not a level");
    assertEquals(Reason.STATIC.toString(), details.getReason());
  }

  @Test
  void aKeyIgnoresTheFacts() {
    FlagEvaluationDetails<Integer> details =
        client.getIntegerDetails("k.over", 0, of(null, null, pairs("facts", "not even a map of facts")));
    assertEquals(1, details.getValue());
    assertNull(details.getErrorCode());
  }

  @Test
  void contextsMergeAttributeByAttributeAcrossTheOpenFeatureLayers() {
    client.setEvaluationContext(of(Map.of("mid", "m1"), null, null));
    assertEquals(6, client.getIntegerValue("k.over", 0, new ImmutableContext()));
    FlagEvaluationDetails<Integer> merged =
        client.getIntegerDetails("k.over", 0, of(null, Map.of("lane", "l1"), null));
    assertEquals(8, merged.getValue(), "the client context supplies the level, the call supplies the axis");
    assertEquals(
        6,
        client.getIntegerValue("k.over", 0, of(Map.of("mid", "m1"), null, null)),
        "an attribute set again replaces the whole earlier attribute");
  }

  private static List<String> strings(Value list) {
    return list.asList().stream().map(Value::asString).toList();
  }
}
