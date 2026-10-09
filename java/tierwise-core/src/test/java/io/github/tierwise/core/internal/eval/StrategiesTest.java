package io.github.tierwise.core.internal.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Resolved.Contribution.Label;
import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

class StrategiesTest {

  private static final TypeDecl COLOURS = TypeDecl.set(List.of("red", "green", "blue"));
  private static final TypeDecl BOUNDED = TypeDecl.integer(1L, 500L);
  private static final TypeDecl BOOL = TypeDecl.simple("boolean");

  private static List<Object> values(Object... items) {
    return new ArrayList<>(List.of(items));
  }

  @Test
  void MERGE_5_identitiesAreTheResultWhenNothingContributes() {
    assertEquals(true, Strategies.identity(Strategy.ALL, BOOL));
    assertEquals(false, Strategies.identity(Strategy.ANY, BOOL));
    assertEquals(List.of("red", "green", "blue"), Strategies.identity(Strategy.INTERSECT, COLOURS));
    assertEquals(List.of(), Strategies.identity(Strategy.UNION, COLOURS));
    assertNull(Strategies.identity(Strategy.MIN, BOUNDED));
    assertNull(Strategies.identity(Strategy.MAX, BOUNDED));
  }

  @Test
  void MERGE_17_allIsALogicalAnd() {
    assertEquals(true, Strategies.all(values(true, true)));
    assertEquals(false, Strategies.all(values(true, false, true)));
  }

  @Test
  void MERGE_17_anyIsALogicalOr() {
    assertEquals(false, Strategies.any(values(false, false)));
    assertEquals(true, Strategies.any(values(false, true)));
  }

  @Test
  void MERGE_17_intersectStartsFromTheUniverseAndReturnsUniverseOrder() {
    assertEquals(List.of("red", "blue"),
        Strategies.intersect(COLOURS, values(List.of("blue", "red", "green"), List.of("red", "blue"))));
    assertEquals(List.of(), Strategies.intersect(COLOURS, values(List.of("red"), List.of("blue"))));
    assertEquals(List.of("red", "green", "blue"), Strategies.intersect(COLOURS, values()));
  }

  @Test
  void MERGE_17_unionStartsFromTheEmptySetAndReturnsUniverseOrder() {
    assertEquals(List.of("red", "blue"), Strategies.union(COLOURS, values(List.of("blue"), List.of("red"))));
    assertEquals(List.of(), Strategies.union(COLOURS, values()));
  }

  @Test
  void MERGE_17_minAndMaxPickTheSmallerAndLargerAndAreAbsentWithoutValues() {
    assertEquals(3L, Strategies.min(values(5L, 3L, 9L)));
    assertEquals(9L, Strategies.max(values(5L, 3L, 9L)));
    assertNull(Strategies.min(values()));
    assertNull(Strategies.max(values()));
    assertEquals(-4L, Strategies.min(values(-4L, 2L)));
  }

  @Test
  void MERGE_17_combineDispatchesToTheStrategyOperation() {
    assertEquals(2L, Strategies.combine(Strategy.MIN, BOUNDED, values(2L, 7L)));
    assertEquals(7L, Strategies.combine(Strategy.MAX, BOUNDED, values(2L, 7L)));
    assertEquals(false, Strategies.combine(Strategy.ALL, BOOL, values(true, false)));
  }

  @Test
  void SVC_9_denyValuesDependOnTheStrategy() {
    assertEquals(Optional.of(false), Strategies.denyValue(Strategy.ALL, BOOL));
    assertEquals(Optional.of(List.of()), Strategies.denyValue(Strategy.INTERSECT, COLOURS));
    assertEquals(Optional.of(1L), Strategies.denyValue(Strategy.MIN, BOUNDED));
    assertTrue(Strategies.denyValue(Strategy.MAX, BOUNDED).isEmpty());
    assertTrue(Strategies.denyValue(Strategy.ANY, BOOL).isEmpty());
    assertTrue(Strategies.denyValue(Strategy.OVERRIDE, BOOL).isEmpty());
  }

  @Test
  void PROV_38_labelsDependOnTheValueAndTheStrategyAlone() {
    assertEquals(Label.VETO, Strategies.label(Strategy.ALL, BOOL, false));
    assertEquals(Label.IGNORED, Strategies.label(Strategy.ALL, BOOL, true));
    assertEquals(Label.WIDENED, Strategies.label(Strategy.ANY, BOOL, true));
    assertEquals(Label.IGNORED, Strategies.label(Strategy.ANY, BOOL, false));
    assertEquals(Label.NARROWED, Strategies.label(Strategy.INTERSECT, COLOURS, List.of("red")));
    assertEquals(Label.NARROWED, Strategies.label(Strategy.INTERSECT, COLOURS, List.of()));
    assertEquals(Label.IGNORED, Strategies.label(Strategy.INTERSECT, COLOURS, List.of("red", "green", "blue")));
    assertEquals(Label.WIDENED, Strategies.label(Strategy.UNION, COLOURS, List.of("red")));
    assertEquals(Label.IGNORED, Strategies.label(Strategy.UNION, COLOURS, List.of()));
    assertEquals(Label.NARROWED, Strategies.label(Strategy.MIN, BOUNDED, 7L));
    assertEquals(Label.WIDENED, Strategies.label(Strategy.MAX, BOUNDED, 7L));
    assertEquals(Label.SET, Strategies.label(Strategy.OVERRIDE, BOUNDED, 7L));
    assertEquals(Label.SET, Strategies.label(Strategy.DEEP_MERGE, BOUNDED, 7L));
  }

  @Test
  void MERGE_2_onlyAllIntersectAndMinAreNarrowing() {
    for (Strategy strategy : Strategy.values()) {
      boolean narrowing = strategy == Strategy.ALL || strategy == Strategy.INTERSECT || strategy == Strategy.MIN;
      assertEquals(narrowing, strategy.isNarrowing(), strategy.name());
      assertEquals(strategy != Strategy.OVERRIDE && strategy != Strategy.DEEP_MERGE, strategy.isCommutative());
      assertEquals(strategy, Strategy.parse(strategy.wire()).orElseThrow());
    }
    assertTrue(Strategy.parse("first").isEmpty());
  }

  @Property
  void MERGE_18_allDoesNotDependOnTheOrderOfTheValues(@ForAll("booleans") List<Boolean> input, @ForAll long seed) {
    assertOrderIndependent(Strategy.ALL, BOOL, input, seed);
    assertOrderIndependent(Strategy.ANY, BOOL, input, seed);
  }

  @Property
  void MERGE_18_integerStrategiesDoNotDependOnTheOrderOfTheValues(@ForAll("integers") List<Long> input,
      @ForAll long seed) {
    assertOrderIndependent(Strategy.MIN, BOUNDED, input, seed);
    assertOrderIndependent(Strategy.MAX, BOUNDED, input, seed);
  }

  @Property
  void MERGE_18_setStrategiesDoNotDependOnTheOrderOfTheValues(@ForAll("sets") List<List<String>> input,
      @ForAll long seed) {
    assertOrderIndependent(Strategy.INTERSECT, COLOURS, input, seed);
    assertOrderIndependent(Strategy.UNION, COLOURS, input, seed);
  }

  private static void assertOrderIndependent(Strategy strategy, TypeDecl type, List<?> input, long seed) {
    List<Object> original = new ArrayList<>(input);
    List<Object> shuffled = new ArrayList<>(input);
    Collections.shuffle(shuffled, new java.util.Random(seed));
    assertEquals(Strategies.combine(strategy, type, original), Strategies.combine(strategy, type, shuffled));
    assertEquals(input, original, "the input is not modified");
  }

  @Provide
  Arbitrary<List<Boolean>> booleans() {
    return Arbitraries.of(true, false).list().ofMaxSize(8);
  }

  @Provide
  Arbitrary<List<Long>> integers() {
    return Arbitraries.longs().between(1L, 500L).list().ofMaxSize(8);
  }

  @Provide
  Arbitrary<List<List<String>>> sets() {
    Arbitrary<List<String>> one = Arbitraries.of("red", "green", "blue").list().uniqueElements().ofMaxSize(3)
        .map(chosen -> {
          List<String> ordered = new ArrayList<>();
          for (String member : List.of("red", "green", "blue")) {
            if (chosen.contains(member)) {
              ordered.add(member);
            }
          }
          return ordered;
        });
    return one.list().ofMaxSize(5);
  }
}
