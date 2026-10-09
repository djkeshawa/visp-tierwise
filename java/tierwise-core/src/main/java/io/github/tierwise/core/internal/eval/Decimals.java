package io.github.tierwise.core.internal.eval;

/** Runtime decimal syntax (TYPE-24) and canonical form (NORM-9, NORM-10). */
final class Decimals {

  private Decimals() {
  }

  /**
   * Tells whether a string matches {@code [+-]?[0-9]+(\.[0-9]+)?} in full, with ASCII digits only.
   *
   * @param text the string
   * @return {@code true} when it is a decimal value in the sense of TYPE-24
   */
  static boolean matches(String text) {
    int n = text.length();
    int i = 0;
    if (i < n && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
      i++;
    }
    int digits = i;
    while (i < n && isDigit(text.charAt(i))) {
      i++;
    }
    if (i == digits) {
      return false;
    }
    if (i == n) {
      return true;
    }
    if (text.charAt(i) != '.') {
      return false;
    }
    i++;
    int fraction = i;
    while (i < n && isDigit(text.charAt(i))) {
      i++;
    }
    return i > fraction && i == n;
  }

  /**
   * Canonicalises a decimal string with the steps of NORM-10.
   *
   * @param text a string for which {@link #matches(String)} holds
   * @return the canonical string: no {@code +}, no leading zeros, no trailing fraction zeros, and
   *     {@code 0} for every zero
   */
  static String canonical(String text) {
    int i = 0;
    boolean negative = false;
    if (text.charAt(0) == '+' || text.charAt(0) == '-') {
      negative = text.charAt(0) == '-';
      i = 1;
    }
    int dot = text.indexOf('.');
    String integer = dot < 0 ? text.substring(i) : text.substring(i, dot);
    String fraction = dot < 0 ? "" : text.substring(dot + 1);
    int start = 0;
    while (start < integer.length() - 1 && integer.charAt(start) == '0') {
      start++;
    }
    integer = integer.substring(start);
    int end = fraction.length();
    while (end > 0 && fraction.charAt(end - 1) == '0') {
      end--;
    }
    fraction = fraction.substring(0, end);
    String body = fraction.isEmpty() ? integer : integer + "." + fraction;
    return negative && !body.equals("0") ? "-" + body : body;
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }
}
