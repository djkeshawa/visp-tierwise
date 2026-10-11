package io.github.visplabs.tierwise.openfeature;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.Value;
import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.FactSet;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the Tierwise context and the FactSet from an OpenFeature evaluation context. The attribute values
 * are converted to plain data and handed to {@link Context#fromJson(Object)} and
 * {@link FactSet#fromJson(Object)}, so the core alone judges their shape and the evaluator raises the
 * matching code ({@code E_CONTEXT_INVALID} or {@code E_FACTS_INVALID}) for a malformed one.
 */
final class ContextMapper {

  private ContextMapper() {
  }

  /** Builds the Tierwise context from {@code tierwise.levels} and {@code tierwise.axes}. */
  static Context context(EvaluationContext source) {
    if (source == null) {
      return Context.empty();
    }
    Map<String, Object> raw = new LinkedHashMap<>();
    copy(source, TierwiseProvider.LEVELS_ATTRIBUTE, "levels", raw);
    copy(source, TierwiseProvider.AXES_ATTRIBUTE, "axes", raw);
    return raw.isEmpty() ? Context.empty() : Context.fromJson(raw);
  }

  /** Builds the FactSet from {@code tierwise.facts}; an absent attribute is the empty FactSet. */
  static FactSet facts(EvaluationContext source) {
    Value value = source == null ? null : source.getValue(TierwiseProvider.FACTS_ATTRIBUTE);
    return value == null ? FactSet.empty() : FactSet.fromJson(PlainValues.fromValue(value));
  }

  private static void copy(EvaluationContext source, String attribute, String member, Map<String, Object> into) {
    Value value = source.getValue(attribute);
    if (value != null) {
      into.put(member, PlainValues.fromValue(value));
    }
  }
}
