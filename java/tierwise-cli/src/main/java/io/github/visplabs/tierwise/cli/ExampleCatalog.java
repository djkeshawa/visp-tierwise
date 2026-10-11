package io.github.visplabs.tierwise.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import org.snakeyaml.engine.v2.api.lowlevel.Compose;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;

/**
 * Lists the examples of a project (POL-28) so that {@code tierwise test} can report each one.
 *
 * <p>The core reports a failing example as {@code E_EXAMPLE_FAILED} (POL-42) and nothing for a passing
 * one, so the command line reads the sources itself to know which examples exist. It reads the rules
 * documents the manifest selects: the inline {@code rules} list of the manifest (MANIFEST-16) and the
 * files that the globs of {@code sources.rules} match (SRC-18). It does not validate anything; a source
 * it cannot read contributes no example, and the core has already reported that source.
 */
final class ExampleCatalog {

  /** The name of the manifest, whose findings and inline content use this file name (FIND-17). */
  static final String MANIFEST = "tierwise.yaml";

  /**
   * One example of a rules document.
   *
   * @param file the source path relative to the project directory, with {@code /} separators
   * @param path the path of the example's list item, as findings write paths (FIND-20)
   * @param name the example's {@code name}, or an empty string when it has none
   * @param line the 1-based line on which the example begins
   */
  record Entry(String file, List<Object> path, String name, int line) {

    /** Tells whether a finding path is at or below this example. */
    boolean contains(List<Object> findingPath) {
      return findingPath.size() >= path.size() && findingPath.subList(0, path.size()).equals(path);
    }
  }

  private static final LoadSettings SETTINGS = LoadSettings.builder().setUseMarks(true).build();

  private ExampleCatalog() {
  }

  /**
   * Lists the examples of the project in a directory.
   *
   * @param dir the project directory
   * @return the examples, the manifest's inline rules first and then the rules files in path order
   */
  static List<Entry> list(Path dir) {
    List<Entry> entries = new ArrayList<>();
    Optional<MappingNode> manifest = readMapping(dir.resolve(MANIFEST));
    if (manifest.isEmpty()) {
      return entries;
    }
    Node rules = value(manifest.get(), "rules");
    if (rules instanceof SequenceNode sequence) {
      int index = 0;
      for (Node document : sequence.getValue()) {
        if (document instanceof MappingNode mapping) {
          collect(entries, MANIFEST, List.of("rules", index), mapping);
        }
        index++;
      }
    }
    for (String file : rulesFiles(dir, manifest.get())) {
      readMapping(dir.resolve(file)).ifPresent(mapping -> collect(entries, file, List.of(), mapping));
    }
    return entries;
  }

  private static void collect(List<Entry> entries, String file, List<Object> prefix, MappingNode document) {
    if (!(value(document, "examples") instanceof SequenceNode examples)) {
      return;
    }
    int index = 0;
    for (Node example : examples.getValue()) {
      List<Object> path = new ArrayList<>(prefix);
      path.add("examples");
      path.add(index);
      String name = example instanceof MappingNode mapping && value(mapping, "name") instanceof ScalarNode scalar
          ? scalar.getValue() : "";
      entries.add(new Entry(file, List.copyOf(path), name, line(example)));
      index++;
    }
  }

  private static List<String> rulesFiles(Path dir, MappingNode manifest) {
    TreeSet<String> files = new TreeSet<>();
    if (value(manifest, "sources") instanceof MappingNode sources
        && value(sources, "rules") instanceof SequenceNode patterns) {
      for (Node pattern : patterns.getValue()) {
        if (pattern instanceof ScalarNode scalar) {
          files.addAll(Glob.parse(scalar.getValue()).files(dir));
        }
      }
    }
    return List.copyOf(files);
  }

  private static Optional<MappingNode> readMapping(Path file) {
    try {
      String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
      return new Compose(SETTINGS).composeString(text)
          .filter(MappingNode.class::isInstance).map(MappingNode.class::cast);
    } catch (IOException | YamlEngineException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private static Node value(MappingNode mapping, String key) {
    for (NodeTuple tuple : mapping.getValue()) {
      if (tuple.getKeyNode() instanceof ScalarNode scalar && scalar.getValue().equals(key)) {
        return tuple.getValueNode();
      }
    }
    return null;
  }

  private static int line(Node node) {
    return node.getStartMark().map(mark -> mark.getLine() + 1).orElse(1);
  }

  /** A glob of {@code sources.rules} (SRC-18): {@code *} within a segment, {@code **} across segments. */
  static final class Glob {

    private final List<String> segments;

    private Glob(List<String> segments) {
      this.segments = segments;
    }

    /** Parses a pattern; empty and {@code .} segments are dropped. */
    static Glob parse(String pattern) {
      List<String> segments = new ArrayList<>();
      for (String segment : pattern.split("/", -1)) {
        if (!segment.isEmpty() && !segment.equals(".")) {
          segments.add(segment);
        }
      }
      return new Glob(segments);
    }

    /** Tells whether a project-relative path with {@code /} separators matches. */
    boolean matches(String relativePath) {
      return match(0, List.of(relativePath.split("/")), 0);
    }

    /** Lists the regular files below a directory that match, as project-relative paths. */
    List<String> files(Path dir) {
      List<String> found = new ArrayList<>();
      Path start = dir.resolve(String.join("/", literalPrefix()));
      if (!Files.isDirectory(start)) {
        return found;
      }
      try {
        // Symbolic links are followed (SRC-18 (e)), as the core does.
        Files.walkFileTree(start, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
            new SimpleFileVisitor<>() {
          @Override
          public FileVisitResult visitFileFailed(Path file, IOException error) {
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
            if (attributes.isRegularFile()) {
              String relative = dir.relativize(file).toString().replace('\\', '/');
              if (matches(relative)) {
                found.add(relative);
              }
            }
            return FileVisitResult.CONTINUE;
          }
        });
      } catch (IOException e) {
        return found;
      }
      return found;
    }

    private List<String> literalPrefix() {
      List<String> prefix = new ArrayList<>();
      for (int i = 0; i < segments.size() - 1 && segments.get(i).indexOf('*') < 0; i++) {
        prefix.add(segments.get(i));
      }
      return prefix;
    }

    private boolean match(int pattern, List<String> path, int position) {
      if (pattern == segments.size()) {
        return position == path.size();
      }
      String segment = segments.get(pattern);
      if (segment.equals("**")) {
        // A path ends in a file name, so ** consumes only directory segments (as in the core).
        for (int skip = position; skip < path.size(); skip++) {
          if (match(pattern + 1, path, skip)) {
            return true;
          }
        }
        return false;
      }
      return position < path.size() && segmentMatches(segment, path.get(position))
          && match(pattern + 1, path, position + 1);
    }

    private static boolean segmentMatches(String pattern, String text) {
      int p = 0;
      int t = 0;
      int star = -1;
      int mark = 0;
      while (t < text.length()) {
        if (p < pattern.length() && pattern.charAt(p) == '*') {
          star = p++;
          mark = t;
        } else if (p < pattern.length() && pattern.charAt(p) == text.charAt(t)) {
          p++;
          t++;
        } else if (star >= 0) {
          p = star + 1;
          t = ++mark;
        } else {
          return false;
        }
      }
      while (p < pattern.length() && pattern.charAt(p) == '*') {
        p++;
      }
      return p == pattern.length();
    }
  }
}
