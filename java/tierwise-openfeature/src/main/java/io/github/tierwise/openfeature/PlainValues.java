package io.github.tierwise.openfeature;

import dev.openfeature.sdk.ImmutableStructure;
import dev.openfeature.sdk.Value;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between OpenFeature {@link Value}s and the plain values of the Tierwise core: {@code Boolean},
 * {@code Long}, {@code String}, {@code List} and {@code Map}.
 */
final class PlainValues {

  private PlainValues() {
  }

  /**
   * Converts an evaluation-context value to a plain value for the evaluator. Anything that is not a
   * boolean, a number, a string, a list or a structure is passed through unchanged, so that the
   * evaluator rejects it with its own error code. An {@code Integer} becomes a {@code Long}.
   *
   * @param value the value; {@code null} and a null value both give {@code null}
   * @return the plain value
   */
  static Object fromValue(Value value) {
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.isBoolean()) {
      return value.asBoolean();
    }
    if (value.isString()) {
      return value.asString();
    }
    if (value.isNumber()) {
      Object number = value.asObject();
      return number instanceof Integer integer ? Long.valueOf(integer.longValue()) : number;
    }
    if (value.isList()) {
      List<Object> list = new ArrayList<>();
      for (Value element : value.asList()) {
        list.add(fromValue(element));
      }
      return list;
    }
    if (value.isStructure()) {
      Map<String, Object> map = new LinkedHashMap<>();
      for (Map.Entry<String, Value> entry : value.asStructure().asUnmodifiableMap().entrySet()) {
        map.put(entry.getKey(), fromValue(entry.getValue()));
      }
      return map;
    }
    return value.asObject();
  }

  /**
   * Converts a plain value that the evaluator returned to an OpenFeature value: a list for a set or a list,
   * a structure for an object.
   *
   * @param plain a {@code Boolean}, {@code Long}, {@code String}, {@code List} or {@code Map}
   * @return the value
   * @throws IllegalArgumentException for any other class, which the evaluator never returns
   */
  static Value toValue(Object plain) {
    if (plain instanceof Boolean flag) {
      return new Value(flag);
    }
    if (plain instanceof Long number) {
      return new Value(number);
    }
    if (plain instanceof String text) {
      return new Value(text);
    }
    if (plain instanceof List<?> list) {
      List<Value> elements = new ArrayList<>(list.size());
      for (Object element : list) {
        elements.add(toValue(element));
      }
      return new Value(elements);
    }
    if (plain instanceof Map<?, ?> map) {
      Map<String, Value> members = new LinkedHashMap<>();
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        members.put((String) entry.getKey(), toValue(entry.getValue()));
      }
      return new Value(new ImmutableStructure(members));
    }
    throw new IllegalArgumentException("not a plain value: " + plain);
  }
}
