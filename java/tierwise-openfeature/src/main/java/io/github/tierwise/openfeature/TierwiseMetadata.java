package io.github.tierwise.openfeature;

/**
 * The names of the flag metadata entries that {@link TierwiseProvider} sets, and the values of the
 * {@link #FLAG_KIND} entry. {@code docs/openfeature-mapping.md} defines each entry.
 *
 * <p>Every value is a string or a boolean, because OpenFeature flag metadata holds nothing richer.
 * Structured entries are compact JSON strings.
 */
public final class TierwiseMetadata {

  /** The digest of the bundle that produced the result (string). Present on every result that has a bundle. */
  public static final String BUNDLE_VERSION = "bundleVersion";

  /** Whether the flag named a key or a feature: {@link #KEY} or {@link #FEATURE} (string). */
  public static final String FLAG_KIND = "flagKind";

  /** The {@link #FLAG_KIND} value for a key. */
  public static final String KEY = "key";

  /** The {@link #FLAG_KIND} value for a feature. */
  public static final String FEATURE = "feature";

  /** The merge strategy of a key (string). Keys only. */
  public static final String STRATEGY = "strategy";

  /** The name of the kind a key opts into (string). Keys only, and only when the key opts into one. */
  public static final String KIND = "kind";

  /**
   * The scope that supplied the value of an {@code override} or {@code deep_merge} key (string):
   * {@code DEFAULT}, or {@code <scope>:<name>}, such as {@code level:division}. Keys only.
   */
  public static final String SOURCE = "source";

  /** The node of the scope in {@link #SOURCE} (string); absent for {@code DEFAULT} and for the root level. */
  public static final String SOURCE_NODE = "sourceNode";

  /**
   * The contribution summary (string): a JSON array with one object for each contribution entry, in
   * evaluation-path order, with the members {@code scope}, {@code name}, {@code node} (when present),
   * {@code effect} and {@code onSourceFailure} (when present). Contributed values are not included. Keys
   * only.
   */
  public static final String CONTRIBUTIONS = "contributions";

  /** The scopes that admission skipped (string): a JSON array of objects, present only when non-empty. */
  public static final String SKIPPED_SCOPES = "skippedScopes";

  /** {@code true} when the key has no value because no scope contributed to a {@code min} or {@code max} key. */
  public static final String VALUE_ABSENT = "valueAbsent";

  /** The decision reason type (string): {@code RULE}, {@code DEFAULT} or {@code MISSING_FACT}. Features only. */
  public static final String DECISION_REASON = "decisionReason";

  /** The id of the winning rule (string). Present when the decision reason is {@code RULE}. */
  public static final String RULE_ID = "ruleId";

  /** The name of the winning rule (string). Present when the decision reason is {@code RULE}. */
  public static final String RULE_NAME = "ruleName";

  /** The missing fact names (string): a JSON array. Present when the decision reason is {@code MISSING_FACT}. */
  public static final String MISSING_FACTS = "missingFacts";

  /** The policy of the selected binding (string). Features only. */
  public static final String POLICY = "policy";

  /** The level of the selected binding (string). Features only. */
  public static final String BINDING_LEVEL = "bindingLevel";

  /** The node of the selected binding (string). Features only, and only for a non-root level. */
  public static final String BINDING_NODE = "bindingNode";

  /** {@code true} when a feature decided the null effect of a nullable enum, which OpenFeature cannot carry. */
  public static final String EFFECT_NULL = "effectNull";

  /** The Tierwise error code, such as {@code E_UNKNOWN_KEY} (string). Present when a Tierwise error caused the resolution error. */
  public static final String TIERWISE_CODE = "tierwiseCode";

  private TierwiseMetadata() {
  }
}
