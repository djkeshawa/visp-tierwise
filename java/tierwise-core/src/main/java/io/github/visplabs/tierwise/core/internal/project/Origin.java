package io.github.visplabs.tierwise.core.internal.project;

/** Where the content of a {@link SourceDocument} was written. */
public enum Origin {
  /** A preset: built in, a relative path, or a URL (MANIFEST-19 to MANIFEST-30). */
  PRESET,
  /** Inline content of the manifest (MANIFEST-13). */
  INLINE,
  /** A source file of the project. */
  FILE
}
