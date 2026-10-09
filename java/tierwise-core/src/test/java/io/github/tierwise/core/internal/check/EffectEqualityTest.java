package io.github.tierwise.core.internal.check;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.internal.model.Returns;
import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The value equality of NORM-18 and NORM-19 where the compiler uses it: comparing an example's decided effect with
 * its expected effect (POL-41, POL-48). Conditions compare scalars only (RULE-35 to RULE-38), so no decision vector
 * compares set, list or object values (spec/conformance/enforcers.d/E6.yaml).
 */
class EffectEqualityTest {

  /** An object effect with a scalar, a set, a list, a nested object and a list of objects. */
  private static final Returns EFFECT = Returns.object(Map.of(
      "days", TypeDecl.integer(null, null),
      "label", TypeDecl.simple("string"),
      "on", TypeDecl.simple("boolean"),
      "tags", TypeDecl.set(List.of("A", "B", "C")),
      "steps", TypeDecl.list(TypeDecl.enumeration(List.of("ONE", "TWO"))),
      "window", TypeDecl.object(Map.of("open", TypeDecl.simple("string"), "close", TypeDecl.simple("string"),
          "tags", TypeDecl.set(List.of("X", "Y")))),
      "legs", TypeDecl.list(TypeDecl.object(Map.of("tags", TypeDecl.set(List.of("X", "Y")),
          "days", TypeDecl.integer(null, null))))));

  private static Map<String, Object> effect(Object... overrides) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("days", 2L);
    out.put("label", "1");
    out.put("on", true);
    out.put("tags", List.of("A", "B"));
    out.put("steps", List.of("ONE", "TWO"));
    out.put("window", Map.of("open", "08:00", "close", "18:00", "tags", List.of("X", "Y")));
    out.put("legs", List.of(Map.of("tags", List.of("X"), "days", 1L), Map.of("tags", List.of("X", "Y"), "days", 2L)));
    for (int i = 0; i < overrides.length; i += 2) {
      out.put((String) overrides[i], overrides[i + 1]);
    }
    return out;
  }

  private static Map<String, Object> reversed(Map<String, Object> map) {
    List<String> names = new java.util.ArrayList<>(map.keySet());
    java.util.Collections.reverse(names);
    Map<String, Object> out = new LinkedHashMap<>();
    names.forEach(name -> out.put(name, map.get(name)));
    return out;
  }

  @Test
  void NORM_19_setsAreEqualWhenTheyHaveTheSameElementsInAnyOrder() {
    assertTrue(ExampleRunner.sameEffect(EFFECT, effect(), effect("tags", List.of("B", "A"))));
    assertFalse(ExampleRunner.sameEffect(EFFECT, effect(), effect("tags", List.of("A", "C"))));
    assertFalse(ExampleRunner.sameEffect(EFFECT, effect(), effect("tags", List.of("A"))));
    assertTrue(ExampleRunner.sameEffect(EFFECT, effect("tags", List.of()), effect("tags", List.of())));
  }

  @Test
  void NORM_19_listsAreEqualForTheSameLengthAndEqualElementsAtEachPosition() {
    assertFalse(ExampleRunner.sameEffect(EFFECT, effect(), effect("steps", List.of("TWO", "ONE"))));
    assertFalse(ExampleRunner.sameEffect(EFFECT, effect(), effect("steps", List.of("ONE", "TWO", "TWO"))));
    assertTrue(ExampleRunner.sameEffect(EFFECT, effect("steps", List.of("ONE", "ONE")),
        effect("steps", List.of("ONE", "ONE"))));
  }

  @Test
  void NORM_19_nestedObjectsAreEqualForTheSameFieldsWhateverTheFieldOrderAndCompareTheirMembersByType() {
    Map<String, Object> window = new LinkedHashMap<>();
    window.put("tags", List.of("Y", "X"));
    window.put("close", "18:00");
    window.put("open", "08:00");
    assertTrue(ExampleRunner.sameEffect(EFFECT, effect(), reversed(effect("window", window))));
    assertFalse(ExampleRunner.sameEffect(EFFECT, effect(), effect("window", Map.of("open", "08:00", "close", "18:00"))));
    assertFalse(ExampleRunner.sameEffect(EFFECT, effect(),
        effect("window", Map.of("open", "08:00", "close", "17:00", "tags", List.of("X", "Y")))));
  }

  @Test
  void NORM_19_aListOfObjectsComparesEachElementUnderItsOwnType() {
    List<Object> sameLegs = List.of(Map.of("days", 1L, "tags", List.of("X")), Map.of("days", 2L, "tags", List.of("Y", "X")));
    assertTrue(ExampleRunner.sameEffect(EFFECT, effect(), effect("legs", sameLegs)));
    List<Object> swapped = List.of(Map.of("tags", List.of("X", "Y"), "days", 2L), Map.of("tags", List.of("X"), "days", 1L));
    assertFalse(ExampleRunner.sameEffect(EFFECT, effect(), effect("legs", swapped)));
  }

  @Test
  void NORM_18_valuesOfDifferentTypesAreNeverEqual() {
    assertFalse(ExampleRunner.sameEffect(EFFECT, effect(), effect("days", "2")));
    assertFalse(ExampleRunner.sameEffect(EFFECT, effect(), effect("label", 1L)));
    assertFalse(ExampleRunner.sameEffect(EFFECT, effect(), effect("on", "true")));
    assertFalse(ExampleRunner.sameEffect(EFFECT, effect(), effect("tags", "A")));
    assertFalse(ExampleRunner.sameEffect(EFFECT, effect(), effect("window", List.of())));
    Returns enumeration = Returns.enumeration(List.of("ALLOW", "DENY"), true);
    assertFalse(ExampleRunner.sameEffect(enumeration, "ALLOW", null));
    assertTrue(ExampleRunner.sameEffect(enumeration, null, null));
  }
}
