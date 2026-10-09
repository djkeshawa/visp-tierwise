package io.github.tierwise.cli.codegen;

import io.github.tierwise.core.Finding;
import java.util.List;
import java.util.Objects;

/**
 * Raised when code cannot be generated from a project: the project has validation errors, or two
 * declared names would map to one generated identifier. No file is written when this is raised.
 */
public final class CodegenException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final transient List<Finding> findings;

  /**
   * Creates the exception.
   *
   * @param message a human-readable message
   * @param findings the findings of the compiler that caused the failure; empty when none caused it
   */
  public CodegenException(String message, List<Finding> findings) {
    super(message);
    this.findings = List.copyOf(Objects.requireNonNull(findings, "findings"));
  }

  /**
   * Returns the compiler findings that caused the failure.
   *
   * @return an unmodifiable list, empty when the failure came from the naming rules
   */
  public List<Finding> findings() {
    return findings;
  }
}
