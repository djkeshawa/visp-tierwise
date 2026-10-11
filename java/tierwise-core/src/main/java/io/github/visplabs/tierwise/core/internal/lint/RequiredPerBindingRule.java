package io.github.visplabs.tierwise.core.internal.lint;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.BindingDef;
import io.github.visplabs.tierwise.core.internal.model.FeatureDef;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.project.SourceDocument;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code examples/required-per-binding} (LINT-29 to LINT-31): a binding of a feature that no example selects.
 * The root binding and each node binding under a non-root level count separately.
 */
final class RequiredPerBindingRule implements LintRule {

  @Override
  public String id() {
    return Code.LINT_EXAMPLES_REQUIRED_PER_BINDING;
  }

  @Override
  public List<LintHit> check(LintContext context) {
    Model model = context.model();
    List<LintHit> hits = new ArrayList<>();
    for (Map.Entry<String, FeatureDef> feature : model.features().entrySet()) {
      Optional<SourceDocument> document = context.index().rulesOf(feature.getKey());
      if (document.isEmpty() || !(document.get().root().get("bindings").orElse(null) instanceof PMap bindings)) {
        continue;
      }
      for (Map.Entry<String, Map<String, BindingDef>> level
          : feature.getValue().bindings().entrySet()) {
        for (String node : level.getValue().keySet()) {
          if (!covered(context, feature.getKey(), feature.getValue(), level.getKey(), node)) {
            locate(bindings, level.getKey(), node, model).ifPresent(at -> hits.add(
                new LintHit(at, "no example selects the binding of " + level.getKey() + " " + node)));
          }
        }
      }
    }
    return hits;
  }

  private static boolean covered(LintContext context, String feature, FeatureDef definition, String level,
      String node) {
    for (LintExample example : context.examples()) {
      if (example.feature().equals(feature)) {
        BindingChoice choice = BindingChoice.of(definition, context.model().hierarchy(), example.levels());
        if (choice.level().equals(level) && choice.node().equals(node)) {
          return true;
        }
      }
    }
    return false;
  }

  private static Optional<PNode> locate(PMap bindings, String level, String node, Model model) {
    boolean root = level.equals(model.hierarchy().rootLevel());
    Optional<PNode> atLevel = bindings.get(level);
    if (root) {
      return atLevel;
    }
    return atLevel.filter(PMap.class::isInstance).map(PMap.class::cast).flatMap(map -> map.get(node));
  }
}
