package io.github.visplabs.tierwise.core.conformance;

import io.github.visplabs.tierwise.core.LevelValueProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeoutException;

/**
 * Stub providers built from the {@code providers} section of an evaluate vector (VEC-37 to VEC-47).
 *
 * <p>A stub maps node IDs to an outcome: {@code values} returns the mapping unchanged (VEC-42) and
 * {@code fail: unavailable|timeout} makes the provider fail (VEC-43). A {@code timeout} outcome throws at
 * once, without waiting for a wall-clock interval (VEC-45); the evaluator treats every provider failure as
 * an unavailable source (SVC-23, SVC-39). Every invocation is counted, failing ones included (VEC-47).
 * An invocation for a node the stub does not list is recorded as a runner failure (VEC-46).
 */
final class StubProviders {

  private final Map<String, Map<String, Integer>> calls = new TreeMap<>();
  private final List<String> unlistedCalls = new ArrayList<>();

  /**
   * Builds the provider of one level from its stub.
   *
   * @param level the level name
   * @param stub the stub: a mapping from node ID to an outcome mapping
   * @return the provider
   * @throws RunnerFailure if the stub or an outcome has a form that VEC-37 or VEC-41 do not allow
   */
  LevelValueProvider provider(String level, Object stub) {
    Map<String, Object> nodes = VectorFiles.asStringMap(stub, "the stub of level " + level);
    Map<String, Object> outcomes = new LinkedHashMap<>();
    for (Map.Entry<String, Object> node : nodes.entrySet()) {
      outcomes.put(node.getKey(), checkOutcome(level, node.getKey(), node.getValue()));
    }
    return nodeId -> invoke(level, nodeId, outcomes);
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> checkOutcome(String level, String node, Object outcome) {
    String where = "the outcome of " + level + "/" + node;
    Map<String, Object> map = VectorFiles.asStringMap(outcome, where);
    if (map.size() != 1) {
      throw new RunnerFailure(where + " must have exactly one of values and fail (VEC-41)");
    }
    if (map.containsKey("values")) {
      if (!(map.get("values") instanceof Map)) {
        throw new RunnerFailure(where + ": values must be a mapping (VEC-41)");
      }
      VectorFiles.asStringMap(map.get("values"), where + " values");
    } else if (map.containsKey("fail")) {
      Object fail = map.get("fail");
      if (!"unavailable".equals(fail) && !"timeout".equals(fail)) {
        throw new RunnerFailure(where + ": fail must be unavailable or timeout (VEC-43)");
      }
    } else {
      throw new RunnerFailure(where + " must have exactly one of values and fail (VEC-41)");
    }
    return (Map<String, Object>) outcome;
  }

  @SuppressWarnings("unchecked")
  private synchronized Map<String, Object> invoke(String level, String nodeId, Map<String, Object> outcomes)
      throws Exception {
    calls.computeIfAbsent(level, name -> new TreeMap<>()).merge(nodeId, 1, Integer::sum);
    Map<String, Object> outcome = (Map<String, Object>) outcomes.get(nodeId);
    if (outcome == null) {
      unlistedCalls.add(level + "/" + nodeId);
      throw new IOException("stub for level " + level + " does not list node " + nodeId);
    }
    if (outcome.containsKey("fail")) {
      if ("timeout".equals(outcome.get("fail"))) {
        throw new TimeoutException("stub timeout for " + level + "/" + nodeId);
      }
      throw new IOException("stub unavailable for " + level + "/" + nodeId);
    }
    return (Map<String, Object>) outcome.get("values");
  }

  /**
   * Returns the invocation counts so far.
   *
   * @return a copy: level, then node ID, then the number of invocations
   */
  synchronized Map<String, Map<String, Integer>> calls() {
    Map<String, Map<String, Integer>> copy = new TreeMap<>();
    calls.forEach((level, nodes) -> copy.put(level, new TreeMap<>(nodes)));
    return copy;
  }

  /**
   * Returns the invocations for nodes that no stub lists.
   *
   * @return {@code level/node} descriptions; a non-empty list makes the vector a runner failure (VEC-46)
   */
  synchronized List<String> unlistedCalls() {
    return List.copyOf(unlistedCalls);
  }

  /**
   * Returns the number of invocations of one level and node.
   *
   * @param level the level name
   * @param node the node ID
   * @return the count, 0 when never invoked
   */
  synchronized int count(String level, String node) {
    Map<String, Integer> nodes = calls.get(level);
    return nodes == null ? 0 : nodes.getOrDefault(node, 0);
  }
}
