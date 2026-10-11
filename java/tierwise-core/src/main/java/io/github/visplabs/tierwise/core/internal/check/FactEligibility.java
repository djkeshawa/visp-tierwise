package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.internal.project.CodePointOrder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The facts that a decision about one entity may read (FACT-13 to FACT-15).
 *
 * @param singleValue the facts declared on the decided entity and on each of its ancestors (FACT-13), in code
 *     point order
 * @param childFacts the facts declared on each child of the decided entity (FACT-14), grouped by child entity in
 *     the order the child is declared; a child with no fact maps to an empty set
 */
public record FactEligibility(Set<String> singleValue, Map<String, Set<String>> childFacts) {

  /** Copies the collections into unmodifiable, sorted form. */
  public FactEligibility {
    TreeSet<String> single = new TreeSet<>(CodePointOrder.ORDER);
    single.addAll(singleValue);
    singleValue = Collections.unmodifiableSortedSet(single);
    Map<String, Set<String>> children = new LinkedHashMap<>();
    for (Map.Entry<String, Set<String>> child : childFacts.entrySet()) {
      TreeSet<String> sorted = new TreeSet<>(CodePointOrder.ORDER);
      sorted.addAll(child.getValue());
      children.put(child.getKey(), Collections.unmodifiableSortedSet(sorted));
    }
    childFacts = Collections.unmodifiableMap(children);
  }

  /**
   * Tells whether a fact is eligible: a single-value fact or a child fact (FACT-15).
   *
   * @param fact the fact name in NFC form
   * @return {@code true} when the fact may be read by the decision
   */
  public boolean isEligible(String fact) {
    return singleValue.contains(fact) || childFacts.values().stream().anyMatch(set -> set.contains(fact));
  }
}
