package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.Finding;
import io.github.tierwise.core.internal.yaml.PNode;

/**
 * Reports the findings of the hierarchy declaration and counts them, so that the stage knows whether the
 * declaration was built without a fault (FIND-35).
 *
 * <p>A declaration is built only when it reports nothing and when every file that it names could be read.
 * {@link #markIncomplete()} records a part that could not be read because the project directory is not
 * known. Such a part is left unbuilt without a finding, because the cause is not in the project text.
 */
final class ScopeReport {

  private final Findings findings;
  private int faults;
  private boolean incomplete;

  /**
   * Creates a report over the shared accumulator.
   *
   * @param findings the accumulator
   */
  ScopeReport(Findings findings) {
    this.findings = findings;
  }

  /**
   * Reports an engine error located at a node.
   *
   * @param code the engine error code
   * @param node the node that locates the finding (FIND-19 to FIND-23)
   * @param message a message that names the clause
   */
  void at(String code, PNode node, String message) {
    findings.error(code, node, message);
    faults++;
  }

  /**
   * Adds a finding that a file reader built (FIND-23, FIND-24).
   *
   * @param finding the finding, of severity {@code error}
   */
  void add(Finding finding) {
    findings.add(finding);
    faults++;
  }

  /**
   * Records a part of the declaration that could not be read because the project directory is unknown.
   */
  void markIncomplete() {
    incomplete = true;
  }

  /**
   * Returns the number of faults reported so far.
   *
   * @return the count
   */
  int faults() {
    return faults;
  }

  /**
   * Tells whether the declaration may be built: no fault and nothing left unread.
   *
   * @return {@code true} when the declaration is complete and valid
   */
  boolean clean() {
    return faults == 0 && !incomplete;
  }
}
