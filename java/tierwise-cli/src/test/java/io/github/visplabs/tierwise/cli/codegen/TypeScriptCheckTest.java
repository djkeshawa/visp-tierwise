package io.github.visplabs.tierwise.cli.codegen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Type-checks the generated TypeScript with the workspace's compiler and strict options. The checks are
 * skipped, with a message, when the workspace compiler or Node.js is not installed.
 */
class TypeScriptCheckTest {

  @BeforeEach
  void requireTheChecker() {
    CodegenTestSupport.assumeTypeScriptChecker();
  }

  @Test
  void commerceGoldenModuleTypeChecks(@TempDir Path directory) throws IOException, InterruptedException {
    Files.copy(CodegenTestSupport.resource("codegen/golden/commerce/ts/tierwise.ts"), directory.resolve("tierwise.ts"));
    assertEquals("", CodegenTestSupport.typeCheck(directory, List.of("tierwise.ts")));
  }

  @Test
  void retailPosGoldenModuleTypeChecks(@TempDir Path directory) throws IOException, InterruptedException {
    Files.copy(CodegenTestSupport.resource("codegen/golden/retail-pos/ts/tierwise.ts"), directory.resolve("tierwise.ts"));
    assertEquals("", CodegenTestSupport.typeCheck(directory, List.of("tierwise.ts")));
  }

  @Test
  void theRichModuleAcceptsTheTypedCallsAndRejectsTheMarkedOnes(@TempDir Path directory)
      throws IOException, InterruptedException {
    CodegenTestSupport.write(directory,
        CodegenTestSupport.generate(CodegenTestSupport.resourceProject("rich"), CodegenLanguage.TYPESCRIPT, null).files());
    Files.copy(CodegenTestSupport.resource("codegen/consumers/usage.ts"), directory.resolve("usage.ts"));
    assertEquals("", CodegenTestSupport.typeCheck(directory, List.of("tierwise.ts", "usage.ts")));
  }
}
