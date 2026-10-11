package io.github.visplabs.tierwise.core.internal.project;

import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PScalar;
import io.github.visplabs.tierwise.core.internal.yaml.PSeq;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A read-only look at the declared decision hierarchy, for the checks of section 1 that need to know
 * which scope is the root level, which scopes are declared, and which levels are service-sourced
 * (SRC-27, SRC-28, SRC-31, SRC-33).
 *
 * <p>The peek validates nothing. It reads only what is well-formed enough to read: a {@code levels} list
 * whose entries are strings, an {@code axes} list, and the {@code values} member of a scope entry. The
 * hierarchy section ({@code SCOPE} area) reports every fault of the declaration itself. When no hierarchy
 * can be read, the checks that need it are skipped.
 *
 * @param rootLevel the first level, NFC-normalised, when it is a string
 * @param declared every level and axis name, NFC-normalised
 * @param serviceLevels the levels that declare {@code values: service}
 */
record HierarchyPeek(Optional<String> rootLevel, Set<String> declared, Set<String> serviceLevels) {

  /** The peek of a project that declares no readable hierarchy. */
  static HierarchyPeek unknown() {
    return new HierarchyPeek(Optional.empty(), Set.of(), Set.of());
  }

  /**
   * Reads the first {@code decision_hierarchy} in definition order.
   *
   * @param schemas the schema documents, presets first
   * @return the peek
   */
  static HierarchyPeek of(List<SourceDocument> schemas) {
    for (SourceDocument document : schemas) {
      Optional<PNode> hierarchy = document.root().get("decision_hierarchy");
      if (hierarchy.isPresent() && hierarchy.get() instanceof PMap map) {
        return read(map);
      }
    }
    return unknown();
  }

  private static HierarchyPeek read(PMap hierarchy) {
    List<String> levels = names(hierarchy.get("levels").orElse(null));
    Set<String> declared = new LinkedHashSet<>(levels);
    declared.addAll(names(hierarchy.get("axes").orElse(null)));
    Set<String> service = new LinkedHashSet<>();
    if (hierarchy.get("scopes").orElse(null) instanceof PMap scopes) {
      for (String level : levels) {
        scopes.entries().forEach((name, entry) -> {
          if (Names.nfc(name).equals(level) && isService(entry)) {
            service.add(level);
          }
        });
      }
    }
    Optional<String> root = levels.isEmpty() ? Optional.empty() : Optional.of(levels.get(0));
    if (hierarchy.get("levels").orElse(null) instanceof PSeq list && !list.items().isEmpty()
        && Names.text(list.items().get(0)).isEmpty()) {
      root = Optional.empty();
    }
    return new HierarchyPeek(root, declared, service);
  }

  private static boolean isService(PNode scope) {
    return scope instanceof PMap map && map.get("values").flatMap(v -> Names.text(v)).filter("service"::equals).isPresent();
  }

  private static List<String> names(PNode node) {
    List<String> names = new ArrayList<>();
    if (node instanceof PSeq list) {
      for (PNode item : list.items()) {
        if (item instanceof PScalar scalar && scalar.kind() == PScalar.Kind.STRING) {
          names.add(Names.nfc((String) scalar.value()));
        }
      }
    }
    return names;
  }
}
