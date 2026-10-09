package io.github.tierwise.cli.codegen;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The naming rules of the Java output. Each rule is a pure function of the declared name, so the same
 * project always yields the same identifiers, and two declared names that map to one identifier are
 * reported instead of being merged.
 *
 * <ul>
 *   <li>A key or feature constant: each ASCII letter is upper-cased, a lower-case letter or digit that
 *       meets an upper-case letter gets a separator in front of it, and every other character becomes a
 *       separator {@code _}. A name that starts with a digit gets a leading {@code _}.
 *   <li>An effect constant follows the same rule, applied to the declared effect.
 *   <li>A feature with an enum form gets the enum type {@code <Segments>Effect}: the ASCII letter and
 *       digit runs of the feature name, each with an upper-case first letter.
 * </ul>
 *
 * <p>A declared name with no ASCII letter or digit has no identifier and is an error.
 */
final class Identifiers {

  /** The reserved words of Java, which a package segment may not be. */
  private static final Set<String> JAVA_KEYWORDS = Set.of(
      "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
      "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
      "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
      "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
      "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
      "volatile", "while", "true", "false", "null", "_");

  private Identifiers() {
  }

  /**
   * Derives the constant name of a declared key, feature or effect.
   *
   * @param declared the declared name
   * @return an upper-case Java identifier
   * @throws CodegenException if the name has no ASCII letter or digit
   */
  static String constantName(String declared) {
    StringBuilder out = new StringBuilder(declared.length() + 4);
    char previous = '\0';
    for (int i = 0; i < declared.length(); i++) {
      char c = declared.charAt(i);
      if (isAsciiLetterOrDigit(c)) {
        if (isAsciiUpper(c) && (isAsciiLower(previous) || isAsciiDigit(previous))) {
          out.append('_');
        }
        out.append(toAsciiUpper(c));
      } else {
        out.append('_');
      }
      previous = c;
    }
    return withLeadingLetter(declared, out.toString());
  }

  /**
   * Derives the enum type name of a feature with an enum form.
   *
   * @param feature the declared feature name
   * @return a Java identifier that ends with {@code Effect}
   * @throws CodegenException if the name has no ASCII letter or digit
   */
  static String enumTypeName(String feature) {
    StringBuilder out = new StringBuilder(feature.length() + 6);
    boolean segmentStart = true;
    for (int i = 0; i < feature.length(); i++) {
      char c = feature.charAt(i);
      if (isAsciiLetterOrDigit(c)) {
        out.append(segmentStart ? toAsciiUpper(c) : c);
        segmentStart = false;
      } else {
        segmentStart = true;
      }
    }
    return withLeadingLetter(feature, out.toString()) + "Effect";
  }

  /**
   * Checks that a package name is a Java package name.
   *
   * @param javaPackage the package name, dot-separated
   * @throws IllegalArgumentException if a segment is not an identifier or is a reserved word
   */
  static void checkPackage(String javaPackage) {
    for (String segment : javaPackage.split("\\.", -1)) {
      if (!segment.matches("[A-Za-z_][A-Za-z0-9_]*") || JAVA_KEYWORDS.contains(segment)) {
        throw new IllegalArgumentException("not a Java package name: " + javaPackage);
      }
    }
  }

  /**
   * Records that a declared name owns a generated identifier, and fails if another declared name of the
   * same kind already owns it.
   *
   * @param owners the owner of each identifier claimed so far in one namespace: identifier to declared name
   * @param identifier the identifier
   * @param declared the declared name
   * @param kind what the names are, for the message
   * @throws CodegenException if another declared name owns the identifier
   */
  static void claim(Map<String, String> owners, String identifier, String declared, String kind) {
    String owner = owners.putIfAbsent(identifier, declared);
    if (owner != null) {
      throw new CodegenException(
          "the " + kind + " names \"" + owner + "\" and \"" + declared + "\" both map to the Java name "
              + identifier + "; rename one of them",
          List.of());
    }
  }

  private static String withLeadingLetter(String declared, String identifier) {
    if (!hasAsciiLetterOrDigit(identifier)) {
      throw new CodegenException(
          "the name \"" + declared + "\" has no ASCII letter or digit, so it has no Java name; rename it",
          List.of());
    }
    return isAsciiDigit(identifier.charAt(0)) ? "_" + identifier : identifier;
  }

  private static boolean hasAsciiLetterOrDigit(String text) {
    for (int i = 0; i < text.length(); i++) {
      if (isAsciiLetterOrDigit(text.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  private static boolean isAsciiUpper(char c) {
    return c >= 'A' && c <= 'Z';
  }

  private static boolean isAsciiLower(char c) {
    return c >= 'a' && c <= 'z';
  }

  private static boolean isAsciiDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isAsciiLetterOrDigit(char c) {
    return isAsciiUpper(c) || isAsciiLower(c) || isAsciiDigit(c);
  }

  private static char toAsciiUpper(char c) {
    return isAsciiLower(c) ? (char) (c - 'a' + 'A') : c;
  }
}
