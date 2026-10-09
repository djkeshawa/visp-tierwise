package io.github.tierwise.core.conformance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.composer.Composer;
import org.snakeyaml.engine.v2.nodes.MappingNode;
import org.snakeyaml.engine.v2.nodes.Node;
import org.snakeyaml.engine.v2.nodes.NodeTuple;
import org.snakeyaml.engine.v2.nodes.NodeType;
import org.snakeyaml.engine.v2.nodes.ScalarNode;
import org.snakeyaml.engine.v2.nodes.SequenceNode;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;
import org.snakeyaml.engine.v2.schema.CoreSchema;

/**
 * Locates {@code spec/} and loads the conformance suite in place (VEC-108 to VEC-110).
 *
 * <p>The directory comes from the system property {@code tierwise.spec.dir}, which the POM sets to
 * {@code ${project.basedir}/../../spec}. When the property is missing the helper searches upward from the
 * working directory for a {@code spec/conformance} directory. Nothing is copied and nothing is written.
 *
 * <p>YAML is read as the spec requires for vector files (VEC-6 to VEC-8): UTF-8 without a byte-order
 * mark, the YAML 1.2 core schema, no duplicate mapping key and a single document. Values arrive as the
 * core schema produces them: {@code Boolean}, {@code Integer}, {@code Long} or {@code BigInteger},
 * {@code Double}, {@code String}, {@code null}, {@code List} and {@code Map}.
 */
final class VectorFiles {

  /** The system property that names {@code spec/}. */
  static final String SPEC_DIR_PROPERTY = "tierwise.spec.dir";

  private VectorFiles() {
  }

  /**
   * Returns the {@code spec/} directory.
   *
   * @return an absolute, normalised path
   */
  static Path specDir() {
    String property = System.getProperty(SPEC_DIR_PROPERTY);
    if (property != null && !property.isBlank()) {
      Path dir = Paths.get(property).toAbsolutePath().normalize();
      if (!Files.isDirectory(dir.resolve("conformance"))) {
        throw new IllegalStateException(SPEC_DIR_PROPERTY + " does not point to spec/: " + dir);
      }
      return dir;
    }
    for (Path dir = Paths.get("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
      if (Files.isDirectory(dir.resolve("spec").resolve("conformance"))) {
        return dir.resolve("spec").normalize();
      }
    }
    throw new IllegalStateException("cannot locate spec/conformance; set -D" + SPEC_DIR_PROPERTY);
  }

  /**
   * Returns {@code spec/conformance/}.
   *
   * @return an absolute path
   */
  static Path conformanceDir() {
    return specDir().resolve("conformance");
  }

  /**
   * Returns the repository root, the parent of {@code spec/}.
   *
   * @return an absolute path
   */
  static Path repositoryRoot() {
    return specDir().getParent();
  }

  /**
   * Lists the {@code .yaml} files below a directory at any depth, in path order.
   *
   * @param dir the directory
   * @return the files; empty when the directory does not exist
   */
  static List<Path> yamlFiles(Path dir) {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> walk = Files.walk(dir)) {
      List<Path> files = new ArrayList<>(walk.filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().endsWith(".yaml"))
          .toList());
      files.sort(null);
      return files;
    } catch (IOException e) {
      throw new IllegalStateException("cannot list " + dir, e);
    }
  }

  /**
   * Lists the directories directly below a directory, in name order (compile cases, VEC-3).
   *
   * @param dir the directory
   * @return the child directories
   */
  static List<Path> childDirectories(Path dir) {
    if (!Files.isDirectory(dir)) {
      return List.of();
    }
    try (Stream<Path> list = Files.list(dir)) {
      List<Path> dirs = new ArrayList<>(list.filter(Files::isDirectory).toList());
      dirs.sort(null);
      return dirs;
    } catch (IOException e) {
      throw new IllegalStateException("cannot list " + dir, e);
    }
  }

  /**
   * Returns a vector's identifier: the path relative to a root, without {@code .yaml}, with {@code /}
   * separators (VEC-12).
   *
   * @param root the family directory, such as {@code evaluate/}
   * @param file the vector file
   * @return the identifier
   */
  static String idOf(Path root, Path file) {
    String relative = root.relativize(file).toString().replace('\\', '/');
    return relative.endsWith(".yaml") ? relative.substring(0, relative.length() - ".yaml".length()) : relative;
  }

  /**
   * Loads one YAML file under the rules of VEC-6 to VEC-8.
   *
   * @param file the file
   * @return the single document
   * @throws RunnerFailure if the file is not valid UTF-8, starts with a byte-order mark, is not well-formed
   *     YAML 1.2, repeats a mapping key or holds several documents
   */
  static Object loadYaml(Path file) {
    byte[] bytes;
    try {
      bytes = Files.readAllBytes(file);
    } catch (IOException e) {
      throw new RunnerFailure("cannot read " + file, e);
    }
    return parseYaml(bytes, file.toString());
  }

  /**
   * Parses YAML bytes under the rules of VEC-6 to VEC-8.
   *
   * @param bytes the file content
   * @param label a name for error messages
   * @return the single document
   */
  static Object parseYaml(byte[] bytes, String label) {
    if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
      throw new RunnerFailure(label + " begins with a byte-order mark (E_PARSE)");
    }
    String text;
    try {
      text = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      throw new RunnerFailure(label + " is not valid UTF-8 (E_PARSE)", e);
    }
    LoadSettings settings = LoadSettings.builder()
        .setSchema(new CoreSchema())
        .setAllowDuplicateKeys(false)
        .setAllowRecursiveKeys(false)
        .setMaxAliasesForCollections(0)
        .setCodePointLimit(64 * 1024 * 1024)
        .setLabel(label)
        .build();
    try {
      Object document = new Load(settings).loadFromString(text);
      new Composer(settings, new ParserImpl(settings, new StreamReader(settings, text))).getSingleNode()
          .ifPresent(node -> rejectYamlFeatures(node, label));
      return document;
    } catch (RuntimeException e) {
      if (e instanceof RunnerFailure failure) {
        throw failure;
      }
      throw new RunnerFailure(label + " is not valid YAML for a vector: " + e.getMessage(), e);
    }
  }

  /** Rejects anchors, aliases and merge keys (VEC-7, VEC-8). */
  private static void rejectYamlFeatures(Node node, String label) {
    if (node.getAnchor().isPresent() || node.getNodeType() == NodeType.ANCHOR) {
      throw new RunnerFailure(label + " uses an anchor or an alias (VEC-7, VEC-8)");
    }
    if (node instanceof MappingNode mapping) {
      for (NodeTuple tuple : mapping.getValue()) {
        if (tuple.getKeyNode() instanceof ScalarNode key && key.isPlain() && key.getValue().equals("<<")) {
          throw new RunnerFailure(label + " uses a merge key (VEC-7)");
        }
        rejectYamlFeatures(tuple.getKeyNode(), label);
        rejectYamlFeatures(tuple.getValueNode(), label);
      }
    } else if (node instanceof SequenceNode sequence) {
      sequence.getValue().forEach(item -> rejectYamlFeatures(item, label));
    }
  }

  /**
   * Loads a YAML file whose document must be a mapping.
   *
   * @param file the file
   * @return the mapping
   * @throws RunnerFailure if the document is not a mapping with string keys
   */
  static Map<String, Object> loadMapping(Path file) {
    return asStringMap(loadYaml(file), file.toString());
  }

  /**
   * Casts a value to a mapping with string keys (VEC-36).
   *
   * @param value the value
   * @param what a description for error messages
   * @return the mapping
   * @throws RunnerFailure if the value is not a mapping or has a key that is not a string
   */
  @SuppressWarnings("unchecked")
  static Map<String, Object> asStringMap(Object value, String what) {
    if (!(value instanceof Map<?, ?> map)) {
      throw new RunnerFailure(what + " is not a mapping");
    }
    for (Object key : map.keySet()) {
      if (!(key instanceof String)) {
        throw new RunnerFailure(what + " has a key that is not a string scalar: " + key + " (quote it, VEC-36)");
      }
    }
    return (Map<String, Object>) map;
  }
}
