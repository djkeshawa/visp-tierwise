package io.github.tierwise.core.internal.check;

import io.github.tierwise.core.internal.model.TypeDecl;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What {@link KeyStage} learned about one declared key, for the stages that check values.
 *
 * <p>A key whose {@code type} or {@code combine} is missing or invalid has empty {@code type} and
 * {@code combine}, and receives no value checks (FIND-35); it is still a declared key, so a value for it
 * is not an unknown key.
 *
 * @param name the key name as declared
 * @param node the declaration node
 * @param type the key's type, when it and the strategy are valid
 * @param combine the key's strategy, when it and the type are valid
 * @param settableAt the valid scope names in {@code settable_at}, NFC-normalised, without repeats; empty
 *     when {@code settable_at} is missing or is not a non-empty list
 * @param settableKnown {@code true} when {@code settable_at} is a non-empty list, so that a value at a
 *     scope that is not in it is {@code E_NOT_SETTABLE_HERE} (KEY-11)
 * @param defaultValue the validated, normalised {@code default}, when one is declared and valid
 * @param declaresDefault {@code true} when the declaration has a {@code default} member
 * @param kind the NFC-normalised name of the kind, when {@code kind} is a string
 */
public record KeyInfo(String name, PNode node, Optional<TypeDecl> type, Optional<String> combine,
    List<String> settableAt, boolean settableKnown, Optional<Object> defaultValue, boolean declaresDefault,
    Optional<String> kind) {

  /** Validates the components. */
  public KeyInfo {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(node, "node");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(combine, "combine");
    Objects.requireNonNull(defaultValue, "defaultValue");
    Objects.requireNonNull(kind, "kind");
    settableAt = List.copyOf(settableAt);
  }

  /**
   * Tells whether the key's values are checked: its type and strategy are valid (FIND-35).
   *
   * @return {@code true} when both are valid
   */
  public boolean checkable() {
    return type.isPresent() && combine.isPresent();
  }
}
