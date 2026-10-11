package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.model.ScopeDef;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import io.github.visplabs.tierwise.core.internal.model.Values;
import io.github.visplabs.tierwise.core.internal.project.NameSuggester;
import io.github.visplabs.tierwise.core.internal.project.SourceDocument;
import io.github.visplabs.tierwise.core.internal.project.ValueGroup;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks the authored values of the values documents (KEY-10 to KEY-13, MERGE-10 to MERGE-12, MERGE-26,
 * KIND-6, KIND-7, SCOPE-33, SCOPE-34, SCOPE-37 to SCOPE-39, SCOPE-43 to SCOPE-46, SRC-32, SRC-35, SRC-42).
 *
 * <p>A group (one scope and node of one document) is checked in this order: the scope, then the node ID
 * and its catalog, then each entry. An undeclared scope gives one finding and the group is not checked
 * further. A node ID that breaks the structural contract is {@code E_INVALID_NODE_ID} and is not also
 * checked for membership (SCOPE-39). The entries of a group with a bad node are still checked, because
 * their faults are independent. A key whose type or strategy is invalid receives no value checks
 * (FIND-35), and a key that is not declared at all is {@code E_UNKNOWN_KEY}. Checks that need the
 * hierarchy, the keys or the kinds are skipped when that input is absent.
 *
 * <p>The values of {@code deep_merge} keys are validated as partial objects (MERGE-11, MERGE-12), except
 * the starting value, which is the root value of a key that has no default (MERGE-10). MERGE-26 is
 * checked after all values are read, because it needs the root values.
 */
final class ValueChecks {

  private final Findings findings;
  private final ValueValidator validator;
  private final ValueMerge merge;
  private final Optional<Hierarchy> hierarchy;
  private final Optional<KeyTable> keys;
  private final Optional<KindTable> kinds;

  private final Set<List<String>> seen = new HashSet<>();
  private final Map<String, Map<String, Map<String, Object>>> model = new LinkedHashMap<>();
  private final Map<String, Object> rootValues = new HashMap<>();
  private final List<Pending> pending = new ArrayList<>();
  private boolean fault;

  /** A partial {@code deep_merge} value that waits for the check of MERGE-26. */
  private record Pending(KeyInfo info, PNode node, Object value) {
  }

  ValueChecks(Findings findings, Optional<Hierarchy> hierarchy, Optional<KeyTable> keys,
      Optional<KindTable> kinds) {
    this.findings = findings;
    this.validator = new ValueValidator(findings);
    this.merge = new ValueMerge(validator);
    this.hierarchy = hierarchy;
    this.keys = keys;
    this.kinds = kinds;
  }

  /**
   * Checks every values document.
   *
   * @param documents the values documents in definition order (FIND-32)
   * @return the normalised values for the model, or empty when a fault was reported or an input needed
   *     to check the values is absent
   */
  Optional<Values> run(List<SourceDocument> documents) {
    for (SourceDocument document : documents) {
      for (ValueGroup group : document.groups()) {
        group(group);
      }
    }
    for (Pending value : pending) {
      startingCheck(value);
    }
    if (fault || hierarchy.isEmpty() || keys.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new Values(model));
  }

  // ---- scope and node ----------------------------------------------------------------------------

  private void group(ValueGroup group) {
    String scope = group.scope();
    String nodeKey = group.node().orElse("");
    if (hierarchy.isPresent()) {
      Hierarchy h = hierarchy.get();
      if (!h.isLevel(scope) && !h.isAxis(scope)) {
        findings.error(Code.E_UNKNOWN_SCOPE, group.scopeLocation(),
            scope + " is not a declared level or axis (SCOPE-46, SRC-32)",
            NameSuggester.closest(scope, h.scopeOrder()));
        fault = true;
        return;
      }
      ScopeDef def = h.scope(scope);
      if (def != null && def.isServiceSourced()) {
        return;
      }
      if (def == null) {
        nodeKey = "";
      } else if (group.node().isEmpty()) {
        return;
      } else {
        node(group, def);
      }
    }
    for (Map.Entry<String, PNode> entry : group.entries().entries().entrySet()) {
      entry(scope, nodeKey, entry.getKey(), entry.getValue());
    }
  }

  /** Checks the node ID of a group against the structural contract and the catalog (SCOPE-38 to SCOPE-45). */
  private void node(ValueGroup group, ScopeDef def) {
    String node = group.node().orElseThrow();
    if (!HierarchyNodeId.isValid(node)) {
      findings.error(Code.E_INVALID_NODE_ID, group.nodeLocation(),
          "a node ID is 1 to 128 code points without a control character (SCOPE-33, SCOPE-34, SCOPE-38)");
      fault = true;
    } else if (!def.open() && !def.nodes().contains(node)) {
      findings.error(Code.E_UNKNOWN_NODE, group.nodeLocation(),
          node + " is not in the closed catalog of " + group.scope() + " (SCOPE-43, SCOPE-44)",
          NameSuggester.closest(node, def.nodes()));
      fault = true;
    }
  }

  // ---- entries -----------------------------------------------------------------------------------

  private void entry(String scope, String nodeKey, String name, PNode node) {
    boolean duplicate = !seen.add(List.of(scope, nodeKey, name));
    if (duplicate) {
      findings.error(Code.E_DUPLICATE_VALUE, node,
          "the key " + name + " has a second value at the same scope and node (KEY-12, SRC-42)");
      fault = true;
    }
    if (keys.isEmpty()) {
      return;
    }
    KeyInfo info = keys.get().keys().get(name);
    if (info == null) {
      findings.error(Code.E_UNKNOWN_KEY, node, "the key " + name + " is not declared (KEY-10, SRC-35)",
          NameSuggester.closest(name, keys.get().keys().keySet()));
      fault = true;
      return;
    }
    if (!info.checkable()) {
      return;
    }
    if (hierarchy.isPresent() && info.settableKnown() && !info.settableAt().contains(scope)) {
      findings.error(Code.E_NOT_SETTABLE_HERE, node,
          "the key " + name + " is not settable at " + scope + " (KEY-11)");
      fault = true;
    }
    boolean root = hierarchy.isPresent() && scope.equals(hierarchy.get().rootLevel());
    boolean partial = info.combine().orElseThrow().equals("deep_merge") && !(root && !info.declaresDefault());
    ValueRules rules = partial ? ValueRules.DEFAULT.partial() : ValueRules.DEFAULT;
    TypeDecl type = info.type().orElseThrow();
    Optional<Object> value = validator.validate(type, node, rules);
    if (value.isEmpty()) {
      fault = true;
      return;
    }
    if (violatesNarrowing(info, type, value.get())) {
      findings.error(Code.E_KIND_VIOLATION, node,
          "an authored value of the key " + name + " equals the identity of its strategy (KIND-6)");
      fault = true;
    }
    if (duplicate) {
      return;
    }
    if (root) {
      rootValues.put(name, value.get());
    }
    if (partial) {
      pending.add(new Pending(info, node, value.get()));
    }
    model.computeIfAbsent(scope, s -> new LinkedHashMap<>()).computeIfAbsent(nodeKey, n -> new LinkedHashMap<>())
        .put(name, value.get());
  }

  /** Tells whether an authored value equals the identity of a narrowing-only kind's strategy (KIND-6). */
  private boolean violatesNarrowing(KeyInfo info, TypeDecl type, Object value) {
    if (info.kind().isEmpty() || kinds.isEmpty()) {
      return false;
    }
    KindDef kind = kinds.get().effective().get(info.kind().get());
    if (kind == null || !kind.narrowingOnly()) {
      return false;
    }
    return switch (info.combine().orElseThrow()) {
      case "all" -> Boolean.TRUE.equals(value);
      case "intersect" -> value instanceof List<?> members && members.equals(type.universe());
      default -> false;
    };
  }

  // ---- MERGE-26 ----------------------------------------------------------------------------------

  /** Checks a partial {@code deep_merge} value against the starting value (MERGE-26). */
  private void startingCheck(Pending value) {
    Object start = value.info().defaultValue().isPresent() ? value.info().defaultValue().get()
        : rootValues.get(value.info().name());
    if (start instanceof Map<?, ?> startMap && value.value() instanceof Map<?, ?> given
        && !merge.check(value.info().type().orElseThrow(), value.node(), given, startMap)) {
      fault = true;
    }
  }
}
