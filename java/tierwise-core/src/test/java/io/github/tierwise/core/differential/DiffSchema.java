package io.github.tierwise.core.differential;

import io.github.tierwise.core.internal.model.EntityDef;
import io.github.tierwise.core.internal.model.FactDef;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * The entity graph and fact scopes of a generated model (ENTITY-*, FACT-*, COND-*).
 *
 * <p>A decision about an entity reads the single-value facts of the entity and of its transitive {@code belongsTo}
 * ancestors. It reads the child facts of the entity's direct {@code hasMany} children (one level down, COND-*). Every
 * result is sorted in code point order.
 */
final class DiffSchema {

  private DiffSchema() {
  }

  /**
   * Returns the entities whose single-value facts a decision about an entity may use: the entity and its transitive
   * {@code belongsTo} ancestors.
   *
   * @param entities the entities of the model
   * @param entity the decided entity
   * @return the entity names in code point order
   */
  static Set<String> decidedScope(Map<String, EntityDef> entities, String entity) {
    Set<String> scope = new TreeSet<>(DiffNames.CODE_POINT);
    Deque<String> pending = new ArrayDeque<>();
    pending.push(entity);
    while (!pending.isEmpty()) {
      String current = pending.pop();
      if (scope.add(current)) {
        pending.addAll(entities.get(current).belongsTo());
      }
    }
    return scope;
  }

  /**
   * Returns the direct {@code hasMany} children of an entity.
   *
   * @param entities the entities of the model
   * @param entity the entity
   * @return the child entity names in code point order
   */
  static List<String> children(Map<String, EntityDef> entities, String entity) {
    return new ArrayList<>(entities.get(entity).hasMany());
  }

  /**
   * Returns the single-value facts that a decision about an entity may use, in code point order.
   *
   * @param entities the entities of the model
   * @param facts the facts of the model
   * @param decidedPer the decided entity
   * @return the fact names
   */
  static List<String> singleFacts(Map<String, EntityDef> entities, Map<String, FactDef> facts, String decidedPer) {
    Set<String> scope = decidedScope(entities, decidedPer);
    return factsWhere(facts, scope::contains);
  }

  /**
   * Returns the child facts that a decision about an entity may use, in code point order.
   *
   * @param entities the entities of the model
   * @param facts the facts of the model
   * @param decidedPer the decided entity
   * @return the fact names
   */
  static List<String> childFacts(Map<String, EntityDef> entities, Map<String, FactDef> facts, String decidedPer) {
    List<String> children = children(entities, decidedPer);
    return factsWhere(facts, children::contains);
  }

  /**
   * Returns the facts that belong to one entity, in code point order.
   *
   * @param facts the facts of the model
   * @param entity the entity
   * @return the fact names
   */
  static List<String> factsOf(Map<String, FactDef> facts, String entity) {
    return factsWhere(facts, entity::equals);
  }

  /**
   * Returns the names of the facts whose entity satisfies a predicate, in code point order.
   *
   * @param facts the facts of the model
   * @param entityTest the test on an entity name
   * @return the fact names
   */
  static List<String> factsWhere(Map<String, FactDef> facts, Predicate<String> entityTest) {
    List<String> out = new ArrayList<>();
    for (Map.Entry<String, FactDef> fact : facts.entrySet()) {
      if (entityTest.test(fact.getValue().entity())) {
        out.add(fact.getKey());
      }
    }
    out.sort(DiffNames.CODE_POINT);
    return out;
  }
}
