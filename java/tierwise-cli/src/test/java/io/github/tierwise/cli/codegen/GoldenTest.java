package io.github.tierwise.cli.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Compares the output for the two samples with the golden files in {@code codegen/golden}. Set the
 * environment variable {@code TIERWISE_CODEGEN_UPDATE_GOLDEN=1} to rewrite the golden files from the
 * current output; review the diff before committing it.
 */
class GoldenTest {

  @Test
  void commerceJavaMatchesItsGoldenFiles() throws IOException {
    compare("commerce", CodegenLanguage.JAVA, Codegen.DEFAULT_JAVA_PACKAGE, "commerce/java");
  }

  @Test
  void commerceTypeScriptMatchesItsGoldenFile() throws IOException {
    compare("commerce", CodegenLanguage.TYPESCRIPT, Codegen.DEFAULT_JAVA_PACKAGE, "commerce/ts");
  }

  @Test
  void retailPosJavaMatchesItsGoldenFiles() throws IOException {
    compare("retail-pos", CodegenLanguage.JAVA, "com.example.retailpos", "retail-pos/java");
  }

  @Test
  void retailPosTypeScriptMatchesItsGoldenFile() throws IOException {
    compare("retail-pos", CodegenLanguage.TYPESCRIPT, Codegen.DEFAULT_JAVA_PACKAGE, "retail-pos/ts");
  }

  private static void compare(String sample, CodegenLanguage language, String javaPackage, String golden)
      throws IOException {
    List<GeneratedFile> files = CodegenTestSupport.generate(CodegenTestSupport.sample(sample), language, javaPackage).files();
    if ("1".equals(System.getenv(CodegenTestSupport.UPDATE_GOLDEN))) {
      Path source = CodegenTestSupport.repositoryRoot().resolve("java/tierwise-cli/src/test/resources/codegen/golden")
          .resolve(golden);
      CodegenTestSupport.write(source, files);
      return;
    }
    Map<String, String> expected = CodegenTestSupport.readTree(CodegenTestSupport.resource("codegen/golden/" + golden));
    Map<String, String> actual = CodegenTestSupport.contents(files);
    assertEquals(expected.keySet(), actual.keySet(), "the set of generated files");
    for (String path : actual.keySet()) {
      assertEquals(expected.get(path), actual.get(path), "the content of " + path);
    }
  }
}
