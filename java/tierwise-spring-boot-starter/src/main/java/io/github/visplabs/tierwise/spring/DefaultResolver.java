package io.github.visplabs.tierwise.spring;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.Resolved;
import java.util.Objects;

/** The {@link Resolver} over the evaluator that is current at the time of each call. */
final class DefaultResolver implements Resolver {

  private final TierwiseEvaluators evaluators;

  DefaultResolver(TierwiseEvaluators evaluators) {
    this.evaluators = Objects.requireNonNull(evaluators, "evaluators");
  }

  @Override
  public Resolved resolve(Context context, String key) {
    return evaluators.current().resolve(context, key);
  }

  @Override
  public Resolved resolveLocal(Context context, String key) {
    return evaluators.current().resolveLocal(context, key);
  }
}
