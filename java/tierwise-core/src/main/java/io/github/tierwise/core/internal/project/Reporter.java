package io.github.tierwise.core.internal.project;

import io.github.tierwise.core.Finding;
import io.github.tierwise.core.Severity;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Collects the findings of the loader. Findings with the same finding key are one finding, and the first
 * stays (FIND-25). Every engine finding has severity {@code error} (FIND-15).
 */
final class Reporter {

  private final Map<Finding.Key, Finding> found = new LinkedHashMap<>();
  private final Map<String, String> presetOrigins = new HashMap<>();

  /**
   * Records which {@code extends} item brought a preset in. The message of every finding located in that
   * preset then names the item (FIND-18).
   *
   * @param presetName the name of the preset as findings report it
   * @param position the zero-based position of the item in {@code extends}
   */
  void notePresetOrigin(String presetName, int position) {
    presetOrigins.put(presetName, "extends item " + position);
  }

  /**
   * Reports a finding at a node.
   *
   * @param code the engine error code
   * @param node the node whose file, path and line locate the finding (FIND-19 to FIND-22)
   * @param message a message that names the clause
   */
  void at(String code, PNode node, String message) {
    at(code, node, message, Optional.empty());
  }

  /**
   * Reports a finding at a node, with a suggestion.
   *
   * @param code the engine error code
   * @param node the node that locates the finding
   * @param message a message that names the clause
   * @param suggestion the exact text of a close declared name, if any
   */
  void at(String code, PNode node, String message, Optional<String> suggestion) {
    add(new Finding(code, Severity.ERROR, node.file(), node.path(), node.line(), message, suggestion));
  }

  /**
   * Reports a finding at the root of a file that has no node to name (FIND-23).
   *
   * @param code the engine error code
   * @param file the file name
   * @param message a message that names the clause
   */
  void atFile(String code, String file, String message) {
    add(Finding.of(code, Severity.ERROR, file, List.of(), 1, message));
  }

  /**
   * Adds findings that were built elsewhere, such as the findings of the YAML loader.
   *
   * @param findings the findings
   */
  void addAll(List<Finding> findings) {
    findings.forEach(this::add);
  }

  private void add(Finding finding) {
    String origin = presetOrigins.get(finding.file());
    Finding located = origin == null ? finding : new Finding(finding.code(), finding.severity(), finding.file(),
        finding.path(), finding.line(), finding.message() + " [preset brought in by " + origin + "]", finding.suggestion());
    found.putIfAbsent(located.key(), located);
  }

  /**
   * Returns the findings in the order they were found.
   *
   * @return a new list
   */
  List<Finding> findings() {
    return new ArrayList<>(found.values());
  }
}
