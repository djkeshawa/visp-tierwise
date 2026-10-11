package io.github.visplabs.tierwise.core.internal.eval;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.Resolved;
import io.github.visplabs.tierwise.core.TierwiseException;
import io.github.visplabs.tierwise.core.internal.model.Model;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The resolve operation and its synchronous variant (EVAL-7, EVAL-9 to EVAL-12, EVAL-16).
 *
 * <p>The steps run in this order, and the first failing step decides the error (EVAL-18, EVAL-20):
 * context validation; key lookup; for the variant that never calls a provider, the check that no call is
 * needed; the provider calls; combination and provenance. A request for several keys is one evaluation
 * call, so each service-sourced level is called at most once for the whole request (SVC-20).
 */
final class Resolver {

  private final Model model;
  private final ContextValidator validator;
  private final ProviderCalls providers;
  private final KeyResolution resolution;

  /**
   * Creates the resolver of an evaluator.
   *
   * @param model the model of the bundle
   * @param bundleVersion the digest of the bundle
   * @param validator the context validator
   * @param providers the provider calls
   */
  Resolver(Model model, String bundleVersion, ContextValidator validator, ProviderCalls providers) {
    this.model = model;
    this.validator = validator;
    this.providers = providers;
    this.resolution = new KeyResolution(model, bundleVersion);
  }

  /**
   * Resolves one key, calling providers where EVAL-7 allows.
   *
   * @param context the context; {@code null} counts as empty (EVAL-54)
   * @param key the key name; an omitted or empty name is an unknown key (EVAL-37)
   * @return the result
   */
  Resolved resolve(Context context, String key) {
    return resolveMany(context, Collections.singletonList(key), true).get(0);
  }

  /**
   * Resolves one key and never calls a provider (EVAL-9, EVAL-11).
   *
   * @param context the context; {@code null} counts as empty
   * @param key the key name
   * @return the result
   * @throws TierwiseException with {@code E_PROVIDER_REQUIRED} when a provider call would be needed
   */
  Resolved resolveLocal(Context context, String key) {
    return resolveMany(context, Collections.singletonList(key), false).get(0);
  }

  /**
   * Resolves several keys as one evaluation call (SVC-20).
   *
   * @param context the context; {@code null} counts as empty
   * @param keys the key names, in the order the results are returned
   * @param callProviders whether providers may be called; when {@code false}, a needed call raises
   *     {@code E_PROVIDER_REQUIRED}
   * @return one result for each requested key, in request order
   */
  List<Resolved> resolveMany(Context context, List<String> keys, boolean callProviders) {
    AdmittedContext admitted = validator.validate(context);
    Set<String> requested = new LinkedHashSet<>();
    for (String key : keys) {
      if (key == null || !model.keys().containsKey(key)) {
        throw new TierwiseException(Code.E_UNKNOWN_KEY, "no such key: " + key);
      }
      requested.add(key);
    }
    if (!callProviders && !providers.neededLevels(admitted, requested).isEmpty()) {
      throw new TierwiseException(Code.E_PROVIDER_REQUIRED, "resolving needs a provider call");
    }
    ProviderOutcomes outcomes = callProviders ? providers.call(admitted, requested) : ProviderOutcomes.NONE;
    List<Resolved> results = new ArrayList<>();
    for (String key : keys) {
      results.add(resolution.resolve(key, admitted, outcomes));
    }
    return results;
  }
}
