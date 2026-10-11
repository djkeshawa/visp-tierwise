package io.github.visplabs.tierwise.core.conformance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Unit tests of the subset and exact matching rules (VEC-54 to VEC-64). */
class ExpectTest {

  private static Map<String, Object> map(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }

  @Test
  void subsetIgnoresUnnamedKeys() {
    assertTrue(Expect.matches(map("value", 6L), map("value", 6L, "key", "k"), false));
  }

  @Test
  void exactRejectsUnnamedKeys() {
    List<String> mismatches = Expect.mismatches(map("value", 6L), map("value", 6L, "key", "k"), true);
    assertEquals(1, mismatches.size());
    assertTrue(mismatches.get(0).contains("$.key"));
  }

  @Test
  void exactAppliesRecursively() {
    Object actual = map("source", map("scope", "level", "name", "n", "node", "x"));
    assertTrue(Expect.matches(map("source", map("scope", "level")), actual, false));
    assertFalse(Expect.matches(map("source", map("scope", "level")), actual, true));
  }

  @Test
  void keyNamedButAbsentNeverMatchesEvenForNull() {
    assertFalse(Expect.matches(map("kind", null), map("value", 1L), false));
    assertTrue(Expect.matches(map("value", null), map("value", null), false));
  }

  @Test
  void expectedNullMatchesOnlyNull() {
    assertTrue(Expect.matches(null, null, false));
    assertFalse(Expect.matches(null, "x", false));
    assertFalse(Expect.matches(null, 0L, false));
    assertFalse(Expect.matches(null, false, false));
  }

  @Test
  void expectedNonNullNeverMatchesNull() {
    assertFalse(Expect.matches("x", null, false));
    assertFalse(Expect.matches(Boolean.FALSE, null, false));
    assertFalse(Expect.matches(0, null, false));
  }

  @Test
  void booleansMatchOnlyBooleans() {
    assertTrue(Expect.matches(true, true, false));
    assertFalse(Expect.matches(true, "true", false));
    assertFalse(Expect.matches(true, 1L, false));
    assertFalse(Expect.matches(1, true, false));
  }

  @Test
  void integersCompareByValueAcrossRepresentations() {
    assertTrue(Expect.matches(8, 8L, false));
    assertTrue(Expect.matches(8L, (Object) 8, false));
    assertTrue(Expect.matches(new BigInteger("9007199254740991"), 9007199254740991L, false));
    assertFalse(Expect.matches(8, 9L, false));
    assertFalse(Expect.matches(8, "8", false));
    assertFalse(Expect.matches(8, 8.0, false));
  }

  @Test
  void stringsCompareByExactCodePoints() {
    assertTrue(Expect.matches("café", "café", false));
    assertFalse(Expect.matches("café", "café", false));
    assertFalse(Expect.matches("1.50", "1.5", false));
  }

  @Test
  void sequencesNeedTheSameLengthAndOrder() {
    assertTrue(Expect.matches(List.of("a", "b"), List.of("a", "b"), false));
    assertFalse(Expect.matches(List.of("a", "b"), List.of("b", "a"), false));
    assertFalse(Expect.matches(List.of("a"), List.of("a", "b"), false));
    assertTrue(Expect.matches(List.of(), List.of(), false));
    assertFalse(Expect.matches(List.of(), List.of("a"), false));
  }

  @Test
  void sequenceItemsMatchAsSubsetsUnlessExact() {
    Object actual = List.of(map("scope", "level", "name", "n", "effect", "SET"));
    assertTrue(Expect.matches(List.of(map("effect", "SET")), actual, false));
    assertFalse(Expect.matches(List.of(map("effect", "SET")), actual, true));
  }

  @Test
  void typeMismatchesBetweenContainersAndScalars() {
    assertFalse(Expect.matches(map("a", 1), List.of(1), false));
    assertFalse(Expect.matches(List.of(1), map("a", 1), false));
    assertFalse(Expect.matches(map("a", 1), "a", false));
  }

  @Test
  void mismatchMessagesNameTheLocation() {
    List<String> mismatches = Expect.mismatches(
        map("contributions", List.of(map("value", 1L))), map("contributions", List.of(map("value", 2L))), false);
    assertEquals(1, mismatches.size());
    assertTrue(mismatches.get(0).startsWith("$.contributions[0].value"), mismatches.get(0));
  }
}
