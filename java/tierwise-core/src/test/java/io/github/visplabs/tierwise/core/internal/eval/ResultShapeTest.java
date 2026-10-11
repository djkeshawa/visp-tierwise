package io.github.visplabs.tierwise.core.internal.eval;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.Decision;
import io.github.visplabs.tierwise.core.Evaluator;
import io.github.visplabs.tierwise.core.EvaluatorOptions;
import io.github.visplabs.tierwise.core.FactSet;
import io.github.visplabs.tierwise.core.LevelValueProvider;
import io.github.visplabs.tierwise.core.Tierwise;
import io.github.visplabs.tierwise.core.internal.json.CanonicalJson;
import io.github.visplabs.tierwise.core.internal.json.StrictJson;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * The shape of a result is an engine guarantee that no evaluator option changes (PROV-2), and a serialised result is
 * an unordered JSON object, so two serialisations that differ only in member order are equal (PROV-85).
 */
class ResultShapeTest {

  private static final Set<String> DECISION_MEMBERS = Set.of("feature", "effect", "bundleVersion", "skippedScopes",
      "binding", "policy", "reason", "trace");
  private static final List<Context> CONTEXTS = List.of(Context.empty(),
      Context.builder().level("division", "south").level("account", "a1").build(),
      Context.builder().level("site", "s1").axis("promo", "autumn").build());
  private static final List<FactSet> FACT_SETS = List.of(FactSet.empty(),
      FactSet.builder().fact("customer.active", true).fact("customer.credit_hold", true)
          .child("item", Map.of("item.stock", "STOCKED")).build(),
      FactSet.builder().fact("customer.active", false).fact("customer.credit_hold", false).fact("order.total", "2.0").build());

  private final TestModels.Builder model = TestModels.standard();

  /** Evaluators over one bundle that differ only in their options. */
  private List<Evaluator> variants() {
    LevelValueProvider values = node -> Map.of("k.all", true, "k.min", 3L);
    LevelValueProvider slow = node -> {
      Thread.sleep(50);
      return Map.of("k.all", true);
    };
    LevelValueProvider failing = TestModels.failing();
    return List.of(
        Tierwise.evaluator(model.loaded(), EvaluatorOptions.builder().provider("account", values, Duration.ofSeconds(5))
            .provider("seat", values, Duration.ofSeconds(5)).build()),
        Tierwise.evaluator(model.loaded(), EvaluatorOptions.builder().provider("account", slow, Duration.ofMillis(1))
            .provider("seat", values, Duration.ofMillis(1)).listener(event -> { }).build()),
        Tierwise.evaluator(model.loaded(), EvaluatorOptions.builder().provider("account", failing, Duration.ofDays(1))
            .provider("seat", failing, Duration.ofDays(1)).listener(event -> {
              throw new IllegalStateException("listener failure");
            }).build()));
  }

  @Test
  void PROV_2_anEvaluatorOptionLeavesTheMemberSetOfADecisionUnchanged() {
    List<Evaluator> evaluators = variants();
    for (Context context : CONTEXTS) {
      for (FactSet facts : FACT_SETS) {
        Map<String, Object> reference = evaluators.get(0).decide(context, "f.gate", facts).toJson();
        assertEquals(DECISION_MEMBERS, reference.keySet());
        for (Evaluator evaluator : evaluators) {
          Map<String, Object> json = evaluator.decide(context, "f.gate", facts).toJson();
          assertEquals(DECISION_MEMBERS, json.keySet());
          assertEquals(reference, json, "decide calls no provider, so the options change nothing");
        }
      }
    }
  }

  @Test
  void PROV_2_anEvaluatorOptionLeavesTheMemberSetOfAResolvedResultUnchanged() {
    List<Evaluator> evaluators = variants();
    for (String key : model.keys.keySet()) {
      for (Context context : CONTEXTS) {
        Set<String> reference = new TreeSet<>(evaluators.get(0).resolve(context, key).toJson().keySet());
        for (Evaluator evaluator : evaluators) {
          assertEquals(reference, new TreeSet<>(evaluator.resolve(context, key).toJson().keySet()), key);
        }
      }
    }
  }

  @Test
  void PROV_85_aSerialisedResultComparesEqualWhateverTheOrderOfItsObjectMembers() {
    Evaluator evaluator = variants().get(0);
    List<Map<String, Object>> results = new ArrayList<>();
    for (FactSet facts : FACT_SETS) {
      results.add(evaluator.decide(CONTEXTS.get(1), "f.gate", facts).toJson());
    }
    results.add(evaluator.resolve(CONTEXTS.get(2), "k.deep").toJson());
    results.add(evaluator.resolve(CONTEXTS.get(1), "k.union").toJson());
    for (Map<String, Object> result : results) {
      String forward = write(result, false);
      String backward = write(result, true);
      assertNotEquals(forward, backward, "the two serialisations differ in member order");
      Object a = StrictJson.parse(forward.getBytes(StandardCharsets.UTF_8));
      Object b = StrictJson.parse(backward.getBytes(StandardCharsets.UTF_8));
      assertEquals(a, b);
      assertEquals(result, b);
      assertArrayEquals(CanonicalJson.toBytes(a), CanonicalJson.toBytes(b));
    }
  }

  @Test
  @SuppressWarnings("unchecked")
  void PROV_85_theOrderOfArrayElementsStaysSignificant() {
    FactSet noRuleMatches = FactSet.builder().fact("customer.active", true).fact("customer.credit_hold", true)
        .child("item", Map.of("item.stock", "DROP")).build();
    Decision decision = variants().get(0).decide(Context.empty(), "f.gate", noRuleMatches);
    Map<String, Object> json = decision.toJson();
    List<Object> trace = (List<Object>) json.get("trace");
    assertEquals(2, trace.size());
    List<Object> swapped = new ArrayList<>(trace);
    Collections.reverse(swapped);
    String text = write(json, false).replace(write(trace, false), write(swapped, false));
    assertNotEquals(json, StrictJson.parse(text.getBytes(StandardCharsets.UTF_8)));
  }

  /** Writes plain JSON with the members of each object in their own order, or in reverse order. */
  private static String write(Object value, boolean reverse) {
    StringBuilder out = new StringBuilder();
    write(value, reverse, out);
    return out.toString();
  }

  private static void write(Object value, boolean reverse, StringBuilder out) {
    if (value instanceof Map<?, ?> map) {
      List<Map.Entry<?, ?>> entries = new ArrayList<>(map.entrySet());
      if (reverse) {
        Collections.reverse(entries);
      }
      out.append('{');
      for (int i = 0; i < entries.size(); i++) {
        out.append(i == 0 ? "" : ",");
        writeString(String.valueOf(entries.get(i).getKey()), out);
        out.append(':');
        write(entries.get(i).getValue(), reverse, out);
      }
      out.append('}');
    } else if (value instanceof List<?> list) {
      out.append('[');
      for (int i = 0; i < list.size(); i++) {
        out.append(i == 0 ? "" : ",");
        write(list.get(i), reverse, out);
      }
      out.append(']');
    } else if (value instanceof String text) {
      writeString(text, out);
    } else {
      out.append(value);
    }
  }

  private static void writeString(String text, StringBuilder out) {
    out.append('"');
    for (char c : text.toCharArray()) {
      if (c == '"' || c == '\\') {
        out.append('\\').append(c);
      } else if (c < 0x20) {
        out.append(String.format("\\u%04x", (int) c));
      } else {
        out.append(c);
      }
    }
    out.append('"');
  }
}
