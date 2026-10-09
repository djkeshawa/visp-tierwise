package io.github.tierwise.core.internal.model;

import java.util.Objects;

/**
 * A fact declaration (BUNDLE-48 to BUNDLE-50).
 *
 * @param entity the entity named by the prefix of the fact's name
 * @param type the fact's type declaration: boolean, integer, decimal, string or enum
 */
public record FactDef(String entity, TypeDecl type) {

  /** Validates the components. */
  public FactDef {
    Objects.requireNonNull(entity, "entity");
    Objects.requireNonNull(type, "type");
  }
}
