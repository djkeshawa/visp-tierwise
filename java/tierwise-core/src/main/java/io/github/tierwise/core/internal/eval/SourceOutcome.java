package io.github.tierwise.core.internal.eval;

import io.github.tierwise.core.Resolved.Contribution.Label;
import java.util.Objects;

/** What a service-sourced level did for one key being resolved (SVC-26, SVC-34). */
sealed interface SourceOutcome {

  /**
   * The provider supplied a valid value.
   *
   * @param value the value in normal form
   */
  record Supplied(Object value) implements SourceOutcome {

    public Supplied {
      Objects.requireNonNull(value, "value");
    }
  }

  /**
   * The level had a source failure for the key.
   *
   * @param label {@link Label#UNAVAILABLE} or {@link Label#INVALID}
   */
  record Failed(Label label) implements SourceOutcome {

    public Failed {
      if (!label.isFailure()) {
        throw new IllegalArgumentException("not a failure label: " + label);
      }
    }
  }
}
