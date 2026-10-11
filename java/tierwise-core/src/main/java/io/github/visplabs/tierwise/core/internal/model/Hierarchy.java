package io.github.visplabs.tierwise.core.internal.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The decision hierarchy: ordered levels, ordered axes and one {@link ScopeDef} for every non-root
 * level and every axis (BUNDLE-29 to BUNDLE-32).
 *
 * @param levels level names in declared order, the root level first
 * @param axes axis names in declared order; empty when none is declared
 * @param scopes scope definitions by scope name; the root level has none
 */
public record Hierarchy(List<String> levels, List<String> axes, Map<String, ScopeDef> scopes) {

  /** Validates the components and makes the collections unmodifiable. */
  public Hierarchy {
    levels = List.copyOf(levels);
    axes = List.copyOf(axes);
    scopes = Plain.orderedMap(scopes);
    if (levels.isEmpty()) {
      throw new IllegalArgumentException("a hierarchy has at least the root level");
    }
  }

  /**
   * Returns the root level, the first declared level.
   *
   * @return the root level name
   */
  public String rootLevel() {
    return levels.get(0);
  }

  /**
   * Tells whether a name is a declared level (the root level included).
   *
   * @param name a scope name
   * @return {@code true} for a level
   */
  public boolean isLevel(String name) {
    return levels.contains(name);
  }

  /**
   * Tells whether a name is a declared axis.
   *
   * @param name a scope name
   * @return {@code true} for an axis
   */
  public boolean isAxis(String name) {
    return axes.contains(name);
  }

  /**
   * Returns the scopes in evaluation-path order: levels in declared order, then axes in declared order
   * (MERGE-13), the root level included.
   *
   * @return an unmodifiable list of scope names
   */
  public List<String> scopeOrder() {
    List<String> path = new java.util.ArrayList<>(levels);
    path.addAll(axes);
    return java.util.Collections.unmodifiableList(path);
  }

  /**
   * Returns the definition of a non-root scope.
   *
   * @param name a scope name
   * @return the definition, or {@code null} for the root level or an undeclared name
   */
  public ScopeDef scope(String name) {
    Objects.requireNonNull(name, "name");
    return scopes.get(name);
  }
}
