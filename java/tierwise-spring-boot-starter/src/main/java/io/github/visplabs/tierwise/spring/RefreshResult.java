package io.github.visplabs.tierwise.spring;

/** The outcome of one {@link TierwiseEvaluators#refresh()}. */
public enum RefreshResult {
  /** A different bundle was loaded and is now in use. */
  SWAPPED,
  /** The source confirmed that the bundle in use is current. */
  UNCHANGED,
  /** The refresh failed; the bundle in use stays in use. */
  FAILED
}
