package io.github.tierwise.core.internal.project;

import io.github.tierwise.core.internal.yaml.PNode;
import io.github.tierwise.core.internal.yaml.PScalar;
import java.text.Normalizer;
import java.util.Optional;

/** Small readers of node shapes and names that the loader and the stages share. */
public final class Names {

  private Names() {
  }

  /**
   * Reads a node as a string. A scalar that the core schema reads as a non-string is not a string
   * (SRC-1, SRC-11).
   *
   * @param node a node, or {@code null}
   * @return the text, or empty when the node is absent or not a string scalar
   */
  public static Optional<String> text(PNode node) {
    if (node instanceof PScalar scalar && scalar.kind() == PScalar.Kind.STRING) {
      return Optional.of((String) scalar.value());
    }
    return Optional.empty();
  }

  /**
   * Normalises a name to NFC (NORM-1, SCOPE-8, SCOPE-35).
   *
   * @param name the name as written
   * @return the NFC form
   */
  public static String nfc(String name) {
    return Normalizer.normalize(name, Normalizer.Form.NFC);
  }
}
