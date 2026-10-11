package io.github.visplabs.tierwise.core.internal.yaml;

import io.github.visplabs.tierwise.core.Finding;
import io.github.visplabs.tierwise.core.Severity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Collects the engine findings of one source. Two findings with the same finding key are one finding
 * (FIND-25), and the first one stays. Every finding of a source is an error (FIND-15).
 */
final class YamlFindings {

  private final String file;
  private final Map<Finding.Key, Finding> found = new LinkedHashMap<>();

  /**
   * Creates an empty collection for a source.
   *
   * @param file the source path relative to the project root (FIND-17)
   */
  YamlFindings(String file) {
    this.file = file;
  }

  /**
   * Adds a finding without a suggestion.
   *
   * @param code the engine error code
   * @param path the path of the node
   * @param line the 1-based line of the node
   * @param message a message that names the clause
   */
  void add(String code, List<Object> path, int line, String message) {
    add(code, path, line, message, Optional.empty());
  }

  /**
   * Adds a finding.
   *
   * @param code the engine error code
   * @param path the path of the node
   * @param line the 1-based line of the node
   * @param message a message that names the clause
   * @param suggestion a suggested replacement text, if any
   */
  void add(String code, List<Object> path, int line, String message, Optional<String> suggestion) {
    Finding finding = new Finding(code, Severity.ERROR, file, path, line, message, suggestion);
    found.putIfAbsent(finding.key(), finding);
  }

  /**
   * Returns the findings in the order they were found.
   *
   * @return an unmodifiable list
   */
  List<Finding> toList() {
    return List.copyOf(found.values());
  }

  /**
   * Quotes a text as a double-quoted YAML scalar, so that it reads as a string (SRC-11).
   *
   * @param text the text
   * @return the quoted text
   */
  static String quoted(String text) {
    StringBuilder out = new StringBuilder("\"");
    for (char c : text.toCharArray()) {
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
    return out.append('"').toString();
  }
}
