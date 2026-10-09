package io.github.tierwise.core.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Structural validation of evaluate vectors and the providerCalls comparison (VEC-65 to VEC-67). */
class EvaluateVectorTest {

  private static final Path BUNDLES = Paths.get("/suite/bundles");
  private static final Path FILE = Paths.get("/suite/evaluate/group/case.yaml");

  private static Map<String, Object> map(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }

  private static Map<String, Object> resolveVector(Object... extra) {
    Map<String, Object> doc = map("id", "group/case", "specVersion", "0.1", "clauses", List.of("EVAL-7"),
        "description", "d", "call", "resolve", "bundle", "../../bundles/b.json", "key", "key_x",
        "expect", map("value", 1));
    for (int i = 0; i < extra.length; i += 2) {
      doc.put((String) extra[i], extra[i + 1]);
    }
    return doc;
  }

  @Test
  void aWellFormedResolveVectorParses() {
    EvaluateVector vector = EvaluateVector.parse(resolveVector(), FILE, BUNDLES);
    assertEquals(Paths.get("/suite/bundles/b.json"), vector.bundle());
    assertEquals("key_x", vector.key());
    assertEquals(map("levels", Map.of(), "axes", Map.of()), vector.context());
    assertEquals(map("value", 1), vector.expectResult());
  }

  @Test
  void aMissingContextMemberBecomesAnEmptyMap() {
    EvaluateVector vector = EvaluateVector.parse(
        resolveVector("context", map("levels", map("level_a", "node_x"))), FILE, BUNDLES);
    assertEquals(map("level_a", "node_x"), vector.context().get("levels"));
    assertEquals(Map.of(), vector.context().get("axes"));
  }

  @Test
  void aNullMemberIsKeptSoTheEvaluatorCanRejectIt() {
    Map<String, Object> context = map("levels", null);
    EvaluateVector vector = EvaluateVector.parse(resolveVector("context", context), FILE, BUNDLES);
    assertEquals(null, vector.context().get("levels"));
    assertTrue(vector.context().containsKey("levels"));
  }

  @Test
  void aNullContextIsARunnerFailure() {
    assertThrows(RunnerFailure.class, () -> EvaluateVector.parse(resolveVector("context", null), FILE, BUNDLES));
  }

  @Test
  void anUnknownFieldIsARunnerFailure() {
    assertThrows(RunnerFailure.class, () -> EvaluateVector.parse(resolveVector("surprise", 1), FILE, BUNDLES));
    assertThrows(RunnerFailure.class, () -> EvaluateVector.parse(
        resolveVector("expect", map("value", 1, "surprise", 2)), FILE, BUNDLES));
  }

  @Test
  void aBundleOutsideBundlesIsARunnerFailure() {
    assertThrows(RunnerFailure.class,
        () -> EvaluateVector.parse(resolveVector("bundle", "../../other/b.json"), FILE, BUNDLES));
  }

  @Test
  void expectFormsAreChecked() {
    assertThrows(RunnerFailure.class, () -> EvaluateVector.parse(resolveVector("expect", map()), FILE, BUNDLES));
    assertThrows(RunnerFailure.class, () -> EvaluateVector.parse(
        resolveVector("expect", map("providerCalls", map())), FILE, BUNDLES));
    assertThrows(RunnerFailure.class, () -> EvaluateVector.parse(
        resolveVector("expect", map("error", "E_UNKNOWN_KEY", "value", 1)), FILE, BUNDLES));
    assertThrows(RunnerFailure.class, () -> EvaluateVector.parse(
        resolveVector("expect", map("error", "E_PARSE")), FILE, BUNDLES));
    assertEquals("E_UNKNOWN_KEY", EvaluateVector.parse(
        resolveVector("expect", map("error", "E_UNKNOWN_KEY")), FILE, BUNDLES).expectError());
  }

  @Test
  void callSpecificFieldsAreChecked() {
    assertThrows(RunnerFailure.class, () -> EvaluateVector.parse(resolveVector("input", map()), FILE, BUNDLES));
    Map<String, Object> decide = resolveVector("call", "decide", "feature", "feature_x");
    decide.remove("key");
    decide.put("expect", map("effect", "A"));
    assertThrows(RunnerFailure.class, () -> EvaluateVector.parse(decide, FILE, BUNDLES));
    decide.put("input", null);
    EvaluateVector vector = EvaluateVector.parse(decide, FILE, BUNDLES);
    assertEquals(null, vector.input());
  }

  @Test
  void anIntegerKeyInAMappingIsARunnerFailure() {
    Map<Object, Object> levels = new LinkedHashMap<>();
    levels.put(7, "node_x");
    assertThrows(RunnerFailure.class, () -> EvaluateVector.parse(
        resolveVector("context", map("levels", levels)), FILE, BUNDLES));
  }

  @Test
  void providersAcceptNoneAndStubs() {
    EvaluateVector vector = EvaluateVector.parse(resolveVector("providers",
        map("level_a", "none", "level_b", map("node_x", map("values", map())))), FILE, BUNDLES);
    assertEquals("none", vector.providers().get("level_a"));
    assertThrows(RunnerFailure.class, () -> EvaluateVector.parse(
        resolveVector("providers", map("level_a", 5)), FILE, BUNDLES));
  }

  @Test
  void providerCallsAreComparedByNamedPairsUnlessExact() {
    Map<String, Map<String, Integer>> actual = Map.of("level_a", Map.of("node_x", 1, "node_y", 2));
    assertTrue(Runner.calls(Map.of("level_a", Map.of("node_x", 1)), actual, false).isEmpty());
    assertEquals(1, Runner.calls(Map.of("level_a", Map.of("node_x", 2)), actual, false).size());
    assertEquals(1, Runner.calls(Map.of("level_a", Map.of("node_z", 1)), actual, false).size());
    assertTrue(Runner.calls(Map.of("level_a", Map.of("node_z", 0)), actual, false).isEmpty());
    assertTrue(Runner.calls(null, actual, false).isEmpty());
  }

  @Test
  void exactCountsEveryUnnamedInvocationAsAMismatch() {
    Map<String, Map<String, Integer>> actual = Map.of("level_a", Map.of("node_x", 1, "node_y", 2));
    assertEquals(1, Runner.calls(Map.of("level_a", Map.of("node_x", 1)), actual, true).size());
    assertTrue(Runner.calls(Map.of("level_a", Map.of("node_x", 1, "node_y", 2)), actual, true).isEmpty());
    assertTrue(Runner.calls(Map.of(), Map.of(), true).isEmpty());
  }

  /** Short alias for the comparison under test. */
  private static final class Runner {
    static List<String> calls(
        Map<String, Map<String, Integer>> expected, Map<String, Map<String, Integer>> actual, boolean exact) {
      return EvaluateRunner.providerCallMismatches(expected, actual, exact);
    }
  }
}
