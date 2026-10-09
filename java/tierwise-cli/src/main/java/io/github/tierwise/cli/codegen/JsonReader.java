package io.github.tierwise.cli.codegen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small reader for the JSON that a canonical bundle uses. It returns maps (in document order), lists,
 * strings, {@code Long} integers, booleans and {@code null}.
 *
 * <p>The reader is strict in the ways that matter here: a repeated member name is an error, and so is a
 * number that is not an integer. Codegen reads bundles that the core has just compiled, so a failure
 * means a defect, and it is reported as {@link IllegalStateException}.
 */
final class JsonReader {

  private final String text;
  private int pos;

  private JsonReader(String text) {
    this.text = text;
  }

  /**
   * Parses one JSON document.
   *
   * @param text the document
   * @return the value
   * @throws IllegalStateException if the text is not a document of the bundle subset
   */
  static Object parse(String text) {
    JsonReader reader = new JsonReader(text);
    Object value = reader.value();
    reader.skipSpace();
    if (reader.pos != text.length()) {
      throw reader.fail("text after the document");
    }
    return value;
  }

  private Object value() {
    skipSpace();
    if (pos >= text.length()) {
      throw fail("the document ends too early");
    }
    char c = text.charAt(pos);
    return switch (c) {
      case '{' -> object();
      case '[' -> array();
      case '"' -> string();
      case 't' -> literal("true", Boolean.TRUE);
      case 'f' -> literal("false", Boolean.FALSE);
      case 'n' -> literal("null", null);
      default -> integer();
    };
  }

  private Map<String, Object> object() {
    Map<String, Object> members = new LinkedHashMap<>();
    pos++;
    skipSpace();
    if (peek('}')) {
      pos++;
      return members;
    }
    while (true) {
      skipSpace();
      if (!peek('"')) {
        throw fail("a member name is expected");
      }
      String name = string();
      skipSpace();
      expect(':');
      if (members.put(name, value()) != null) {
        throw fail("the member " + name + " is repeated");
      }
      skipSpace();
      if (peek(',')) {
        pos++;
      } else {
        expect('}');
        return members;
      }
    }
  }

  private List<Object> array() {
    List<Object> elements = new ArrayList<>();
    pos++;
    skipSpace();
    if (peek(']')) {
      pos++;
      return elements;
    }
    while (true) {
      elements.add(value());
      skipSpace();
      if (peek(',')) {
        pos++;
      } else {
        expect(']');
        return elements;
      }
    }
  }

  private String string() {
    expect('"');
    StringBuilder out = new StringBuilder();
    while (true) {
      if (pos >= text.length()) {
        throw fail("the string is not closed");
      }
      char c = text.charAt(pos++);
      if (c == '"') {
        return out.toString();
      }
      if (c != '\\') {
        out.append(c);
        continue;
      }
      if (pos >= text.length()) {
        throw fail("the escape is not complete");
      }
      char escape = text.charAt(pos++);
      switch (escape) {
        case '"', '\\', '/' -> out.append(escape);
        case 'b' -> out.append('\b');
        case 'f' -> out.append('\f');
        case 'n' -> out.append('\n');
        case 'r' -> out.append('\r');
        case 't' -> out.append('\t');
        case 'u' -> out.append(hexUnit());
        default -> throw fail("an unknown escape");
      }
    }
  }

  private char hexUnit() {
    if (pos + 4 > text.length()) {
      throw fail("the unicode escape is not complete");
    }
    try {
      char unit = (char) Integer.parseInt(text.substring(pos, pos + 4), 16);
      pos += 4;
      return unit;
    } catch (NumberFormatException e) {
      throw fail("the unicode escape is not hexadecimal");
    }
  }

  private Long integer() {
    int start = pos;
    if (peek('-')) {
      pos++;
    }
    int digits = pos;
    while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
      pos++;
    }
    if (pos == digits) {
      throw fail("a value is expected");
    }
    if (pos < text.length() && (text.charAt(pos) == '.' || text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
      throw fail("a number that is not an integer");
    }
    try {
      return Long.parseLong(text.substring(start, pos));
    } catch (NumberFormatException e) {
      throw fail("an integer out of range");
    }
  }

  private Object literal(String word, Object value) {
    if (!text.startsWith(word, pos)) {
      throw fail("a value is expected");
    }
    pos += word.length();
    return value;
  }

  private void skipSpace() {
    while (pos < text.length()) {
      char c = text.charAt(pos);
      if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
        return;
      }
      pos++;
    }
  }

  private boolean peek(char expected) {
    return pos < text.length() && text.charAt(pos) == expected;
  }

  private void expect(char expected) {
    if (!peek(expected)) {
      throw fail("'" + expected + "' is expected");
    }
    pos++;
  }

  private IllegalStateException fail(String reason) {
    return new IllegalStateException("the bundle is not valid JSON at offset " + pos + ": " + reason);
  }
}
