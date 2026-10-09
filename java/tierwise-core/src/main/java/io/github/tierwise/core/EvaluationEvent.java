package io.github.tierwise.core;

import java.util.Objects;
import java.util.Optional;

/**
 * An event for an {@link EvaluationListener} (EVAL-53).
 *
 * @param type what happened
 * @param bundleVersion the digest of the bundle the evaluator was created for
 * @param level the service-sourced level the event concerns, when it concerns one
 * @param node the node ID the event concerns, when it concerns one
 * @param key the key the event concerns, when it concerns one
 */
public record EvaluationEvent(
    Type type, String bundleVersion, Optional<String> level, Optional<String> node, Optional<String> key) {

  /** The kinds of event (SVC-43 to SVC-47, EVAL-43). */
  public enum Type {
    /** An evaluator was created for a bundle (EVAL-43). */
    BUNDLE_LOADED,
    /** A provider threw, rejected or timed out for a key (SVC-23, SVC-44). */
    SOURCE_UNAVAILABLE,
    /** A provider returned an invalid value for a key (SVC-34). */
    SOURCE_INVALID,
    /** A provider returned an entry for a key the bundle does not declare (SVC-27, SVC-45). */
    UNKNOWN_KEY,
    /** A provider returned an entry for a key that is not settable at the level (SVC-29, SVC-47). */
    IGNORED_ENTRY
  }

  /** Validates the components. */
  public EvaluationEvent {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(bundleVersion, "bundleVersion");
    Objects.requireNonNull(level, "level");
    Objects.requireNonNull(node, "node");
    Objects.requireNonNull(key, "key");
  }

  /**
   * Creates the event for a created evaluator.
   *
   * @param bundleVersion the bundle digest
   * @return the event
   */
  public static EvaluationEvent loaded(String bundleVersion) {
    return new EvaluationEvent(Type.BUNDLE_LOADED, bundleVersion, Optional.empty(), Optional.empty(), Optional.empty());
  }

  /**
   * Creates an event that concerns one key at one node of a level.
   *
   * @param type the event type
   * @param bundleVersion the bundle digest
   * @param level the level name
   * @param node the node ID
   * @param key the key name
   * @return the event
   */
  public static EvaluationEvent forKey(Type type, String bundleVersion, String level, String node, String key) {
    return new EvaluationEvent(type, bundleVersion, Optional.of(level), Optional.of(node), Optional.of(key));
  }
}
