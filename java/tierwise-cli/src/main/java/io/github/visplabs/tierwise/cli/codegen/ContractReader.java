package io.github.visplabs.tierwise.cli.codegen;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads the keys and features of a canonical bundle into a {@link Contract}. The bundle is the output
 * of {@code Tierwise.compile}, so every member is present and well formed; a missing member is reported
 * as {@link IllegalStateException}.
 */
final class ContractReader {

  private ContractReader() {
  }

  /**
   * Reads the contract of one bundle.
   *
   * @param bundle the exact bytes of a canonical bundle
   * @return the contract
   * @throws IllegalStateException if the bundle does not have the shape of BUNDLE-14 to BUNDLE-108
   */
  static Contract read(byte[] bundle) {
    Map<String, Object> root = objectOf(JsonReader.parse(new String(bundle, StandardCharsets.UTF_8)), "the bundle");
    String bundleVersion = stringOf(root.get("bundleVersion"), "bundleVersion");
    Map<String, Object> content = objectOf(root.get("content"), "content");

    List<Contract.KeyDecl> keys = new ArrayList<>();
    for (Map.Entry<String, Object> entry : objectOf(content.get("keys"), "keys").entrySet()) {
      Map<String, Object> key = objectOf(entry.getValue(), "key " + entry.getKey());
      keys.add(new Contract.KeyDecl(
          entry.getKey(),
          stringOf(key.get("combine"), "combine"),
          parseType(objectOf(key.get("type"), "the type of " + entry.getKey()))));
    }

    List<Contract.FeatureDecl> features = new ArrayList<>();
    for (Map.Entry<String, Object> entry : objectOf(content.get("features"), "features").entrySet()) {
      Map<String, Object> feature = objectOf(entry.getValue(), "feature " + entry.getKey());
      features.add(new Contract.FeatureDecl(
          entry.getKey(), parseReturns(objectOf(feature.get("returns"), "returns of " + entry.getKey()))));
    }
    return new Contract(bundleVersion, keys, features);
  }

  private static TypeDecl parseType(Map<String, Object> declaration) {
    String type = stringOf(declaration.get("type"), "type");
    return switch (type) {
      case "boolean" -> new TypeDecl.BooleanType();
      case "integer" -> new TypeDecl.IntegerType();
      case "decimal" -> new TypeDecl.DecimalType();
      case "string" -> new TypeDecl.StringType();
      case "enum" -> new TypeDecl.EnumType(stringsOf(declaration.get("values"), "values"));
      case "set" -> new TypeDecl.SetType(stringsOf(declaration.get("universe"), "universe"));
      case "list" -> new TypeDecl.ListType(parseType(objectOf(declaration.get("items"), "items")));
      case "object" -> new TypeDecl.ObjectType(parseFields(declaration.get("fields")));
      default -> throw new IllegalStateException("the bundle has the unknown type " + type);
    };
  }

  private static Contract.Returns parseReturns(Map<String, Object> returns) {
    if (returns.containsKey("enum")) {
      return new Contract.Returns.EnumForm(
          stringsOf(returns.get("enum"), "enum"), Boolean.TRUE.equals(returns.get("nullable")));
    }
    if (returns.containsKey("object")) {
      return new Contract.Returns.ObjectForm(parseFields(returns.get("object")));
    }
    throw new IllegalStateException("the bundle has a returns member with neither enum nor object");
  }

  private static Map<String, TypeDecl.Field> parseFields(Object raw) {
    Map<String, TypeDecl.Field> fields = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : objectOf(raw, "fields").entrySet()) {
      Map<String, Object> field = objectOf(entry.getValue(), "field " + entry.getKey());
      fields.put(entry.getKey(), new TypeDecl.Field(
          parseType(field), Boolean.TRUE.equals(field.get("optional"))));
    }
    return Collections.unmodifiableMap(fields);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> objectOf(Object value, String what) {
    if (!(value instanceof Map<?, ?>)) {
      throw new IllegalStateException("the bundle has no object for " + what);
    }
    return (Map<String, Object>) value;
  }

  private static String stringOf(Object value, String what) {
    if (!(value instanceof String text)) {
      throw new IllegalStateException("the bundle has no string for " + what);
    }
    return text;
  }

  private static List<String> stringsOf(Object value, String what) {
    if (!(value instanceof List<?> items)) {
      throw new IllegalStateException("the bundle has no list for " + what);
    }
    List<String> strings = new ArrayList<>(items.size());
    for (Object item : items) {
      strings.add(stringOf(item, what));
    }
    return strings;
  }
}
