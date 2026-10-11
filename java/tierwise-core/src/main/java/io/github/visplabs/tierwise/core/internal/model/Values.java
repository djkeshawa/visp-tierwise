package io.github.visplabs.tierwise.core.internal.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The authored values of a bundle (BUNDLE-64 to BUNDLE-67): scope name, then node key, then key name,
 * then the value. The root level's node key is the empty string (BUNDLE-66). A service-sourced level
 * has no entry (BUNDLE-67).
 *
 * @param byScope the values by scope name, node key and key name
 */
public record Values(Map<String, Map<String, Map<String, Object>>> byScope) {

  /** An empty values map. */
  public static final Values EMPTY = new Values(Map.of());

  /** Copies the three levels of maps deeply into unmodifiable maps. */
  public Values {
    Map<String, Map<String, Map<String, Object>>> scopes = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, Map<String, Object>>> scope : byScope.entrySet()) {
      Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
      for (Map.Entry<String, Map<String, Object>> node : scope.getValue().entrySet()) {
        nodes.put(node.getKey(), Plain.copyMap(node.getValue()));
      }
      scopes.put(scope.getKey(), Collections.unmodifiableMap(nodes));
    }
    byScope = Collections.unmodifiableMap(scopes);
  }

  /**
   * Returns the value that a node holds for a key.
   *
   * @param scope a scope name
   * @param nodeKey the node ID, or the empty string for the root level
   * @param key a key name
   * @return the value, or {@code null} when the node holds none
   */
  public Object get(String scope, String nodeKey, String key) {
    Map<String, Map<String, Object>> nodes = byScope.get(scope);
    if (nodes == null) {
      return null;
    }
    Map<String, Object> entries = nodes.get(nodeKey);
    return entries == null ? null : entries.get(key);
  }
}
