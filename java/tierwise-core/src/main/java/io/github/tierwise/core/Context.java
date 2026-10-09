package io.github.tierwise.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The caller-supplied map of non-root levels and axes to node IDs for one evaluation (CTX-1).
 *
 * <p>A well-formed context has a {@code levels} map and an {@code axes} map from strings to strings. The
 * root level is never named. {@link #fromJson(Object)} accepts any raw value, so that conformance input
 * can be malformed on purpose; a malformed context carries a {@linkplain #problem() problem}, and the
 * evaluator raises {@code E_CONTEXT_INVALID} for it (EVAL-30, EVAL-31). The checks that depend on the
 * bundle (names, node IDs, admission) run later, inside the evaluator.
 */
public final class Context {

  private static final Context EMPTY = new Context(Map.of(), Map.of(), null);

  private final Map<String, String> levels;
  private final Map<String, String> axes;
  private final String problem;

  private Context(Map<String, String> levels, Map<String, String> axes, String problem) {
    this.levels = levels;
    this.axes = axes;
    this.problem = problem;
  }

  /**
   * Returns the empty context.
   *
   * @return a context that names no level and no axis
   */
  public static Context empty() {
    return EMPTY;
  }

  /**
   * Creates a well-formed context.
   *
   * @param levels non-root level names mapped to node IDs
   * @param axes axis names mapped to node IDs
   * @return the context
   */
  public static Context of(Map<String, String> levels, Map<String, String> axes) {
    return new Context(copy(levels), copy(axes), null);
  }

  /**
   * Starts a builder.
   *
   * @return a new builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Creates a context from raw data, such as parsed JSON or YAML.
   *
   * <p>The raw value must be a map with the optional members {@code levels} and {@code axes}, each a map
   * from strings to strings. A missing member is an empty map (CTX-2). Any other shape yields a context
   * whose {@link #problem()} is present: a value that is not a map (including {@code null}), a member
   * other than {@code levels} and {@code axes}, a member that is {@code null} or not a map, a name that
   * is not a string, or a node ID that is not a string (CTX-1, CTX-2, CTX-6).
   *
   * @param raw the raw context
   * @return the context; never {@code null}
   */
  public static Context fromJson(Object raw) {
    if (!(raw instanceof Map<?, ?> members)) {
      return malformed("the context is not a map");
    }
    Map<String, String> levels = Map.of();
    Map<String, String> axes = Map.of();
    for (Map.Entry<?, ?> member : members.entrySet()) {
      if (!(member.getKey() instanceof String name)) {
        return malformed("a context member name is not a string");
      }
      if (name.equals("levels") || name.equals("axes")) {
        Map<String, String> read = new LinkedHashMap<>();
        String problem = readNodes(name, member.getValue(), read);
        if (problem != null) {
          return malformed(problem);
        }
        if (name.equals("levels")) {
          levels = read;
        } else {
          axes = read;
        }
      } else {
        return malformed("the context has an unknown member: " + name);
      }
    }
    return new Context(copy(levels), copy(axes), null);
  }

  private static String readNodes(String member, Object value, Map<String, String> into) {
    if (!(value instanceof Map<?, ?> map)) {
      return "the context member " + member + " is not a map";
    }
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (!(entry.getKey() instanceof String name)) {
        return "a name in " + member + " is not a string";
      }
      if (!(entry.getValue() instanceof String node)) {
        return "the node ID for " + name + " in " + member + " is not a string";
      }
      into.put(name, node);
    }
    return null;
  }

  private static Context malformed(String problem) {
    return new Context(Map.of(), Map.of(), problem);
  }

  private static Map<String, String> copy(Map<String, String> source) {
    Map<String, String> copy = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : source.entrySet()) {
      copy.put(Objects.requireNonNull(entry.getKey(), "name"), Objects.requireNonNull(entry.getValue(), "node ID"));
    }
    return Collections.unmodifiableMap(copy);
  }

  /**
   * Returns the non-root levels and their node IDs.
   *
   * @return an unmodifiable map; empty for a malformed context
   */
  public Map<String, String> levels() {
    return levels;
  }

  /**
   * Returns the axes and their node IDs.
   *
   * @return an unmodifiable map; empty for a malformed context
   */
  public Map<String, String> axes() {
    return axes;
  }

  /**
   * Describes why the raw value given to {@link #fromJson(Object)} was not a well-formed context.
   *
   * @return the problem, or empty for a well-formed context. When present, an evaluator raises
   *     {@code E_CONTEXT_INVALID}.
   */
  public Optional<String> problem() {
    return Optional.ofNullable(problem);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Context that
        && levels.equals(that.levels)
        && axes.equals(that.axes)
        && Objects.equals(problem, that.problem);
  }

  @Override
  public int hashCode() {
    return Objects.hash(levels, axes, problem);
  }

  @Override
  public String toString() {
    return problem == null ? "Context[levels=" + levels + ", axes=" + axes + "]" : "Context[malformed: " + problem + "]";
  }

  /** Builds a well-formed {@link Context}. */
  public static final class Builder {

    private final Map<String, String> levels = new LinkedHashMap<>();
    private final Map<String, String> axes = new LinkedHashMap<>();

    private Builder() {
    }

    /**
     * Names a node of a non-root level.
     *
     * @param level the level name
     * @param nodeId the node ID
     * @return this builder
     */
    public Builder level(String level, String nodeId) {
      levels.put(Objects.requireNonNull(level, "level"), Objects.requireNonNull(nodeId, "nodeId"));
      return this;
    }

    /**
     * Names a node of an axis.
     *
     * @param axis the axis name
     * @param nodeId the node ID
     * @return this builder
     */
    public Builder axis(String axis, String nodeId) {
      axes.put(Objects.requireNonNull(axis, "axis"), Objects.requireNonNull(nodeId, "nodeId"));
      return this;
    }

    /**
     * Builds the context.
     *
     * @return the context
     */
    public Context build() {
      return Context.of(levels, axes);
    }
  }
}
