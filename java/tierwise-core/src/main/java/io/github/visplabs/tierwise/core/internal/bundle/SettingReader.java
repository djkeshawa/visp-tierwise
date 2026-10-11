package io.github.visplabs.tierwise.core.internal.bundle;

import io.github.visplabs.tierwise.core.internal.model.FactDef;
import io.github.visplabs.tierwise.core.internal.model.SettingDef;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the settings of a policy (BUNDLE-84 to BUNDLE-90, POL-15 to POL-20), and checks the list values that
 * settings and bindings hold.
 */
final class SettingReader {

  private SettingReader() {
  }

  /**
   * Reads the settings of one policy.
   *
   * @param value the {@code settings} member
   * @param facts the declared facts
   * @param at the location of the policy
   * @return the settings by name, possibly empty
   */
  static Map<String, SettingDef> read(Object value, Map<String, FactDef> facts, String at) {
    Map<String, Object> raw = Members.object(value, at + ".settings");
    Map<String, SettingDef> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> entry : raw.entrySet()) {
      String name = entry.getKey();
      String settingAt = at + ".settings." + name;
      requireSettingName(name, settingAt);
      out.put(name, readSetting(entry.getValue(), facts, settingAt));
    }
    return out;
  }

  private static SettingDef readSetting(Object value, Map<String, FactDef> facts, String at) {
    Map<String, Object> members = Members.object(value, at);
    boolean single = members.containsKey("valueOf");
    String form = single ? "valueOf" : "listOf";
    Members.members(members, at, Set.of("default", form), Set.of());
    String factName = Members.string(members.get(form), at + "." + form);
    FactDef fact = facts.get(factName);
    if (fact == null) {
      throw BundleFault.integrity(at + " names the undeclared fact " + factName + " (BUNDLE-120)");
    }
    Object defaultValue = members.get("default");
    if (single) {
      ValueChecker.check(fact.type(), defaultValue, ValueChecker.Mode.COMPLETE, at + ".default");
      return new SettingDef(SettingDef.Kind.VALUE_OF, factName, defaultValue);
    }
    requireListValue(fact.type(), defaultValue, at + ".default");
    return new SettingDef(SettingDef.Kind.LIST_OF, factName, defaultValue);
  }

  private static void requireSettingName(String name, String at) {
    if (name.isEmpty() || name.contains("$") || name.contains(".") || name.equals("policy")) {
      throw BundleFault.integrity(at + " is not a valid setting name (POL-20)");
    }
  }

  /**
   * Requires that a value is a list of distinct values of a fact's type, in bundle order (BUNDLE-89, BUNDLE-90,
   * BUNDLE-133).
   *
   * @param type the fact's type
   * @param value the list
   * @param at the location of the list
   */
  static void requireListValue(TypeDecl type, Object value, String at) {
    if (!(value instanceof List<?> items)) {
      throw BundleFault.integrity(at + " must be a list of values (BUNDLE-89)");
    }
    for (int i = 0; i < items.size(); i++) {
      ValueChecker.check(type, items.get(i), ValueChecker.Mode.COMPLETE, at + "[" + i + "]");
      if (i > 0) {
        int order = Order.compareScalars(items.get(i - 1), items.get(i));
        if (order == 0) {
          throw BundleFault.integrity(at + " repeats an element (BUNDLE-133)");
        }
        if (order > 0) {
          throw BundleFault.integrity(at + " is not in bundle order (BUNDLE-90, BUNDLE-75, BUNDLE-152)");
        }
      }
    }
  }
}
