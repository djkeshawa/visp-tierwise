package io.github.tierwise.core.internal.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DeepMergeTest {

  private static final TypeDecl TYPE = TestModels.standard().keys.get("k.deep").type();

  private static Map<String, Object> map(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }

  @Test
  void MERGE_22_aFieldOfTheOverridingObjectReplacesTheBaseField() {
    assertEquals(map("cap", 20L, "unit", "items"),
        DeepMerge.merge(TYPE, map("cap", 10L, "unit", "items"), map("cap", 20L)));
  }

  @Test
  void MERGE_22_anObjectFieldMergesInTurnWhenTheBaseHasAValue() {
    Map<String, Object> base = map("cap", 1L, "window", map("from", "08:00", "to", "18:00"));
    Map<String, Object> over = map("window", map("to", "17:00"));
    assertEquals(map("cap", 1L, "window", map("from", "08:00", "to", "17:00")), DeepMerge.merge(TYPE, base, over));
  }

  @Test
  void MERGE_22_anObjectFieldTheBaseLacksIsTakenAsIs() {
    Map<String, Object> over = map("window", map("from", "08:00"));
    assertEquals(map("cap", 1L, "window", map("from", "08:00")), DeepMerge.merge(TYPE, map("cap", 1L), over));
  }

  @Test
  void MERGE_22_aListReplacesTheEarlierValueAsAWhole() {
    assertEquals(map("tags", List.of("b")), DeepMerge.merge(TYPE, map("tags", List.of("a", "c")), map("tags", List.of("b"))));
  }

  @Test
  void MERGE_22_aMergeNeverRemovesAField() {
    assertEquals(map("cap", 1L, "unit", "u"), DeepMerge.merge(TYPE, map("cap", 1L, "unit", "u"), map()));
  }

  @Test
  void EVAL_49_theInputsAreNotModifiedAndTheResultIsUnmodifiable() {
    Map<String, Object> base = map("cap", 1L);
    Map<String, Object> over = map("cap", 2L);
    Map<String, Object> merged = DeepMerge.merge(TYPE, base, over);
    assertEquals(map("cap", 1L), base);
    assertEquals(map("cap", 2L), over);
    assertThrows(UnsupportedOperationException.class, () -> merged.put("x", 1L));
  }
}
