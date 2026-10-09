package io.github.tierwise.core.internal.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A key declaration (BUNDLE-51 to BUNDLE-63).
 *
 * @param type the key's type declaration
 * @param combine the merge strategy name, in its source spelling
 * @param defaultValue the key's {@code default}; present exactly when the key declares one, never null
 * @param settableAt scope names at which the key may be set: levels in declared order, then axes
 * @param combineAcrossAxes {@code axis_wins}, {@code hierarchy_wins}, {@code max} or {@code min};
 *     present exactly when the key is order-dependent and settable at an axis
 * @param onSourceFailure {@code skip} or {@code deny}; present exactly when the key is settable at a
 *     service-sourced level, whether declared on the key or obtained through its kind
 * @param kind the name of the kind the key opts into; nothing else about the kind is kept
 */
public record KeyDef(
    TypeDecl type,
    String combine,
    Optional<Object> defaultValue,
    List<String> settableAt,
    Optional<String> combineAcrossAxes,
    Optional<String> onSourceFailure,
    Optional<String> kind) {

  /** Validates the components and copies the default value deeply. */
  public KeyDef {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(combine, "combine");
    Objects.requireNonNull(combineAcrossAxes, "combineAcrossAxes");
    Objects.requireNonNull(onSourceFailure, "onSourceFailure");
    Objects.requireNonNull(kind, "kind");
    defaultValue = Objects.requireNonNull(defaultValue, "defaultValue").map(Plain::copy);
    settableAt = List.copyOf(settableAt);
  }
}
