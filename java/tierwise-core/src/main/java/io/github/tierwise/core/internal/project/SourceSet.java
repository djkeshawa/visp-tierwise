package io.github.tierwise.core.internal.project;

import io.github.tierwise.core.internal.yaml.PMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * The immutable result of loading a project: every contributing document (SRC-13), parsed and tagged,
 * and the lint settings.
 *
 * <p>The documents are in definition order. Presets come first in {@code extends} order, then the
 * manifest's inline content, then source files in code-point order of their paths (MANIFEST-31,
 * FIND-32). Inline rules come in the order of the {@code rules} list. A stage that reports a repeat
 * therefore reports the later document by taking the documents in this order. The set does not depend
 * on the order in which the manifest lists patterns or files (SRC-37).
 *
 * @param manifestRoot the manifest root, or {@code null} when the manifest could not be read
 * @param documents every document, in definition order
 * @param lint the valid lint settings
 * @param rootLevel the name of the root level, when a hierarchy declares one that the loader can read
 */
public record SourceSet(PMap manifestRoot, List<SourceDocument> documents, LintSettings lint,
    Optional<String> rootLevel) {

  /** Copies the documents. */
  public SourceSet {
    Objects.requireNonNull(lint, "lint");
    Objects.requireNonNull(rootLevel, "rootLevel");
    documents = List.copyOf(documents);
  }

  /**
   * The set of a project whose manifest could not be read.
   *
   * @return a set with no document
   */
  public static SourceSet empty() {
    return new SourceSet(null, List.of(), LintSettings.none(), Optional.empty());
  }

  /**
   * Returns the manifest root.
   *
   * @return the root, or empty when the manifest could not be read
   */
  public Optional<PMap> manifest() {
    return Optional.ofNullable(manifestRoot);
  }

  /**
   * Returns the documents of one role, in definition order.
   *
   * @param role the role
   * @return the documents
   */
  public List<SourceDocument> of(SourceRole role) {
    return documents.stream().filter(document -> document.role() == role).collect(Collectors.toList());
  }

  /**
   * Returns the schema documents, presets included, in definition order.
   *
   * @return the documents
   */
  public List<SourceDocument> schemas() {
    return of(SourceRole.SCHEMA);
  }

  /**
   * Returns the values documents in definition order.
   *
   * @return the documents
   */
  public List<SourceDocument> values() {
    return of(SourceRole.VALUES);
  }

  /**
   * Returns the rules documents in definition order.
   *
   * @return the documents
   */
  public List<SourceDocument> rules() {
    return of(SourceRole.RULES);
  }
}
