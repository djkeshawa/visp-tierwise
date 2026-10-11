package io.github.visplabs.tierwise.core.internal.eval;

import java.util.Map;

/**
 * The outcomes of the provider calls of one evaluation call: for each called level and each key being
 * resolved at it, a value or a source failure. A key that the provider left out, or whose entry was
 * ignored, has no outcome (SVC-30, SVC-36).
 */
final class ProviderOutcomes {

  /** The outcomes of an evaluation call that made no provider call. */
  static final ProviderOutcomes NONE = new ProviderOutcomes(Map.of());

  private final Map<String, Map<String, SourceOutcome>> byLevel;

  ProviderOutcomes(Map<String, Map<String, SourceOutcome>> byLevel) {
    this.byLevel = byLevel;
  }

  /**
   * Returns the outcome for a key at a level.
   *
   * @param level a service-sourced level name
   * @param key a key name
   * @return the outcome, or {@code null} when the level made no contribution for the key
   */
  SourceOutcome get(String level, String key) {
    Map<String, SourceOutcome> keys = byLevel.get(level);
    return keys == null ? null : keys.get(key);
  }
}
