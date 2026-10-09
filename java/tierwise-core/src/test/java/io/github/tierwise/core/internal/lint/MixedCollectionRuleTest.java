package io.github.tierwise.core.internal.lint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.tierwise.core.internal.model.BindingDef;
import io.github.tierwise.core.internal.model.ConditionDef;
import io.github.tierwise.core.internal.model.EntityDef;
import io.github.tierwise.core.internal.model.FactDef;
import io.github.tierwise.core.internal.model.FeatureDef;
import io.github.tierwise.core.internal.model.Model;
import io.github.tierwise.core.internal.model.PolicyDef;
import io.github.tierwise.core.internal.model.Returns;
import io.github.tierwise.core.internal.model.RuleDef;
import io.github.tierwise.core.internal.model.TypeDecl;
import io.github.tierwise.core.internal.model.Values;
import io.github.tierwise.core.internal.project.SourceSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@code examples/mixed-collection} (LINT-32 to LINT-36). */
class MixedCollectionRuleTest {

  @TempDir
  Path dir;

  private static final String RULES = """
      feature: order.submission
      policies:
        review:
          default: DENY
          rules:
            - id: all-stock
              name: All stock
              when:
                item.stock: { all_in: [STOCKED] }
              then: ALLOW
      bindings:
        global: { policy: review }
      """;

  @Test
  void aPolicyNeedsAnExampleWithAMixedChildCollection() {
    SourceSet sources = LintFixture.sources(dir, Map.of("rules/order.yaml", RULES));
    Model model = model();
    LintExample mixed = new LintExample("order.submission", Map.of(), Map.of(),
        Map.of("item", List.of(Map.of("item.stock", "STOCKED"), Map.of("item.stock", "DROP_SHIP"))));
    LintExample notMixed = new LintExample("order.submission", Map.of(), Map.of(),
        Map.of("item", List.of(Map.of("item.stock", "STOCKED"))));

    assertEquals(List.of(), new MixedCollectionRule().check(LintContext.of(sources, model, List.of(mixed)))
        .stream().map(LintFixture::where).toList());
    assertEquals(List.of("3 [policies, review]"), new MixedCollectionRule()
        .check(LintContext.of(sources, model, List.of(notMixed))).stream().map(LintFixture::where).toList());
  }

  private static Model model() {
    EntityDef order = new EntityDef(List.of(), List.of("item"));
    EntityDef item = new EntityDef(List.of(), List.of());
    FactDef stock = new FactDef("item", TypeDecl.simple("string"));
    PolicyDef review = new PolicyDef(Map.of(), "DENY", List.of("item.stock"), List.of(new RuleDef("all-stock",
        "All stock", List.of(new ConditionDef("item.stock", "all_in", List.of("STOCKED"))), "ALLOW")));
    FeatureDef feature = new FeatureDef("order", Returns.enumeration(List.of("ALLOW", "DENY"), false), "FIRST",
        "DENY", List.of("global"), Map.of("review", review), Map.of("global", Map.of("", new BindingDef("review",
            Map.of()))));
    return LintFixture.model(List.of("global"), Map.of("order", order, "item", item), Map.of("item.stock", stock),
        Map.of(), Values.EMPTY, Map.of("order.submission", feature));
  }
}
