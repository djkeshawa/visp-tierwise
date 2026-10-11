package io.github.visplabs.tierwise.cli.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The string literals that generated code uses for declared names. */
class LiteralsTest {

  @Test
  void copiesPrintableAsciiBetweenQuotes() {
    assertEquals("\"checkout.order-1 x\"", Literals.quote("checkout.order-1 x"));
  }

  @Test
  void escapesTheQuoteAndTheBackslash() {
    assertEquals("\"a\\\"b\\\\c\"", Literals.quote("a\"b\\c"));
  }

  @Test
  void usesTheShortEscapesForTheFiveControlCharacters() {
    assertEquals("\"\\b\\t\\n\\f\\r\"", Literals.quote("\b\t\n\f\r"));
  }

  @Test
  void writesOtherCharactersAsUnicodeEscapes() {
    assertEquals("\"\\u00e9\\u0001\\u007f\"", Literals.quote("é\u0001\u007f"));
  }

  @Test
  void writesSurrogatePairsAsTwoUnitEscapes() {
    assertEquals("\"\\ud83d\\ude00\"", Literals.quote("😀"));
  }
}
