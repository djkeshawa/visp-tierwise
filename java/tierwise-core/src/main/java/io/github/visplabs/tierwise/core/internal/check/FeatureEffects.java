package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.Returns;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PScalar;
import java.util.Optional;

/**
 * Checks an effect against a feature's {@code returns} (FEAT-9, FEAT-10, FEAT-13, FEAT-14, POL-27).
 *
 * <p>An effect that is not a valid value is one finding, {@code E_EFFECT_MISMATCH}, located at the effect
 * itself, whatever the part of an object effect that is wrong.
 */
final class FeatureEffects {

  private final Findings findings;

  /**
   * Creates the checker.
   *
   * @param findings the accumulator that receives the findings
   */
  FeatureEffects(Findings findings) {
    this.findings = findings;
  }

  /**
   * Reads an effect.
   *
   * @param returns the feature's effect form
   * @param node the effect node
   * @return the effect, or empty when it is not a valid value of {@code returns} (which is reported)
   */
  Optional<FeatureEffect> read(Returns returns, PNode node) {
    Optional<FeatureEffect> effect = returns.objectForm() ? object(returns, node) : enumeration(returns, node);
    if (effect.isEmpty()) {
      findings.error(Code.E_EFFECT_MISMATCH, node, "the effect is not a valid value of returns (FEAT-13, FEAT-14)");
    }
    return effect;
  }

  /**
   * Reads the {@code then} of a rule: a setting reference is an invalid setting, not a mismatch (POL-27).
   *
   * @param returns the feature's effect form
   * @param node the effect node
   * @return the effect, or empty when it is not valid (which is reported)
   */
  Optional<FeatureEffect> readThen(Returns returns, PNode node) {
    if (FeatureNodes.isReference(node)) {
      findings.error(Code.E_INVALID_SETTING, node, "a rule's then is not a setting reference (POL-27)");
      return Optional.empty();
    }
    return read(returns, node);
  }

  private static Optional<FeatureEffect> object(Returns returns, PNode node) {
    Optional<Object> value = new ValueValidator(new Findings()).validate(TypeDecl.object(returns.fields()), node);
    return value.map(FeatureEffect::new);
  }

  private static Optional<FeatureEffect> enumeration(Returns returns, PNode node) {
    if (!(node instanceof PScalar scalar)) {
      return Optional.empty();
    }
    if (scalar.kind() == PScalar.Kind.NULL) {
      return returns.nullable() ? Optional.of(new FeatureEffect(null)) : Optional.empty();
    }
    return FeatureNodes.name(node).filter(returns.enumValues()::contains).map(FeatureEffect::new);
  }
}
