package io.github.tierwise.core.conformance;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A lenient JSON reader for the runner's own needs (it reads a bundle file to learn which levels are
 * service-sourced). It is not the strict loader of the core: duplicate names keep the last value and
 * numbers keep whatever type they have.
 */
final class JsonValues {

  private static final JsonFactory FACTORY = new JsonFactory();

  private JsonValues() {
  }

  /**
   * Parses one JSON document into maps, lists, strings, longs, big integers, doubles, booleans and null.
   *
   * @param bytes UTF-8 JSON
   * @return the value
   * @throws IOException if the input is not well-formed JSON
   */
  static Object parse(byte[] bytes) throws IOException {
    try (JsonParser parser = FACTORY.createParser(bytes)) {
      parser.nextToken();
      Object value = read(parser);
      if (parser.nextToken() != null) {
        throw new IOException("trailing content after the JSON value");
      }
      return value;
    }
  }

  private static Object read(JsonParser parser) throws IOException {
    JsonToken token = parser.currentToken();
    if (token == null) {
      throw new IOException("empty input");
    }
    switch (token) {
      case START_OBJECT: {
        Map<String, Object> map = new LinkedHashMap<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
          String name = parser.currentName();
          parser.nextToken();
          map.put(name, read(parser));
        }
        return map;
      }
      case START_ARRAY: {
        List<Object> list = new ArrayList<>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
          list.add(read(parser));
        }
        return list;
      }
      case VALUE_STRING:
        return parser.getText();
      case VALUE_NUMBER_INT: {
        BigInteger value = parser.getBigIntegerValue();
        return value.bitLength() < 64 ? (Object) value.longValue() : value;
      }
      case VALUE_NUMBER_FLOAT:
        return parser.getDoubleValue();
      case VALUE_TRUE:
        return Boolean.TRUE;
      case VALUE_FALSE:
        return Boolean.FALSE;
      case VALUE_NULL:
        return null;
      default:
        throw new IOException("unexpected token " + token);
    }
  }
}
