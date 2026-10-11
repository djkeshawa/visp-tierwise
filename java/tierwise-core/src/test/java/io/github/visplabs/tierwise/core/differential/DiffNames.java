package io.github.visplabs.tierwise.core.differential;

import java.util.Comparator;
import java.util.Random;

/**
 * Names and text for generated models.
 *
 * <p>The pools mix ASCII with characters outside the Basic Multilingual Plane. Code point order and UTF-16 order
 * disagree on some of their pairs, so a sort or a comparison that uses the wrong order gives a different result on
 * the two sides of the differential run. Every name the generator makes is ASCII; only node IDs, enum members,
 * set members and string values use the wider pool.
 */
final class DiffNames {

  /** The order of the bundle format for node IDs, enum members and other sorted strings (BUNDLE-75). */
  static final Comparator<String> CODE_POINT = DiffNames::compareCodePoints;

  private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz";

  private static final String[] WORDS = {
    "alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa", "lambda", "mu",
    "nu", "xi", "omicron", "pi", "rho", "sigma", "tau", "upsilon", "phi", "chi", "psi", "omega",
  };

  private static final String[] PIECES = {
    "A", "Z", "a", "z", "0", "9", " ", "-", "_", "/", ":", ".",
    "é", "Å", "ß", "Ω", "漢字", "Ａ", "�", "😀",
  };

  private DiffNames() {
  }

  /**
   * Compares two strings by code point, the order that BUNDLE-75 defines.
   *
   * @param a the first string
   * @param b the second string
   * @return a negative number, zero or a positive number as {@code a} sorts before, with or after {@code b}
   */
  static int compareCodePoints(String a, String b) {
    int i = 0;
    int j = 0;
    while (i < a.length() && j < b.length()) {
      int x = a.codePointAt(i);
      int y = b.codePointAt(j);
      if (x != y) {
        return Integer.compare(x, y);
      }
      i += Character.charCount(x);
      j += Character.charCount(y);
    }
    return Boolean.compare(i < a.length(), j < b.length());
  }

  /**
   * Returns a lower-case ASCII identifier, sometimes with a numeric suffix.
   *
   * @param r the random source
   * @return the identifier, never empty and never containing a dot
   */
  static String identifier(Random r) {
    int length = 3 + r.nextInt(6);
    StringBuilder out = new StringBuilder(length + 4);
    for (int i = 0; i < length; i++) {
      out.append(LETTERS.charAt(r.nextInt(LETTERS.length())));
    }
    if (r.nextInt(3) == 0) {
      out.append('_').append(r.nextInt(100));
    }
    return out.toString();
  }

  /**
   * Returns a dotted name of two identifiers, the shape of a key or a feature name.
   *
   * @param r the random source
   * @return the name
   */
  static String dotted(Random r) {
    return identifier(r) + "." + identifier(r);
  }

  /**
   * Returns a non-empty string for a value of a string type, an enum member, a set member or an effect name. It
   * is a plain word or one to three pieces, which may include characters outside the Basic Multilingual Plane.
   *
   * @param r the random source
   * @return the string
   */
  static String text(Random r) {
    if (r.nextBoolean()) {
      return WORDS[r.nextInt(WORDS.length)];
    }
    int count = 1 + r.nextInt(3);
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < count; i++) {
      out.append(PIECES[r.nextInt(PIECES.length)]);
    }
    return out.toString();
  }

  /**
   * Returns a node ID: non-empty, free of control characters and far below the 128 code point limit (SCOPE-33).
   *
   * @param r the random source
   * @return the node ID
   */
  static String nodeId(Random r) {
    StringBuilder out = new StringBuilder(text(r));
    if (r.nextBoolean()) {
      out.append(PIECES[r.nextInt(PIECES.length)]).append(identifier(r));
    }
    return out.toString();
  }
}
