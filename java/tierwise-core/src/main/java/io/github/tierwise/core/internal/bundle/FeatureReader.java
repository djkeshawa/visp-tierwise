package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.model.BindingDef;
import io.github.tierwise.core.internal.model.EntityDef;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.FeatureDef;
import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.model.PolicyDef;
import io.github.tierwise.core.internal.model.Returns;
import io.github.tierwise.core.internal.model.TypeDecl;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads {@code features} (BUNDLE-76 to BUNDLE-82, BUNDLE-120, BUNDLE-121, BUNDLE-127, BUNDLE-128) with their
 * {@code returns}, policies and bindings.
 */
final class FeatureReader {

  private FeatureReader() {
  }

  /**
   * Reads every feature.
   *
   * @param value the {@code features} member
   * @param hierarchy the hierarchy
   * @param entities the declared entities
   * @param facts the declared facts
   * @return the features by name
   */
  static Map<String, FeatureDef> read(Object value, Hierarchy hierarchy, Map<String, EntityDef> entities,
      Map<String, FactDef> facts) {
    Map<String, Object> raw = Members.object(value, "content.features");
    Map<String, FeatureDef> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      out.put(entry.getKey(), readFeature(entry.getValue(), entry.getKey(), hierarchy, entities, facts));
    }
    return out;
  }

  private static FeatureDef readFeature(Object value, String name, Hierarchy hierarchy, Map<String, EntityDef> entities,
      Map<String, FactDef> facts) {
    String at = "content.features." + name;
    Map<String, Object> members = Members.object(value, at);
    Members.members(members, at, Set.of("decidedPer", "returns", "hitPolicy", "ifFactMissing", "bindingSettableAt",
        "policies", "bindings"), Set.of());
    String decidedPer = Members.string(members.get("decidedPer"), at + ".decidedPer");
    if (!entities.containsKey(decidedPer)) {
      throw BundleFault.integrity(at + " is decided per the undeclared entity " + decidedPer + " (BUNDLE-121)");
    }
    Returns returns = readReturns(members.get("returns"), at + ".returns");
    String hitPolicy = Members.oneOf(members.get("hitPolicy"), Set.of("FIRST"), at + ".hitPolicy");
    EffectChecker.check(returns, members.get("ifFactMissing"), at + ".ifFactMissing");
    List<String> bindingLevels = readBindingLevels(members.get("bindingSettableAt"), at, hierarchy);
    Map<String, PolicyDef> policies = readPolicies(members.get("policies"), facts, returns, at);
    Map<String, Map<String, BindingDef>> bindings =
        BindingReader.read(members.get("bindings"), hierarchy, bindingLevels, policies, facts, at + ".bindings");
    return new FeatureDef(decidedPer, returns, hitPolicy, members.get("ifFactMissing"), bindingLevels, policies,
        bindings);
  }

  private static Returns readReturns(Object value, String at) {
    Map<String, Object> members = Members.object(value, at);
    boolean enumForm = members.containsKey("enum");
    if (enumForm == members.containsKey("object")) {
      throw BundleFault.integrity(at + " takes exactly one of enum and object (FEAT-6)");
    }
    if (enumForm) {
      Members.members(members, at, Set.of("enum"), Set.of("nullable"));
      List<String> values = TypeReader.distinctNonEmpty(members.get("enum"), at + ".enum");
      if (!Order.strictlyIncreasing(values)) {
        throw BundleFault.integrity(at + ".enum is not in code point order (BUNDLE-75, BUNDLE-82)");
      }
      boolean nullable = false;
      if (members.containsKey("nullable")) {
        if (!Boolean.TRUE.equals(members.get("nullable"))) {
          throw BundleFault.integrity(at + ".nullable is present only when true (BUNDLE-82)");
        }
        nullable = true;
      }
      return Returns.enumeration(values, nullable);
    }
    Members.members(members, at, Set.of("object"), Set.of());
    Map<String, Object> raw = Members.object(members.get("object"), at + ".object");
    if (raw.isEmpty()) {
      throw BundleFault.integrity(at + ".object declares no field (TYPE-68)");
    }
    Map<String, TypeDecl> fields = new LinkedHashMap<>();
    for (Map.Entry<String, Object> field : raw.entrySet()) {
      if (field.getKey().isEmpty()) {
        throw BundleFault.integrity(at + ".object has an empty field name (TYPE-68)");
      }
      fields.put(field.getKey(), TypeReader.read(field.getValue(), at + ".object." + field.getKey(), true));
    }
    return Returns.object(fields);
  }

  private static List<String> readBindingLevels(Object value, String at, Hierarchy hierarchy) {
    List<String> names = Members.stringArray(value, at + ".bindingSettableAt");
    if (names.isEmpty()) {
      throw BundleFault.integrity(at + ".bindingSettableAt is empty (FEAT-2)");
    }
    List<String> levels = hierarchy.levels();
    int previous = -1;
    for (String name : names) {
      int position = levels.indexOf(name);
      if (position < 0) {
        throw BundleFault.integrity(at + ".bindingSettableAt names " + name + ", which is not a level (BUNDLE-127, BUNDLE-106)");
      }
      if (position <= previous) {
        throw BundleFault.integrity(at + ".bindingSettableAt is not in level order without repeats (BUNDLE-81)");
      }
      if (hierarchy.scope(name) != null && hierarchy.scope(name).isServiceSourced()) {
        throw BundleFault.integrity(at + ".bindingSettableAt names the service-sourced level " + name + " (BUNDLE-128)");
      }
      previous = position;
    }
    if (!names.contains(hierarchy.rootLevel())) {
      throw BundleFault.integrity(at + ".bindingSettableAt omits the root level (BUNDLE-128)");
    }
    return new ArrayList<>(names);
  }

  private static Map<String, PolicyDef> readPolicies(Object value, Map<String, FactDef> facts, Returns returns,
      String at) {
    Map<String, Object> raw = Members.object(value, at + ".policies");
    Map<String, PolicyDef> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      out.put(entry.getKey(), PolicyReader.read(entry.getValue(), facts, returns, at + ".policies." + entry.getKey()));
    }
    return out;
  }
}
