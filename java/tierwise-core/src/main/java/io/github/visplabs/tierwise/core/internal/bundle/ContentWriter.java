package io.github.visplabs.tierwise.core.internal.bundle;

import io.github.visplabs.tierwise.core.internal.model.BindingDef;
import io.github.visplabs.tierwise.core.internal.model.ConditionDef;
import io.github.visplabs.tierwise.core.internal.model.EntityDef;
import io.github.visplabs.tierwise.core.internal.model.FactDef;
import io.github.visplabs.tierwise.core.internal.model.FeatureDef;
import io.github.visplabs.tierwise.core.internal.model.Hierarchy;
import io.github.visplabs.tierwise.core.internal.model.KeyDef;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.model.PolicyDef;
import io.github.visplabs.tierwise.core.internal.model.Returns;
import io.github.visplabs.tierwise.core.internal.model.RuleDef;
import io.github.visplabs.tierwise.core.internal.model.ScopeDef;
import io.github.visplabs.tierwise.core.internal.model.SettingDef;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import io.github.visplabs.tierwise.core.internal.model.Values;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a {@link Model} into the {@code content} object of a bundle (section 10.3). Arrays whose order the format
 * fixes are written in that order: code point order for node IDs, fact and entity names, enum values, literal
 * lists and conditions (BUNDLE-39, BUNDLE-75, BUNDLE-90, BUNDLE-96), and scope order for {@code settableAt} (BUNDLE-54).
 * Empty scope and node entries are omitted (BUNDLE-22), and absent optional members are not written (BUNDLE-20).
 */
final class ContentWriter {

  private ContentWriter() {
  }

  /**
   * Returns the content of a model.
   *
   * @param model the model
   * @return a map of plain values, ready for canonical serialisation
   */
  static Map<String, Object> write(Model model) {
    Map<String, Object> content = new LinkedHashMap<>();
    content.put("bundleFormatVersion", model.bundleFormatVersion());
    content.put("specVersion", model.specVersion());
    content.put("hierarchy", hierarchy(model.hierarchy()));
    content.put("entities", entities(model.entities()));
    content.put("facts", facts(model.facts()));
    content.put("keys", keys(model.keys(), model.hierarchy()));
    content.put("values", values(model.values()));
    content.put("features", features(model.features(), model.hierarchy()));
    return content;
  }

  private static Map<String, Object> hierarchy(Hierarchy hierarchy) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("levels", List.copyOf(hierarchy.levels()));
    out.put("axes", List.copyOf(hierarchy.axes()));
    Map<String, Object> scopes = new LinkedHashMap<>();
    for (Map.Entry<String, ScopeDef> entry : hierarchy.scopes().entrySet()) {
      ScopeDef scope = entry.getValue();
      Map<String, Object> definition = new LinkedHashMap<>();
      definition.put("values", scope.values().wire());
      if (scope.open()) {
        definition.put("nodes", "open");
      } else {
        definition.put("nodes", Order.sortedCopy(scope.nodes()));
        definition.put("onUnknownNode", scope.onUnknownNode().orElseThrow().wire());
      }
      scopes.put(entry.getKey(), definition);
    }
    out.put("scopes", scopes);
    return out;
  }

  private static Map<String, Object> entities(Map<String, EntityDef> entities) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, EntityDef> entry : entities.entrySet()) {
      Map<String, Object> member = new LinkedHashMap<>();
      member.put("belongsTo", Order.sortedCopy(entry.getValue().belongsTo()));
      member.put("hasMany", Order.sortedCopy(entry.getValue().hasMany()));
      out.put(entry.getKey(), member);
    }
    return out;
  }

  private static Map<String, Object> facts(Map<String, FactDef> facts) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, FactDef> entry : facts.entrySet()) {
      Map<String, Object> member = new LinkedHashMap<>();
      member.put("entity", entry.getValue().entity());
      member.put("type", TypeWriter.write(entry.getValue().type()));
      out.put(entry.getKey(), member);
    }
    return out;
  }

  private static Map<String, Object> keys(Map<String, KeyDef> keys, Hierarchy hierarchy) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, KeyDef> entry : keys.entrySet()) {
      KeyDef key = entry.getValue();
      Map<String, Object> member = new LinkedHashMap<>();
      member.put("type", TypeWriter.write(key.type()));
      member.put("combine", key.combine());
      key.defaultValue().ifPresent(value -> member.put("default", value));
      member.put("settableAt", Order.sortedByReference(key.settableAt(), hierarchy.scopeOrder()));
      key.combineAcrossAxes().ifPresent(value -> member.put("combineAcrossAxes", value));
      key.onSourceFailure().ifPresent(value -> member.put("onSourceFailure", value));
      key.kind().ifPresent(value -> member.put("kind", value));
      out.put(entry.getKey(), member);
    }
    return out;
  }

  private static Map<String, Object> values(Values values) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, Map<String, Object>>> scope : values.byScope().entrySet()) {
      Map<String, Object> nodes = new LinkedHashMap<>();
      for (Map.Entry<String, Map<String, Object>> node : scope.getValue().entrySet()) {
        if (!node.getValue().isEmpty()) {
          nodes.put(node.getKey(), new LinkedHashMap<>(node.getValue()));
        }
      }
      if (!nodes.isEmpty()) {
        out.put(scope.getKey(), nodes);
      }
    }
    return out;
  }

  private static Map<String, Object> features(Map<String, FeatureDef> features, Hierarchy hierarchy) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, FeatureDef> entry : features.entrySet()) {
      out.put(entry.getKey(), feature(entry.getValue(), hierarchy));
    }
    return out;
  }

  private static Map<String, Object> feature(FeatureDef feature, Hierarchy hierarchy) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("decidedPer", feature.decidedPer());
    out.put("returns", returns(feature.returns()));
    out.put("hitPolicy", feature.hitPolicy());
    out.put("ifFactMissing", feature.ifFactMissing());
    out.put("bindingSettableAt", Order.sortedByReference(feature.bindingSettableAt(), hierarchy.scopeOrder()));
    Map<String, Object> policies = new LinkedHashMap<>();
    for (Map.Entry<String, PolicyDef> policy : feature.policies().entrySet()) {
      policies.put(policy.getKey(), policy(policy.getValue()));
    }
    out.put("policies", policies);
    out.put("bindings", bindings(feature.bindings()));
    return out;
  }

  private static Map<String, Object> returns(Returns returns) {
    Map<String, Object> out = new LinkedHashMap<>();
    if (returns.objectForm()) {
      Map<String, Object> fields = new LinkedHashMap<>();
      for (Map.Entry<String, TypeDecl> field : returns.fields().entrySet()) {
        fields.put(field.getKey(), TypeWriter.write(field.getValue()));
      }
      out.put("object", fields);
    } else {
      out.put("enum", Order.sortedCopy(returns.enumValues()));
      if (returns.nullable()) {
        out.put("nullable", Boolean.TRUE);
      }
    }
    return out;
  }

  private static Map<String, Object> policy(PolicyDef policy) {
    Map<String, Object> settings = new LinkedHashMap<>();
    for (Map.Entry<String, SettingDef> entry : policy.settings().entrySet()) {
      settings.put(entry.getKey(), setting(entry.getValue()));
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("settings", settings);
    out.put("default", policy.defaultEffect());
    out.put("requiredFacts", Order.sortedCopy(policy.requiredFacts()));
    List<Object> rules = new ArrayList<>();
    for (RuleDef rule : policy.rules()) {
      rules.add(rule(rule));
    }
    out.put("rules", rules);
    return out;
  }

  private static Map<String, Object> setting(SettingDef setting) {
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("default", orderedIfList(setting.defaultValue()));
    out.put(setting.kind() == SettingDef.Kind.VALUE_OF ? "valueOf" : "listOf", setting.fact());
    return out;
  }

  private static Map<String, Object> rule(RuleDef rule) {
    List<ConditionDef> conditions = new ArrayList<>(rule.when());
    conditions.sort(Comparator.comparing(ConditionDef::fact, Order.CODE_POINT));
    List<Object> when = new ArrayList<>();
    for (ConditionDef condition : conditions) {
      Map<String, Object> member = new LinkedHashMap<>();
      member.put("fact", condition.fact());
      member.put("op", condition.op());
      member.put("value", "eq".equals(condition.op()) ? condition.value() : orderedIfList(condition.value()));
      when.add(member);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("id", rule.id());
    out.put("name", rule.name());
    out.put("when", when);
    out.put("then", rule.then());
    return out;
  }

  private static Map<String, Object> bindings(Map<String, Map<String, BindingDef>> bindings) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Map<String, BindingDef>> level : bindings.entrySet()) {
      if (level.getValue().isEmpty()) {
        continue;
      }
      Map<String, Object> nodes = new LinkedHashMap<>();
      for (Map.Entry<String, BindingDef> node : level.getValue().entrySet()) {
        nodes.put(node.getKey(), binding(node.getValue()));
      }
      out.put(level.getKey(), nodes);
    }
    return out;
  }

  private static Map<String, Object> binding(BindingDef binding) {
    Map<String, Object> settings = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : binding.settings().entrySet()) {
      settings.put(entry.getKey(), orderedIfList(entry.getValue()));
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("policy", binding.policy());
    out.put("settings", settings);
    return out;
  }

  /**
   * Writes a list of scalar literals in bundle order, which is code point order for strings (BUNDLE-75, BUNDLE-152).
   * Any other value is written as it is.
   */
  private static Object orderedIfList(Object value) {
    if (value instanceof List<?> items) {
      return Order.sortedScalars(items);
    }
    return value;
  }
}
