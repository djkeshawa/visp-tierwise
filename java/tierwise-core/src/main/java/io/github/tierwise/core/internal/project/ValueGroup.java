package io.github.tierwise.core.internal.project;

import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.Objects;
import java.util.Optional;

/**
 * The {@code key: value} entries that a values document supplies for one scope and node.
 *
 * <p>The scope and node are NFC-normalised (SRC-24, SCOPE-8, SCOPE-35). Whether the scope is declared,
 * the node is in a closed catalog and the keys are declared is checked by the stages that own those
 * rules (SRC-32, SRC-35).
 *
 * @param scope the scope name; the root level name for root values
 * @param node the node ID, or empty for the root level (SCOPE-13)
 * @param scopeLocation the node where a finding about the scope is located: the scope entry of a
 *     multi-node document, the {@code scope} member of a root entry, or the document root of a
 *     path-mapped file
 * @param nodeLocation the node where a finding about the node ID is located: the node entry of a
 *     multi-node document, otherwise the document root
 * @param entries the {@code key: value} entries
 */
public record ValueGroup(
    String scope, Optional<String> node, PNode scopeLocation, PNode nodeLocation, PMap entries) {

  /** Validates the components. */
  public ValueGroup {
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(node, "node");
    Objects.requireNonNull(scopeLocation, "scopeLocation");
    Objects.requireNonNull(nodeLocation, "nodeLocation");
    Objects.requireNonNull(entries, "entries");
  }
}
