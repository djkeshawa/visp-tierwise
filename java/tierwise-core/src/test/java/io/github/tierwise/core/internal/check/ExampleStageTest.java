package io.github.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Finding;
import io.github.tierwise.core.Severity;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the example stage over whole projects: a passing and a failing example, reported at the {@code expect}
 * entry (POL-41, POL-42, FIND-34), a null fact as a missing fact (POL-39), and the run only when no other engine
 * finding exists (POL-47, FIND-35). The evaluation errors (POL-46) are covered by {@link ExampleRunnerTest}.
 */
class ExampleStageTest {

  @TempDir
  Path dir;

  private static final String MANIFEST = """
      specVersion: "0.1"
      sources:
        schema:
          - schema/core.yaml
        rules:
          - rules/*.yaml
      """;

  /** The schema of the example project: an order feature with enum effects and a quote with object effects. */
  static final String SCHEMA = """
      decision_hierarchy:
        levels: [global, division, account]
        axes: [seller]
        scopes:
          division: { nodes: [north, south], on_unknown_node: error }
          account:  { nodes: open, values: service }
          seller:   { nodes: [acme], on_unknown_node: error }
      entity_hierarchy:
        customer: {}
        order: { belongs_to: customer, has_many: item }
        item: {}
      facts:
        customer.active: { type: boolean }
        customer.credit_hold: { type: boolean }
        item.stock_type: { type: enum, values: [STOCKED, DROP_SHIP, SPECIAL_ORDER] }
        item.weight: { type: integer }
      features:
        checkout.order_submission:
          decided_per: order
          returns: { enum: [ALLOW, DENY] }
          hit_policy: FIRST
          binding_settable_at: [global, division]
          if_fact_missing: DENY
        checkout.quote:
          decided_per: order
          returns:
            object:
              method: { type: enum, values: [NEXT_DAY, GROUND] }
              days: { type: integer, min: 1, max: 30 }
              note: { type: string, optional: true }
          hit_policy: FIRST
          binding_settable_at: [global]
          if_fact_missing: { method: GROUND, days: 5 }
      """;

  /** The rules of the order feature, with the expectation of its one example given by the caller. */
  static String orderRules(String expect) {
    return """
        feature: checkout.order_submission

        policies:
          credit_check:
            settings:
              allowed_on_credit_hold: { list_of: item.stock_type, default: [] }
            default: DENY
            rules:
              - id: good-credit
                name: Good credit
                when:
                  customer.active: true
                  customer.credit_hold: false
                then: ALLOW
              - id: credit-hold-allowed-stock
                name: On credit hold, every item is an allowed stock type
                when:
                  customer.active: true
                  customer.credit_hold: true
                  item.stock_type: { all_in: $allowed_on_credit_hold }
                then: ALLOW
          allow_all:
            default: ALLOW
            rules: []

        bindings:
          global: { policy: credit_check }
          division:
            north: { policy: allow_all }
            south: { policy: credit_check, allowed_on_credit_hold: [STOCKED, SPECIAL_ORDER] }

        examples:
          - name: Credit hold with only allowed stock is allowed
            context: { levels: { division: south } }
            facts: { customer.active: true, customer.credit_hold: true, item.stock_type: [STOCKED, SPECIAL_ORDER] }
            expect: %s
        """.formatted(expect);
  }

  /** The rules of the quote feature, whose two examples pass. */
  static final String QUOTE_RULES = """
      feature: checkout.quote

      policies:
        quote_default:
          default: { method: GROUND, days: 5 }
          rules:
            - id: express-active
              name: Active customers get next day
              when:
                customer.active: true
              then: { method: NEXT_DAY, days: 1 }

      bindings:
        global: { policy: quote_default }

      examples:
        - name: Active customer gets the express quote
          context: {}
          facts: { customer.active: true }
          expect: { days: 1, method: NEXT_DAY }
        - name: Missing customer gets the default quote
          context: {}
          facts: {}
          expect: { method: GROUND, days: 5 }
      """;

  private static Map<String, String> project(String orderRules) {
    return Map.of("tierwise.yaml", MANIFEST, "schema/core.yaml", SCHEMA,
        "rules/checkout.order_submission.yaml", orderRules, "rules/checkout.quote.yaml", QUOTE_RULES);
  }

  @Test
  void POL_41_aPassingExampleProducesNoFindingAndEveryValidExamplePublishes() {
    CheckContext context = PipelineProject.run(dir, project(orderRules("ALLOW")), null);
    assertEquals(List.of(), PipelineProject.describe(context));
    assertEquals(3, context.find(ExampleSet.class).orElseThrow().examples().size());
  }

  @Test
  void POL_42_AndFIND_34_aFailingExampleIsReportedAtItsExpectEntry() {
    String rules = orderRules("DENY");
    CheckContext context = PipelineProject.run(dir, project(rules), null);
    int line = PipelineProject.lineOf(rules, "expect:");
    assertEquals(List.of("E_EXAMPLE_FAILED rules/checkout.order_submission.yaml " + line + " [examples, 0, expect]"),
        PipelineProject.describe(context));
    Finding failure = context.findings().sorted().get(0);
    assertTrue(failure.message().contains("expects DENY but the decision is ALLOW"), failure.message());
    assertTrue(context.findings().hasErrors());
  }

  @Test
  void POL_39_aNullFactIsAMissingFactAndTheExampleRunsNormally() {
    String rules = orderRules("ALLOW") + """
          - name: Missing active flag is denied
            context: {}
            facts: { customer.active: null }
            expect: DENY
        """;
    CheckContext context = PipelineProject.run(dir, project(rules), null);
    assertEquals(List.of(), PipelineProject.describe(context));
    assertEquals(4, context.find(ExampleSet.class).orElseThrow().examples().size());
  }

  @Test
  void POL_47_examplesRunOnlyWhenTheirOwnChecksAddNoEngineFinding() {
    String rules = orderRules("DENY") + """
          - name: Broken example
            context: {}
            facts: {}
            expect: ALLOW
            note: not allowed
        """;
    CheckContext context = PipelineProject.run(dir, project(rules), null);
    List<String> found = PipelineProject.describe(context);
    assertEquals(1, found.size(), found.toString());
    assertTrue(found.get(0).startsWith("E_INVALID_DECLARATION "), found.toString());
    assertFalse(found.stream().anyMatch(line -> line.startsWith("E_EXAMPLE_FAILED")), found.toString());
  }

  @Test
  void FIND_35_theExampleStageRunsNoExampleAfterAnEarlierEngineFinding() {
    CheckContext context = PipelineProject.run(dir, project(orderRules("DENY")), "examples");
    assertFalse(context.findings().hasEngineFindings());
    context.findings().add(Finding.of(Code.E_PARSE, Severity.ERROR, "f", List.of(), 1, "an earlier fault"));
    new ExampleStage().run(context);
    assertFalse(PipelineProject.describe(context).stream().anyMatch(line -> line.startsWith("E_EXAMPLE_FAILED")),
        PipelineProject.describe(context).toString());
    assertTrue(context.find(ExampleSet.class).isPresent(), "the valid examples are still published for lint");
  }

  @Test
  void POL_47_theStageIsDisabledByAnEngineFindingFromAnEarlierStage() {
    CheckContext context = PipelineProject.run(dir, project(orderRules("DENY")), "examples");
    context.findings().add(Finding.of(Code.E_PARSE, Severity.ERROR, "f", List.of(), 1, "an earlier fault"));
    assertFalse(new ExampleStage().enabled(context));
  }
}
