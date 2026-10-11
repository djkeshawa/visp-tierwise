package io.github.visplabs.tierwise.core.internal.project;

import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import java.util.List;
import java.util.Optional;

/**
 * The parts of a manifest that passed their shape checks (section 1.1, 1.3, 1.5, 1.7, 1.8).
 *
 * <p>A part whose shape was wrong is reported by {@link ManifestReader} and is absent here, so the later
 * steps never see it.
 *
 * @param root the manifest root
 * @param extendsItems the well-formed items of {@code extends}, in order
 * @param schemaGlobs the glob patterns of {@code sources.schema}
 * @param ruleGlobs the glob patterns of {@code sources.rules}
 * @param valuesEntries the well-formed items of {@code sources.values}
 * @param inlineSchema the inline {@code schema} content
 * @param inlineValues the inline {@code values} content
 * @param inlineRules the items of inline {@code rules}, each a mapping
 * @param namespaces the well-formed entries of {@code namespaces}
 * @param lint the {@code lint} node, checked by {@link LintSettingsReader}
 */
record Manifest(
    PMap root,
    List<ExtendsItem> extendsItems,
    List<Glob> schemaGlobs,
    List<Glob> ruleGlobs,
    List<ValuesEntry> valuesEntries,
    Optional<PMap> inlineSchema,
    Optional<PMap> inlineValues,
    List<PMap> inlineRules,
    List<NamespaceEntry> namespaces,
    Optional<PNode> lint) {

  /** The three forms of a preset reference (MANIFEST-21, MANIFEST-23, MANIFEST-25). */
  enum PresetKind {
    /** {@code tierwise:<name>}. */
    BUILT_IN,
    /** A relative path. */
    PATH,
    /** A mapping with {@code url} and {@code sha256}. */
    URL
  }

  /**
   * An item of {@code extends}.
   *
   * @param index the position in the list
   * @param node the item node, where a finding about the reference is located (FIND-23)
   * @param kind the form
   * @param text the built-in name, the path, or the URL
   * @param sha256 the pinned digest of a URL preset, otherwise empty
   */
  record ExtendsItem(int index, PNode node, PresetKind kind, String text, String sha256) {
  }

  /**
   * A glob pattern.
   *
   * @param node the list item, where a finding about the pattern is located
   * @param pattern the pattern text
   */
  record Glob(PNode node, String pattern) {
  }

  /** The shapes of a {@code sources.values} item (SRC-21). */
  enum ValuesKind {
    /** {@code { pattern: ... }}. */
    PATH_MAPPED,
    /** {@code { file: ..., scope: ... }}. */
    ROOT,
    /** {@code { file: ... }}. */
    MULTI_NODE
  }

  /**
   * An item of {@code sources.values}.
   *
   * @param node the item, where a finding about its selection is located
   * @param kind the shape
   * @param locator the pattern member of a path-mapped entry, otherwise the file member
   * @param text the pattern or the file path
   * @param scope the {@code scope} of a root entry, otherwise empty
   * @param scopeNode the {@code scope} member of a root entry, otherwise {@code null}
   * @param pattern the parsed pattern of a path-mapped entry, otherwise {@code null}
   */
  record ValuesEntry(PMap node, ValuesKind kind, PNode locator, String text, Optional<String> scope,
      PNode scopeNode, PathPattern pattern) {
  }

  /**
   * An entry of {@code namespaces}.
   *
   * @param name the namespace name
   * @param node the entry, where a finding about the entry is located
   * @param schemaNode the {@code schema} member
   * @param schemaPath the path of the schema file
   */
  record NamespaceEntry(String name, PNode node, PNode schemaNode, String schemaPath) {
  }
}
