package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.ScopeDef;
import io.github.tierwise.core.internal.project.NameSuggester;
import io.github.tierwise.core.internal.project.Names;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.List;
import java.util.Optional;

/**
 * The scope and node lookup of a built hierarchy, which the later stages call for every scope and node that a
 * source writes: the values (SRC-32), the bindings (BIND-10, BIND-11), the key and feature settings (KEY-6,
 * FEAT-17).
 *
 * <p>It is published by {@link HierarchyStage} through {@link CheckContext#scopes()}, so it exists only when
 * the hierarchy was built. A caller that finds no lookup skips the checks that need it (FIND-35). The helpers
 * report the engine errors that the clauses name, located at the node that the caller passes:
 * {@code E_UNKNOWN_SCOPE} (SCOPE-46), {@code E_INVALID_NODE_ID} (SCOPE-38) and {@code E_UNKNOWN_NODE} (SCOPE-43,
 * SCOPE-44). Names are NFC-normalised before lookup (SCOPE-8, SCOPE-35).
 */
public final class ScopeIndex {

  /** The outcome of checking one node ID written in a source. */
  public enum NodeStatus {
    /** The node is admitted: it is a member of a closed catalog, or the catalog is open (SCOPE-45). */
    ADMITTED,
    /** The node ID breaks the form rules, and {@code E_INVALID_NODE_ID} was reported (SCOPE-39). */
    INVALID,
    /** The node is not a member of its closed catalog, and {@code E_UNKNOWN_NODE} was reported (SCOPE-44). */
    UNKNOWN,
    /** The scope has no node IDs (the root level), or it is not a declared scope, so nothing is checked. */
    NOT_APPLICABLE
  }

  private final Hierarchy hierarchy;

  /**
   * Creates the lookup of a built hierarchy.
   *
   * @param hierarchy the hierarchy
   */
  public ScopeIndex(Hierarchy hierarchy) {
    this.hierarchy = hierarchy;
  }

  /**
   * Returns the hierarchy.
   *
   * @return the hierarchy
   */
  public Hierarchy hierarchy() {
    return hierarchy;
  }

  /**
   * Returns the root level's name.
   *
   * @return the name
   */
  public String rootLevel() {
    return hierarchy.rootLevel();
  }

  /**
   * Tells whether a name is a declared level, the root level included, or a declared axis.
   *
   * @param name the name as written
   * @return {@code true} when the NFC form of the name is declared
   */
  public boolean isDeclared(String name) {
    String scope = Names.nfc(name);
    return hierarchy.isLevel(scope) || hierarchy.isAxis(scope);
  }

  /**
   * Returns the definition of a non-root scope.
   *
   * @param name the name as written
   * @return the definition, or empty for the root level or an undeclared name
   */
  public Optional<ScopeDef> definition(String name) {
    return Optional.ofNullable(hierarchy.scope(Names.nfc(name)));
  }

  /**
   * Resolves a scope name that a source writes (SCOPE-46). The root level, the levels and the axes are declared.
   *
   * @param findings the accumulator
   * @param location the node that writes the name
   * @param written the name as written
   * @return the NFC form when the name is declared, otherwise empty after {@code E_UNKNOWN_SCOPE} is reported
   */
  public Optional<String> resolveScope(Findings findings, PNode location, String written) {
    String scope = Names.nfc(written);
    if (isDeclared(scope)) {
      return Optional.of(scope);
    }
    List<String> declared = hierarchy.scopeOrder();
    findings.error(Code.E_UNKNOWN_SCOPE, location,
        "the scope " + written + " is not a declared level or axis (SCOPE-46)", NameSuggester.closest(scope, declared));
    return Optional.empty();
  }

  /**
   * Checks a node ID that a source writes for a scope (SCOPE-38, SCOPE-39, SCOPE-43 to SCOPE-45).
   *
   * <p>An invalid ID is {@code E_INVALID_NODE_ID} and is not checked for membership (SCOPE-39). A valid ID in a
   * closed catalog that does not list it is {@code E_UNKNOWN_NODE}, whatever the {@code on_unknown_node} setting
   * (SCOPE-44). An open catalog admits any valid ID (SCOPE-45).
   *
   * @param findings the accumulator
   * @param location the node that writes the ID
   * @param scope the scope, already resolved by {@link #resolveScope}
   * @param written the node ID as written
   * @return the outcome
   */
  public NodeStatus checkNode(Findings findings, PNode location, String scope, String written) {
    Optional<ScopeDef> definition = definition(scope);
    if (definition.isEmpty()) {
      return NodeStatus.NOT_APPLICABLE;
    }
    Optional<String> id = HierarchyNodeId.admissible(written);
    if (id.isEmpty()) {
      findings.error(Code.E_INVALID_NODE_ID, location,
          "the node ID breaks the form rules of SCOPE-33 to SCOPE-37 (SCOPE-38)");
      return NodeStatus.INVALID;
    }
    ScopeDef scopeDef = definition.get();
    if (scopeDef.open() || scopeDef.nodes().contains(id.get())) {
      return NodeStatus.ADMITTED;
    }
    findings.error(Code.E_UNKNOWN_NODE, location, "the node " + id.get() + " is not in the closed catalog of "
        + Names.nfc(scope) + " (SCOPE-43, SCOPE-44)", NameSuggester.closest(id.get(), scopeDef.nodes()));
    return NodeStatus.UNKNOWN;
  }
}
