package io.github.visplabs.tierwise.spring;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.Decision;
import io.github.visplabs.tierwise.core.FactSet;
import java.util.Objects;
import org.apache.commons.logging.LogFactory;

/**
 * The {@link DecisionEngine} over the evaluator that is current at the time of each call. It counts and
 * logs each decision.
 */
final class DefaultDecisionEngine implements DecisionEngine {

  private final TierwiseEvaluators evaluators;
  private final TierwiseObserver observer;
  private final DecisionLogger logger;

  DefaultDecisionEngine(TierwiseEvaluators evaluators, TierwiseObserver observer, DecisionLogger logger) {
    this.evaluators = Objects.requireNonNull(evaluators, "evaluators");
    this.observer = Objects.requireNonNull(observer, "observer");
    this.logger = Objects.requireNonNull(logger, "logger");
  }

  @Override
  public Decision decide(Context context, String feature, FactSet facts) {
    Decision decision = evaluators.current().decide(context, feature, facts);
    try {
      observer.onDecision(decision.feature(), decision.reason().type().name());
      logger.log(decision);
    } catch (RuntimeException e) {
      LogFactory.getLog(DefaultDecisionEngine.class).warn("Tierwise decision reporting failed: " + e);
    }
    return decision;
  }
}
