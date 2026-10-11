package io.github.visplabs.tierwise.core.internal.eval;

import io.github.visplabs.tierwise.core.ScopeKind;
import java.util.Objects;
import java.util.Optional;

/**
 * One scope on the evaluation path (CTX-19): the root level, or an admitted level or axis with the node
 * that the context supplied for it.
 *
 * @param name the declared scope name
 * @param kind whether it is a level or an axis
 * @param node the node ID as the context supplied it; {@code null} for the root level
 */
record PathScope(String name, ScopeKind kind, String node) {

  PathScope {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(kind, "kind");
  }

  /**
   * Tells whether this is the root level, the only path scope without a node.
   *
   * @return {@code true} for the root level
   */
  boolean isRoot() {
    return node == null;
  }

  /**
   * Returns the node as an optional.
   *
   * @return the node ID, or empty for the root level
   */
  Optional<String> optionalNode() {
    return Optional.ofNullable(node);
  }

  /**
   * Returns the key under which the values and bindings of this scope's node are stored.
   *
   * @return the node ID, or the empty string for the root level (BUNDLE-66)
   */
  String nodeKey() {
    return node == null ? "" : node;
  }
}
