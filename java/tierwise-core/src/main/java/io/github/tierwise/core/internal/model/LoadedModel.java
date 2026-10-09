package io.github.tierwise.core.internal.model;

import io.github.tierwise.core.LoadedBundle;
import java.util.Objects;

/**
 * The only implementation of {@link LoadedBundle}: a verified bundle digest together with the
 * {@link Model} that the bundle reader built from its bytes.
 *
 * @param bundleVersion the {@code bundleVersion} of the loaded bundle
 * @param model the immutable model
 */
public record LoadedModel(String bundleVersion, Model model) implements LoadedBundle {

  /** Validates the components. */
  public LoadedModel {
    Objects.requireNonNull(bundleVersion, "bundleVersion");
    Objects.requireNonNull(model, "model");
  }
}
