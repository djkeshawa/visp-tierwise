package io.github.visplabs.tierwise.core.internal.project;

import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One parsed source document with the tags that the manifest gives it.
 *
 * <p>The document is a preset, a source file, a block of inline content or one item of inline rules.
 * Its {@link #root()} keeps the file, path and line of every node (FIND-17 to FIND-22); for inline
 * content the root is a node of the manifest and its path starts at the manifest root (SRC-36).
 *
 * @param file the file name as findings report it: the project-relative path, {@code tierwise.yaml}
 *     for inline content, or the preset name (FIND-17, FIND-18)
 * @param origin where the content was written
 * @param layer the layer of MANIFEST-31: the position of a preset among the presets that loaded, in
 *     {@code extends} order, and the number of loaded presets for every document of the project itself
 * @param role the role (SRC-12)
 * @param namespace the namespace of a namespaced schema file (NS-3), otherwise empty
 * @param mapping the mapping of a values document, otherwise empty
 * @param root the document root
 * @param groups the entries of a values document by scope and node; shape faults and service-sourced
 *     scopes are already reported and left out (SRC-31, SRC-33)
 */
public record SourceDocument(
    String file,
    Origin origin,
    int layer,
    SourceRole role,
    Optional<String> namespace,
    Optional<ValuesMapping> mapping,
    PMap root,
    List<ValueGroup> groups) {

  /** Validates the components and copies the groups. */
  public SourceDocument {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(origin, "origin");
    Objects.requireNonNull(role, "role");
    Objects.requireNonNull(namespace, "namespace");
    Objects.requireNonNull(mapping, "mapping");
    Objects.requireNonNull(root, "root");
    groups = List.copyOf(groups);
  }

  /**
   * Tells whether the document is a preset.
   *
   * @return {@code true} for a preset
   */
  public boolean isPreset() {
    return origin == Origin.PRESET;
  }

  /**
   * Tells whether the document is a namespaced schema file (NS-3).
   *
   * @return {@code true} when a {@code namespaces} entry lists the file
   */
  public boolean isNamespaced() {
    return namespace.isPresent();
  }

  /**
   * Returns a copy with other groups.
   *
   * @param newGroups the groups
   * @return the copy
   */
  SourceDocument withGroups(List<ValueGroup> newGroups) {
    return new SourceDocument(file, origin, layer, role, namespace, mapping, root, newGroups);
  }
}
