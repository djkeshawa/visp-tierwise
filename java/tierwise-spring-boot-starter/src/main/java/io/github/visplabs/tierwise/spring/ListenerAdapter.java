package io.github.visplabs.tierwise.spring;

import io.github.visplabs.tierwise.core.EvaluationEvent;
import io.github.visplabs.tierwise.core.EvaluationListener;
import java.util.List;
import java.util.Objects;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * The evaluator's listener. It reports provider failures to the {@link TierwiseObserver}, logs the other
 * provider events at debug level, and forwards every event to the application's own listeners. A listener
 * that throws does not stop the others.
 */
final class ListenerAdapter implements EvaluationListener {

  private static final Log LOG = LogFactory.getLog(ListenerAdapter.class);

  private final TierwiseObserver observer;
  private final List<EvaluationListener> listeners;

  ListenerAdapter(TierwiseObserver observer, List<EvaluationListener> listeners) {
    this.observer = Objects.requireNonNull(observer, "observer");
    this.listeners = List.copyOf(listeners);
  }

  @Override
  public void onEvent(EvaluationEvent event) {
    try {
      switch (event.type()) {
        case SOURCE_UNAVAILABLE -> observer.onProviderFailure(event.level().orElse(""), "unavailable");
        case SOURCE_INVALID -> observer.onProviderFailure(event.level().orElse(""), "invalid");
        case UNKNOWN_KEY, IGNORED_ENTRY -> LOG.debug("Tierwise provider event " + event.type() + " level="
            + event.level().orElse("") + " key=" + event.key().orElse(""));
        case BUNDLE_LOADED -> LOG.debug("Tierwise evaluator created for bundle " + event.bundleVersion());
      }
    } catch (RuntimeException e) {
      LOG.warn("Tierwise observer failed: " + e);
    }
    for (EvaluationListener listener : listeners) {
      try {
        listener.onEvent(event);
      } catch (RuntimeException e) {
        LOG.warn("An EvaluationListener failed: " + e);
      }
    }
  }
}
