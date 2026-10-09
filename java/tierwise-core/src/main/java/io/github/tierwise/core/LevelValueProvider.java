package io.github.tierwise.core;

import java.util.Map;

/**
 * Supplies the values of one service-sourced level for one node (SVC-13 to SVC-37).
 *
 * <p>The evaluator calls the provider at most once per node and call, never for {@code decide}. A
 * provider that throws, or that exceeds its timeout, makes the keys settable at that level an
 * {@code UNAVAILABLE} source failure; the call itself does not fail (EVAL-39).
 */
@FunctionalInterface
public interface LevelValueProvider {

  /**
   * Returns the values for a node.
   *
   * @param nodeId the node ID that the context supplies for the level
   * @return key names mapped to plain values; a key that the map omits contributes nothing
   * @throws Exception to report the node as unavailable
   */
  Map<String, Object> valuesFor(String nodeId) throws Exception;
}
