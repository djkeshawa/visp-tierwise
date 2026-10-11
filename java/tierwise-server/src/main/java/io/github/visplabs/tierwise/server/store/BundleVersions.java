package io.github.visplabs.tierwise.server.store;

import java.util.regex.Pattern;

/** The form of a {@code bundleVersion} (BUNDLE-5), shared by the stores and the endpoints. */
public final class BundleVersions {

  private static final Pattern FORM = Pattern.compile("sha256:[0-9a-f]{64}");

  private BundleVersions() {
  }

  /**
   * Tells whether a string has the form of a {@code bundleVersion}.
   *
   * @param text any string, or {@code null}
   * @return {@code true} for {@code sha256:} followed by exactly 64 lower-case hexadecimal digits
   */
  public static boolean isValid(String text) {
    return text != null && FORM.matcher(text).matches();
  }

  /**
   * Checks the form of a {@code bundleVersion}.
   *
   * @param text the string to check
   * @return the same string
   * @throws IllegalArgumentException when the string does not have the form of BUNDLE-5
   */
  public static String require(String text) {
    if (!isValid(text)) {
      throw new IllegalArgumentException("not a bundleVersion: " + abbreviate(text));
    }
    return text;
  }

  /**
   * Returns the 64 hexadecimal digits of a valid {@code bundleVersion}.
   *
   * @param version a valid version
   * @return the digits, without the {@code sha256:} prefix
   * @throws IllegalArgumentException when the string does not have the form of BUNDLE-5
   */
  public static String digest(String version) {
    return require(version).substring("sha256:".length());
  }

  private static String abbreviate(String text) {
    if (text == null) {
      return "null";
    }
    return text.length() > 80 ? text.substring(0, 80) + "..." : text;
  }
}
