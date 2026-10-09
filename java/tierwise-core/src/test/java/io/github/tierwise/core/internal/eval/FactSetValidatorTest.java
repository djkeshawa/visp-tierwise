package io.github.tierwise.core.internal.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.core.internal.model.Model;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** FACT-13 to FACT-44. The decided entity is {@code order}: customer is its parent, item its child. */
class FactSetValidatorTest {

  private final Model model = TestModels.standard().model();
  private final FactSetValidator validator = new FactSetValidator(model);
  private final EntityScope scope = EntityScope.of(model, "order");

  private static Map<String, Object> raw(Object... pairs) {
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }

  private ValidatedFacts ok(Object rawFactSet) {
    return validator.validate(scope, FactSet.fromJson(rawFactSet));
  }

  private void invalid(Object rawFactSet) {
    TierwiseException e = assertThrows(TierwiseException.class, () -> ok(rawFactSet));
    assertEquals(Code.E_FACTS_INVALID, e.code());
  }

  @Test
  void FACT_13_singleValueFactsAreThoseOfTheDecidedEntityAndItsAncestors() {
    assertTrue(scope.isSingleValue("order.total"));
    assertTrue(scope.isSingleValue("customer.active"));
    assertTrue(!scope.isSingleValue("item.stock"));
    assertTrue(!scope.isSingleValue("invoice.paid"));
  }

  @Test
  void FACT_14_childFactsAreThoseOfTheChildrenOfTheDecidedEntity() {
    assertTrue(scope.isChild("item"));
    assertTrue(!scope.isChild("customer"));
    assertEquals(java.util.Set.of("item.stock", "item.price"), scope.factsOfChild("item"));
  }

  @Test
  void ENTITY_12_noRelationshipIsInferredFromAHasManyEntry() {
    // invoice has_many order, but an invoice is not an ancestor of an order.
    assertTrue(!scope.isSingleValue("invoice.paid"));
    invalid(raw("facts", raw("invoice.paid", true)));
  }

  @Test
  void FACT_22_theFactSetMustBeAMap() {
    invalid(null);
    invalid(List.of());
    invalid("facts");
  }

  @Test
  void EVAL_54_aNullFactSetArgumentIsAnEmptyFactSet() {
    ValidatedFacts facts = validator.validate(scope, null);
    assertTrue(facts.facts().isEmpty());
    assertTrue(facts.children().isEmpty());
  }

  @Test
  void FACT_23_anEntryOtherThanFactsAndChildrenIsInvalid() {
    invalid(raw("extra", raw()));
  }

  @Test
  void FACT_24_factsAndChildrenMayBeOmitted() {
    assertTrue(ok(raw()).facts().isEmpty());
    assertTrue(ok(raw("facts", raw())).children().isEmpty());
  }

  @Test
  void FACT_25_factsMustBeAMapAndFACT_30_notNull() {
    invalid(raw("facts", List.of()));
    invalid(raw("facts", null));
  }

  @Test
  void FACT_26_everyFactsEntryMustBeADeclaredFactWhateverItsValue() {
    invalid(raw("facts", raw("customer.nickname", null)));
    invalid(raw("facts", raw("customer.nickname", "x")));
  }

  @Test
  void FACT_27_everyFactsEntryMustBeASingleValueFactOfTheDecision() {
    invalid(raw("facts", raw("item.stock", "STOCKED")));
    invalid(raw("facts", raw("item.stock", null)));
  }

  @Test
  void FACT_28_childrenMustBeAMapAndFACT_30_notNull() {
    invalid(raw("children", List.of()));
    invalid(raw("children", null));
  }

  @Test
  void FACT_29_everyChildrenEntryMustBeAChildOfTheDecidedEntity() {
    invalid(raw("children", raw("customer", List.of())));
    invalid(raw("children", raw("invoice", List.of())));
    invalid(raw("children", raw("order", List.of())));
  }

  @Test
  void FACT_30_aNullChildrenEntryIsInvalid() {
    invalid(raw("children", raw("item", null)));
  }

  @Test
  void FACT_31_aChildrenEntryMustBeAList() {
    invalid(raw("children", raw("item", raw())));
    invalid(raw("children", raw("item", "x")));
  }

  @Test
  void FACT_32_everyChildMapMustBeAMap() {
    invalid(raw("children", raw("item", java.util.Arrays.asList((Object) null))));
    invalid(raw("children", raw("item", List.of("x"))));
  }

  @Test
  void FACT_33_everyChildMapEntryMustBeADeclaredFactOfTheChildEntity() {
    invalid(raw("children", raw("item", List.of(raw("item.nickname", "x")))));
    invalid(raw("children", raw("item", List.of(raw("order.total", "1")))));
    invalid(raw("children", raw("item", List.of(raw("customer.active", true)))));
    invalid(raw("children", raw("item", List.of(raw("item.nickname", null)))));
  }

  @Test
  void FACT_35_aNonNullValueMustBeAValidValueOfItsDeclaredType() {
    invalid(raw("facts", raw("customer.active", "true")));
    invalid(raw("facts", raw("customer.active", 1L)));
    invalid(raw("facts", raw("order.label", 5L)));
    invalid(raw("facts", raw("order.lines", "5")));
    invalid(raw("children", raw("item", List.of(raw("item.stock", "stocked")))));
  }

  @Test
  void FACT_35_aNullValueIsNotCheckedAgainstTheType() {
    ValidatedFacts facts = ok(raw("facts", raw("customer.active", null)));
    assertTrue(facts.facts().containsKey("customer.active"));
    assertNull(facts.facts().get("customer.active"));
  }

  @Test
  void FACT_36_entryNamesAndStringsAreComparedAsReceived() {
    invalid(raw("facts", raw("Customer.active", true)));
    invalid(raw("children", raw("Item", List.of())));
    ValidatedFacts facts = ok(raw("facts", raw("order.label", "é")));
    assertEquals("é", facts.facts().get("order.label"));
  }

  @Test
  void FACT_37_anEnumStringMustEqualAnEntryOfValues() {
    invalid(raw("facts", raw("order.kind", "retail")));
    assertEquals("RETAIL", ok(raw("facts", raw("order.kind", "RETAIL"))).facts().get("order.kind"));
  }

  @Test
  void FACT_38_aNumberGivenForADecimalFactIsInvalid() {
    invalid(raw("facts", raw("order.total", 1.5)));
    invalid(raw("facts", raw("order.total", 1L)));
  }

  @Test
  void FACT_39_aDecimalStringIsAcceptedAndCanonicalised() {
    assertEquals("1.5", ok(raw("facts", raw("order.total", "+1.50"))).facts().get("order.total"));
    assertEquals("0", ok(raw("facts", raw("order.total", "-0.0"))).facts().get("order.total"));
    ValidatedFacts children = ok(raw("children", raw("item", List.of(raw("item.price", "02.50")))));
    assertEquals("2.5", children.children().get("item").get(0).get("item.price"));
  }

  @Test
  void FACT_40_aStringThatIsNotADecimalIsInvalid() {
    for (String text : new String[] {"1.", ".5", "1e3", "abc", ""}) {
      invalid(raw("facts", raw("order.total", text)));
    }
  }

  @Test
  void FACT_41_anIntegerOutsidePlusOrMinus2To53MinusOneIsInvalid() {
    invalid(raw("facts", raw("order.lines", (1L << 53))));
    invalid(raw("facts", raw("order.lines", -(1L << 53))));
    invalid(raw("facts", raw("order.lines", 101L)));
    invalid(raw("facts", raw("order.lines", -1L)));
    assertEquals(100L, ok(raw("facts", raw("order.lines", 100L))).facts().get("order.lines"));
  }

  @Test
  void NORM_8_aFactSetStringWithAnUnpairedSurrogateIsInvalid() {
    invalid(raw("facts", raw("order.label", "x\uD83D")));
    invalid(raw("children", raw("item", List.of(raw("item.stock", "\uDE00")))));
  }

  @Test
  void TYPE_33_andTYPE_37_stringFormatsAreCheckedAtRuntime() {
    invalid(raw("facts", raw("order.window", "24:00")));
    invalid(raw("facts", raw("order.zone", "Europe//London")));
    assertEquals("Mars/Olympus_Mons", ok(raw("facts", raw("order.zone", "Mars/Olympus_Mons"))).facts().get("order.zone"));
  }

  @Test
  void FACT_43_aFailingEntryIsNeverIgnoredOrTreatedAsMissing() {
    invalid(raw("facts", raw("customer.active", "yes", "order.label", "ok")));
  }

  @Test
  void FACT_10_onlyTheTypeOfAFactDeclarationIsUsedToValidate() {
    // A FactDef carries the entity and the type, nothing else: the source member of a declaration is not in the bundle.
    assertEquals(2, io.github.tierwise.core.internal.model.FactDef.class.getRecordComponents().length);
    invalid(raw("facts", raw("customer.active", "documented-as-text")));
  }

  @Test
  void FACT_42_everyFailureIsReportedWithOneCode() {
    for (Object bad : new Object[] {null, List.of(), raw("x", 1L), raw("facts", raw("nope", 1L)),
        raw("facts", raw("customer.active", "x")), raw("children", raw("item", List.of(raw("nope", 1L))))}) {
      assertEquals(Code.E_FACTS_INVALID, assertThrows(TierwiseException.class, () -> ok(bad)).code());
    }
  }

  @Test
  void FACT_20_theOrderOfChildMapsDoesNotAffectValidity() {
    Map<String, Object> a = raw("item.stock", "STOCKED");
    Map<String, Object> b = raw("item.stock", "DROP");
    ok(raw("children", raw("item", List.of(a, b))));
    ok(raw("children", raw("item", List.of(b, a))));
  }
}
