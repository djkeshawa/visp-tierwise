package io.github.visplabs.tierwise.core.internal.check;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the cycles of the oriented entity graph (ENTITY-14 to ENTITY-17).
 *
 * <p>Every edge runs from a parent to a child: from {@code P} to {@code E} for {@code belongs_to: P} on {@code E},
 * and from {@code P} to {@code C} for {@code has_many: C} on {@code P}. A cycle is a strongly connected
 * component with more than one entity, or an entity with an edge to itself. The check covers a cycle through
 * {@code belongs_to} edges alone, through {@code has_many} edges alone, and through both (ENTITY-17). An entity
 * that is both an ancestor and a child of another lies on a cycle, so ENTITY-16 is covered too.
 *
 * <p>The graph is searched with Tarjan's algorithm. One cycle is reported once (FIND-33): each component is
 * reported at its entity that comes first in document order.
 */
final class EntityCycles {

  private final Map<String, Integer> index = new HashMap<>();
  private final Map<String, Integer> low = new HashMap<>();
  private final Set<String> onStack = new HashSet<>();
  private final Deque<String> stack = new ArrayDeque<>();
  private final Map<String, List<String>> successors;
  private final List<List<String>> components = new ArrayList<>();
  private int counter;

  private EntityCycles(Map<String, List<String>> successors) {
    this.successors = successors;
  }

  /**
   * Returns, for each cycle, the entity that comes first in document order.
   *
   * @param order the entity names in document order
   * @param successors the entities that each entity has an edge to, by name; a name without an entry has none
   * @return the first entity of each cyclic component, in document order
   */
  static List<String> firstOfEachCycle(List<String> order, Map<String, List<String>> successors) {
    EntityCycles search = new EntityCycles(successors);
    for (String name : order) {
      if (!search.index.containsKey(name)) {
        search.connect(name);
      }
    }
    Map<String, Integer> position = new HashMap<>();
    for (int i = 0; i < order.size(); i++) {
      position.put(order.get(i), i);
    }
    List<String> firsts = new ArrayList<>();
    for (List<String> component : search.components) {
      if (search.isCycle(component)) {
        firsts.add(component.stream().min((a, b) -> Integer.compare(position.get(a), position.get(b))).orElseThrow());
      }
    }
    firsts.sort((a, b) -> Integer.compare(position.get(a), position.get(b)));
    return firsts;
  }

  private boolean isCycle(List<String> component) {
    if (component.size() > 1) {
      return true;
    }
    String only = component.get(0);
    return successors.getOrDefault(only, List.of()).contains(only);
  }

  private void connect(String v) {
    index.put(v, counter);
    low.put(v, counter);
    counter++;
    stack.push(v);
    onStack.add(v);
    for (String w : successors.getOrDefault(v, List.of())) {
      if (!index.containsKey(w)) {
        connect(w);
        low.put(v, Math.min(low.get(v), low.get(w)));
      } else if (onStack.contains(w)) {
        low.put(v, Math.min(low.get(v), index.get(w)));
      }
    }
    if (low.get(v).equals(index.get(v))) {
      List<String> component = new ArrayList<>();
      String w;
      do {
        w = stack.pop();
        onStack.remove(w);
        component.add(w);
      } while (!w.equals(v));
      components.add(component);
    }
  }
}
