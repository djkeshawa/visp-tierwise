package io.github.tierwise.core.internal.project;

import io.github.tierwise.core.internal.yaml.PNode;

/**
 * How a values document maps to scopes and nodes (SRC-21 to SRC-31). The content of a path-mapped
 * document and of a root document is a set of {@code key: value} entries. The content of a multi-node
 * document is a mapping from scope to nodes.
 */
public sealed interface ValuesMapping {

  /**
   * A path-mapped values file (SRC-22): scope and node come from the file path.
   *
   * @param scope the captured scope name, NFC-normalised (SRC-24)
   * @param node the captured node ID, NFC-normalised (SRC-24)
   */
  record PathMapped(String scope, String node) implements ValuesMapping {
  }

  /**
   * A root values file (SRC-26): the entries are for the root level.
   *
   * @param scope the {@code scope} member of the manifest entry, NFC-normalised
   * @param scopeNode the {@code scope} member in the manifest, where a finding about it is located
   */
  record RootEntry(String scope, PNode scopeNode) implements ValuesMapping {
  }

  /** A multi-node values file or the inline {@code values} content (SRC-29, SRC-30). */
  record MultiNode() implements ValuesMapping {
  }
}
