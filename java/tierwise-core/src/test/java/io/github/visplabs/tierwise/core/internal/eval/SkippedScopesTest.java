package io.github.visplabs.tierwise.core.internal.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.Context;
import io.github.visplabs.tierwise.core.Evaluator;
import io.github.visplabs.tierwise.core.FactSet;
import io.github.visplabs.tierwise.core.TierwiseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

/**
 * {@code skippedScopes} is a property of the context: it never names the root level (PROV-57), holds at most one
 * entry for each scope (PROV-59), and is the same in every result computed from one context (PROV-60).
 *
 * <p>In the standard test model, {@code site} (a level) and {@code promo} (an axis) are closed under
 * {@code ignore}, so an unknown node there is skipped.
 */
class SkippedScopesTest {

  private static final TestModels.Builder MODEL = TestModels.standard();
  private static final List<FactSet> FACT_SETS = List.of(FactSet.empty(),
      FactSet.builder().fact("customer.active", true).fact("customer.credit_hold", false).build(),
      FactSet.builder().fact("order.total", "2").build());

  private final Evaluator evaluator = TestModels.evaluator(MODEL, node -> Map.of(), node -> Map.of(), null);

  /** The context of one property run: the node given for each scope, or {@code null} for none. */
  record Nodes(String division, String site, String account, String seller, String promo) {

    Context context() {
      Context.Builder builder = Context.builder();
      if (division != null) {
        builder.level("division", division);
      }
      if (site != null) {
        builder.level("site", site);
      }
      if (account != null) {
        builder.level("account", account);
      }
      if (seller != null) {
        builder.axis("seller", seller);
      }
      if (promo != null) {
        builder.axis("promo", promo);
      }
      return builder.build();
    }

    List<Object> expectedSkipped() {
      List<Object> expected = new ArrayList<>();
      if (site != null && !site.equals("s1") && !site.equals("s2")) {
        expected.add(entry("level", "site", site));
      }
      if (promo != null && !promo.equals("spring")) {
        expected.add(entry("axis", "promo", promo));
      }
      return expected;
    }
  }

  private static Map<String, Object> entry(String scope, String name, String node) {
    Map<String, Object> entry = new LinkedHashMap<>();
    entry.put("scope", scope);
    entry.put("name", name);
    entry.put("node", node);
    entry.put("reason", "UNKNOWN_NODE");
    return entry;
  }

  @Provide
  Arbitrary<Nodes> nodes() {
    return Combinators.combine(Arbitraries.of("north", "south").injectNull(0.4),
        Arbitraries.of("s1", "s2", "s9", "S1").injectNull(0.3), Arbitraries.of("a1").injectNull(0.5),
        Arbitraries.of("acme").injectNull(0.5), Arbitraries.of("spring", "autumn", "Spring").injectNull(0.3))
        .as(Nodes::new);
  }

  /** The skippedScopes of every result one context gives: every key, both resolve variants, and decide. */
  private List<Object> everySkippedScopes(Context context) {
    List<Object> lists = new ArrayList<>();
    for (String key : MODEL.keys.keySet()) {
      lists.add(evaluator.resolve(context, key).toJson().get("skippedScopes"));
      try {
        lists.add(evaluator.resolveLocal(context, key).toJson().get("skippedScopes"));
      } catch (TierwiseException e) {
        assertEquals(Code.E_PROVIDER_REQUIRED, e.code());
      }
    }
    for (FactSet facts : FACT_SETS) {
      lists.add(evaluator.decide(context, "f.gate", facts).toJson().get("skippedScopes"));
    }
    return lists;
  }

  @Test
  void PROV_57_aContextThatNamesTheRootLevelRaisesContextInvalid() {
    for (Context root : List.of(Context.builder().level("global", "").build(),
        Context.builder().level("global", "x").level("site", "s9").build(), Context.builder().axis("global", "x").build())) {
      assertEquals(Code.E_CONTEXT_INVALID, assertThrows(TierwiseException.class, () -> evaluator.resolve(root, "k.all")).code());
      assertEquals(Code.E_CONTEXT_INVALID,
          assertThrows(TierwiseException.class, () -> evaluator.resolveLocal(root, "k.note")).code());
      assertEquals(Code.E_CONTEXT_INVALID,
          assertThrows(TierwiseException.class, () -> evaluator.decide(root, "f.gate", FactSet.empty())).code());
    }
  }

  @SuppressWarnings("unchecked")
  @Property(tries = 60)
  void PROV_57_skippedScopesNeverNamesTheRootLevel(@ForAll("nodes") Nodes nodes) {
    for (Object list : everySkippedScopes(nodes.context())) {
      for (Map<String, Object> entry : (List<Map<String, Object>>) list) {
        assertTrue(!entry.get("name").equals("global"), list.toString());
      }
    }
  }

  @SuppressWarnings("unchecked")
  @Property(tries = 60)
  void PROV_59_skippedScopesHoldsAtMostOneEntryForEachScope(@ForAll("nodes") Nodes nodes) {
    for (Object list : everySkippedScopes(nodes.context())) {
      Set<String> scopes = new HashSet<>();
      for (Map<String, Object> entry : (List<Map<String, Object>>) list) {
        assertTrue(scopes.add(entry.get("scope") + ":" + entry.get("name")), list.toString());
      }
    }
  }

  @Test
  void PROV_59_twoSkippedScopesGiveOneEntryEach() {
    Context both = Context.builder().level("site", "s9").axis("promo", "autumn").build();
    assertEquals(List.of(entry("level", "site", "s9"), entry("axis", "promo", "autumn")),
        evaluator.resolve(both, "k.union").toJson().get("skippedScopes"));
  }

  @Property(tries = 80)
  void PROV_60_everyResultFromOneContextReportsTheSameSkippedScopes(@ForAll("nodes") Nodes nodes) {
    List<Object> lists = everySkippedScopes(nodes.context());
    assertTrue(lists.size() > MODEL.keys.size());
    for (Object list : lists) {
      assertEquals(nodes.expectedSkipped(), list, nodes.toString());
    }
  }
}
