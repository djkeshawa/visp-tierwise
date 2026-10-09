package io.github.tierwise.core;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The entry points of the Tierwise core.
 *
 * <p>{@link #compile(Path)} validates a project and writes the canonical bundle. {@link
 * #loadBundle(byte[])} verifies bundle bytes, and {@link #evaluator(LoadedBundle, EvaluatorOptions)}
 * creates the object that answers {@code resolve} and {@code decide}.
 */
public final class Tierwise {

  private Tierwise() {
  }

  /**
   * Validates and compiles the project in a directory with default options.
   *
   * @param projectRoot the directory that contains {@code tierwise.yaml}
   * @return the findings and, when none is an error, the canonical bundle bytes
   */
  public static CompileResult compile(Path projectRoot) {
    return compile(projectRoot, CompileOptions.defaults());
  }

  /**
   * Validates and compiles the project in a directory.
   *
   * @param projectRoot the directory that contains {@code tierwise.yaml}
   * @param options the compile options
   * @return the findings and, when none is an error, the canonical bundle bytes
   */
  public static CompileResult compile(Path projectRoot, CompileOptions options) {
    Objects.requireNonNull(projectRoot, "projectRoot");
    Objects.requireNonNull(options, "options");
    throw new UnsupportedOperationException("not implemented: internal.project");
  }

  /**
   * Loads a bundle, applying the checks of EVAL-14 in order.
   *
   * @param bytes the exact bytes of one bundle
   * @return the loaded bundle
   * @throws TierwiseException with {@code E_BUNDLE_INTEGRITY} or {@code E_BUNDLE_UNSUPPORTED}
   */
  public static LoadedBundle loadBundle(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes");
    throw new UnsupportedOperationException("not implemented: internal.bundle");
  }

  /**
   * Creates an evaluator for a loaded bundle (EVAL-4). It calls no provider.
   *
   * @param bundle a bundle returned by {@link #loadBundle(byte[])}
   * @param options providers, their timeouts and the listener
   * @return the evaluator
   * @throws TierwiseException with {@code E_EVALUATOR_OPTIONS} or {@code E_PROVIDER_MISSING}
   */
  public static Evaluator evaluator(LoadedBundle bundle, EvaluatorOptions options) {
    Objects.requireNonNull(bundle, "bundle");
    Objects.requireNonNull(options, "options");
    throw new UnsupportedOperationException("not implemented: internal.eval");
  }
}
