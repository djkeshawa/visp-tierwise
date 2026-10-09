package io.github.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The facts that a decision may read (FACT-13 to FACT-15), on the example of section 3.3. */
class FactIndexTest {

  @TempDir
  Path dir;

  /** The entity hierarchy of section 3.3 with the facts that its example names, and a grandchild fact. */
  private CheckContext example() throws IOException {
    HierarchyFixture.schema(dir, HierarchyDeclarationTest.text(
        "entity_hierarchy:",
        "  customer: {}",
        "  invoice: { has_many: order }",
        "  order: { belongs_to: customer, has_many: item }",
        "  item: { has_many: part }",
        "  part: {}",
        "facts:",
        "  customer.credit_hold: { type: boolean }",
        "  order.total: { type: decimal }",
        "  invoice.locked: { type: boolean }",
        "  item.stock_type: { type: enum, values: [STOCKED, DROP_SHIP] }",
        "  part.code: { type: string }",
        "decision_hierarchy:",
        "  levels: [global]"));
    return HierarchyFixture.run(dir);
  }

  @Test
  void fact13_theSingleValueFactsAreThoseOfTheEntityAndItsAncestors() throws IOException {
    FactEligibility eligible = example().facts().orElseThrow().eligibleFor("order");
    assertTrue(eligible.singleValue().contains("customer.credit_hold"));
    assertTrue(eligible.singleValue().contains("order.total"));
  }

  @Test
  void fact14_theChildFactsAreGroupedByChildEntity() throws IOException {
    FactEligibility eligible = example().facts().orElseThrow().eligibleFor("order");
    assertEquals(Map.of("item", Set.of("item.stock_type")), Map.copyOf(eligible.childFacts()));
  }

  @Test
  void fact15_otherFactsAreNotEligible() throws IOException {
    FactEligibility eligible = example().facts().orElseThrow().eligibleFor("order");
    assertFalse(eligible.isEligible("invoice.locked"), "invoice has_many order but is not an ancestor");
    assertFalse(eligible.isEligible("part.code"), "a grandchild's facts are not eligible");
    assertFalse(eligible.isEligible("nothing.here"));
    assertTrue(eligible.isEligible("item.stock_type"));
  }

  @Test
  void fact13_15_aDecisionAboutAParentDoesNotReadTheFactsOfItsChildren() throws IOException {
    FactEligibility eligible = example().facts().orElseThrow().eligibleFor("customer");
    assertTrue(eligible.singleValue().contains("customer.credit_hold"));
    assertFalse(eligible.isEligible("order.total"), "a child's fact is not single-value for its parent (ENTITY-12)");
    assertTrue(eligible.childFacts().isEmpty(), "customer declares no has_many");
  }

  @Test
  void fact13_anUndeclaredEntityHasNoEligibleFacts() throws IOException {
    FactEligibility eligible = example().facts().orElseThrow().eligibleFor("ghost");
    assertTrue(eligible.singleValue().isEmpty());
    assertTrue(eligible.childFacts().isEmpty());
  }

  @Test
  void entity10_11_theIndexGivesTheAncestorsAndChildrenOfAnEntity() throws IOException {
    EntityIndex index = example().entities().orElseThrow();
    assertEquals(List.of("customer"), index.parents("order"));
    assertEquals(List.of("item"), index.children("order"));
    assertEquals(List.of("customer"), List.copyOf(index.ancestors("order")));
  }

  @Test
  void fact3_factNamesAreComparedAfterNormalisation() throws IOException {
    FactIndex facts = example().facts().orElseThrow();
    assertTrue(facts.isDeclared("order.total"));
    assertEquals("order", facts.definition("order.total").orElseThrow().entity());
    assertFalse(facts.isDeclared("order.missing"));
  }
}
