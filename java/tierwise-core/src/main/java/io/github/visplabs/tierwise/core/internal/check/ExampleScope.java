package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.internal.model.EntityDef;
import io.github.visplabs.tierwise.core.internal.model.Model;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * The entities whose facts an example of a decision about one entity may use (ENTITY-10, ENTITY-11,
 * FACT-13 to FACT-15): the decided entity and its ancestors give single-value facts, and its children give
 * child facts. A grandchild, or an entity that only names the decided one in {@code has_many}, gives no
 * eligible fact.
 */
final class ExampleScope {

  private final Set<String> singleValue;
  private final Set<String> children;

  private ExampleScope(Set<String> singleValue, Set<String> children) {
    this.singleValue = singleValue;
    this.children = children;
  }

  /**
   * Computes the scope of a decided entity.
   *
   * @param model the model
   * @param decided the decided entity
   * @return the scope
   */
  static ExampleScope of(Model model, String decided) {
    Set<String> ancestors = new HashSet<>();
    ancestors.add(decided);
    Deque<String> pending = new ArrayDeque<>();
    pending.push(decided);
    while (!pending.isEmpty()) {
      EntityDef current = model.entities().get(pending.pop());
      if (current == null) {
        continue;
      }
      for (String parent : current.belongsTo()) {
        if (ancestors.add(parent)) {
          pending.push(parent);
        }
      }
    }
    EntityDef decidedDef = model.entities().get(decided);
    Set<String> children = decidedDef == null ? Set.of() : new HashSet<>(decidedDef.hasMany());
    return new ExampleScope(Set.copyOf(ancestors), Set.copyOf(children));
  }

  /**
   * Tells whether facts of an entity are single-value facts of the decision.
   *
   * @param entity the entity that declares the fact
   * @return {@code true} for the decided entity or one of its ancestors
   */
  boolean isSingleValue(String entity) {
    return singleValue.contains(entity);
  }

  /**
   * Tells whether facts of an entity are child facts of the decision.
   *
   * @param entity the entity that declares the fact
   * @return {@code true} for a child of the decided entity
   */
  boolean isChild(String entity) {
    return children.contains(entity);
  }
}
