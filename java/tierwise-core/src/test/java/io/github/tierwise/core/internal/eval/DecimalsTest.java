package io.github.tierwise.core.internal.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DecimalsTest {

  @Test
  void TYPE_24_acceptsOnlyTheRuntimeDecimalSyntax() {
    for (String valid : new String[] {"0", "1.50", "+007.100", "-0.0", "00.5", "123456789012345678901234567890.1"}) {
      assertTrue(Decimals.matches(valid), valid);
    }
    for (String invalid : new String[] {"", "+", "-", "1.", ".5", "1e3", "1.5e0", " 1", "1 ", "1,5", "--1", "1.2.3",
        "١", "1.٢", "0x10", "NaN"}) {
      assertFalse(Decimals.matches(invalid), invalid);
    }
  }

  @Test
  void NORM_10_canonicalisesTheDocumentedExamples() {
    assertEquals("1.5", Decimals.canonical("1.50"));
    assertEquals("7.1", Decimals.canonical("+007.100"));
    assertEquals("0", Decimals.canonical("-0.0"));
    assertEquals("0.5", Decimals.canonical("00.5"));
    assertEquals("-2", Decimals.canonical("-2.000"));
    assertEquals("0", Decimals.canonical("000"));
    assertEquals("100", Decimals.canonical("0100"));
  }

  @Test
  void NORM_12_equalNumericValuesGiveTheSameCanonicalString() {
    assertEquals(Decimals.canonical("1.5"), Decimals.canonical("+01.500"));
    assertEquals(Decimals.canonical("0"), Decimals.canonical("-0"));
  }

  @Test
  void NORM_11_resultIsACanonicalDecimalString() {
    String canonical = Decimals.canonical("-0012.3400");
    assertTrue(canonical.matches("-?(0|[1-9][0-9]*)(\\.[0-9]*[1-9])?"));
    assertEquals("-12.34", canonical);
  }
}
