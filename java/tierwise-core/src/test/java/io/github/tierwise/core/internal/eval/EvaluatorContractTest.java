package io.github.tierwise.core.internal.eval;

import static io.github.tierwise.core.internal.eval.TestModels.mapOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Context;
import io.github.tierwise.core.Decision;
import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.LevelValueProvider;
import io.github.tierwise.core.LoadedBundle;
import io.github.tierwise.core.Resolved;
import io.github.tierwise.core.Tierwise;
import io.github.tierwise.core.TierwiseException;
import io.github.tierwise.core.internal.model.LoadedModel;
import io.github.tierwise.core.internal.model.TypeDecl;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;
import org.junit.jupiter.api.Test;

/** Immutability, purity, determinism and concurrency of operations (EVAL-13, EVAL-46 to EVAL-52, DET-7, DET-8). */
@SuppressWarnings("unchecked")
class EvaluatorContractTest {

  private static final Context ACCOUNT = Context.builder().level("account", "a1").build();
  private static final Context NORTH = Context.builder().level("division", "north").level("account", "a1").build();

  private final TestModels.Builder builder = TestModels.standard();

  private Evaluator evaluator(LevelValueProvider account) {
    return TestModels.evaluator(builder, account, node -> Map.of(), null);
  }

  private static Map<String, Object> freshProviderMap() {
    Map<String, Object> values = new HashMap<>();
    values.put("k.all", true);
    values.put("k.union", new ArrayList<>(List.of("blue", "red")));
    values.put("k.deep", mapOf("cap", 50L, "tags", new ArrayList<>(List.of("x"))));
    return values;
  }

  @Test
  void EVAL_13_noOperationHasAParameterThatSelectsOneScopeOrNode() {
    for (Method method : Evaluator.class.getMethods()) {
      List<Class<?>> types = Arrays.asList(method.getParameterTypes());
      assertTrue(Context.class.equals(types.get(0)) && String.class.equals(types.get(1)), method.getName());
      assertTrue(types.size() == 2 || (types.size() == 3 && types.get(2).equals(FactSet.class)), method.getName());
    }
    assertEquals(3, Evaluator.class.getMethods().length);
  }

  @Test
  void EVAL_46_anOperationDoesNotModifyTheLoadedBundle() {
    LoadedModel loaded = builder.loaded();
    Evaluator evaluator = Tierwise.evaluator(loaded, EvaluatorOptions.builder()
        .provider("account", TestModels.values(freshProviderMap()), Duration.ofSeconds(1))
        .provider("seat", node -> Map.of(), Duration.ofSeconds(1)).build());
    int hash = loaded.model().hashCode();
    String text = loaded.model().toString();
    for (String key : loaded.model().keys().keySet()) {
      try {
        evaluator.resolve(NORTH, key);
      } catch (TierwiseException expected) {
        // some contexts do not fit every key; the bundle must stay untouched either way
      }
    }
    evaluator.decide(NORTH, "f.gate", FactSet.builder().fact("customer.active", true).build());
    assertEquals(hash, loaded.model().hashCode());
    assertEquals(text, loaded.model().toString());
    assertEquals(builder.model(), loaded.model());
    assertThrows(UnsupportedOperationException.class, () -> loaded.model().keys().clear());
    assertThrows(UnsupportedOperationException.class, () -> loaded.model().features().get("f.gate").policies().clear());
  }

  @Test
  void EVAL_47_aReturnedValueSharesNoMutableStateWithTheBundleOrTheEvaluator() {
    Evaluator evaluator = evaluator(TestModels.values(freshProviderMap()));
    Resolved resolved = evaluator.resolve(NORTH, "k.deep");
    assertThrows(UnsupportedOperationException.class, () -> ((Map<String, Object>) resolved.value()).put("x", 1L));
    assertThrows(UnsupportedOperationException.class, () -> resolved.contributions().clear());
    assertThrows(UnsupportedOperationException.class, () -> resolved.skippedScopes().clear());
    assertThrows(UnsupportedOperationException.class, () -> resolved.toJson().put("x", 1));
    Decision decision = evaluator.decide(NORTH, "f.gate", FactSet.empty());
    assertThrows(UnsupportedOperationException.class, () -> decision.trace().clear());
    assertThrows(UnsupportedOperationException.class, () -> decision.toJson().put("x", 1));
    Resolved set = evaluator.resolve(NORTH, "k.union");
    assertThrows(UnsupportedOperationException.class, () -> ((List<Object>) set.value()).add("green"));
  }

  @Test
  void EVAL_49_anOperationDoesNotModifyItsInputs() {
    Map<String, Object> providerMap = freshProviderMap();
    Map<String, Object> providerSnapshot = deepCopy(providerMap);
    Context context = Context.builder().level("account", "a1").level("division", "north").build();
    Map<String, Object> factMap = mapOf("customer.active", true, "customer.credit_hold", true);
    Map<String, Object> childMap = mapOf("item.stock", "STOCKED");
    List<Map<String, Object>> childList = new ArrayList<>(List.of(childMap));
    Map<String, List<Map<String, Object>>> children = new LinkedHashMap<>();
    children.put("item", childList);
    FactSet facts = FactSet.of(factMap, children);
    Evaluator evaluator = evaluator(node -> providerMap);
    evaluator.resolve(context, "k.union");
    evaluator.resolve(context, "k.deep");
    evaluator.decide(context, "f.gate", facts);
    assertEquals(providerSnapshot, providerMap, "the map a provider returns is not modified");
    assertEquals(mapOf("customer.active", true, "customer.credit_hold", true), factMap);
    assertEquals(List.of(mapOf("item.stock", "STOCKED")), childList);
    assertEquals(Map.of("account", "a1", "division", "north"), context.levels());
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> deepCopy(Map<String, Object> source) {
    Map<String, Object> copy = new HashMap<>();
    source.forEach((key, value) -> copy.put(key, value instanceof Map<?, ?> m ? deepCopy((Map<String, Object>) m)
        : value instanceof List<?> l ? new ArrayList<>(l) : value));
    return copy;
  }

  @Test
  void EVAL_50_aChangeToAReturnedResultCannotChangeALaterResult() {
    builder.value("global", "", "k.union", List.of("red"));
    Evaluator evaluator = evaluator(TestModels.values(Map.of()));
    Resolved first = evaluator.resolve(Context.empty(), "k.union");
    try {
      ((List<Object>) first.value()).add("blue");
    } catch (UnsupportedOperationException expected) {
      // the result is immutable
    }
    assertEquals(List.of("red"), evaluator.resolve(Context.empty(), "k.union").value());
    assertNotSame(first, evaluator.resolve(Context.empty(), "k.union"));
  }

  @Test
  void EVAL_48_anEvaluatorHasOnlyFinalFieldsAndOptionChangesAfterCreationDoNotAffectIt() {
    for (Class<?> type : List.of(EvaluatorImpl.class, Resolver.class, DecisionEngine.class, ProviderCalls.class,
        ContextValidator.class, KeyResolution.class)) {
      for (Field field : type.getDeclaredFields()) {
        if (!Modifier.isStatic(field.getModifiers())) {
          assertTrue(Modifier.isFinal(field.getModifiers()), type.getSimpleName() + "." + field.getName());
        }
      }
    }
    EvaluatorOptions.Builder options = EvaluatorOptions.builder().provider("account", TestModels.failing(), Duration.ofSeconds(1))
        .provider("seat", node -> Map.of(), Duration.ofSeconds(1));
    Evaluator evaluator = Tierwise.evaluator(builder.loaded(), options.build());
    options.provider("account", node -> mapOf("k.all", true), Duration.ofSeconds(1)).listener(event -> {
      throw new AssertionError("the listener of a later build must not be used");
    });
    assertEquals(Resolved.Contribution.Label.UNAVAILABLE, evaluator.resolve(ACCOUNT, "k.all").contributions().get(0).effect());
  }

  @Test
  void EVAL_51_aCallReturnsTheSameResultWhateverRanBefore() {
    Evaluator evaluator = evaluator(TestModels.values(freshProviderMap()));
    Map<String, Object> first = evaluator.resolve(NORTH, "k.deep").toJson();
    Map<String, Object> decisionFirst = evaluator.decide(NORTH, "f.gate", FactSet.empty()).toJson();
    for (String key : builder.keys.keySet()) {
      try {
        evaluator.resolve(NORTH, key);
      } catch (TierwiseException expected) {
        // irrelevant here
      }
    }
    evaluator.decide(Context.empty(), "f.gate", FactSet.builder().fact("customer.active", true).build());
    assertEquals(first, evaluator.resolve(NORTH, "k.deep").toJson());
    assertEquals(decisionFirst, evaluator.decide(NORTH, "f.gate", FactSet.empty()).toJson());
    Evaluator other = evaluator(TestModels.values(freshProviderMap()));
    assertEquals(first, other.resolve(NORTH, "k.deep").toJson());
  }

  @Property(tries = 60)
  void EVAL_51_aDecisionIsAPureFunctionOfBundleContextAndFacts(@ForAll boolean active, @ForAll boolean hold,
      @ForAll("stocks") List<String> stocks, @ForAll("divisions") String division) {
    Evaluator evaluator = TestModels.standard().evaluator(Map.of("account", node -> Map.of(), "seat", node -> Map.of()));
    FactSet.Builder facts = FactSet.builder().fact("customer.active", active).fact("customer.credit_hold", hold);
    stocks.forEach(stock -> facts.child("item", Map.of("item.stock", stock)));
    Context context = division == null ? Context.empty() : Context.builder().level("division", division).build();
    FactSet built = facts.build();
    Decision a = evaluator.decide(context, "f.gate", built);
    Decision b = evaluator.decide(context, "f.gate", built);
    assertEquals(a, b);
    assertEquals(a.toJson(), b.toJson());
  }

  @Provide
  Arbitrary<List<String>> stocks() {
    return Arbitraries.of("STOCKED", "SPECIAL", "DROP").list().ofMaxSize(4);
  }

  @Provide
  Arbitrary<String> divisions() {
    return Arbitraries.of("north", "south").injectNull(0.3);
  }

  @Test
  void EVAL_52_concurrentCallsReturnWhatEachReturnsAlone() throws Exception {
    Evaluator evaluator = evaluator(TestModels.values(freshProviderMap()));
    List<Callable<List<Object>>> jobs = new ArrayList<>();
    List<List<Object>> alone = new ArrayList<>();
    for (int i = 0; i < 6; i++) {
      Callable<List<Object>> job = job(evaluator, i);
      alone.add(job.call());
      jobs.add(job);
    }
    ExecutorService pool = Executors.newFixedThreadPool(12);
    try {
      List<Future<List<Object>>> futures = new ArrayList<>();
      for (int round = 0; round < 10; round++) {
        for (Callable<List<Object>> job : jobs) {
          futures.add(pool.submit(job));
        }
      }
      for (int i = 0; i < futures.size(); i++) {
        assertEquals(alone.get(i % jobs.size()), futures.get(i).get());
      }
    } finally {
      pool.shutdownNow();
    }
  }

  private static Callable<List<Object>> job(Evaluator evaluator, int variant) {
    return () -> {
      List<Object> results = new ArrayList<>();
      Context context = variant % 2 == 0 ? NORTH : ACCOUNT;
      results.add(evaluator.resolve(context, "k.deep").toJson());
      results.add(evaluator.resolve(context, variant % 3 == 0 ? "k.union" : "k.all").toJson());
      results.add(evaluator.decide(context, "f.gate", FactSet.builder().fact("customer.active", variant % 2 == 0).build())
          .toJson());
      return results;
    };
  }

  @Test
  void DET_7_resultsDoNotDependOnTheDefaultLocaleOrTimeZone() {
    Evaluator evaluator = evaluator(TestModels.values(freshProviderMap()));
    FactSet facts = FactSet.builder().fact("customer.active", true).fact("customer.credit_hold", false).build();
    Map<String, Object> before = evaluator.resolve(NORTH, "k.deep").toJson();
    Map<String, Object> decisionBefore = evaluator.decide(NORTH, "f.gate", facts).toJson();
    Locale locale = Locale.getDefault();
    TimeZone zone = TimeZone.getDefault();
    try {
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));
      TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
      Evaluator turkish = evaluator(TestModels.values(freshProviderMap()));
      assertEquals(before, turkish.resolve(NORTH, "k.deep").toJson());
      assertEquals(decisionBefore, turkish.decide(NORTH, "f.gate", facts).toJson());
      Locale.setDefault(Locale.forLanguageTag("ar-EG"));
      TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
      assertEquals(before, evaluator(TestModels.values(freshProviderMap())).resolve(NORTH, "k.deep").toJson());
    } finally {
      Locale.setDefault(locale);
      TimeZone.setDefault(zone);
    }
  }

  @Test
  void DET_8_resultsDoNotDependOnTheIterationOrderOfInputMaps() {
    Evaluator evaluator = evaluator(TestModels.values(freshProviderMap()));
    Map<String, Object> forward = new LinkedHashMap<>();
    forward.put("customer.active", true);
    forward.put("customer.credit_hold", true);
    Map<String, Object> backward = new LinkedHashMap<>();
    backward.put("customer.credit_hold", true);
    backward.put("customer.active", true);
    List<Map<String, Object>> items = List.of(mapOf("item.stock", "STOCKED"), mapOf("item.stock", "DROP"));
    List<Map<String, Object>> reversed = List.of(items.get(1), items.get(0));
    Context south = Context.builder().level("division", "south").level("account", "a1").build();
    Context southReversed = Context.of(new LinkedHashMap<>(Map.of("account", "a1", "division", "south")), Map.of());
    Decision a = evaluator.decide(south, "f.gate", FactSet.of(forward, Map.of("item", items)));
    Decision b = evaluator.decide(southReversed, "f.gate", FactSet.of(backward, Map.of("item", reversed)));
    assertEquals(a.toJson(), b.toJson());
    Resolved x = evaluator.resolve(south, "k.deep");
    Resolved y = evaluator.resolve(southReversed, "k.deep");
    assertEquals(x.toJson(), y.toJson());
  }

  /** What a stub provider does for one level in the SVC-21 property. */
  enum Behaviour { TRUE, FALSE, INVALID, NULL_RESULT, THROW, OMIT }

  @Property(tries = 40)
  void SVC_21_aResultDoesNotDependOnTheOrderOrConcurrencyOfProviderCalls(
      @ForAll @IntRange(min = 0, max = 4) int accountDelay, @ForAll @IntRange(min = 0, max = 4) int seatDelay,
      @ForAll Behaviour accountBehaviour, @ForAll Behaviour seatBehaviour) {
    TestModels.Builder model = TestModels.standard();
    model.key("k.both", TestModels.key(TypeDecl.simple("boolean"), "all", null, List.of("global", "account", "seat"), null,
        "deny"));
    Context both = Context.builder().level("account", "a1").level("seat", "t1").build();
    Map<String, Object> reference = resolveBoth(model, both, behaviourProvider(accountBehaviour, 0),
        behaviourProvider(seatBehaviour, 0));
    Map<String, Object> delayed = resolveBoth(model, both, behaviourProvider(accountBehaviour, accountDelay),
        behaviourProvider(seatBehaviour, seatDelay));
    assertEquals(reference, delayed);
    Map<String, Object> swapped = resolveBoth(model, both, behaviourProvider(accountBehaviour, seatDelay),
        behaviourProvider(seatBehaviour, accountDelay));
    assertEquals(reference, swapped);
  }

  private static Map<String, Object> resolveBoth(TestModels.Builder model, Context context, LevelValueProvider account,
      LevelValueProvider seat) {
    return TestModels.evaluator(model, account, seat, null).resolve(context, "k.both").toJson();
  }

  private static LevelValueProvider behaviourProvider(Behaviour behaviour, int delayMillis) {
    return node -> {
      if (delayMillis > 0) {
        Thread.sleep(delayMillis);
      }
      switch (behaviour) {
        case TRUE:
          return mapOf("k.both", true);
        case FALSE:
          return mapOf("k.both", false);
        case INVALID:
          return mapOf("k.both", "not a boolean");
        case NULL_RESULT:
          return null;
        case THROW:
          throw new java.io.IOException("down");
        default:
          return Map.of();
      }
    };
  }

  @Property(tries = 80)
  void PROV_9_everyCallerErrorIsRaisedWithAnEvaluationCodeAndNeverReturnedAsAResult(
      @ForAll("garbageContexts") Object rawContext, @ForAll("garbageFactSets") Object rawFacts,
      @ForAll("names") String name) {
    Evaluator evaluator = evaluator(TestModels.values(Map.of()));
    Context context = Context.fromJson(rawContext);
    FactSet facts = FactSet.fromJson(rawFacts);
    for (Callable<Object> call : List.<Callable<Object>>of(() -> evaluator.resolve(context, name),
        () -> evaluator.resolveLocal(context, name), () -> evaluator.decide(context, name, facts))) {
      try {
        Object result = call.call();
        assertTrue(result instanceof Resolved || result instanceof Decision);
      } catch (TierwiseException e) {
        assertTrue(Code.evaluation().contains(e.code()), e.code());
      } catch (Exception e) {
        throw new AssertionError("only TierwiseException may be raised", e);
      }
    }
  }

  @Provide
  Arbitrary<Object> garbageContexts() {
    Arbitrary<Object> scopes = Arbitraries.of("global", "division", "site", "account", "seller", "warehouse", "");
    Arbitrary<Object> nodes = Arbitraries.of("north", "west", "s9", "a1", "", "x\uD83D", 7L, "acme");
    Arbitrary<Object> maps = Arbitraries.maps(scopes, nodes).ofMaxSize(3).map(m -> (Object) m);
    Arbitrary<Object> shape = Arbitraries.of(null, "levels", List.of(), 5L);
    return Arbitraries.oneOf(maps.map(m -> (Object) Map.of("levels", m)), maps.map(m -> (Object) Map.of("axes", m)),
        maps.map(m -> (Object) Map.of("levels", m, "axes", m)), shape);
  }

  @Provide
  Arbitrary<Object> garbageFactSets() {
    Arbitrary<Object> factNames = Arbitraries.of("customer.active", "order.total", "item.stock", "nope", "customer.credit_hold");
    Arbitrary<Object> values = Arbitraries.of(true, "true", 5L, 1.5, "1.5", null, "STOCKED", List.of());
    Arbitrary<Object> maps = Arbitraries.maps(factNames, values).ofMaxSize(3).map(m -> (Object) new HashMap<>(m));
    return Arbitraries.oneOf(maps.map(m -> (Object) Map.of("facts", m)),
        maps.map(m -> (Object) Map.of("children", Map.of("item", List.of(m)))), Arbitraries.of(null, "x", List.of()));
  }

  @Provide
  Arbitrary<String> names() {
    return Arbitraries.of(null, "", "k.all", "f.gate", "k.deep", "no.such");
  }

  @Test
  void PROV_49_noFailureEntryUnderDenyAppearsForAnOrderDependentKey() {
    // A loaded bundle is not checked for deny on an order-dependent key (SVC-11 stops the compiler), so a
    // hand-built one proves that the evaluator never produces such an entry.
    builder.key("k.bad_deny", TestModels.key(TypeDecl.simple("string"), "override", "d", List.of("global", "account"), null,
        "deny"));
    Resolved resolved = evaluator(TestModels.failing()).resolve(ACCOUNT, "k.bad_deny");
    assertEquals("d", resolved.value());
    assertTrue(resolved.contributions().stream().noneMatch(entry -> entry.onSourceFailure().equals(java.util.Optional.of("deny"))));
    assertEquals(1, resolved.contributions().size());
  }

  @Test
  void TYPE_38_anEvaluatorConsultsNoTimeZoneData() {
    Evaluator evaluator = evaluator(TestModels.values(Map.of()));
    FactSet facts = FactSet.fromJson(Map.of("facts", Map.of("order.zone", "Mars/Olympus_Mons")));
    evaluator.decide(Context.empty(), "f.gate", facts);
    assertFalse(java.util.Arrays.asList(TimeZone.getAvailableIDs()).contains("Mars/Olympus_Mons"));
  }

  @Test
  void TYPE_73_aRuntimeValueIsNeverReportedWithACodeOfSectionFour() {
    Evaluator evaluator = evaluator(TestModels.values(Map.of()));
    for (Object bad : new Object[] {"x", 1.5, 700L, List.of(), true}) {
      TierwiseException e = assertThrows(TierwiseException.class,
          () -> evaluator.decide(Context.empty(), "f.gate", FactSet.fromJson(Map.of("facts", mapOf("order.lines", bad)))));
      assertEquals(Code.E_FACTS_INVALID, e.code());
    }
  }

  @Test
  void BUNDLE_145_theEvaluatorApiHasNoSidecarInputAndNeverRaisesProvenanceInvalid() {
    assertFalse(Code.evaluation().contains(Code.E_PROVENANCE_INVALID));
    for (Method method : Tierwise.class.getMethods()) {
      if (method.getName().equals("evaluator")) {
        assertEquals(List.of(LoadedBundle.class, EvaluatorOptions.class), Arrays.asList(method.getParameterTypes()));
      }
    }
    for (Method method : Evaluator.class.getMethods()) {
      for (Class<?> type : method.getParameterTypes()) {
        assertTrue(type == Context.class || type == String.class || type == FactSet.class, method.getName());
      }
    }
  }

  @Test
  void BUNDLE_153_aSidecarIsNotPartOfTheEvaluatorOptions() {
    for (Method method : EvaluatorOptions.Builder.class.getMethods()) {
      assertFalse(method.getName().toLowerCase(Locale.ROOT).contains("sidecar"), method.getName());
      assertFalse(method.getName().toLowerCase(Locale.ROOT).contains("provenance"), method.getName());
    }
  }

  @Test
  void EVAL_55_theOnlyBundleValueAnOperationAcceptsIsOneThatLoadBundleReturned() throws Exception {
    assertTrue(LoadedBundle.class.isSealed());
    assertEquals(List.of(LoadedModel.class), Arrays.asList(LoadedBundle.class.getPermittedSubclasses()));
    assertEquals(LoadedBundle.class, Tierwise.class.getMethod("loadBundle", byte[].class).getReturnType());
  }

  @Test
  void EVAL_3_loadBundleReturnsItsResultDirectlyWithNoPromiseOrCallback() throws Exception {
    Method load = Tierwise.class.getMethod("loadBundle", byte[].class);
    assertEquals(LoadedBundle.class, load.getReturnType());
    assertEquals(1, load.getParameterCount());
  }
}
