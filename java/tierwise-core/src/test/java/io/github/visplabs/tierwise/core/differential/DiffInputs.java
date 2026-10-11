package io.github.visplabs.tierwise.core.differential;

import io.github.visplabs.tierwise.core.internal.model.FeatureDef;
import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.model.KeyDef;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.model.ScopeDef;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Draws the inputs of one differential case for a model: the call, its key or feature, the context, the FactSet and
 * the provider registrations with their stubs (VEC-24 to VEC-47 of the conformance suite).
 *
 * <p>Most draws are well formed and name the model's own keys, features, levels and nodes, so that the case reaches
 * the decision logic. A small share is malformed on purpose, for each error code of the evaluation (EVAL-30 to
 * EVAL-36, EVAL-38). The map that comes back is exactly what the JSON Lines file records, so both evaluators read the
 * same input.
 *
 * <p>The listener event {@code IGNORED_ENTRY} (SVC-29, SVC-47) is not drawn. A single-key resolve calls a provider only
 * for a level that the key can set (SVC-17), so an entry for a key the level cannot set is never met there, and
 * VEC-125 lists that clause as unobservable under a single-key resolve. Entries for other keys are drawn and are
 * ignored without an event, as SVC-29 and PROV-44 require.
 */
final class DiffInputs {

  private static final long TIMEOUT_MS = 30_000L;

  private final Model model;
  private final Random r;

  private DiffInputs(Model model, Random random) {
    this.model = model;
    this.r = random;
  }

  /**
   * Draws the inputs of one case.
   *
   * @param model the model
   * @param bundle the bytes of the bundle that the model was written as
   * @param random the random source
   * @return the input map: {@code bundle}, {@code call}, {@code key} or {@code feature}, {@code providers}, and
   *     optionally {@code context} and {@code facts}
   */
  static Map<String, Object> draw(Model model, byte[] bundle, Random random) {
    return new DiffInputs(model, random).input(bundle);
  }

  /** The context of a case, and the node that each well-formed level names. */
  private record Context(boolean present, Object raw, Map<String, String> levelNodes) {
  }

  private Map<String, Object> input(byte[] bundle) {
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("bundle", Base64.getEncoder().encodeToString(bundle));
    Context context = context();
    String call = call();
    input.put("call", call);
    String feature = null;
    if ("decide".equals(call)) {
      feature = feature();
      input.put("feature", feature);
    } else {
      input.put("key", key(context.levelNodes()));
    }
    if (context.present()) {
      input.put("context", context.raw());
    }
    if ("decide".equals(call) && r.nextInt(100) >= 8) {
      input.put("facts", facts(feature));
    }
    input.put("providers", providers(context.levelNodes()));
    return input;
  }

  private String call() {
    int roll = r.nextInt(100);
    if (roll < 35) {
      return "resolve";
    }
    return roll < 60 ? "resolveLocal" : "decide";
  }

  /**
   * Picks the key of a resolve call. Half of the picks, when the context names a service-sourced level, are keys that
   * the level may set, so that the calls reach the providers that the case registers.
   */
  private String key(Map<String, String> named) {
    List<String> keys = new ArrayList<>(model.keys().keySet());
    if (r.nextInt(100) < 4) {
      return "undeclared.key";
    }
    List<String> serviceNamed = new ArrayList<>();
    for (String level : named.keySet()) {
      if (isService(level)) {
        serviceNamed.add(level);
      }
    }
    if (!serviceNamed.isEmpty() && r.nextBoolean()) {
      List<String> touching = new ArrayList<>();
      for (String key : keys) {
        for (String level : serviceNamed) {
          if (model.keys().get(key).settableAt().contains(level)) {
            touching.add(key);
            break;
          }
        }
      }
      if (!touching.isEmpty()) {
        return touching.get(r.nextInt(touching.size()));
      }
    }
    return keys.get(r.nextInt(keys.size()));
  }

  private String feature() {
    List<String> features = new ArrayList<>(model.features().keySet());
    if (r.nextInt(100) < 4) {
      return "undeclared.feature";
    }
    return features.get(r.nextInt(features.size()));
  }

  // ---- context ----------------------------------------------------------------------------------------------

  private Context context() {
    int roll = r.nextInt(100);
    if (roll >= 90) {
      return new Context(false, null, Map.of());
    }
    if (roll < 3) {
      return new Context(true, malformedContext(), Map.of());
    }
    Hierarchy hierarchy = model.hierarchy();
    Map<String, String> levels = new LinkedHashMap<>();
    Map<String, String> axes = new LinkedHashMap<>();
    for (String level : hierarchy.levels().subList(1, hierarchy.levels().size())) {
      // A service-sourced level is named more often, so that the provider paths are reached.
      int include = isService(level) ? 90 : 60;
      if (r.nextInt(100) < include) {
        levels.put(level, nodeFor(level));
      }
    }
    for (String axis : hierarchy.axes()) {
      if (r.nextInt(10) < 6) {
        axes.put(axis, nodeFor(axis));
      }
    }
    if (r.nextInt(100) == 0) {
      levels.put(hierarchy.rootLevel(), "root-named");
    }
    if (r.nextInt(100) == 0) {
      levels.put("undeclared_level", "anything");
    }
    Map<String, Object> raw = new LinkedHashMap<>();
    Map<String, String> named = Map.of();
    if (r.nextInt(4) != 0) {
      raw.put("levels", new LinkedHashMap<String, Object>(levels));
      named = levels;
    }
    if (r.nextInt(4) != 0) {
      raw.put("axes", new LinkedHashMap<String, Object>(axes));
    }
    return new Context(true, raw, named);
  }

  /** A node that is listed in a closed catalog most of the time, and otherwise unlisted or from an open catalog. */
  private String nodeFor(String scope) {
    ScopeDef def = model.hierarchy().scope(scope);
    if (!def.open() && !def.nodes().isEmpty() && r.nextInt(100) < 85) {
      return def.nodes().get(r.nextInt(def.nodes().size()));
    }
    return DiffNames.nodeId(r);
  }

  private Object malformedContext() {
    return switch (r.nextInt(6)) {
      case 0 -> null;
      case 1 -> "text";
      case 2 -> new ArrayList<Object>();
      case 3 -> singleton("levels", new ArrayList<Object>());
      case 4 -> singleton("levels", singleton("level", 5L));
      default -> singleton("extra", new LinkedHashMap<String, Object>());
    };
  }

  // ---- facts ------------------------------------------------------------------------------------------------

  private Object facts(String feature) {
    if (r.nextInt(100) < 2) {
      return malformedFacts();
    }
    FeatureDef def = model.features().get(feature);
    Map<String, Object> single = new LinkedHashMap<>();
    Map<String, Object> children = new LinkedHashMap<>();
    if (def != null) {
      for (String fact : DiffSchema.singleFacts(model.entities(), model.facts(), def.decidedPer())) {
        putFact(single, fact, model.facts().get(fact).type());
      }
      if (r.nextInt(100) < 1) {
        single.put("outside.fact", Boolean.TRUE);
      }
      for (String child : DiffSchema.children(model.entities(), def.decidedPer())) {
        if (r.nextInt(100) < 10) {
          continue;
        }
        List<Object> items = new ArrayList<>();
        int count = r.nextInt(4);
        for (int i = 0; i < count; i++) {
          Map<String, Object> item = new LinkedHashMap<>();
          for (String fact : DiffSchema.factsOf(model.facts(), child)) {
            putFact(item, fact, model.facts().get(fact).type());
          }
          items.add(item);
        }
        children.put(child, items);
      }
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("facts", single);
    out.put("children", children);
    return out;
  }

  /** Puts a fact value, which is valid, missing or omitted, and sometimes of the wrong type. */
  private void putFact(Map<String, Object> into, String fact, TypeDecl type) {
    int roll = r.nextInt(100);
    if (roll < 78) {
      into.put(fact, DiffValues.value(type, r));
    } else if (roll < 88) {
      into.put(fact, null);
    }
    if (r.nextInt(100) < 2) {
      into.put(fact, DiffValues.wrong(type, r));
    }
  }

  private Object malformedFacts() {
    return switch (r.nextInt(5)) {
      case 0 -> null;
      case 1 -> "text";
      case 2 -> singleton("facts", 3L);
      case 3 -> singleton("unknown", new LinkedHashMap<String, Object>());
      default -> singleton("children", singleton("x", 1L));
    };
  }

  // ---- providers --------------------------------------------------------------------------------------------

  /**
   * Registers one provider for each service-sourced level (SVC-14), except for a few omitted ones (E_PROVIDER_MISSING),
   * and sometimes one for a name that is not a level (E_EVALUATOR_OPTIONS). The stub of a level lists the node that
   * the context names for it, so that a call reaches the provider with a known node.
   */
  private Map<String, Object> providers(Map<String, String> levelNodes) {
    Map<String, Object> out = new LinkedHashMap<>();
    Hierarchy hierarchy = model.hierarchy();
    for (String level : hierarchy.levels().subList(1, hierarchy.levels().size())) {
      if (!isService(level) || r.nextInt(100) < 3) {
        continue;
      }
      Map<String, Object> stub = new LinkedHashMap<>();
      String node = levelNodes.get(level);
      if (node != null) {
        stub.put(node, outcome(level));
      }
      out.put(level, registration(stub));
    }
    if (r.nextInt(100) == 0) {
      out.put("undeclared_level", registration(new LinkedHashMap<>()));
    }
    return out;
  }

  private boolean isService(String level) {
    ScopeDef def = model.hierarchy().scope(level);
    return def != null && def.isServiceSourced();
  }

  /** A registration: a timeout, which is invalid in one case in a hundred, and the stub. */
  private Map<String, Object> registration(Map<String, Object> stub) {
    int roll = r.nextInt(100);
    long timeout = roll == 0 ? 0L : roll == 1 ? -1L : TIMEOUT_MS;
    Map<String, Object> registration = new LinkedHashMap<>();
    registration.put("timeoutMs", Long.valueOf(timeout));
    registration.put("stub", stub);
    return registration;
  }

  /**
   * The outcome of a stubbed call: a failure, or values. The values are valid for the keys that the level may set. A
   * case in a few also has a wrong value (SOURCE_INVALID), a key that the level may not set (IGNORED_ENTRY), or an
   * undeclared key (UNKNOWN_KEY).
   */
  private Map<String, Object> outcome(String level) {
    Map<String, Object> out = new LinkedHashMap<>();
    int roll = r.nextInt(100);
    if (roll < 8) {
      out.put("fail", "unavailable");
      return out;
    }
    if (roll < 12) {
      out.put("fail", "timeout");
      return out;
    }
    Map<String, Object> values = new LinkedHashMap<>();
    List<String> settable = new ArrayList<>();
    List<String> others = new ArrayList<>();
    for (Map.Entry<String, KeyDef> key : model.keys().entrySet()) {
      if (key.getValue().settableAt().contains(level)) {
        settable.add(key.getKey());
      } else {
        others.add(key.getKey());
      }
    }
    for (String key : settable) {
      if (r.nextInt(100) < 60) {
        values.put(key, DiffValues.valueFor(model.keys().get(key), r));
      }
    }
    if (!settable.isEmpty() && r.nextInt(100) < 10) {
      String key = settable.get(r.nextInt(settable.size()));
      values.put(key, DiffValues.wrong(model.keys().get(key).type(), r));
    }
    if (!others.isEmpty() && r.nextInt(100) < 8) {
      String key = others.get(r.nextInt(others.size()));
      values.put(key, DiffValues.valueFor(model.keys().get(key), r));
    }
    if (r.nextInt(100) < 3) {
      values.put("undeclared.key", Boolean.TRUE);
    }
    out.put("values", values);
    return out;
  }

  private static Map<String, Object> singleton(String name, Object value) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put(name, value);
    return out;
  }
}
