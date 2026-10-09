package io.github.tierwise.core.internal.lint;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A valid example of a rules file, as the lint rules need it (POL-28 to POL-36).
 *
 * <p>The example reaches the lint stage only when every example check passed, so its context and its
 * children are the normalised values that section 2 and section 3 accept.
 *
 * @param feature the feature that the example's rules file configures
 * @param levels the non-root levels that the context names, with their node IDs
 * @param axes the axes that the context names, with their node IDs
 * @param children the child maps of each child entity: the shorthand lists and the explicit children
 *     (POL-32 to POL-35); a child entity with no entry has an omitted collection
 */
public record LintExample(
    String feature,
    Map<String, String> levels,
    Map<String, String> axes,
    Map<String, List<Map<String, Object>>> children) {

  /** Copies the maps and makes them unmodifiable. */
  public LintExample {
    Objects.requireNonNull(feature, "feature");
    levels = copyStrings(levels);
    axes = copyStrings(axes);
    Map<String, List<Map<String, Object>>> copy = new LinkedHashMap<>();
    for (Map.Entry<String, List<Map<String, Object>>> entry : children.entrySet()) {
      copy.put(entry.getKey(), List.copyOf(entry.getValue().stream().map(LintExample::copyMap).toList()));
    }
    children = Collections.unmodifiableMap(copy);
  }

  private static Map<String, String> copyStrings(Map<String, String> source) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }

  private static Map<String, Object> copyMap(Map<String, Object> source) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }
}
