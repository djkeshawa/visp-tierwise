package io.github.visplabs.tierwise.cli.codegen;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The naming rules of the generated Java identifiers. */
class IdentifiersTest {

  @Test
  void constantNameUpperCasesAndTurnsSeparatorsIntoUnderscores() {
    assertEquals("CHECKOUT_ORDER_ALLOWED_MAX_LINES", Identifiers.constantName("checkout.order.allowed_max_lines"));
  }

  @Test
  void constantNameSeparatesCamelCaseWords() {
    assertEquals("PICKUP_WINDOW", Identifiers.constantName("pickupWindow"));
  }

  @Test
  void constantNameSeparatesAnUpperCaseLetterThatFollowsADigit() {
    assertEquals("V2_API", Identifiers.constantName("v2Api"));
  }

  @Test
  void constantNameKeepsAnUpperCaseRunTogether() {
    assertEquals("HTTP_CODE", Identifiers.constantName("HTTP_CODE"));
  }

  @Test
  void constantNameGivesANameThatStartsWithADigitALeadingUnderscore() {
    assertEquals("_2FA_CODE", Identifiers.constantName("2fa.code"));
  }

  @Test
  void constantNameReplacesEveryNonAsciiCharacterWithASeparator() {
    assertEquals("CAF_", Identifiers.constantName("café"));
  }

  @Test
  void constantNameRejectsANameWithNoAsciiLetterOrDigit() {
    assertThrows(CodegenException.class, () -> Identifiers.constantName("...-_"));
  }

  @Test
  void enumTypeNameJoinsTheSegmentsWithAnEffectSuffix() {
    assertEquals("CheckoutOrderSubmissionEffect", Identifiers.enumTypeName("checkout.order_submission"));
  }

  @Test
  void enumTypeNameCapitalisesEachSegmentAndKeepsItsOtherLetters() {
    assertEquals("PickupWindowEffect", Identifiers.enumTypeName("pickup_Window"));
  }

  @Test
  void enumTypeNameRejectsANameWithNoAsciiLetterOrDigit() {
    assertThrows(CodegenException.class, () -> Identifiers.enumTypeName("._"));
  }

  @Test
  void claimAcceptsEachIdentifierOnce() {
    Map<String, String> owners = new HashMap<>();
    assertDoesNotThrow(() -> {
      Identifiers.claim(owners, "A", "a.b", "key");
      Identifiers.claim(owners, "B", "b.c", "key");
    });
  }

  @Test
  void claimNamesBothDeclaredNamesWhenTwoMapToOneIdentifier() {
    Map<String, String> owners = new HashMap<>();
    Identifiers.claim(owners, "RICH_A_B_C", "rich.a_b.c", "key");
    CodegenException error = assertThrows(CodegenException.class,
        () -> Identifiers.claim(owners, "RICH_A_B_C", "rich.a.b_c", "key"));
    assertTrue(error.getMessage().contains("\"rich.a_b.c\""), error.getMessage());
    assertTrue(error.getMessage().contains("\"rich.a.b_c\""), error.getMessage());
    assertTrue(error.getMessage().contains("RICH_A_B_C"), error.getMessage());
  }

  @Test
  void checkPackageAcceptsDottedIdentifiers() {
    assertDoesNotThrow(() -> Identifiers.checkPackage("com.example.retailpos"));
    assertDoesNotThrow(() -> Identifiers.checkPackage("tierwise.generated"));
  }

  @Test
  void checkPackageRejectsAReservedWordSegment() {
    assertThrows(IllegalArgumentException.class, () -> Identifiers.checkPackage("com.class.x"));
  }

  @Test
  void checkPackageRejectsAnEmptyOrDigitLeadingSegment() {
    assertThrows(IllegalArgumentException.class, () -> Identifiers.checkPackage("a..b"));
    assertThrows(IllegalArgumentException.class, () -> Identifiers.checkPackage("a.1b"));
  }
}
