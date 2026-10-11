package io.github.visplabs.tierwise.core;

import java.util.Objects;
import java.util.Optional;

/**
 * A naming pattern that a project declares or inherits from a preset (LINT-14), as {@link
 * Tierwise#namingPatterns(java.nio.file.Path, CompileOptions)} returns it. Each pattern gives the lint rule
 * {@code naming/<name>} (LINT-12), and its explanation is its {@code description} or, without one, the
 * explanation of the naming family followed by the pattern (FIND-30).
 *
 * <p>The texts are those of the declaration in effect, as written. They are not checked here: a
 * compilation reports a faulty declaration.
 *
 * @param name the pattern name, the {@code <name>} of the rule {@code naming/<name>}
 * @param category the {@code category} member (LINT-15)
 * @param pattern the {@code pattern} member (LINT-17)
 * @param description the {@code description} member, when the declaration has one
 */
public record NamingPattern(String name, String category, String pattern, Optional<String> description) {

  /**
   * Checks that no component is {@code null}.
   *
   * @throws NullPointerException if a component is {@code null}
   */
  public NamingPattern {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(pattern, "pattern");
    Objects.requireNonNull(description, "description");
  }
}
