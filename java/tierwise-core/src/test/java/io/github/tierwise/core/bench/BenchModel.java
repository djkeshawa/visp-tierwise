package io.github.tierwise.core.bench;

import io.github.tierwise.core.Context;
import io.github.tierwise.core.FactSet;
import io.github.tierwise.core.internal.model.BindingDef;
import io.github.tierwise.core.internal.model.ConditionDef;
import io.github.tierwise.core.internal.model.EntityDef;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.FeatureDef;
import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.KeyDef;
import io.github.tierwise.core.internal.model.Model;
import io.github.tierwise.core.internal.model.PolicyDef;
import io.github.tierwise.core.internal.model.Returns;
import io.github.tierwise.core.internal.model.RuleDef;
import io.github.tierwise.core.internal.model.ScopeDef;
import io.github.tierwise.core.internal.model.SettingDef;
import io.github.tierwise.core.internal.model.TypeDecl;
import io.github.tierwise.core.internal.model.Values;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The model of the in-process benchmark: 1,000 keys and 50 features (requirements section 12, implementation plan 3.9).
 *
 * <p>The model is fixed, with no random choices, so every run measures the same bundle. It has three levels and one
 * axis, all files-sourced, and no service-sourced level, so {@code resolve} never calls a provider and the numbers
 * exclude provider cost. Keys cycle through five shapes: an {@code all} boolean, an {@code override} integer with an
 * axis, a {@code min} integer, an {@code override} enum and a {@code union} set. Every feature decides an order from
 * its child lines and has one policy with six rules, two settings, and bindings at the root and at some zones.
 */
public final class BenchModel {

  /** The number of keys of the model. */
  public static final int KEYS = 1000;

  /** The number of features of the model. */
  public static final int FEATURES = 50;

  private static final int ZONES = 40;
  private static final int UNITS = 20;
  private static final int LANES = 4;
  private static final List<String> STATUSES = List.of("closed", "hold", "open", "shipped");
  private static final List<String> KINDS = List.of("a", "b", "c");
  private static final List<String> SETS = List.of("s0", "s1", "s2", "s3", "s4", "s5");

  private BenchModel() {
  }

  /**
   * Builds the model.
   *
   * @return the model
   */
  public static Model build() {
    Hierarchy hierarchy = hierarchy();
    Map<String, EntityDef> entities = entities();
    Map<String, FactDef> facts = facts();
    Map<String, KeyDef> keys = keys();
    Values values = values(keys);
    Map<String, FeatureDef> features = features();
    return new Model(Model.FORMAT_VERSION, Model.SPEC_VERSION, hierarchy, entities, facts, keys, values, features);
  }

  /**
   * Returns the key names of the model in declaration order.
   *
   * @return the names
   */
  public static List<String> keyNames() {
    List<String> names = new ArrayList<>();
    for (int i = 0; i < KEYS; i++) {
      names.add(keyName(i));
    }
    return names;
  }

  /**
   * Returns the feature names of the model in declaration order.
   *
   * @return the names
   */
  public static List<String> featureNames() {
    List<String> names = new ArrayList<>();
    for (int i = 0; i < FEATURES; i++) {
      names.add(featureName(i));
    }
    return names;
  }

  /**
   * Returns contexts that name a zone, a unit and a lane, so that the lookups reach the values of every scope.
   *
   * @param count the number of contexts
   * @return the contexts
   */
  public static List<Context> contexts(int count) {
    List<Context> out = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      out.add(Context.builder()
          .level("zone", zone(i))
          .level("unit", unit(i))
          .axis("lane", "l" + (i % LANES))
          .build());
    }
    return out;
  }

  /**
   * Returns fact sets for decisions: an order with a total, a flag and a status, and three lines.
   *
   * @param count the number of fact sets
   * @return the fact sets
   */
  public static List<FactSet> factSets(int count) {
    List<FactSet> out = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      FactSet.Builder builder = FactSet.builder()
          .fact("order.total", (long) (i * 7919 % 10000))
          .fact("order.region_ok", i % 2 == 0)
          .fact("order.status", STATUSES.get(i % STATUSES.size()));
      for (int j = 0; j < 3; j++) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("line.kind", KINDS.get((i + j) % KINDS.size()));
        line.put("line.qty", (long) ((i + j) * 13 % 101));
        builder.child("line", line);
      }
      out.add(builder.build());
    }
    return out;
  }

  /** The name of the key at an index, zero-padded so that the names sort in declaration order. */
  static String keyName(int index) {
    return "k." + pad(index, 4);
  }

  /** The name of the feature at an index. */
  static String featureName(int index) {
    return "f." + pad(index, 2);
  }

  private static String zone(int i) {
    return "z" + pad(i % ZONES, 2);
  }

  private static String unit(int i) {
    return "u" + pad(i % UNITS, 2);
  }

  private static String pad(int value, int width) {
    String digits = String.valueOf(value);
    return "0".repeat(Math.max(0, width - digits.length())) + digits;
  }

  private static Hierarchy hierarchy() {
    Map<String, ScopeDef> scopes = new LinkedHashMap<>();
    scopes.put("zone", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, nodes("z", ZONES),
        ScopeDef.OnUnknownNode.ERROR));
    scopes.put("unit", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, nodes("u", UNITS),
        ScopeDef.OnUnknownNode.IGNORE));
    scopes.put("lane", ScopeDef.closedCatalog(ScopeDef.ValuesSource.FILES, nodes("l", LANES),
        ScopeDef.OnUnknownNode.IGNORE));
    return new Hierarchy(List.of("base", "zone", "unit"), List.of("lane"), scopes);
  }

  private static List<String> nodes(String prefix, int count) {
    List<String> out = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      out.add(prefix + pad(i, prefix.equals("l") ? 1 : 2));
    }
    return out;
  }

  private static Map<String, EntityDef> entities() {
    Map<String, EntityDef> out = new LinkedHashMap<>();
    out.put("line", new EntityDef(List.of("order"), List.of()));
    out.put("order", new EntityDef(List.of(), List.of("line")));
    return out;
  }

  private static Map<String, FactDef> facts() {
    Map<String, FactDef> out = new LinkedHashMap<>();
    out.put("order.total", new FactDef("order", TypeDecl.integer(0L, 10000L)));
    out.put("order.region_ok", new FactDef("order", TypeDecl.simple("boolean")));
    out.put("order.status", new FactDef("order", TypeDecl.enumeration(STATUSES)));
    out.put("line.kind", new FactDef("line", TypeDecl.enumeration(KINDS)));
    out.put("line.qty", new FactDef("line", TypeDecl.integer(0L, 100L)));
    return out;
  }

  private static Map<String, KeyDef> keys() {
    Map<String, KeyDef> out = new LinkedHashMap<>();
    for (int i = 0; i < KEYS; i++) {
      out.put(keyName(i), key(i % 5));
    }
    return out;
  }

  private static KeyDef key(int shape) {
    return switch (shape) {
      case 0 -> new KeyDef(TypeDecl.simple("boolean"), "all", Optional.empty(), List.of("base", "zone"),
          Optional.empty(), Optional.empty(), Optional.empty());
      case 1 -> new KeyDef(TypeDecl.integer(0L, 1000L), "override", Optional.of(0L), List.of("base", "zone", "lane"),
          Optional.of("max"), Optional.empty(), Optional.empty());
      case 2 -> new KeyDef(TypeDecl.integer(0L, 1000L), "min", Optional.empty(), List.of("base", "zone", "unit"),
          Optional.empty(), Optional.empty(), Optional.empty());
      case 3 -> new KeyDef(TypeDecl.enumeration(List.of("a", "b", "c", "d")), "override", Optional.of("a"),
          List.of("base", "unit"), Optional.empty(), Optional.empty(), Optional.empty());
      default -> new KeyDef(TypeDecl.set(SETS), "union", Optional.empty(), List.of("base", "zone", "lane"),
          Optional.empty(), Optional.empty(), Optional.empty());
    };
  }

  /**
   * Makes the values: the root holds a value for every key that the root may set, and the zones, units and lanes hold
   * values for a quarter of the keys each, so that the lookups find values at every scope.
   */
  private static Values values(Map<String, KeyDef> keys) {
    List<String> zoneNodes = nodes("z", ZONES);
    List<String> unitNodes = nodes("u", UNITS);
    List<String> laneNodes = nodes("l", LANES);
    Map<String, Map<String, Object>> root = new LinkedHashMap<>();
    Map<String, Object> rootEntries = new LinkedHashMap<>();
    Map<String, Map<String, Object>> zones = new LinkedHashMap<>();
    Map<String, Map<String, Object>> units = new LinkedHashMap<>();
    Map<String, Map<String, Object>> lanes = new LinkedHashMap<>();
    int index = 0;
    for (Map.Entry<String, KeyDef> key : keys.entrySet()) {
      int shape = index % 5;
      rootEntries.put(key.getKey(), rootValue(shape, index));
      for (int z = 0; z < ZONES; z++) {
        if ((index + z) % 4 == 0 && key.getValue().settableAt().contains("zone")) {
          zones.computeIfAbsent(zoneNodes.get(z), n -> new LinkedHashMap<>())
              .put(key.getKey(), rootValue(shape, index + z));
        }
      }
      for (int u = 0; u < UNITS; u++) {
        if ((index + u) % 4 == 0 && key.getValue().settableAt().contains("unit")) {
          units.computeIfAbsent(unitNodes.get(u), n -> new LinkedHashMap<>())
              .put(key.getKey(), rootValue(shape, index + u));
        }
      }
      for (int l = 0; l < LANES; l++) {
        if (index % 2 == l % 2 && key.getValue().settableAt().contains("lane")) {
          lanes.computeIfAbsent(laneNodes.get(l), n -> new LinkedHashMap<>())
              .put(key.getKey(), rootValue(shape, index + l));
        }
      }
      index++;
    }
    root.put("", rootEntries);
    Map<String, Map<String, Map<String, Object>>> byScope = new LinkedHashMap<>();
    byScope.put("base", root);
    byScope.put("zone", zones);
    byScope.put("unit", units);
    byScope.put("lane", lanes);
    return new Values(byScope);
  }

  private static Object rootValue(int shape, int index) {
    return switch (shape) {
      case 0 -> index % 3 != 0;
      case 1 -> (long) (index % 1000);
      case 2 -> (long) (index * 37 % 1000);
      case 3 -> index % 2 == 0 ? "b" : "d";
      default -> List.of("s1", "s3");
    };
  }

  private static Map<String, FeatureDef> features() {
    Map<String, FeatureDef> out = new LinkedHashMap<>();
    for (int i = 0; i < FEATURES; i++) {
      out.put(featureName(i), feature(i));
    }
    return out;
  }

  private static FeatureDef feature(int index) {
    Map<String, SettingDef> settings = new LinkedHashMap<>();
    settings.put("limit", new SettingDef(SettingDef.Kind.VALUE_OF, "order.total", 500L));
    settings.put("allowed", new SettingDef(SettingDef.Kind.LIST_OF, "order.status", List.of("open")));
    List<RuleDef> rules = List.of(
        new RuleDef("r1", "Flagged region", List.of(new ConditionDef("order.region_ok", "eq", true)), "ALLOW"),
        new RuleDef("r2", "Allowed status",
            List.of(new ConditionDef("order.status", "in", "$allowed")), "ALLOW"),
        new RuleDef("r3", "Limit reached",
            List.of(new ConditionDef("order.total", "eq", "$limit")), "REVIEW"),
        new RuleDef("r4", "Kinds", List.of(new ConditionDef("line.kind", "all_in", List.of("a", "b"))), "ALLOW"),
        new RuleDef("r5", "Quantities",
            List.of(new ConditionDef("line.qty", "any_in", List.of(1L, 2L, 3L))), "REVIEW"),
        new RuleDef("r6", "Default catch-all", List.of(), "DENY"));
    PolicyDef policy = new PolicyDef(settings, "DENY",
        List.of("line.kind", "line.qty", "order.region_ok", "order.status", "order.total"), rules);
    List<String> zoneNodes = nodes("z", ZONES);
    Map<String, BindingDef> zoneBindings = new LinkedHashMap<>();
    for (int z = 0; z < 10; z++) {
      zoneBindings.put(zoneNodes.get(z), new BindingDef("p", Map.of("limit", 100L + index + z)));
    }
    Map<String, Map<String, BindingDef>> bindings = new LinkedHashMap<>();
    bindings.put("base", Map.of("", new BindingDef("p", Map.of())));
    bindings.put("zone", zoneBindings);
    return new FeatureDef("order", Returns.enumeration(List.of("ALLOW", "DENY", "REVIEW"), false), "FIRST", "DENY",
        List.of("base", "zone"), Map.of("p", policy), bindings);
  }
}
