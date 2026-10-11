package io.github.visplabs.tierwise.core.internal.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deep, unmodifiable copies of plain values.
 *
 * <p>A plain value is a {@code Boolean}, a {@code Long} (every integer), a {@code String}, a
 * {@code List<Object>} (sets and lists) or a {@code Map<String, Object>} (objects). {@code Byte},
 * {@code Short} and {@code Integer} are widened to {@code Long}. Nothing else is accepted, and
 * {@code null} is never a value, except through {@link #copyEffect(Object)}.
 */
public final class Plain {

  private Plain() {
  }

  /**
   * Copies a plain value deeply into unmodifiable collections.
   *
   * @param value a plain value, never {@code null}
   * @return an equal value that shares no mutable state with the argument
   * @throws IllegalArgumentException if the value, or a nested value, is not a plain value
   */
  public static Object copy(Object value) {
    if (value == null) {
      throw new IllegalArgumentException("null is not a plain value");
    }
    if (value instanceof Boolean || value instanceof Long || value instanceof String) {
      return value;
    }
    if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
      return ((Number) value).longValue();
    }
    if (value instanceof Map<?, ?> map) {
      return copyMap(map);
    }
    if (value instanceof Collection<?> collection) {
      List<Object> copy = new ArrayList<>(collection.size());
      for (Object element : collection) {
        copy.add(copy(element));
      }
      return Collections.unmodifiableList(copy);
    }
    throw new IllegalArgumentException("not a plain value: " + value.getClass().getName());
  }

  /**
   * Copies an effect: a plain value, or {@code null} for the null effect of a nullable enum feature.
   *
   * @param value a plain value or {@code null}
   * @return the copy, or {@code null}
   */
  public static Object copyEffect(Object value) {
    return value == null ? null : copy(value);
  }

  /**
   * Copies a map whose keys are strings and whose values are plain values.
   *
   * @param map the map to copy
   * @return an unmodifiable copy that keeps the iteration order of the argument
   * @throws IllegalArgumentException if a key is not a string or a value is not a plain value
   */
  public static Map<String, Object> copyMap(Map<?, ?> map) {
    Map<String, Object> copy = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (!(entry.getKey() instanceof String key)) {
        throw new IllegalArgumentException("object member names must be strings: " + entry.getKey());
      }
      copy.put(key, copy(entry.getValue()));
    }
    return Collections.unmodifiableMap(copy);
  }

  /**
   * Copies a map of already-immutable values without copying the values, keeping iteration order.
   *
   * @param map the map to copy; no key or value may be {@code null}
   * @param <V> the value type
   * @return an unmodifiable copy
   */
  public static <V> Map<String, V> orderedMap(Map<String, ? extends V> map) {
    Map<String, V> copy = new LinkedHashMap<>();
    for (Map.Entry<String, ? extends V> entry : map.entrySet()) {
      if (entry.getKey() == null || entry.getValue() == null) {
        throw new NullPointerException("null key or value in map");
      }
      copy.put(entry.getKey(), entry.getValue());
    }
    return Collections.unmodifiableMap(copy);
  }
}
