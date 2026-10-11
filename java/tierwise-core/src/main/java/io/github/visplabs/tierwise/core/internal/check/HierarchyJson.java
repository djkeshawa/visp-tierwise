package io.github.visplabs.tierwise.core.internal.check;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import io.github.visplabs.tierwise.core.Code;
import io.github.visplabs.tierwise.core.Finding;
import io.github.visplabs.tierwise.core.Severity;
import io.github.visplabs.tierwise.core.internal.yaml.PMap;
import io.github.visplabs.tierwise.core.internal.yaml.PNode;
import io.github.visplabs.tierwise.core.internal.yaml.PScalar;
import io.github.visplabs.tierwise.core.internal.yaml.PSeq;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a generated node catalog as a node tree with positions (SCOPE-29, SCOPE-30, FIND-19 to FIND-22).
 *
 * <p>The document must be one JSON value in UTF-8 without a byte-order mark. A file that breaks those rules,
 * or that is not well-formed JSON, is {@code E_PARSE} at the empty path and line 1 (FIND-23). A property name
 * that repeats in one object is {@code E_DUPLICATE_KEY} at its second occurrence (SRC-9, FIND-22); the first
 * value is kept. A structural fault of the catalog, such as an extra property, is not a parse fault and is
 * left to the caller.
 */
final class HierarchyJson {

  private static final JsonFactory FACTORY = new JsonFactory();

  private final String file;
  private final List<Finding> findings = new ArrayList<>();

  private HierarchyJson(String file) {
    this.file = file;
  }

  /**
   * The outcome of reading a catalog file.
   *
   * @param root the root node, empty when the document could not be parsed
   * @param findings the findings about the document, the duplicate property names included
   */
  record Read(Optional<PNode> root, List<Finding> findings) {

    /** Copies the findings. */
    Read {
      findings = List.copyOf(findings);
    }
  }

  /**
   * Reads one catalog file.
   *
   * @param file the file name as findings report it
   * @param bytes the bytes of the file
   * @return the root and the findings
   */
  static Read read(String file, byte[] bytes) {
    return new HierarchyJson(file).parse(bytes);
  }

  private Read parse(byte[] bytes) {
    if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
      return parseFault("the catalog starts with a byte-order mark (SCOPE-30)");
    }
    String text;
    try {
      text = StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      return parseFault("the catalog is not valid UTF-8 (SCOPE-30)");
    }
    try (JsonParser parser = FACTORY.createParser(text)) {
      if (parser.nextToken() == null) {
        return parseFault("the catalog is empty (SCOPE-30)");
      }
      PNode root = value(parser, List.of(), 1);
      if (parser.nextToken() != null) {
        return parseFault("data follows the catalog document (SCOPE-30)");
      }
      return new Read(Optional.of(root), findings);
    } catch (IOException | RuntimeException e) {
      return parseFault("the catalog is not well-formed JSON (SCOPE-30)");
    }
  }

  private Read parseFault(String message) {
    Finding finding = Finding.of(Code.E_PARSE, Severity.ERROR, file, List.of(), 1, message);
    return new Read(Optional.empty(), List.of(finding));
  }

  /** Reads the value whose first token is the current token; {@code line} is where the value begins. */
  private PNode value(JsonParser parser, List<Object> path, int line) throws IOException {
    JsonToken token = parser.currentToken();
    if (token == null) {
      throw new IOException("the document ends early");
    }
    switch (token) {
      case START_OBJECT:
        return object(parser, path, line);
      case START_ARRAY:
        return array(parser, path, line);
      case VALUE_STRING: {
        String text = parser.getText();
        return new PScalar(file, path, line, PScalar.Kind.STRING, text, text);
      }
      case VALUE_NUMBER_INT: {
        BigInteger number = parser.getBigIntegerValue();
        Object value = number.bitLength() < Long.SIZE ? (Object) number.longValue() : number;
        return new PScalar(file, path, line, PScalar.Kind.INTEGER, value, parser.getText());
      }
      case VALUE_NUMBER_FLOAT:
        return new PScalar(file, path, line, PScalar.Kind.FLOAT, Double.parseDouble(parser.getText()),
            parser.getText());
      case VALUE_TRUE:
        return new PScalar(file, path, line, PScalar.Kind.BOOLEAN, Boolean.TRUE, "true");
      case VALUE_FALSE:
        return new PScalar(file, path, line, PScalar.Kind.BOOLEAN, Boolean.FALSE, "false");
      case VALUE_NULL:
        return new PScalar(file, path, line, PScalar.Kind.NULL, null, "null");
      default:
        throw new IOException("unexpected token " + token);
    }
  }

  private PMap object(JsonParser parser, List<Object> path, int line) throws IOException {
    Map<String, PNode> entries = new LinkedHashMap<>();
    JsonToken token = parser.nextToken();
    while (token == JsonToken.FIELD_NAME) {
      String key = parser.currentName();
      int keyLine = tokenLine(parser);
      List<Object> childPath = append(path, key);
      parser.nextToken();
      PNode child = value(parser, childPath, keyLine);
      if (entries.containsKey(key)) {
        findings.add(Finding.of(Code.E_DUPLICATE_KEY, Severity.ERROR, file, childPath, keyLine,
            "the property " + key + " is repeated in one object (SRC-3, SCOPE-30)"));
      } else {
        entries.put(key, child);
      }
      token = parser.nextToken();
    }
    if (token != JsonToken.END_OBJECT) {
      throw new IOException("an object does not end");
    }
    return new PMap(file, path, line, entries);
  }

  private PSeq array(JsonParser parser, List<Object> path, int line) throws IOException {
    List<PNode> items = new ArrayList<>();
    int index = 0;
    JsonToken token = parser.nextToken();
    while (token != JsonToken.END_ARRAY) {
      if (token == null) {
        throw new IOException("an array does not end");
      }
      items.add(value(parser, append(path, index), tokenLine(parser)));
      index++;
      token = parser.nextToken();
    }
    return new PSeq(file, path, line, items);
  }

  private static int tokenLine(JsonParser parser) {
    return Math.max(1, parser.currentTokenLocation().getLineNr());
  }

  private static List<Object> append(List<Object> path, Object segment) {
    List<Object> extended = new ArrayList<>(path);
    extended.add(segment);
    return extended;
  }
}
