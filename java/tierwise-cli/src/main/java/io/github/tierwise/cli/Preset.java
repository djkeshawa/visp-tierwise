package io.github.tierwise.cli;

/** The built-in presets that {@code tierwise init} can write a project for (MANIFEST-21). */
enum Preset {
  /** Kinds for settings and capabilities, naming checks, strict lint. */
  RECOMMENDED,
  /** No kinds and no naming checks; lint only for likely bugs. */
  MINIMAL,
  /** Kinds for numeric limits, on/off features and granted sets. */
  ENTITLEMENTS;

  /** Returns the name that the manifest uses after {@code tierwise:} and that the option accepts. */
  String id() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
