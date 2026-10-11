package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.EntityDef;
import io.github.visplabs.tierwise.core.internal.project.CodePointOrder;
import io.github.visplabs.tierwise.core.internal.project.NameSuggester;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checks the entity relations against the declared names and builds the entity definitions (ENTITY-6, ENTITY-9
 * to ENTITY-17).
 *
 * <p>A relation name that is not declared is {@code E_UNKNOWN_ENTITY} at the node that writes it (ENTITY-6). It
 * is left out of the graph, so it adds no cycle. A cycle is {@code E_ENTITY_GRAPH} at the entity's value
 * (FIND-33). The definitions list each relation in code point order (BUNDLE-44 to BUNDLE-47).
 */
final class EntityGraph {

  private final EntityReport report;

  /**
   * Creates a graph checker.
   *
   * @param report the report that receives the faults
   */
  EntityGraph(EntityReport report) {
    this.report = report;
  }

  /**
   * Checks the entries and builds their definitions.
   *
   * @param entries the declared entities in document order
   * @return the definitions by name, in document order; complete only when the report stays clean
   */
  Map<String, EntityDef> build(List<EntityDeclaration.Entry> entries) {
    Set<String> declared = new LinkedHashSet<>();
    Map<String, PNode> locations = new HashMap<>();
    for (EntityDeclaration.Entry entry : entries) {
      declared.add(entry.name());
      locations.put(entry.name(), entry.valueNode());
    }
    Map<String, List<String>> successors = new HashMap<>();
    for (EntityDeclaration.Entry entry : entries) {
      successors.put(entry.name(), new ArrayList<>());
    }
    for (EntityDeclaration.Entry entry : entries) {
      for (EntityDeclaration.Ref parent : entry.belongsTo()) {
        if (requireDeclared(parent, declared)) {
          successors.get(parent.name()).add(entry.name());
        }
      }
      for (EntityDeclaration.Ref child : entry.hasMany()) {
        if (requireDeclared(child, declared)) {
          successors.get(entry.name()).add(child.name());
        }
      }
    }
    for (String name : EntityCycles.firstOfEachCycle(new ArrayList<>(declared), successors)) {
      report.at(Code.E_ENTITY_GRAPH, locations.get(name),
          "the entity " + name + " lies on a cycle of the relations (ENTITY-14 to ENTITY-17, FIND-33)");
    }
    Map<String, EntityDef> definitions = new LinkedHashMap<>();
    for (EntityDeclaration.Entry entry : entries) {
      definitions.put(entry.name(), new EntityDef(declaredNames(entry.belongsTo(), declared),
          declaredNames(entry.hasMany(), declared)));
    }
    return definitions;
  }

  /** Reports a relation name that is not declared (ENTITY-6); returns whether it is declared. */
  private boolean requireDeclared(EntityDeclaration.Ref ref, Set<String> declared) {
    if (declared.contains(ref.name())) {
      return true;
    }
    report.at(Code.E_UNKNOWN_ENTITY, ref.location(), "the entity " + ref.name() + " is not declared (ENTITY-6)",
        NameSuggester.closest(ref.name(), declared));
    return false;
  }

  /** The declared names of a relation, sorted in code point order. */
  private static List<String> declaredNames(List<EntityDeclaration.Ref> refs, Set<String> declared) {
    List<String> names = new ArrayList<>();
    for (EntityDeclaration.Ref ref : refs) {
      if (declared.contains(ref.name())) {
        names.add(ref.name());
      }
    }
    names.sort(CodePointOrder.ORDER);
    return names;
  }
}
