package io.github.tierwise.cli.codegen;

/**
 * Quotes declared names as string literals that are valid in both Java and TypeScript.
 *
 * <p>Printable ASCII is copied, except that the quote and the backslash are escaped. The characters
 * backspace, tab, line feed, form feed and carriage return use their short escapes. Every other
 * character becomes a {@code uXXXX} escape of its UTF-16 unit. Java processes unicode escapes before it
 * scans a literal, so the control characters that end a line are never written as unicode escapes.
 */
final class Literals {

  private Literals() {
  }

  /**
   * Returns the double-quoted literal for a string.
   *
   * @param text the string
   * @return the literal, including its quotes
   */
  static String quote(String text) {
    StringBuilder out = new StringBuilder(text.length() + 2).append('"');
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\t' -> out.append("\\t");
        case '\n' -> out.append("\\n");
        case '\f' -> out.append("\\f");
        case '\r' -> out.append("\\r");
        default -> {
          if (c >= 0x20 && c <= 0x7e) {
            out.append(c);
          } else {
            out.append(String.format("\\u%04x", (int) c));
          }
        }
      }
    }
    return out.append('"').toString();
  }
}
