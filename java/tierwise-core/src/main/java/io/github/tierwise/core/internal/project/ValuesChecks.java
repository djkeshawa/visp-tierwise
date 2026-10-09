package io.github.tierwise.core.internal.project;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Checks the shape of values files and splits their content into {@link ValueGroup}s (SRC-21 to SRC-33).
 *
 * <p>The checks that need to know the root level or the service-sourced levels use a
 * {@link HierarchyPeek}. Without a readable hierarchy they are skipped, and a multi-node document gives
 * no group, because the root-level entry cannot be told from a scope. Whether a scope is declared, a
 * node is in its catalog and a key is declared belongs to the sections that own those rules (SRC-32,
 * SRC-35).
 */
final class ValuesChecks {

  private final Reporter reporter;
  private final HierarchyPeek hierarchy;

  ValuesChecks(Reporter reporter, HierarchyPeek hierarchy) {
    this.reporter = reporter;
    this.hierarchy = hierarchy;
  }

  /**
   * Checks a root entry (SRC-27).
   *
   * @param scope the {@code scope} of the entry, NFC-normalised
   * @param scopeNode the {@code scope} member in the manifest
   * @return {@code true} when the entry may be used
   */
  boolean rootEntryAllowed(String scope, PNode scopeNode) {
    if (hierarchy.rootLevel().isPresent() && !scope.equals(hierarchy.rootLevel().get())
        && hierarchy.declared().contains(scope)) {
      reporter.at(Code.E_SOURCE_MAPPING, scopeNode, "a root entry names the scope " + scope
          + ", which is not the root level (SRC-27)");
      return false;
    }
    return true;
  }

  /**
   * Checks the scope that a path pattern captured (SRC-28).
   *
   * @param scope the captured scope, NFC-normalised
   * @param patternNode the {@code pattern} member in the manifest
   * @return {@code true} when the file may be used
   */
  boolean capturedScopeAllowed(String scope, PNode patternNode) {
    if (hierarchy.rootLevel().filter(scope::equals).isPresent()) {
      reporter.at(Code.E_SOURCE_MAPPING, patternNode,
          "a path pattern captures the root level " + scope + " as the scope (SRC-28)");
      return false;
    }
    return true;
  }

  /**
   * Splits the content of a values document into groups, reporting shape faults (SRC-31, SRC-33).
   *
   * @param root the document root, or the inline {@code values} mapping
   * @param mapping how the document maps to scopes and nodes
   * @return the groups; a group for a faulty or service-sourced scope or node is left out
   */
  List<ValueGroup> groups(PMap root, ValuesMapping mapping) {
    if (mapping instanceof ValuesMapping.PathMapped path) {
      return serviceFault(path.scope(), root)
          ? List.of()
          : List.of(new ValueGroup(path.scope(), Optional.of(path.node()), root, root, root));
    }
    if (mapping instanceof ValuesMapping.RootEntry entry) {
      return List.of(new ValueGroup(entry.scope(), Optional.empty(), entry.scopeNode(), root, root));
    }
    return multiNode(root);
  }

  private boolean serviceFault(String scope, PNode location) {
    if (hierarchy.serviceLevels().contains(scope)) {
      reporter.at(Code.E_SOURCE_MAPPING, location,
          "a values file gives values for the service-sourced level " + scope + " (SRC-33)");
      return true;
    }
    return false;
  }

  private List<ValueGroup> multiNode(PMap root) {
    List<ValueGroup> groups = new ArrayList<>();
    Optional<String> rootLevel = hierarchy.rootLevel();
    for (Map.Entry<String, PNode> scopeEntry : root.entries().entrySet()) {
      String scope = Names.nfc(scopeEntry.getKey());
      PNode scopeNode = scopeEntry.getValue();
      if (serviceFault(scope, scopeNode)) {
        continue;
      }
      if (!(scopeNode instanceof PMap scopeMap)) {
        reporter.at(Code.E_SOURCE_MAPPING, scopeNode,
            "the value of the scope " + scope + " is not a mapping (SRC-31)");
        continue;
      }
      if (rootLevel.isEmpty()) {
        continue;
      }
      if (scope.equals(rootLevel.get())) {
        groups.add(new ValueGroup(scope, Optional.empty(), scopeNode, scopeNode, scopeMap));
      } else {
        nodes(scope, scopeMap, groups);
      }
    }
    return groups;
  }

  private void nodes(String scope, PMap scopeMap, List<ValueGroup> groups) {
    for (Map.Entry<String, PNode> nodeEntry : scopeMap.entries().entrySet()) {
      PNode nodeNode = nodeEntry.getValue();
      if (nodeNode instanceof PMap nodeMap) {
        groups.add(new ValueGroup(scope, Optional.of(Names.nfc(nodeEntry.getKey())), scopeMap, nodeNode, nodeMap));
      } else {
        reporter.at(Code.E_SOURCE_MAPPING, nodeNode, "the value of the node " + nodeEntry.getKey()
            + " is not a mapping of entries (SRC-31)");
      }
    }
  }
}
