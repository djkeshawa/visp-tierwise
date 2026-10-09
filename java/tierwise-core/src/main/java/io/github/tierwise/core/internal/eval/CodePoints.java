package io.github.tierwise.core.internal.eval;

import java.util.Comparator;

/** String helpers that follow Unicode code points rather than UTF-16 code units (NORM-6, DET-5). */
final class CodePoints {

  /** Orders strings by code point, as the specification orders fact names (RULE-8, RULE-44). */
  static final Comparator<String> ORDER = CodePoints::compare;

  private CodePoints() {
  }

  /**
   * Compares two strings code point by code point.
   *
   * @param a the first string
   * @param b the second string
   * @return a negative number, zero or a positive number as {@code a} is before, equal to or after
   *     {@code b}
   */
  static int compare(String a, String b) {
    int i = 0;
    int j = 0;
    while (i < a.length() && j < b.length()) {
      int ca = a.codePointAt(i);
      int cb = b.codePointAt(j);
      if (ca != cb) {
        return Integer.compare(ca, cb);
      }
      i += Character.charCount(ca);
      j += Character.charCount(cb);
    }
    return Integer.compare(a.length() - i, b.length() - j);
  }

  /**
   * Tells whether a string holds an unpaired surrogate code unit (NORM-8, SCOPE-37).
   *
   * @param text the string
   * @return {@code true} when a high surrogate is not followed by a low one, or a low surrogate is not
   *     preceded by a high one
   */
  static boolean hasUnpairedSurrogate(String text) {
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (Character.isHighSurrogate(c)) {
        if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
          i++;
        } else {
          return true;
        }
      } else if (Character.isLowSurrogate(c)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Counts the code points of a string; an unpaired surrogate counts as one.
   *
   * @param text the string
   * @return the number of code points
   */
  static int count(String text) {
    return text.codePointCount(0, text.length());
  }
}
