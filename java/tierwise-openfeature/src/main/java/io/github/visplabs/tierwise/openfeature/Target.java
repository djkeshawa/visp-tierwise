package io.github.visplabs.tierwise.openfeature;

import dev.openfeature.sdk.Value;
import java.util.List;
import java.util.Map;

/**
 * The value type an OpenFeature evaluation asks for. A target says whether features can answer it and
 * converts a plain value of the core to the requested Java type.
 *
 * @param <T> the Java type of the evaluation
 */
abstract class Target<T> {

  /** Boolean flags: boolean keys. */
  static final Target<Boolean> BOOLEAN = new Target<>("boolean", false) {
    @Override
    Boolean convert(Object plain) {
      if (plain instanceof Boolean flag) {
        return flag;
      }
      throw mismatch(plain);
    }
  };

  /** String flags: string, enum and decimal keys, and features with an enum effect. */
  static final Target<String> STRING = new Target<>("string", true) {
    @Override
    String convert(Object plain) {
      if (plain instanceof String text) {
        return text;
      }
      throw mismatch(plain);
    }
  };

  /** Integer flags: integer keys within the {@code int} range. */
  static final Target<Integer> INTEGER = new Target<>("integer", false) {
    @Override
    Integer convert(Object plain) {
      if (plain instanceof Long number) {
        if (number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE) {
          return (int) (long) number;
        }
        throw new Mismatch("the integer " + number + " is outside the int range");
      }
      throw mismatch(plain);
    }
  };

  /** Double flags: integer keys that a {@code double} holds exactly (magnitude up to 2^53). */
  static final Target<Double> DOUBLE = new Target<>("number", false) {
    @Override
    Double convert(Object plain) {
      if (plain instanceof Long number) {
        if (Math.abs(number) <= EXACT_DOUBLE_LIMIT) {
          return (double) (long) number;
        }
        throw new Mismatch("the integer " + number + " is not exactly representable as a double");
      }
      throw mismatch(plain);
    }
  };

  /** Object flags: set, list and object keys, and features with an object effect. */
  static final Target<Value> OBJECT = new Target<>("object", true) {
    @Override
    Value convert(Object plain) {
      if (plain instanceof List<?> || plain instanceof Map<?, ?>) {
        return PlainValues.toValue(plain);
      }
      throw mismatch(plain);
    }
  };

  /** The largest magnitude below which every integer is a double. */
  private static final long EXACT_DOUBLE_LIMIT = 1L << 53;

  private final String name;
  private final boolean features;

  private Target(String name, boolean features) {
    this.name = name;
    this.features = features;
  }

  /** Tells whether a feature can answer this type. */
  boolean readsFeatures() {
    return features;
  }

  /** Names the type for messages. */
  String describe() {
    return name;
  }

  /**
   * Converts a plain value.
   *
   * @param plain the value the evaluator returned, never {@code null}
   * @return the converted value
   * @throws Mismatch when the value does not have the requested type or is out of range
   */
  abstract T convert(Object plain);

  /** Builds the mismatch for a value of the wrong kind. */
  final Mismatch mismatch(Object plain) {
    return new Mismatch("the value is " + kindOf(plain) + ", not " + name);
  }

  private static String kindOf(Object plain) {
    if (plain instanceof Boolean) {
      return "a boolean";
    }
    if (plain instanceof Long) {
      return "an integer";
    }
    if (plain instanceof String) {
      return "a string";
    }
    if (plain instanceof List<?>) {
      return "a list";
    }
    return "an object";
  }

  /** Raised by {@link #convert(Object)} when a result cannot be read as the requested type. */
  static final class Mismatch extends RuntimeException {

    private static final long serialVersionUID = 1L;

    Mismatch(String message) {
      super(message, null, false, false);
    }
  }
}
