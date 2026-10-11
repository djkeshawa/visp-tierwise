package io.github.visplabs.tierwise.core.internal.lint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.visplabs.tierwise.core.internal.model.BindingDef;
import io.github.visplabs.tierwise.core.internal.model.ConditionDef;
import io.github.visplabs.tierwise.core.internal.model.EntityDef;
import io.github.visplabs.tierwise.core.internal.model.FactDef;
import io.github.visplabs.tierwise.core.internal.model.FeatureDef;
import io.github.visplabs.tierwise.core.internal.model.KeyDef;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.model.PolicyDef;
import io.github.visplabs.tierwise.core.internal.model.Returns;
import io.github.visplabs.tierwise.core.internal.model.RuleDef;
import io.github.visplabs.tierwise.core.internal.model.SettingDef;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import io.github.visplabs.tierwise.core.internal.model.Values;
import io.github.visplabs.tierwise.core.internal.project.SourceSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@code schema/unused} (LINT-37 to LINT-43). */
class UnusedRuleTest {

  @TempDir
  Path dir;

  private static final String SCHEMA = """
      keys:
        search.unused_key:
          type: boolean
          combine: all
          settable_at: [global]
      facts:
        item.spare:
          type: boolean
        item.used:
          type: boolean
      """;

  private static final String RULES = """
      feature: order.submission
      policies:
        review:
          settings:
            spare_flag: { value_of: item.used, default: true }
          default: DENY
          rules:
            - id: used
              name: Used
              when:
                item.used: true
              then: ALLOW
        spare:
          default: DENY
          rules: []
      bindings:
        global: { policy: review }
      """;

  @Test
  void unusedKeysFactsSettingsAndPoliciesAreFlagged() {
    SourceSet sources = LintFixture.sources(dir, Map.of("schema/core.yaml", SCHEMA, "rules/order.yaml", RULES));
    List<String> found = new UnusedRule().check(LintContext.of(sources, model(), List.of())).stream()
        .map(LintFixture::where).sorted().toList();
    assertEquals(List.of("13 [policies, spare]", "2 [keys, search.unused_key]",
        "5 [policies, review, settings, spare_flag]", "7 [facts, item.spare]"), found);
  }

  private static Model model() {
    KeyDef unused = new KeyDef(TypeDecl.simple("boolean"), "all", Optional.empty(), List.of("global"),
        Optional.empty(), Optional.empty(), Optional.empty());
    FactDef spare = new FactDef("item", TypeDecl.simple("boolean"));
    FactDef used = new FactDef("item", TypeDecl.simple("boolean"));
    PolicyDef review = new PolicyDef(Map.of("spare_flag", new SettingDef(SettingDef.Kind.VALUE_OF, "item.used", true)),
        "DENY", List.of("item.used"), List.of(new RuleDef("used", "Used",
            List.of(new ConditionDef("item.used", "eq", true)), "ALLOW")));
    PolicyDef sparePolicy = new PolicyDef(Map.of(), "DENY", List.of(), List.of());
    FeatureDef feature = new FeatureDef("order", Returns.enumeration(List.of("ALLOW", "DENY"), false), "FIRST",
        "DENY", List.of("global"), Map.of("review", review, "spare", sparePolicy),
        Map.of("global", Map.of("", new BindingDef("review", Map.of()))));
    return LintFixture.model(List.of("global"), Map.of("item", new EntityDef(List.of(), List.of())),
        Map.of("item.spare", spare, "item.used", used), Map.of("search.unused_key", unused), Values.EMPTY,
        Map.of("order.submission", feature));
  }
}
