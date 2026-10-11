package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.CompileOptions;
import io.github.visplabs.tierwise.core.CompileResult;
import io.github.visplabs.tierwise.core.internal.bundle.BundleWriter;
import io.github.visplabs.tierwise.core.internal.model.Model;
import io.github.visplabs.tierwise.core.internal.project.ProjectLoad;
import io.github.visplabs.tierwise.core.internal.project.ProjectLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The compiler pipeline (section 13.1).
 *
 * <p>The pipeline loads the project, then runs the stages in a fixed order over one {@link CheckContext}:
 * manifest, hierarchy, entities and facts, kinds, keys, values, features, examples, lint. It finally
 * writes the canonical bundle, but only when no finding has severity {@code error} (FIND-7, FIND-8) and
 * every part of the model was built. Every stage runs even when an earlier one reported a fault, so that
 * the validation reports every finding it determines (FIND-3); a stage skips the checks whose inputs
 * failed to build (FIND-35). Examples and lint are the exceptions: they run only when there is no other
 * engine finding (POL-47, LINT-11). The findings are ordered as FIND-26 requires.
 */
public final class Compiler {

  private final List<Stage> stages;

  /**
   * Creates a compiler with the given stages.
   *
   * @param stages the stages in the order they run
   */
  public Compiler(List<Stage> stages) {
    this.stages = List.copyOf(stages);
  }

  /**
   * Returns the stages in their fixed order.
   *
   * @return a new list: manifest, hierarchy, entities and facts, kinds, keys, values, features,
   *     examples, lint
   */
  public static List<Stage> standardStages() {
    return List.of(new ManifestStage(), new HierarchyStage(), new EntityFactStage(), new KindStage(),
        new KeyStage(), new ValueStage(), new FeatureStage(), new ExampleStage(), new LintStage());
  }

  /**
   * Compiles the project in a directory with the standard stages.
   *
   * @param projectRoot the directory that contains {@code tierwise.yaml}
   * @param options the compile options
   * @return the findings and, when none is an error, the bundle
   */
  public static CompileResult compile(Path projectRoot, CompileOptions options) {
    return new Compiler(standardStages()).run(projectRoot, options);
  }

  /**
   * Compiles the project in a directory.
   *
   * @param projectRoot the directory that contains {@code tierwise.yaml}
   * @param options the compile options
   * @return the findings and, when none is an error, the bundle
   */
  public CompileResult run(Path projectRoot, CompileOptions options) {
    Objects.requireNonNull(projectRoot, "projectRoot");
    Objects.requireNonNull(options, "options");
    ProjectLoad project = ProjectLoader.load(projectRoot, options);
    CheckContext context = new CheckContext(project, options, projectRoot);
    for (Stage stage : stages) {
      if (stage.enabled(context)) {
        stage.run(context);
      }
    }
    return result(context);
  }

  private static CompileResult result(CheckContext context) {
    byte[] bundle = null;
    if (!context.findings().hasErrors()) {
      Optional<Model> model = ModelAssembler.assemble(context.parts());
      if (model.isPresent()) {
        bundle = BundleWriter.write(model.get()).bytes();
      }
    }
    return new CompileResult(context.findings().sorted(), bundle);
  }
}
