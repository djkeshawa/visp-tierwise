package io.github.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Decimal syntax and canonical form (TYPE-24, NORM-9, NORM-10), time (TYPE-33), surrogates (NORM-8), zone names (TYPE-34). */
class ValueTextTest {

  @Test
  void NORM_10_theWorkedExamplesOfTheSpecification() {
    assertEquals("1.5", ValueText.canonicalDecimal("1.50"));
    assertEquals("7.1", ValueText.canonicalDecimal("+007.100"));
    assertEquals("0", ValueText.canonicalDecimal("-0.0"));
    assertEquals("0.5", ValueText.canonicalDecimal("00.5"));
  }

  @Test
  void NORM_10_zeroHasOneSpellingAndTheSignIsKeptOnlyForNonZero() {
    for (String zero : List.of("0", "+0", "-0", "000", "0.000", "-0.000", "+00.0")) {
      assertEquals("0", ValueText.canonicalDecimal(zero), zero);
    }
    assertEquals("-1", ValueText.canonicalDecimal("-1.0"));
    assertEquals("-0.001", ValueText.canonicalDecimal("-0.0010"));
    assertEquals("10", ValueText.canonicalDecimal("0010"));
    assertEquals("100", ValueText.canonicalDecimal("100.00"));
  }

  @Test
  void NORM_12_equalValuesGiveTheSameCanonicalString() {
    assertEquals(ValueText.canonicalDecimal("1.5"), ValueText.canonicalDecimal("+01.500"));
    assertEquals(ValueText.canonicalDecimal("12345678901234567890.10"), "12345678901234567890.1");
  }

  @Test
  void NORM_11_theResultIsCanonicalAndIdempotent() {
    for (String text : List.of("0", "-5.25", "123.456", "+0.0010", "00012")) {
      String once = ValueText.canonicalDecimal(text);
      assertTrue(once.matches("-?(0|[1-9][0-9]*)(\\.[0-9]*[1-9])?") && !once.equals("-0"), once);
      assertEquals(once, ValueText.canonicalDecimal(once));
    }
  }

  @Test
  void TYPE_24_aDecimalMatchesTheSyntaxInFullWithAsciiDigits() {
    for (String good : List.of("0", "+1", "-1", "1.5", "007", "0.0", "+0.50")) {
      assertTrue(ValueText.isDecimal(good), good);
    }
    for (String bad : List.of("", ".5", "5.", "1e3", "1,5", " 1", "1 ", "1\n", "+", "-", "--1", "1.2.3", "\u0661", "0x1")) {
      assertFalse(ValueText.isDecimal(bad), bad);
    }
  }

  @Test
  void TYPE_33_aTimeMatchesInFullAndNeverBeforeAFinalLineTerminator() {
    assertTrue(ValueText.isTimeHhmm("00:00"));
    assertTrue(ValueText.isTimeHhmm("19:59"));
    assertTrue(ValueText.isTimeHhmm("23:59"));
    assertFalse(ValueText.isTimeHhmm("24:00"));
    assertFalse(ValueText.isTimeHhmm("12:30\n"));
    assertFalse(ValueText.isTimeHhmm("12:30 "));
    assertFalse(ValueText.isTimeHhmm("1:30"));
    assertFalse(ValueText.isTimeHhmm("\u0661\u0662:30"));
  }

  @Test
  void NORM_8_unpairedSurrogatesAreDetected() {
    assertFalse(ValueText.hasUnpairedSurrogate(""));
    assertFalse(ValueText.hasUnpairedSurrogate("a\uD83D\uDE00b"));
    assertTrue(ValueText.hasUnpairedSurrogate("\uD83D"));
    assertTrue(ValueText.hasUnpairedSurrogate("\uDE00"));
    assertTrue(ValueText.hasUnpairedSurrogate("\uDE00\uD83D"));
    assertTrue(ValueText.hasUnpairedSurrogate("\uD83Dx"));
  }

  @Test
  void TYPE_34_theZoneNamesAreThoseOfTheSpecDataFileAndNothingElse() throws Exception {
    Path file = Path.of(System.getProperty("tierwise.spec.dir")).resolve("data/tz-names.txt");
    List<String> names = Files.readAllLines(file, StandardCharsets.UTF_8);
    assertTrue(names.size() > 500);
    for (String name : names) {
      assertTrue(TimeZoneNames.contains(name), name);
    }
    assertTrue(TimeZoneNames.contains("Europe/Paris"));
    assertTrue(TimeZoneNames.contains("UTC"));
    assertFalse(TimeZoneNames.contains("europe/paris"));
    assertFalse(TimeZoneNames.contains(""));
    assertFalse(TimeZoneNames.contains("Europe/Paris\n"));
    assertFalse(TimeZoneNames.contains("Factory"));
  }
}
