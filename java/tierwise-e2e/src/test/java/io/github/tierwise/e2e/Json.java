package io.github.tierwise.e2e;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** JSON helpers. All results are compared as trees that were parsed from text, so numbers have one Java type. */
final class Json {

  static final JsonMapper MAPPER = JsonMapper.builder().build();

  private Json() {
  }

  /** Parses JSON text. */
  static JsonNode parse(String text) {
    return MAPPER.readTree(text);
  }

  /** Converts a plain value (maps, lists, strings, numbers, booleans) to a tree, through its JSON text. */
  static JsonNode tree(Object plain) {
    return parse(MAPPER.writeValueAsString(plain));
  }

  /** Converts a tree to plain values, for the {@code fromJson} factories of the core. */
  static Object plain(JsonNode node) {
    return MAPPER.treeToValue(node, Object.class);
  }

  /** Renders a tree for a failure message. */
  static String pretty(JsonNode node) {
    return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
  }
}
