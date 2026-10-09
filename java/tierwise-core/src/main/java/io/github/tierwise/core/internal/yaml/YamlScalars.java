package io.github.tierwise.core.internal.yaml;

import java.util.List;
import org.snakeyaml.engine.v2.events.ScalarEvent;

/**
 * Builds the scalar nodes of a source from scalar events.
 *
 * <p>A plain scalar takes its type from the core schema, and every other scalar is a string (SRC-1).
 * An integral float, such as {@code 1.0} or {@code 1e3}, and a non-finite float, such as {@code .inf} or
 * {@code .nan}, cannot be held as plain data (the rule of the reference loader, see VEC-35). Such a
 * float is reported and kept as a {@link PScalar.Kind#FLOAT} node, so that the type check of the
 * declaration reports the same finding at the same key (FIND-25), and no entry of a required field
 * appears missing. Every other float is kept without a finding.
 */
final class YamlScalars {

  private final String file;
  private final YamlCodes codes;
  private final YamlFindings findings;

  /**
   * Creates a builder for one source.
   *
   * @param file the source path relative to the project root
   * @param codes the codes for floats
   * @param findings the findings of the source
   */
  YamlScalars(String file, YamlCodes codes, YamlFindings findings) {
    this.file = file;
    this.codes = codes;
    this.findings = findings;
  }

  /**
   * Builds the node of a scalar.
   *
   * @param scalar the scalar event
   * @param path the path of the node
   * @param line the 1-based line of the node
   * @return the node, which is always present
   */
  PScalar build(ScalarEvent scalar, List<Object> path, int line) {
    String text = scalar.getValue();
    if (!scalar.isPlain()) {
      return new PScalar(file, path, line, PScalar.Kind.STRING, text, text);
    }
    CoreSchema.Typed typed = CoreSchema.resolve(text);
    if (typed.kind() == PScalar.Kind.FLOAT && !isPlainData((Double) typed.value())) {
      findings.add(codes.type(), path, line, "the float " + text
          + " cannot be held as plain data (an integral or non-finite float; see TYPE-17 and VEC-35)");
    }
    return new PScalar(file, path, line, typed.kind(), typed.value(), text);
  }

  private static boolean isPlainData(double value) {
    return Double.isFinite(value) && Math.rint(value) != value;
  }
}
