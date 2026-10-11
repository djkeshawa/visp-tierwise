package io.github.visplabs.tierwise.core.internal.yaml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The anchors of one source, and the copies that an alias produces.
 *
 * <p>An alias is reported as {@code E_YAML_FEATURE} (SRC-2), and a copy of its target keeps the rest of
 * the source checked as written. The copy takes the path of the aliasing node (FIND-19), and its root
 * begins on the line of the aliasing node (FIND-22). The nodes inside keep the lines they have in the
 * source, because those lines show where the content is written. An anchor is defined only when its node
 * is complete, so an alias to an ancestor has no target.
 *
 * <p>Copies are bounded. A copy of an anchored node may hold aliases of its own, so the copies of nested
 * aliases multiply: ten levels of ten aliases each would be a billion nodes (a billion-laughs source). The
 * copies of one source share one budget of {@link #COPY_BUDGET} nodes, and no copy node reaches a path
 * length above the nesting limit {@link YamlNodeReader#MAX_DEPTH}. An alias whose copy would exceed the
 * budget, or would be too deep, produces no node, so its entry is absent from the tree. The alias is still
 * reported, and a later check may report a required entry as missing. Aliases are copied in document
 * order, so the outcome is the same for the same source.
 */
final class AnchorTable {

  /** The number of nodes that the copies of one source may hold in all. */
  static final int COPY_BUDGET = 100_000;

  private final int maxDepth;
  private final Map<String, Defined> definitions = new HashMap<>();
  private int budget = COPY_BUDGET;

  /**
   * A defined anchor: its node, the number of nodes in it, and the greatest path length of a node in it.
   *
   * @param node the anchored node
   * @param size the number of nodes in the anchored node, the node included
   * @param height the greatest path length of a node in the anchored node
   */
  private record Defined(PNode node, int size, int height) {
  }

  /**
   * Creates the table of one source.
   *
   * @param maxDepth the greatest path length that a copy may reach
   */
  AnchorTable(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  /**
   * Defines an anchor, replacing an earlier definition of the same name.
   *
   * @param name the anchor name
   * @param node the complete node
   */
  void define(String name, PNode node) {
    definitions.put(name, new Defined(node, count(node), height(node)));
  }

  /**
   * Copies the node of a defined anchor to the position of an alias, when the copy fits the budget and the
   * depth limit.
   *
   * @param name the anchor name
   * @param to the path of the aliasing node
   * @param line the line of the aliasing node
   * @return the copy, or empty when the anchor is not defined yet, or when the copy would exceed the
   *     budget or the depth limit
   */
  Optional<PNode> copy(String name, List<Object> to, int line) {
    Defined target = definitions.get(name);
    if (target == null) {
      return Optional.empty();
    }
    int base = target.node().path().size();
    int deepest = to.size() + target.height() - base;
    if (target.size() > budget || deepest > maxDepth) {
      return Optional.empty();
    }
    budget -= target.size();
    return Optional.of(move(target.node(), to, base, line));
  }

  private static PNode move(PNode node, List<Object> to, int base, int line) {
    List<Object> path = new ArrayList<>(to);
    path.addAll(node.path().subList(base, node.path().size()));
    if (node instanceof PMap map) {
      Map<String, PNode> entries = new LinkedHashMap<>();
      map.entries().forEach((key, value) -> entries.put(key, move(value, to, base, value.line())));
      return new PMap(map.file(), path, line, entries);
    }
    if (node instanceof PSeq seq) {
      List<PNode> items = new ArrayList<>();
      for (PNode item : seq.items()) {
        items.add(move(item, to, base, item.line()));
      }
      return new PSeq(seq.file(), path, line, items);
    }
    PScalar scalar = (PScalar) node;
    return new PScalar(scalar.file(), path, line, scalar.kind(), scalar.value(), scalar.text());
  }

  private static int count(PNode node) {
    int total = 1;
    for (PNode child : children(node)) {
      total += count(child);
    }
    return total;
  }

  private static int height(PNode node) {
    int height = node.path().size();
    for (PNode child : children(node)) {
      height = Math.max(height, height(child));
    }
    return height;
  }

  private static Collection<PNode> children(PNode node) {
    if (node instanceof PMap map) {
      return map.entries().values();
    }
    if (node instanceof PSeq seq) {
      return seq.items();
    }
    return List.of();
  }
}
