package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.Finding;
import io.github.tierwise.core.Severity;
import io.github.tierwise.core.internal.model.Hierarchy;
import io.github.tierwise.core.internal.project.ProjectLoader;
import io.github.tierwise.core.internal.project.SourceDocument;
import io.github.tierwise.core.internal.project.SourceSet;
import java.util.List;
import java.util.Optional;

/**
 * The decision hierarchy and node admission stage (section 2, SCOPE area).
 *
 * <p>The stage reads the {@code decision_hierarchy} declaration of the first shared or preset schema document
 * that declares one, in definition order (SRC-39). A later declaration is a duplicate, which the project loader
 * reports as {@code E_DUPLICATE_DEFINITION}, and the stage does not read it. A namespaced file declares no
 * hierarchy (NS-4). A project whose sources declare no hierarchy is {@code E_SCOPE_DECLARATION} at the manifest
 * root (SCOPE-48), unless the findings so far already include a manifest, parse or source-mapping error.
 *
 * <p>When the declaration is valid and every file it names could be read, the stage sets
 * {@link ModelParts#hierarchy(Hierarchy)} and publishes the {@link ScopeIndex} lookup for the later stages
 * (SCOPE-43 to SCOPE-46). Otherwise it publishes nothing, and the later stages skip the checks that need the
 * hierarchy (FIND-35).
 */
public final class HierarchyStage implements Stage {

  @Override
  public String name() {
    return "hierarchy";
  }

  @Override
  public void run(CheckContext context) {
    Optional<SourceDocument> declaring = declaringDocument(context.sources());
    if (declaring.isEmpty()) {
      reportMissing(context);
      return;
    }
    ScopeReport report = new ScopeReport(context.findings());
    HierarchyCatalog catalog = context.projectRoot().map(HierarchyCatalog::new).orElse(null);
    HierarchyDeclaration reader = new HierarchyDeclaration(report, catalog);
    Optional<Hierarchy> hierarchy = reader.read(declaring.get().root().get("decision_hierarchy").orElseThrow());
    if (hierarchy.isPresent() && report.clean()) {
      context.parts().hierarchy(hierarchy.get());
      context.provide(ScopeIndex.class, new ScopeIndex(hierarchy.get()));
    }
  }

  /** The first non-namespaced schema document that declares a hierarchy (SRC-39, NS-4). */
  private static Optional<SourceDocument> declaringDocument(SourceSet sources) {
    return sources.schemas().stream()
        .filter(document -> !document.isNamespaced())
        .filter(document -> document.root().has("decision_hierarchy"))
        .findFirst();
  }

  /** SCOPE-48: no source declares a hierarchy, unless an earlier stage already failed the project. */
  private static void reportMissing(CheckContext context) {
    if (context.findings().hasCode(Code.E_MANIFEST, Code.E_PARSE, Code.E_SOURCE_MAPPING)) {
      return;
    }
    context.findings().add(Finding.of(Code.E_SCOPE_DECLARATION, Severity.ERROR, ProjectLoader.MANIFEST_FILE,
        List.of(), 1, "no source declares decision_hierarchy (SCOPE-48)"));
  }
}
