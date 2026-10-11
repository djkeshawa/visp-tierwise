package io.github.visplabs.tierwise.core.internal.eval;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.Decision;
import io.github.visplabs.tierwise.core.Evaluator;
import io.github.visplabs.tierwise.core.EvaluatorOptions;
import io.github.visplabs.tierwise.core.FactSet;
import io.github.visplabs.tierwise.core.LoadedBundle;
import io.github.visplabs.tierwise.core.Resolved;
import io.github.visplabs.tierwise.core.internal.model.LoadedModel;
import io.github.visplabs.tierwise.core.internal.model.Model;
import java.util.List;
import java.util.Map;

/**
 * The evaluator of one loaded bundle (EVAL-4 to EVAL-12).
 *
 * <p>An instance is immutable: its bundle, providers, timeouts and listener are fixed when it is created
 * (EVAL-48), and it keeps no state between calls, so concurrent calls do not influence one another
 * (EVAL-51, EVAL-52). It never modifies the bundle or its inputs (EVAL-46, EVAL-49), and everything it
 * returns is a fresh immutable copy (EVAL-47, EVAL-50).
 */
public final class EvaluatorImpl implements Evaluator {

  private final Resolver resolver;
  private final DecisionEngine decisions;

  private EvaluatorImpl(Resolver resolver, DecisionEngine decisions) {
    this.resolver = resolver;
    this.decisions = decisions;
  }

  /**
   * Creates an evaluator after the checks of EVAL-15. It calls no provider (EVAL-6), and it reports one
   * load event to the listener, if any, only when it returns (EVAL-43).
   *
   * @param bundle a bundle returned by the bundle loader
   * @param options the options; an omitted argument counts as empty options (EVAL-28)
   * @return the evaluator
   * @throws io.github.visplabs.tierwise.core.TierwiseException with {@code E_EVALUATOR_OPTIONS} or
   *     {@code E_PROVIDER_MISSING}
   */
  public static Evaluator create(LoadedBundle bundle, EvaluatorOptions options) {
    EvaluatorOptions supplied = options == null ? EvaluatorOptions.none() : options;
    LoadedModel loaded = (LoadedModel) bundle;
    Model model = loaded.model();
    Map<String, ProviderCalls.Registration> registrations = ProviderRegistrations.check(model, supplied);
    EventSink events = new EventSink(supplied.listener().orElse(null), loaded.bundleVersion());
    ContextValidator validator = new ContextValidator(model);
    ProviderCalls providers = new ProviderCalls(model, registrations, events);
    Resolver resolver = new Resolver(model, loaded.bundleVersion(), validator, providers);
    DecisionEngine decisions = new DecisionEngine(model, loaded.bundleVersion(), validator);
    events.loaded();
    return new EvaluatorImpl(resolver, decisions);
  }

  @Override
  public Resolved resolve(Context context, String key) {
    return resolver.resolve(context, key);
  }

  @Override
  public Resolved resolveLocal(Context context, String key) {
    return resolver.resolveLocal(context, key);
  }

  @Override
  public Decision decide(Context context, String feature, FactSet facts) {
    return decisions.decide(context, feature, facts);
  }

  /**
   * Resolves several keys as one evaluation call (SVC-20): each service-sourced level is called at most
   * once for the whole request. This is the contract that a multi-key request such as a server's
   * {@code keys[]} relies on; the public {@link Evaluator} interface resolves one key at a time.
   *
   * @param context the context; {@code null} counts as empty
   * @param keys the key names, in the order of the results
   * @return one result for each key
   */
  public List<Resolved> resolveAll(Context context, List<String> keys) {
    return resolver.resolveMany(context, keys, true);
  }
}
