package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.internal.model.Returns;
import io.github.visplabs.tierwise.core.internal.model.TypeDecl;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PScalar;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Checks the {@code expect} of an example against {@code returns} (POL-40, FEAT-13, FEAT-14). A value that is
 * not an effect of the feature is {@code E_EFFECT_MISMATCH}. {@code null} is an effect only for a nullable
 * enum (FEAT-9).
 */
final class ExampleExpect {

  /**
   * The checked expectation.
   *
   * @param valid {@code false} when the value is not an effect
   * @param value the effect, plain; {@code null} is the null effect of a nullable enum
   */
  record Parsed(boolean valid, Object value) {
  }

  private static final ValueRules RULES = ValueRules.DEFAULT.withOwnerCode(Code.E_EFFECT_MISMATCH);

  private ExampleExpect() {
  }

  /**
   * Checks an expectation.
   *
   * @param node the {@code expect} node
   * @param returns the feature's {@code returns}
   * @param validator the validator that reports the faults
   * @return the checked expectation
   */
  static Parsed parse(PNode node, Returns returns, ValueValidator validator) {
    if (node instanceof PScalar scalar && scalar.kind() == PScalar.Kind.NULL && returns.nullable()) {
      return new Parsed(true, null);
    }
    TypeDecl declaration = returns.objectForm()
        ? new TypeDecl("object", Optional.empty(), Optional.empty(), Optional.empty(), List.of(), List.of(),
            Optional.empty(), returns.fields(), false)
        : new TypeDecl("enum", Optional.empty(), Optional.empty(), Optional.empty(), returns.enumValues(), List.of(),
            Optional.empty(), Map.of(), false);
    Optional<Object> value = validator.validate(declaration, node, RULES);
    return value.map(plain -> new Parsed(true, plain)).orElseGet(() -> new Parsed(false, null));
  }
}
