package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.internal.yaml.PNode;
import java.util.Optional;

/**
 * Reports the findings of one section of the entity and fact declarations, and counts them, so that the stage
 * knows whether that section was built without a fault (FIND-35).
 *
 * <p>The entity declarations and the fact declarations are counted separately: a fault in one leaves the
 * other part buildable.
 */
final class EntityReport {

  private final Findings findings;
  private int faults;
  private boolean incomplete;

  /**
   * Creates a report over the shared accumulator.
   *
   * @param findings the accumulator
   */
  EntityReport(Findings findings) {
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
   * Reports an engine error located at a node, with a suggestion (FIND-27, FIND-28).
   *
   * @param code the engine error code
   * @param node the node that locates the finding
   * @param message a message that names the clause
   * @param suggestion the exact text of a close declared name, if any
   */
  void at(String code, PNode node, String message, Optional<String> suggestion) {
    findings.error(code, node, message, suggestion);
    faults++;
  }

  /**
   * Records that a section could not be read at all, because its source has the wrong shape. The loader
   * reports that shape fault, so this stage reports nothing more.
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
   * Tells whether the section may be built.
   *
   * @return {@code true} when nothing was reported and the section was read
   */
  boolean clean() {
    return faults == 0 && !incomplete;
  }
}
