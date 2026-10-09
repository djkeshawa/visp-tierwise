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
 * The result of {@code decide} (specification section 9.6): the effect of a feature and the provenance
 * that explains it.
 *
 * @param feature the declared feature name (PROV-64)
 * @param effect the effect, unchanged (PROV-65 to PROV-67): a string of the feature's enum, {@code null}
 *     for a nullable enum, or an object
 * @param bundleVersion the digest of the loaded bundle
 * @param skippedScopes the scopes that admission skipped (PROV-54 to PROV-61)
 * @param binding the selected binding (PROV-68 to PROV-70)
 * @param policy the policy of the selected binding (PROV-71)
 * @param reason why the effect was chosen (PROV-73 to PROV-77)
 * @param trace the rules checked, in order (PROV-78 to PROV-84)
 */
public record Decision(
    String feature,
    Object effect,
    String bundleVersion,
    List<SkippedScope> skippedScopes,
    Binding binding,
    String policy,
    Reason reason,
    List<TraceEntry> trace) {

  /** Validates the components and copies the effect deeply. */
  public Decision {
    Objects.requireNonNull(feature, "feature");
    Objects.requireNonNull(bundleVersion, "bundleVersion");
    Objects.requireNonNull(binding, "binding");
    Objects.requireNonNull(policy, "policy");
    Objects.requireNonNull(reason, "reason");
    effect = Plain.copyEffect(effect);
    skippedScopes = List.copyOf(skippedScopes);
    trace = List.copyOf(trace);
  }

  /**
   * Returns the JSON form of section 9.6: the members {@code feature}, {@code effect},
   * {@code bundleVersion}, {@code skippedScopes}, {@code binding}, {@code policy}, {@code reason} and
   * {@code trace}. Neither {@code contributions} nor {@code source} appears (PROV-63).
   *
   * @return an unmodifiable map
   */
  public Map<String, Object> toJson() {
    Map<String, Object> json = new LinkedHashMap<>();
    json.put("feature", feature);
    json.put("effect", effect);
    json.put("bundleVersion", bundleVersion);
    json.put("skippedScopes", Resolved.skippedJson(skippedScopes));
    json.put("binding", binding.toJson());
    json.put("policy", policy);
    json.put("reason", reason.toJson());
    List<Object> entries = new ArrayList<>();
    for (TraceEntry entry : trace) {
      entries.add(entry.toJson());
    }
    json.put("trace", Collections.unmodifiableList(entries));
    return Collections.unmodifiableMap(json);
  }

  /**
   * The selected binding.
   *
   * @param level the level of the selected binding
   * @param node the node ID that the context supplies for the level; present if and only if the level is
   *     not the root level (PROV-69, PROV-70)
   */
  public record Binding(String level, Optional<String> node) {

    /** Validates the components. */
    public Binding {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(node, "node");
    }

    /**
     * Returns the JSON form.
     *
     * @return an unmodifiable map {@code { level, node? }}
     */
    public Map<String, Object> toJson() {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("level", level);
      node.ifPresent(id -> json.put("node", id));
      return Collections.unmodifiableMap(json);
    }
  }

  /**
   * Why the effect was chosen: a rule matched, no rule matched, or a fact was missing (PROV-73).
   *
   * @param type which of the three it is
   * @param id for {@link Type#RULE}, the winning rule's ID; otherwise empty
   * @param name for {@link Type#RULE}, the winning rule's name; otherwise empty
   * @param facts for {@link Type#MISSING_FACT}, the missing fact names in the order RULE-44 defines;
   *     otherwise empty
   */
  public record Reason(Type type, Optional<String> id, Optional<String> name, List<String> facts) {

    /** The three kinds of reason. */
    public enum Type {
      /** A rule matched. */
      RULE,
      /** No rule matched and the policy default applied. */
      DEFAULT,
      /** The missing-fact precheck failed and the feature's {@code if_fact_missing} applied. */
      MISSING_FACT
    }

    /** Validates the components against the shape each type requires (PROV-74 to PROV-76). */
    public Reason {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(name, "name");
      facts = List.copyOf(facts);
      boolean rule = type == Type.RULE;
      if (rule != (id.isPresent() && name.isPresent()) || (!rule && (id.isPresent() || name.isPresent()))) {
        throw new IllegalArgumentException("only a RULE reason has an id and a name");
      }
      if ((type == Type.MISSING_FACT) == facts.isEmpty()) {
        throw new IllegalArgumentException("only a MISSING_FACT reason has facts, and it has at least one");
      }
    }

    /**
     * Creates the reason for a winning rule.
     *
     * @param id the rule ID
     * @param name the rule name
     * @return the reason
     */
    public static Reason rule(String id, String name) {
      return new Reason(Type.RULE, Optional.of(id), Optional.of(name), List.of());
    }

    /**
     * Creates the reason for a decision that fell through to the policy default.
     *
     * @return the reason
     */
    public static Reason defaultEffect() {
      return new Reason(Type.DEFAULT, Optional.empty(), Optional.empty(), List.of());
    }

    /**
     * Creates the reason for a failed missing-fact precheck.
     *
     * @param facts the missing fact names
     * @return the reason
     */
    public static Reason missingFact(List<String> facts) {
      return new Reason(Type.MISSING_FACT, Optional.empty(), Optional.empty(), facts);
    }

    /**
     * Returns the JSON form: {@code { type, id, name }}, {@code { type }} or {@code { type, facts }}.
     *
     * @return an unmodifiable map
     */
    public Map<String, Object> toJson() {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("type", type.name());
      id.ifPresent(value -> json.put("id", value));
      name.ifPresent(value -> json.put("name", value));
      if (type == Type.MISSING_FACT) {
        json.put("facts", facts);
      }
      return Collections.unmodifiableMap(json);
    }
  }

  /**
   * One rule that was checked (PROV-78 to PROV-81).
   *
   * @param id the rule ID
   * @param matched whether every condition held
   * @param failed the first failing condition; present if and only if the rule did not match
   */
  public record TraceEntry(String id, boolean matched, Optional<Failed> failed) {

    /** Validates the components. */
    public TraceEntry {
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(failed, "failed");
      if (matched == failed.isPresent()) {
        throw new IllegalArgumentException("failed is present if and only if the rule did not match");
      }
    }

    /**
     * Creates the entry of a matching rule.
     *
     * @param id the rule ID
     * @return the entry
     */
    public static TraceEntry matched(String id) {
      return new TraceEntry(id, true, Optional.empty());
    }

    /**
     * Creates the entry of a rule that did not match.
     *
     * @param id the rule ID
     * @param fact the fact of the first failing condition
     * @param op the operator of that condition
     * @return the entry
     */
    public static TraceEntry failed(String id, String fact, String op) {
      return new TraceEntry(id, false, Optional.of(new Failed(fact, op)));
    }

    /**
     * Returns the JSON form: {@code { id, matched, failed? }}.
     *
     * @return an unmodifiable map
     */
    public Map<String, Object> toJson() {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("id", id);
      json.put("matched", matched);
      failed.ifPresent(value -> json.put("failed", value.toJson()));
      return Collections.unmodifiableMap(json);
    }
  }

  /**
   * The first failing condition of a rule that did not match (PROV-81).
   *
   * @param fact the fact name
   * @param op the operator
   */
  public record Failed(String fact, String op) {

    /** Validates the components. */
    public Failed {
      Objects.requireNonNull(fact, "fact");
      Objects.requireNonNull(op, "op");
    }

    /**
     * Returns the JSON form.
     *
     * @return an unmodifiable map {@code { fact, op }}
     */
    public Map<String, Object> toJson() {
      Map<String, Object> json = new LinkedHashMap<>();
      json.put("fact", fact);
      json.put("op", op);
      return Collections.unmodifiableMap(json);
    }
  }
}
