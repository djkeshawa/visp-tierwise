package io.github.tierwise.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContextTest {

  private static Map<String, Object> raw(Object... pairs) {
    Map<String, Object> map = new HashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }

  @Test
  void wellFormedShapeKeepsLevelsAndAxes() {
    Context context = Context.fromJson(raw("levels", Map.of("level_a", "node_x"), "axes", Map.of("axis_a", "node_y")));
    assertTrue(context.problem().isEmpty());
    assertEquals(Map.of("level_a", "node_x"), context.levels());
    assertEquals(Map.of("axis_a", "node_y"), context.axes());
  }

  @Test
  void anOmittedMemberIsAnEmptyMap() {
    Context context = Context.fromJson(raw("levels", Map.of("level_a", "node_x")));
    assertTrue(context.problem().isEmpty());
    assertEquals(Map.of(), context.axes());
    assertEquals(Context.empty(), Context.fromJson(Map.of()));
  }

  @Test
  void aNullMemberIsMalformed() {
    assertTrue(Context.fromJson(raw("levels", null)).problem().isPresent());
    assertTrue(Context.fromJson(raw("axes", null)).problem().isPresent());
  }

  @Test
  void aContextThatIsNotAMapIsMalformed() {
    assertTrue(Context.fromJson(null).problem().isPresent());
    assertTrue(Context.fromJson("levels").problem().isPresent());
    assertTrue(Context.fromJson(List.of()).problem().isPresent());
  }

  @Test
  void aNodeIdThatIsNotAStringIsMalformed() {
    assertTrue(Context.fromJson(raw("levels", Map.of("level_a", 7))).problem().isPresent());
    Map<String, Object> withNull = new HashMap<>();
    withNull.put("level_a", null);
    assertTrue(Context.fromJson(raw("levels", withNull)).problem().isPresent());
  }

  @Test
  void aNameThatIsNotAStringIsMalformed() {
    Map<Object, Object> levels = new HashMap<>();
    levels.put(3, "node_x");
    assertTrue(Context.fromJson(raw("levels", levels)).problem().isPresent());
  }

  @Test
  void anUnknownMemberIsMalformed() {
    Context context = Context.fromJson(raw("levels", Map.of(), "extra", Map.of()));
    assertTrue(context.problem().orElseThrow().contains("extra"));
    assertEquals(Map.of(), context.levels());
  }

  @Test
  void aMemberThatIsNotAMapIsMalformed() {
    assertTrue(Context.fromJson(raw("levels", List.of("level_a"))).problem().isPresent());
    assertTrue(Context.fromJson(raw("axes", "axis_a")).problem().isPresent());
  }

  @Test
  void builderAndFactoryAgree() {
    Context built = Context.builder().level("level_a", "node_x").axis("axis_a", "node_y").build();
    assertEquals(Context.of(Map.of("level_a", "node_x"), Map.of("axis_a", "node_y")), built);
    assertEquals(built.hashCode(), Context.fromJson(raw(
        "levels", Map.of("level_a", "node_x"), "axes", Map.of("axis_a", "node_y"))).hashCode());
  }

  @Test
  void aContextIsImmutableAndCopiesItsInput() {
    Map<String, String> levels = new HashMap<>(Map.of("level_a", "node_x"));
    Context context = Context.of(levels, Map.of());
    levels.put("level_b", "node_z");
    assertEquals(Map.of("level_a", "node_x"), context.levels());
    assertThrows(UnsupportedOperationException.class, () -> context.levels().put("level_b", "node_z"));
  }

  @Test
  void aMalformedContextDiffersFromTheEmptyContext() {
    assertTrue(!Context.fromJson(null).equals(Context.empty()));
  }
}
