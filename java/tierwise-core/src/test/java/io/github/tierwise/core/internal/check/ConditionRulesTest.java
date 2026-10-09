package io.github.tierwise.core.internal.check;

import static io.github.tierwise.core.internal.check.FeatureFixture.GATE;
import static io.github.tierwise.core.internal.check.FeatureFixture.run;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tierwise.core.internal.check.FeatureFixture.Outcome;
import io.github.tierwise.core.internal.model.ConditionDef;
import io.github.tierwise.core.internal.model.PolicyDef;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Conditions and the setting references in them: RULE-1 to RULE-28 and BUNDLE-91 to BUNDLE-104. */
class ConditionRulesTest {

  private static final String INVOICE_FEATURE = """
      features:
        release:
          decided_per: invoice
          returns: { enum: [HOLD, RELEASE] }
          hit_policy: FIRST
          binding_settable_at: [global]
          if_fact_missing: HOLD
      """;

  /** A rules file whose only rule has the given {@code when} node, written inline. */
  private static String rules(String when) {
    return """
        feature: gate
        policies:
          p:
            settings:
              allowed: { list_of: item.stock_type, default: [] }
              held: { value_of: customer.credit_hold, default: true }
              lines: { list_of: order.lines, default: [] }
            default: DENY
            rules:
              - id: r
                name: R
                when: %s
                then: ALLOW
        bindings:
          global: { policy: p }
        """.formatted(when);
  }

  private static Outcome when(String when) {
    return run(GATE, rules(when));
  }

  private static void assertFound(Outcome outcome, String... expected) {
    assertEquals(List.of(expected), outcome.found());
  }

  private static String at(String fact) {
    return "[policies, p, rules, 0, when, " + fact + "]";
  }

  private static PolicyDef policy(Outcome outcome) {
    return outcome.parts().features().orElseThrow().get("gate").policies().get("p");
  }

  @Test
  void rule1And2WhenIsAMappingAndTheEmptyMappingIsLegal() {
    assertFound(when("{}"));
    assertFound(when("[a]"), "E_INVALID_DECLARATION [policies, p, rules, 0, when]");
    assertFound(when("ALLOW"), "E_INVALID_DECLARATION [policies, p, rules, 0, when]");
  }

  @Test
  void rule3TheFourFormsAreLegal() {
    Outcome outcome = when("{ customer.active: true, order.lines: { in: [1, 2] }, "
        + "item.stock_type: { any_in: [STOCKED] }, customer.credit_hold: $held }");
    assertFound(outcome);
    List<ConditionDef> conditions = policy(outcome).rules().get(0).when();
    assertEquals(List.of("customer.active", "customer.credit_hold", "item.stock_type", "order.lines"),
        conditions.stream().map(ConditionDef::fact).toList());
    assertEquals(List.of("eq", "eq", "any_in", "in"), conditions.stream().map(ConditionDef::op).toList());
    assertEquals("$held", conditions.get(1).value());
    assertEquals(List.of(1L, 2L), conditions.get(3).value());
  }

  @Test
  void rule4AStringThatBeginsWithADollarIsAReference() {
    assertFound(when("{ customer.tier: \"$gold\" }"), "E_UNKNOWN_SETTING " + at("customer.tier"));
  }

  @Test
  void rule6MalformedSyntaxIsAnInvalidDeclaration() {
    String invalid = "E_INVALID_DECLARATION ";
    assertFound(when("{ order.lines: { gt: 3 } }"), invalid + at("order.lines"));
    assertFound(when("{ order.lines: { in: [1], any_in: [2] } }"), invalid + at("order.lines"));
    assertFound(when("{ order.lines: {} }"), invalid + at("order.lines"));
    assertFound(when("{ order.lines: { in: 3 } }"), invalid + at("order.lines"));
    assertFound(when("{ order.lines: { in: null } }"), invalid + at("order.lines"));
    assertFound(when("{ order.lines: [1] }"), invalid + at("order.lines"));
    assertFound(when("{ customer.active: { eq: true } }"), invalid + at("customer.active"));
    assertFound(when("{ customer.rate: { gt: \"1.5\" } }"), invalid + at("customer.rate"));
  }

  @Test
  void rule8TheConditionsAreSortedByFactName() {
    Outcome outcome = when("{ order.lines: 3, customer.active: true, item.stock_type: { all_in: [STOCKED] } }");
    assertFound(outcome);
    assertEquals(List.of("customer.active", "item.stock_type", "order.lines"),
        policy(outcome).rules().get(0).when().stream().map(ConditionDef::fact).toList());
  }

  @Test
  void rule9AConditionNamesADeclaredFact() {
    assertFound(when("{ customer.nickname: x }"), "E_UNKNOWN_FACT " + at("customer.nickname"));
    assertFound(when("{ nickname: x }"), "E_UNKNOWN_ENTITY " + at("nickname"));
    assertFound(when("{ \"customer.\": x }"), "E_INVALID_DECLARATION " + at("customer."));
  }

  @Test
  void rule10AnUndeclaredEntityPartIsAnUnknownFact() {
    assertFound(when("{ visitor.active: true }"), "E_UNKNOWN_FACT " + at("visitor.active"));
  }

  @Test
  void rule11AFactTwoHasManyEdgesDeepIsUnsupported() {
    String rules = """
        feature: release
        policies:
          check:
            default: HOLD
            rules:
              - id: r
                name: R
                when: { item.stock_type: STOCKED }
                then: RELEASE
        bindings:
          global: { policy: check }
        """;
    assertFound(run(INVOICE_FEATURE, rules), "E_DEPTH_UNSUPPORTED [policies, check, rules, 0, when, item.stock_type]");
    assertFound(run(INVOICE_FEATURE, rules.replace("item.stock_type: STOCKED", "item.stock_type: { any_in: [STOCKED] }")),
        "E_DEPTH_UNSUPPORTED [policies, check, rules, 0, when, item.stock_type]");
  }

  @Test
  void rule12AFactOutOfScopeIsNotInScope() {
    assertFound(when("{ warehouse.open: true }"), "E_FACT_NOT_IN_SCOPE " + at("warehouse.open"));
    assertFound(when("{ invoice.locked: true }"), "E_FACT_NOT_IN_SCOPE " + at("invoice.locked"));
    assertFound(when("{ warehouse.open: { all_in: [true] } }"), "E_FACT_NOT_IN_SCOPE " + at("warehouse.open"));
  }

  @Test
  void rule13AChildFactNeedsAQuantifier() {
    assertFound(when("{ item.stock_type: STOCKED }"), "E_QUANTIFIER_REQUIRED " + at("item.stock_type"));
    assertFound(when("{ item.stock_type: { in: [STOCKED] } }"), "E_QUANTIFIER_REQUIRED " + at("item.stock_type"));
  }

  @Test
  void rule14ASingleValueFactTakesNoQuantifier() {
    assertFound(when("{ customer.active: { any_in: [true] } }"), "E_QUANTIFIER_NOT_ALLOWED " + at("customer.active"));
    assertFound(when("{ customer.active: { all_in: [true] } }"), "E_QUANTIFIER_NOT_ALLOWED " + at("customer.active"));
  }

  @Test
  void rule15And16ADecimalFactTakesTheEqualityAndMembershipForms() {
    Outcome outcome = when("{ customer.rate: \"0.50\", item.unit_price: { any_in: [\"1.50\", \"2\"] } }");
    assertFound(outcome);
    List<ConditionDef> conditions = policy(outcome).rules().get(0).when();
    assertEquals("0.5", conditions.get(0).value());
    assertEquals(List.of("1.5", "2"), conditions.get(1).value());
  }

  @Test
  void rule17And18AnIllegalLiteralIsReportedAtTheCondition() {
    String illegal = "E_ILLEGAL_FACT_VALUE ";
    assertFound(when("{ customer.active: yes }"), illegal + at("customer.active"));
    assertFound(when("{ customer.rate: 1.5 }"), illegal + at("customer.rate"));
    assertFound(when("{ item.stock_type: { any_in: [stocked] } }"), illegal + at("item.stock_type"));
    assertFound(when("{ order.lines: 0 }"), illegal + at("order.lines"));
    assertFound(when("{ order.lines: { in: [1, \"2\"] } }"), illegal + at("order.lines"));
  }

  @Test
  void rule19ANullLiteralIsIllegal() {
    assertFound(when("{ customer.active: null }"), "E_ILLEGAL_FACT_VALUE " + at("customer.active"));
    assertFound(when("{ customer.active: ~ }"), "E_ILLEGAL_FACT_VALUE " + at("customer.active"));
    assertFound(when("{ order.lines: { in: [1, null] } }"), "E_ILLEGAL_FACT_VALUE " + at("order.lines"));
  }

  @Test
  void rule20AnIntegerLiteralBeyondTheEngineRangeIsAnIntegerRange() {
    assertFound(when("{ order.lines: { in: [1, 9007199254740992] } }"), "E_INTEGER_RANGE " + at("order.lines"));
    assertFound(when("{ order.lines: 99999999999999999999999 }"), "E_INTEGER_RANGE " + at("order.lines"));
    assertFound(when("{ customer.active: 9007199254740992 }"), "E_ILLEGAL_FACT_VALUE " + at("customer.active"));
  }

  @Test
  void rule21ALiteralListHasNoRepeat() {
    assertFound(when("{ order.lines: { in: [1, 2, 1] } }"), "E_DUPLICATE_VALUE " + at("order.lines"));
    assertFound(when("{ item.unit_price: { any_in: [\"1.5\", \"1.50\"] } }"), "E_DUPLICATE_VALUE " + at("item.unit_price"));
  }

  @Test
  void rule22AReferenceNamesADeclaredSetting() {
    assertFound(when("{ customer.credit_hold: $helds }"), "E_UNKNOWN_SETTING " + at("customer.credit_hold"));
    assertFound(when("{ customer.credit_hold: $ }"), "E_UNKNOWN_SETTING " + at("customer.credit_hold"));
    assertFound(when("{ item.stock_type: { all_in: $alowed } }"),
        "E_UNKNOWN_SETTING [policies, p, rules, 0, when, item.stock_type, all_in]");
  }

  @Test
  void rule23AReferenceIsTheWholeOperand() {
    assertFound(when("{ item.stock_type: { all_in: [STOCKED, $allowed] } }"), "E_INVALID_SETTING " + at("item.stock_type"));
    assertFound(when("{ item.stock_type: { all_in: [$allowed] } }"),
        "E_INVALID_SETTING [policies, p, rules, 0, when, item.stock_type, all_in, 0]");
  }

  @Test
  void rule24TheSettingKindMatchesThePosition() {
    assertFound(when("{ customer.credit_hold: { in: $held } }"), "E_INVALID_SETTING " + at("customer.credit_hold"));
    assertFound(when("{ item.stock_type: { all_in: $held } }"), "E_INVALID_SETTING " + at("item.stock_type"));
  }

  @Test
  void rule25TheSettingIsDeclaredForTheConditionsFact() {
    assertFound(when("{ item.unit_price: { any_in: $allowed } }"), "E_INVALID_SETTING " + at("item.unit_price"));
    assertFound(when("{ customer.active: $held }"), "E_INVALID_SETTING " + at("customer.active"));
  }

  @Test
  void rule26TheFormChecksApplyToAReferenceOperand() {
    assertFound(when("{ item.stock_type: $held }"), "E_QUANTIFIER_REQUIRED " + at("item.stock_type"));
    assertFound(when("{ item.stock_type: { in: $allowed } }"), "E_QUANTIFIER_REQUIRED " + at("item.stock_type"));
    assertFound(when("{ customer.credit_hold: { any_in: $allowed } }"), "E_QUANTIFIER_NOT_ALLOWED " + at("customer.credit_hold"));
  }

  @Test
  void rule28OneConditionReportsOneCodeInTheOrderOfTheChecks() {
    assertFound(when("{ visitor.active: { gt: 1 } }"), "E_UNKNOWN_FACT " + at("visitor.active"));
    assertFound(when("{ warehouse.open: [x] }"), "E_FACT_NOT_IN_SCOPE " + at("warehouse.open"));
    assertFound(when("{ item.stock_type: [STOCKED] }"), "E_INVALID_DECLARATION " + at("item.stock_type"));
    assertFound(when("{ item.stock_type: { in: $alowed } }"), "E_QUANTIFIER_REQUIRED " + at("item.stock_type"));
    assertFound(when("{ item.stock_type: { in: [SOLD] } }"), "E_QUANTIFIER_REQUIRED " + at("item.stock_type"));
    assertFound(when("{ item.stock_type: { all_in: [SOLD, $allowed] } }"), "E_INVALID_SETTING " + at("item.stock_type"));
    assertFound(when("{ order.lines: { in: [1, 99999999999999999999, null, 1] } }"), "E_INTEGER_RANGE " + at("order.lines"));
    assertFound(when("{ order.lines: { in: [1, null, 1] } }"), "E_ILLEGAL_FACT_VALUE " + at("order.lines"));
  }

  @Test
  void bundle91RequiredFactsAreTheDistinctFactsOfAllRulesInCodePointOrder() {
    String rules = """
        feature: gate
        policies:
          p:
            default: DENY
            rules:
              - id: a
                name: A
                when: { order.lines: 3, customer.active: true }
                then: ALLOW
              - id: b
                name: B
                when: { customer.active: false, item.stock_type: { any_in: [STOCKED] } }
                then: ALLOW
              - id: c
                name: C
                when: {}
                then: DENY
        bindings:
          global: { policy: p }
        """;
    Outcome outcome = run(GATE, rules);
    assertFound(outcome);
    assertEquals(List.of("customer.active", "item.stock_type", "order.lines"), policy(outcome).requiredFacts());
  }
}
