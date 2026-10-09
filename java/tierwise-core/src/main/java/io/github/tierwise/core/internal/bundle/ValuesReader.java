package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.KeyDef;
import io.github.tierwise.core.internal.model.Values;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads {@code values} (BUNDLE-22, BUNDLE-64 to BUNDLE-67, BUNDLE-129 to BUNDLE-131) and the starting-value rules
 * that depend on it (MERGE-7, MERGE-10, MERGE-11, MERGE-12).
 */
final class ValuesReader {

  private ValuesReader() {
  }

  /**
   * Reads the authored values and checks each against its key.
   *
   * @param value the {@code values} member
   * @param hierarchy the hierarchy
   * @param keys the declared keys
   * @return the values
   */
  static Values read(Object value, Hierarchy hierarchy, Map<String, KeyDef> keys) {
    Map<String, Object> scopes = Members.object(value, "content.values");
    Map<String, Map<String, Map<String, Object>>> byScope = new LinkedHashMap<>();
    for (Map.Entry<String, Object> scopeEntry : scopes.entrySet()) {
      String scope = scopeEntry.getKey();
      String at = "content.values." + scope;
      if (!hierarchy.isLevel(scope) && !hierarchy.isAxis(scope)) {
        throw BundleFault.integrity(at + " names an undeclared scope (BUNDLE-127)");
      }
      if (!scope.equals(hierarchy.rootLevel()) && hierarchy.scope(scope).isServiceSourced()) {
        throw BundleFault.integrity(at + " holds values for a service-sourced level (BUNDLE-67)");
      }
      byScope.put(scope, readScope(scope, scopeEntry.getValue(), at, hierarchy, keys));
    }
    return new Values(byScope);
  }

  private static Map<String, Map<String, Object>> readScope(String scope, Object value, String at,
      Hierarchy hierarchy, Map<String, KeyDef> keys) {
    Map<String, Object> nodes = Members.object(value, at);
    if (nodes.isEmpty()) {
      throw BundleFault.integrity(at + " has no node entry, so it must be absent (BUNDLE-22)");
    }
    Map<String, Map<String, Object>> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> nodeEntry : nodes.entrySet()) {
      String nodeAt = at + "." + nodeEntry.getKey();
      NodeIds.requireAdmitted(hierarchy, scope, nodeEntry.getKey(), nodeAt);
      out.put(nodeEntry.getKey(), readNode(scope, nodeEntry.getKey(), nodeEntry.getValue(), nodeAt, hierarchy, keys));
    }
    return out;
  }

  private static Map<String, Object> readNode(String scope, String node, Object value, String at,
      Hierarchy hierarchy, Map<String, KeyDef> keys) {
    Map<String, Object> entries = Members.object(value, at);
    if (entries.isEmpty()) {
      throw BundleFault.integrity(at + " has no key value, so it must be absent (BUNDLE-22)");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : entries.entrySet()) {
      String keyAt = at + "." + entry.getKey();
      KeyDef key = keys.get(entry.getKey());
      if (key == null) {
        throw BundleFault.integrity(keyAt + " names an undeclared key (BUNDLE-130)");
      }
      if (!key.settableAt().contains(scope)) {
        throw BundleFault.integrity(keyAt + " is set at " + scope + ", where the key is not settable (BUNDLE-130)");
      }
      ValueChecker.Mode mode = mode(key, scope, node, hierarchy);
      ValueChecker.check(key.type(), entry.getValue(), mode, keyAt);
      out.put(entry.getKey(), entry.getValue());
    }
    return out;
  }

  /**
   * Gives the check mode of one value: a {@code deep_merge} value is partial, except the starting value, which is
   * the default when one is declared and the root value otherwise (MERGE-10 to MERGE-12).
   */
  private static ValueChecker.Mode mode(KeyDef key, String scope, String node, Hierarchy hierarchy) {
    if (!"deep_merge".equals(key.combine())) {
      return ValueChecker.Mode.COMPLETE;
    }
    if (key.defaultValue().isPresent()) {
      return ValueChecker.Mode.PARTIAL;
    }
    boolean root = scope.equals(hierarchy.rootLevel()) && node.isEmpty();
    return root ? ValueChecker.Mode.COMPLETE : ValueChecker.Mode.PARTIAL;
  }

  /**
   * Requires a root value for each order-dependent key that declares no default (MERGE-7).
   *
   * @param values the values
   * @param keys the declared keys
   * @param hierarchy the hierarchy
   */
  static void requireStartingValues(Values values, Map<String, KeyDef> keys, Hierarchy hierarchy) {
    for (Map.Entry<String, KeyDef> entry : keys.entrySet()) {
      KeyDef key = entry.getValue();
      if (KeyReader.ORDER_DEPENDENT.contains(key.combine()) && key.defaultValue().isEmpty()
          && values.get(hierarchy.rootLevel(), "", entry.getKey()) == null) {
        throw BundleFault.integrity("content.keys." + entry.getKey()
            + " is order-dependent, so it needs a default or a root value (MERGE-7)");
      }
    }
  }
}
