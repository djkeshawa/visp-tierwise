package io.github.visplabs.tierwise.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of a validation and compilation (FIND-1 to FIND-8).
 *
 * <p>The bundle is present only when no finding has severity {@link Severity#ERROR} (FIND-7).
 */
public final class CompileResult {

  private final List<Finding> findings;
  private final byte[] bundle;

  /**
   * Creates a result.
   *
   * @param findings every finding, in the order of FIND-26
   * @param bundle the canonical bundle bytes, or {@code null} when validation failed
   * @throws IllegalArgumentException if a bundle is given together with an error finding
   */
  public CompileResult(List<Finding> findings, byte[] bundle) {
    this.findings = List.copyOf(findings);
    this.bundle = bundle == null ? null : bundle.clone();
    if (bundle != null && hasErrors()) {
      throw new IllegalArgumentException("a bundle is written only when no finding is an error (FIND-7)");
    }
  }

  /**
   * Returns every finding of every severity.
   *
   * @return an unmodifiable list
   */
  public List<Finding> findings() {
    return findings;
  }

  /**
   * Returns the canonical bundle bytes.
   *
   * @return a copy of the bytes, or empty when a finding has severity {@code error}
   */
  public Optional<byte[]> bundle() {
    return bundle == null ? Optional.empty() : Optional.of(bundle.clone());
  }

  /**
   * Tells whether the validation failed (FIND-5).
   *
   * @return {@code true} when at least one finding has severity {@code error}
   */
  public boolean hasErrors() {
    return findings.stream().anyMatch(finding -> finding.severity() == Severity.ERROR);
  }

  @Override
  public String toString() {
    return "CompileResult[findings=" + findings.size() + ", bundle=" + (bundle == null ? "none" : bundle.length + " bytes") + "]";
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof CompileResult that
        && findings.equals(that.findings)
        && java.util.Arrays.equals(bundle, that.bundle);
  }

  @Override
  public int hashCode() {
    return Objects.hash(findings, java.util.Arrays.hashCode(bundle));
  }
}
