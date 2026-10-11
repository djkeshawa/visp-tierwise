package io.github.visplabs.tierwise.core.internal.model;

import java.util.List;

/**
 * An entity and its direct edges (BUNDLE-44 to BUNDLE-47).
 *
 * @param belongsTo the entities named by the entity's own {@code belongs_to}, in code point order
 * @param hasMany the entities named by the entity's own {@code has_many}, in code point order
 */
public record EntityDef(List<String> belongsTo, List<String> hasMany) {

  /** Makes the lists unmodifiable. */
  public EntityDef {
    belongsTo = List.copyOf(belongsTo);
    hasMany = List.copyOf(hasMany);
  }
}
