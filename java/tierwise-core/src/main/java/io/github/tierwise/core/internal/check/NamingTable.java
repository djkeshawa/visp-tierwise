package io.github.tierwise.core.internal.check;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The naming patterns of a compilation, published by {@link KindStage} for the lint rules
 * {@code naming/<name>} (LINT-12, LINT-19) and for the kind check of LINT-16.
 *
 * @param effective the pattern in effect for each name: a later layer replaces an earlier one entirely
 *     (MANIFEST-32, MANIFEST-33), and the first declaration wins among the project's own sources
 * @param all every declaration, replaced ones included, in definition order
 */
public record NamingTable(Map<String, NamingPattern> effective, List<NamingPattern> all) {

  /** Makes the components unmodifiable. */
  public NamingTable {
    effective = Collections.unmodifiableMap(new LinkedHashMap<>(effective));
    all = List.copyOf(all);
  }
}
