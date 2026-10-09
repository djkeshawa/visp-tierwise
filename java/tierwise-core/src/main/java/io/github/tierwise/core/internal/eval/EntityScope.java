package io.github.tierwise.core.internal.eval;

import io.github.tierwise.core.internal.model.EntityDef;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.Model;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The facts that are eligible for a decision about one entity (FACT-13 to FACT-15): the single-value
 * facts, declared on the decided entity and on its ancestors, and the child facts, declared on its
 * children and grouped by child entity. An evaluator infers no other relation (ENTITY-12).
 */
final class EntityScope {

  private final String entity;
  private final Set<String> singleValueFacts;
  private final Map<String, Set<String>> childFacts;

  private EntityScope(String entity, Set<String> singleValueFacts, Map<String, Set<String>> childFacts) {
    this.entity = entity;
    this.singleValueFacts = singleValueFacts;
    this.childFacts = childFacts;
  }

  /**
   * Computes the eligible facts of an entity.
   *
   * @param model the model of the bundle
   * @param decided the decided entity
   * @return the scope
   */
  static EntityScope of(Model model, String decided) {
    Set<String> entities = new HashSet<>();
    entities.add(decided);
    Deque<String> toVisit = new ArrayDeque<>();
    toVisit.push(decided);
    while (!toVisit.isEmpty()) {
      EntityDef current = model.entities().get(toVisit.pop());
      if (current == null) {
        continue;
      }
      for (String parent : current.belongsTo()) {
        if (entities.add(parent)) {
          toVisit.push(parent);
        }
      }
    }
    EntityDef decidedDef = model.entities().get(decided);
    Map<String, Set<String>> children = new HashMap<>();
    if (decidedDef != null) {
      for (String child : decidedDef.hasMany()) {
        children.put(child, new HashSet<>());
      }
    }
    Set<String> single = new HashSet<>();
    for (Map.Entry<String, FactDef> fact : model.facts().entrySet()) {
      String owner = fact.getValue().entity();
      if (entities.contains(owner)) {
        single.add(fact.getKey());
      } else if (children.containsKey(owner)) {
        children.get(owner).add(fact.getKey());
      }
    }
    return new EntityScope(decided, Set.copyOf(single), freeze(children));
  }

  private static Map<String, Set<String>> freeze(Map<String, Set<String>> children) {
    Map<String, Set<String>> frozen = new HashMap<>();
    children.forEach((entity, facts) -> frozen.put(entity, Set.copyOf(facts)));
    return Map.copyOf(frozen);
  }

  /**
   * Returns the decided entity.
   *
   * @return the entity name
   */
  String entity() {
    return entity;
  }

  /**
   * Tells whether a fact is a single-value fact of the decision (FACT-13).
   *
   * @param fact a fact name
   * @return {@code true} when it is declared on the decided entity or an ancestor
   */
  boolean isSingleValue(String fact) {
    return singleValueFacts.contains(fact);
  }

  /**
   * Tells whether an entity is a child of the decided entity (ENTITY-11).
   *
   * @param name an entity name
   * @return {@code true} for a child
   */
  boolean isChild(String name) {
    return childFacts.containsKey(name);
  }

  /**
   * Returns the facts declared on a child entity (FACT-14).
   *
   * @param child a child entity of the decided entity
   * @return the fact names
   */
  Set<String> factsOfChild(String child) {
    return childFacts.get(child);
  }
}
