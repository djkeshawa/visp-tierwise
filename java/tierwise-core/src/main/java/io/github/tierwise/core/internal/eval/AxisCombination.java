package io.github.tierwise.core.internal.eval;

import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The result of an order-dependent key ({@code override} and {@code deep_merge}) from its default D, its
 * hierarchy result H and its axis result A (AXIS-7), together with the scope that supplied the result
 * (PROV-50 to PROV-53).
 *
 * <p>H is formed from the levels that contribute, A from the axes that contribute, each in
 * evaluation-path order (MERGE-19, MERGE-20, MERGE-23, MERGE-24). Whether H and A are present is decided
 * without D (MERGE-25). All functions are pure.
 */
final class AxisCombination {

  /** A value contributed by one scope. */
  record Contributed(PathScope scope, Object value) {
  }

  /**
   * The outcome: the result and the scope that names its source.
   *
   * @param value the result; never {@code null}
   * @param source the winning scope, or empty when the source is {@code DEFAULT}
   */
  record Outcome(Object value, Optional<PathScope> source) {
  }

  private AxisCombination() {
  }

  /**
   * Forms the result of an {@code override} key (MERGE-19, MERGE-20, MERGE-21, AXIS-7, AXIS-8).
   *
   * @param defaultValue D, or {@code null} when the key declares none
   * @param levels the values of the contributing levels, least specific first
   * @param axes the values of the contributing axes, in declared order
   * @param across the key's {@code combine_across_axes}, when it has one
   * @return the result and its source
   */
  static Outcome override(
      Object defaultValue, List<Contributed> levels, List<Contributed> axes, Optional<String> across) {
    Contributed hierarchy = levels.isEmpty() ? null : levels.get(levels.size() - 1);
    Contributed axis = axes.isEmpty() ? null : axes.get(axes.size() - 1);
    if (hierarchy == null && axis == null) {
      return new Outcome(defaultValue, Optional.empty());
    }
    Contributed winner = hierarchy == null ? axis : axis == null ? hierarchy : both(hierarchy, axis, across);
    return new Outcome(winner.value(), Optional.of(winner.scope()));
  }

  private static Contributed both(Contributed hierarchy, Contributed axis, Optional<String> across) {
    switch (across.orElse("axis_wins")) {
      case "hierarchy_wins":
        return hierarchy;
      case "max":
        return (Long) axis.value() > (Long) hierarchy.value() ? axis : hierarchy;
      case "min":
        return (Long) axis.value() < (Long) hierarchy.value() ? axis : hierarchy;
      default:
        return axis;
    }
  }

  /**
   * Forms the result of a {@code deep_merge} key (MERGE-22 to MERGE-25, AXIS-7, PROV-53).
   *
   * <p>The result is the fold of the present terms of {@code D ⊕ H ⊕ A} ({@code axis_wins}) or
   * {@code D ⊕ A ⊕ H} ({@code hierarchy_wins}). The source is the last present term of the fold, or
   * {@code DEFAULT} when only D is present.
   *
   * @param type the key's {@code object} declaration
   * @param defaultValue D, or {@code null} when the key declares none
   * @param levels the values of the contributing levels, least specific first
   * @param axes the values of the contributing axes, in declared order
   * @param across the key's {@code combine_across_axes}, when it has one
   * @return the result and its source
   */
  static Outcome deepMerge(
      TypeDecl type,
      Object defaultValue,
      List<Contributed> levels,
      List<Contributed> axes,
      Optional<String> across) {
    Term hierarchy = fold(type, levels);
    Term axis = fold(type, axes);
    boolean hierarchyLast = across.orElse("axis_wins").equals("hierarchy_wins");
    Term first = hierarchyLast ? axis : hierarchy;
    Term last = hierarchyLast ? hierarchy : axis;
    Object value = defaultValue;
    Optional<PathScope> source = Optional.empty();
    for (Term term : new Term[] {first, last}) {
      if (term != null) {
        value = value == null ? term.value() : DeepMerge.merge(type, asObject(value), asObject(term.value()));
        source = Optional.of(term.source());
      }
    }
    return new Outcome(value, source);
  }

  /** A folded group of values and the scope that names the group: its last contributing scope. */
  private record Term(Object value, PathScope source) {
  }

  private static Term fold(TypeDecl type, List<Contributed> contributions) {
    if (contributions.isEmpty()) {
      return null;
    }
    Object value = contributions.get(0).value();
    for (Contributed next : contributions.subList(1, contributions.size())) {
      value = DeepMerge.merge(type, asObject(value), asObject(next.value()));
    }
    return new Term(value, contributions.get(contributions.size() - 1).scope());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asObject(Object value) {
    return (Map<String, Object>) value;
  }
}
