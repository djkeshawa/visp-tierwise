package io.github.tierwise.core.internal.project;

import java.util.ArrayList;
import java.util.List;

/**
 * A glob pattern of {@code sources.schema} or {@code sources.rules} (SRC-18).
 *
 * <p>{@code /} separates path segments. {@code *} matches any run of characters within one segment.
 * {@code **} as a whole segment matches zero or more whole directory segments, so {@code a/**}{@code /b}
 * also matches {@code a/b}. Matching is case-sensitive and compares code points. Hidden files are not
 * treated specially. Every other character, such as {@code ?} and {@code [}, is a literal.
 */
final class GlobPattern {

  private final List<String> segments;

  private GlobPattern(List<String> segments) {
    this.segments = segments;
  }

  /**
   * Parses a pattern. Empty segments and {@code .} segments are dropped.
   *
   * @param text the pattern as the manifest writes it
   * @return the pattern
   */
  static GlobPattern parse(String text) {
    List<String> segments = new ArrayList<>();
    for (String segment : text.split("/", -1)) {
      if (!segment.isEmpty() && !segment.equals(".")) {
        segments.add(segment);
      }
    }
    return new GlobPattern(segments);
  }

  /**
   * Returns the directory that contains every match: the leading segments without a wildcard, except
   * the last segment.
   *
   * @return a project-relative directory path with {@code /} separators; empty for the project root
   */
  String directoryPrefix() {
    List<String> prefix = new ArrayList<>();
    for (int i = 0; i < segments.size() - 1; i++) {
      if (segments.get(i).indexOf('*') >= 0) {
        break;
      }
      prefix.add(segments.get(i));
    }
    return String.join("/", prefix);
  }

  /**
   * Tells whether a project-relative path matches.
   *
   * @param relativePath the path with {@code /} separators
   * @return {@code true} when every segment matches
   */
  boolean matches(String relativePath) {
    List<String> path = new ArrayList<>();
    for (String segment : relativePath.split("/", -1)) {
      path.add(segment);
    }
    return matchFrom(0, path, 0);
  }

  private boolean matchFrom(int p, List<String> path, int s) {
    if (p == segments.size()) {
      return s == path.size();
    }
    String pattern = segments.get(p);
    if (pattern.equals("**")) {
      // A path always ends in a file name, so ** may consume only directory segments.
      for (int skip = s; skip < path.size(); skip++) {
        if (matchFrom(p + 1, path, skip)) {
          return true;
        }
      }
      return false;
    }
    return s < path.size() && segmentMatches(pattern, path.get(s)) && matchFrom(p + 1, path, s + 1);
  }

  /** Matches one segment, where {@code *} is any run of code points. */
  static boolean segmentMatches(String pattern, String text) {
    int[] p = pattern.codePoints().toArray();
    int[] t = text.codePoints().toArray();
    int pi = 0;
    int ti = 0;
    int star = -1;
    int mark = 0;
    while (ti < t.length) {
      if (pi < p.length && p[pi] == '*') {
        star = pi++;
        mark = ti;
      } else if (pi < p.length && p[pi] == t[ti]) {
        pi++;
        ti++;
      } else if (star >= 0) {
        pi = star + 1;
        ti = ++mark;
      } else {
        return false;
      }
    }
    while (pi < p.length && p[pi] == '*') {
      pi++;
    }
    return pi == p.length;
  }
}
