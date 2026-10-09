package io.github.tierwise.core.internal.eval;

import io.github.tierwise.core.Evaluator;
import io.github.tierwise.core.EvaluatorOptions;
import io.github.tierwise.core.LevelValueProvider;
import io.github.tierwise.core.Tierwise;
import io.github.tierwise.core.internal.model.BindingDef;
import io.github.tierwise.core.internal.model.ConditionDef;
import io.github.tierwise.core.internal.model.EntityDef;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.FeatureDef;
import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.KeyDef;
import io.github.tierwise.core.internal.model.LoadedModel;
import io.github.tierwise.core.internal.model.Model;
import io.github.tierwise.core.internal.model.PolicyDef;
import io.github.tierwise.core.internal.model.Returns;
import io.github.tierwise.core.internal.model.RuleDef;
import io.github.tierwise.core.internal.model.ScopeDef;
import io.github.tierwise.core.internal.model.SettingDef;
import io.github.tierwise.core.internal.model.TypeDecl;
import io.github.tierwise.core.internal.model.Values;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hand-built models for the unit tests of the evaluator. The names are test data only.
 *
 * <p>The hierarchy is {@code global, division, site, account, seat} with the axes {@code seller} and
 * {@code promo}. {@code division} and {@code seller} are closed with {@code error}; {@code site} and
 * {@code promo} are closed with {@code ignore}; {@code account} and {@code seat} are open and
 * service-sourced.
 */
final class TestModels {

  static final String VERSION = "sha256:" + "a".repeat(64);
  static final Duration TIMEOUT = Duration.ofSeconds(5);

  private TestModels() {
  }

  /** Collects the pieces of a model; start from {@link #standard()}. */
  static final class Builder {
    final Map<String, KeyDef> keys = new LinkedHashMap<>();
    final Map<String, Map<String, Map<String, Object>>> values = new LinkedHashMap<>();
    final Map<String, FeatureDef> features = new LinkedHashMap<>();
    final Map<String, FactDef> facts = new LinkedHashMap<>();
    final Map<String, EntityDef> entities = new LinkedHashMap<>();
    Hierarchy hierarchy;

    Builder key(String name, KeyDef key) {
      keys.put(name, key);
      return this;
    }

    Builder value(String scope, String node, String key, Object value) {
      values.computeIfAbsent(scope, s -> new LinkedHashMap<>())
          .computeIfAbsent(node, n -> new LinkedHashMap<>()).put(key, value);
      return this;
    }

    Builder feature(String name, FeatureDef feature) {
      features.put(name, feature);
      return this;
    }

    Model model() {
      return new Model("1", "0.1", hierarchy, entities, facts, keys, new Values(values), features);
    }

    LoadedModel loaded() {
      return new LoadedModel(VERSION, model());
    }

    Evaluator evaluator(EvaluatorOptions options) {
      return Tierwise.evaluator(loaded(), options);
    }

    Evaluator evaluator(Map<String, LevelValueProvider> providers) {
      EvaluatorOptions.Builder options = EvaluatorOptions.builder();
      providers.forEach((level, provider) -> options.provider(level, provider, TIMEOUT));
      return evaluator(options.build());
    }
  }

  /** The standard hierarchy with the keys, facts and features that most tests use. */
  static Builder standard() {
    Builder b = new Builder();
    Map<String, ScopeDef> scopes = new LinkedHashMap<>();
    scopes.put("division", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, List.of("north", "south"),
        ScopeDef.OnUnknownNode.ERROR));
    scopes.put("site", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, List.of("s1", "s2"),
        ScopeDef.OnUnknownNode.IGNORE));
    scopes.put("account", ScopeDef.openCatalog(ScopeDef.ValuesSource.SERVICE));
    scopes.put("seat", ScopeDef.openCatalog(ScopeDef.ValuesSource.SERVICE));
    scopes.put("seller", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, List.of("acme"),
        ScopeDef.OnUnknownNode.ERROR));
    scopes.put("promo", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, List.of("spring"),
        ScopeDef.OnUnknownNode.IGNORE));
    b.hierarchy = new Hierarchy(List.of("global", "division", "site", "account", "seat"),
        List.of("seller", "promo"), scopes);

    TypeDecl colours = TypeDecl.set(List.of("red", "green", "blue"));
    List<String> fileAndAccount = List.of("global", "division", "site", "account");
    b.key("k.all", key(TypeDecl.simple("boolean"), "all", null, fileAndAccount, null, "deny"));
    b.key("k.all_skip", key(TypeDecl.simple("boolean"), "all", null, fileAndAccount, null, "skip"));
    b.key("k.any", key(TypeDecl.simple("boolean"), "any", null, List.of("global", "division", "account", "seller"),
        null, "skip"));
    b.key("k.intersect", key(colours, "intersect", null, fileAndAccount, null, "deny"));
    b.key("k.union", key(colours, "union", null, List.of("global", "division", "account", "promo"), null, "skip"));
    b.key("k.min", key(TypeDecl.integer(1L, 500L), "min", null, fileAndAccount, null, "deny"));
    b.key("k.min_skip", key(TypeDecl.integer(1L, 500L), "min", null, fileAndAccount, null, "skip"));
    b.key("k.max", key(TypeDecl.integer(0L, 500L), "max", null,
        List.of("global", "division", "account", "seller"), null, "skip"));
    b.key("k.seat", key(TypeDecl.simple("boolean"), "all", null, List.of("global", "seat"), null, "deny"));
    b.key("k.note", key(TypeDecl.simple("string"), "override", "none", List.of("global", "division"), null, null));
    b.key("k.label", key(TypeDecl.simple("string"), "override", "standard", fileAndAccount, null, "skip"));
    List<String> withAxes = List.of("global", "division", "account", "seller", "promo");
    b.key("k.max_across", key(TypeDecl.integer(0L, 100L), "override", 3L, withAxes, "max", "skip"));
    b.key("k.min_across", key(TypeDecl.integer(0L, 100L), "override", 3L, withAxes, "min", "skip"));
    b.key("k.axis_wins", key(TypeDecl.simple("string"), "override", "d", withAxes, "axis_wins", "skip"));
    b.key("k.hierarchy_wins", key(TypeDecl.simple("string"), "override", "d", withAxes, "hierarchy_wins", "skip"));
    b.key("k.decimal", key(TypeDecl.simple("decimal"), "override", "0", List.of("global", "account"), null, "skip"));
    Map<String, TypeDecl> windowFields = new LinkedHashMap<>();
    windowFields.put("from", stringFormat("time_hhmm"));
    windowFields.put("to", stringFormat("time_hhmm").asOptional());
    Map<String, TypeDecl> fields = new LinkedHashMap<>();
    fields.put("cap", TypeDecl.integer(0L, 100L));
    fields.put("unit", TypeDecl.simple("string"));
    fields.put("tags", TypeDecl.list(TypeDecl.simple("string")));
    fields.put("window", TypeDecl.object(windowFields).asOptional());
    TypeDecl deep = TypeDecl.object(fields);
    Map<String, Object> deepDefault = Map.of("cap", 10L, "unit", "items", "tags", List.of("a"));
    b.key("k.deep", key(deep, "deep_merge", deepDefault, withAxes, "axis_wins", "skip"));
    b.key("k.deep_hierarchy", key(deep, "deep_merge", deepDefault, withAxes, "hierarchy_wins", "skip"));
    b.key("k.deep_root", key(deep, "deep_merge", null, List.of("global", "division", "account"), null, "skip"));
    b.key("k.object_override", new KeyDef(TypeDecl.object(windowFields), "override",
        Optional.of(Map.of("from", "08:00")), List.of("global", "account"), Optional.empty(), Optional.of("skip"),
        Optional.empty()));
    b.key("k.kinded", new KeyDef(TypeDecl.simple("boolean"), "all", Optional.empty(), fileAndAccount,
        Optional.empty(), Optional.of("deny"), Optional.of("capability")));
    addDecisionModel(b);
    return b;
  }

  private static void addDecisionModel(Builder b) {
    b.entities.put("customer", new EntityDef(List.of(), List.of()));
    b.entities.put("order", new EntityDef(List.of("customer"), List.of("item")));
    b.entities.put("item", new EntityDef(List.of(), List.of()));
    b.entities.put("invoice", new EntityDef(List.of(), List.of("order")));
    b.facts.put("customer.active", new FactDef("customer", TypeDecl.simple("boolean")));
    b.facts.put("customer.credit_hold", new FactDef("customer", TypeDecl.simple("boolean")));
    b.facts.put("order.total", new FactDef("order", TypeDecl.simple("decimal")));
    b.facts.put("order.lines", new FactDef("order", TypeDecl.integer(0L, 100L)));
    b.facts.put("order.label", new FactDef("order", TypeDecl.simple("string")));
    b.facts.put("order.window", new FactDef("order", stringFormat("time_hhmm")));
    b.facts.put("order.zone", new FactDef("order", stringFormat("iana_tz")));
    b.facts.put("order.kind", new FactDef("order", TypeDecl.enumeration(List.of("RETAIL", "TRADE"))));
    b.facts.put("item.stock", new FactDef("item", TypeDecl.enumeration(List.of("STOCKED", "SPECIAL", "DROP"))));
    b.facts.put("item.price", new FactDef("item", TypeDecl.simple("decimal")));
    b.facts.put("invoice.paid", new FactDef("invoice", TypeDecl.simple("boolean")));

    Map<String, SettingDef> strictSettings = new LinkedHashMap<>();
    strictSettings.put("allowed", new SettingDef(SettingDef.Kind.LIST_OF, "item.stock", List.of()));
    PolicyDef strict = new PolicyDef(strictSettings, "DENY",
        List.of("customer.active", "customer.credit_hold", "item.stock"),
        List.of(
            new RuleDef("good-credit", "Good credit",
                List.of(new ConditionDef("customer.active", "eq", true),
                    new ConditionDef("customer.credit_hold", "eq", false)),
                "ALLOW"),
            new RuleDef("hold-allowed-stock", "On hold, every item is an allowed stock type",
                List.of(new ConditionDef("customer.active", "eq", true),
                    new ConditionDef("customer.credit_hold", "eq", true),
                    new ConditionDef("item.stock", "all_in", "$allowed")),
                "ALLOW")));
    PolicyDef open = new PolicyDef(Map.of(), "ALLOW", List.of(), List.of());
    Map<String, SettingDef> totalSettings = new LinkedHashMap<>();
    totalSettings.put("expected", new SettingDef(SettingDef.Kind.VALUE_OF, "order.total", "1.5"));
    PolicyDef totals = new PolicyDef(totalSettings, "DENY", List.of("order.total"),
        List.of(new RuleDef("total-matches", "Total matches", List.of(new ConditionDef("order.total", "eq", "$expected")),
            "ALLOW")));
    Map<String, PolicyDef> policies = new LinkedHashMap<>();
    policies.put("strict", strict);
    policies.put("open", open);
    policies.put("totals", totals);
    Map<String, Map<String, BindingDef>> bindings = new LinkedHashMap<>();
    bindings.put("global", Map.of("", new BindingDef("strict", Map.of())));
    Map<String, BindingDef> division = new LinkedHashMap<>();
    division.put("north", new BindingDef("open", Map.of()));
    division.put("south", new BindingDef("strict", Map.of("allowed", List.of("STOCKED", "SPECIAL"))));
    bindings.put("division", division);
    bindings.put("site", Map.of("s1", new BindingDef("totals", Map.of("expected", "2"))));
    b.feature("f.gate", new FeatureDef("order", Returns.enumeration(List.of("ALLOW", "DENY"), false), "FIRST",
        "DENY", List.of("global", "division", "site"), policies, bindings));
  }

  static KeyDef key(TypeDecl type, String combine, Object dflt, List<String> settableAt, String across,
      String onSourceFailure) {
    return new KeyDef(type, combine, Optional.ofNullable(dflt), settableAt, Optional.ofNullable(across),
        Optional.ofNullable(onSourceFailure), Optional.empty());
  }

  static TypeDecl stringFormat(String format) {
    return new TypeDecl("string", Optional.empty(), Optional.empty(), Optional.of(format), List.of(), List.of(),
        Optional.empty(), Map.of(), false);
  }

  /** A provider that returns fixed values per node and counts its calls. */
  static final class CountingProvider implements LevelValueProvider {
    private final Map<String, Map<String, Object>> byNode;
    private final List<String> calls = new ArrayList<>();

    CountingProvider(Map<String, Map<String, Object>> byNode) {
      this.byNode = byNode;
    }

    @Override
    public synchronized Map<String, Object> valuesFor(String nodeId) throws Exception {
      calls.add(nodeId);
      Map<String, Object> values = byNode.get(nodeId);
      if (values == null) {
        throw new IllegalStateException("no values for " + nodeId);
      }
      return values;
    }

    synchronized List<String> calls() {
      return List.copyOf(calls);
    }
  }

  /** A provider that returns the same values for every node. */
  static LevelValueProvider values(Map<String, Object> values) {
    return node -> values;
  }

  /** A provider that fails. */
  static LevelValueProvider failing() {
    return node -> {
      throw new java.io.IOException("unavailable");
    };
  }

  /** Creates an evaluator with the given providers for {@code account} and {@code seat}. */
  static Evaluator evaluator(Builder b, LevelValueProvider account, LevelValueProvider seat,
      io.github.tierwise.core.EvaluationListener listener) {
    EvaluatorOptions.Builder options = EvaluatorOptions.builder()
        .provider("account", account, TIMEOUT)
        .provider("seat", seat, TIMEOUT);
    if (listener != null) {
      options.listener(listener);
    }
    return b.evaluator(options.build());
  }

  /** A mutable map built from alternating keys and values, so that a test can use {@code null} values. */
  static Map<String, Object> mapOf(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }
}
