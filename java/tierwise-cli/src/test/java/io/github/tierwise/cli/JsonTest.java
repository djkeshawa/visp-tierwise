package io.github.tierwise.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class JsonTest {

  @Test
  void writesNestedObjectsAndArraysWithoutWhitespace() {
    Json json = new Json().beginObject()
        .name("a").value(1)
        .name("b").beginArray().value("x").value(true).endArray()
        .name("c").beginObject().name("d").path(List.of("k", 2)).endObject()
        .endObject();
    assertEquals("{\"a\":1,\"b\":[\"x\",true],\"c\":{\"d\":[\"k\",2]}}", json.toString());
  }

  @Test
  void escapesQuotesBackslashesAndControlCharacters() {
    Json json = new Json().beginArray().value("a\"b\\c\nd\u0001e").endArray();
    assertEquals("[\"a\\\"b\\\\c\\nd\\u0001e\"]", json.toString());
  }

  @Test
  void keepsNonAsciiTextAsIs() {
    assertEquals("[\"é中\"]", new Json().beginArray().value("é中").endArray().toString());
  }
}
