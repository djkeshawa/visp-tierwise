package io.github.tierwise.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FactSetTest {

  private static Map<String, Object> raw(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }

  @Test
  void anEmptyMapIsTheEmptyFactSet() {
    FactSet facts = FactSet.fromJson(Map.of());
    assertTrue(facts.problem().isEmpty());
    assertEquals(FactSet.empty(), facts);
  }

  @Test
  void factsAndChildrenAreKept() {
    FactSet facts = FactSet.fromJson(raw(
        "facts", raw("entity_a.fact_x", true, "entity_a.fact_y", null),
        "children", raw("entity_b", List.of(raw("entity_b.fact_z", "v"), raw()))));
    assertTrue(facts.problem().isEmpty());
    assertEquals(true, facts.facts().get("entity_a.fact_x"));
    assertTrue(facts.facts().containsKey("entity_a.fact_y"));
    assertNull(facts.facts().get("entity_a.fact_y"));
    assertEquals(2, facts.children().get("entity_b").size());
  }

  @Test
  void valuesAreNotTypeCheckedHere() {
    FactSet facts = FactSet.fromJson(raw("facts", raw("entity_a.fact_x", "true", "entity_a.fact_n", 1.5)));
    assertTrue(facts.problem().isEmpty());
    assertEquals(1.5, facts.facts().get("entity_a.fact_n"));
  }

  @Test
  void aValueThatIsNotAMapIsMalformed() {
    assertTrue(FactSet.fromJson(null).problem().isPresent());
    assertTrue(FactSet.fromJson(List.of()).problem().isPresent());
    assertTrue(FactSet.fromJson("facts").problem().isPresent());
  }

  @Test
  void anUnknownMemberIsMalformed() {
    assertTrue(FactSet.fromJson(raw("facts", Map.of(), "other", Map.of())).problem().isPresent());
  }

  @Test
  void aNullOrNonMapMemberIsMalformed() {
    assertTrue(FactSet.fromJson(raw("facts", null)).problem().isPresent());
    assertTrue(FactSet.fromJson(raw("children", null)).problem().isPresent());
    assertTrue(FactSet.fromJson(raw("facts", List.of())).problem().isPresent());
    assertTrue(FactSet.fromJson(raw("children", "x")).problem().isPresent());
  }

  @Test
  void aChildEntryThatIsNotAListOfMapsIsMalformed() {
    assertTrue(FactSet.fromJson(raw("children", raw("entity_b", raw()))).problem().isPresent());
    assertTrue(FactSet.fromJson(raw("children", raw("entity_b", List.of("x")))).problem().isPresent());
    assertTrue(FactSet.fromJson(raw("children", raw("entity_b", List.of(Map.of(1, 2))))).problem().isPresent());
  }

  @Test
  void builderBuildsTheSameFactSetAsFromJson() {
    FactSet built = FactSet.builder()
        .fact("entity_a.fact_x", true)
        .child("entity_b", Map.of("entity_b.fact_z", "v"))
        .build();
    FactSet parsed = FactSet.fromJson(raw(
        "facts", raw("entity_a.fact_x", true),
        "children", raw("entity_b", List.of(raw("entity_b.fact_z", "v")))));
    assertEquals(parsed, built);
  }

  @Test
  void aFactSetIsImmutableAndCopiesItsInput() {
    Map<String, Object> facts = new HashMap<>(Map.of("entity_a.fact_x", true));
    List<Object> list = new ArrayList<>();
    FactSet set = FactSet.fromJson(raw("facts", facts, "children", raw("entity_b", list)));
    facts.put("entity_a.fact_y", false);
    list.add(raw());
    assertEquals(Map.of("entity_a.fact_x", true), set.facts());
    assertTrue(set.children().get("entity_b").isEmpty());
    assertThrows(UnsupportedOperationException.class, () -> set.facts().put("entity_a.fact_y", false));
    assertThrows(UnsupportedOperationException.class, () -> set.children().get("entity_b").add(Map.of()));
  }
}
