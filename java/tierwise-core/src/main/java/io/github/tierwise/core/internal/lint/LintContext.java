package io.github.tierwise.core.internal.lint;

import io.github.tierwise.core.internal.model.Model;
import io.github.tierwise.core.internal.project.SourceSet;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.List;
import java.util.Objects;

/**
 * What the lint rules read: the loaded sources with their lint settings, the assembled model, the valid
 * examples, the declaration index of the sources, and the node IDs of the generated catalogs.
 *
 * @param sources the loaded sources
 * @param model the model that the stages assembled
 * @param examples the valid examples of every rules file
 * @param index the declarations of the schema and rules documents
 * @param catalogNodes the items of the generated catalogs of the closed scopes, in document order; each item
 *     locates its finding in the catalog file (LINT-19, FIND-19), and is empty when none is read
 */
public record LintContext(
    SourceSet sources,
    Model model,
    List<LintExample> examples,
    SourceIndex index,
    List<PNode> catalogNodes) {

  /** Validates the components and copies the collections. */
  public LintContext {
    Objects.requireNonNull(sources, "sources");
    Objects.requireNonNull(model, "model");
    Objects.requireNonNull(index, "index");
    examples = List.copyOf(examples);
    catalogNodes = List.copyOf(catalogNodes);
  }

  /**
   * Creates the context and indexes the sources, with no generated catalog read.
   *
   * @param sources the loaded sources
   * @param model the assembled model
   * @param examples the valid examples
   * @return the context
   */
  public static LintContext of(SourceSet sources, Model model, List<LintExample> examples) {
    return of(sources, model, examples, List.of());
  }

  /**
   * Creates the context and indexes the sources.
   *
   * @param sources the loaded sources
   * @param model the assembled model
   * @param examples the valid examples
   * @param catalogNodes the items of the generated catalogs of the closed scopes
   * @return the context
   */
  public static LintContext of(SourceSet sources, Model model, List<LintExample> examples, List<PNode> catalogNodes) {
    return new LintContext(sources, model, examples, SourceIndex.of(sources), catalogNodes);
  }
}
