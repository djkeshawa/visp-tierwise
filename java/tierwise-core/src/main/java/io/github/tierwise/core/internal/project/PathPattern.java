package io.github.tierwise.core.internal.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The path pattern of a path-mapped values entry (SRC-22, SRC-23).
 *
 * <p>The pattern holds exactly one {@code {scope}} and exactly one {@code {node}} placeholder in
 * different path segments. Each placeholder matches a run of characters within one segment (possibly
 * empty), and the text around the placeholders matches only itself.
 */
final class PathPattern {

  private static final String SCOPE = "{scope}";
  private static final String NODE = "{node}";

  /** The names that one file path supplies. */
  record Captures(String scope, String node) {
  }

  private final List<Pattern> segments;
  private final int scopeSegment;
  private final int nodeSegment;
  private final String directoryPrefix;

  private PathPattern(List<Pattern> segments, int scopeSegment, int nodeSegment, String directoryPrefix) {
    this.segments = segments;
    this.scopeSegment = scopeSegment;
    this.nodeSegment = nodeSegment;
    this.directoryPrefix = directoryPrefix;
  }

  /**
   * Parses a pattern.
   *
   * @param text the pattern as the manifest writes it
   * @return the pattern, or empty when SRC-23 forbids it: a placeholder count other than one each, both
   *     placeholders in one segment, another placeholder, or a glob wildcard ({@code *}, {@code ?} or
   *     {@code [})
   */
  static Optional<PathPattern> parse(String text) {
    List<String> parts = new ArrayList<>();
    for (String part : text.split("/", -1)) {
      if (!part.equals(".")) {
        parts.add(part);
      }
    }
    int scopeSegment = -1;
    int nodeSegment = -1;
    List<Pattern> compiled = new ArrayList<>();
    List<String> literalPrefix = new ArrayList<>();
    boolean placeholderSeen = false;
    for (int i = 0; i < parts.size(); i++) {
      String part = parts.get(i);
      if (part.indexOf('*') >= 0 || part.indexOf('?') >= 0 || part.indexOf('[') >= 0) {
        return Optional.empty();
      }
      boolean hasScope = part.contains(SCOPE);
      boolean hasNode = part.contains(NODE);
      if (hasScope && part.indexOf(SCOPE) != part.lastIndexOf(SCOPE)
          || hasNode && part.indexOf(NODE) != part.lastIndexOf(NODE) || hasScope && hasNode) {
        return Optional.empty();
      }
      String rest = part.replace(SCOPE, "").replace(NODE, "");
      if (rest.indexOf('{') >= 0 || rest.indexOf('}') >= 0) {
        return Optional.empty();
      }
      if (hasScope) {
        if (scopeSegment >= 0) {
          return Optional.empty();
        }
        scopeSegment = i;
      }
      if (hasNode) {
        if (nodeSegment >= 0) {
          return Optional.empty();
        }
        nodeSegment = i;
      }
      placeholderSeen |= hasScope || hasNode;
      if (!placeholderSeen && i < parts.size() - 1) {
        literalPrefix.add(part);
      }
      compiled.add(segmentPattern(part));
    }
    if (scopeSegment < 0 || nodeSegment < 0) {
      return Optional.empty();
    }
    return Optional.of(new PathPattern(compiled, scopeSegment, nodeSegment, String.join("/", literalPrefix)));
  }

  private static Pattern segmentPattern(String part) {
    StringBuilder regex = new StringBuilder();
    int i = 0;
    while (i < part.length()) {
      if (part.startsWith(SCOPE, i) || part.startsWith(NODE, i)) {
        regex.append("(.*)");
        i += part.startsWith(SCOPE, i) ? SCOPE.length() : NODE.length();
      } else {
        int next = i;
        while (next < part.length() && !part.startsWith(SCOPE, next) && !part.startsWith(NODE, next)) {
          next++;
        }
        regex.append(Pattern.quote(part.substring(i, next)));
        i = next;
      }
    }
    return Pattern.compile(regex.toString(), Pattern.DOTALL);
  }

  /**
   * Returns the directory that contains every match: the leading segments without a placeholder.
   *
   * @return a project-relative directory path with {@code /} separators; empty for the project root
   */
  String directoryPrefix() {
    return directoryPrefix;
  }

  /**
   * Matches a project-relative path.
   *
   * @param relativePath the path with {@code /} separators
   * @return the captured scope and node as written, or empty when the path does not match
   */
  Optional<Captures> match(String relativePath) {
    String[] path = relativePath.split("/", -1);
    if (path.length != segments.size()) {
      return Optional.empty();
    }
    String scope = null;
    String node = null;
    for (int i = 0; i < path.length; i++) {
      Matcher matcher = segments.get(i).matcher(path[i]);
      if (!matcher.matches()) {
        return Optional.empty();
      }
      if (i == scopeSegment) {
        scope = matcher.group(1);
      } else if (i == nodeSegment) {
        node = matcher.group(1);
      }
    }
    return Optional.of(new Captures(scope, node));
  }
}
