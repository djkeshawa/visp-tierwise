package io.github.visplabs.tierwise.cli.codegen;

import io.github.visplabs.tierwise.core.CompileResult;
import io.github.visplabs.tierwise.core.Finding;
import java.util.List;
import java.util.Objects;

/**
 * Generates typed handles for a project: {@code tierwise codegen --lang java|ts}.
 *
 * <p>The caller compiles the project in memory, so no bundle needs to exist on disk, and passes the result
 * here. A result with an error finding gets no code. The Java output is one package; the TypeScript output is
 * one module.
 */
public final class Codegen {

  /** The Java package that is generated when {@code --package} is not given. */
  public static final String DEFAULT_JAVA_PACKAGE = "tierwise.generated";

  private Codegen() {
  }

  /**
   * The result of a successful generation.
   *
   * @param files the generated files, in a fixed order
   * @param warnings the findings of the compiler that did not fail it, such as lint warnings
   */
  public record Generation(List<GeneratedFile> files, List<Finding> warnings) {

    /** Validates the components. */
    public Generation {
      files = List.copyOf(files);
      warnings = List.copyOf(warnings);
    }
  }

  /**
   * Checks that a package name can name the generated Java package.
   *
   * @param javaPackage the package name, dot-separated
   * @throws IllegalArgumentException if it is not a Java package name
   */
  public static void checkJavaPackage(String javaPackage) {
    Objects.requireNonNull(javaPackage, "javaPackage");
    Identifiers.checkPackage(javaPackage);
  }

  /**
   * Generates the typed handles of a compiled project.
   *
   * @param result the result of {@code Tierwise.compile} for the project; its bundle is read, never written
   * @param language the language to write
   * @param javaPackage the Java package; ignored for {@link CodegenLanguage#TYPESCRIPT}
   * @return the generated files and the warnings
   * @throws CodegenException if the project has an error finding, or two declared names would map to one
   *     identifier
   * @throws IllegalArgumentException if the Java package name is not valid
   */
  public static Generation generate(CompileResult result, CodegenLanguage language, String javaPackage) {
    Objects.requireNonNull(result, "result");
    Objects.requireNonNull(language, "language");
    if (result.hasErrors()) {
      throw new CodegenException("the project has errors, so no code was generated", result.findings());
    }
    byte[] bundle = result.bundle().orElseThrow(
        () -> new IllegalStateException("a compile without error findings produced no bundle"));
    Contract contract = ContractReader.read(bundle);
    List<GeneratedFile> files = switch (language) {
      case JAVA -> JavaEmitter.emit(contract, Objects.requireNonNull(javaPackage, "javaPackage"));
      case TYPESCRIPT -> TypeScriptEmitter.emit(contract);
    };
    return new Generation(files, result.findings());
  }
}
