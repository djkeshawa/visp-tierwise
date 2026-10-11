package io.github.visplabs.tierwise.core.internal.check;

import java.util.regex.Pattern;

/**
 * Text rules of section 4: decimal syntax and canonical form (TYPE-24, NORM-9, NORM-10), the
 * {@code time_hhmm} format (TYPE-33) and unpaired surrogates (NORM-8).
 */
final class ValueText {

  private static final Pattern DECIMAL = Pattern.compile("[+-]?[0-9]+(\\.[0-9]+)?");
  private static final Pattern TIME_HHMM = Pattern.compile("([01][0-9]|2[0-3]):[0-5][0-9]");

  private ValueText() {
  }

  /**
   * Tells whether a string is a decimal value: {@code [+-]?[0-9]+(\.[0-9]+)?} in full, with ASCII digits
   * (TYPE-24).
   *
   * @param text the string
   * @return {@code true} when it matches in full
   */
  static boolean isDecimal(String text) {
    return DECIMAL.matcher(text).matches();
  }

  /**
   * Canonicalises a decimal string with the steps of NORM-10.
   *
   * @param text a string for which {@link #isDecimal(String)} holds
   * @return the canonical string: no {@code +}, no leading zeros, no trailing fraction zeros, and
   *     {@code 0} for every zero
   */
  static String canonicalDecimal(String text) {
    boolean negative = text.charAt(0) == '-';
    int start = text.charAt(0) == '+' || negative ? 1 : 0;
    int dot = text.indexOf('.');
    String integer = dot < 0 ? text.substring(start) : text.substring(start, dot);
    String fraction = dot < 0 ? "" : text.substring(dot + 1);
    int lead = 0;
    while (lead < integer.length() - 1 && integer.charAt(lead) == '0') {
      lead++;
    }
    integer = integer.substring(lead);
    int end = fraction.length();
    while (end > 0 && fraction.charAt(end - 1) == '0') {
      end--;
    }
    fraction = fraction.substring(0, end);
    String body = fraction.isEmpty() ? integer : integer + "." + fraction;
    return negative && !body.equals("0") ? "-" + body : body;
  }

  /**
   * Tells whether a string is a {@code time_hhmm} value: {@code ^([01][0-9]|2[0-3]):[0-5][0-9]$} in full
   * (TYPE-33). {@code matches} requires the whole input, so a final line terminator does not match.
   *
   * @param text the string
   * @return {@code true} when it matches in full
   */
  static boolean isTimeHhmm(String text) {
    return TIME_HHMM.matcher(text).matches();
  }

  /**
   * Tells whether a string holds an unpaired surrogate code unit (NORM-8).
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
}
