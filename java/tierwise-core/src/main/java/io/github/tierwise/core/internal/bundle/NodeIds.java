package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.ScopeDef;

/**
 * The node keys of {@code values} and {@code bindings} (BUNDLE-66, BUNDLE-129). The root level has one implicit
 * node, keyed by the empty string; every other scope is keyed by a node ID that its catalog admits.
 */
final class NodeIds {

  private static final int MAX_LENGTH = 128;

  private NodeIds() {
  }

  /**
   * Requires that a string is a node ID: non-empty, at most 128 code points, and free of control characters
   * (SCOPE-33, SCOPE-34).
   *
   * @param id the node ID
   * @param at the location of the ID
   */
  static void requireNodeId(String id, String at) {
    if (id.isEmpty()) {
      throw BundleFault.integrity(at + " is an empty node ID (BUNDLE-129)");
    }
    if (id.codePointCount(0, id.length()) > MAX_LENGTH) {
      throw BundleFault.integrity(at + " has more than 128 code points (SCOPE-33)");
    }
    id.codePoints().forEach(cp -> {
      if (cp <= 0x1F || (cp >= 0x7F && cp <= 0x9F)) {
        throw BundleFault.integrity(at + " contains a control character (SCOPE-34)");
      }
    });
  }

  /**
   * Requires that a node key is one that the scope admits: the empty string under the root level only, and
   * for any other scope a node ID that its closed catalog lists, or any node ID when the catalog is open.
   *
   * @param hierarchy the hierarchy
   * @param scope the scope name, already known to be declared
   * @param nodeKey the node key
   * @param at the location of the key
   */
  static void requireAdmitted(Hierarchy hierarchy, String scope, String nodeKey, String at) {
    if (scope.equals(hierarchy.rootLevel())) {
      if (!nodeKey.isEmpty()) {
        throw BundleFault.integrity(at + ": the root level has only the empty node key (BUNDLE-66)");
      }
      return;
    }
    if (nodeKey.isEmpty()) {
      throw BundleFault.integrity(at + ": the empty node key is only for the root level (BUNDLE-129)");
    }
    requireNodeId(nodeKey, at);
    ScopeDef definition = hierarchy.scope(scope);
    if (!definition.open() && !definition.nodes().contains(nodeKey)) {
      throw BundleFault.integrity(at + ": the closed catalog of " + scope + " does not admit " + nodeKey
          + " (BUNDLE-129)");
    }
  }
}
