package io.github.visplabs.tierwise.core.internal.yaml;

import io.github.visplabs.tierwise.core.Code;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.common.Anchor;
import org.snakeyaml.engine.v2.common.SpecVersion;
import org.snakeyaml.engine.v2.events.AliasEvent;
import org.snakeyaml.engine.v2.events.DocumentStartEvent;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.MappingStartEvent;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.events.SequenceStartEvent;
import org.snakeyaml.engine.v2.parser.Parser;
import org.snakeyaml.engine.v2.parser.ParserImpl;
import org.snakeyaml.engine.v2.scanner.StreamReader;

/**
 * Builds the node tree of one YAML source from its parser events, and collects the findings of the
 * nodes as it goes.
 *
 * <p>The reader uses the event stream of the parser rather than a composed node graph. The composed
 * graph does not keep whether a tag was written, and it does not keep where an alias was written, and
 * SRC-2 and SRC-7 need both. The nodes take their positions as follows (FIND-19, FIND-22). The root
 * has the empty path and line 1. A mapping value begins on the line of its key. A block sequence item
 * begins on the line of its {@code -} indicator, and a flow item on its first character.
 *
 * <p>A file-level fault ends the read with that fault alone (see {@link YamlFault}).
 */
final class YamlNodeReader {

  private static final LoadSettings SETTINGS = LoadSettings.builder()
      .setLabel("source")
      .setUseMarks(true)
      .setCodePointLimit(Integer.MAX_VALUE)
      .build();

  /** The path segment of the value of a key that is not a scalar, which the tree does not keep. */
  private static final String COMPLEX_KEY = "?";

  /**
   * The greatest nesting depth of a source, counted in nodes from the root, which is depth 1. A source
   * that nests deeper is a file-level fault (SRC-5), so the reader never recurses without bound. A
   * complex key is read one level deeper, and it does not lengthen the path, so the depth counts the
   * nodes that are read rather than the path segments. The limit is far above the depth of any schema.
   */
  static final int MAX_DEPTH = 100;

  private final String file;
  private final YamlCodes codes;
  private final YamlPositions positions;
  private final Parser parser;
  private final YamlFindings findings;
  private final YamlScalars scalars;
  private final AnchorTable anchors = new AnchorTable(MAX_DEPTH);
  private final Predicate<List<Object>> nfcKeysAt;
  private int nesting;

  /**
   * Creates the reader of one source.
   *
   * @param file the source path relative to the project root
   * @param text the text of the source
   * @param codes the codes for faults of the source
   * @param nfcKeysAt tells whether the keys of the mapping at a path are NFC-normalised
   */
  YamlNodeReader(String file, String text, YamlCodes codes, Predicate<List<Object>> nfcKeysAt) {
    this.file = file;
    this.codes = codes;
    this.nfcKeysAt = nfcKeysAt;
    this.positions = new YamlPositions(text);
    this.parser = new ParserImpl(SETTINGS, new StreamReader(SETTINGS, text));
    this.findings = new YamlFindings(file);
    this.scalars = new YamlScalars(file, codes, findings);
  }

  /**
   * Reads the one document of the source.
   *
   * @return the root mapping, when the source is one document with a mapping root, and the findings
   * @throws YamlFault for a file-level fault: no document, more than one document, a root that is not
   *     a mapping, or a YAML version other than 1.2 (SRC-1, SRC-6, MANIFEST-2)
   */
  YamlLoadResult read() {
    parser.next(); // StreamStart
    if (parser.checkEvent(Event.ID.StreamEnd)) {
      throw new YamlFault(codes.shape(), "the source holds no YAML document (SRC-6)");
    }
    if (parser.next() instanceof DocumentStartEvent document) {
      checkVersion(document);
    }
    if (!parser.checkEvent(Event.ID.MappingStart)) {
      throw new YamlFault(codes.shape(), "the root node must be a mapping (SRC-6)");
    }
    Optional<PMap> root = readNode(List.of(), 1).filter(PMap.class::isInstance).map(PMap.class::cast);
    parser.next(); // DocumentEnd
    if (parser.checkEvent(Event.ID.DocumentStart)) {
      throw new YamlFault(codes.shape(), "the source holds more than one YAML document (SRC-6)");
    }
    return new YamlLoadResult(root, findings.toList());
  }

  private void checkVersion(DocumentStartEvent document) {
    Optional<SpecVersion> version = document.getSpecVersion();
    if (version.isPresent() && (version.get().getMajor() != 1 || version.get().getMinor() != 2)) {
      throw new YamlFault(codes.shape(), "the YAML version must be 1.2 (SRC-1)");
    }
  }

  private Optional<PNode> readNode(List<Object> path, int line) {
    if (nesting >= MAX_DEPTH) {
      throw new YamlFault(codes.shape(), "the source is nested too deeply (SRC-5)");
    }
    nesting++;
    try {
      return readContent(path, line);
    } finally {
      nesting--;
    }
  }

  private Optional<PNode> readContent(List<Object> path, int line) {
    Event event = parser.next();
    if (event instanceof ScalarEvent scalar) {
      return Optional.of(readScalarNode(scalar, path, line));
    }
    if (event instanceof AliasEvent alias) {
      return readAlias(alias, path, line);
    }
    if (event instanceof MappingStartEvent mapping) {
      checkProperties(mapping.getTag(), mapping.getAnchor(), path, line);
      PMap map = readMapping(path, line);
      defineAnchor(mapping.getAnchor(), map);
      return Optional.of(map);
    }
    if (event instanceof SequenceStartEvent sequence) {
      checkProperties(sequence.getTag(), sequence.getAnchor(), path, line);
      PSeq items = readSequence(!sequence.isFlow(), path, line);
      defineAnchor(sequence.getAnchor(), items);
      return Optional.of(items);
    }
    throw new IllegalStateException("unexpected event " + event.getEventId());
  }

  private PScalar readScalarNode(ScalarEvent scalar, List<Object> path, int line) {
    checkProperties(scalar.getTag(), scalar.getAnchor(), path, line);
    PScalar node = scalars.build(scalar, path, line);
    defineAnchor(scalar.getAnchor(), node);
    return node;
  }

  private Optional<PNode> readAlias(AliasEvent alias, List<Object> path, int line) {
    findings.add(Code.E_YAML_FEATURE, path, line, "an alias is not allowed (SRC-2)");
    return anchors.copy(alias.getAlias().getValue(), path, line);
  }

  private void checkProperties(Optional<String> tag, Optional<Anchor> anchor, List<Object> path, int line) {
    tag.ifPresent(name -> findings.add(
        Code.E_YAML_FEATURE, path, line, "the explicit tag " + name + " is not allowed (SRC-7)"));
    if (anchor.isPresent()) {
      findings.add(Code.E_YAML_FEATURE, path, line, "an anchor is not allowed (SRC-7)");
    }
  }

  private void defineAnchor(Optional<Anchor> anchor, PNode node) {
    anchor.ifPresent(name -> anchors.define(name.getValue(), node));
  }

  private PMap readMapping(List<Object> path, int line) {
    Map<String, PNode> entries = new LinkedHashMap<>();
    Set<String> seen = new HashSet<>();
    boolean nfc = nfcKeysAt.test(path);
    while (!parser.checkEvent(Event.ID.MappingEnd)) {
      readEntry(path, line, nfc, entries, seen);
    }
    parser.next(); // MappingEnd
    return new PMap(file, path, line, entries);
  }

  private void readEntry(
      List<Object> path, int mappingLine, boolean nfc, Map<String, PNode> entries, Set<String> seen) {
    Event keyEvent = parser.peekEvent();
    int keyLine = lineOf(keyEvent, mappingLine);
    if (!(keyEvent instanceof ScalarEvent key)) {
      readComplexKey(path, keyLine, keyEvent instanceof AliasEvent);
      return;
    }
    parser.next(); // key
    String text = key.getValue();
    String name = nfc ? Normalizer.normalize(text, Normalizer.Form.NFC) : text;
    List<Object> keyPath = append(path, name);
    if (key.isPlain() && text.equals("<<")) {
      checkProperties(key.getTag(), key.getAnchor(), keyPath, keyLine);
      findings.add(Code.E_YAML_FEATURE, keyPath, keyLine, "a merge key is not allowed (SRC-2)");
      readNode(keyPath, keyLine);
      return;
    }
    PScalar keyNode = readScalarNode(key, keyPath, keyLine);
    if (keyNode.kind() != PScalar.Kind.STRING) {
      findings.add(codes.type(), keyPath, keyLine,
          "the key " + text + " is not a string; quote it (SRC-11)", Optional.of(YamlFindings.quoted(text)));
      readNode(keyPath, keyLine);
      return;
    }
    if (!seen.add(Normalizer.normalize(text, Normalizer.Form.NFC))) {
      findings.add(Code.E_DUPLICATE_KEY, keyPath, keyLine, "the key " + name + " is repeated (SRC-3)");
      readNode(keyPath, keyLine);
      return;
    }
    readNode(keyPath, keyLine).ifPresent(value -> entries.put(name, value));
  }

  /**
   * Reads an entry whose key is not a scalar. The entry is dropped, because a key of the tree is a
   * string (SRC-11); its findings are still reported.
   */
  private void readComplexKey(List<Object> path, int keyLine, boolean alias) {
    readNode(path, keyLine);
    if (!alias) {
      findings.add(codes.type(), path, keyLine, "a mapping key is not a string; quote it (SRC-11)");
    }
    readNode(append(path, COMPLEX_KEY), keyLine);
  }

  private PSeq readSequence(boolean block, List<Object> path, int line) {
    List<PNode> items = new ArrayList<>();
    int index = 0;
    while (!parser.checkEvent(Event.ID.SequenceEnd)) {
      Event item = parser.peekEvent();
      int itemLine = itemLine(item, block, line);
      readNode(append(path, index), itemLine).ifPresent(items::add);
      index++;
    }
    parser.next(); // SequenceEnd
    return new PSeq(file, path, line, items);
  }

  private int itemLine(Event item, boolean block, int fallback) {
    return item.getStartMark()
        .map(mark -> block ? positions.blockItemLine(mark.getIndex()) : positions.lineAt(mark.getIndex()))
        .orElse(fallback);
  }

  private int lineOf(Event event, int fallback) {
    return event.getStartMark().map(mark -> positions.lineAt(mark.getIndex())).orElse(fallback);
  }

  private static List<Object> append(List<Object> path, Object segment) {
    List<Object> extended = new ArrayList<>(path);
    extended.add(segment);
    return extended;
  }
}
