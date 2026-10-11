package io.github.visplabs.tierwise.core.internal.check;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The kinds of a compilation, published by {@link KindStage} for {@link KeyStage}, {@link ValueStage}
 * and the lint rules. A kind table is compile-time data and is not part of the model.
 *
 * @param effective the definition in effect for each kind name: a later layer replaces an earlier one
 *     entirely (MANIFEST-32, MANIFEST-33), and the first declaration wins among the project's own sources
 * @param all every definition, replaced ones included, in definition order (LINT-49)
 */
public record KindTable(Map<String, KindDef> effective, List<KindDef> all) {

  /** Makes the components unmodifiable. */
  public KindTable {
    effective = Collections.unmodifiableMap(new LinkedHashMap<>(effective));
    all = List.copyOf(all);
  }
}
