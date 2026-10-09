package io.github.tierwise.cli.codegen;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.github.tierwise.core.Tierwise;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Helpers of the codegen tests: the repository and the sample projects, the test resources, the golden
 * files, the Java compiler and the workspace TypeScript compiler.
 */
final class CodegenTestSupport {

  /**
   * The environment variable that rewrites the golden files from the current output when it is {@code 1}.
   * The golden tests compare otherwise.
   */
  static final String UPDATE_GOLDEN = "TIERWISE_CODEGEN_UPDATE_GOLDEN";

  private CodegenTestSupport() {
  }

  /**
   * Compiles a project in memory and generates its typed handles, as the command does.
   *
   * @param project the project directory
   * @param language the language to write
   * @param javaPackage the Java package, or {@code null} for TypeScript
   * @return the generation
   */
  static Codegen.Generation generate(Path project, CodegenLanguage language, String javaPackage) {
    return Codegen.generate(Tierwise.compile(project), language, javaPackage);
  }

  /**
   * Returns the root of the repository, found by walking up from the working directory.
   *
   * @return the directory that contains {@code samples/commerce}
   */
  static Path repositoryRoot() {
    Path start = Path.of("").toAbsolutePath();
    Path dir = start;
    while (dir != null && !Files.isDirectory(dir.resolve("samples").resolve("commerce"))) {
      dir = dir.getParent();
    }
    if (dir == null) {
      throw new IllegalStateException("the repository root is not above " + start);
    }
    return dir;
  }

  /**
   * Returns a sample project of the repository.
   *
   * @param name the directory name under {@code samples/}
   * @return the project directory
   */
  static Path sample(String name) {
    return repositoryRoot().resolve("samples").resolve(name);
  }

  /**
   * Returns a test resource that is a directory or a file on disk.
   *
   * @param path the path on the class path
   * @return the path of the resource
   */
  static Path resource(String path) {
    URL url = CodegenTestSupport.class.getClassLoader().getResource(path);
    if (url == null) {
      throw new IllegalStateException("missing test resource: " + path);
    }
    try {
      return Path.of(url.toURI());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("bad resource location: " + path, e);
    }
  }

  /**
   * Returns a test project of the codegen tests: a directory under {@code codegen/projects} in the test
   * resources.
   *
   * @param name the directory name
   * @return the project directory
   */
  static Path resourceProject(String name) {
    return resource("codegen/projects/" + name);
  }

  /**
   * Copies a directory tree.
   *
   * @param source the directory to copy
   * @param target the directory to create or fill
   * @throws IOException if a copy fails
   */
  static void copyTree(Path source, Path target) throws IOException {
    try (Stream<Path> paths = Files.walk(source)) {
      for (Path path : paths.toList()) {
        Path destination = target.resolve(source.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(destination);
        } else {
          Files.createDirectories(destination.getParent());
          Files.copy(path, destination);
        }
      }
    }
  }

  /**
   * Reads every regular file below a directory.
   *
   * @param root the directory
   * @return the text of each file, keyed by its path with {@code /} separators, in sorted order
   * @throws IOException if a read fails
   */
  static Map<String, String> readTree(Path root) throws IOException {
    Map<String, String> files = new TreeMap<>();
    try (Stream<Path> paths = Files.walk(root)) {
      for (Path path : paths.filter(Files::isRegularFile).toList()) {
        files.put(root.relativize(path).toString().replace('\\', '/'), Files.readString(path, UTF_8));
      }
    }
    return files;
  }

  /**
   * Keys generated files by their path.
   *
   * @param files the files
   * @return the contents, in sorted order of path
   */
  static Map<String, String> contents(List<GeneratedFile> files) {
    Map<String, String> byPath = new TreeMap<>();
    for (GeneratedFile file : files) {
      byPath.put(file.path(), file.content());
    }
    return byPath;
  }

  /**
   * Writes generated files below a directory.
   *
   * @param root the output directory
   * @param files the files
   */
  static void write(Path root, List<GeneratedFile> files) {
    for (GeneratedFile file : files) {
      file.writeTo(root);
    }
  }

  /**
   * Returns the class path that compiled code needs to see the core: the core's own location and the
   * test class path.
   *
   * @return the class path, separated for this platform
   */
  static String javaClasspath() {
    try {
      Path core = Path.of(Tierwise.class.getProtectionDomain().getCodeSource().getLocation().toURI());
      return core + File.pathSeparator + System.getProperty("java.class.path");
    } catch (URISyntaxException e) {
      throw new IllegalStateException("the core location is not a file", e);
    }
  }

  /**
   * Compiles Java sources with the strict options of the workspace: release 17, all lint warnings, and
   * warnings as errors. The test is skipped when the system compiler is not available.
   *
   * @param sources the source files
   * @param classes the output directory
   * @throws IOException if the output cannot be written
   */
  static void compileJava(List<Path> sources, Path classes) throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    assumeTrue(compiler != null, "skipped: no system Java compiler (run the tests on a JDK, not a JRE)");
    Files.createDirectories(classes);
    List<String> arguments = new ArrayList<>(List.of(
        "-encoding", "UTF-8", "--release", "17", "-Xlint:all", "-Werror",
        "-classpath", javaClasspath(), "-d", classes.toString()));
    for (Path source : sources) {
      arguments.add(source.toString());
    }
    ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
    int code = compiler.run(null, null, diagnostics, arguments.toArray(String[]::new));
    if (code != 0) {
      throw new AssertionError("javac failed:\n" + diagnostics.toString(UTF_8));
    }
  }

  /**
   * Lists the Java sources below a directory.
   *
   * @param root the directory
   * @return the paths of the {@code .java} files, sorted
   * @throws IOException if the tree cannot be read
   */
  static List<Path> javaSources(Path root) throws IOException {
    try (Stream<Path> paths = Files.walk(root)) {
      return paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
    }
  }

  /**
   * Returns the workspace TypeScript compiler, when the workspace has its dependencies installed.
   *
   * @return the path of {@code tsc.js}, or empty
   */
  static Optional<Path> typeScriptCompiler() {
    Path tsc = repositoryRoot().resolve("js").resolve("node_modules").resolve("typescript")
        .resolve("bin").resolve("tsc");
    return Files.isRegularFile(tsc) ? Optional.of(tsc) : Optional.empty();
  }

  /**
   * Tells whether Node.js runs on this machine.
   *
   * @return {@code true} when {@code node --version} succeeds
   */
  static boolean nodeAvailable() {
    try {
      Process process = new ProcessBuilder("node", "--version").redirectErrorStream(true).start();
      process.getInputStream().readAllBytes();
      return process.waitFor() == 0;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  /**
   * Type-checks files of one directory with the workspace compiler and strict options. The module
   * {@code @tierwise/evaluator} resolves to the evaluator sources of the workspace.
   *
   * @param directory the directory that holds the files
   * @param files the files, relative to the directory
   * @return the compiler output; empty when the check passes
   * @throws IOException if the compiler cannot be started
   * @throws InterruptedException if the wait is interrupted
   */
  static String typeCheck(Path directory, List<String> files) throws IOException, InterruptedException {
    Path tsc = typeScriptCompiler().orElseThrow();
    Path evaluator = repositoryRoot().resolve("js").resolve("evaluator").resolve("src").resolve("index.ts");
    String fileList = String.join(", ", files.stream().map(name -> "\"" + name + "\"").toList());
    String config = """
        {
          "compilerOptions": {
            "target": "ES2020",
            "module": "NodeNext",
            "moduleResolution": "NodeNext",
            "lib": ["ES2020", "DOM"],
            "strict": true,
            "noUncheckedIndexedAccess": true,
            "exactOptionalPropertyTypes": true,
            "useUnknownInCatchVariables": true,
            "noEmit": true,
            "paths": { "@tierwise/evaluator": ["%s"] }
          },
          "files": [%s]
        }
        """.formatted(evaluator.toString().replace("\\", "\\\\"), fileList);
    Files.writeString(directory.resolve("tsconfig.json"), config, UTF_8);
    Process process = new ProcessBuilder("node", tsc.toString(), "-p", "tsconfig.json")
        .directory(directory.toFile())
        .redirectErrorStream(true)
        .start();
    String output = new String(process.getInputStream().readAllBytes(), UTF_8);
    int code = process.waitFor();
    return code == 0 ? "" : "tsc exited with " + code + ":\n" + output;
  }

  /** Skips the calling test unless the TypeScript check can run here. */
  static void assumeTypeScriptChecker() {
    assumeTrue(typeScriptCompiler().isPresent(),
        "skipped: the workspace TypeScript compiler is not installed (run pnpm install in js/)");
    assumeTrue(nodeAvailable(), "skipped: node is not on the PATH");
  }
}
