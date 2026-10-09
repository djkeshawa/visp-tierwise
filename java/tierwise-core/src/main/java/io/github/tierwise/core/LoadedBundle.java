package io.github.tierwise.core;

import io.github.tierwise.core.internal.model.LoadedModel;

/**
 * A bundle that passed every load check (EVAL-14). It is immutable, so it is safe to share between
 * evaluators and threads (EVAL-46).
 *
 * <p>Instances are created only by {@link Tierwise#loadBundle(byte[])}.
 */
public sealed interface LoadedBundle permits LoadedModel {

  /**
   * Returns the digest that names the bundle.
   *
   * @return {@code sha256:} followed by 64 lower-case hexadecimal digits (BUNDLE-5)
   */
  String bundleVersion();
}
