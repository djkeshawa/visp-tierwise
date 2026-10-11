package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.internal.model.EntityDef;
import io.github.visplabs.tierwise.core.internal.model.FactDef;
import io.github.visplabs.tierwise.core.internal.project.SourceDocument;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The entity and fact declaration stage (section 3, ENTITY and FACT areas).
 *
 * <p>The stage reads the {@code entity_hierarchy} of the first non-namespaced schema document that declares one
 * (ENTITY-2, ENTITY-3). The loader reports a second declaration as {@code E_DUPLICATE_DEFINITION}, and the stage
 * does not read it. The {@code facts} of every non-namespaced schema document are read (FACT-4). Each part is
 * published only when its own declarations are free of faults (FIND-35): {@link ModelParts#entities(Map)} with
 * {@link EntityIndex}, and {@link ModelParts#facts(Map)} with {@link FactIndex}, which also needs the entity part.
 */
public final class EntityFactStage implements Stage {

  @Override
  public String name() {
    return "entities-and-facts";
  }

  @Override
  public void run(CheckContext context) {
    List<SourceDocument> documents = context.sources().schemas().stream()
        .filter(document -> !document.isNamespaced())
        .toList();
    EntityReport entityReport = new EntityReport(context.findings());
    Optional<Set<String>> declared = Optional.empty();
    Map<String, EntityDef> definitions = Map.of();
    Optional<SourceDocument> entityDocument = documents.stream()
        .filter(document -> document.root().has("entity_hierarchy"))
        .findFirst();
    if (entityDocument.isEmpty()) {
      declared = Optional.of(Set.of());
    } else {
      PNode member = entityDocument.get().root().get("entity_hierarchy").orElseThrow();
      if (member instanceof PMap map) {
        List<EntityDeclaration.Entry> entries = new EntityDeclaration(entityReport).read(map);
        Set<String> names = new LinkedHashSet<>();
        entries.forEach(entry -> names.add(entry.name()));
        declared = Optional.of(names);
        definitions = new EntityGraph(entityReport).build(entries);
      } else {
        entityReport.markIncomplete();
      }
    }
    if (entityReport.clean()) {
      context.parts().entities(definitions);
      context.provide(EntityIndex.class, new EntityIndex(definitions));
    }

    EntityReport factReport = new EntityReport(context.findings());
    Map<String, FactDef> facts = new FactDeclaration(context.findings(), factReport, declared.orElse(null))
        .read(documents);
    if (factReport.clean()) {
      context.parts().facts(facts);
      context.entities().ifPresent(entities -> context.provide(FactIndex.class, new FactIndex(facts, entities)));
    }
  }
}
