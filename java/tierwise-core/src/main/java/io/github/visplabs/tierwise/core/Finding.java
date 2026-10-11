package io.github.visplabs.tierwise.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A diagnostic with a stable code and a location (FIND-10, FIND-11).
 *
 * @param code an engine error code or a lint rule identifier ({@link Code})
 * @param severity the severity; always {@link Severity#ERROR} for an engine code (FIND-15)
 * @param file the source path relative to the project root with {@code /} separators, or the name of a
 *     preset (FIND-17, FIND-18)
 * @param path the path of the node: {@code String} mapping keys and {@code Integer} zero-based sequence
 *     positions, empty for the document root (FIND-19, FIND-20)
 * @param line the 1-based line on which the node begins (FIND-21, FIND-22)
 * @param message a human-readable message; not compared by conformance vectors
 * @param suggestion the exact text of a close declared name (FIND-27, FIND-28)
 */
public record Finding(
    String code,
    Severity severity,
    String file,
    List<Object> path,
    int line,
    String message,
    Optional<String> suggestion) {

  /**
   * The identity of a finding for deduplication and conformance (FIND-24): everything except severity,
   * message and suggestion.
   *
   * @param code the code
   * @param file the file
   * @param path the path
   * @param line the line
   */
  public record Key(String code, String file, List<Object> path, int line) {

    /** Copies the path into an unmodifiable list. */
    public Key {
      Objects.requireNonNull(code, "code");
      Objects.requireNonNull(file, "file");
      path = List.copyOf(path);
    }
  }

  /**
   * Validates the components and copies the path.
   *
   * @throws IllegalArgumentException if a path segment is neither a {@code String} nor an
   *     {@code Integer}, or the line is below 1
   */
  public Finding {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(suggestion, "suggestion");
    path = List.copyOf(path);
    for (Object segment : path) {
      if (!(segment instanceof String) && !(segment instanceof Integer)) {
        throw new IllegalArgumentException("a path segment is a String or an Integer: " + segment);
      }
    }
    if (line < 1) {
      throw new IllegalArgumentException("line is 1-based: " + line);
    }
  }

  /**
   * Creates a finding without a suggestion.
   *
   * @param code the code
   * @param severity the severity
   * @param file the file
   * @param path the path
   * @param line the line
   * @param message the message
   * @return the finding
   */
  public static Finding of(
      String code, Severity severity, String file, List<Object> path, int line, String message) {
    return new Finding(code, severity, file, path, line, message, Optional.empty());
  }

  /**
   * Returns the finding key of this finding.
   *
   * @return the tuple (code, file, path, line)
   */
  public Key key() {
    return new Key(code, file, path, line);
  }
}
