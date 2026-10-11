package io.github.visplabs.tierwise.core.internal.check;

import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A kind definition (KIND-1, KIND-3), reduced to the members that were valid. A member that is absent or
 * invalid imposes no constraint (KIND-3).
 *
 * @param name the kind name, NFC-normalised (KIND-2)
 * @param strategies the allowed strategies, when the member is a valid non-empty list of strategies
 * @param narrowingOnly {@code true} when {@code narrowing_only} is the boolean {@code true} (KIND-5)
 * @param requireDefault {@code true} when {@code require_default} is the boolean {@code true} (KIND-8)
 * @param onSourceFailure {@code skip} or {@code deny}, when the member is valid (KIND-9, KIND-11)
 * @param naming the name of the key pattern, when the member is a string (KIND-12)
 * @param entry the definition node, which locates findings about the definition
 * @param layer the layer of MANIFEST-31 that declares the kind
 * @param preset {@code true} when a preset declares the kind
 */
public record KindDef(String name, Optional<List<String>> strategies, boolean narrowingOnly,
    boolean requireDefault, Optional<String> onSourceFailure, Optional<String> naming, PNode entry, int layer,
    boolean preset) {

  /** Validates the components and copies the strategies. */
  public KindDef {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(onSourceFailure, "onSourceFailure");
    Objects.requireNonNull(naming, "naming");
    Objects.requireNonNull(entry, "entry");
    strategies = Objects.requireNonNull(strategies, "strategies").map(List::copyOf);
  }
}
