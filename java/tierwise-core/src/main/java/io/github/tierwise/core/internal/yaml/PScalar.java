package io.github.tierwise.core.internal.yaml;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * A scalar node, typed by the YAML 1.2 core schema (SRC-1).
 *
 * <p>The {@link #text() source token} is kept next to the typed {@link #value()}, because range checks
 * must look at the digits of the token, not at a converted number (TYPE-20).
 *
 * @param file the source file
 * @param path the path from the document root
 * @param line the 1-based line on which the node begins
 * @param kind the core-schema kind of the scalar
 * @param value the typed value: {@code null} for {@link Kind#NULL}, a {@code Boolean} for
 *     {@link Kind#BOOLEAN}, a {@code Long} or, when the token does not fit, a {@code BigInteger} for
 *     {@link Kind#INTEGER}, or {@link OutOfRangeInteger#INSTANCE} for an integer token of more than
 *     {@link #MAX_INTEGER_DIGITS} digits, a {@code Double} for {@link Kind#FLOAT}, a {@code String} for
 *     {@link Kind#STRING}
 * @param text the scalar as written in the source, quotes removed and escapes decoded for a string
 */
public record PScalar(String file, List<Object> path, int line, Kind kind, Object value, String text)
    implements PNode {

  /** The greatest number of digits of an integer token that the loader parses (ADR 0018). */
  public static final int MAX_INTEGER_DIGITS = 20;

  /** The core-schema kinds of a scalar. */
  public enum Kind {
    /** The core-schema null. */
    NULL,
    /** {@code true} or {@code false}. */
    BOOLEAN,
    /** A core-schema integer. */
    INTEGER,
    /** A core-schema floating-point number. */
    FLOAT,
    /** Everything else, including every quoted scalar. */
    STRING
  }

  /** Validates the components. */
  public PScalar {
    Objects.requireNonNull(file, "file");
    path = List.copyOf(path);
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(text, "text");
    boolean consistent = switch (kind) {
      case NULL -> value == null;
      case BOOLEAN -> value instanceof Boolean;
      case INTEGER -> value instanceof Long || value instanceof BigInteger || value == OutOfRangeInteger.INSTANCE;
      case FLOAT -> value instanceof Double;
      case STRING -> value instanceof String;
    };
    if (!consistent) {
      throw new IllegalArgumentException("value " + value + " does not match the kind " + kind);
    }
  }
}
