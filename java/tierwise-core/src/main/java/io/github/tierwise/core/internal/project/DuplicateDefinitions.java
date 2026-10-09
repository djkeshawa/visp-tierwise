package io.github.tierwise.core.internal.project;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Reports a definition that is declared more than once across the sources, presets included (SRC-37 to
 * SRC-44, POL-5).
 *
 * <p>The documents come in definition order, so the later declaration is the one reported (FIND-32):
 * presets in {@code extends} order, then the manifest's inline content, then source files in code-point
 * order of their paths. A second declaration is a duplicate even when it is identical (SRC-41). Kinds and
 * naming patterns are replaced by a later layer, so only a repeat within the project's own sources is
 * reported (SRC-43, MANIFEST-32). Rules documents that name the same feature are reported at the
 * {@code feature} entry of every document after the first (FIND-34).
 */
final class DuplicateDefinitions {

  private static final List<String> SINGLE = List.of("decision_hierarchy", "entity_hierarchy");
  private static final UnaryOperator<String> EXACT = name -> name;

  private final Reporter reporter;

  DuplicateDefinitions(Reporter reporter) {
    this.reporter = reporter;
  }

  /**
   * Checks the schema documents.
   *
   * @param schemas every schema document in definition order
   */
  void checkSchemas(List<SourceDocument> schemas) {
    Set<String> declaredOnce = new HashSet<>();
    Map<String, Set<String>> names = new HashMap<>();
    Map<String, Set<String>> projectNames = new HashMap<>();
    for (SourceDocument document : schemas) {
      PMap root = document.root();
      if (document.isNamespaced()) {
        // A namespaced file declares only keys and features (NS-4). Any other declaration is an
        // E_NAMESPACE_VIOLATION and is not a declaration, so it cannot duplicate one in a shared file.
        names(root, "keys", EXACT, names);
        names(root, "features", EXACT, names);
        continue;
      }
      for (String member : SINGLE) {
        root.get(member).ifPresent(node -> {
          if (!declaredOnce.add(member)) {
            reporter.at(Code.E_DUPLICATE_DEFINITION, node, "the " + member + " is declared more than once (SRC-39)");
          }
        });
      }
      names(root, "keys", EXACT, names);
      names(root, "features", EXACT, names);
      names(root, "facts", Names::nfc, names);
      if (!document.isPreset()) {
        names(root, "kinds", Names::nfc, projectNames);
        names(root, "naming", EXACT, projectNames);
      }
    }
  }

  private void names(PMap root, String member, UnaryOperator<String> identity, Map<String, Set<String>> seen) {
    if (!(root.get(member).orElse(null) instanceof PMap declarations)) {
      return;
    }
    Set<String> known = seen.computeIfAbsent(member, name -> new HashSet<>());
    for (Map.Entry<String, PNode> entry : declarations.entries().entrySet()) {
      if (!known.add(identity.apply(entry.getKey()))) {
        reporter.at(Code.E_DUPLICATE_DEFINITION, entry.getValue(),
            entry.getKey() + " is declared more than once (SRC-40, SRC-43)");
      }
    }
  }

  /**
   * Checks the rules documents (SRC-44, POL-5).
   *
   * @param rules every rules document in definition order
   */
  void checkRules(List<SourceDocument> rules) {
    Set<String> features = new HashSet<>();
    for (SourceDocument document : rules) {
      PNode feature = document.root().get("feature").orElse(null);
      if (feature != null && Names.text(feature).isPresent() && !features.add(Names.text(feature).get())) {
        reporter.at(Code.E_DUPLICATE_DEFINITION, feature,
            "the feature " + Names.text(feature).get() + " is configured by more than one rules document (SRC-44)");
      }
    }
  }
}
