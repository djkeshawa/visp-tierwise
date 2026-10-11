package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.BindingDef;
import io.github.visplabs.tierwise.core.internal.model.FeatureDef;
import io.github.visplabs.tierwise.core.internal.model.PolicyDef;
import io.github.visplabs.tierwise.core.internal.project.SourceDocument;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The feature, policy, rule and binding stage (sections 7 and 8: FEAT, POL, BIND and the compile-time RULE
 * clauses).
 *
 * <p>The stage reads the {@code features} of the schema documents and the rules documents, reports every
 * fault of them and builds {@link ModelParts#features(Map)}. It publishes the part only when every
 * feature is declared without fault, every feature is configured by a rules document without fault, and
 * the hierarchy, the entities and the facts were built, because a check that lacks one of them is skipped
 * (FIND-35).
 *
 * <p>Two rules documents that name the same feature are reported by the loader ({@code E_DUPLICATE_DEFINITION},
 * POL-5, FIND-34). The stage still checks the later document, and builds the feature from the first one.
 * Examples are not read here; the example stage checks them (POL-28 to POL-49).
 */
public final class FeatureStage implements Stage {

  @Override
  public String name() {
    return "features";
  }

  @Override
  public void run(CheckContext context) {
    Findings findings = context.findings();
    FeatureInputs inputs = FeatureInputs.of(context);
    Map<String, FeatureDecl> features = declarations(context, findings, inputs);
    Map<String, List<RulesDocumentParser.Result>> configured = new LinkedHashMap<>();
    RulesDocumentParser rules = new RulesDocumentParser(findings, inputs, features);
    for (SourceDocument document : context.sources().rules()) {
      RulesDocumentParser.Result result = rules.parse(document);
      result.feature().ifPresent(name -> configured.computeIfAbsent(name, key -> new ArrayList<>()).add(result));
    }
    Map<String, FeatureDef> built = new LinkedHashMap<>();
    boolean complete = inputs.complete();
    for (FeatureDecl feature : features.values()) {
      Optional<FeatureDef> def = build(feature, configured.get(feature.name()), findings, inputs);
      if (def.isPresent()) {
        built.put(feature.name(), def.get());
      } else {
        complete = false;
      }
    }
    if (complete) {
      context.parts().features(built);
    }
  }

  /** Parses the feature declarations of the schema documents, presets included, in definition order. */
  private static Map<String, FeatureDecl> declarations(CheckContext context, Findings findings, FeatureInputs inputs) {
    FeatureDeclParser parser = new FeatureDeclParser(findings, inputs);
    Map<String, FeatureDecl> features = new LinkedHashMap<>();
    for (SourceDocument document : context.sources().schemas()) {
      if (document.root().get("features").orElse(null) instanceof PMap declared) {
        for (Map.Entry<String, PNode> entry : declared.entries().entrySet()) {
          // A second declaration is reported by the loader (SRC-40), so the first one stands.
          if (!features.containsKey(entry.getKey())) {
            features.put(entry.getKey(), parser.parse(entry.getKey(), entry.getValue()));
          }
        }
      }
    }
    return features;
  }

  /** Builds a feature from its declaration and its first rules document, or reports that it has none. */
  private static Optional<FeatureDef> build(FeatureDecl feature, List<RulesDocumentParser.Result> documents,
      Findings findings, FeatureInputs inputs) {
    if (documents == null) {
      if (inputs.hierarchy().isPresent()) {
        findings.error(Code.E_ROOT_BINDING_MISSING, feature.node(),
            "the feature " + feature.name() + " is configured by no rules file, so it has no root binding (BIND-13)");
      }
      return Optional.empty();
    }
    RulesDocumentParser.Result first = documents.get(0);
    if (!feature.clean() || first.policies().isEmpty() || first.bindings().isEmpty()) {
      return Optional.empty();
    }
    Map<String, PolicyDef> policies = first.policies().get();
    Map<String, Map<String, BindingDef>> bindings = first.bindings().get();
    return Optional.of(new FeatureDef(feature.decidedPer().orElseThrow(), feature.returns().orElseThrow(), "FIRST",
        feature.ifFactMissing().orElseThrow().value(), feature.bindingSettableAt().orElseThrow(), policies, bindings));
  }
}
