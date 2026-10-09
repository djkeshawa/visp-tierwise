package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.List;
import java.util.Set;

/**
 * The facts about merge strategies that section 5 fixes: which strategies exist and how they are
 * classified (MERGE-1, MERGE-2), and which types each allows (MERGE-3).
 */
final class StrategyRules {

  /** The commutative strategies (MERGE-1). */
  static final Set<String> COMMUTATIVE = Set.of("all", "any", "intersect", "union", "min", "max");

  /** The order-dependent strategies (MERGE-1). */
  static final Set<String> ORDER_DEPENDENT = Set.of("override", "deep_merge");

  /** The narrowing strategies (MERGE-2). */
  static final Set<String> NARROWING = Set.of("all", "intersect", "min");

  /** The values that {@code combine_across_axes} may take (AXIS-4). */
  static final List<String> ACROSS_AXES = List.of("axis_wins", "hierarchy_wins", "max", "min");

  private StrategyRules() {
  }

  /**
   * Tells whether a name is a merge strategy (MERGE-1).
   *
   * @param name a name
   * @return {@code true} for one of the eight strategies
   */
  static boolean isStrategy(String name) {
    return COMMUTATIVE.contains(name) || ORDER_DEPENDENT.contains(name);
  }

  /**
   * Tells whether a strategy is commutative (MERGE-1).
   *
   * @param strategy a strategy name
   * @return {@code true} for a commutative strategy
   */
  static boolean isCommutative(String strategy) {
    return COMMUTATIVE.contains(strategy);
  }

  /**
   * Tells whether a strategy is narrowing (MERGE-2).
   *
   * @param strategy a strategy name
   * @return {@code true} for {@code all}, {@code intersect} or {@code min}
   */
  static boolean isNarrowing(String strategy) {
    return NARROWING.contains(strategy);
  }

  /**
   * Tells whether the type of a key is allowed for its strategy (MERGE-3).
   *
   * @param strategy a strategy name
   * @param type the key's type
   * @return {@code true} when the table of MERGE-3 allows the pair
   */
  static boolean allowsType(String strategy, TypeDecl type) {
    return switch (strategy) {
      case "override" -> true;
      case "deep_merge" -> type.type().equals("object");
      case "all", "any" -> type.type().equals("boolean");
      case "intersect", "union" -> type.type().equals("set");
      default -> type.type().equals("integer");
    };
  }
}
