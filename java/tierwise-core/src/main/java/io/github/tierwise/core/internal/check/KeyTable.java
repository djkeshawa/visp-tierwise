package io.github.tierwise.core.internal.check;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The declared keys of a compilation, published by {@link KeyStage} for {@link ValueStage} and the
 * stages after it. Unlike {@link io.github.tierwise.core.internal.model.KeyDef} it also holds the keys
 * whose declaration has a fault.
 *
 * @param keys the first declaration of each key name, in definition order; a repeated declaration is
 *     {@code E_DUPLICATE_DEFINITION} (SRC-40) and is not checked
 */
public record KeyTable(Map<String, KeyInfo> keys) {

  /** Makes the map unmodifiable. */
  public KeyTable {
    keys = Collections.unmodifiableMap(new LinkedHashMap<>(keys));
  }
}
