package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes a type declaration as the bundle holds it (BUNDLE-52, BUNDLE-82): the {@code type} member, the type
 * members that the type defines, and {@code optional} only on an optional object field.
 */
final class TypeWriter {

  private TypeWriter() {
  }

  /**
   * Returns the content form of a declaration.
   *
   * @param type the declaration
   * @return a map of plain values
   */
  static Map<String, Object> write(TypeDecl type) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("type", type.type());
    type.min().ifPresent(value -> out.put("min", value));
    type.max().ifPresent(value -> out.put("max", value));
    type.format().ifPresent(value -> out.put("format", value));
    switch (type.type()) {
      case "enum" -> out.put("values", Order.sortedCopy(type.values()));
      case "set" -> out.put("universe", type.universe());
      case "list" -> out.put("items", write(type.items().orElseThrow()));
      case "object" -> out.put("fields", writeFields(type));
      default -> {
      }
    }
    if (type.optional()) {
      out.put("optional", Boolean.TRUE);
    }
    return out;
  }

  private static Map<String, Object> writeFields(TypeDecl type) {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (Map.Entry<String, TypeDecl> field : type.fields().entrySet()) {
      fields.put(field.getKey(), write(field.getValue()));
    }
    return fields;
  }
}
