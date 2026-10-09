// The names and constants of the OpenFeature mapping (docs/openfeature-mapping.md). They are the same for the
// server provider and the web provider, and they match the Java provider.

/** The provider name reported in `metadata.name`. */
export const PROVIDER_NAME = 'Tierwise';

/** The structure attribute that maps non-root level names to node IDs. */
export const LEVELS_ATTRIBUTE = 'tierwise.levels';

/** The structure attribute that maps axis names to node IDs. */
export const AXES_ATTRIBUTE = 'tierwise.axes';

/** The structure attribute with the facts of a decision: optional `facts` and `children` members. */
export const FACTS_ATTRIBUTE = 'tierwise.facts';

/**
 * The names of the flag metadata entries that the providers set, and the values of `flagKind`. Every value is a
 * string or a boolean. Structured entries are compact JSON strings.
 */
export const TierwiseMetadata = Object.freeze({
  /** The digest of the bundle that produced the result (string). */
  BUNDLE_VERSION: 'bundleVersion',
  /** `key` or `feature` (string). */
  FLAG_KIND: 'flagKind',
  /** The `flagKind` value for a key. */
  KEY: 'key',
  /** The `flagKind` value for a feature. */
  FEATURE: 'feature',
  /** The merge strategy of a key (string). Keys only. */
  STRATEGY: 'strategy',
  /** The name of the kind a key opts into (string). Keys only, when the key opts into one. */
  KIND: 'kind',
  /** `DEFAULT` or `<scope>:<name>` (string). Keys with the strategy `override` or `deep_merge` only. */
  SOURCE: 'source',
  /** The node of the scope in `source` (string); absent for `DEFAULT` and for the root level. */
  SOURCE_NODE: 'sourceNode',
  /**
   * The contribution summary (string): a JSON array with one object for each contribution entry, in
   * evaluation-path order, with the members `scope`, `name`, `node` (when present), `effect` and
   * `onSourceFailure` (when present). Contributed values are not included. Keys only.
   */
  CONTRIBUTIONS: 'contributions',
  /** The scopes that admission skipped (string): a JSON array, present only when non-empty. */
  SKIPPED_SCOPES: 'skippedScopes',
  /** `true` when a `min` or `max` key has no value because no scope contributed. */
  VALUE_ABSENT: 'valueAbsent',
  /** `RULE`, `DEFAULT` or `MISSING_FACT` (string). Features only. */
  DECISION_REASON: 'decisionReason',
  /** The id of the winning rule (string). Present when the decision reason is `RULE`. */
  RULE_ID: 'ruleId',
  /** The name of the winning rule (string). Present when the decision reason is `RULE`. */
  RULE_NAME: 'ruleName',
  /** The missing fact names (string): a JSON array. Present when the decision reason is `MISSING_FACT`. */
  MISSING_FACTS: 'missingFacts',
  /** The policy of the selected binding (string). Features only. */
  POLICY: 'policy',
  /** The level of the selected binding (string). Features only. */
  BINDING_LEVEL: 'bindingLevel',
  /** The node of the selected binding (string). Features only, for a non-root level. */
  BINDING_NODE: 'bindingNode',
  /** `true` when a feature decided the null effect of a nullable enum, which OpenFeature cannot carry. */
  EFFECT_NULL: 'effectNull',
  /** The Tierwise error code, such as `E_UNKNOWN_KEY` (string). Present when a Tierwise error caused the error. */
  TIERWISE_CODE: 'tierwiseCode',
} as const);
