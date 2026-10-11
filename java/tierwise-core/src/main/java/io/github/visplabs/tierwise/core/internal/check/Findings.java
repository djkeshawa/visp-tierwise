package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.Finding;
import io.github.visplabs.tierwise.core.Severity;
import io.github.visplabs.tierwise.core.internal.project.CodePointOrder;
import io.github.visplabs.tierwise.core.internal.project.PathOrder;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Accumulates the findings of one validation.
 *
 * <p>Two findings with the same finding key (code, file, path, line) are one finding, and the first
 * stays (FIND-25). {@link #sorted()} lists the findings in the order of FIND-26: by file in code-point
 * order, then line, then path segment by segment, then code. The order does not depend on the order in
 * which the stages added the findings, so the output is deterministic (FIND-4).
 */
public final class Findings {

  private static final Comparator<Finding> ORDER = Comparator
      .comparing(Finding::file, CodePointOrder.ORDER)
      .thenComparingInt(Finding::line)
      .thenComparing(Finding::path, PathOrder.ORDER)
      .thenComparing(Finding::code, CodePointOrder.ORDER);

  private final Map<Finding.Key, Finding> found = new LinkedHashMap<>();

  /**
   * Adds a finding.
   *
   * @param finding the finding
   */
  public void add(Finding finding) {
    found.putIfAbsent(finding.key(), finding);
  }

  /**
   * Adds findings.
   *
   * @param findings the findings
   */
  public void addAll(Collection<Finding> findings) {
    findings.forEach(this::add);
  }

  /**
   * Reports an engine error located at a node (FIND-12, FIND-15).
   *
   * @param code the engine error code
   * @param node the node whose file, path and line locate the finding (FIND-19 to FIND-23)
   * @param message a message that names the clause
   */
  public void error(String code, PNode node, String message) {
    error(code, node, message, Optional.empty());
  }

  /**
   * Reports an engine error with a suggestion (FIND-27, FIND-28).
   *
   * @param code the engine error code
   * @param node the node that locates the finding
   * @param message a message that names the clause
   * @param suggestion the exact text of a close declared name, if any
   */
  public void error(String code, PNode node, String message, Optional<String> suggestion) {
    add(new Finding(code, Severity.ERROR, node.file(), node.path(), node.line(), message, suggestion));
  }

  /**
   * Reports a lint finding at its resolved severity (FIND-13, FIND-16).
   *
   * @param ruleId the lint rule identifier
   * @param severity the resolved severity of the rule
   * @param node the node that locates the finding
   * @param message a message
   */
  public void lint(String ruleId, Severity severity, PNode node, String message) {
    add(new Finding(ruleId, severity, node.file(), node.path(), node.line(), message, Optional.empty()));
  }

  /**
   * Tells whether any finding has severity {@code error} (FIND-5).
   *
   * @return {@code true} when the validation fails
   */
  public boolean hasErrors() {
    return found.values().stream().anyMatch(finding -> finding.severity() == Severity.ERROR);
  }

  /**
   * Tells whether any engine finding exists. Lint rules and examples run only when none does
   * (LINT-11, POL-47).
   *
   * @return {@code true} when a finding carries an engine error code
   */
  public boolean hasEngineFindings() {
    return found.values().stream().anyMatch(finding -> Code.engine().contains(finding.code()));
  }

  /**
   * Tells whether a finding with one of the codes exists.
   *
   * @param codes the codes
   * @return {@code true} when some finding has one of them
   */
  public boolean hasCode(String... codes) {
    for (Finding finding : found.values()) {
      for (String code : codes) {
        if (finding.code().equals(code)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Returns the number of distinct findings.
   *
   * @return the count
   */
  public int size() {
    return found.size();
  }

  /**
   * Returns the findings in the order of FIND-26.
   *
   * @return a new list
   */
  public List<Finding> sorted() {
    List<Finding> list = new ArrayList<>(found.values());
    list.sort(ORDER);
    return list;
  }
}
