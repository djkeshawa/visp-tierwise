package io.github.visplabs.tierwise.core.internal.project;

import java.util.Comparator;

/** Orders strings by Unicode code point, not by UTF-16 code unit (FIND-26, FIND-32). */
public final class CodePointOrder {

  /** The comparator. */
  public static final Comparator<String> ORDER = CodePointOrder::compare;

  private CodePointOrder() {
  }

  /**
   * Compares two strings code point by code point.
   *
   * @param a the first string
   * @param b the second string
   * @return a negative number, zero or a positive number as {@code a} is before, equal to or after {@code b}
   */
  public static int compare(String a, String b) {
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
}
