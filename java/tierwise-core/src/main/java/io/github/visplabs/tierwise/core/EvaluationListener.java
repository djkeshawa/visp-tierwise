package io.github.visplabs.tierwise.core;

/**
 * Receives the events that an evaluator reports (EVAL-41 to EVAL-45).
 *
 * <p>Calls are not serialised: an implementation must tolerate concurrent calls (EVAL-53). An exception
 * that a listener throws is ignored.
 */
@FunctionalInterface
public interface EvaluationListener {

  /**
   * Receives one event.
   *
   * @param event the event
   */
  void onEvent(EvaluationEvent event);
}
