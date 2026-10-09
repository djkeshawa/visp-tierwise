package io.github.tierwise.core.internal.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One non-root level or axis (BUNDLE-33 to BUNDLE-43).
 *
 * @param values where the scope's values come from
 * @param open {@code true} for an open catalog ({@code "nodes": "open"})
 * @param nodes the complete closed catalog in code point order; empty when the catalog is open
 * @param onUnknownNode the admission policy for an unlisted node; present exactly when the catalog is
 *     closed (BUNDLE-42)
 */
public record ScopeDef(
    ValuesSource values, boolean open, List<String> nodes, Optional<OnUnknownNode> onUnknownNode) {

  /** Where the values of a scope come from (BUNDLE-34). */
  public enum ValuesSource {
    /** Values come from source files. */
    FILES("files"),
    /** Values come from a provider at evaluation time; only levels are service-sourced. */
    SERVICE("service");

    private final String wire;

    ValuesSource(String wire) {
      this.wire = wire;
    }

    /**
     * Returns the spelling used in a bundle.
     *
     * @return the bundle spelling
     */
    public String wire() {
      return wire;
    }

    /**
     * Parses the bundle spelling.
     *
     * @param text a bundle spelling
     * @return the constant, or empty when the text is not a spelling
     */
    public static Optional<ValuesSource> parse(String text) {
      for (ValuesSource candidate : values()) {
        if (candidate.wire.equals(text)) {
          return Optional.of(candidate);
        }
      }
      return Optional.empty();
    }
  }

  /** What admission does with a node that a closed catalog does not list (BUNDLE-43). */
  public enum OnUnknownNode {
    /** The node is an error ({@code E_UNKNOWN_NODE}). */
    ERROR("error"),
    /** The scope is skipped and reported in {@code skippedScopes}. */
    IGNORE("ignore");

    private final String wire;

    OnUnknownNode(String wire) {
      this.wire = wire;
    }

    /**
     * Returns the spelling used in a bundle.
     *
     * @return the bundle spelling
     */
    public String wire() {
      return wire;
    }

    /**
     * Parses the bundle spelling.
     *
     * @param text a bundle spelling
     * @return the constant, or empty when the text is not a spelling
     */
    public static Optional<OnUnknownNode> parse(String text) {
      for (OnUnknownNode candidate : values()) {
        if (candidate.wire.equals(text)) {
          return Optional.of(candidate);
        }
      }
      return Optional.empty();
    }
  }

  /** Validates the components and makes the catalog unmodifiable. */
  public ScopeDef {
    Objects.requireNonNull(values, "values");
    Objects.requireNonNull(onUnknownNode, "onUnknownNode");
    nodes = List.copyOf(nodes);
    if (open && !nodes.isEmpty()) {
      throw new IllegalArgumentException("an open catalog lists no nodes");
    }
    if (open == onUnknownNode.isPresent()) {
      throw new IllegalArgumentException("onUnknownNode is present exactly when the catalog is closed");
    }
  }

  /**
   * Creates the definition of an open catalog.
   *
   * @param values where the values come from
   * @return the definition
   */
  public static ScopeDef openCatalog(ValuesSource values) {
    return new ScopeDef(values, true, List.of(), Optional.empty());
  }

  /**
   * Creates the definition of a closed catalog.
   *
   * @param values where the values come from
   * @param nodes the complete catalog
   * @param onUnknownNode the admission policy for an unlisted node
   * @return the definition
   */
  public static ScopeDef closedCatalog(ValuesSource values, List<String> nodes, OnUnknownNode onUnknownNode) {
    return new ScopeDef(values, false, nodes, Optional.of(onUnknownNode));
  }

  /**
   * Tells whether the scope is service-sourced.
   *
   * @return {@code true} when a provider supplies the values
   */
  public boolean isServiceSourced() {
    return values == ValuesSource.SERVICE;
  }
}
