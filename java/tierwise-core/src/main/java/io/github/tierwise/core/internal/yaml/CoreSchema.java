package io.github.tierwise.core.internal.yaml;

import io.github.tierwise.core.internal.yaml.PScalar.Kind;
import java.math.BigInteger;
import java.util.regex.Pattern;

/**
 * Resolves a plain scalar under the YAML 1.2 core schema (SRC-1, YAML 1.2.2 section 10.3.2).
 *
 * <p>Only the whole text decides the type. {@code yes}, {@code no}, {@code on} and {@code off} are
 * strings, and so is every token outside the core forms, such as {@code 0b101} and {@code 1_000}.
 * Quoted and block scalars are never resolved; they are strings.
 */
final class CoreSchema {

  private static final Pattern NULL = Pattern.compile("null|Null|NULL|~");
  private static final Pattern BOOLEAN = Pattern.compile("true|True|TRUE|false|False|FALSE");
  private static final Pattern INTEGER = Pattern.compile("[-+]?[0-9]+");
  private static final Pattern HEXADECIMAL = Pattern.compile("0x[0-9a-fA-F]+");
  private static final Pattern OCTAL = Pattern.compile("0o[0-7]+");
  private static final Pattern FLOAT =
      Pattern.compile("[-+]?(\\.[0-9]+|[0-9]+(\\.[0-9]*)?)([eE][-+]?[0-9]+)?");
  private static final Pattern INFINITY = Pattern.compile("[-+]?\\.(inf|Inf|INF)");
  private static final Pattern NAN = Pattern.compile("\\.(nan|NaN|NAN)");

  private CoreSchema() {
  }

  /**
   * A resolved scalar: its kind and its typed value (see {@link PScalar}).
   *
   * @param kind the kind
   * @param value the value: {@code null}, a {@code Boolean}, a {@code Long}, a {@code BigInteger} or
   *     {@link OutOfRangeInteger#INSTANCE}, a {@code Double}, or a {@code String}
   */
  record Typed(Kind kind, Object value) {
  }

  /**
   * Resolves the text of a plain scalar.
   *
   * <p>An integer is a {@code Long} when it fits in 64 bits, and a {@code BigInteger} otherwise. A
   * {@code Long} outside the engine range ({@code 2^53 - 1}) is kept, so that the check of TYPE-20 can
   * report it (TYPE-18). A token of more than 20 digits is not parsed and becomes the marker
   * {@link OutOfRangeInteger#INSTANCE}.
   *
   * @param text the text of the scalar as written
   * @return the resolved scalar
   */
  static Typed resolve(String text) {
    if (text.isEmpty() || NULL.matcher(text).matches()) {
      return new Typed(Kind.NULL, null);
    }
    if (BOOLEAN.matcher(text).matches()) {
      return new Typed(Kind.BOOLEAN, text.startsWith("t") || text.startsWith("T"));
    }
    if (INTEGER.matcher(text).matches()) {
      return new Typed(Kind.INTEGER, integer(digitsOf(text), text, 10));
    }
    if (HEXADECIMAL.matcher(text).matches()) {
      return new Typed(Kind.INTEGER, integer(text.substring(2), text.substring(2), 16));
    }
    if (OCTAL.matcher(text).matches()) {
      return new Typed(Kind.INTEGER, integer(text.substring(2), text.substring(2), 8));
    }
    if (FLOAT.matcher(text).matches()) {
      return new Typed(Kind.FLOAT, Double.parseDouble(text));
    }
    if (INFINITY.matcher(text).matches()) {
      return new Typed(Kind.FLOAT,
          text.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY);
    }
    if (NAN.matcher(text).matches()) {
      return new Typed(Kind.FLOAT, Double.NaN);
    }
    return new Typed(Kind.STRING, text);
  }

  /** The digits of a decimal token, without its sign. */
  private static String digitsOf(String text) {
    char first = text.charAt(0);
    return first == '-' || first == '+' ? text.substring(1) : text;
  }

  /**
   * Converts the digits of an integer token. A token with more than {@link PScalar#MAX_INTEGER_DIGITS}
   * digits is {@link OutOfRangeInteger#INSTANCE} and is never parsed, so that a very long token costs
   * linear time (ADR 0018).
   */
  private static Object integer(String digits, String token, int radix) {
    if (digits.length() > PScalar.MAX_INTEGER_DIGITS) {
      return OutOfRangeInteger.INSTANCE;
    }
    BigInteger value = radix == 10 ? new BigInteger(token) : new BigInteger(digits, radix);
    if (value.bitLength() < Long.SIZE) {
      return value.longValue();
    }
    return value;
  }
}
