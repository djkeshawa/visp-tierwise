package io.github.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.internal.model.TypeDecl;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The fact declarations (FACT-1 to FACT-8, FACT-11, FACT-45, FACT-3) run through the stages. */
class FactDeclarationTest {

  private static final String ENTITIES = "entity_hierarchy:\n  customer: {}\n  order: {belongs_to: customer}\n";

  @TempDir
  Path dir;

  /**
   * A schema with the entity declarations above and the given fact lines. The first fact is on line 5. The
   * hierarchy follows the facts, so that the lines of the facts do not move and no SCOPE-48 finding is reported.
   */
  private CheckContext facts(String... factLines) throws IOException {
    StringBuilder text = new StringBuilder(ENTITIES).append("facts:\n");
    for (String line : factLines) {
      text.append("  ").append(line).append('\n');
    }
    text.append("decision_hierarchy:\n  levels: [global]\n");
    HierarchyFixture.schema(dir, text.toString());
    return HierarchyFixture.run(dir);
  }

  private static String file() {
    return HierarchyFixture.SCHEMA;
  }

  @Test
  void fact1_aFactIsDeclaredOnItsEntityAndKeepsItsType() throws IOException {
    CheckContext context = facts("customer.active: { type: boolean }", "order.total: { type: decimal }");
    assertTrue(HierarchyFixture.describe(context).isEmpty());
    assertEquals("customer", context.parts().facts().orElseThrow().get("customer.active").entity());
    assertEquals("decimal", context.parts().facts().orElseThrow().get("order.total").type().type());
  }

  @Test
  void fact2_theEntityPartMustBeDeclared() throws IOException {
    CheckContext context = facts("visitor.active: { type: boolean }");
    assertEquals(List.of("E_UNKNOWN_ENTITY " + file() + " [facts, visitor.active] 5"),
        HierarchyFixture.describe(context));
    assertFalse(context.parts().facts().isPresent());
  }

  @Test
  void fact2_anUndeclaredEntityIsSuggestedFromTheDeclaredNames() throws IOException {
    CheckContext context = facts("custmer.active: { type: boolean }");
    assertEquals(java.util.Optional.of("customer"), context.findings().sorted().get(0).suggestion());
  }

  @Test
  void fact3_aNameWithoutADotHasNoEntity() throws IOException {
    CheckContext context = facts("active: { type: boolean }");
    assertEquals(List.of("E_UNKNOWN_ENTITY " + file() + " [facts, active] 5"), HierarchyFixture.describe(context));
  }

  @Test
  void fact3_anEmptyAttributeIsInvalid() throws IOException {
    CheckContext context = facts("customer.: { type: boolean }");
    assertEquals(List.of("E_INVALID_DECLARATION " + file() + " [facts, customer.] 5"),
        HierarchyFixture.describe(context));
  }

  @Test
  void fact6_aDeclarationNeedsAType() throws IOException {
    CheckContext context = facts("customer.note: { source: crm }");
    assertEquals(List.of("E_TYPE_REQUIRED " + file() + " [facts, customer.note] 5"),
        HierarchyFixture.describe(context));
  }

  @Test
  void fact7_theTypeIsBooleanStringIntegerDecimalOrEnum() throws IOException {
    CheckContext context = facts("customer.note: { type: text }");
    assertEquals(List.of("E_INVALID_DECLARATION " + file() + " [facts, customer.note, type] 5"),
        HierarchyFixture.describe(context));
  }

  @Test
  void fact8_setListAndObjectAreNotFactTypes() throws IOException {
    CheckContext context = facts("order.tags: { type: set, universe: [a, b] }");
    assertEquals(List.of("E_INVALID_DECLARATION " + file() + " [facts, order.tags, type] 5"),
        HierarchyFixture.describe(context));
  }

  @Test
  void fact9_theTypeMembersOfTheTypeAreAllowed() throws IOException {
    CheckContext context = facts("order.lines: { type: integer, min: 1, max: 500 }",
        "order.cutoff: { type: string, format: time_hhmm }",
        "customer.tier: { type: enum, values: [GOLD, STANDARD] }");
    assertTrue(HierarchyFixture.describe(context).isEmpty());
    TypeDecl lines = context.parts().facts().orElseThrow().get("order.lines").type();
    assertEquals(1L, lines.min().orElseThrow());
    assertEquals(500L, lines.max().orElseThrow());
  }

  @Test
  void fact10_11_theSourceIsDocumentationAndMustBeAString() throws IOException {
    CheckContext ok = facts("customer.active: { type: boolean, source: crm.flags }");
    assertTrue(HierarchyFixture.describe(ok).isEmpty());
    CheckContext context = facts("customer.active: { type: boolean, source: 5 }");
    assertEquals(List.of("E_INVALID_DECLARATION " + file() + " [facts, customer.active, source] 5"),
        HierarchyFixture.describe(context));
  }

  @Test
  void fact45_aFactHasOnlyTypeSourceDescriptionAndTheTypeMembers() throws IOException {
    CheckContext context = facts("customer.active: { type: boolean, owner: ops }");
    assertEquals(List.of("E_INVALID_DECLARATION " + file() + " [facts, customer.active, owner] 5"),
        HierarchyFixture.describe(context));
  }

  @Test
  void fact45_descriptionMustBeAString() throws IOException {
    CheckContext context = facts("customer.active: { type: boolean, description: 3 }");
    assertEquals(List.of("E_INVALID_DECLARATION " + file() + " [facts, customer.active, description] 5"),
        HierarchyFixture.describe(context));
  }

  @Test
  void fact5_aRepeatAcrossSourcesIsReportedByTheLoaderAndTheFirstDeclarationIsKept() throws IOException {
    HierarchyFixture.write(dir, Map.of(
        "schema/core.yaml", "decision_hierarchy:\n  levels: [global]\n" + ENTITIES
            + "facts:\n  customer.active: { type: boolean }\n",
        "schema/extra.yaml", "facts:\n  customer.active: { type: string }\n"));
    CheckContext context = HierarchyFixture.run(dir);
    assertEquals("boolean", context.parts().facts().orElseThrow().get("customer.active").type().type());
    assertTrue(HierarchyFixture.describe(context, "E_DUPLICATE_DEFINITION").size() == 1);
  }

  @Test
  void fact_thePartIsPublishedOnlyWhenTheFactsAreClean() throws IOException {
    CheckContext context = facts("customer.active: { type: boolean }", "visitor.active: { type: boolean }");
    assertFalse(context.parts().facts().isPresent());
    assertFalse(context.facts().isPresent());
  }
}
