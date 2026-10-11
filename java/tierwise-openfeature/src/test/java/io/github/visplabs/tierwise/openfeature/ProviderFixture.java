package io.github.visplabs.tierwise.openfeature;

import dev.openfeature.sdk.Client;
import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.ImmutableContext;
import dev.openfeature.sdk.OpenFeatureAPI;
import dev.openfeature.sdk.Value;
import java.util.HashMap;
import java.util.Map;

/** Helpers shared by the provider tests: contexts built from plain maps and a client over an isolated API. */
final class ProviderFixture {

  private ProviderFixture() {
  }

  /** A client over a new isolated OpenFeature API with the given provider. */
  static Client client(FeatureProvider provider) {
    OpenFeatureAPI api = OpenFeatureAPI.createIsolated();
    try {
      api.setProviderAndWait(provider);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    return api.getClient();
  }

  /** A context whose attributes are converted from plain maps, lists, strings, numbers and booleans. */
  static EvaluationContext context(Map<String, Object> attributes) {
    Map<String, Value> converted = new HashMap<>();
    for (Map.Entry<String, Object> entry : attributes.entrySet()) {
      converted.put(entry.getKey(), Value.objectToValue(entry.getValue()));
    }
    return new ImmutableContext(converted);
  }

  /** A context with {@code tierwise.levels}. */
  static EvaluationContext levels(String... namesAndNodes) {
    return context(Map.of(TierwiseProvider.LEVELS_ATTRIBUTE, pairs((Object[]) namesAndNodes)));
  }

  /** A context with {@code tierwise.levels}, {@code tierwise.axes} and {@code tierwise.facts}. */
  static EvaluationContext of(Map<String, Object> levels, Map<String, Object> axes, Map<String, Object> facts) {
    Map<String, Object> attributes = new HashMap<>();
    if (levels != null) {
      attributes.put(TierwiseProvider.LEVELS_ATTRIBUTE, levels);
    }
    if (axes != null) {
      attributes.put(TierwiseProvider.AXES_ATTRIBUTE, axes);
    }
    if (facts != null) {
      attributes.put(TierwiseProvider.FACTS_ATTRIBUTE, facts);
    }
    return context(attributes);
  }

  /** A context with {@code tierwise.facts} only. */
  static EvaluationContext facts(Map<String, Object> facts) {
    return of(null, null, facts);
  }

  /** Builds a map from alternating names and values. */
  static Map<String, Object> pairs(Object... namesAndValues) {
    Map<String, Object> map = new java.util.LinkedHashMap<>();
    for (int i = 0; i < namesAndValues.length; i += 2) {
      map.put((String) namesAndValues[i], namesAndValues[i + 1]);
    }
    return map;
  }
}
