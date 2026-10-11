package io.github.visplabs.tierwise.core;

import java.util.Optional;

/** The severity of a {@link Finding} (FIND-15, FIND-16). */
public enum Severity {
  /** The validation fails. Every engine finding has this severity. */
  ERROR("error"),
  /** A lint finding that does not fail the validation. */
  WARN("warn"),
  /** An informational lint finding. */
  INFO("info");

  private final String id;

  Severity(String id) {
    this.id = id;
  }

  /**
   * Returns the lower-case spelling used in manifests, presets and reports.
   *
   * @return {@code error}, {@code warn} or {@code info}
   */
  public String id() {
    return id;
  }

  /**
   * Parses the lower-case spelling.
   *
   * @param text a spelling
   * @return the severity, or empty when the text is not one of {@code error}, {@code warn}, {@code info}
   */
  public static Optional<Severity> parse(String text) {
    for (Severity severity : values()) {
      if (severity.id.equals(text)) {
        return Optional.of(severity);
      }
    }
    return Optional.empty();
  }
}
