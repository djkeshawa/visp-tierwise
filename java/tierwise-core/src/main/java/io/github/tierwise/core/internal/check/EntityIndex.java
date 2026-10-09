package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.model.EntityDef;
import io.github.tierwise.core.internal.model.Plain;
import io.github.tierwise.core.internal.project.CodePointOrder;
import io.github.tierwise.core.internal.project.NameSuggester;
import io.github.tierwise.core.internal.project.Names;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The entity graph of a built model, which the later stages call: the names that a decision or a relation
 * writes (ENTITY-6, FEAT-5) and the ancestors and children of an entity (ENTITY-10, ENTITY-11).
 *
 * <p>It is published by {@link EntityFactStage} through {@link CheckContext#entities()}, so it exists only when the
 * entity declarations were built without a fault. A caller that finds no index skips the checks that need it
 * (FIND-35).
 */
public final class EntityIndex {

  private final Map<String, EntityDef> definitions;

  /**
   * Creates the index of built entities.
   *
   * @param definitions the entity definitions by name
   */
  public EntityIndex(Map<String, EntityDef> definitions) {
    this.definitions = Plain.orderedMap(definitions);
  }

  /**
   * Tells whether an entity is declared.
   *
   * @param name the name as written, compared after NFC normalisation (FACT-3)
   * @return {@code true} for a declared entity
   */
  public boolean isDeclared(String name) {
    return definitions.containsKey(Names.nfc(name));
  }

  /**
   * Returns the definition of an entity.
   *
   * @param name the name as written
   * @return the definition, or empty for an undeclared entity
   */
  public Optional<EntityDef> definition(String name) {
    return Optional.ofNullable(definitions.get(Names.nfc(name)));
  }

  /**
   * Returns the declared entity names in document order.
   *
   * @return an unmodifiable set
   */
  public Set<String> names() {
    return definitions.keySet();
  }

  /**
   * Resolves an entity name that a source writes (ENTITY-6, FEAT-5).
   *
   * @param findings the accumulator
   * @param location the node that writes the name
   * @param written the name as written
   * @return the NFC form when the entity is declared, otherwise empty after {@code E_UNKNOWN_ENTITY} is reported
   */
  public Optional<String> resolveEntity(Findings findings, PNode location, String written) {
    String name = Names.nfc(written);
    if (isDeclared(name)) {
      return Optional.of(name);
    }
    findings.error(Code.E_UNKNOWN_ENTITY, location, "the entity " + name + " is not declared (ENTITY-6, FEAT-5)",
        NameSuggester.closest(name, definitions.keySet()));
    return Optional.empty();
  }

  /**
   * Returns the parents of an entity: the entities in its {@code belongs_to}.
   *
   * @param name the entity name
   * @return the parents in code point order, or empty for an undeclared entity
   */
  public List<String> parents(String name) {
    return definition(name).map(EntityDef::belongsTo).orElse(List.of());
  }

  /**
   * Returns the children of an entity: the entities in its {@code has_many} (ENTITY-11).
   *
   * @param name the entity name
   * @return the children in code point order, or empty for an undeclared entity
   */
  public List<String> children(String name) {
    return definition(name).map(EntityDef::hasMany).orElse(List.of());
  }

  /**
   * Returns the ancestors of an entity: the entities reachable by one or more {@code belongs_to} edges
   * (ENTITY-10). An evaluator never infers a relation that is not declared (ENTITY-12).
   *
   * @param name the entity name
   * @return the ancestors in code point order
   */
  public Set<String> ancestors(String name) {
    Set<String> found = new TreeSet<>(CodePointOrder.ORDER);
    Deque<String> pending = new ArrayDeque<>(parents(name));
    while (!pending.isEmpty()) {
      String next = pending.pop();
      if (found.add(next)) {
        List<String> more = new ArrayList<>(parents(next));
        more.forEach(pending::push);
      }
    }
    return found;
  }
}
