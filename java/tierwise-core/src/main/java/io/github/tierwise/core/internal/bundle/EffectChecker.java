package io.github.tierwise.core.internal.bundle;

import io.github.tierwise.core.internal.model.Returns;
import io.github.tierwise.core.internal.model.TypeDecl;

/**
 * Checks an effect against a feature's {@code returns} (FEAT-9, FEAT-11, FEAT-13, BUNDLE-24). An effect is one
 * of the enum strings, or {@code null} where the enum form is nullable, or a complete object of the object form.
 */
final class EffectChecker {

  private EffectChecker() {
  }

  /**
   * Requires that an effect is valid for {@code returns}.
   *
   * @param returns the feature's effect form
   * @param effect the effect; {@code null} is legal only for a nullable enum (BUNDLE-24, FEAT-9)
   * @param at the location of the effect
   */
  static void check(Returns returns, Object effect, String at) {
    if (effect == null) {
      if (returns.objectForm() || !returns.nullable()) {
        throw BundleFault.integrity(at + " is null, but the feature's returns is not nullable (BUNDLE-24, FEAT-9)");
      }
      return;
    }
    if (returns.objectForm()) {
      ValueChecker.check(TypeDecl.object(returns.fields()), effect, ValueChecker.Mode.COMPLETE, at);
      return;
    }
    if (!(effect instanceof String name) || !returns.enumValues().contains(name)) {
      throw BundleFault.integrity(at + " is not an effect of the feature's returns (FEAT-13)");
    }
  }
}
