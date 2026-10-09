package io.github.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The entity declarations and the relations between entities, run through the stages (ENTITY-1 to ENTITY-12,
 * ENTITY-18, ENTITY-19). The cycle rules have their own test class.
 */
class EntityDeclarationTest {

  @TempDir
  Path dir;

  /** Writes the entity lines, then a hierarchy, so that the lines of the entities do not move (no SCOPE-48). */
  private CheckContext project(String... lines) throws IOException {
    String[] all = java.util.Arrays.copyOf(lines, lines.length + 2);
    all[lines.length] = "decision_hierarchy:";
    all[lines.length + 1] = "  levels: [global]";
    HierarchyFixture.schema(dir, HierarchyDeclarationTest.text(all));
    return HierarchyFixture.run(dir);
  }

  private static String file() {
    return HierarchyFixture.SCHEMA;
  }

  @Test
  void entity1_4_5_anEntityIsAMapOfBelongsToAndHasManyAndEitherMayBeAName() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  customer: {}",
        "  order:",
        "    belongs_to: customer",
        "    has_many: []",
        "  item:",
        "    belongs_to: []");
    assertTrue(HierarchyFixture.describe(context).isEmpty());
    assertEquals(List.of("customer"), context.parts().entities().orElseThrow().get("order").belongsTo());
  }

  @Test
  void entity6_aRelationNameMustBeDeclared() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  customer: {}",
        "  order:",
        "    belongs_to: [customer, ghost]");
    assertEquals(List.of("E_UNKNOWN_ENTITY " + file() + " [entity_hierarchy, order, belongs_to, 1] 4"),
        HierarchyFixture.describe(context));
    assertFalse(context.parts().entities().isPresent());
  }

  @Test
  void entity6_aScalarRelationNameIsLocatedAtTheMember() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  order:",
        "    has_many: ghost");
    assertEquals(List.of("E_UNKNOWN_ENTITY " + file() + " [entity_hierarchy, order, has_many] 3"),
        HierarchyFixture.describe(context));
  }

  @Test
  void entity7_anEntityDeclarationThatIsNotAMapIsInvalidAtTheEntity() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  customer: {}",
        "  legacy:");
    assertEquals(List.of("E_INVALID_DECLARATION " + file() + " [entity_hierarchy, legacy] 3"),
        HierarchyFixture.describe(context));
    assertFalse(context.parts().entities().isPresent());
  }

  @Test
  void entity4_7_anEntityMayHaveOnlyBelongsToAndHasMany() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  order:",
        "    belongs_to: []",
        "    label: order");
    assertEquals(List.of("E_INVALID_DECLARATION " + file() + " [entity_hierarchy, order, label] 4"),
        HierarchyFixture.describe(context));
  }

  @Test
  void entity7_aRelationThatIsAMapIsInvalid() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  invoice:",
        "    has_many: {order: 1}");
    assertEquals(List.of("E_INVALID_DECLARATION " + file() + " [entity_hierarchy, invoice, has_many] 3"),
        HierarchyFixture.describe(context));
  }

  @Test
  void entity7_aListElementThatIsAMapIsInvalidAtTheElement() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  invoice:",
        "    has_many: [{order: 1}]");
    assertEquals(List.of("E_INVALID_DECLARATION " + file() + " [entity_hierarchy, invoice, has_many, 0] 3"),
        HierarchyFixture.describe(context));
  }

  @Test
  void entity8_aNameRepeatedInOneListIsDuplicateAtTheLaterItem() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  customer: {}",
        "  invoice: {}",
        "  order:",
        "    belongs_to: [customer, invoice, customer]");
    assertEquals(List.of("E_DUPLICATE_VALUE " + file() + " [entity_hierarchy, order, belongs_to, 2] 5"),
        HierarchyFixture.describe(context));
  }

  @Test
  void entity18_anEmptyEntityNameIsInvalidAtTheEntity() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  \"\": {}");
    assertEquals(List.of("E_INVALID_DECLARATION " + file() + " [entity_hierarchy, ] 2"),
        HierarchyFixture.describe(context));
  }

  @Test
  void entity18_aDottedEntityNameIsInvalidAndNotDeclared() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  ledger.book: {}",
        "  order:",
        "    belongs_to: ledger.book");
    assertEquals(List.of(
        "E_INVALID_DECLARATION " + file() + " [entity_hierarchy, ledger.book] 2",
        "E_UNKNOWN_ENTITY " + file() + " [entity_hierarchy, order, belongs_to] 4"),
        HierarchyFixture.describe(context));
  }

  @Test
  void entity19_aNumberInARelationIsATypeMismatch() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  item: {}",
        "  carrier:",
        "    has_many: 5");
    assertEquals(List.of("E_TYPE_MISMATCH " + file() + " [entity_hierarchy, carrier, has_many] 4"),
        HierarchyFixture.describe(context));
  }

  @Test
  void entity19_aBooleanInAListIsATypeMismatchAtTheItem() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  item: {}",
        "  invoice:",
        "    has_many: [true]");
    assertEquals(List.of("E_TYPE_MISMATCH " + file() + " [entity_hierarchy, invoice, has_many, 0] 4"),
        HierarchyFixture.describe(context));
  }

  @Test
  void entity19_aNullNameIsNeverConvertedToAString() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  item: {}",
        "  invoice:",
        "    has_many: [~]");
    assertEquals(List.of("E_TYPE_MISMATCH " + file() + " [entity_hierarchy, invoice, has_many, 0] 4"),
        HierarchyFixture.describe(context));
  }

  @Test
  void entity9_10_anEntityMayHaveSeveralParentsAndAncestorsAreTheTransitiveParents() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  customer: {}",
        "  invoice: {has_many: order}",
        "  order:",
        "    belongs_to: [invoice, customer]",
        "  line:",
        "    belongs_to: order");
    assertTrue(HierarchyFixture.describe(context).isEmpty());
    EntityIndex index = context.entities().orElseThrow();
    assertEquals(List.of("customer", "invoice"), index.parents("order"));
    assertEquals(List.of("customer", "invoice", "order"), List.copyOf(index.ancestors("line")));
  }

  @Test
  void entity11_12_childrenAreTheHasManyEntriesAndNothingIsInferred() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  invoice: {has_many: order}",
        "  order: {has_many: item}",
        "  item: {}");
    assertTrue(HierarchyFixture.describe(context).isEmpty());
    EntityIndex index = context.entities().orElseThrow();
    assertEquals(List.of("order"), index.children("invoice"));
    assertEquals(List.of("item"), index.children("order"));
    assertTrue(index.children("item").isEmpty(), "a child of a child is not a child");
    assertTrue(index.parents("invoice").isEmpty(), "a has_many entry does not make its owner a parent");
    assertTrue(index.ancestors("order").isEmpty(), "ancestors follow belongs_to only");
  }

  @Test
  void entity13_aParentListedOnBothSidesIsNoCycle() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  invoice: {has_many: order}",
        "  order: {belongs_to: invoice}");
    assertTrue(HierarchyFixture.describe(context).isEmpty());
    EntityIndex index = context.entities().orElseThrow();
    assertEquals(List.of("invoice"), index.parents("order"));
    assertEquals(List.of("order"), index.children("invoice"));
  }

  @Test
  void entity3_aRepeatedEntityNameInOneMappingIsADuplicateKey() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  customer: {}",
        "  customer: {}");
    assertEquals(List.of("E_DUPLICATE_KEY " + file() + " [entity_hierarchy, customer] 3"),
        HierarchyFixture.describe(context));
  }

  @Test
  void entity_thePartIsPublishedOnlyWhenTheEntityDeclarationsAreClean() throws IOException {
    CheckContext context = project(
        "entity_hierarchy:",
        "  customer: {}",
        "  order: {belongs_to: ghost}");
    assertFalse(context.parts().entities().isPresent());
    assertFalse(context.entities().isPresent());
  }
}
