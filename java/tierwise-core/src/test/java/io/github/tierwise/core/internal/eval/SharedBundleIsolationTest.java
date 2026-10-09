package io.github.tierwise.core.internal.eval;

import static io.github.tierwise.core.internal.eval.TestModels.mapOf;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tierwise.core.Context;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.LevelValueProvider;
import io.github.tierwise.core.Tierwise;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.core.internal.model.LoadedModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * EVAL-51 and EVAL-52 over evaluators that share one loaded bundle: a call gives the outcome it gives alone,
 * whatever calls ran before it on that evaluator or on another one, and whatever calls run at the same time.
 */
class SharedBundleIsolationTest {

  private static final LoadedModel BUNDLE = TestModels.standard().loaded();

  /** One operation with its arguments. */
  record Call(String op, Context context, String name, FactSet facts) {

    Object outcome(Evaluator evaluator) {
      try {
        return switch (op) {
          case "resolve" -> evaluator.resolve(context, name).toJson();
          case "resolveLocal" -> evaluator.resolveLocal(context, name).toJson();
          default -> evaluator.decide(context, name, facts).toJson();
        };
      } catch (TierwiseException e) {
        return e.code();
      }
    }
  }

  /** A provider whose answer depends only on the node, after a node-dependent delay. */
  private static LevelValueProvider provider() {
    return node -> {
      Thread.sleep(node.equals("a1") ? 3 : 1);
      if (node.equals("down")) {
        throw new java.io.IOException("down");
      }
      return mapOf("k.all", node.equals("a1"), "k.min", 7L, "k.union", List.of("blue"), "k.deep", mapOf("cap", 3L),
          "k.label", "from-" + node);
    };
  }

  private static Evaluator evaluator() {
    return Tierwise.evaluator(BUNDLE, EvaluatorOptions.builder().provider("account", provider(), TestModels.TIMEOUT)
        .provider("seat", node -> Map.of(), TestModels.TIMEOUT).build());
  }

  @Provide
  Arbitrary<Call> calls() {
    Arbitrary<Context> contexts = Arbitraries.of(Context.empty(),
        Context.builder().level("division", "north").build(),
        Context.builder().level("division", "south").level("account", "a1").build(),
        Context.builder().level("account", "a2").axis("seller", "acme").build(),
        Context.builder().level("account", "down").build(),
        Context.builder().level("division", "west").build(),
        Context.builder().axis("promo", "autumn").build());
    Arbitrary<String> keys = Arbitraries.of("k.all", "k.min", "k.union", "k.deep", "k.label", "k.max_across", "k.nope");
    Arbitrary<FactSet> facts = Arbitraries.of(FactSet.empty(),
        FactSet.builder().fact("customer.active", true).fact("customer.credit_hold", false).build(),
        FactSet.builder().fact("customer.active", true).fact("customer.credit_hold", true)
            .child("item", Map.of("item.stock", "STOCKED")).child("item", Map.of("item.stock", "DROP")).build(),
        FactSet.builder().fact("order.lines", "many").build());
    Arbitrary<Call> resolves = Combinators.combine(Arbitraries.of("resolve", "resolveLocal"), contexts, keys)
        .as((op, context, key) -> new Call(op, context, key, null));
    Arbitrary<Call> decides = Combinators.combine(contexts, Arbitraries.of("f.gate", "f.nope"), facts)
        .as((context, feature, factSet) -> new Call("decide", context, feature, factSet));
    return Arbitraries.oneOf(resolves, decides);
  }

  @Provide
  Arbitrary<List<Call>> callLists() {
    return calls().list().ofMinSize(1).ofMaxSize(10);
  }

  @Property(tries = 60)
  void EVAL_51_aCallGivesItsOutcomeAloneWhateverRanBeforeOnEvaluatorsSharingTheBundle(
      @ForAll("callLists") List<Call> before, @ForAll("calls") Call call) {
    Object alone = call.outcome(evaluator());
    Evaluator shared = evaluator();
    Evaluator other = evaluator();
    for (int i = 0; i < before.size(); i++) {
      before.get(i).outcome(i % 2 == 0 ? shared : other);
    }
    assertEquals(alone, call.outcome(shared));
    assertEquals(alone, call.outcome(other));
  }

  @Property(tries = 15)
  void EVAL_52_concurrentCallsOnEvaluatorsSharingOneBundleGiveTheirOutcomesAlone(@ForAll("callLists") List<Call> calls)
      throws Exception {
    List<Object> alone = new ArrayList<>();
    for (Call call : calls) {
      alone.add(call.outcome(evaluator()));
    }
    List<Evaluator> evaluators = List.of(evaluator(), evaluator(), evaluator());
    ExecutorService pool = Executors.newFixedThreadPool(8);
    try {
      List<Future<Object>> futures = new ArrayList<>();
      for (int round = 0; round < 3; round++) {
        for (int i = 0; i < calls.size(); i++) {
          Call call = calls.get(i);
          Evaluator evaluator = evaluators.get((i + round) % evaluators.size());
          futures.add(pool.submit((Callable<Object>) () -> call.outcome(evaluator)));
        }
      }
      for (int i = 0; i < futures.size(); i++) {
        assertEquals(alone.get(i % calls.size()), futures.get(i).get());
      }
    } finally {
      pool.shutdownNow();
    }
  }
}
