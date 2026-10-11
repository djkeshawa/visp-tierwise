package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.internal.project.Names;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PScalar;
import java.util.Optional;

/** Small readers of node shapes that the feature, policy, rule, condition and binding checks share. */
final class FeatureNodes {

  private FeatureNodes() {
  }

  /**
   * Reads a node as a string name: a string scalar without an unpaired surrogate (NORM-8), NFC-normalised
   * (NORM-1). A scalar that the core schema reads as another kind is not a name and is never converted
   * (SRC-11).
   *
   * @param node a node
   * @return the normalised text, or empty when the node is not a usable string
   */
  static Optional<String> name(PNode node) {
    return Names.text(node).filter(text -> !ValueText.hasUnpairedSurrogate(text)).map(Names::nfc);
  }

  /**
   * Tells whether a node is the core-schema null.
   *
   * @param node a node
   * @return {@code true} for a null scalar
   */
  static boolean isNull(PNode node) {
    return node instanceof PScalar scalar && scalar.kind() == PScalar.Kind.NULL;
  }

  /**
   * Tells whether a node is a string whose first code point is {@code $}, which RULE-4 reads as a setting
   * reference.
   *
   * @param node a node
   * @return {@code true} for a setting reference
   */
  static boolean isReference(PNode node) {
    return Names.text(node).filter(text -> text.startsWith("$")).isPresent();
  }

  /**
   * Returns the setting name of a reference node: the text after the {@code $}, NFC-normalised (POL-20).
   *
   * @param node a node for which {@link #isReference(PNode)} holds
   * @return the setting name, which may be empty
   */
  static String referenceName(PNode node) {
    return Names.nfc(Names.text(node).orElseThrow().substring(1));
  }
}
