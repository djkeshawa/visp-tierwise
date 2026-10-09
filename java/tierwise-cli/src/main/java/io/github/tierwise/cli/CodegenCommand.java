package io.github.tierwise.cli;

import io.github.tierwise.cli.codegen.Codegen;
import io.github.tierwise.cli.codegen.CodegenException;
import io.github.tierwise.cli.codegen.CodegenLanguage;
import io.github.tierwise.cli.codegen.GeneratedFile;
import io.github.tierwise.core.CompileResult;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code tierwise codegen --lang java|ts [--package PKG] [-o DIR] [DIR]}: compiles the project in memory and
 * writes typed handles for its keys and features (requirements §7.1 and §7.2).
 *
 * <p>Java gets a package: the handle interfaces, the constant classes with one enum for each enum-valued
 * feature, and the typed facade. TypeScript gets one module. Nothing is written when the project has an
 * error finding; the findings are printed instead, and the exit code is 1. A usage error, such as a
 * {@code --package} with {@code --lang ts}, exits with 2 and writes nothing.
 */
@Command(name = "codegen", mixinStandardHelpOptions = true, versionProvider = VersionProvider.class,
    description = "Generate typed key and feature handles for Java or TypeScript.")
final class CodegenCommand extends ProjectCommand {

  @Option(names = "--lang", required = true, paramLabel = "java|ts",
      description = "The language to write: java (a package of handles) or ts (one module).")
  String lang;

  @Option(names = "--package", paramLabel = "PKG",
      description = "The Java package to write (default: " + Codegen.DEFAULT_JAVA_PACKAGE + "). Java only.")
  String javaPackage;

  @Option(names = {"-o", "--output"}, paramLabel = "DIR",
      description = "The directory to write into (default: the current directory).")
  Path output = Path.of(".");

  @Override
  public Integer call() {
    CodegenLanguage language = languageOf(lang);
    if (language == CodegenLanguage.TYPESCRIPT && javaPackage != null) {
      throw new UsageException("--package applies to --lang java only");
    }
    String packageName = javaPackage == null ? Codegen.DEFAULT_JAVA_PACKAGE : javaPackage;
    if (language == CodegenLanguage.JAVA) {
      try {
        Codegen.checkJavaPackage(packageName);
      } catch (IllegalArgumentException e) {
        throw new UsageException(e.getMessage(), e);
      }
    }

    CompileResult result = Projects.compile(dir);
    Codegen.Generation generation;
    try {
      generation = Codegen.generate(result, language, packageName);
    } catch (CodegenException e) {
      FindingReport.writeText(out(), e.findings());
      err().println("codegen: " + e.getMessage());
      return ExitCodes.INVALID;
    }

    FindingReport.writeText(out(), generation.warnings());
    for (GeneratedFile file : generation.files()) {
      out().println("wrote " + write(file));
    }
    if (!generation.warnings().isEmpty()) {
      err().println("codegen: " + FindingReport.count(generation.warnings()).summary());
    }
    return ExitCodes.OK;
  }

  private static CodegenLanguage languageOf(String option) {
    Optional<CodegenLanguage> language = CodegenLanguage.fromOption(option);
    return language.orElseThrow(() -> new UsageException("unknown language " + option + ": use java or ts"));
  }

  private Path write(GeneratedFile file) {
    try {
      return file.writeTo(output);
    } catch (UncheckedIOException e) {
      throw new UsageException(e.getMessage(), e);
    }
  }
}
