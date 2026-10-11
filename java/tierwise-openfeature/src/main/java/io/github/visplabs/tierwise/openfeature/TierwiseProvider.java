package io.github.visplabs.tierwise.openfeature;

import dev.openfeature.sdk.EvaluationContext;
import dev.openfeature.sdk.FeatureProvider;
import dev.openfeature.sdk.Metadata;
import dev.openfeature.sdk.ProviderEvaluation;
import dev.openfeature.sdk.Value;
import io.github.visplabs.tierwise.core.Evaluator;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * An OpenFeature {@link FeatureProvider} that answers flag evaluations from a Tierwise {@link Evaluator}.
 *
 * <h2>Flags</h2>
 *
 * <p>A flag key is the declared name of a Tierwise key or feature. The type of the evaluation decides what
 * the flag can be:
 *
 * <ul>
 *   <li>boolean: a boolean key;
 *   <li>string: a string, enum or decimal key, or a feature with an enum effect;
 *   <li>integer: an integer key within the {@code int} range, otherwise a {@code TYPE_MISMATCH}
 *       resolution error; the long evaluation of the SDK reads the same keys through the double
 *       evaluation;
 *   <li>double: an integer key that a {@code double} holds exactly (magnitude up to 2^53);
 *   <li>object: a set, list or object key as a list or structure {@link Value}, or a feature with an
 *       object effect.
 * </ul>
 *
 * <p>A name is looked up as a key first and as a feature second. A name that is both a key and a feature
 * therefore always reads the key.
 *
 * <h2>Evaluation context</h2>
 *
 * <p>Tierwise levels and axes come from the structure attributes {@link #LEVELS_ATTRIBUTE} and {@link
 * #AXES_ATTRIBUTE}, which map names to node IDs. The facts of a decision come from the structure attribute
 * {@link #FACTS_ATTRIBUTE}, with the optional members {@code facts} and {@code children}. The targeting key
 * and every other attribute are ignored. OpenFeature merges contexts attribute by attribute, so an
 * attribute set at a more specific layer replaces the same attribute from a less specific one as a whole.
 *
 * <h2>Results</h2>
 *
 * <p>The reason is {@code STATIC}, {@code DEFAULT} or {@code TARGETING_MATCH}, or {@code ERROR} with an
 * error code; the flag metadata holds the entries of {@link TierwiseMetadata}. A key that has no value, and
 * a feature whose effect is {@code null}, resolve to the caller's default value with reason
 * {@code DEFAULT}. {@code docs/openfeature-mapping.md} is the full mapping.
 *
 * <p>The provider is stateless and thread-safe when its supplier is.
 */
public final class TierwiseProvider implements FeatureProvider {

  /** The provider name reported in {@link #getMetadata()}. */
  public static final String PROVIDER_NAME = "Tierwise";

  /** The structure attribute that maps non-root level names to node IDs. */
  public static final String LEVELS_ATTRIBUTE = "tierwise.levels";

  /** The structure attribute that maps axis names to node IDs. */
  public static final String AXES_ATTRIBUTE = "tierwise.axes";

  /** The structure attribute with the facts of a decision: optional {@code facts} and {@code children}. */
  public static final String FACTS_ATTRIBUTE = "tierwise.facts";

  private static final Metadata METADATA = () -> PROVIDER_NAME;

  private final Supplier<? extends Evaluator> evaluators;

  /**
   * Creates a provider that reads the current evaluator from a supplier on every evaluation. Use it with a
   * source that swaps bundles, such as the evaluators of the Spring Boot starter. A supplier that returns
   * {@code null} makes the evaluation fail with {@code PROVIDER_NOT_READY}.
   *
   * @param evaluators the supplier of the current evaluator
   */
  public TierwiseProvider(Supplier<? extends Evaluator> evaluators) {
    this.evaluators = Objects.requireNonNull(evaluators, "evaluators");
  }

  /**
   * Creates a provider over one fixed evaluator.
   *
   * @param evaluator the evaluator
   * @return the provider
   */
  public static TierwiseProvider of(Evaluator evaluator) {
    Objects.requireNonNull(evaluator, "evaluator");
    return new TierwiseProvider(() -> evaluator);
  }

  @Override
  public Metadata getMetadata() {
    return METADATA;
  }

  @Override
  public ProviderEvaluation<Boolean> getBooleanEvaluation(String key, Boolean defaultValue, EvaluationContext ctx) {
    return evaluate(key, defaultValue, ctx, Target.BOOLEAN);
  }

  @Override
  public ProviderEvaluation<String> getStringEvaluation(String key, String defaultValue, EvaluationContext ctx) {
    return evaluate(key, defaultValue, ctx, Target.STRING);
  }

  @Override
  public ProviderEvaluation<Integer> getIntegerEvaluation(String key, Integer defaultValue, EvaluationContext ctx) {
    return evaluate(key, defaultValue, ctx, Target.INTEGER);
  }

  @Override
  public ProviderEvaluation<Double> getDoubleEvaluation(String key, Double defaultValue, EvaluationContext ctx) {
    return evaluate(key, defaultValue, ctx, Target.DOUBLE);
  }

  @Override
  public ProviderEvaluation<Value> getObjectEvaluation(String key, Value defaultValue, EvaluationContext ctx) {
    return evaluate(key, defaultValue, ctx, Target.OBJECT);
  }

  private <T> ProviderEvaluation<T> evaluate(String key, T defaultValue, EvaluationContext ctx, Target<T> target) {
    Evaluator evaluator = evaluators.get();
    if (evaluator == null) {
      return Errors.notReady(defaultValue);
    }
    return FlagEvaluation.evaluate(evaluator, key, defaultValue, ctx, target);
  }
}
