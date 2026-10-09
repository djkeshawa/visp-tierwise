package io.github.tierwise.core.conformance;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Reaches the two canonical primitives of {@code internal.json}, which a later work item implements.
 *
 * <p>The harness calls them by reflection so that it compiles before they exist. If a class is missing,
 * the call raises {@code UnsupportedOperationException("not implemented: internal.json")}, which fails the
 * vector the same way an unimplemented public method does.
 *
 * <p>Contract the implementation must meet (the first matching public static method is used):
 *
 * <ul>
 *   <li>{@code io.github.tierwise.core.internal.json.CanonicalJson}: a method named {@code toBytes},
 *       {@code write}, {@code canonicalize}, {@code serialize} or {@code encode} taking one {@code Object}
 *       (a plain value tree of {@code Boolean}, {@code Long}, {@code String}, {@code null},
 *       {@code List<Object>} and {@code Map<String, Object>}) and returning the UTF-8 bytes of its JCS
 *       serialisation as {@code byte[]} (or a {@code String}).
 *   <li>{@code io.github.tierwise.core.internal.json.Sha256}: a method named {@code digest}, {@code hash} or
 *       {@code sha256} taking a {@code byte[]} and returning the 32 digest bytes as {@code byte[]} (or the
 *       64 lower-case hexadecimal characters as a {@code String}).
 * </ul>
 */
final class Primitives {

  private static final String JSON_PACKAGE = "io.github.tierwise.core.internal.json.";

  private Primitives() {
  }

  /**
   * Serialises a plain value tree to its JCS bytes.
   *
   * @param value the value
   * @return the bytes
   */
  static byte[] canonicalJson(Object value) {
    Object result = call("CanonicalJson", List.of("toBytes", "write", "canonicalize", "serialize", "encode"),
        Object.class, value);
    return result instanceof String text ? text.getBytes(StandardCharsets.UTF_8) : (byte[]) result;
  }

  /**
   * Computes the SHA-256 digest.
   *
   * @param input the bytes to hash
   * @return the digest as 64 lower-case hexadecimal characters
   */
  static String sha256Hex(byte[] input) {
    Object result = call("Sha256", List.of("digest", "hash", "sha256"), byte[].class, input);
    if (result instanceof String hex) {
      return hex;
    }
    return hex((byte[]) result);
  }

  /**
   * Renders bytes as lower-case hexadecimal.
   *
   * @param bytes the bytes
   * @return the hexadecimal text
   */
  static String hex(byte[] bytes) {
    StringBuilder out = new StringBuilder(bytes.length * 2);
    for (byte b : bytes) {
      out.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
    }
    return out.toString();
  }

  private static Object call(String className, List<String> names, Class<?> parameter, Object argument) {
    Class<?> type;
    try {
      type = Class.forName(JSON_PACKAGE + className);
    } catch (ClassNotFoundException e) {
      throw new UnsupportedOperationException("not implemented: internal.json (" + className + " is missing)");
    }
    for (String name : names) {
      for (Method method : type.getMethods()) {
        if (method.getName().equals(name) && Modifier.isStatic(method.getModifiers())
            && method.getParameterCount() == 1 && method.getParameterTypes()[0].isAssignableFrom(parameter)
            && (method.getReturnType() == byte[].class || method.getReturnType() == String.class)) {
          try {
            return method.invoke(null, argument);
          } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
              throw runtime;
            }
            throw new IllegalStateException(cause);
          } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
          }
        }
      }
    }
    throw new UnsupportedOperationException(
        "not implemented: internal.json (no public static method " + names + " on " + className + ")");
  }
}
