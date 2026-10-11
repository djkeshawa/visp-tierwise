package io.github.visplabs.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cycles of the oriented entity graph (ENTITY-13 to ENTITY-17, FIND-33). The unit tests give the graph
 * directly; the stage tests give the same cases as the schemas a project writes.
 */
class EntityCyclesTest {

  @TempDir
  Path dir;

  private static Map<String, List<String>> edges(Object... pairs) {
    Map<String, List<String>> map = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], new java.util.ArrayList<>(List.of((String[]) pairs[i + 1])));
    }
    return map;
  }

  private static List<String> order(String... names) {
    return List.of(names);
  }

  @Test
  void entity13_17_anAcyclicGraphHasNoCycle() {
    Map<String, List<String>> graph = edges("gamma", new String[] {"alpha"}, "alpha", new String[] {"beta"},
        "beta", new String[] {});
    assertTrue(EntityCycles.firstOfEachCycle(order("alpha", "beta", "gamma"), graph).isEmpty());
  }

  @Test
  void entity14_17_aTwoEntityCycleIsReportedOnceAtTheFirstEntityInDocumentOrder() {
    Map<String, List<String>> graph = edges("alpha", new String[] {"gamma"}, "gamma", new String[] {"alpha"});
    assertEquals(List.of("alpha"), EntityCycles.firstOfEachCycle(order("alpha", "gamma"), graph));
  }

  @Test
  void entity16_17_aCycleThroughBothKindsOfEdgesIsReportedAtTheFirstEntity() {
    // alpha belongs to gamma (gamma -> alpha), alpha has beta (alpha -> beta), beta has gamma (beta -> gamma).
    Map<String, List<String>> graph = edges("gamma", new String[] {"alpha"}, "alpha", new String[] {"beta"},
        "beta", new String[] {"gamma"});
    assertEquals(List.of("alpha"), EntityCycles.firstOfEachCycle(order("alpha", "beta", "gamma"), graph));
  }

  @Test
  void entity14_15_aSelfEdgeIsACycle() {
    Map<String, List<String>> graph = edges("gamma", new String[] {"gamma"});
    assertEquals(List.of("gamma"), EntityCycles.firstOfEachCycle(order("alpha", "gamma"), graph));
  }

  @Test
  void findings33_twoCyclesAreReportedOnceEachInDocumentOrder() {
    Map<String, List<String>> graph = edges("a", new String[] {"b"}, "b", new String[] {"a"},
        "c", new String[] {"d"}, "d", new String[] {"c"});
    assertEquals(List.of("a", "c"), EntityCycles.firstOfEachCycle(order("a", "b", "c", "d"), graph));
  }

  @Test
  void entity16_anEntityThatIsBothAncestorAndChildOfAnotherIsACycle() throws IOException {
    HierarchyFixture.schema(dir, HierarchyDeclarationTest.text(
        "entity_hierarchy:",
        "  alpha:",
        "    belongs_to: gamma",
        "    has_many: [beta, gamma]",
        "  beta: {}",
        "  gamma: {}"));
    List<String> found = HierarchyFixture.describe(HierarchyFixture.run(dir), "E_ENTITY_GRAPH");
    assertEquals(List.of("E_ENTITY_GRAPH " + HierarchyFixture.SCHEMA + " [entity_hierarchy, alpha] 2"), found);
  }

  @Test
  void entity14_aBelongsToSelfEdgeIsReportedAtTheEntity() throws IOException {
    HierarchyFixture.schema(dir, HierarchyDeclarationTest.text(
        "entity_hierarchy:",
        "  gamma:",
        "    belongs_to: gamma"));
    assertEquals(List.of("E_ENTITY_GRAPH " + HierarchyFixture.SCHEMA + " [entity_hierarchy, gamma] 2"),
        HierarchyFixture.describe(HierarchyFixture.run(dir), "E_ENTITY_GRAPH"));
  }

  @Test
  void entity15_aHasManySelfEdgeIsReportedAtTheEntity() throws IOException {
    HierarchyFixture.schema(dir, HierarchyDeclarationTest.text(
        "entity_hierarchy:",
        "  beta:",
        "    has_many: beta"));
    assertEquals(List.of("E_ENTITY_GRAPH " + HierarchyFixture.SCHEMA + " [entity_hierarchy, beta] 2"),
        HierarchyFixture.describe(HierarchyFixture.run(dir), "E_ENTITY_GRAPH"));
  }

  @Test
  void entity17_anAcyclicProjectHasNoEntityGraphFinding() throws IOException {
    HierarchyFixture.schema(dir, HierarchyDeclarationTest.text(
        "entity_hierarchy:",
        "  customer: {}",
        "  invoice: {has_many: order}",
        "  order: {belongs_to: [invoice, customer]}"));
    assertTrue(HierarchyFixture.describe(HierarchyFixture.run(dir), "E_ENTITY_GRAPH").isEmpty());
  }
}
