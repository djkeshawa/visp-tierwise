package io.github.visplabs.tierwise.core.internal.eval;

import io.github.visplabs.tierwise.core.EvaluationEvent;
import io.github.visplabs.tierwise.core.EvaluationListener;

/**
 * Delivers listener events (EVAL-41 to EVAL-45, EVAL-53).
 *
 * <p>Events are delivered on the calling thread, before the operation returns. A listener that throws is
 * ignored: it changes no result and raises no error (EVAL-44). Calls are not serialised.
 */
final class EventSink {

  private final EvaluationListener listener;
  private final String bundleVersion;

  /**
   * Creates a sink.
   *
   * @param listener the listener, or {@code null} when none is configured
   * @param bundleVersion the digest of the bundle, carried by every event
   */
  EventSink(EvaluationListener listener, String bundleVersion) {
    this.listener = listener;
    this.bundleVersion = bundleVersion;
  }

  /** Reports the load event of a created evaluator (EVAL-43). */
  void loaded() {
    deliver(EvaluationEvent.loaded(bundleVersion));
  }

  /**
   * Reports a provider event for one key.
   *
   * @param type the event type
   * @param level the service-sourced level
   * @param node the node ID that was passed to the provider
   * @param key the key name
   */
  void forKey(EvaluationEvent.Type type, String level, String node, String key) {
    if (listener != null) {
      deliver(EvaluationEvent.forKey(type, bundleVersion, level, node, key));
    }
  }

  private void deliver(EvaluationEvent event) {
    if (listener == null) {
      return;
    }
    try {
      listener.onEvent(event);
    } catch (RuntimeException | LinkageError | AssertionError e) {
      // A listener that throws is ignored (EVAL-44).
    }
  }
}
