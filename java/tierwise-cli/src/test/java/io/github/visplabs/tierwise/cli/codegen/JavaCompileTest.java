package io.github.visplabs.tierwise.cli.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Compiles the generated Java with {@code javax.tools}, and runs a consumer of the rich project's
 * generated facade against a real evaluator. The consumer's checks are in {@code RichUsage}.
 */
class JavaCompileTest {

  @Test
  void commerceGoldenJavaCompilesWithLintAsErrors(@TempDir Path directory) throws IOException {
    List<Path> sources = CodegenTestSupport.javaSources(CodegenTestSupport.resource("codegen/golden/commerce/java"));
    CodegenTestSupport.compileJava(sources, directory.resolve("classes"));
  }

  @Test
  void retailPosGoldenJavaCompilesWithLintAsErrors(@TempDir Path directory) throws IOException {
    List<Path> sources = CodegenTestSupport.javaSources(CodegenTestSupport.resource("codegen/golden/retail-pos/java"));
    CodegenTestSupport.compileJava(sources, directory.resolve("classes"));
  }

  @Test
  void theRichFacadeRunsOnAnEvaluatorAndDecodesEveryValueForm(@TempDir Path directory)
      throws IOException, ReflectiveOperationException {
    Path generated = directory.resolve("generated");
    CodegenTestSupport.write(generated,
        CodegenTestSupport.generate(CodegenTestSupport.resourceProject("rich"), CodegenLanguage.JAVA, "rich.gen").files());
    Path consumer = directory.resolve("consumer").resolve("rich").resolve("gen").resolve("RichUsage.java");
    Files.createDirectories(consumer.getParent());
    Files.copy(CodegenTestSupport.resource("codegen/consumers/RichUsage.java"), consumer);

    List<Path> sources = new ArrayList<>(CodegenTestSupport.javaSources(generated));
    sources.add(consumer);
    Path classes = directory.resolve("classes");
    CodegenTestSupport.compileJava(sources, classes);

    try (URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()},
        JavaCompileTest.class.getClassLoader())) {
      Class<?> usage = Class.forName("rich.gen.RichUsage", true, loader);
      Object result;
      try {
        result = usage.getMethod("run", Path.class).invoke(null, CodegenTestSupport.resourceProject("rich"));
      } catch (InvocationTargetException e) {
        throw new AssertionError("the consumer failed: " + e.getCause(), e.getCause());
      }
      assertEquals("ok", result);
    }
  }
}
