package io.github.visplabs.tierwise.core.internal.eval;

import java.util.Optional;

/** The eight merge strategies (MERGE-1). */
enum Strategy {
  /** Logical AND of booleans; narrowing. */
  ALL("all", true, true),
  /** Logical OR of booleans. */
  ANY("any", true, false),
  /** Set intersection; narrowing. */
  INTERSECT("intersect", true, true),
  /** Set union. */
  UNION("union", true, false),
  /** Smallest integer; narrowing. */
  MIN("min", true, true),
  /** Largest integer. */
  MAX("max", true, false),
  /** The most specific value replaces the others. */
  OVERRIDE("override", false, false),
  /** Objects merge field by field. */
  DEEP_MERGE("deep_merge", false, false);

  private final String wire;
  private final boolean commutative;
  private final boolean narrowing;

  Strategy(String wire, boolean commutative, boolean narrowing) {
    this.wire = wire;
    this.commutative = commutative;
    this.narrowing = narrowing;
  }

  /**
   * Returns the spelling used in a bundle.
   *
   * @return the {@code combine} value
   */
  String wire() {
    return wire;
  }

  /**
   * Tells whether the result is independent of the order of contributions (MERGE-1).
   *
   * @return {@code true} for the six commutative strategies
   */
  boolean isCommutative() {
    return commutative;
  }

  /**
   * Tells whether the strategy is narrowing (MERGE-2).
   *
   * @return {@code true} for {@code all}, {@code intersect} and {@code min}
   */
  boolean isNarrowing() {
    return narrowing;
  }

  /**
   * Parses the spelling of a bundle.
   *
   * @param text a {@code combine} value
   * @return the strategy, or empty when the text names none
   */
  static Optional<Strategy> parse(String text) {
    for (Strategy candidate : values()) {
      if (candidate.wire.equals(text)) {
        return Optional.of(candidate);
      }
    }
    return Optional.empty();
  }
}
