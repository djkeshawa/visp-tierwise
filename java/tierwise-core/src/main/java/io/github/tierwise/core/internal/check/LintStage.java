package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.internal.lint.LintContext;
import io.github.tierwise.core.internal.lint.LintFinding;
import io.github.tierwise.core.internal.lint.LintRunner;
import io.github.tierwise.core.internal.model.Model;
import io.github.tierwise.core.internal.project.Names;
import io.github.tierwise.core.internal.project.SourceDocument;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The lint stage (LINT-1 to LINT-50).
 *
 * <p>The stage runs the lint rules of the project with the severity that the lint settings of its presets and
 * manifest resolve (LINT-4 to LINT-7), and reports each lint finding at the resolved severity (FIND-16). It
 * runs only when no earlier stage has reported an engine finding (LINT-11), and it needs a complete model and
 * the examples that the example stage published (FIND-35).
 *
 * <p>The node IDs of a generated catalog of a closed scope are read here, because the lint package reads no
 * project file (LINT-19). A catalog that cannot be read is left out: its fault is an engine finding, so the
 * stage does not run. When the project directory is not known, no catalog is read.
 */
public final class LintStage implements Stage {

  @Override
  public String name() {
    return "lint";
  }

  @Override
  public boolean enabled(CheckContext context) {
    return !context.findings().hasEngineFindings();
  }

  @Override
  public void run(CheckContext context) {
    Optional<Model> model = ModelAssembler.assemble(context.parts());
    Optional<ExampleSet> examples = context.find(ExampleSet.class);
    if (model.isEmpty() || examples.isEmpty()) {
      return;
    }
    LintContext lint = LintContext.of(context.sources(), model.get(), examples.get().examples(),
        generatedNodes(context));
    for (LintFinding finding : LintRunner.run(lint)) {
      context.findings().lint(finding.ruleId(), finding.severity(), finding.node(), finding.message());
    }
  }

  /**
   * Reads the items of the generated catalogs that the closed scopes of the hierarchy name with
   * {@code nodes: {from: ...}} (SCOPE-27 to SCOPE-31). The items keep the catalog file and their path, so a
   * naming finding about one is located in the catalog (FIND-19, FIND-23, LINT-21).
   */
  private static List<PNode> generatedNodes(CheckContext context) {
    Optional<Path> root = context.projectRoot();
    Optional<SourceDocument> declaring = context.sources().schemas().stream()
        .filter(document -> !document.isNamespaced())
        .filter(document -> document.root().has("decision_hierarchy"))
        .findFirst();
    if (root.isEmpty() || declaring.isEmpty()) {
      return List.of();
    }
    Optional<PMap> hierarchy = declaring.get().root().get("decision_hierarchy")
        .filter(PMap.class::isInstance).map(PMap.class::cast);
    Optional<PMap> scopes = hierarchy.flatMap(decision -> decision.get("scopes"))
        .filter(PMap.class::isInstance).map(PMap.class::cast);
    if (scopes.isEmpty()) {
      return List.of();
    }
    HierarchyCatalog catalog = new HierarchyCatalog(root.get());
    ScopeReport unreported = new ScopeReport(new Findings());
    List<PNode> nodes = new ArrayList<>();
    for (PNode scope : scopes.get().entries().values()) {
      if (!(scope instanceof PMap body) || !(body.get("nodes").orElse(null) instanceof PMap object)) {
        continue;
      }
      Optional<PNode> from = object.get("from");
      Optional<String> path = from.flatMap(Names::text).filter(text -> !text.isEmpty());
      if (path.isPresent()) {
        catalog.read(path.get(), from.get(), unreported).ifPresent(read -> nodes.addAll(read.items()));
      }
    }
    return nodes;
  }
}
