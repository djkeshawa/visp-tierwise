package io.github.visplabs.tierwise.core.internal.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Runtime validation of fact values and provider values (FACT-35, SVC-31, TYPE-12 to TYPE-77, NORM-*). */
class RuntimeValuesTest {

  private static final TypeDecl COLOURS = TypeDecl.set(List.of("red", "green", "blue"));

  private static Optional<Object> check(TypeDecl type, Object value) {
    return RuntimeValues.validate(type, value);
  }

  private static void assertInvalid(TypeDecl type, Object value) {
    assertTrue(check(type, value).isEmpty(), "expected invalid: " + value);
  }

  @Test
  void TYPE_12_booleanAcceptsOnlyBooleans() {
    assertEquals(true, check(TypeDecl.simple("boolean"), true).orElseThrow());
    assertInvalid(TypeDecl.simple("boolean"), "true");
    assertInvalid(TypeDecl.simple("boolean"), 1L);
  }

  @Test
  void TYPE_76_nullIsNeverAValue() {
    for (String type : new String[] {"boolean", "integer", "decimal", "string"}) {
      assertInvalid(TypeDecl.simple(type), null);
    }
    assertInvalid(COLOURS, null);
  }

  @Test
  void TYPE_17_anIntegerIsANumberWithAnIntegralValue() {
    TypeDecl integer = TypeDecl.integer(null, null);
    assertEquals(5L, check(integer, 5L).orElseThrow());
    assertEquals(5L, check(integer, 5).orElseThrow());
    assertEquals(5L, check(integer, (short) 5).orElseThrow());
    assertEquals(5L, check(integer, BigInteger.valueOf(5)).orElseThrow());
    assertEquals(5L, check(integer, new BigDecimal("5.00")).orElseThrow());
    assertInvalid(integer, 1.5);
    assertInvalid(integer, "5");
    assertInvalid(integer, Double.NaN);
    assertInvalid(integer, true);
  }

  @Test
  void TYPE_18_integerIsWithinPlusOrMinus2To53MinusOne() {
    TypeDecl integer = TypeDecl.integer(null, null);
    long limit = (1L << 53) - 1;
    assertEquals(limit, check(integer, limit).orElseThrow());
    assertEquals(-limit, check(integer, -limit).orElseThrow());
    assertInvalid(integer, limit + 1);
    assertInvalid(integer, -limit - 1);
    assertInvalid(integer, new BigInteger("100000000000000000000"));
    assertInvalid(integer, Long.MAX_VALUE);
  }

  @Test
  void TYPE_19_declaredBoundsAreInclusive() {
    TypeDecl bounded = TypeDecl.integer(1L, 5L);
    assertEquals(1L, check(bounded, 1L).orElseThrow());
    assertEquals(5L, check(bounded, 5L).orElseThrow());
    assertInvalid(bounded, 0L);
    assertInvalid(bounded, 6L);
  }

  @Test
  void TYPE_25_aNumberIsNeverADecimal() {
    TypeDecl decimal = TypeDecl.simple("decimal");
    assertInvalid(decimal, 1.5);
    assertInvalid(decimal, 1L);
    assertInvalid(decimal, new BigDecimal("1.5"));
  }

  @Test
  void NORM_15_decimalStringsAreCanonicalisedAndBadSyntaxIsInvalid() {
    TypeDecl decimal = TypeDecl.simple("decimal");
    assertEquals("1.5", check(decimal, "+1.50").orElseThrow());
    assertEquals("0", check(decimal, "-0.0").orElseThrow());
    assertInvalid(decimal, "1.");
    assertInvalid(decimal, ".5");
    assertInvalid(decimal, "1e3");
    assertInvalid(decimal, "١.5");
  }

  @Test
  void NORM_8_aStringWithAnUnpairedSurrogateIsInvalid() {
    assertInvalid(TypeDecl.simple("string"), "x\uD83D");
    assertInvalid(TypeDecl.simple("string"), "\uDE00x");
    assertEquals("x😀", check(TypeDecl.simple("string"), "x😀").orElseThrow());
  }

  @Test
  void NORM_3_stringsAreNeverNormalised() {
    String decomposed = "é";
    assertEquals(decomposed, check(TypeDecl.simple("string"), decomposed).orElseThrow());
    assertInvalid(TypeDecl.enumeration(List.of("é")), decomposed);
  }

  @Test
  void TYPE_33_timeHhmmMatchesInFullWithAsciiDigits() {
    TypeDecl time = TestModels.stringFormat("time_hhmm");
    for (String valid : new String[] {"00:00", "09:59", "19:30", "23:59"}) {
      assertEquals(valid, check(time, valid).orElseThrow());
    }
    for (String invalid : new String[] {"24:00", "7:30", "12:60", "12:5", "12:30\n", "12-30", "1١:30", "", "123:00"}) {
      assertInvalid(time, invalid);
    }
  }

  @Test
  void TYPE_37_ianaTzIsCheckedAgainstTheSyntaxOnly() {
    TypeDecl zone = TestModels.stringFormat("iana_tz");
    for (String valid : new String[] {"Europe/London", "UTC", "Etc/GMT+5", "America/Argentina/Buenos_Aires",
        "Not/A_Real-Zone"}) {
      assertEquals(valid, check(zone, valid).orElseThrow(), valid);
    }
    for (String invalid : new String[] {"", "/UTC", "UTC/", "Europe//London", "Europe/ London", "Europe/London\n",
        "Zürich"}) {
      assertInvalid(zone, invalid);
    }
  }

  @Test
  void TYPE_44_enumValueEqualsOneEntryExactly() {
    TypeDecl stock = TypeDecl.enumeration(List.of("STOCKED", "DROP"));
    assertEquals("DROP", check(stock, "DROP").orElseThrow());
    assertInvalid(stock, "drop");
    assertInvalid(stock, 1L);
  }

  @Test
  void TYPE_46_setValuesHaveNoRepeatAndStayInsideTheUniverse() {
    assertInvalid(COLOURS, List.of("red", "red"));
    assertInvalid(COLOURS, List.of("pink"));
    assertInvalid(COLOURS, "red");
    assertInvalid(COLOURS, List.of(1L));
  }

  @Test
  void TYPE_51_aSetResultListsMembersInUniverseOrder() {
    assertEquals(List.of("red", "blue"), check(COLOURS, List.of("blue", "red")).orElseThrow());
    assertEquals(List.of(), check(COLOURS, List.of()).orElseThrow());
  }

  @Test
  void TYPE_56_everyListElementIsValidAndRepeatsAreAllowed() {
    TypeDecl list = TypeDecl.list(TypeDecl.integer(0L, 9L));
    assertEquals(List.of(1L, 1L), check(list, List.of(1, 1L)).orElseThrow());
    assertInvalid(list, List.of(1L, 10L));
    assertInvalid(list, Map.of());
  }

  @Test
  void TYPE_64_anObjectKeyThatNamesNoDeclaredFieldIsInvalid() {
    TypeDecl object = TypeDecl.object(Map.of("a", TypeDecl.simple("string")));
    assertEquals(Map.of("a", "x"), check(object, Map.of("a", "x")).orElseThrow());
    assertInvalid(object, Map.of("a", "x", "b", "y"));
  }

  @Test
  void TYPE_62_aRequiredFieldMustBePresentUnlessThePartialRuleApplies() {
    Map<String, TypeDecl> fields = new LinkedHashMap<>();
    fields.put("a", TypeDecl.simple("string"));
    fields.put("b", TypeDecl.simple("string").asOptional());
    TypeDecl object = TypeDecl.object(fields);
    assertEquals(Map.of("a", "x"), check(object, Map.of("a", "x")).orElseThrow());
    assertInvalid(object, Map.of("b", "y"));
    assertEquals(Map.of("b", "y"), RuntimeValues.validatePartial(object, Map.of("b", "y"), Map.of("a", "s")).orElseThrow());
  }

  @Test
  void TYPE_67_aNullFieldIsInvalidWhetherOrNotTheFieldIsOptional() {
    Map<String, Object> withNull = new LinkedHashMap<>();
    withNull.put("b", null);
    TypeDecl object = TypeDecl.object(Map.of("b", TypeDecl.simple("string").asOptional()));
    assertInvalid(object, withNull);
    assertTrue(RuntimeValues.validatePartial(object, withNull, Map.of()).isEmpty());
  }

  @Test
  void MERGE_26_aPartialValueMustBeCompleteWhereTheStartingValueSetsNoOptionalObject() {
    Map<String, TypeDecl> inner = new LinkedHashMap<>();
    inner.put("from", TypeDecl.simple("string"));
    inner.put("to", TypeDecl.simple("string"));
    Map<String, TypeDecl> fields = new LinkedHashMap<>();
    fields.put("cap", TypeDecl.integer(null, null));
    fields.put("window", TypeDecl.object(inner).asOptional());
    TypeDecl object = TypeDecl.object(fields);
    Map<String, Object> startWithoutWindow = Map.of("cap", 1L);
    assertTrue(RuntimeValues.validatePartial(object, Map.of("window", Map.of("from", "x")), startWithoutWindow).isEmpty());
    assertTrue(RuntimeValues.validatePartial(object, Map.of("window", Map.of("from", "x", "to", "y")), startWithoutWindow)
        .isPresent());
    Map<String, Object> startWithWindow = Map.of("cap", 1L, "window", Map.of("from", "a", "to", "b"));
    assertTrue(RuntimeValues.validatePartial(object, Map.of("window", Map.of("from", "x")), startWithWindow).isPresent());
  }

  @Test
  void MERGE_12_unknownAndWronglyTypedFieldsStayInvalidInAPartialValue() {
    TypeDecl object = TypeDecl.object(Map.of("cap", TypeDecl.integer(0L, 10L)));
    assertTrue(RuntimeValues.validatePartial(object, Map.of("other", 1L), Map.of("cap", 1L)).isEmpty());
    assertTrue(RuntimeValues.validatePartial(object, Map.of("cap", 11L), Map.of("cap", 1L)).isEmpty());
  }

  @Test
  void EVAL_47_aValidValueSharesNoMutableStateWithTheInput() {
    List<Object> input = new ArrayList<>(List.of("blue", "red"));
    @SuppressWarnings("unchecked")
    List<Object> output = (List<Object>) check(COLOURS, input).orElseThrow();
    input.clear();
    assertEquals(List.of("red", "blue"), output);
    try {
      output.add("green");
      throw new AssertionError("the copy must be unmodifiable");
    } catch (UnsupportedOperationException expected) {
      // expected
    }
  }
}
