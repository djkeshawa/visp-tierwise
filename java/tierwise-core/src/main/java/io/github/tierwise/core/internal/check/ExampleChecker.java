package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.model.FeatureDef;
import io.github.tierwise.core.internal.model.Model;
import io.github.tierwise.core.internal.project.Names;
import io.github.tierwise.core.internal.project.SourceDocument;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import io.github.tierwise.core.internal.yaml.PSeq;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks the examples of the rules documents (POL-28 to POL-45). It reports every shape fault, and returns the
 * examples whose checks all passed.
 *
 * <p>The checks that need the model (the feature, the context, the facts and the expectation) run only when the
 * model is present (FIND-35). The shape checks run in every case.
 */
final class ExampleChecker {

  private static final Set<String> MEMBERS = Set.of("name", "context", "facts", "expect", "children");
  private static final List<String> REQUIRED = List.of("name", "context", "facts", "expect");

  private final Model model;
  private final Findings findings;
  private final ValueValidator validator;

  /**
   * Creates the checker.
   *
   * @param model the model, or {@code null} when it is not complete
   * @param findings the findings to report to
   */
  ExampleChecker(Model model, Findings findings) {
    this.model = model;
    this.findings = findings;
    this.validator = new ValueValidator(findings);
  }

  /**
   * Checks the examples of one rules document.
   *
   * @param document the rules document
   * @return the valid examples, in document order
   */
  List<ExampleCase> check(SourceDocument document) {
    Optional<PNode> node = document.root().get("examples");
    if (node.isEmpty()) {
      return List.of();
    }
    if (!(node.get() instanceof PSeq examples)) {
      findings.error(Code.E_INVALID_DECLARATION, node.get(), "examples is not a list (POL-28)");
      return List.of();
    }
    String feature = document.root().get("feature").flatMap(Names::text).orElse(null);
    FeatureDef definition = model == null || feature == null ? null : model.features().get(feature);
    ExampleScope scope = definition == null ? null : ExampleScope.of(model, definition.decidedPer());
    Set<String> names = new HashSet<>();
    List<ExampleCase> valid = new ArrayList<>();
    for (PNode item : examples.items()) {
      ExampleCase checked = checkItem(item, names, feature, definition, scope);
      if (checked != null) {
        valid.add(checked);
      }
    }
    return valid;
  }

  private ExampleCase checkItem(PNode item, Set<String> names, String feature, FeatureDef definition,
      ExampleScope scope) {
    if (!(item instanceof PMap example)) {
      findings.error(Code.E_INVALID_DECLARATION, item, "an example is not a mapping (POL-28)");
      return null;
    }
    boolean valid = true;
    for (var member : example.entries().entrySet()) {
      if (!MEMBERS.contains(member.getKey())) {
        findings.error(Code.E_INVALID_DECLARATION, member.getValue(), "an example has the member "
            + member.getKey() + ", which is not allowed (FEAT-2)");
        valid = false;
      }
    }
    for (String required : REQUIRED) {
      if (!example.has(required)) {
        findings.error(Code.E_INVALID_DECLARATION, example, "an example lacks " + required + " (FEAT-2, POL-28)");
        valid = false;
      }
    }
    String name = null;
    PNode nameNode = example.get("name").orElse(null);
    if (nameNode != null) {
      Optional<String> text = Names.text(nameNode);
      if (text.isEmpty()) {
        findings.error(Code.E_INVALID_DECLARATION, nameNode, "the name of an example is not a string (POL-29)");
        valid = false;
      } else {
        name = text.get();
        if (!names.add(name)) {
          findings.error(Code.E_DUPLICATE_VALUE, nameNode, "two examples have the name " + name + " (POL-30)");
        }
      }
    } else {
      valid = false;
    }
    if (definition == null || scope == null) {
      return null;
    }
    ExampleContext.Parsed context = example.get("context").isPresent()
        ? ExampleContext.parse(example.get("context").get(), model.hierarchy(), findings)
        : new ExampleContext.Parsed(Map.of(), Map.of(), false);
    valid &= context.valid();
    ExampleFacts.Parsed facts = null;
    PNode factsNode = example.get("facts").orElse(null);
    if (factsNode != null) {
      facts = ExampleFacts.parse(model, scope, validator, findings, factsNode, example.get("children").orElse(null));
      valid &= facts.valid();
    } else {
      valid = false;
    }
    ExampleExpect.Parsed expect = new ExampleExpect.Parsed(false, null);
    PNode expectNode = example.get("expect").orElse(null);
    if (expectNode != null) {
      expect = ExampleExpect.parse(expectNode, definition.returns(), validator);
      valid &= expect.valid();
    } else {
      valid = false;
    }
    if (!valid || name == null || facts == null) {
      return null;
    }
    return new ExampleCase(feature, name, expectNode, context.levels(), context.axes(), facts.facts(),
        facts.children(), expect.value());
  }
}
