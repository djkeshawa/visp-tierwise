package io.github.tierwise.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The caller-supplied facts for one decision: single-value facts of the decided entity and its
 * ancestors, and the child maps of its children (FACT-22 to FACT-33).
 *
 * <p>A fact whose value is {@code null} is missing, not invalid (FACT-19). Whether a fact is declared and
 * whether its value has the right type are checked by the evaluator, which raises
 * {@code E_FACTS_INVALID}. {@link #fromJson(Object)} accepts any raw value; a shape that cannot be a
 * FactSet yields a value with a {@linkplain #problem() problem}, which the evaluator reports the same
 * way.
 */
public final class FactSet {

  private static final FactSet EMPTY = new FactSet(Map.of(), Map.of(), null);

  private final Map<String, Object> facts;
  private final Map<String, List<Map<String, Object>>> children;
  private final String problem;

  private FactSet(
      Map<String, Object> facts, Map<String, List<Map<String, Object>>> children, String problem) {
    this.facts = facts;
    this.children = children;
    this.problem = problem;
  }

  /**
   * Returns the empty FactSet: every single-value fact is absent and no child exists.
   *
   * @return the empty FactSet
   */
  public static FactSet empty() {
    return EMPTY;
  }

  /**
   * Creates a FactSet from typed parts.
   *
   * @param facts fact names mapped to values; a {@code null} value marks the fact missing
   * @param children child entity names mapped to child maps (fact name to value)
   * @return the FactSet
   */
  public static FactSet of(Map<String, Object> facts, Map<String, List<Map<String, Object>>> children) {
    try {
      return new FactSet(copyFacts(facts), copyChildren(children), null);
    } catch (ShapeException e) {
      return malformed(e.getMessage());
    }
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
   * Creates a FactSet from raw data, such as parsed JSON or YAML.
   *
   * <p>The raw value must be a map with the optional members {@code facts}, a map from fact names to
   * values, and {@code children}, a map from child entity names to lists of child maps. A missing member
   * is empty (FACT-24). Any other shape yields a FactSet whose {@link #problem()} is present: a value
   * that is not a map (including {@code null}), any other member, a member that is {@code null} or not a
   * map, a child entry that is not a list, an element that is not a map, or a name that is not a string.
   * Values are not type-checked here and are kept as given.
   *
   * @param raw the raw FactSet
   * @return the FactSet; never {@code null}
   */
  public static FactSet fromJson(Object raw) {
    if (!(raw instanceof Map<?, ?> members)) {
      return malformed("the FactSet is not a map");
    }
    try {
      Map<String, Object> facts = Map.of();
      Map<String, List<Map<String, Object>>> children = Map.of();
      for (Map.Entry<?, ?> member : members.entrySet()) {
        if (!(member.getKey() instanceof String name)) {
          throw new ShapeException("a FactSet member name is not a string");
        }
        switch (name) {
          case "facts" -> facts = copyFacts(member.getValue());
          case "children" -> children = copyChildren(member.getValue());
          default -> throw new ShapeException("the FactSet has an unknown member: " + name);
        }
      }
      return new FactSet(facts, children, null);
    } catch (ShapeException e) {
      return malformed(e.getMessage());
    }
  }

  private static FactSet malformed(String problem) {
    return new FactSet(Map.of(), Map.of(), problem);
  }

  private static Map<String, Object> copyFacts(Object raw) {
    if (!(raw instanceof Map<?, ?> map)) {
      throw new ShapeException("facts is not a map");
    }
    Map<String, Object> copy = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (!(entry.getKey() instanceof String name)) {
        throw new ShapeException("a fact name is not a string");
      }
      copy.put(name, freeze(entry.getValue()));
    }
    return Collections.unmodifiableMap(copy);
  }

  private static Map<String, List<Map<String, Object>>> copyChildren(Object raw) {
    if (!(raw instanceof Map<?, ?> map)) {
      throw new ShapeException("children is not a map");
    }
    Map<String, List<Map<String, Object>>> copy = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : map.entrySet()) {
      if (!(entry.getKey() instanceof String name)) {
        throw new ShapeException("a child entity name is not a string");
      }
      if (!(entry.getValue() instanceof Collection<?> list)) {
        throw new ShapeException("the children of " + name + " are not a list");
      }
      List<Map<String, Object>> elements = new ArrayList<>(list.size());
      for (Object element : list) {
        if (!(element instanceof Map<?, ?>)) {
          throw new ShapeException("a child of " + name + " is not a map");
        }
        elements.add(copyFacts(element));
      }
      copy.put(name, Collections.unmodifiableList(elements));
    }
    return Collections.unmodifiableMap(copy);
  }

  /** Copies nested maps and lists into unmodifiable ones and keeps every other value as given. */
  private static Object freeze(Object value) {
    if (value instanceof Map<?, ?> map) {
      return copyFacts(map);
    }
    if (value instanceof Collection<?> collection) {
      List<Object> copy = new ArrayList<>(collection.size());
      for (Object element : collection) {
        copy.add(freeze(element));
      }
      return Collections.unmodifiableList(copy);
    }
    return value;
  }

  /**
   * Returns the single-value facts.
   *
   * @return an unmodifiable map from fact name to value; a value may be {@code null} (missing); empty for
   *     a malformed FactSet
   */
  public Map<String, Object> facts() {
    return facts;
  }

  /**
   * Returns the child maps.
   *
   * @return an unmodifiable map from child entity name to its child maps; empty for a malformed FactSet
   */
  public Map<String, List<Map<String, Object>>> children() {
    return children;
  }

  /**
   * Describes why the raw value given to {@link #fromJson(Object)} was not a well-formed FactSet.
   *
   * @return the problem, or empty. When present, an evaluator raises {@code E_FACTS_INVALID}.
   */
  public Optional<String> problem() {
    return Optional.ofNullable(problem);
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof FactSet that
        && facts.equals(that.facts)
        && children.equals(that.children)
        && Objects.equals(problem, that.problem);
  }

  @Override
  public int hashCode() {
    return Objects.hash(facts, children, problem);
  }

  @Override
  public String toString() {
    return problem == null
        ? "FactSet[facts=" + facts + ", children=" + children + "]"
        : "FactSet[malformed: " + problem + "]";
  }

  private static final class ShapeException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    ShapeException(String message) {
      super(message, null, false, false);
    }
  }

  /** Builds a {@link FactSet}. */
  public static final class Builder {

    private final Map<String, Object> facts = new LinkedHashMap<>();
    private final Map<String, List<Map<String, Object>>> children = new LinkedHashMap<>();

    private Builder() {
    }

    /**
     * Sets a single-value fact.
     *
     * @param name the fact name, {@code <entity>.<attribute>}
     * @param value the value; {@code null} marks the fact missing
     * @return this builder
     */
    public Builder fact(String name, Object value) {
      facts.put(Objects.requireNonNull(name, "name"), value);
      return this;
    }

    /**
     * Adds a child map for a child entity.
     *
     * @param entity the child entity name
     * @param childFacts the child's facts, by fact name
     * @return this builder
     */
    public Builder child(String entity, Map<String, Object> childFacts) {
      children.computeIfAbsent(Objects.requireNonNull(entity, "entity"), name -> new ArrayList<>())
          .add(new LinkedHashMap<>(childFacts));
      return this;
    }

    /**
     * Builds the FactSet.
     *
     * @return the FactSet
     */
    public FactSet build() {
      return FactSet.of(facts, children);
    }
  }
}
