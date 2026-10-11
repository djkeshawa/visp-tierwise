package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.BindingDef;
import io.github.visplabs.tierwise.core.internal.model.PolicyDef;
import io.github.visplabs.tierwise.core.internal.project.SourceDocument;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses one rules document: its top-level keys, the feature it configures, its policies and its bindings
 * (POL-1 to POL-4, SRC-15). Its examples belong to the example stage (POL-28 to POL-49).
 */
final class RulesDocumentParser {

  /** The top-level keys of a rules file (POL-1). */
  private static final Set<String> KEYS = Set.of("feature", "policies", "bindings", "examples");

  /**
   * What one rules document yields.
   *
   * @param feature the name of the feature the document configures, when it names a declared feature
   * @param policies the policies by name, when the document has no fault in them
   * @param bindings the bindings by level and node key, when the document has no fault in them
   */
  record Result(
      Optional<String> feature,
      Optional<Map<String, PolicyDef>> policies,
      Optional<Map<String, Map<String, BindingDef>>> bindings) {

    static final Result NONE = new Result(Optional.empty(), Optional.empty(), Optional.empty());
  }

  private final Findings findings;
  private final FeatureInputs inputs;
  private final Map<String, FeatureDecl> features;

  /**
   * Creates the parser.
   *
   * @param findings the accumulator that receives the findings
   * @param inputs what the earlier stages built
   * @param features the declared features by name
   */
  RulesDocumentParser(Findings findings, FeatureInputs inputs, Map<String, FeatureDecl> features) {
    this.findings = findings;
    this.inputs = inputs;
    this.features = features;
  }

  /**
   * Parses a document.
   *
   * @param document the rules document
   * @return the result
   */
  Result parse(SourceDocument document) {
    PMap root = document.root();
    for (Map.Entry<String, PNode> entry : root.entries().entrySet()) {
      if (!KEYS.contains(entry.getKey())) {
        findings.error(Code.E_INVALID_DECLARATION, entry.getValue(),
            "a rules file has no top-level key " + entry.getKey() + " (POL-1, SRC-15)");
      }
    }
    Optional<String> featureName = featureName(root);
    for (String required : new String[] {"policies", "bindings"}) {
      if (!root.has(required)) {
        findings.error(Code.E_INVALID_DECLARATION, root, "a rules file declares " + required + " (POL-1)");
      }
    }
    if (featureName.isEmpty()) {
      return Result.NONE;
    }
    FeatureDecl feature = features.get(featureName.get());
    if (feature == null) {
      findings.error(Code.E_UNKNOWN_FEATURE, root.get("feature").orElseThrow(),
          "the feature " + featureName.get() + " is not declared (POL-3)");
      return Result.NONE;
    }
    Optional<Map<String, PolicyInfo>> policies = root.get("policies").flatMap(node -> new PolicyParser(findings, inputs, feature).parse(node));
    Optional<Map<String, Map<String, BindingDef>>> bindings =
        root.get("bindings").flatMap(node -> new BindingParser(findings, inputs, feature, policies).parse(node));
    return new Result(featureName, policies.flatMap(RulesDocumentParser::definitions), bindings);
  }

  private Optional<String> featureName(PMap root) {
    Optional<PNode> node = root.get("feature");
    if (node.isEmpty()) {
      findings.error(Code.E_INVALID_DECLARATION, root, "a rules file declares feature (POL-1)");
      return Optional.empty();
    }
    Optional<String> name = FeatureNodes.name(node.get()).filter(text -> !text.isEmpty());
    if (name.isEmpty()) {
      findings.error(Code.E_INVALID_DECLARATION, node.get(), "feature is the name of one feature (POL-2)");
    }
    return name;
  }

  /** The policies as definitions, or empty when any policy has a fault. */
  private static Optional<Map<String, PolicyDef>> definitions(Map<String, PolicyInfo> policies) {
    Map<String, PolicyDef> result = new LinkedHashMap<>();
    for (PolicyInfo policy : policies.values()) {
      if (policy.definition().isEmpty()) {
        return Optional.empty();
      }
      result.put(policy.name(), policy.definition().get());
    }
    return Optional.of(result);
  }
}
