package io.github.tierwise.core.internal.yaml;

import java.util.List;

/**
 * A node of a parsed source document, with its location.
 *
 * <p>The location follows the rules for findings: {@link #file()} is the source path relative to the
 * project root with {@code /} separators (FIND-17), {@link #path()} is the sequence of mapping keys
 * ({@code String}) and zero-based sequence positions ({@code Integer}) from the document root, which has
 * the empty path (FIND-19, FIND-20), and {@link #line()} is the 1-based line on which the node begins
 * (FIND-21, FIND-22). The value of a mapping entry begins on the line of its key.
 */
public sealed interface PNode permits PMap, PSeq, PScalar {

  /**
   * Returns the source file.
   *
   * @return the file name as findings report it
   */
  String file();

  /**
   * Returns the path from the document root to this node.
   *
   * @return an unmodifiable list of {@code String} and {@code Integer} segments
   */
  List<Object> path();

  /**
   * Returns the line on which this node begins.
   *
   * @return a 1-based line number
   */
  int line();
}
