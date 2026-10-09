package io.github.tierwise.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A scope that admission skipped because its node was unlisted under {@code on_unknown_node: ignore}
 * (PROV-54 to PROV-61). The list of skipped scopes is a property of the context, so it is the same in
 * every result computed from one context (PROV-60).
 *
 * @param scope whether the scope is a level or an axis
 * @param name the declared scope name
 * @param node the unknown node ID that the context supplied
 */
public record SkippedScope(ScopeKind scope, String name, String node) {

  /** The only reason in this version (PROV-55). */
  public static final String REASON = "UNKNOWN_NODE";

  /** Validates the components. */
  public SkippedScope {
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(node, "node");
  }

  /**
   * Returns the JSON form of section 9.5.
   *
   * @return an unmodifiable object {@code { scope, name, node, reason }}
   */
  public Map<String, Object> toJson() {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("scope", scope.json());
    json.put("name", name);
    json.put("node", node);
    json.put("reason", REASON);
    return java.util.Collections.unmodifiableMap(json);
  }
}
