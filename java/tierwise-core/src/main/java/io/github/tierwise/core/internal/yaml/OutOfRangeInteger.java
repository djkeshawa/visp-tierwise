package io.github.tierwise.core.internal.yaml;

/**
 * The value of an integer token that is too long to parse: more than {@link PScalar#MAX_INTEGER_DIGITS}
 * digits. Such a token lies far outside the engine range of plus or minus 2^53 - 1, so a range check
 * reports it as {@code E_INTEGER_RANGE} (TYPE-20) without converting its digits. The loader does not
 * parse the token, which avoids the quadratic cost of converting a very long decimal token (ADR 0018).
 */
public enum OutOfRangeInteger {

  /** The only marker. */
  INSTANCE
}
