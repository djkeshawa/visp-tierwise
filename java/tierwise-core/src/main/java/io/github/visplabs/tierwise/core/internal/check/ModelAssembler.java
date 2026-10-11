package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.internal.model.Model;
import java.util.Optional;

/** Assembles the {@link Model} from the parts that the stages built. */
final class ModelAssembler {

  private ModelAssembler() {
  }

  /**
   * Assembles the model.
   *
   * @param parts the parts
   * @return the model, or empty when a part was not built
   */
  static Optional<Model> assemble(ModelParts parts) {
    if (!parts.complete()) {
      return Optional.empty();
    }
    return Optional.of(new Model(Model.FORMAT_VERSION, Model.SPEC_VERSION, parts.hierarchy().orElseThrow(),
        parts.entities().orElseThrow(), parts.facts().orElseThrow(), parts.keys().orElseThrow(),
        parts.values().orElseThrow(), parts.features().orElseThrow()));
  }
}
