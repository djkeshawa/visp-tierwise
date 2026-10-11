package io.github.visplabs.tierwise.core;

import io.github.visplabs.tierwise.core.internal.bundle.BundleReader;
import io.github.visplabs.tierwise.core.internal.check.Compiler;
import io.github.visplabs.tierwise.core.internal.check.DeclaredNamingPatterns;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * The entry points of the Tierwise core.
 *
 * <p>{@link #compile(Path)} validates a project and writes the canonical bundle. {@link
 * #loadBundle(byte[])} verifies bundle bytes, and {@link #evaluator(LoadedBundle, EvaluatorOptions)}
 * creates the object that answers {@code resolve} and {@code decide}. {@link #namingPatterns(Path,
 * CompileOptions)} reads the naming patterns that explain the lint rules {@code naming/<name>}.
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
    return Compiler.compile(projectRoot, options);
  }

  /**
   * Reads the naming patterns in effect for a project: those its shared schema files and inline schema
   * declare and those it inherits from its presets, a later layer replacing an earlier pattern of the same
   * name (LINT-12, LINT-14, MANIFEST-32). A tool uses them to explain the lint rule {@code naming/<name>}
   * (FIND-30).
   *
   * <p>This is not a validation and reports no finding. A project with faults still gives every pattern
   * whose {@code category} and {@code pattern} are strings; a pattern without them is left out.
   *
   * @param projectRoot the directory that contains {@code tierwise.yaml}
   * @param options the compile options, which supply the fetcher for URL presets
   * @return an unmodifiable map from pattern name to pattern, in code-point order of the names; empty when
   *     the project declares no pattern or has no readable manifest
   */
  public static Map<String, NamingPattern> namingPatterns(Path projectRoot, CompileOptions options) {
    Objects.requireNonNull(projectRoot, "projectRoot");
    Objects.requireNonNull(options, "options");
    return DeclaredNamingPatterns.read(projectRoot, options);
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
    return BundleReader.read(bytes);
  }

  /**
   * Creates an evaluator for a loaded bundle (EVAL-4). It calls no provider.
   *
   * @param bundle a bundle returned by {@link #loadBundle(byte[])}
   * @param options providers, their timeouts and the listener; {@code null} counts as empty options
   *     (EVAL-28)
   * @return the evaluator
   * @throws TierwiseException with {@code E_EVALUATOR_OPTIONS} or {@code E_PROVIDER_MISSING}
   */
  public static Evaluator evaluator(LoadedBundle bundle, EvaluatorOptions options) {
    Objects.requireNonNull(bundle, "bundle");
    return io.github.visplabs.tierwise.core.internal.eval.EvaluatorImpl.create(bundle, options);
  }
}
