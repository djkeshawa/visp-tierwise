package io.github.tierwise.core.internal.project;

import io.github.tierwise.core.Code;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks the top-level keys of schema files, inline schema content and presets: SRC-14, SRC-16, SRC-45,
 * MANIFEST-36, NS-4, NS-5, NS-7 and NS-12. Whatever lies below a top-level member belongs to the section
 * that owns the member.
 */
final class SchemaChecks {

  /** The top-level keys of a schema file (SRC-14). */
  static final Set<String> SCHEMA_KEYS = Set.of(
      "decision_hierarchy", "entity_hierarchy", "facts", "kinds", "naming", "keys", "features", "namespace");

  /** The top-level keys of a preset (MANIFEST-36, NS-12): those of a schema file, without {@code namespace}, and {@code lint}. */
  private static final Set<String> PRESET_KEYS = Set.of(
      "decision_hierarchy", "entity_hierarchy", "facts", "kinds", "naming", "keys", "features", "lint");

  /** The members that must be mappings (SRC-45). {@code decision_hierarchy} belongs to section 2. */
  private static final Set<String> MAPPING_MEMBERS =
      Set.of("entity_hierarchy", "facts", "kinds", "naming", "keys", "features");

  private static final Set<String> NAMESPACED_KEYS = Set.of("keys", "features", "namespace");

  private final Reporter reporter;

  SchemaChecks(Reporter reporter) {
    this.reporter = reporter;
  }

  /**
   * Checks a preset (MANIFEST-36, NS-12). A fault is {@code E_PRESET}.
   *
   * @param root the preset root
   */
  void checkPreset(PMap root) {
    for (Map.Entry<String, PNode> entry : root.entries().entrySet()) {
      if (!PRESET_KEYS.contains(entry.getKey())) {
        reporter.at(Code.E_PRESET, entry.getValue(), "a preset has no top-level key " + entry.getKey()
            + (entry.getKey().equals("extends") ? " (MANIFEST-35)" : " (MANIFEST-36, NS-12, MANIFEST-6)"));
      }
    }
    checkMemberShapes(root, Code.E_PRESET, MAPPING_MEMBERS);
  }

  /**
   * Checks a shared schema file or inline schema content (SRC-14, SRC-16, NS-7, SRC-45).
   *
   * @param root the file root or the inline schema mapping
   */
  void checkShared(PMap root) {
    for (Map.Entry<String, PNode> entry : root.entries().entrySet()) {
      String key = entry.getKey();
      if (key.equals("namespace")) {
        reporter.at(Code.E_NAMESPACE_VIOLATION, entry.getValue(), "a shared file has no namespace key (NS-7)");
      } else if (!SCHEMA_KEYS.contains(key)) {
        reporter.at(Code.E_INVALID_DECLARATION, entry.getValue(),
            "a schema file has no top-level key " + key + " (SRC-14, SRC-16)");
      }
    }
    checkMemberShapes(root, Code.E_INVALID_DECLARATION, MAPPING_MEMBERS);
  }

  /**
   * Checks a namespaced schema file (NS-4, NS-5, NS-7, SRC-45).
   *
   * @param root the file root
   * @param namespace the name under which {@code namespaces} lists the file
   */
  void checkNamespaced(PMap root, String namespace) {
    for (Map.Entry<String, PNode> entry : root.entries().entrySet()) {
      if (!NAMESPACED_KEYS.contains(entry.getKey())) {
        reporter.at(Code.E_NAMESPACE_VIOLATION, entry.getValue(),
            "a namespaced file has no top-level key " + entry.getKey() + " (NS-4)");
      }
    }
    Optional<PNode> declared = root.get("namespace");
    if (declared.isEmpty()) {
      reporter.at(Code.E_NAMESPACE_VIOLATION, root, "a namespaced file has the namespace key (NS-7)");
    } else if (!Names.text(declared.get()).filter(namespace::equals).isPresent()) {
      reporter.at(Code.E_NAMESPACE_VIOLATION, declared.get(),
          "the namespace key must equal " + namespace + ", the name that namespaces lists (NS-7)");
    }
    checkMemberShapes(root, Code.E_INVALID_DECLARATION, Set.of("keys", "features"));
    checkPrefix(root, "keys", namespace);
    checkPrefix(root, "features", namespace);
  }

  private void checkPrefix(PMap root, String member, String namespace) {
    if (root.get(member).orElse(null) instanceof PMap names) {
      for (Map.Entry<String, PNode> entry : names.entries().entrySet()) {
        if (!entry.getKey().startsWith(namespace + ".")) {
          reporter.at(Code.E_NAMESPACE_VIOLATION, entry.getValue(),
              entry.getKey() + " is not in the namespace " + namespace + " (NS-5, NS-10)");
        }
      }
    }
  }

  private void checkMemberShapes(PMap root, String code, Set<String> mappingMembers) {
    for (String member : mappingMembers) {
      Optional<PNode> node = root.get(member);
      if (node.isPresent() && !(node.get() instanceof PMap)) {
        reporter.at(code, node.get(), member + " is not a mapping (SRC-45)");
      }
    }
  }
}
