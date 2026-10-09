package io.github.tierwise.core.internal.yaml;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * A mapping node. Keys are strings in the form the compiler reads them: quotes removed, escapes decoded
 * and normalised as the owning section requires (FIND-20). Entries keep document order.
 *
 * @param file the source file
 * @param path the path from the document root
 * @param line the 1-based line on which the node begins
 * @param entries the entries in document order
 */
public record PMap(String file, List<Object> path, int line, Map<String, PNode> entries) implements PNode {

  /** Validates the components and makes the collections unmodifiable. */
  public PMap {
    Objects.requireNonNull(file, "file");
    path = List.copyOf(path);
    entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
  }

  /**
   * Returns the node of an entry.
   *
   * @param key the mapping key
   * @return the node, or empty when the mapping has no such key
   */
  public Optional<PNode> get(String key) {
    return Optional.ofNullable(entries.get(key));
  }

  /**
   * Tells whether the mapping has a key.
   *
   * @param key the mapping key
   * @return {@code true} when an entry exists
   */
  public boolean has(String key) {
    return entries.containsKey(key);
  }
}
