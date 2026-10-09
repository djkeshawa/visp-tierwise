package io.github.tierwise.core;

import io.github.tierwise.core.internal.model.Plain;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The result of {@code resolve} (specification section 9.2): the value of a key and the provenance that
 * explains it.
 *
 * @param key the declared key name (PROV-12)
 * @param strategy the key's merge strategy (PROV-13)
 * @param kind the name of the kind the key opts into; present if and only if it opts into one (PROV-14)
 * @param value the result as a plain value, or {@code null} if and only if the result is absent
 *     (PROV-16, PROV-17)
 * @param bundleVersion the digest of the loaded bundle (PROV-18)
 * @param skippedScopes the scopes that admission skipped, in declared order (PROV-54 to PROV-61)
 * @param contributions at most one entry for each contributing scope, in evaluation-path order
 *     (PROV-19 to PROV-44)
 * @param source the winning scope for an {@code override} or {@code deep_merge} key; present if and only
 *     if the strategy is one of these (PROV-45)
 */
public record Resolved(
    String key,
    String strategy,
    Optional<String> kind,
    Object value,
    String bundleVersion,
    List<SkippedScope> skippedScopes,
    List<Contribution> contributions,
    Optional<Source> source) {

  /** Validates the components and copies the value deeply. */
  public Resolved {
    Objects.requireNonNull(key, "key");
    Objects.requireNonNull(strategy, "strategy");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(bundleVersion, "bundleVersion");
    Objects.requireNonNull(source, "source");
    value = Plain.copyEffect(value);
    skippedScopes = List.copyOf(skippedScopes);
    contributions = List.copyOf(contributions);
  }

  /**
   * Tells whether the result has a value.
   *
   * @return {@code true} unless the result is absent
   */
  public boolean hasValue() {
    return value != null;
  }

  /**
   * Returns the JSON form of section 9.2. Absent conditional members are absent from the map, never
   * {@code null} (PROV-3); {@code value} is {@code null} exactly when the result is absent.
   *
   * @return an unmodifiable map with the members {@code key}, {@code strategy}, {@code kind} (when
   *     present), {@code value}, {@code bundleVersion}, {@code skippedScopes}, {@code contributions}
   *     and {@code source} (when present)
   */
  public Map<String, Object> toJson() {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("key", key);
    json.put("strategy", strategy);
    kind.ifPresent(name -> json.put("kind", name));
    json.put("value", value);
    json.put("bundleVersion", bundleVersion);
    json.put("skippedScopes", skippedJson(skippedScopes));
    List<Object> entries = new ArrayList<>();
    for (Contribution contribution : contributions) {
      entries.add(contribution.toJson());
    }
    json.put("contributions", Collections.unmodifiableList(entries));
    source.ifPresent(winner -> json.put("source", winner.toJson()));
    return Collections.unmodifiableMap(json);
  }

  static List<Object> skippedJson(List<SkippedScope> skipped) {
    List<Object> entries = new ArrayList<>();
    for (SkippedScope scope : skipped) {
      entries.add(scope.toJson());
    }
    return Collections.unmodifiableList(entries);
  }

  /**
   * One element of {@code contributions} (section 9.3): a value entry or a failure entry.
   *
   * @param scope whether the scope is a level or an axis (PROV-23)
   * @param name the declared scope name (PROV-23)
   * @param node the node ID that the context supplies; present if and only if the scope is not the root
   *     level (PROV-25, PROV-26)
   * @param value the contributed value; present for a value entry and for a failure entry under
   *     {@code deny}, absent for a failure entry under {@code skip}; never {@code null} (PROV-27 to
   *     PROV-31)
   * @param effect the contribution label (PROV-24)
   * @param onSourceFailure {@code skip} or {@code deny}; present if and only if the entry is a failure
   *     entry (PROV-32, PROV-33)
   */
  public record Contribution(
      ScopeKind scope,
      String name,
      Optional<String> node,
      Optional<Object> value,
      Label effect,
      Optional<String> onSourceFailure) {

    /** The contribution labels (PROV-38, PROV-39, PROV-40). */
    public enum Label {
      /** An {@code all} key contributes {@code false}. */
      VETO,
      /** A value widens the result: {@code any}, {@code union}, {@code max}. */
      WIDENED,
      /** A value narrows the result: {@code intersect}, {@code min}. */
      NARROWED,
      /** An {@code override} or {@code deep_merge} key contributes a value. */
      SET,
      /** The value is the identity of the key's strategy. */
      IGNORED,
      /** A source failure: the provider threw, rejected or timed out. */
      UNAVAILABLE,
      /** A source failure: the provider returned an invalid value. */
      INVALID;

      /**
       * Tells whether the label marks a failure entry.
       *
       * @return {@code true} for {@link #UNAVAILABLE} and {@link #INVALID}
       */
      public boolean isFailure() {
        return this == UNAVAILABLE || this == INVALID;
      }
    }

    /** Validates the components and enforces the presence rules of PROV-27, PROV-30 and PROV-32. */
    public Contribution {
      Objects.requireNonNull(scope, "scope");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(node, "node");
      Objects.requireNonNull(effect, "effect");
      Objects.requireNonNull(onSourceFailure, "onSourceFailure");
      value = Objects.requireNonNull(value, "value").map(Plain::copy);
      if (effect.isFailure() != onSourceFailure.isPresent()) {
        throw new IllegalArgumentException("onSourceFailure is present if and only if the entry is a failure entry");
      }
      boolean valueExpected = !effect.isFailure() || onSourceFailure.get().equals("deny");
      if (valueExpected != value.isPresent()) {
        throw new IllegalArgumentException(
            "value is present for a value entry and for a failure under deny, and only then");
      }
    }

    /**
     * Returns the JSON form of section 9.3.
     *
     * @return an unmodifiable map with the members {@code scope}, {@code name}, {@code node} (when
     *     present), {@code value} (when present), {@code effect} and {@code onSourceFailure} (when
     *     present)
     */
    public Map<String, Object> toJson() {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("scope", scope.json());
      json.put("name", name);
      node.ifPresent(id -> json.put("node", id));
      value.ifPresent(v -> json.put("value", v));
      json.put("effect", effect.name());
      onSourceFailure.ifPresent(behaviour -> json.put("onSourceFailure", behaviour));
      return Collections.unmodifiableMap(json);
    }
  }

  /**
   * The {@code source} of an {@code override} or {@code deep_merge} result (section 9.4): the string
   * {@code DEFAULT}, or the one scope that supplied the winning value.
   *
   * @param scope the kind of the winning scope; empty exactly for {@code DEFAULT}
   * @param name the winning scope's declared name; empty exactly for {@code DEFAULT}
   * @param node the winning node ID; present if and only if the winning scope is not the root level
   *     (PROV-47)
   */
  public record Source(Optional<ScopeKind> scope, Optional<String> name, Optional<String> node) {

    /** The source of a result that no scope contributed to (PROV-48). */
    public static final Source DEFAULT = new Source(Optional.empty(), Optional.empty(), Optional.empty());

    /** Validates the components. */
    public Source {
      Objects.requireNonNull(scope, "scope");
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(node, "node");
      if (scope.isPresent() != name.isPresent()) {
        throw new IllegalArgumentException("scope and name are both present or both absent");
      }
      if (scope.isEmpty() && node.isPresent()) {
        throw new IllegalArgumentException("DEFAULT has no node");
      }
    }

    /**
     * Creates the source for a winning scope.
     *
     * @param scope whether the scope is a level or an axis
     * @param name the declared scope name
     * @param node the node ID, or {@code null} for the root level
     * @return the source
     */
    public static Source of(ScopeKind scope, String name, String node) {
      return new Source(Optional.of(scope), Optional.of(name), Optional.ofNullable(node));
    }

    /**
     * Tells whether this is the {@code DEFAULT} source.
     *
     * @return {@code true} when no scope contributed
     */
    public boolean isDefault() {
      return scope.isEmpty();
    }

    /**
     * Returns the JSON form of PROV-46.
     *
     * @return the string {@code DEFAULT}, or an unmodifiable map {@code { scope, name, node? }}
     */
    public Object toJson() {
      if (isDefault()) {
        return "DEFAULT";
      }
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("scope", scope.get().json());
      json.put("name", name.get());
      node.ifPresent(id -> json.put("node", id));
      return Collections.unmodifiableMap(json);
    }
  }
}
