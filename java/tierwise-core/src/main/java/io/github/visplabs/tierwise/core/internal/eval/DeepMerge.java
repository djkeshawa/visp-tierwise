package io.github.visplabs.tierwise.core.internal.eval;

import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The merge of two objects (MERGE-22). */
final class DeepMerge {

  private DeepMerge() {
  }

  /**
   * Merges an overriding object over a base object.
   *
   * <p>Each field of the overriding object is set in a copy of the base. If the field is declared with
   * type {@code object} and the base has a value for it, the two field values are merged in turn.
   * Otherwise the field value replaces the earlier one as a whole; this includes lists and sets. A merge
   * never removes a field.
   *
   * @param type the {@code object} declaration of both values
   * @param base the base object
   * @param over the overriding object, possibly partial
   * @return a new unmodifiable object
   */
  static Map<String, Object> merge(TypeDecl type, Map<String, Object> base, Map<String, Object> over) {
    Map<String, Object> merged = new LinkedHashMap<>(base);
    for (Map.Entry<String, Object> field : over.entrySet()) {
      TypeDecl declared = type.fields().get(field.getKey());
      Object earlier = merged.get(field.getKey());
      if (declared != null && declared.type().equals("object") && isObject(earlier) && isObject(field.getValue())) {
        merged.put(field.getKey(), merge(declared, asObject(earlier), asObject(field.getValue())));
      } else {
        merged.put(field.getKey(), field.getValue());
      }
    }
    return Collections.unmodifiableMap(merged);
  }

  private static boolean isObject(Object value) {
    return value instanceof Map<?, ?>;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> asObject(Object value) {
    return (Map<String, Object>) value;
  }
}
