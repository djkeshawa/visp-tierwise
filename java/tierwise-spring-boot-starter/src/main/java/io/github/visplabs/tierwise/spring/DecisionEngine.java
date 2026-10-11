package io.github.visplabs.tierwise.spring;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.Decision;
import io.github.visplabs.tierwise.core.FactSet;
import io.github.visplabs.tierwise.core.TierwiseException;

/**
 * Decides features against the bundle currently in use. Each decision is counted in the {@code
 * tierwise.decisions} meter and logged with its bundle version at {@code tierwise.decision-log-level}.
 */
public interface DecisionEngine {

  /**
   * Decides a feature for one entity. No provider is called.
   *
   * @param context the context; {@code null} counts as empty
   * @param feature the declared feature name
   * @param facts the facts of the decided entity and its children; {@code null} counts as empty
   * @return the effect with its provenance
   * @throws TierwiseException with a code from EVAL-30 to EVAL-33, EVAL-35 or EVAL-36
   */
  Decision decide(Context context, String feature, FactSet facts);
}
