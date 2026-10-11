package io.github.visplabs.tierwise.core.internal.yaml;

import java.util.Arrays;

/**
 * Maps code-point indices of a source to 1-based lines (FIND-21, FIND-22).
 *
 * <p>Lines are counted at line feed, at carriage return and at carriage return followed by line feed,
 * which counts as one break. Other characters, such as U+2028, do not break a line. The parser's own
 * line count is not used, because it may count characters that YAML 1.2 does not treat as breaks.
 */
final class YamlPositions {

  private final int[] codePoints;
  private final int[] lineStarts;

  /**
   * Indexes the text of a source.
   *
   * @param text the text, as the parser reads it
   */
  YamlPositions(String text) {
    this.codePoints = text.codePoints().toArray();
    int[] starts = new int[codePoints.length + 1];
    int count = 0;
    starts[count++] = 0;
    for (int i = 0; i < codePoints.length; i++) {
      int c = codePoints[i];
      if (c == '\r' && i + 1 < codePoints.length && codePoints[i + 1] == '\n') {
        continue;
      }
      if (c == '\r' || c == '\n') {
        starts[count++] = i + 1;
      }
    }
    this.lineStarts = Arrays.copyOf(starts, count);
  }

  /**
   * Returns the line of a code-point index.
   *
   * @param index the index, counted in code points from the start of the source
   * @return the 1-based line
   */
  int lineAt(int index) {
    int position = Arrays.binarySearch(lineStarts, index);
    return position >= 0 ? position + 1 : -position - 1;
  }

  /**
   * Returns the line of a block sequence item from the index at which its content begins.
   *
   * <p>A block item begins on the line of its {@code -} indicator (FIND-22). Between the indicator and
   * the content there are only blanks, line breaks and comments, so the scan goes back over them: a
   * line whose code part ends in the indicator holds the item, and a line with no code part (a comment
   * or a blank line) is skipped. When the first code before the content is not an indicator, the item
   * is located on the line of its content.
   *
   * @param contentIndex the code-point index at which the content of the item begins
   * @return the 1-based line
   */
  int blockItemLine(int contentIndex) {
    int j = Math.min(contentIndex, codePoints.length) - 1;
    while (j >= 0) {
      while (j >= 0 && isBlank(codePoints[j])) {
        j--;
      }
      if (j < 0) {
        break;
      }
      int start = lineStart(j);
      int m = codeEnd(start, j) - 1;
      while (m >= start && isSpaceOrTab(codePoints[m])) {
        m--;
      }
      if (m < start) {
        j = start - 1;
        continue;
      }
      return codePoints[m] == '-' ? lineAt(m) : lineAt(contentIndex);
    }
    return lineAt(contentIndex);
  }

  /** Returns the index of the first code point of the line that holds an index. */
  private int lineStart(int index) {
    int start = index;
    while (start > 0 && !isLineBreak(codePoints[start - 1])) {
      start--;
    }
    return start;
  }

  /**
   * Returns the end of the code part of a line segment: the index of a comment that begins in the
   * segment, or one past its last index when there is none. A comment begins at a {@code #} that is at
   * the start of the line or follows a blank.
   */
  private int codeEnd(int start, int last) {
    for (int k = start; k <= last; k++) {
      if (codePoints[k] == '#' && (k == start || isSpaceOrTab(codePoints[k - 1]))) {
        return k;
      }
    }
    return last + 1;
  }

  private static boolean isBlank(int c) {
    return c == ' ' || c == '\t' || c == '\r' || c == '\n';
  }

  private static boolean isSpaceOrTab(int c) {
    return c == ' ' || c == '\t';
  }

  private static boolean isLineBreak(int c) {
    return c == '\r' || c == '\n';
  }
}
