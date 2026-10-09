package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.Plain;
import io.github.tierwise.core.internal.project.CodePointOrder;
import io.github.tierwise.core.internal.project.Names;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The declared facts and the facts that a decision about an entity may read, which the rule and feature stages
 * call (FACT-13 to FACT-15, FACT-3).
 *
 * <p>It is published by {@link EntityFactStage} through {@link CheckContext#facts()}, when both the facts and the
 * entities were built without a fault.
 */
public final class FactIndex {

  private final Map<String, FactDef> facts;
  private final EntityIndex entities;

  /**
   * Creates the index of built facts.
   *
   * @param facts the fact declarations by name
   * @param entities the entity index that gives the ancestors and the children
   */
  public FactIndex(Map<String, FactDef> facts, EntityIndex entities) {
    this.facts = Plain.orderedMap(facts);
    this.entities = entities;
  }

  /**
   * Tells whether a fact is declared.
   *
   * @param name the name as written, compared after NFC normalisation (FACT-3)
   * @return {@code true} for a declared fact
   */
  public boolean isDeclared(String name) {
    return facts.containsKey(Names.nfc(name));
  }

  /**
   * Returns the declaration of a fact.
   *
   * @param name the name as written
   * @return the declaration, or empty for an undeclared fact
   */
  public Optional<FactDef> definition(String name) {
    return Optional.ofNullable(facts.get(Names.nfc(name)));
  }

  /**
   * Returns the facts that a decision about an entity may read. An undeclared entity has none.
   *
   * @param decidedEntity the entity the decision is about (FEAT-4)
   * @return the single-value facts of the entity and its ancestors, and the facts of each child
   */
  public FactEligibility eligibleFor(String decidedEntity) {
    String entity = Names.nfc(decidedEntity);
    if (!entities.isDeclared(entity)) {
      return new FactEligibility(Set.of(), Map.of());
    }
    Set<String> owners = new TreeSet<>(CodePointOrder.ORDER);
    owners.add(entity);
    owners.addAll(entities.ancestors(entity));
    Set<String> single = new TreeSet<>(CodePointOrder.ORDER);
    for (Map.Entry<String, FactDef> fact : facts.entrySet()) {
      if (owners.contains(fact.getValue().entity())) {
        single.add(fact.getKey());
      }
    }
    Map<String, Set<String>> children = new LinkedHashMap<>();
    for (String child : entities.children(entity)) {
      Set<String> own = new TreeSet<>(CodePointOrder.ORDER);
      for (Map.Entry<String, FactDef> fact : facts.entrySet()) {
        if (fact.getValue().entity().equals(child)) {
          own.add(fact.getKey());
        }
      }
      children.put(child, own);
    }
    return new FactEligibility(single, children);
  }
}
