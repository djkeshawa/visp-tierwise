package io.github.visplabs.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.internal.model.EntityDef;
import io.github.visplabs.tierwise.core.internal.model.FactDef;
import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import io.github.visplabs.tierwise.core.internal.model.Values;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tests the facts of examples in the shorthand and the explicit form (POL-32 to POL-37, POL-45). */
class ExampleFactsTest {

  private static final Model MODEL = new Model(Model.FORMAT_VERSION, Model.SPEC_VERSION,
      new Hierarchy(List.of("global"), List.of(), Map.of()),
      Map.of("customer", new EntityDef(List.of(), List.of()),
          "order", new EntityDef(List.of("customer"), List.of("item")),
          "item", new EntityDef(List.of(), List.of())),
      Map.of("customer.active", new FactDef("customer", TypeDecl.simple("boolean")),
          "item.stock", new FactDef("item", new TypeDecl("enum", Optional.empty(),
              Optional.empty(), Optional.empty(), List.of("STOCKED", "DROP_SHIP"), List.of(),
              Optional.empty(), Map.of(), false)),
          "item.weight", new FactDef("item", TypeDecl.simple("boolean"))),
      Map.of(), Values.EMPTY, Map.of());

  private static final ExampleScope SCOPE = ExampleScope.of(MODEL, "order");

  private static ExampleFacts.Parsed parse(String yaml, Findings findings) {
    PMap root = CheckTestSupport.root(yaml);
    return ExampleFacts.parse(MODEL, SCOPE, new ValueValidator(findings), findings, root.get("facts").orElseThrow(),
        root.get("children").orElse(null));
  }

  @Test
  void aShorthandListBecomesOneChildMapForEachElement() {
    Findings findings = new Findings();
    ExampleFacts.Parsed parsed = parse("facts: { customer.active: true, item.stock: [STOCKED, DROP_SHIP] }\n",
        findings);
    assertTrue(parsed.valid());
    assertEquals(Map.of("customer.active", true), parsed.facts());
    assertEquals(2, parsed.children().get("item").size());
    assertEquals(List.of(), CheckTestSupport.descriptions(findings));
  }

  @Test
  void aListUnderASingleValueFactAndASecondShorthandForTheSameEntityAreStructureViolations() {
    Findings list = new Findings();
    assertFalse(parse("facts: { customer.active: [true] }\n", list).valid());
    CheckTestSupport.assertFindings(list, "E_INVALID_DECLARATION 1 [facts, customer.active]");

    Findings twice = new Findings();
    assertFalse(parse("facts: { item.stock: [STOCKED], item.weight: [true] }\n", twice).valid());
    CheckTestSupport.assertFindings(twice, "E_INVALID_DECLARATION 1 [facts, item.weight]");
  }

  @Test
  void aValueOfTheWrongTypeIsIllegalAndAnUndeclaredFactIsUnknown() {
    Findings illegal = new Findings();
    parse("facts: { customer.active: \"yes\" }\n", illegal);
    CheckTestSupport.assertFindings(illegal, "E_ILLEGAL_FACT_VALUE 1 [facts, customer.active]");

    Findings unknown = new Findings();
    parse("facts: { customer.nickname: true }\n", unknown);
    CheckTestSupport.assertFindings(unknown, "E_UNKNOWN_FACT 1 [facts, customer.nickname]");
  }

  @Test
  void inTheExplicitFormAListUnderFactsIsNotShorthand() {
    Findings findings = new Findings();
    assertFalse(parse("facts: { item.stock: [STOCKED] }\nchildren: { item: [{ item.stock: STOCKED }] }\n",
        findings).valid());
    CheckTestSupport.assertFindings(findings, "E_INVALID_DECLARATION 1 [facts, item.stock]");
  }
}
