package io.github.tierwise.core.internal.project;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;

/**
 * The built-in presets {@code tierwise:recommended}, {@code tierwise:minimal} and
 * {@code tierwise:entitlements} (MANIFEST-21). The build copies the files of the repository's
 * {@code presets/} directory into the classpath next to this class.
 */
final class BuiltInPresets {

  /** The built-in names (MANIFEST-21). */
  static final Set<String> NAMES = Set.of("recommended", "minimal", "entitlements");

  private BuiltInPresets() {
  }

  /**
   * Reads a built-in preset.
   *
   * @param name the name after {@code tierwise:}
   * @return the bytes of the preset file, or empty when the name is not built in (MANIFEST-22)
   */
  static Optional<byte[]> read(String name) {
    if (!NAMES.contains(name)) {
      return Optional.empty();
    }
    try (InputStream in = BuiltInPresets.class.getResourceAsStream("presets/" + name + ".yaml")) {
      return in == null ? Optional.empty() : Optional.of(in.readAllBytes());
    } catch (IOException e) {
      return Optional.empty();
    }
  }
}
