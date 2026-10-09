package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.PolicyDef;
import io.github.tierwise.core.internal.model.Returns;
import io.github.tierwise.core.internal.model.RuleDef;
import io.github.tierwise.core.internal.model.SettingDef;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads one policy (BUNDLE-83 to BUNDLE-92, BUNDLE-136): its settings, its default effect, its rules, and its
 * {@code requiredFacts}, which must equal the facts that the rules use.
 */
final class PolicyReader {

  private PolicyReader() {
  }

  /**
   * Reads a policy.
   *
   * @param value the policy object
   * @param facts the declared facts
   * @param returns the feature's effect form
   * @param at the location of the policy
   * @return the policy
   */
  static PolicyDef read(Object value, Map<String, FactDef> facts, Returns returns, String at) {
    Map<String, Object> members = Members.object(value, at);
    Members.members(members, at, Set.of("settings", "default", "requiredFacts", "rules"), Set.of());
    Map<String, SettingDef> settings = SettingReader.read(members.get("settings"), facts, at);
    EffectChecker.check(returns, members.get("default"), at + ".default");
    List<String> requiredFacts = Members.stringArray(members.get("requiredFacts"), at + ".requiredFacts");
    if (!Order.strictlyIncreasing(requiredFacts)) {
      throw BundleFault.integrity(at + ".requiredFacts is not in code point order without repeats (BUNDLE-92)");
    }
    for (String fact : requiredFacts) {
      if (!facts.containsKey(fact)) {
        throw BundleFault.integrity(at + ".requiredFacts names the undeclared fact " + fact + " (BUNDLE-120)");
      }
    }
    List<RuleDef> rules = RuleReader.read(members.get("rules"), settings, facts, returns, at + ".rules");
    if (!RuleReader.derivedFacts(rules).equals(requiredFacts)) {
      throw BundleFault.integrity(at + ".requiredFacts is not the distinct facts of its rules (BUNDLE-136)");
    }
    return new PolicyDef(settings, members.get("default"), requiredFacts, rules);
  }
}
