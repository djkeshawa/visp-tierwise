package io.github.visplabs.tierwise.openfeature;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.openfeature.sdk.ErrorCode;
import dev.openfeature.sdk.Value;
import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.Resolved;
import io.github.visplabs.tierwise.core.ScopeKind;
import io.github.visplabs.tierwise.core.SkippedScope;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** The pure mapping pieces: error codes, value types, value conversion and the JSON of the metadata. */
class MappingUnitTest {

  // ---- error codes ---------------------------------------------------------------------------------------

  @Test
  void everyEvaluationCodeMapsToItsOpenFeatureCode() {
    Map<String, ErrorCode> expected = new LinkedHashMap<>();
    expected.put(Code.E_UNKNOWN_KEY, ErrorCode.FLAG_NOT_FOUND);
    expected.put(Code.E_UNKNOWN_FEATURE, ErrorCode.FLAG_NOT_FOUND);
    expected.put(Code.E_CONTEXT_INVALID, ErrorCode.INVALID_CONTEXT);
    expected.put(Code.E_UNKNOWN_SCOPE, ErrorCode.INVALID_CONTEXT);
    expected.put(Code.E_UNKNOWN_NODE, ErrorCode.INVALID_CONTEXT);
    expected.put(Code.E_FACTS_INVALID, ErrorCode.INVALID_CONTEXT);
    expected.put(Code.E_PROVIDER_REQUIRED, ErrorCode.GENERAL);
    expected.put(Code.E_PROVIDER_MISSING, ErrorCode.GENERAL);
    expected.put(Code.E_EVALUATOR_OPTIONS, ErrorCode.GENERAL);
    expected.put(Code.E_BUNDLE_INTEGRITY, ErrorCode.GENERAL);
    expected.put(Code.E_BUNDLE_UNSUPPORTED, ErrorCode.GENERAL);
    expected.put("E_SOMETHING_NEW", ErrorCode.GENERAL);
    expected.forEach((code, error) -> assertEquals(error, Errors.map(code), code));
  }

  // ---- targets ---------------------------------------------------------------------------------------------

  @Test
  void theIntegerTargetAcceptsExactlyTheIntRange() {
    assertEquals(Integer.MAX_VALUE, Target.INTEGER.convert((long) Integer.MAX_VALUE));
    assertEquals(Integer.MIN_VALUE, Target.INTEGER.convert((long) Integer.MIN_VALUE));
    assertThrows(Target.Mismatch.class, () -> Target.INTEGER.convert(Integer.MAX_VALUE + 1L));
    assertThrows(Target.Mismatch.class, () -> Target.INTEGER.convert(Integer.MIN_VALUE - 1L));
    assertThrows(Target.Mismatch.class, () -> Target.INTEGER.convert("1"));
  }

  @Test
  void theDoubleTargetAcceptsIntegersThatADoubleHoldsExactly() {
    long limit = 1L << 53;
    assertEquals((double) limit, Target.DOUBLE.convert(limit));
    assertEquals((double) -limit, Target.DOUBLE.convert(-limit));
    assertThrows(Target.Mismatch.class, () -> Target.DOUBLE.convert(limit + 1));
    assertThrows(Target.Mismatch.class, () -> Target.DOUBLE.convert(-limit - 1));
    assertThrows(Target.Mismatch.class, () -> Target.DOUBLE.convert("1.5"));
  }

  @Test
  void theOtherTargetsAcceptOnlyTheirOwnKind() {
    assertEquals(true, Target.BOOLEAN.convert(true));
    assertThrows(Target.Mismatch.class, () -> Target.BOOLEAN.convert("true"));
    assertEquals("x", Target.STRING.convert("x"));
    assertThrows(Target.Mismatch.class, () -> Target.STRING.convert(1L));
    assertEquals(new Value(List.of(new Value("a"))), Target.OBJECT.convert(List.of("a")));
    assertThrows(Target.Mismatch.class, () -> Target.OBJECT.convert("a"));
    assertThrows(Target.Mismatch.class, () -> Target.OBJECT.convert(true));
  }

  @Test
  void onlyStringAndObjectTargetsReadFeatures() {
    assertEquals(false, Target.BOOLEAN.readsFeatures());
    assertEquals(false, Target.INTEGER.readsFeatures());
    assertEquals(false, Target.DOUBLE.readsFeatures());
    assertEquals(true, Target.STRING.readsFeatures());
    assertEquals(true, Target.OBJECT.readsFeatures());
  }

  // ---- values ------------------------------------------------------------------------------------------------

  @Test
  void plainValuesBecomeOpenFeatureValues() {
    Map<String, Object> plain = new LinkedHashMap<>();
    plain.put("flag", true);
    plain.put("count", 7L);
    plain.put("name", "n");
    plain.put("items", List.of("a", List.of(1L)));
    plain.put("nested", Map.of("k", "v"));
    Value value = PlainValues.toValue(plain);
    Map<String, Value> members = value.asStructure().asUnmodifiableMap();
    assertEquals(java.util.Set.of("flag", "count", "name", "items", "nested"), members.keySet(), "member order is not significant (PROV-85)");
    assertEquals(true, members.get("flag").asBoolean());
    assertEquals(7L, members.get("count").asLong());
    assertEquals("n", members.get("name").asString());
    assertEquals("a", members.get("items").asList().get(0).asString());
    assertEquals(1L, members.get("items").asList().get(1).asList().get(0).asLong());
    assertEquals("v", members.get("nested").asStructure().getValue("k").asString());
    assertThrows(IllegalArgumentException.class, () -> PlainValues.toValue(1.5));
  }

  @Test
  void openFeatureValuesBecomePlainValues() {
    Value value = Value.objectToValue(Map.of("i", 3, "l", 4L, "d", 2.5, "s", "t", "b", false, "list", List.of(1, "x")));
    @SuppressWarnings("unchecked")
    Map<String, Object> plain = (Map<String, Object>) PlainValues.fromValue(value);
    assertEquals(3L, plain.get("i"), "an Integer widens to a Long");
    assertEquals(4L, plain.get("l"));
    assertEquals(2.5, plain.get("d"), "a Double is passed on for the evaluator to judge");
    assertEquals("t", plain.get("s"));
    assertEquals(false, plain.get("b"));
    assertEquals(List.of(1L, "x"), plain.get("list"));
    assertNull(PlainValues.fromValue(null));
    assertNull(PlainValues.fromValue(new Value()));
  }

  // ---- JSON of the metadata -----------------------------------------------------------------------------------

  @Test
  void contributionsAreWrittenInAFixedMemberOrder() {
    List<Resolved.Contribution> entries =
        List.of(
            new Resolved.Contribution(
                ScopeKind.LEVEL, "top", Optional.empty(), Optional.of(true), Resolved.Contribution.Label.IGNORED, Optional.empty()),
            new Resolved.Contribution(
                ScopeKind.LEVEL, "low", Optional.of("n\"1"), Optional.of(false), Resolved.Contribution.Label.UNAVAILABLE, Optional.of("deny")),
            new Resolved.Contribution(
                ScopeKind.AXIS, "lane", Optional.of("l1"), Optional.empty(), Resolved.Contribution.Label.INVALID, Optional.of("skip")));
    assertEquals(
        "[{\"scope\":\"level\",\"name\":\"top\",\"effect\":\"IGNORED\"},"
            + "{\"scope\":\"level\",\"name\":\"low\",\"node\":\"n\\\"1\",\"effect\":\"UNAVAILABLE\",\"onSourceFailure\":\"deny\"},"
            + "{\"scope\":\"axis\",\"name\":\"lane\",\"node\":\"l1\",\"effect\":\"INVALID\",\"onSourceFailure\":\"skip\"}]",
        Json.contributions(entries));
    assertEquals("[]", Json.contributions(List.of()));
  }

  @Test
  void skippedScopesAndStringsAreWritten() {
    assertEquals(
        "[{\"scope\":\"axis\",\"name\":\"lane\",\"node\":\"zz\",\"reason\":\"UNKNOWN_NODE\"}]",
        Json.skippedScopes(List.of(new SkippedScope(ScopeKind.AXIS, "lane", "zz"))));
    assertEquals("[\"a.b\",\"c\"]", Json.strings(List.of("a.b", "c")));
    assertEquals("[]", Json.strings(List.of()));
  }

  @Test
  void stringsAreEscapedAsJsonStringifyDoes() {
    assertEquals(
        "[\"q\\\"b\\\\s\\n\\r\\t\\b\\f\\u0001\\u001f é€\"]",
        Json.strings(List.of("q\"b\\s\n\r\t\b\f\u0001\u001f é€")));
  }
}
