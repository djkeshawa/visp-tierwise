package io.github.tierwise.core;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * The stable codes of findings and evaluation errors, one constant for each entry of the error-code
 * catalogue ({@code spec/error-codes.md}).
 *
 * <p>Three groups exist. <em>Engine errors</em> are reported as findings with severity {@code error}
 * by the compiler. <em>Evaluation errors</em> are raised by an evaluator as a {@link TierwiseException};
 * four of them ({@link #E_UNKNOWN_FEATURE}, {@link #E_UNKNOWN_KEY}, {@link #E_UNKNOWN_NODE} and
 * {@link #E_UNKNOWN_SCOPE}) are also engine errors. <em>Lint rules</em> are identified by a name such as
 * {@code values/no-op}, and each naming pattern adds one rule {@code naming/<name>}.
 */
public final class Code {

  /** The prefix of the identifier of every naming rule ({@code naming/<name>}). */
  public static final String NAMING_PREFIX = "naming/";

  // ---- Engine errors ------------------------------------------------------------------------

  /**
   * A key's {@code combine_across_axes} member has a value that is not allowed, or the member is
   * used where it is not allowed.
   */
  public static final String E_AXIS_COMBINE_INVALID = "E_AXIS_COMBINE_INVALID";

  /**
   * A binding is written at a scope that its feature does not allow.
   */
  public static final String E_BINDING_NOT_ALLOWED = "E_BINDING_NOT_ALLOWED";

  /**
   * A commutative key declares a {@code default}.
   */
  public static final String E_DEFAULT_NOT_ALLOWED = "E_DEFAULT_NOT_ALLOWED";

  /**
   * A key with {@code deny} in effect at a service-sourced level, using the {@code min} strategy,
   * has no lower bound.
   */
  public static final String E_DENY_BOUND_REQUIRED = "E_DENY_BOUND_REQUIRED";

  /**
   * A key with {@code deny} in effect at a service-sourced level uses a strategy that is not
   * narrowing.
   */
  public static final String E_DENY_NOT_NARROWING = "E_DENY_NOT_NARROWING";

  /**
   * A condition or setting uses a fact that is reachable only through too many {@code has_many}
   * links.
   */
  public static final String E_DEPTH_UNSUPPORTED = "E_DEPTH_UNSUPPORTED";

  /**
   * A definition that may be declared only once is declared again.
   */
  public static final String E_DUPLICATE_DEFINITION = "E_DUPLICATE_DEFINITION";

  /**
   * A mapping contains the same key twice.
   */
  public static final String E_DUPLICATE_KEY = "E_DUPLICATE_KEY";

  /**
   * Two node IDs in one catalog are the same ID.
   */
  public static final String E_DUPLICATE_NODE = "E_DUPLICATE_NODE";

  /**
   * Two rules of one policy have the same {@code id}.
   */
  public static final String E_DUPLICATE_RULE_ID = "E_DUPLICATE_RULE_ID";

  /**
   * A value, or an item in a list, is given twice where only one is allowed.
   */
  public static final String E_DUPLICATE_VALUE = "E_DUPLICATE_VALUE";

  /**
   * An effect is used that is not a value of the feature's {@code returns}.
   */
  public static final String E_EFFECT_MISMATCH = "E_EFFECT_MISMATCH";

  /**
   * The entity relations form a cycle, or one entity is both an ancestor and a child of another.
   */
  public static final String E_ENTITY_GRAPH = "E_ENTITY_GRAPH";

  /**
   * An example of a rules file does not produce its expected effect.
   */
  public static final String E_EXAMPLE_FAILED = "E_EXAMPLE_FAILED";

  /**
   * A condition or a setting uses a declared fact that is not in the scope of the decision.
   */
  public static final String E_FACT_NOT_IN_SCOPE = "E_FACT_NOT_IN_SCOPE";

  /**
   * A literal in a condition, or a fact value in an example, is not a valid value of the fact's
   * type.
   */
  public static final String E_ILLEGAL_FACT_VALUE = "E_ILLEGAL_FACT_VALUE";

  /**
   * An integer value or bound is outside the range that the engine supports.
   */
  public static final String E_INTEGER_RANGE = "E_INTEGER_RANGE";

  /**
   * A declaration is malformed, or a value in an example or a declaration has the wrong structure.
   */
  public static final String E_INVALID_DECLARATION = "E_INVALID_DECLARATION";

  /**
   * A node ID written in a source does not have a valid form.
   */
  public static final String E_INVALID_NODE_ID = "E_INVALID_NODE_ID";

  /**
   * A setting declaration, a setting value or a setting reference is invalid.
   */
  public static final String E_INVALID_SETTING = "E_INVALID_SETTING";

  /**
   * A kind definition is internally inconsistent or uses a value that is not allowed.
   */
  public static final String E_KIND_DEFINITION = "E_KIND_DEFINITION";

  /**
   * A key that uses a kind breaks one of the kind's constraints.
   */
  public static final String E_KIND_VIOLATION = "E_KIND_VIOLATION";

  /**
   * The manifest is missing, malformed or names a source that cannot be used.
   */
  public static final String E_MANIFEST = "E_MANIFEST";

  /**
   * An order-dependent key that is settable on an axis has no {@code combine_across_axes}.
   */
  public static final String E_MISSING_AXIS_COMBINE = "E_MISSING_AXIS_COMBINE";

  /**
   * An order-dependent key has no starting value.
   */
  public static final String E_MISSING_DEFAULT = "E_MISSING_DEFAULT";

  /**
   * A key that is settable at a service-sourced level has no source-failure behaviour.
   */
  public static final String E_MISSING_SOURCE_FAILURE = "E_MISSING_SOURCE_FAILURE";

  /**
   * A namespaced schema file contains something that its namespace does not allow, or a shared file
   * declares a namespace.
   */
  public static final String E_NAMESPACE_VIOLATION = "E_NAMESPACE_VIOLATION";

  /**
   * A member of a {@code set} value is not in the {@code universe} of its type.
   */
  public static final String E_NOT_IN_UNIVERSE = "E_NOT_IN_UNIVERSE";

  /**
   * A key has a value at a scope that is not in its {@code settable_at}.
   */
  public static final String E_NOT_SETTABLE_HERE = "E_NOT_SETTABLE_HERE";

  /**
   * An integer source value is outside its declared bounds.
   */
  public static final String E_OUT_OF_RANGE = "E_OUT_OF_RANGE";

  /**
   * A source file other than the manifest, or a catalog file, cannot be parsed.
   */
  public static final String E_PARSE = "E_PARSE";

  /**
   * A preset reference, a preset file or a lint setting in a preset is invalid.
   */
  public static final String E_PRESET = "E_PRESET";

  /**
   * A quantifier is used on a single-value fact.
   */
  public static final String E_QUANTIFIER_NOT_ALLOWED = "E_QUANTIFIER_NOT_ALLOWED";

  /**
   * A child fact is tested without a quantifier.
   */
  public static final String E_QUANTIFIER_REQUIRED = "E_QUANTIFIER_REQUIRED";

  /**
   * A feature has no root binding.
   */
  public static final String E_ROOT_BINDING_MISSING = "E_ROOT_BINDING_MISSING";

  /**
   * A feature's {@code binding_settable_at} does not include the root level.
   */
  public static final String E_ROOT_BINDING_NOT_ALLOWED = "E_ROOT_BINDING_NOT_ALLOWED";

  /**
   * The decision hierarchy or a scope entry is malformed.
   */
  public static final String E_SCOPE_DECLARATION = "E_SCOPE_DECLARATION";

  /**
   * A source file is mapped to a scope or a node in a way that is not allowed.
   */
  public static final String E_SOURCE_MAPPING = "E_SOURCE_MAPPING";

  /**
   * A key's type is not allowed for its merge strategy.
   */
  public static final String E_STRATEGY_TYPE = "E_STRATEGY_TYPE";

  /**
   * A source value does not have the form of its declared type.
   */
  public static final String E_TYPE_MISMATCH = "E_TYPE_MISMATCH";

  /**
   * A declaration has no {@code type}.
   */
  public static final String E_TYPE_REQUIRED = "E_TYPE_REQUIRED";

  /**
   * A name refers to an entity that is not declared.
   */
  public static final String E_UNKNOWN_ENTITY = "E_UNKNOWN_ENTITY";

  /**
   * A fact name refers to a fact that is not declared.
   */
  public static final String E_UNKNOWN_FACT = "E_UNKNOWN_FACT";

  /**
   * A name refers to a feature that is not declared.
   */
  public static final String E_UNKNOWN_FEATURE = "E_UNKNOWN_FEATURE";

  /**
   * A name refers to a key that is not declared.
   */
  public static final String E_UNKNOWN_KEY = "E_UNKNOWN_KEY";

  /**
   * A key names a kind that is not declared.
   */
  public static final String E_UNKNOWN_KIND = "E_UNKNOWN_KIND";

  /**
   * A node ID is not a member of a closed catalog.
   */
  public static final String E_UNKNOWN_NODE = "E_UNKNOWN_NODE";

  /**
   * A binding names a policy that is not declared in its rules file.
   */
  public static final String E_UNKNOWN_POLICY = "E_UNKNOWN_POLICY";

  /**
   * A name refers to a level or axis that is not declared, or a context names an undeclared scope.
   */
  public static final String E_UNKNOWN_SCOPE = "E_UNKNOWN_SCOPE";

  /**
   * A binding or a setting reference names a setting that the policy does not declare.
   */
  public static final String E_UNKNOWN_SETTING = "E_UNKNOWN_SETTING";

  /**
   * A source uses a YAML feature that is outside the portable subset.
   */
  public static final String E_YAML_FEATURE = "E_YAML_FEATURE";

  // ---- Evaluation errors that are not engine errors -----------------------------------------

  /**
   * The bytes of a bundle fail a strict-parsing, digest or structure check.
   */
  public static final String E_BUNDLE_INTEGRITY = "E_BUNDLE_INTEGRITY";

  /**
   * The bundle's format or spec version is not one that this evaluator supports.
   */
  public static final String E_BUNDLE_UNSUPPORTED = "E_BUNDLE_UNSUPPORTED";

  /**
   * The context argument is malformed, or it names the root level.
   */
  public static final String E_CONTEXT_INVALID = "E_CONTEXT_INVALID";

  /**
   * The options given to create evaluator are invalid.
   */
  public static final String E_EVALUATOR_OPTIONS = "E_EVALUATOR_OPTIONS";

  /**
   * The FactSet given to decide is malformed or does not match the decided entity.
   */
  public static final String E_FACTS_INVALID = "E_FACTS_INVALID";

  /**
   * No provider is registered for a service-sourced level.
   */
  public static final String E_PROVIDER_MISSING = "E_PROVIDER_MISSING";

  /**
   * The synchronous variant of resolve would need a provider call.
   */
  public static final String E_PROVIDER_REQUIRED = "E_PROVIDER_REQUIRED";

  // ---- Tooling error ------------------------------------------------------------------------

  /**
   * A provenance sidecar does not match the bundle that it is kept beside.
   */
  public static final String E_PROVENANCE_INVALID = "E_PROVENANCE_INVALID";

  // ---- Lint rules ---------------------------------------------------------------------------

  /**
   * Lint rule {@code examples/mixed-collection}: A policy with {@code all_in} or {@code any_in} has no example with a mixed child collection.
   */
  public static final String LINT_EXAMPLES_MIXED_COLLECTION = "examples/mixed-collection";

  /**
   * Lint rule {@code examples/required-per-binding}: A binding of a feature is the selected binding of no example.
   */
  public static final String LINT_EXAMPLES_REQUIRED_PER_BINDING = "examples/required-per-binding";

  /**
   * Lint rule {@code presets/override}: A later layer defines a kind or naming pattern again and replaces an earlier one.
   */
  public static final String LINT_PRESETS_OVERRIDE = "presets/override";

  /**
   * Lint rule {@code rules/dead-rule}: A rule can never run.
   */
  public static final String LINT_RULES_DEAD_RULE = "rules/dead-rule";

  /**
   * Lint rule {@code schema/description-required}: A key, feature or fact declaration has no description.
   */
  public static final String LINT_SCHEMA_DESCRIPTION_REQUIRED = "schema/description-required";

  /**
   * Lint rule {@code schema/owner-required}: A key declared in a shared file has no owner although namespaces are used.
   */
  public static final String LINT_SCHEMA_OWNER_REQUIRED = "schema/owner-required";

  /**
   * Lint rule {@code schema/unused}: A key, fact, setting or policy is declared but never used.
   */
  public static final String LINT_SCHEMA_UNUSED = "schema/unused";

  /**
   * Lint rule {@code values/no-op}: An authored value equals the identity of its key's strategy.
   */
  public static final String LINT_VALUES_NO_OP = "values/no-op";

  private static final Set<String> ENGINE = set(
      E_AXIS_COMBINE_INVALID, E_BINDING_NOT_ALLOWED, E_DEFAULT_NOT_ALLOWED, E_DENY_BOUND_REQUIRED, E_DENY_NOT_NARROWING, E_DEPTH_UNSUPPORTED, E_DUPLICATE_DEFINITION, E_DUPLICATE_KEY, E_DUPLICATE_NODE, E_DUPLICATE_RULE_ID, E_DUPLICATE_VALUE, E_EFFECT_MISMATCH, E_ENTITY_GRAPH, E_EXAMPLE_FAILED, E_FACT_NOT_IN_SCOPE, E_ILLEGAL_FACT_VALUE, E_INTEGER_RANGE, E_INVALID_DECLARATION, E_INVALID_NODE_ID, E_INVALID_SETTING, E_KIND_DEFINITION, E_KIND_VIOLATION, E_MANIFEST, E_MISSING_AXIS_COMBINE, E_MISSING_DEFAULT, E_MISSING_SOURCE_FAILURE, E_NAMESPACE_VIOLATION, E_NOT_IN_UNIVERSE, E_NOT_SETTABLE_HERE, E_OUT_OF_RANGE, E_PARSE, E_PRESET, E_QUANTIFIER_NOT_ALLOWED, E_QUANTIFIER_REQUIRED, E_ROOT_BINDING_MISSING, E_ROOT_BINDING_NOT_ALLOWED, E_SCOPE_DECLARATION, E_SOURCE_MAPPING, E_STRATEGY_TYPE, E_TYPE_MISMATCH, E_TYPE_REQUIRED, E_UNKNOWN_ENTITY, E_UNKNOWN_FACT, E_UNKNOWN_FEATURE, E_UNKNOWN_KEY, E_UNKNOWN_KIND, E_UNKNOWN_NODE, E_UNKNOWN_POLICY, E_UNKNOWN_SCOPE, E_UNKNOWN_SETTING, E_YAML_FEATURE);

  private static final Set<String> EVALUATION = set(
      E_BUNDLE_INTEGRITY, E_BUNDLE_UNSUPPORTED, E_CONTEXT_INVALID, E_EVALUATOR_OPTIONS, E_FACTS_INVALID,
      E_PROVIDER_MISSING, E_PROVIDER_REQUIRED, E_UNKNOWN_SCOPE, E_UNKNOWN_NODE, E_UNKNOWN_KEY,
      E_UNKNOWN_FEATURE);

  private static final Set<String> LINT = set(
      LINT_EXAMPLES_MIXED_COLLECTION, LINT_EXAMPLES_REQUIRED_PER_BINDING, LINT_PRESETS_OVERRIDE,
      LINT_RULES_DEAD_RULE, LINT_SCHEMA_DESCRIPTION_REQUIRED, LINT_SCHEMA_OWNER_REQUIRED,
      LINT_SCHEMA_UNUSED, LINT_VALUES_NO_OP);

  private static final Set<String> ALL;

  static {
    Set<String> all = new LinkedHashSet<>(ENGINE);
    all.addAll(EVALUATION);
    all.add(E_PROVENANCE_INVALID);
    all.addAll(LINT);
    ALL = Collections.unmodifiableSet(all);
  }

  private Code() {
  }

  private static Set<String> set(String... codes) {
    return Collections.unmodifiableSet(new LinkedHashSet<>(java.util.List.of(codes)));
  }

  /**
   * Looks up a code or lint rule identifier.
   *
   * @param name a code such as {@code E_PARSE}, a lint rule such as {@code values/no-op}, or a naming
   *     rule {@code naming/<name>}
   * @return the identifier, or empty when the name is not in the catalogue
   */
  public static Optional<String> of(String name) {
    if (name == null) {
      return Optional.empty();
    }
    if (ALL.contains(name) || isNamingRule(name)) {
      return Optional.of(name);
    }
    return Optional.empty();
  }

  /**
   * Returns the identifier of the naming rule for a pattern.
   *
   * @param patternName the name of the naming pattern, without {@code /} or {@code *}
   * @return {@code naming/<name>}
   * @throws IllegalArgumentException if the name is empty or contains {@code /}, {@code *} or white space
   */
  public static String naming(String patternName) {
    String id = NAMING_PREFIX + patternName;
    if (!isNamingRule(id)) {
      throw new IllegalArgumentException("not a naming pattern name: " + patternName);
    }
    return id;
  }

  /**
   * Returns every code and fixed lint rule identifier, in catalogue order.
   *
   * @return an unmodifiable set; it does not contain the naming rules
   */
  public static Set<String> all() {
    return ALL;
  }

  /**
   * Returns the engine error codes.
   *
   * @return an unmodifiable set
   */
  public static Set<String> engine() {
    return ENGINE;
  }

  /**
   * Returns the evaluation error codes that an evaluation operation may raise (EVAL-38).
   *
   * @return an unmodifiable set of the eleven codes
   */
  public static Set<String> evaluation() {
    return EVALUATION;
  }

  /**
   * Returns the fixed lint rule identifiers.
   *
   * @return an unmodifiable set; it does not contain the naming rules
   */
  public static Set<String> lint() {
    return LINT;
  }

  private static boolean isNamingRule(String name) {
    if (!name.startsWith(NAMING_PREFIX) || name.length() == NAMING_PREFIX.length()) {
      return false;
    }
    for (int i = NAMING_PREFIX.length(); i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '/' || c == '*' || Character.isWhitespace(c)) {
        return false;
      }
    }
    return true;
  }
}
