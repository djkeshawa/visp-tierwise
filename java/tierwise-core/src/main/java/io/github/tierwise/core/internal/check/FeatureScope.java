package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.internal.model.EntityDef;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The facts that a decision about one entity may read (FACT-13 to FACT-15), and the depth test of RULE-11.
 *
 * <p>The single-value facts belong to the decided entity and its ancestors, the child facts to its
 * children. An entity that is neither is out of scope, and it is {@link Position#DEEP} when it is
 * reachable from the decided entity through two or more {@code has_many} edges.
 */
final class FeatureScope {

  /** Where an entity stands relative to the decided entity. */
  enum Position {
    /** The decided entity or an ancestor: its facts are single-value facts. */
    SINGLE,
    /** A child of the decided entity: its facts are child facts. */
    CHILD,
    /** Out of scope, and reachable through two or more {@code has_many} edges (RULE-11). */
    DEEP,
    /** Out of scope for any other reason (RULE-12). */
    OUT
  }

  private final Set<String> single;
  private final Set<String> children;
  private final Set<String> deep;

  private FeatureScope(Set<String> single, Set<String> children, Set<String> deep) {
    this.single = single;
    this.children = children;
    this.deep = deep;
  }

  /**
   * Computes the scope of a decided entity.
   *
   * @param decided the decided entity, which is declared
   * @param entities the declared entities by name
   * @return the scope
   */
  static FeatureScope of(String decided, Map<String, EntityDef> entities) {
    Set<String> single = new HashSet<>();
    Deque<String> pending = new ArrayDeque<>();
    single.add(decided);
    pending.add(decided);
    while (!pending.isEmpty()) {
      EntityDef entity = entities.get(pending.poll());
      if (entity == null) {
        continue;
      }
      for (String parent : entity.belongsTo()) {
        if (single.add(parent)) {
          pending.add(parent);
        }
      }
    }
    Set<String> children = new HashSet<>();
    EntityDef decidedEntity = entities.get(decided);
    if (decidedEntity != null) {
      children.addAll(decidedEntity.hasMany());
    }
    Set<String> deep = new HashSet<>();
    pending.addAll(children);
    Set<String> expanded = new HashSet<>();
    while (!pending.isEmpty()) {
      String name = pending.poll();
      if (!expanded.add(name)) {
        continue;
      }
      EntityDef entity = entities.get(name);
      if (entity == null) {
        continue;
      }
      for (String child : entity.hasMany()) {
        deep.add(child);
        pending.add(child);
      }
    }
    return new FeatureScope(single, children, deep);
  }

  /**
   * Classifies an entity.
   *
   * @param entity an entity name
   * @return its position relative to the decided entity
   */
  Position position(String entity) {
    if (single.contains(entity)) {
      return Position.SINGLE;
    }
    if (children.contains(entity)) {
      return Position.CHILD;
    }
    return deep.contains(entity) ? Position.DEEP : Position.OUT;
  }
}
