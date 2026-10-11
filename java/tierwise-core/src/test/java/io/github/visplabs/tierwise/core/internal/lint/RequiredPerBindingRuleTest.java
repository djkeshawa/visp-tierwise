package io.github.visplabs.tierwise.core.internal.lint;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.visplabs.tierwise.core.internal.model.BindingDef;
import io.github.visplabs.tierwise.core.internal.model.FeatureDef;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.model.PolicyDef;
import io.github.visplabs.tierwise.core.internal.model.Returns;
import io.github.visplabs.tierwise.core.internal.model.Values;
import io.github.visplabs.tierwise.core.internal.project.SourceSet;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests {@code examples/required-per-binding} (LINT-29 to LINT-31). */
class RequiredPerBindingRuleTest {

  @TempDir
  Path dir;

  private static final String RULES = """
      feature: order.submission
      policies:
        review:
          default: DENY
          rules: []
      bindings:
        global: { policy: review }
        region:
          north: { policy: review }
      """;

  @Test
  void eachBindingNeedsAnExampleThatSelectsIt() {
    SourceSet sources = LintFixture.sources(dir, Map.of("rules/order.yaml", RULES));
    Model model = model();
    LintExample root = new LintExample("order.submission", Map.of(), Map.of(), Map.of());
    LintExample north = new LintExample("order.submission", Map.of("region", "north"), Map.of(), Map.of());

    List<LintHit> onlyRoot = new RequiredPerBindingRule().check(
        LintContext.of(sources, model, List.of(root)));
    assertEquals(List.of("9 [bindings, region, north]"), onlyRoot.stream().map(LintFixture::where).toList());

    List<LintHit> both = new RequiredPerBindingRule().check(
        LintContext.of(sources, model, List.of(root, north)));
    assertEquals(List.of(), both.stream().map(LintFixture::where).toList());
  }

  private static Model model() {
    PolicyDef review = new PolicyDef(Map.of(), "DENY", List.of(), List.of());
    FeatureDef feature = new FeatureDef("order", Returns.enumeration(List.of("ALLOW", "DENY"), false), "FIRST",
        "DENY", List.of("global", "region"), Map.of("review", review),
        Map.of("global", Map.of("", new BindingDef("review", Map.of())),
            "region", Map.of("north", new BindingDef("review", Map.of()))));
    return LintFixture.model(List.of("global", "region"), Map.of(), Map.of(), Map.of(), Values.EMPTY,
        Map.of("order.submission", feature));
  }
}
