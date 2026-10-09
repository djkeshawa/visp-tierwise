package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.model.ConditionDef;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.Returns;
import io.github.tierwise.core.internal.model.RuleDef;
import io.github.tierwise.core.internal.model.SettingDef;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Reads the rules of a policy (BUNDLE-93 to BUNDLE-104, BUNDLE-125, BUNDLE-126, BUNDLE-132) and derives the fact
 * names that its conditions use (BUNDLE-91).
 */
final class RuleReader {

  private static final Set<String> SINGLE_OPERATORS = Set.of("eq");
  private static final Set<String> LIST_OPERATORS = Set.of("in", "all_in", "any_in");

  private RuleReader() {
  }

  /**
   * Reads the rules of one policy.
   *
   * @param value the {@code rules} member
   * @param settings the policy's settings
   * @param facts the declared facts
   * @param returns the feature's effect form
   * @param at the location of the rules
   * @return the rules in source order
   */
  static List<RuleDef> read(Object value, Map<String, SettingDef> settings, Map<String, FactDef> facts,
      Returns returns, String at) {
    List<Object> elements = Members.array(value, at);
    List<RuleDef> rules = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (int i = 0; i < elements.size(); i++) {
      String ruleAt = at + "[" + i + "]";
      Map<String, Object> members = Members.object(elements.get(i), ruleAt);
      Members.members(members, ruleAt, Set.of("id", "name", "when", "then"), Set.of());
      String id = Members.string(members.get("id"), ruleAt + ".id");
      if (!ids.add(id)) {
        throw BundleFault.integrity(ruleAt + " repeats the rule id " + id + " in its policy (BUNDLE-132)");
      }
      String name = Members.string(members.get("name"), ruleAt + ".name");
      List<ConditionDef> conditions = readConditions(members.get("when"), settings, facts, ruleAt + ".when");
      EffectChecker.check(returns, members.get("then"), ruleAt + ".then");
      rules.add(new RuleDef(id, name, conditions, members.get("then")));
    }
    return rules;
  }

  /**
   * Returns the fact names of every condition of every rule, distinct and in code point order (BUNDLE-91,
   * BUNDLE-92).
   *
   * @param rules the rules of one policy
   * @return the derived fact names
   */
  static List<String> derivedFacts(List<RuleDef> rules) {
    TreeSet<String> facts = new TreeSet<>(Order.CODE_POINT);
    for (RuleDef rule : rules) {
      for (ConditionDef condition : rule.when()) {
        facts.add(condition.fact());
      }
    }
    return new ArrayList<>(facts);
  }

  private static List<ConditionDef> readConditions(Object value, Map<String, SettingDef> settings,
      Map<String, FactDef> facts, String at) {
    List<Object> elements = Members.array(value, at);
    List<ConditionDef> conditions = new ArrayList<>();
    String previous = null;
    for (int i = 0; i < elements.size(); i++) {
      String conditionAt = at + "[" + i + "]";
      ConditionDef condition = readCondition(elements.get(i), settings, facts, conditionAt);
      if (previous != null && Order.compareCodePoints(previous, condition.fact()) >= 0) {
        throw BundleFault.integrity(conditionAt + " is not sorted by fact, with no repeats (BUNDLE-96, BUNDLE-119)");
      }
      previous = condition.fact();
      conditions.add(condition);
    }
    return conditions;
  }

  private static ConditionDef readCondition(Object value, Map<String, SettingDef> settings, Map<String, FactDef> facts,
      String at) {
    Map<String, Object> members = Members.object(value, at);
    Members.members(members, at, Set.of("fact", "op", "value"), Set.of());
    String factName = Members.string(members.get("fact"), at + ".fact");
    FactDef fact = facts.get(factName);
    if (fact == null) {
      throw BundleFault.integrity(at + " names the undeclared fact " + factName + " (BUNDLE-120)");
    }
    String op = Members.oneOf(members.get("op"), union(SINGLE_OPERATORS, LIST_OPERATORS), at + ".op");
    Object operand = members.get("value");
    String valueAt = at + ".value";
    if (isReference(operand)) {
      requireReference((String) operand, op, factName, settings, valueAt);
    } else if (SINGLE_OPERATORS.contains(op)) {
      ValueChecker.check(fact.type(), operand, ValueChecker.Mode.COMPLETE, valueAt);
    } else {
      requireLiteralList(fact, operand, valueAt);
    }
    return new ConditionDef(factName, op, operand);
  }

  /** A string that begins with {@code $} is a setting reference (RULE-4, BUNDLE-104). */
  private static boolean isReference(Object operand) {
    return operand instanceof String text && text.startsWith(ConditionDef.SETTING_PREFIX);
  }

  private static void requireReference(String operand, String op, String factName, Map<String, SettingDef> settings,
      String at) {
    String settingName = operand.substring(ConditionDef.SETTING_PREFIX.length());
    SettingDef setting = settings.get(settingName);
    if (setting == null) {
      throw BundleFault.integrity(at + " names no setting of its policy (BUNDLE-125)");
    }
    boolean allowed = SINGLE_OPERATORS.contains(op)
        ? setting.kind() == SettingDef.Kind.VALUE_OF
        : setting.kind() == SettingDef.Kind.LIST_OF;
    if (!allowed || !setting.fact().equals(factName)) {
      throw BundleFault.integrity(at + " is a setting reference in a position that RULE does not allow (BUNDLE-126)");
    }
  }

  /**
   * Requires the operand list of {@code in}, {@code all_in} or {@code any_in}: literals of the fact's type, with no
   * setting reference, in bundle order (BUNDLE-75, BUNDLE-101, BUNDLE-126, BUNDLE-152).
   */
  private static void requireLiteralList(FactDef fact, Object operand, String at) {
    if (!(operand instanceof List<?> items)) {
      throw BundleFault.integrity(at + " must be a list of literals or a setting reference (BUNDLE-101)");
    }
    for (int i = 0; i < items.size(); i++) {
      if (isReference(items.get(i))) {
        throw BundleFault.integrity(at + "[" + i + "] is a setting reference inside a literal list (BUNDLE-126)");
      }
      ValueChecker.check(fact.type(), items.get(i), ValueChecker.Mode.COMPLETE, at + "[" + i + "]");
      if (i > 0 && Order.compareScalars(items.get(i - 1), items.get(i)) > 0) {
        throw BundleFault.integrity(at + " is not in bundle order (BUNDLE-75, BUNDLE-152)");
      }
    }
  }

  private static Set<String> union(Set<String> first, Set<String> second) {
    Set<String> all = new HashSet<>(first);
    all.addAll(second);
    return all;
  }
}
