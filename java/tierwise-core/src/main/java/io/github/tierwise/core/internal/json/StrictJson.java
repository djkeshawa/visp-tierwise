package io.github.tierwise.core.internal.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
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
import java.util.regex.Pattern;

/**
 * The strict reader of bundle bytes (BUNDLE-111, BUNDLE-116). It turns a UTF-8 JSON document into plain
 * values: {@code Map<String, Object>} with insertion order, {@code List<Object>}, {@code String}, {@code Long},
 * {@code Boolean} and {@code null}.
 *
 * <p>It rejects a byte-order mark, invalid UTF-8, a member name that repeats in one object at any depth, a
 * number that is not an integer of the form {@code 0|-?[1-9][0-9]*} within +/-(2^53 - 1), a string with an
 * unpaired surrogate (also one written as an escape), and data after the document. It does not check the
 * exact byte form: a caller compares the bytes with {@link CanonicalJson} for that.
 */
public final class StrictJson {

  private static final Pattern INTEGER = Pattern.compile("0|-?[1-9][0-9]*");
  private static final BigInteger MAX_SAFE = BigInteger.valueOf(CanonicalJson.MAX_SAFE_INTEGER);
  private static final JsonFactory FACTORY = new JsonFactory();

  private StrictJson() {
  }

  /**
   * Parses one strict JSON document from UTF-8 bytes.
   *
   * @param bytes the bytes of the document
   * @return the plain value of the document
   * @throws JsonFormatException if the bytes are not an acceptable document
   */
  public static Object parse(byte[] bytes) {
    String text = decode(bytes);
    try (JsonParser parser = FACTORY.createParser(text)) {
      if (parser.nextToken() == null) {
        throw new JsonFormatException("the document is empty");
      }
      Object value = read(parser);
      if (parser.nextToken() != null) {
        throw new JsonFormatException("data follows the document (BUNDLE-4, BUNDLE-116)");
      }
      return value;
    } catch (IOException e) {
      throw new JsonFormatException("the document is not well-formed JSON: " + e.getMessage());
    }
  }

  private static String decode(byte[] bytes) {
    if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
      throw new JsonFormatException("the document begins with a byte-order mark (BUNDLE-3)");
    }
    try {
      return StandardCharsets.UTF_8.newDecoder()
          .onMalformedInput(CodingErrorAction.REPORT)
          .onUnmappableCharacter(CodingErrorAction.REPORT)
          .decode(ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      throw new JsonFormatException("the document is not valid UTF-8 (BUNDLE-116)");
    }
  }

  private static Object read(JsonParser parser) throws IOException {
    JsonToken token = parser.currentToken();
    if (token == null) {
      throw new JsonFormatException("unexpected end of the document");
    }
    switch (token) {
      case START_OBJECT:
        return readObject(parser);
      case START_ARRAY:
        return readArray(parser);
      case VALUE_STRING:
        return checkedString(parser.getText());
      case VALUE_NUMBER_INT:
        return integer(parser.getText());
      case VALUE_NUMBER_FLOAT:
        throw new JsonFormatException("a number that is not an integer (BUNDLE-11)");
      case VALUE_TRUE:
        return Boolean.TRUE;
      case VALUE_FALSE:
        return Boolean.FALSE;
      case VALUE_NULL:
        return null;
      default:
        throw new JsonFormatException("unexpected token " + token);
    }
  }

  private static Map<String, Object> readObject(JsonParser parser) throws IOException {
    Map<String, Object> members = new LinkedHashMap<>();
    while (true) {
      JsonToken token = parser.nextToken();
      if (token == JsonToken.END_OBJECT) {
        return members;
      }
      if (token != JsonToken.FIELD_NAME) {
        throw new JsonFormatException("a member name was expected");
      }
      String name = checkedString(parser.currentName());
      if (members.containsKey(name)) {
        throw new JsonFormatException("duplicate property name (BUNDLE-111): " + name);
      }
      parser.nextToken();
      members.put(name, read(parser));
    }
  }

  private static List<Object> readArray(JsonParser parser) throws IOException {
    List<Object> items = new ArrayList<>();
    while (true) {
      JsonToken token = parser.nextToken();
      if (token == JsonToken.END_ARRAY) {
        return items;
      }
      items.add(read(parser));
    }
  }

  private static String checkedString(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isHighSurrogate(c) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
        i++;
      } else if (Character.isSurrogate(c)) {
        throw new JsonFormatException("an unpaired surrogate (BUNDLE-13)");
      }
    }
    return text;
  }

  private static Long integer(String text) {
    if (!INTEGER.matcher(text).matches()) {
      throw new JsonFormatException("a number that is not an integer, or that has a leading zero or is -0 (BUNDLE-11)");
    }
    BigInteger value = new BigInteger(text);
    if (value.abs().compareTo(MAX_SAFE) > 0) {
      throw new JsonFormatException("an integer outside +/-(2^53 - 1) (BUNDLE-12)");
    }
    return value.longValue();
  }
}
