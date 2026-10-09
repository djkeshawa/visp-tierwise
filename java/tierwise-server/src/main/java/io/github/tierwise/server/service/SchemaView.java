package io.github.tierwise.server.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the answer of {@code GET /v1/schema} from the content of a verified bundle: the hierarchy, entities,
 * facts, keys, features and policies, without values and bindings.
 *
 * <p>The view carries the names and declarations that a client needs to build a context, a FactSet and a
 * request. It adds nothing that is not in the bundle. Its shape is marked (OPEN) in the README, because
 * requirements section 9 only says "keys, features, facts and policies".
 */
final class SchemaView {

  private SchemaView() {
  }

  /**
   * Builds the view.
   *
   * @param bundleVersion the {@code bundleVersion}
   * @param content the parsed {@code content} member of a bundle that passed the loader
   * @return an unmodifiable map, ready to serialise
   */
  static Map<String, Object> build(String bundleVersion, Map<String, Object> content) {
    Map<String, Object> view = new LinkedHashMap<>();
    view.put("bundleVersion", bundleVersion);
    view.put("bundleFormatVersion", content.get("bundleFormatVersion"));
    view.put("specVersion", content.get("specVersion"));
    view.put("hierarchy", content.get("hierarchy"));
    view.put("entities", content.get("entities"));
    view.put("facts", content.get("facts"));
    view.put("keys", content.get("keys"));
    Map<String, Object> features = new LinkedHashMap<>();
    List<Object> policies = new ArrayList<>();
    for (Map.Entry<String, Object> feature : map(content.get("features")).entrySet()) {
      features.put(feature.getKey(), featureSummary(feature.getValue()));
      collectPolicies(feature.getKey(), feature.getValue(), policies);
    }
    view.put("features", features);
    view.put("policies", Collections.unmodifiableList(policies));
    return Collections.unmodifiableMap(view);
  }

  /** The service-sourced levels: the scopes whose {@code values} member is {@code "service"}. */
  static java.util.Set<String> serviceLevels(Map<String, Object> content) {
    java.util.Set<String> levels = new java.util.TreeSet<>();
    Map<String, Object> scopes = map(map(content.get("hierarchy")).get("scopes"));
    for (Map.Entry<String, Object> scope : scopes.entrySet()) {
      if ("service".equals(map(scope.getValue()).get("values"))) {
        levels.add(scope.getKey());
      }
    }
    return Collections.unmodifiableSet(levels);
  }

  private static Map<String, Object> featureSummary(Object feature) {
    Map<String, Object> source = map(feature);
    Map<String, Object> summary = new LinkedHashMap<>();
    for (Map.Entry<String, Object> member : source.entrySet()) {
      String name = member.getKey();
      if (!name.equals("policies") && !name.equals("bindings")) {
        summary.put(name, member.getValue());
      }
    }
    summary.put("policies", new ArrayList<>(map(source.get("policies")).keySet()));
    return summary;
  }

  private static void collectPolicies(String feature, Object declaration, List<Object> into) {
    for (Map.Entry<String, Object> policy : map(map(declaration).get("policies")).entrySet()) {
      Map<String, Object> entry = new LinkedHashMap<>();
      entry.put("feature", feature);
      entry.put("policy", policy.getKey());
      entry.putAll(map(policy.getValue()));
      into.add(entry);
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> map(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
  }
}
