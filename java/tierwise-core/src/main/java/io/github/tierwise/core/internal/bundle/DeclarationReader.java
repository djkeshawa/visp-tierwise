package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.model.EntityDef;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code entities} and {@code facts} (BUNDLE-44 to BUNDLE-50, BUNDLE-121, BUNDLE-122).
 */
final class DeclarationReader {

  /** The types that a fact may have (BUNDLE-50). */
  private static final Set<String> FACT_TYPES = Set.of("boolean", "integer", "decimal", "string", "enum");

  private DeclarationReader() {
  }

  /**
   * Reads the entities and their direct edges. Every edge must name a declared entity (BUNDLE-121).
   *
   * @param value the {@code entities} member
   * @return the entities by name
   */
  static Map<String, EntityDef> entities(Object value) {
    Map<String, Object> raw = Members.object(value, "content.entities");
    Map<String, EntityDef> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      String at = "content.entities." + entry.getKey();
      requireEntityName(entry.getKey(), at);
      Map<String, Object> members = Members.object(entry.getValue(), at);
      Members.members(members, at, Set.of("belongsTo", "hasMany"), Set.of());
      out.put(entry.getKey(), new EntityDef(sortedEdges(members.get("belongsTo"), at + ".belongsTo"),
          sortedEdges(members.get("hasMany"), at + ".hasMany")));
    }
    for (EntityDef entity : out.values()) {
      requireDeclared(entity.belongsTo(), out.keySet());
      requireDeclared(entity.hasMany(), out.keySet());
    }
    requireAcyclic(out);
    return out;
  }

  /**
   * Requires that the oriented entity graph has no cycle (ENTITY-14 to ENTITY-17, BUNDLE-118). Every edge runs from
   * parent to child: from P to E for a {@code belongsTo} member of E, and from P to C for a {@code hasMany} member of
   * P. A self edge is a cycle. The check removes entities with no incoming edge until none is left, which needs no
   * recursion, so a long chain cannot exhaust the stack.
   *
   * @param entities the declared entities, whose edges name declared entities
   */
  private static void requireAcyclic(Map<String, EntityDef> entities) {
    Map<String, Integer> incoming = new HashMap<>();
    Map<String, List<String>> outgoing = new HashMap<>();
    for (String name : entities.keySet()) {
      incoming.put(name, 0);
      outgoing.put(name, new ArrayList<>());
    }
    for (Map.Entry<String, EntityDef> entry : entities.entrySet()) {
      for (String parent : entry.getValue().belongsTo()) {
        addEdge(parent, entry.getKey(), incoming, outgoing);
      }
      for (String child : entry.getValue().hasMany()) {
        addEdge(entry.getKey(), child, incoming, outgoing);
      }
    }
    Deque<String> ready = new ArrayDeque<>();
    for (String name : entities.keySet()) {
      if (incoming.get(name) == 0) {
        ready.push(name);
      }
    }
    int visited = 0;
    while (!ready.isEmpty()) {
      String name = ready.pop();
      visited++;
      for (String child : outgoing.get(name)) {
        int left = incoming.get(child) - 1;
        incoming.put(child, left);
        if (left == 0) {
          ready.push(child);
        }
      }
    }
    if (visited != entities.size()) {
      throw BundleFault.integrity("the oriented entity graph has a cycle (ENTITY-14, ENTITY-15, ENTITY-16 or ENTITY-17)");
    }
  }

  private static void addEdge(String parent, String child, Map<String, Integer> incoming,
      Map<String, List<String>> outgoing) {
    outgoing.get(parent).add(child);
    incoming.put(child, incoming.get(child) + 1);
  }

  /**
   * Reads the facts. A fact is named {@code <entity>.<attribute>}, split at its first dot (FACT-3).
   *
   * @param value the {@code facts} member
   * @param entities the declared entities
   * @return the facts by name
   */
  static Map<String, FactDef> facts(Object value, Map<String, EntityDef> entities) {
    Map<String, Object> raw = Members.object(value, "content.facts");
    Map<String, FactDef> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      String name = entry.getKey();
      String at = "content.facts." + name;
      int dot = name.indexOf('.');
      if (dot < 0 || dot == name.length() - 1) {
        throw BundleFault.integrity(at + " is not named <entity>.<attribute> (FACT-3)");
      }
      String prefix = name.substring(0, dot);
      Map<String, Object> members = Members.object(entry.getValue(), at);
      Members.members(members, at, Set.of("entity", "type"), Set.of());
      String entity = Members.string(members.get("entity"), at + ".entity");
      if (!entity.equals(prefix)) {
        throw BundleFault.integrity(at + ".entity differs from the prefix of the fact name (BUNDLE-122)");
      }
      if (!entities.containsKey(entity)) {
        throw BundleFault.integrity(at + " names the undeclared entity " + entity + " (BUNDLE-121)");
      }
      TypeDecl type = TypeReader.read(members.get("type"), at + ".type", false);
      if (!FACT_TYPES.contains(type.type())) {
        throw BundleFault.integrity(at + " has the type " + type.type() + ", which a fact may not have (BUNDLE-50)");
      }
      out.put(name, new FactDef(entity, type));
    }
    return out;
  }

  private static void requireEntityName(String name, String at) {
    if (name.isEmpty() || name.indexOf('.') >= 0) {
      throw BundleFault.integrity(at + " is not a valid entity name (ENTITY-18)");
    }
  }

  private static List<String> sortedEdges(Object value, String at) {
    List<String> names = Members.stringArray(value, at);
    if (!Order.strictlyIncreasing(names)) {
      throw BundleFault.integrity(at + " is not in code point order without repeats (BUNDLE-75)");
    }
    return names;
  }

  private static void requireDeclared(List<String> names, Set<String> declared) {
    for (String name : names) {
      if (!declared.contains(name)) {
        throw BundleFault.integrity("the edge names the undeclared entity " + name + " (BUNDLE-121)");
      }
    }
  }
}
