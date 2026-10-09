package io.github.tierwise.core.internal.eval;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.Model;
import io.github.tierwise.core.internal.model.ScopeDef;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The create-evaluator checks of EVAL-15, applied in this order; the first failing check decides the
 * code (EVAL-20):
 *
 * <ol>
 *   <li>the options, including each timeout ([EVAL-28], [SVC-38], [SVC-48]): {@code E_EVALUATOR_OPTIONS};
 *   <li>the provider registrations ([EVAL-27], [SVC-14]): a level that the bundle does not declare, a
 *       declared level that is not service-sourced, or a level registered twice raises
 *       {@code E_EVALUATOR_OPTIONS};
 *   <li>the provider check of [SVC-14] ([EVAL-26]): a service-sourced level without a provider raises
 *       {@code E_PROVIDER_MISSING}, whether or not any key is settable there and whether or not the
 *       context will name it.
 * </ol>
 *
 * <p>A timeout is valid only when it is a positive whole number of milliseconds no greater than 2^53-1, the
 * integer range of TYPE-18 (SVC-41, ADR 0025); the evaluator supplies no default (SVC-48).
 */
final class ProviderRegistrations {

  /** The largest valid timeout: 2^53-1 milliseconds (SVC-41). */
  private static final Duration MAX_TIMEOUT = Duration.ofMillis(9_007_199_254_740_991L);

  private ProviderRegistrations() {
  }

  /**
   * Checks the options against the bundle and returns the providers by level.
   *
   * @param model the model of the bundle
   * @param options the options
   * @return one registration for each service-sourced level
   * @throws TierwiseException with {@code E_EVALUATOR_OPTIONS} or {@code E_PROVIDER_MISSING}
   */
  static Map<String, ProviderCalls.Registration> check(Model model, EvaluatorOptions options) {
    for (EvaluatorOptions.ProviderRegistration registration : options.providers()) {
      checkTimeout(registration);
    }
    Map<String, ProviderCalls.Registration> byLevel = new LinkedHashMap<>();
    for (EvaluatorOptions.ProviderRegistration registration : options.providers()) {
      checkLevel(model.hierarchy(), registration.level());
      ProviderCalls.Registration added = new ProviderCalls.Registration(registration.provider(),
          millis(registration.timeout()));
      if (byLevel.put(registration.level(), added) != null) {
        throw options("a provider is registered twice for the level " + registration.level());
      }
    }
    for (Map.Entry<String, ScopeDef> scope : model.hierarchy().scopes().entrySet()) {
      if (scope.getValue().isServiceSourced() && !byLevel.containsKey(scope.getKey())) {
        throw new TierwiseException(Code.E_PROVIDER_MISSING,
            "no provider is registered for the service-sourced level " + scope.getKey());
      }
    }
    return byLevel;
  }

  private static void checkTimeout(EvaluatorOptions.ProviderRegistration registration) {
    Duration timeout = registration.timeout();
    if (timeout == null) {
      throw options("the provider for " + registration.level() + " has no timeout");
    }
    boolean wholeMillis = timeout.getNano() % 1_000_000 == 0;
    if (timeout.isNegative() || timeout.isZero() || !wholeMillis || timeout.compareTo(MAX_TIMEOUT) > 0) {
      throw options("the timeout of the provider for " + registration.level()
          + " is not a positive whole number of milliseconds no greater than 2^53-1");
    }
  }

  private static void checkLevel(Hierarchy hierarchy, String level) {
    ScopeDef scope = hierarchy.scope(level);
    boolean declared = hierarchy.isLevel(level) || hierarchy.isAxis(level);
    if (!declared) {
      throw options("the bundle does not declare the level " + level);
    }
    if (scope == null || !scope.isServiceSourced()) {
      throw options("the level " + level + " is not service-sourced");
    }
  }

  private static long millis(Duration timeout) {
    try {
      return timeout.toMillis();
    } catch (ArithmeticException e) {
      return Long.MAX_VALUE;
    }
  }

  private static TierwiseException options(String message) {
    return new TierwiseException(Code.E_EVALUATOR_OPTIONS, message);
  }
}
