package io.github.visplabs.tierwise.core.internal.project;

import java.util.List;
import java.util.function.Predicate;

/**
 * Tells the YAML loader which mappings have keys that the specification NFC-normalises (FIND-20). A path
 * segment and a finding then hold the normalised key.
 *
 * <p>Fact and entity names are compared after NFC normalisation (FACT-3), and so are the names of kinds,
 * the keys of {@code fields} mappings (NORM-1, TYPE-68) and, in a values file, the scope names and node
 * IDs (SCOPE-8, SCOPE-35). Key names, feature names and naming pattern names are compared as written.
 */
final class NfcKeys {

  private NfcKeys() {
  }

  /**
   * The mappings of a schema file or of a preset.
   *
   * @return the predicate
   */
  static Predicate<List<Object>> schema() {
    return NfcKeys::isSchemaMapping;
  }

  /**
   * The mappings of the manifest: the inline {@code schema} content and the scope names of the inline
   * {@code values} content.
   *
   * @return the predicate
   */
  static Predicate<List<Object>> manifest() {
    return path -> path.equals(List.of("values"))
        || path.size() >= 1 && "schema".equals(path.get(0)) && isSchemaMapping(path.subList(1, path.size()));
  }

  /**
   * The mappings of a multi-node values file: the scope names and, below a non-root scope, the node IDs.
   *
   * @param rootLevel the root level name, or {@code null} when unknown
   * @return the predicate
   */
  static Predicate<List<Object>> multiNodeValues(String rootLevel) {
    return path -> path.isEmpty() || path.size() == 1 && rootLevel != null && !rootLevel.equals(path.get(0));
  }

  private static boolean isSchemaMapping(List<Object> path) {
    if (path.size() == 1) {
      return path.get(0).equals("facts") || path.get(0).equals("entity_hierarchy") || path.get(0).equals("kinds");
    }
    if (path.size() == 2) {
      return path.get(0).equals("decision_hierarchy") && path.get(1).equals("scopes");
    }
    return path.size() >= 3 && path.get(path.size() - 1).equals("fields")
        && (path.get(0).equals("keys") || path.get(0).equals("features"));
  }
}
