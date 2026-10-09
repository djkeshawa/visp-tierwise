package io.github.tierwise.core.internal.lint;

import io.github.tierwise.core.internal.project.Names;
import io.github.tierwise.core.internal.project.SourceDocument;
import io.github.tierwise.core.internal.project.SourceSet;
import io.github.tierwise.core.internal.yaml.PMap;
import io.github.tierwise.core.internal.yaml.PNode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The declarations of the schema and rules documents, with the document that holds each one, so that a
 * lint rule can locate its finding in the source (LINT-21, FIND-17, FIND-19).
 *
 * <p>The index reads the raw documents, presets included. A declaration that is not a mapping is left
 * out: the engine checks report it, and the lint stage runs only when there is no engine finding.
 */
public final class SourceIndex {

  /**
   * A named declaration of a section, such as one key or one fact.
   *
   * @param name the name as the mapping writes it
   * @param document the document that declares it
   * @param node the value node of the entry, which locates findings about the declaration
   */
  public record Entry(String name, SourceDocument document, PMap node) {
  }

  /**
   * One definition of a kind or a naming pattern in one layer (MANIFEST-31, MANIFEST-32).
   *
   * @param name the name of the definition
   * @param layer the layer of the document that holds it
   * @param file the file of that document, which {@code presets/override} names
   * @param node the value node of the entry
   */
  public record Definition(String name, int layer, String file, PMap node) {
  }

  private final List<SourceDocument> schemas;
  private final List<SourceDocument> rules;
  private final List<Entry> keys = new ArrayList<>();
  private final List<Entry> facts = new ArrayList<>();
  private final List<Entry> features = new ArrayList<>();
  private final List<Definition> namingDefinitions = new ArrayList<>();
  private final List<Definition> kindDefinitions = new ArrayList<>();
  private final Map<String, Definition> naming;
  private final Map<String, Definition> kinds;
  private final Map<String, SourceDocument> rulesByFeature = new LinkedHashMap<>();

  private SourceIndex(SourceSet sources) {
    this.schemas = sources.schemas();
    this.rules = sources.rules();
    for (SourceDocument document : schemas) {
      collectEntries(document, "keys", keys);
      collectEntries(document, "facts", facts);
      collectEntries(document, "features", features);
      collectDefinitions(document, "naming", namingDefinitions);
      collectDefinitions(document, "kinds", kindDefinitions);
    }
    this.naming = effective(namingDefinitions);
    this.kinds = effective(kindDefinitions);
    for (SourceDocument document : rules) {
      Optional<String> feature = document.root().get("feature").flatMap(Names::text);
      feature.ifPresent(name -> rulesByFeature.putIfAbsent(name, document));
    }
  }

  /**
   * Indexes the declarations of a loaded project.
   *
   * @param sources the loaded sources
   * @return the index
   */
  public static SourceIndex of(SourceSet sources) {
    return new SourceIndex(sources);
  }

  /**
   * Returns the schema documents, presets included, in definition order.
   *
   * @return the documents
   */
  public List<SourceDocument> schemas() {
    return Collections.unmodifiableList(schemas);
  }

  /**
   * Returns the rules documents in definition order.
   *
   * @return the documents
   */
  public List<SourceDocument> rules() {
    return Collections.unmodifiableList(rules);
  }

  /**
   * Returns the key declarations of every schema document.
   *
   * @return the entries in definition order
   */
  public List<Entry> keys() {
    return Collections.unmodifiableList(keys);
  }

  /**
   * Returns the fact declarations of every schema document.
   *
   * @return the entries in definition order
   */
  public List<Entry> facts() {
    return Collections.unmodifiableList(facts);
  }

  /**
   * Returns the feature declarations of every schema document.
   *
   * @return the entries in definition order
   */
  public List<Entry> features() {
    return Collections.unmodifiableList(features);
  }

  /**
   * Returns every naming definition, in layer order, for {@code presets/override}.
   *
   * @return the definitions
   */
  public List<Definition> namingDefinitions() {
    return Collections.unmodifiableList(namingDefinitions);
  }

  /**
   * Returns every kind definition, in layer order, for {@code presets/override}.
   *
   * @return the definitions
   */
  public List<Definition> kindDefinitions() {
    return Collections.unmodifiableList(kindDefinitions);
  }

  /**
   * Returns the naming patterns in force: for each name, the last definition, which replaces the others
   * (MANIFEST-32).
   *
   * @return the definitions by pattern name
   */
  public Map<String, Definition> naming() {
    return Collections.unmodifiableMap(naming);
  }

  /**
   * Returns the kinds in force: for each name, the last definition (MANIFEST-32).
   *
   * @return the definitions by kind name
   */
  public Map<String, Definition> kinds() {
    return Collections.unmodifiableMap(kinds);
  }

  /**
   * Returns the rules document of a feature.
   *
   * @param feature the feature name
   * @return the document, or empty when no rules document names the feature
   */
  public Optional<SourceDocument> rulesOf(String feature) {
    return Optional.ofNullable(rulesByFeature.get(feature));
  }

  /**
   * Reads a text member of a mapping.
   *
   * @param map the mapping
   * @param key the member name
   * @return the text, or empty when the member is absent or not a string
   */
  public static Optional<String> text(PMap map, String key) {
    return map.get(key).flatMap(Names::text);
  }

  private static void collectEntries(SourceDocument document, String section, List<Entry> into) {
    Optional<PNode> node = document.root().get(section);
    if (node.isPresent() && node.get() instanceof PMap map) {
      for (Map.Entry<String, PNode> entry : map.entries().entrySet()) {
        if (entry.getValue() instanceof PMap declaration) {
          into.add(new Entry(entry.getKey(), document, declaration));
        }
      }
    }
  }

  private static void collectDefinitions(SourceDocument document, String section, List<Definition> into) {
    Optional<PNode> node = document.root().get(section);
    if (node.isPresent() && node.get() instanceof PMap map) {
      for (Map.Entry<String, PNode> entry : map.entries().entrySet()) {
        if (entry.getValue() instanceof PMap definition) {
          into.add(new Definition(entry.getKey(), document.layer(), document.file(), definition));
        }
      }
    }
  }

  private static Map<String, Definition> effective(List<Definition> definitions) {
    Map<String, Definition> last = new LinkedHashMap<>();
    for (Definition definition : definitions) {
      last.put(definition.name(), definition);
    }
    return last;
  }
}
