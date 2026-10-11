package io.github.visplabs.tierwise.core.internal.project;

import java.util.Collection;
import java.util.Optional;

/**
 * Suggests the declared name that is closest to an unknown name (FIND-27, FIND-28).
 *
 * <p>Closeness is the Damerau-Levenshtein distance in its optimal-string-alignment form, over code
 * points, as the implementation plan (item 2.7) chooses. A suggestion is always the exact text of one
 * candidate.
 */
public final class NameSuggester {

  private NameSuggester() {
  }

  /**
   * Finds the closest candidate.
   *
   * @param unknown the name that matched no declaration
   * @param candidates the declared names of the matching category
   * @return the candidate with the smallest distance when that distance is small enough, the first
   *     such candidate in iteration order on a tie, otherwise empty
   */
  public static Optional<String> closest(String unknown, Collection<String> candidates) {
    int[] target = unknown.codePoints().toArray();
    int limit = Math.max(2, Math.min(4, target.length / 3));
    String best = null;
    int bestDistance = Integer.MAX_VALUE;
    for (String candidate : candidates) {
      int distance = distance(target, candidate.codePoints().toArray());
      if (distance < bestDistance && distance <= limit && !candidate.equals(unknown)) {
        best = candidate;
        bestDistance = distance;
      }
    }
    return Optional.ofNullable(best);
  }

  /**
   * Computes the optimal-string-alignment distance of two code point sequences.
   *
   * @param a the first sequence
   * @param b the second sequence
   * @return the number of insertions, deletions, substitutions and adjacent transpositions
   */
  static int distance(int[] a, int[] b) {
    int[][] d = new int[a.length + 1][b.length + 1];
    for (int i = 0; i <= a.length; i++) {
      d[i][0] = i;
    }
    for (int j = 0; j <= b.length; j++) {
      d[0][j] = j;
    }
    for (int i = 1; i <= a.length; i++) {
      for (int j = 1; j <= b.length; j++) {
        int cost = a[i - 1] == b[j - 1] ? 0 : 1;
        d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
        if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
          d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
        }
      }
    }
    return d[a.length][b.length];
  }
}
