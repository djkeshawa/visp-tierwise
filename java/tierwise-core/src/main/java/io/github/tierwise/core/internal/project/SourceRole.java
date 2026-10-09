package io.github.tierwise.core.internal.project;

/** The role of a source file, decided by the manifest entry that selects it (SRC-12). */
public enum SourceRole {
  /** A schema file, a preset, or inline schema content. */
  SCHEMA,
  /** A values file or inline values content. */
  VALUES,
  /** A rules file or one item of inline rules content. */
  RULES
}
