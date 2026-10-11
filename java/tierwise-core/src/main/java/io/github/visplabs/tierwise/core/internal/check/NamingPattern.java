package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.Objects;
import java.util.Optional;

/**
 * A naming pattern declared under the top-level {@code naming} mapping of a shared schema file or a
 * preset (LINT-14).
 *
 * @param name the pattern name, which also names the lint rule {@code naming/<name>} (LINT-12)
 * @param category one of {@code key}, {@code enum_value}, {@code node_id} or {@code feature}; empty when
 *     the declaration did not give a valid category (LINT-15)
 * @param regex the compiled pattern (LINT-17, LINT-18); empty when the declaration did not give a valid
 *     pattern
 * @param description the {@code description} member, when it is a string
 * @param entry the declaration node, which locates findings about the declaration
 * @param layer the layer of MANIFEST-31 that declares the pattern: the position of its preset, or the
 *     number of presets for the project's own sources
 * @param preset {@code true} when a preset declares the pattern
 */
public record NamingPattern(String name, Optional<String> category, Optional<NamingRegex> regex,
    Optional<String> description, PNode entry, int layer, boolean preset) {

  /** Validates the components. */
  public NamingPattern {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(regex, "regex");
    Objects.requireNonNull(description, "description");
    Objects.requireNonNull(entry, "entry");
  }

  /**
   * Tells whether the declaration is valid, so that the pattern can be used to check names.
   *
   * @return {@code true} when category and pattern are valid
   */
  public boolean usable() {
    return category.isPresent() && regex.isPresent();
  }
}
