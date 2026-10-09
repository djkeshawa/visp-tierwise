package io.github.tierwise.core.internal.check;

import static io.github.tierwise.core.internal.check.CheckTestSupport.root;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.internal.check.TypeDeclParser.Position;
import io.github.tierwise.core.internal.model.TypeDecl;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import io.github.tierwise.core.internal.yaml.YamlLoadResult;
import io.github.tierwise.core.internal.yaml.YamlLoader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Source values against a declaration: TYPE-12 to TYPE-71, NORM-1, NORM-8, NORM-13 and NORM-14. */
class ValueValidatorTest {

  /** The outcome of validating one value: the plain value and the sorted descriptions of the findings. */
  private record Outcome(Optional<Object> value, List<String> findings) {
  }

  private static TypeDecl declare(String yaml) {
    return new TypeDeclParser(new Findings()).parse(root(yaml), Position.KEY).orElseThrow();
  }

  /** Validates the value written as {@code value:} of a one-entry document, so its line is 1. */
  private static Outcome check(String declaration, String value) {
    return check(declaration, value, ValueRules.DEFAULT);
  }

  private static Outcome check(String declaration, String value, ValueRules rules) {
    YamlLoadResult loaded = YamlLoader.load(("value: " + value + "\n").getBytes(StandardCharsets.UTF_8), "values/v.yaml", false);
    PMap document = loaded.root().orElseThrow();
    PNode node = document.get("value").orElseThrow();
    Findings findings = new Findings();
    findings.addAll(loaded.findings());
    Optional<Object> result = new ValueValidator(findings).validate(declare(declaration), node, rules);
    return new Outcome(result, CheckTestSupport.descriptions(findings));
  }

  private static void assertValid(String declaration, String value, Object expected) {
    Outcome outcome = check(declaration, value);
    assertEquals(List.of(), outcome.findings(), declaration + " <- " + value);
    assertEquals(Optional.of(expected), outcome.value(), declaration + " <- " + value);
  }

  private static void assertInvalid(String declaration, String value, String... findings) {
    Outcome outcome = check(declaration, value);
    assertTrue(outcome.value().isEmpty(), declaration + " <- " + value + " gave " + outcome.value());
    List<String> wanted = new java.util.ArrayList<>(List.of(findings));
    java.util.Collections.sort(wanted);
    assertEquals(wanted, outcome.findings(), declaration + " <- " + value);
  }

  // ---- boolean, integer, decimal, string -------------------------------------------------------------

  @Test
  void TYPE_12_aBooleanIsTrueOrFalseAndNothingElse() {
    assertValid("type: boolean", "true", true);
    assertValid("type: boolean", "False", false);
    for (String bad : new String[] {"yes", "\"true\"", "1", "null", "[true]", "{}", "on"}) {
      assertInvalid("type: boolean", bad, "E_TYPE_MISMATCH 1 [value]");
    }
  }

  @Test
  void TYPE_15_aSourceIntegerIsAPlainScalarThatTheCoreSchemaReadsAsAnInteger() {
    assertValid("type: integer", "5", 5L);
    assertValid("type: integer", "+5", 5L);
    assertValid("type: integer", "-0", 0L);
    assertValid("type: integer", "0x1F", 31L);
    assertValid("type: integer", "0o17", 15L);
    for (String bad : new String[] {"\"5\"", "5.5", "true", "null", "[5]", "abc", "1_0"}) {
      assertInvalid("type: integer", bad, "E_TYPE_MISMATCH 1 [value]");
    }
    assertInvalid("type: integer", "5.0", "E_TYPE_MISMATCH 1 [value]");
    assertInvalid("type: integer", "1e3", "E_TYPE_MISMATCH 1 [value]");
  }

  @Test
  void TYPE_18_anIntegerBeyondPlusOrMinus2To53Minus1IsEIntegerRange() {
    assertValid("type: integer", "9007199254740991", 9007199254740991L);
    assertValid("type: integer", "-9007199254740991", -9007199254740991L);
    assertInvalid("type: integer", "9007199254740992", "E_INTEGER_RANGE 1 [value]");
    assertInvalid("type: integer", "-9007199254740992", "E_INTEGER_RANGE 1 [value]");
    assertInvalid("type: integer", "99999999999999999999", "E_INTEGER_RANGE 1 [value]");
    assertInvalid("type: integer", "99999999999999999999999999999999", "E_INTEGER_RANGE 1 [value]");
    assertInvalid("type: integer", "0x7FFFFFFFFFFFFFFFFFFFFF", "E_INTEGER_RANGE 1 [value]");
  }

  @Test
  void TYPE_19_boundsAreInclusive() {
    String declaration = "type: integer\nmin: 1\nmax: 5";
    assertValid(declaration, "1", 1L);
    assertValid(declaration, "5", 5L);
    assertInvalid(declaration, "0", "E_OUT_OF_RANGE 1 [value]");
    assertInvalid(declaration, "6", "E_OUT_OF_RANGE 1 [value]");
    assertInvalid("type: integer\nmin: 1", "-3", "E_OUT_OF_RANGE 1 [value]");
    assertValid("type: integer\nmin: 1", "9007199254740991", 9007199254740991L);
    assertInvalid("type: integer\nmax: 5", "6", "E_OUT_OF_RANGE 1 [value]");
  }

  @Test
  void TYPE_79_aValueThatBreaksBothTheRangeAndTheBoundsGetsTwoFindings() {
    assertInvalid("type: integer\nmin: 0\nmax: 10", "9007199254740992", "E_INTEGER_RANGE 1 [value]", "E_OUT_OF_RANGE 1 [value]");
    assertInvalid("type: integer\nmin: 0\nmax: 10", "-99999999999999999999999999", "E_INTEGER_RANGE 1 [value]",
        "E_OUT_OF_RANGE 1 [value]");
    assertInvalid("type: integer\nmin: 0\nmax: 10", "99999999999999999999999999", "E_INTEGER_RANGE 1 [value]",
        "E_OUT_OF_RANGE 1 [value]");
  }

  @Test
  void TYPE_79_aValueOutsideTheEngineRangeButInsideItsBoundsIsOnlyOutOfRange() {
    assertInvalid("type: integer\nmin: 0", "99999999999999999999999999", "E_INTEGER_RANGE 1 [value]");
    assertInvalid("type: integer\nmax: 0", "-99999999999999999999999999", "E_INTEGER_RANGE 1 [value]");
  }

  @Test
  void NORM_13_aDecimalIsAStringThatIsCanonicalised() {
    assertValid("type: decimal", "\"1.50\"", "1.5");
    assertValid("type: decimal", "\"+007.100\"", "7.1");
    assertValid("type: decimal", "\"-0.0\"", "0");
    assertValid("type: decimal", "\"00.5\"", "0.5");
    assertValid("type: decimal", "\"-12\"", "-12");
    assertValid("type: decimal", "\"0\"", "0");
    assertValid("type: decimal", "\"+0\"", "0");
    assertValid("type: decimal", "\"100.000\"", "100");
    assertValid("type: decimal", "\"-0.50\"", "-0.5");
  }

  @Test
  void TYPE_25_aNumberOrAMalformedStringIsNotADecimal() {
    for (String bad : new String[] {"\"1e3\"", "1.5", "5", "\".5\"", "\"5.\"", "\"\"", "\"1,5\"", "\" 1\"", "\"1 \"",
        "\"\\u0661\"", "\"NaN\"", "null", "true", "\"1.5\\n\""}) {
      Outcome outcome = check("type: decimal", bad);
      assertTrue(outcome.value().isEmpty(), bad);
      assertEquals(List.of("E_TYPE_MISMATCH 1 [value]"), outcome.findings(), bad);
    }
  }

  @Test
  void TYPE_28_aStringMustBeAString() {
    assertValid("type: string", "abc", "abc");
    assertValid("type: string", "\"\"", "");
    assertValid("type: string", "\"5\"", "5");
    assertValid("type: string", "yes", "yes");
    for (String bad : new String[] {"5", "true", "null", "1.5", "[a]", "{a: b}"}) {
      assertInvalid("type: string", bad, "E_TYPE_MISMATCH 1 [value]");
    }
  }

  @Test
  void NORM_1_stringsAreNormalisedToNfc() {
    assertValid("type: string", "\"cafe\u0301\"", "caf\u00e9");
    assertValid("type: string", "\"caf\u00e9\"", "caf\u00e9");
  }

  @Test
  void NORM_8_aStringWithAnUnpairedSurrogateIsInvalid() {
    assertInvalid("type: string", "\"\\ud800\"", "E_TYPE_MISMATCH 1 [value]");
    assertInvalid("type: string", "\"a\\udc00b\"", "E_TYPE_MISMATCH 1 [value]");
    assertValid("type: string", "\"\\ud83d\\ude00\"", "\uD83D\uDE00");
  }

  @Test
  void TYPE_33_aTimeMatchesHhMmInFull() {
    String declaration = "type: string\nformat: time_hhmm";
    assertValid(declaration, "\"00:00\"", "00:00");
    assertValid(declaration, "\"23:59\"", "23:59");
    assertValid(declaration, "\"09:05\"", "09:05");
    for (String bad : new String[] {"\"24:00\"", "\"9:05\"", "\"09:5\"", "\"09:60\"", "\"09:05 \"", "\" 09:05\"", "\"09:05\\n\"",
        "\"\"", "\"0905\"", "\"09:05:00\"", "\"\\u0660\\u0669:05\"", "\"09-05\""}) {
      assertInvalid(declaration, bad, "E_TYPE_MISMATCH 1 [value]");
    }
  }

  @Test
  void TYPE_34_anIanaTzValueIsAListedZoneOrLinkNameCodePointForCodePoint() {
    String declaration = "type: string\nformat: iana_tz";
    assertValid(declaration, "Europe/Paris", "Europe/Paris");
    assertValid(declaration, "UTC", "UTC");
    assertValid(declaration, "America/Argentina/Buenos_Aires", "America/Argentina/Buenos_Aires");
    for (String bad : new String[] {"europe/paris", "Europe/Paris ", "Europe/Pariss", "Mars/Olympus", "", "Etc/GMT+0 "}) {
      assertInvalid(declaration, "\"" + bad + "\"", "E_TYPE_MISMATCH 1 [value]");
    }
  }

  // ---- enum, set, list, object ---------------------------------------------------------------------

  @Test
  void TYPE_44_anEnumValueEqualsOneEntryOfValuesUnderNfc() {
    String declaration = "type: enum\nvalues: [ALLOW, DENY, \"caf\u00e9\"]";
    assertValid(declaration, "ALLOW", "ALLOW");
    assertValid(declaration, "\"cafe\u0301\"", "caf\u00e9");
    for (String bad : new String[] {"\"allow\"", "\"ALLOW \"", "OTHER", "5", "null", "[ALLOW]"}) {
      assertInvalid(declaration, bad, "E_TYPE_MISMATCH 1 [value]");
    }
  }

  @Test
  void TYPE_45_aSetIsAListOfStringsStoredInUniverseOrder() {
    String declaration = "type: set\nuniverse: [north, south, west]";
    assertValid(declaration, "[west, north]", List.of("north", "west"));
    assertValid(declaration, "[]", List.of());
    assertValid(declaration, "[south, north, west]", List.of("north", "south", "west"));
    assertInvalid(declaration, "north", "E_TYPE_MISMATCH 1 [value]");
    assertInvalid(declaration, "{north: 1}", "E_TYPE_MISMATCH 1 [value]");
    assertInvalid(declaration, "[north, 5]", "E_TYPE_MISMATCH 1 [value, 1]");
    assertInvalid(declaration, "null", "E_TYPE_MISMATCH 1 [value]");
  }

  @Test
  void TYPE_48_aMemberOutsideTheUniverseIsENotInUniverseAndEachOneIsReported() {
    assertInvalid("type: set\nuniverse: [a, b]", "[a, c, d]", "E_NOT_IN_UNIVERSE 1 [value, 1]", "E_NOT_IN_UNIVERSE 1 [value, 2]");
  }

  @Test
  void TYPE_52_aRepeatedMemberIsEDuplicateValueAtTheLaterOccurrenceAfterNfc() {
    assertInvalid("type: set\nuniverse: [a, b]", "[a, b, a]", "E_DUPLICATE_VALUE 1 [value, 2]");
    assertInvalid("type: set\nuniverse: [\"caf\u00e9\"]", "[\"caf\u00e9\", \"cafe\u0301\"]", "E_DUPLICATE_VALUE 1 [value, 1]");
    assertValid("type: set\nuniverse: [\"caf\u00e9\"]", "[\"cafe\u0301\"]", List.of("caf\u00e9"));
  }

  @Test
  void TYPE_56_everyElementOfAListIsValidatedAndEachInvalidElementIsReported() {
    String declaration = "type: list\nitems: {type: integer, min: 1}";
    assertValid(declaration, "[1, 2, 2]", List.of(1L, 2L, 2L));
    assertValid(declaration, "[]", List.of());
    assertInvalid(declaration, "[1, x, 0, 3, true]", "E_TYPE_MISMATCH 1 [value, 1]", "E_OUT_OF_RANGE 1 [value, 2]",
        "E_TYPE_MISMATCH 1 [value, 4]");
    assertInvalid(declaration, "5", "E_TYPE_MISMATCH 1 [value]");
    assertInvalid(declaration, "{a: 1}", "E_TYPE_MISMATCH 1 [value]");
  }

  private static final String OBJECT = "type: object\nfields:\n  a: {type: string}\n  b: {type: integer, optional: true}\n  c: {type: boolean}";

  @Test
  void TYPE_61_anObjectIsAMapWithItsFieldsValidated() {
    assertValid(OBJECT, "{a: x, c: true}", Map.of("a", "x", "c", true));
    assertValid(OBJECT, "{c: true, b: 4, a: x}", Map.of("a", "x", "b", 4L, "c", true));
    assertInvalid(OBJECT, "[a]", "E_TYPE_MISMATCH 1 [value]");
    assertInvalid(OBJECT, "x", "E_TYPE_MISMATCH 1 [value]");
    assertInvalid(OBJECT, "null", "E_TYPE_MISMATCH 1 [value]");
  }

  @Test
  void TYPE_63_aMissingRequiredFieldIsETypeMismatchAtTheObject() {
    assertInvalid(OBJECT, "{a: x}", "E_TYPE_MISMATCH 1 [value]");
    assertInvalid(OBJECT, "{}", "E_TYPE_MISMATCH 1 [value]");
  }

  @Test
  void TYPE_64_anUnknownFieldIsETypeMismatchAtItsEntryAndIsNeverIgnored() {
    assertInvalid(OBJECT, "{a: x, c: true, z: 1}", "E_TYPE_MISMATCH 1 [value, z]");
  }

  @Test
  void TYPE_23_anUnknownFieldAndAMissingFieldAndABadFieldAreThreeFindings() {
    assertInvalid(OBJECT, "{a: 5, z: 1}", "E_TYPE_MISMATCH 1 [value, a]", "E_TYPE_MISMATCH 1 [value, z]",
        "E_TYPE_MISMATCH 1 [value]");
  }

  @Test
  void TYPE_65_aFieldValueIsValidatedAgainstItsDeclaration() {
    assertInvalid(OBJECT, "{a: x, c: true, b: 1.5}", "E_TYPE_MISMATCH 1 [value, b]");
    assertInvalid(OBJECT, "{a: x, c: yes}", "E_TYPE_MISMATCH 1 [value, c]");
  }

  @Test
  void TYPE_67_aNullFieldIsNotAValueEvenForAnOptionalField() {
    assertInvalid(OBJECT, "{a: x, c: true, b: null}", "E_TYPE_MISMATCH 1 [value, b]");
    assertInvalid(OBJECT, "{a: null, c: true}", "E_TYPE_MISMATCH 1 [value, a]");
  }

  @Test
  void TYPE_62_aPartialObjectMayLackRequiredFieldsAtEveryDepthWhenThePartialRulesAreUsed() {
    String nested = "type: object\nfields:\n  a: {type: string}\n  inner:\n    type: object\n    fields:\n      x: {type: integer}\n"
        + "      y: {type: integer}";
    Outcome outcome = check(nested, "{inner: {x: 1}}", ValueRules.DEFAULT.partial());
    assertEquals(List.of(), outcome.findings());
    assertEquals(Optional.of(Map.of("inner", Map.of("x", 1L))), outcome.value());
    assertEquals(List.of("E_TYPE_MISMATCH 1 [value, inner]", "E_TYPE_MISMATCH 1 [value]"),
        check(nested, "{inner: {x: 1}}").findings());
    assertEquals(List.of("E_TYPE_MISMATCH 1 [value, z]"), check(nested, "{z: 1}", ValueRules.DEFAULT.partial()).findings());
  }

  @Test
  void NORM_1_objectKeysAreNormalisedToNfc() {
    String declaration = "type: object\nfields:\n  \"caf\u00e9\": {type: integer}";
    assertValid(declaration, "{\"cafe\u0301\": 1}", Map.of("caf\u00e9", 1L));
  }

  @Test
  void TYPE_69_declarationsNestToAnyDepth() {
    String declaration = "type: list\nitems:\n  type: object\n  fields:\n    tags:\n      type: set\n      universe: [a, b]\n"
        + "    window:\n      type: object\n      fields:\n        start: {type: string, format: time_hhmm}";
    assertValid(declaration, "[{tags: [b, a], window: {start: \"08:30\"}}]",
        List.of(Map.of("tags", List.of("a", "b"), "window", Map.of("start", "08:30"))));
    assertInvalid(declaration, "[{tags: [c], window: {start: \"8:30\"}}]", "E_NOT_IN_UNIVERSE 1 [value, 0, tags, 0]",
        "E_TYPE_MISMATCH 1 [value, 0, window, start]");
  }

  // ---- codes -----------------------------------------------------------------------------------------

  @Test
  void TYPE_71_theCodeOfTheOwningClauseReplacesEveryCodeOfSection4() {
    ValueRules owner = ValueRules.DEFAULT.withOwnerCode("E_INVALID_SETTING");
    assertEquals(List.of("E_INVALID_SETTING 1 [value]"), check("type: integer\nmin: 0", "-1", owner).findings());
    assertEquals(List.of("E_INVALID_SETTING 1 [value]"), check("type: integer\nmin: 0", "x", owner).findings());
    assertEquals(List.of("E_INVALID_SETTING 1 [value]"), check("type: integer\nmin: 0\nmax: 1", "99999999999999999999999", owner).findings());
    assertEquals(List.of("E_INVALID_SETTING 1 [value, 1]"), check("type: set\nuniverse: [a]", "[a, b]", owner).findings());
    assertEquals(List.of("E_INVALID_SETTING 1 [value, 1]"), check("type: set\nuniverse: [a]", "[a, a]", owner).findings());
  }

  @Test
  void aValidValueGivesAnUnmodifiablePlainValue() {
    Object list = check("type: list\nitems: {type: string}", "[a]").value().orElseThrow();
    org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class, () -> ((List<?>) list).clear());
  }

  @Test
  void aFloatTheLoaderRefusedIsAMismatchAtTheSameKeyAsTheLoaderFinding() {
    Outcome outcome = check("type: integer", "2.0");
    assertEquals(List.of("E_TYPE_MISMATCH 1 [value]"), outcome.findings());
  }
}
