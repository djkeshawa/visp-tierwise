package io.github.tierwise.spring;

/**
 * Receives the operational events of the starter. The Micrometer implementation is created
 * automatically when Micrometer and a {@code MeterRegistry} bean are present. An application can publish
 * its own bean to report the events elsewhere; the auto-configured one then backs off.
 *
 * <p>Implementations must be thread-safe and must not throw; the starter ignores an exception thrown by
 * an observer.
 */
public interface TierwiseObserver {

  /** An observer that ignores every event. */
  TierwiseObserver NOOP = new TierwiseObserver() {
  };

  /**
   * Called once the {@link TierwiseEvaluators} has its first bundle, before any other event. An
   * implementation can register gauges that read the staleness.
   *
   * @param evaluators the evaluators that were just started
   */
  default void onStarted(TierwiseEvaluators evaluators) {
  }

  /**
   * Called when a bundle becomes the one in use: at startup, and at each swap.
   *
   * @param bundleVersion the {@code bundleVersion} of the new bundle
   */
  default void onBundleActivated(String bundleVersion) {
  }

  /** Called when a refresh fails and the current bundle stays in use. */
  default void onRefreshFailure() {
  }

  /**
   * Called for each key whose provider call failed during an evaluation.
   *
   * @param level the service-sourced level
   * @param outcome {@code unavailable} when the provider threw or timed out, {@code invalid} when it
   *     returned an invalid value
   */
  default void onProviderFailure(String level, String outcome) {
  }

  /**
   * Called after each decision.
   *
   * @param feature the decided feature
   * @param reason the type of the decision's reason: {@code RULE}, {@code DEFAULT} or {@code MISSING_FACT}
   */
  default void onDecision(String feature, String reason) {
  }
}
