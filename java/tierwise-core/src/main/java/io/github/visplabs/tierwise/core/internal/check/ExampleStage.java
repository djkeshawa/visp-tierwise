package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.project.SourceDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The example stage (POL-41 to POL-49, FIND-2, FIND-34).
 *
 * <p>The stage checks the examples of every rules document: their shape, their names, their context, their
 * facts and their {@code expect} (POL-28 to POL-45). Each example that passes all checks is run through the
 * evaluator over the in-memory model (POL-41) when the validation has no other engine finding, because an
 * example runs only in that case (POL-47, ADR 0014). The stage publishes the valid examples for the lint stage.
 *
 * <p>The stage runs only when no earlier stage has reported an engine finding. Examples are compile-time data
 * and are not written to the bundle (BUNDLE-109).
 */
public final class ExampleStage implements Stage {

  @Override
  public String name() {
    return "examples";
  }

  @Override
  public boolean enabled(CheckContext context) {
    return !context.findings().hasEngineFindings();
  }

  @Override
  public void run(CheckContext context) {
    Optional<Model> model = ModelAssembler.assemble(context.parts());
    ExampleChecker checker = new ExampleChecker(model.orElse(null), context.findings());
    List<ExampleCase> cases = new ArrayList<>();
    for (SourceDocument document : context.sources().rules()) {
      cases.addAll(checker.check(document));
    }
    if (model.isPresent() && !context.findings().hasEngineFindings()) {
      ExampleRunner.run(model.get(), cases, context.findings());
    }
    context.provide(ExampleSet.class, new ExampleSet(cases.stream().map(ExampleCase::lint).toList()));
  }
}
