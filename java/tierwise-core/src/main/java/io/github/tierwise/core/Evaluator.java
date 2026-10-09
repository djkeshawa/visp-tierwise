package io.github.tierwise.core;

/**
 * Evaluates keys and features against one loaded bundle (EVAL-7 to EVAL-12).
 *
 * <p>An evaluator is immutable and safe to share between threads (EVAL-48, EVAL-52). A {@code null}
 * context counts as an empty context and a {@code null} fact set as an empty one (EVAL-54). Every
 * method raises a {@link TierwiseException} for a caller error.
 */
public interface Evaluator {

  /**
   * Resolves a key, calling providers where the key is settable at a service-sourced level that the
   * context names (EVAL-7).
   *
   * @param context the context
   * @param key the declared key name
   * @return the resolved value with its provenance
   * @throws TierwiseException with a code from EVAL-30 to EVAL-34
   */
  Resolved resolve(Context context, String key);

  /**
   * Resolves a key and never calls a provider (EVAL-9, EVAL-11). It returns what {@link #resolve} returns
   * for an input that needs no provider call.
   *
   * @param context the context
   * @param key the declared key name
   * @return the resolved value with its provenance
   * @throws TierwiseException with {@code E_PROVIDER_REQUIRED} when a provider call would be needed, or
   *     any other code {@link #resolve} raises
   */
  Resolved resolveLocal(Context context, String key);

  /**
   * Decides a feature for one entity. It calls no provider (EVAL-8).
   *
   * @param context the context
   * @param feature the declared feature name
   * @param facts the facts of the decided entity and its children
   * @return the effect with its provenance
   * @throws TierwiseException with a code from EVAL-30 to EVAL-33, EVAL-35 or EVAL-36
   */
  Decision decide(Context context, String feature, FactSet facts);
}
