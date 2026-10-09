package io.github.tierwise.core.internal.eval;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValueEqualityTest {

  @Test
  void NORM_18_valuesOfDifferentTypesAreNeverEqual() {
    assertFalse(ValueEquality.scalarEquals(true, "true"));
    assertFalse(ValueEquality.scalarEquals(1L, true));
    assertFalse(ValueEquality.scalarEquals(1L, "1"));
    assertFalse(ValueEquality.scalarEquals("1.5", 1.5));
    assertFalse(ValueEquality.scalarEquals(null, null));
  }

  @Test
  void RULE_38_noOperandIsCoercedBeforeAComparison() {
    assertFalse(ValueEquality.scalarEquals(true, "true"));
    assertFalse(ValueEquality.scalarEquals("1.5", 1L));
    assertTrue(ValueEquality.scalarEquals(7L, 7L));
    assertTrue(ValueEquality.scalarEquals("1.5", "1.5"));
    assertTrue(ValueEquality.scalarEquals(false, false));
  }

  @Test
  void NORM_6_stringsAreEqualOnlyForTheSameCodePoints() {
    assertFalse(ValueEquality.scalarEquals("é", "é"));
    assertFalse(ValueEquality.scalarEquals("a", "A"));
  }

  @Test
  void NORM_19_setsAreEqualWhenTheyHaveTheSameElements() {
    TypeDecl set = TypeDecl.set(List.of("a", "b", "c"));
    assertTrue(ValueEquality.equal(set, List.of("a", "b"), List.of("b", "a")));
    assertFalse(ValueEquality.equal(set, List.of("a", "b"), List.of("a", "c")));
    assertFalse(ValueEquality.equal(set, List.of("a"), List.of("a", "b")));
    assertTrue(ValueEquality.equal(set, List.of(), List.of()));
  }

  @Test
  void NORM_19_listsAreEqualWhenTheyHaveTheSameLengthAndEqualElementsAtEachPosition() {
    TypeDecl list = TypeDecl.list(TypeDecl.integer(null, null));
    assertTrue(ValueEquality.equal(list, List.of(1L, 2L), List.of(1L, 2L)));
    assertFalse(ValueEquality.equal(list, List.of(1L, 2L), List.of(2L, 1L)));
    assertFalse(ValueEquality.equal(list, List.of(1L), List.of(1L, 1L)));
  }

  @Test
  void NORM_19_objectsAreEqualForTheSameFieldNamesAndEqualValuesWhateverTheFieldOrder() {
    Map<String, TypeDecl> fields = new LinkedHashMap<>();
    fields.put("tags", TypeDecl.set(List.of("x", "y")));
    fields.put("n", TypeDecl.integer(null, null));
    TypeDecl object = TypeDecl.object(fields);
    Map<String, Object> left = new LinkedHashMap<>();
    left.put("tags", List.of("x", "y"));
    left.put("n", 1L);
    Map<String, Object> right = new LinkedHashMap<>();
    right.put("n", 1L);
    right.put("tags", List.of("y", "x"));
    assertTrue(ValueEquality.equal(object, left, right));
    right.put("n", 2L);
    assertFalse(ValueEquality.equal(object, left, right));
    right.put("n", 1L);
    right.remove("tags");
    assertFalse(ValueEquality.equal(object, left, right));
  }

  @Test
  void NORM_19_nestedValuesAreComparedByTheirDeclaredTypes() {
    TypeDecl inner = TypeDecl.object(Map.of("s", TypeDecl.set(List.of("a", "b"))));
    TypeDecl list = TypeDecl.list(inner);
    assertTrue(ValueEquality.equal(list, List.of(Map.of("s", List.of("a", "b"))), List.of(Map.of("s", List.of("b", "a")))));
    assertFalse(ValueEquality.equal(list, List.of(Map.of("s", List.of("a"))), List.of(Map.of("s", List.of("b")))));
  }
}
