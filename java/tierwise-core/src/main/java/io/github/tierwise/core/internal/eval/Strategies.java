package io.github.tierwise.core.internal.eval;

import io.github.tierwise.core.Resolved.Contribution.Label;
import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The commutative merge strategies as pure functions (MERGE-5, MERGE-17, MERGE-18), with their
 * identities, their deny values (SVC-9) and the contribution labels of their value entries (PROV-38).
 *
 * <p>Every function takes plain values in normal form: booleans, {@code Long} integers and sets as lists
 * of strings. None of them modifies its arguments, and the order of the contributed values never
 * changes a result. Set results list their members in the declaration order of {@code universe}
 * (TYPE-51). The order-dependent strategies are in {@link AxisCombination}.
 */
final class Strategies {

  private Strategies() {
  }

  /**
   * Returns the identity of a commutative strategy: the result when no scope contributes (MERGE-5).
   *
   * @param strategy a commutative strategy
   * @param type the key's type declaration
   * @return {@code true} for {@code all}, {@code false} for {@code any}, the full universe for
   *     {@code intersect}, the empty set for {@code union}, and {@code null} (absent) for {@code min}
   *     and {@code max}
   */
  static Object identity(Strategy strategy, TypeDecl type) {
    switch (strategy) {
      case ALL:
        return Boolean.TRUE;
      case ANY:
        return Boolean.FALSE;
      case INTERSECT:
        return List.copyOf(type.universe());
      case UNION:
        return List.of();
      case MIN:
      case MAX:
        return null;
      default:
        throw new IllegalArgumentException(strategy + " is order-dependent and has no identity");
    }
  }

  /**
   * Combines contributed values with the operation of a commutative strategy, starting from its
   * identity (MERGE-17).
   *
   * @param strategy a commutative strategy
   * @param type the key's type declaration
   * @param values the contributed values, in any order
   * @return the result, or {@code null} when the strategy is {@code min} or {@code max} and no value
   *     contributes
   */
  static Object combine(Strategy strategy, TypeDecl type, List<Object> values) {
    switch (strategy) {
      case ALL:
        return all(values);
      case ANY:
        return any(values);
      case INTERSECT:
        return intersect(type, values);
      case UNION:
        return union(type, values);
      case MIN:
        return min(values);
      case MAX:
        return max(values);
      default:
        throw new IllegalArgumentException(strategy + " is order-dependent");
    }
  }

  /**
   * Logical AND (identity {@code true}).
   *
   * @param values booleans
   * @return {@code true} unless a value is {@code false}
   */
  static Boolean all(List<Object> values) {
    for (Object value : values) {
      if (!((Boolean) value)) {
        return Boolean.FALSE;
      }
    }
    return Boolean.TRUE;
  }

  /**
   * Logical OR (identity {@code false}).
   *
   * @param values booleans
   * @return {@code true} if any value is {@code true}
   */
  static Boolean any(List<Object> values) {
    for (Object value : values) {
      if ((Boolean) value) {
        return Boolean.TRUE;
      }
    }
    return Boolean.FALSE;
  }

  /**
   * Set intersection, starting from the full universe.
   *
   * @param type the {@code set} declaration
   * @param values sets as lists of strings
   * @return the members common to every value, in universe order
   */
  static List<Object> intersect(TypeDecl type, List<Object> values) {
    Set<Object> kept = new HashSet<>(type.universe());
    for (Object value : values) {
      kept.retainAll(new HashSet<>(members(value)));
    }
    return inUniverseOrder(type, kept);
  }

  /**
   * Set union, starting from the empty set.
   *
   * @param type the {@code set} declaration
   * @param values sets as lists of strings
   * @return the members of any value, in universe order
   */
  static List<Object> union(TypeDecl type, List<Object> values) {
    Set<Object> joined = new HashSet<>();
    for (Object value : values) {
      joined.addAll(members(value));
    }
    return inUniverseOrder(type, joined);
  }

  /**
   * The smaller of the contributed integers.
   *
   * @param values {@code Long} values
   * @return the smallest value, or {@code null} when there is none
   */
  static Long min(List<Object> values) {
    Long best = null;
    for (Object value : values) {
      long candidate = (Long) value;
      best = best == null || candidate < best ? candidate : best;
    }
    return best;
  }

  /**
   * The larger of the contributed integers.
   *
   * @param values {@code Long} values
   * @return the largest value, or {@code null} when there is none
   */
  static Long max(List<Object> values) {
    Long best = null;
    for (Object value : values) {
      long candidate = (Long) value;
      best = best == null || candidate > best ? candidate : best;
    }
    return best;
  }

  /**
   * Returns the deny value of a strategy: what a level contributes when its source fails under
   * {@code deny} (SVC-9).
   *
   * @param strategy the key's strategy
   * @param type the key's type declaration
   * @return {@code false} for {@code all}, the empty set for {@code intersect}, the {@code min} bound of
   *     the integer type for {@code min}; empty for every other strategy, which cannot carry a deny
   *     value (SVC-11, SVC-12)
   */
  static Optional<Object> denyValue(Strategy strategy, TypeDecl type) {
    switch (strategy) {
      case ALL:
        return Optional.of(Boolean.FALSE);
      case INTERSECT:
        return Optional.of(List.of());
      case MIN:
        return type.min().map(bound -> (Object) bound);
      default:
        return Optional.empty();
    }
  }

  /**
   * Returns the contribution label of a value entry (PROV-37, PROV-38). The label depends on the value
   * and the strategy alone.
   *
   * @param strategy the key's strategy
   * @param type the key's type declaration
   * @param value the value that the scope contributes
   * @return the label
   */
  static Label label(Strategy strategy, TypeDecl type, Object value) {
    switch (strategy) {
      case ALL:
        return (Boolean) value ? Label.IGNORED : Label.VETO;
      case ANY:
        return (Boolean) value ? Label.WIDENED : Label.IGNORED;
      case INTERSECT:
        return members(value).size() >= type.universe().size() ? Label.IGNORED : Label.NARROWED;
      case UNION:
        return members(value).isEmpty() ? Label.IGNORED : Label.WIDENED;
      case MIN:
        return Label.NARROWED;
      case MAX:
        return Label.WIDENED;
      default:
        return Label.SET;
    }
  }

  private static List<?> members(Object value) {
    return (List<?>) value;
  }

  private static List<Object> inUniverseOrder(TypeDecl type, Set<Object> chosen) {
    List<Object> ordered = new ArrayList<>();
    for (String member : type.universe()) {
      if (chosen.contains(member)) {
        ordered.add(member);
      }
    }
    return Collections.unmodifiableList(ordered);
  }
}
