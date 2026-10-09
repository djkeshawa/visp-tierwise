package io.github.tierwise.core.differential;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

/**
 * Builds one random model that is valid by the rules of the specification: hierarchy and admission (SCOPE-*),
 * entities and facts (ENTITY-*, FACT-*), keys and their strategies (MERGE-*, KEY-*), values (BUNDLE-64 to BUNDLE-67),
 * and features with policies, rules and bindings (FEAT-*, POL-*, RULE-*, BIND-*).
 *
 * <p>Every choice comes from the random source that the caller passes in, so the same source gives the same model.
 * The names come from {@link DiffNames}, and all of them are unique across the model. The result is written and
 * loaded by the caller, which rejects a model that the bundle reader refuses; the builder counts on its own rules
 * and does not try to repair a model.
 */
final class DiffModelBuilder {

  private final Random r;
  private final Set<String> used = new LinkedHashSet<>();

  private DiffModelBuilder(Random random) {
    this.r = random;
  }

  /**
   * Builds a model.
   *
   * @param random the random source of every choice
   * @return the model
   */
  static Model build(Random random) {
    return new DiffModelBuilder(random).model();
  }

  private Model model() {
    Hierarchy hierarchy = hierarchy();
    Map<String, EntityDef> entities = entities();
    Map<String, FactDef> facts = facts(entities);
    Map<String, KeyDef> keys = keys(hierarchy);
    Values values = values(hierarchy, keys);
    Map<String, FeatureDef> features = features(hierarchy, entities, facts);
    return new Model(Model.FORMAT_VERSION, Model.SPEC_VERSION, hierarchy, entities, facts, keys, values, features);
  }

  /** Returns a name that no earlier name of the model used. */
  private String fresh(Supplier<String> make) {
    String name;
    do {
      name = make.get();
    } while (!used.add(name));
    return name;
  }

  // ---- hierarchy and admission -------------------------------------------------------------------------------

  private Hierarchy hierarchy() {
    List<String> levels = new ArrayList<>();
    levels.add(fresh(() -> DiffNames.identifier(r)));
    int nonRoot = r.nextInt(4);
    for (int i = 0; i < nonRoot; i++) {
      levels.add(fresh(() -> DiffNames.identifier(r)));
    }
    List<String> axes = new ArrayList<>();
    int axisCount = r.nextInt(3);
    for (int i = 0; i < axisCount; i++) {
      axes.add(fresh(() -> DiffNames.identifier(r)));
    }
    Map<String, ScopeDef> scopes = new LinkedHashMap<>();
    for (String level : levels.subList(1, levels.size())) {
      scopes.put(level, scope(true));
    }
    for (String axis : axes) {
      scopes.put(axis, scope(false));
    }
    return new Hierarchy(levels, axes, scopes);
  }

  private ScopeDef scope(boolean level) {
    ScopeDef.ValuesSource source = level && r.nextInt(10) < 5
        ? ScopeDef.ValuesSource.SERVICE
        : ScopeDef.ValuesSource.FILES;
    if (r.nextInt(10) < 3) {
      return ScopeDef.openCatalog(source);
    }
    ScopeDef.OnUnknownNode policy = r.nextBoolean() ? ScopeDef.OnUnknownNode.ERROR : ScopeDef.OnUnknownNode.IGNORE;
    return ScopeDef.closedCatalog(source, nodeIds(1 + r.nextInt(5)), policy);
  }

  /** Returns distinct node IDs in code point order (BUNDLE-38, BUNDLE-39). */
  private List<String> nodeIds(int count) {
    Set<String> ids = new TreeSet<>(DiffNames.CODE_POINT);
    while (ids.size() < count) {
      ids.add(DiffNames.nodeId(r));
    }
    return new ArrayList<>(ids);
  }

  private static boolean isService(Hierarchy hierarchy, String scope) {
    ScopeDef def = hierarchy.scope(scope);
    return def != null && def.isServiceSourced();
  }

  // ---- entities and facts ------------------------------------------------------------------------------------

  /**
   * Makes entities whose {@code belongsTo} edges and {@code hasMany} edges only point from a lower index to a higher
   * one, so that the entity graph has no cycle (ENTITY-*). The two kinds of edge are chosen independently, which is
   * how the specification treats them: no inverse is inferred.
   */
  private Map<String, EntityDef> entities() {
    int count = 1 + r.nextInt(3);
    List<String> names = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      names.add(fresh(() -> DiffNames.identifier(r)));
    }
    Map<String, EntityDef> out = new LinkedHashMap<>();
    for (int i = 0; i < count; i++) {
      List<String> belongsTo = new ArrayList<>();
      List<String> hasMany = new ArrayList<>();
      for (int j = 0; j < i; j++) {
        if (r.nextInt(10) < 4) {
          belongsTo.add(names.get(j));
        }
      }
      for (int j = i + 1; j < count; j++) {
        if (r.nextInt(10) < 4) {
          hasMany.add(names.get(j));
        }
      }
      belongsTo.sort(DiffNames.CODE_POINT);
      hasMany.sort(DiffNames.CODE_POINT);
      out.put(names.get(i), new EntityDef(belongsTo, hasMany));
    }
    return out;
  }

  private Map<String, FactDef> facts(Map<String, EntityDef> entities) {
    Map<String, FactDef> out = new LinkedHashMap<>();
    for (String entity : entities.keySet()) {
      int count = 1 + r.nextInt(3);
      for (int i = 0; i < count; i++) {
        String attribute = fresh(() -> DiffNames.identifier(r));
        out.put(entity + "." + attribute, new FactDef(entity, DiffValues.scalar(r)));
      }
    }
    return out;
  }

  // ---- keys --------------------------------------------------------------------------------------------------

  private Map<String, KeyDef> keys(Hierarchy hierarchy) {
    int count = 1 + r.nextInt(6);
    Map<String, KeyDef> out = new LinkedHashMap<>();
    for (int i = 0; i < count; i++) {
      String name = fresh(() -> DiffNames.dotted(r));
      out.put(name, key(hierarchy));
    }
    return out;
  }

  /**
   * Makes one key. The strategy is chosen among those that the type allows (MERGE-3). A key settable at a service
   * level gets an {@code onSourceFailure} (BUNDLE-59), and {@code deny} only for a narrowing strategy (MERGE-*). An
   * order-dependent key settable at an axis gets a {@code combineAcrossAxes} (BUNDLE-57). An order-dependent key
   * always gets a default, which is a complete value of its type (MERGE-4, MERGE-7, MERGE-10).
   */
  private KeyDef key(Hierarchy hierarchy) {
    DiffValues.Shape shape = DiffValues.Shape.values()[r.nextInt(DiffValues.Shape.values().length)];
    String combine = strategy(shape);
    TypeDecl type = "min".equals(combine) ? DiffValues.integerWithMinimum(r) : DiffValues.type(r, shape);
    List<String> settable = settableAt(hierarchy);
    boolean service = settable.stream().anyMatch(scope -> isService(hierarchy, scope));
    boolean axis = settable.stream().anyMatch(hierarchy::isAxis);
    boolean orderDependent = "override".equals(combine) || "deep_merge".equals(combine);
    Optional<String> onSourceFailure = Optional.empty();
    if (service) {
      boolean narrowing = "all".equals(combine) || "intersect".equals(combine) || "min".equals(combine);
      onSourceFailure = Optional.of(narrowing && r.nextInt(4) != 0 ? "deny" : "skip");
    }
    Optional<String> acrossAxes = Optional.empty();
    if (orderDependent && axis) {
      List<String> options = new ArrayList<>(List.of("axis_wins", "hierarchy_wins"));
      if ("integer".equals(type.type())) {
        options.add("max");
        options.add("min");
      }
      acrossAxes = Optional.of(options.get(r.nextInt(options.size())));
    }
    Optional<Object> defaultValue = orderDependent ? Optional.of(DiffValues.value(type, r)) : Optional.empty();
    Optional<String> kind = r.nextInt(5) == 0
        ? Optional.of(fresh(() -> DiffNames.identifier(r)))
        : Optional.empty();
    return new KeyDef(type, combine, defaultValue, settable, acrossAxes, onSourceFailure, kind);
  }

  private String strategy(DiffValues.Shape shape) {
    return switch (shape) {
      case BOOLEAN -> pick("override", "all", "any");
      case INTEGER -> pick("override", "min", "max");
      case SET -> pick("intersect", "union");
      case OBJECT -> pick("override", "deep_merge");
      default -> "override";
    };
  }

  private String pick(String... options) {
    return options[r.nextInt(options.length)];
  }

  /** Returns a non-empty subset of the scope order (KEY-7, BUNDLE-54). */
  private List<String> settableAt(Hierarchy hierarchy) {
    List<String> chosen = new ArrayList<>();
    for (String scope : hierarchy.scopeOrder()) {
      if (r.nextInt(10) < 6) {
        chosen.add(scope);
      }
    }
    if (chosen.isEmpty()) {
      List<String> order = hierarchy.scopeOrder();
      chosen.add(order.get(r.nextInt(order.size())));
    }
    return chosen;
  }

  // ---- values ------------------------------------------------------------------------------------------------

  /**
   * Makes the authored values: at each non-service scope, a random part of its nodes holds a random part of the keys
   * that the scope may set (BUNDLE-64 to BUNDLE-67). A node with no entry is left out, as BUNDLE-22 requires.
   */
  private Values values(Hierarchy hierarchy, Map<String, KeyDef> keys) {
    Map<String, Map<String, Map<String, Object>>> byScope = new LinkedHashMap<>();
    for (String scope : hierarchy.scopeOrder()) {
      if (isService(hierarchy, scope)) {
        continue;
      }
      Map<String, Map<String, Object>> nodes = new LinkedHashMap<>();
      for (String node : candidateNodes(hierarchy, scope)) {
        Map<String, Object> entries = new LinkedHashMap<>();
        for (Map.Entry<String, KeyDef> key : keys.entrySet()) {
          if (key.getValue().settableAt().contains(scope) && r.nextInt(3) == 0) {
            entries.put(key.getKey(), DiffValues.valueFor(key.getValue(), r));
          }
        }
        if (!entries.isEmpty()) {
          nodes.put(node, entries);
        }
      }
      if (!nodes.isEmpty()) {
        byScope.put(scope, nodes);
      }
    }
    return new Values(byScope);
  }

  /** Returns the node keys that a scope may hold: the empty key for the root, otherwise admitted nodes (BUNDLE-129). */
  private List<String> candidateNodes(Hierarchy hierarchy, String scope) {
    if (scope.equals(hierarchy.rootLevel())) {
      return List.of("");
    }
    ScopeDef def = hierarchy.scope(scope);
    List<String> out = new ArrayList<>();
    if (def.open()) {
      Set<String> ids = new TreeSet<>(DiffNames.CODE_POINT);
      int count = r.nextInt(3);
      while (ids.size() < count) {
        ids.add(DiffNames.nodeId(r));
      }
      out.addAll(ids);
    } else {
      for (String node : def.nodes()) {
        if (r.nextBoolean()) {
          out.add(node);
        }
      }
    }
    return out;
  }

  // ---- features ----------------------------------------------------------------------------------------------

  private Map<String, FeatureDef> features(
      Hierarchy hierarchy, Map<String, EntityDef> entities, Map<String, FactDef> facts) {
    int count = 1 + r.nextInt(3);
    List<String> entityNames = new ArrayList<>(entities.keySet());
    Map<String, FeatureDef> out = new LinkedHashMap<>();
    for (int i = 0; i < count; i++) {
      String name = fresh(() -> DiffNames.dotted(r));
      String decidedPer = entityNames.get(r.nextInt(entityNames.size()));
      out.put(name, feature(hierarchy, decidedPer, entities, facts));
    }
    return out;
  }

  private FeatureDef feature(
      Hierarchy hierarchy, String decidedPer, Map<String, EntityDef> entities, Map<String, FactDef> facts) {
    Returns returns = r.nextInt(3) == 0 ? objectReturns() : enumReturns();
    Object ifFactMissing = effect(returns);
    List<String> bindingLevels = bindingLevels(hierarchy);
    Map<String, PolicyDef> policies = policies(decidedPer, returns, entities, facts);
    Map<String, Map<String, BindingDef>> bindings = bindings(hierarchy, bindingLevels, policies, facts);
    return new FeatureDef(decidedPer, returns, "FIRST", ifFactMissing, bindingLevels, policies, bindings);
  }

  private Returns enumReturns() {
    List<String> values = new ArrayList<>();
    Set<String> seen = new TreeSet<>(DiffNames.CODE_POINT);
    int count = 2 + r.nextInt(3);
    while (seen.size() < count) {
      seen.add(DiffNames.text(r));
    }
    values.addAll(seen);
    return Returns.enumeration(values, r.nextInt(5) == 0);
  }

  private Returns objectReturns() {
    return Returns.object(DiffValues.fields(r));
  }

  /** The effect of a feature: a member of its enum, {@code null} for a nullable enum, or a complete object. */
  private Object effect(Returns returns) {
    if (returns.objectForm()) {
      return DiffValues.objectValue(TypeDecl.object(returns.fields()), r, false);
    }
    if (returns.nullable() && r.nextInt(4) == 0) {
      return null;
    }
    return returns.enumValues().get(r.nextInt(returns.enumValues().size()));
  }

  /** The levels that a binding may be set at: the root, and some non-service levels in level order (BUNDLE-81). */
  private List<String> bindingLevels(Hierarchy hierarchy) {
    List<String> out = new ArrayList<>();
    out.add(hierarchy.rootLevel());
    for (String level : hierarchy.levels().subList(1, hierarchy.levels().size())) {
      if (!isService(hierarchy, level) && r.nextBoolean()) {
        out.add(level);
      }
    }
    return out;
  }

  private Map<String, PolicyDef> policies(
      String decidedPer, Returns returns, Map<String, EntityDef> entities, Map<String, FactDef> facts) {
    int count = 1 + r.nextInt(2);
    Map<String, PolicyDef> out = new LinkedHashMap<>();
    for (int i = 0; i < count; i++) {
      String name = fresh(() -> DiffNames.identifier(r));
      out.put(name, policy(decidedPer, returns, entities, facts));
    }
    return out;
  }

  /**
   * Makes one policy. Its settings and conditions use only facts in the scope of the decision (E_FACT_NOT_IN_SCOPE),
   * and a child fact is used only through a quantifier (E_QUANTIFIER_REQUIRED). {@code requiredFacts} is the sorted
   * distinct set of facts of the rules (BUNDLE-136).
   */
  private PolicyDef policy(
      String decidedPer, Returns returns, Map<String, EntityDef> entities, Map<String, FactDef> facts) {
    List<String> single = DiffSchema.singleFacts(entities, facts, decidedPer);
    List<String> child = DiffSchema.childFacts(entities, facts, decidedPer);
    Set<String> sortedPool = new TreeSet<>(DiffNames.CODE_POINT);
    sortedPool.addAll(single);
    sortedPool.addAll(child);
    List<String> pool = new ArrayList<>(sortedPool);
    Map<String, SettingDef> settings = settings(single, pool, facts);
    List<RuleDef> rules = rules(pool, child, settings, facts, returns);
    TreeSet<String> required = new TreeSet<>(DiffNames.CODE_POINT);
    for (RuleDef rule : rules) {
      for (ConditionDef condition : rule.when()) {
        required.add(condition.fact());
      }
    }
    return new PolicyDef(settings, effect(returns), new ArrayList<>(required), rules);
  }

  private Map<String, SettingDef> settings(List<String> single, List<String> pool, Map<String, FactDef> facts) {
    Map<String, SettingDef> out = new LinkedHashMap<>();
    int count = r.nextInt(3);
    for (int i = 0; i < count; i++) {
      String name = fresh(() -> DiffNames.identifier(r));
      if (!single.isEmpty() && r.nextBoolean()) {
        String fact = single.get(r.nextInt(single.size()));
        out.put(name, new SettingDef(SettingDef.Kind.VALUE_OF, fact, DiffValues.value(facts.get(fact).type(), r)));
      } else {
        List<String> listable = nonBoolean(pool, facts);
        if (!listable.isEmpty()) {
          String fact = listable.get(r.nextInt(listable.size()));
          TypeDecl type = facts.get(fact).type();
          out.put(name, new SettingDef(SettingDef.Kind.LIST_OF, fact, DiffValues.sortedDistinct(type, r)));
        }
      }
    }
    return out;
  }

  private static List<String> nonBoolean(List<String> names, Map<String, FactDef> facts) {
    List<String> out = new ArrayList<>();
    for (String name : names) {
      if (!"boolean".equals(facts.get(name).type().type())) {
        out.add(name);
      }
    }
    return out;
  }

  private List<RuleDef> rules(
      List<String> pool, List<String> child, Map<String, SettingDef> settings, Map<String, FactDef> facts,
      Returns returns) {
    List<RuleDef> out = new ArrayList<>();
    int count = r.nextInt(5);
    for (int i = 0; i < count; i++) {
      String id = fresh(() -> DiffNames.identifier(r));
      List<ConditionDef> when = conditions(pool, child, settings, facts);
      out.add(new RuleDef(id, DiffNames.text(r), when, effect(returns)));
    }
    return out;
  }

  /**
   * Makes the conditions of one rule: up to two distinct facts, sorted by name (BUNDLE-96). A single-value fact uses
   * {@code eq} or {@code in}; a child fact uses {@code all_in} or {@code any_in}. A setting reference is used only
   * where a setting of the same fact and the same form exists (BUNDLE-126).
   */
  private List<ConditionDef> conditions(
      List<String> pool, List<String> child, Map<String, SettingDef> settings, Map<String, FactDef> facts) {
    Set<String> chosen = new TreeSet<>(DiffNames.CODE_POINT);
    int size = r.nextInt(3);
    for (int i = 0; i < size && !pool.isEmpty(); i++) {
      chosen.add(pool.get(r.nextInt(pool.size())));
    }
    List<ConditionDef> when = new ArrayList<>();
    for (String fact : chosen) {
      TypeDecl type = facts.get(fact).type();
      if (child.contains(fact)) {
        String op = r.nextBoolean() ? "all_in" : "any_in";
        when.add(new ConditionDef(fact, op, listOperand(fact, type, settings)));
      } else if ("boolean".equals(type.type()) || r.nextBoolean()) {
        when.add(new ConditionDef(fact, "eq", singleOperand(fact, type, settings)));
      } else {
        when.add(new ConditionDef(fact, "in", listOperand(fact, type, settings)));
      }
    }
    return when;
  }

  private Object singleOperand(String fact, TypeDecl type, Map<String, SettingDef> settings) {
    String reference = referenceTo(fact, SettingDef.Kind.VALUE_OF, settings);
    if (reference != null && r.nextBoolean()) {
      return ConditionDef.SETTING_PREFIX + reference;
    }
    return DiffValues.value(type, r);
  }

  private Object listOperand(String fact, TypeDecl type, Map<String, SettingDef> settings) {
    String reference = referenceTo(fact, SettingDef.Kind.LIST_OF, settings);
    if (reference != null && r.nextBoolean()) {
      return ConditionDef.SETTING_PREFIX + reference;
    }
    if ("boolean".equals(type.type())) {
      List<Object> single = new ArrayList<>();
      single.add(Boolean.valueOf(r.nextBoolean()));
      return single;
    }
    return DiffValues.sortedDistinct(type, r);
  }

  private static String referenceTo(String fact, SettingDef.Kind kind, Map<String, SettingDef> settings) {
    for (Map.Entry<String, SettingDef> entry : settings.entrySet()) {
      if (entry.getValue().kind() == kind && entry.getValue().fact().equals(fact)) {
        return entry.getKey();
      }
    }
    return null;
  }

  /**
   * Makes the bindings: the root binding (BIND-13) and, at some other levels, bindings at admitted nodes. A binding
   * sets some of its policy's settings to valid values; a setting that it leaves out takes the policy default.
   */
  private Map<String, Map<String, BindingDef>> bindings(
      Hierarchy hierarchy, List<String> levels, Map<String, PolicyDef> policies, Map<String, FactDef> facts) {
    List<String> names = new ArrayList<>(policies.keySet());
    Map<String, Map<String, BindingDef>> out = new LinkedHashMap<>();
    out.put(hierarchy.rootLevel(), Map.of("", binding(names, policies, facts)));
    for (String level : levels.subList(1, levels.size())) {
      Map<String, BindingDef> nodes = new TreeMap<>(DiffNames.CODE_POINT);
      for (String node : candidateNodes(hierarchy, level)) {
        nodes.put(node, binding(names, policies, facts));
      }
      if (!nodes.isEmpty()) {
        out.put(level, nodes);
      }
    }
    return out;
  }

  private BindingDef binding(List<String> names, Map<String, PolicyDef> policies, Map<String, FactDef> facts) {
    String policyName = names.get(r.nextInt(names.size()));
    PolicyDef policy = policies.get(policyName);
    Map<String, Object> settings = new LinkedHashMap<>();
    for (Map.Entry<String, SettingDef> setting : policy.settings().entrySet()) {
      if (r.nextBoolean()) {
        continue;
      }
      TypeDecl type = facts.get(setting.getValue().fact()).type();
      Object value = setting.getValue().kind() == SettingDef.Kind.VALUE_OF
          ? DiffValues.value(type, r)
          : DiffValues.sortedDistinct(type, r);
      settings.put(setting.getKey(), value);
    }
    return new BindingDef(policyName, settings);
  }
}
