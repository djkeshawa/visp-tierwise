package io.github.tierwise.core.internal.yaml;

import java.util.List;
import java.util.Objects;

/**
 * A sequence node.
 *
 * @param file the source file
 * @param path the path from the document root
 * @param line the 1-based line on which the node begins
 * @param items the items in document order
 */
public record PSeq(String file, List<Object> path, int line, List<PNode> items) implements PNode {

  /** Validates the components and makes the collections unmodifiable. */
  public PSeq {
    Objects.requireNonNull(file, "file");
    path = List.copyOf(path);
    items = List.copyOf(items);
  }
}
